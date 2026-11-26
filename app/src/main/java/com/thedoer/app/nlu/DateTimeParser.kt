package com.thedoer.app.nlu

import java.text.Normalizer
import java.util.Calendar
import java.util.Locale

/**
 * Interprète une expression de temps en français et la convertit en
 * instant précis (millisecondes depuis 1970, fuseau de l'appareil).
 *
 * Le texte analysé peut être une phrase entière (« mets une alarme à sept
 * heures et demie ») : l'expression de temps y est repérée automatiquement.
 *
 * Expressions reconnues :
 *  - heures : « 7h », « 7h30 », « 7:30 », « sept heures trente »,
 *    « sept heures et demie », « sept heures et quart »,
 *    « huit heures moins le quart », « midi », « midi et demi », « minuit »
 *  - périodes : « du soir » et « de l'après-midi » (ajoutent 12 h)
 *  - jours : « demain », « après-demain », « aujourd'hui »
 *  - moments : « ce matin » (9 h), « cet après-midi » (15 h), « ce soir » (20 h),
 *    « demain » seul (9 h)
 *  - durées : « dans 2 heures », « dans une heure et demie », « dans 30 minutes »,
 *    « dans une demi-heure », « dans un quart d'heure »
 *
 * Règles de décision :
 *  - heure sans jour précisé : aujourd'hui si elle est à venir, sinon demain ;
 *  - heure avec un jour explicite déjà passée (« ce matin » à 14 h) : refusée.
 *
 * Principe de prudence : une formulation non reconnue donne null. Mieux vaut
 * dire « je n'ai pas compris » que programmer une alarme à une heure fausse.
 *
 * @param clock source de l'heure courante, injectable pour les tests.
 */
class DateTimeParser(private val clock: () -> Long = System::currentTimeMillis) {

    /** Instant résolu. Conteneur extensible (précision, récurrence...) sans casser les appelants. */
    data class ParsedTime(val timestampMillis: Long)

    private data class ClockTime(val hour: Int, val minute: Int)

    fun parse(text: String): ParsedTime? {
        val prepared = prepare(text)
        if (prepared.isEmpty()) return null

        val now = clock()

        parseRelative(prepared, now)?.let { return ParsedTime(it) }

        val dayOffset = detectDayOffset(prepared)
        val clockTime = parseClockTime(prepared)
            ?: parseNamedMoment(prepared)
            ?: return null

        return buildInstant(now, dayOffset, clockTime)?.let { ParsedTime(it) }
    }

    // ---------------------------------------------------------------
    // Préparation du texte
    // ---------------------------------------------------------------

    /** Minuscules, sans accents ni ponctuation, nombres en chiffres. */
    private fun prepare(text: String): String {
        val lowered = text.lowercase(Locale.ROOT)
        val unaccented = Normalizer.normalize(lowered, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
        val cleaned = unaccented.replace(NOT_ALPHANUMERIC, " ").trim()
        return FrenchNumberParser.replaceNumberWords(cleaned)
    }

    // ---------------------------------------------------------------
    // Durées relatives : « dans deux heures »
    // ---------------------------------------------------------------

    private fun parseRelative(text: String, now: Long): Long? {
        val delayMinutes = relativeDelayMinutes(text) ?: return null
        if (delayMinutes !in 1..MAX_RELATIVE_DELAY_MINUTES) return null

        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.MINUTE, delayMinutes)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    private fun relativeDelayMinutes(text: String): Int? {
        HOURS_REGEX.find(text)?.let { match ->
            val hours = match.groupValues[1].toInt()
            val extra = match.groupValues[2]
            val extraMinutes = when {
                extra.isEmpty() -> 0
                extra.startsWith("et demi") -> 30
                extra == "et quart" -> 15
                else -> extra.toInt()
            }
            return hours * 60 + extraMinutes
        }
        MINUTES_REGEX.find(text)?.let { return it.groupValues[1].toInt() }
        if (HALF_HOUR_REGEX.containsMatchIn(text)) return 30
        if (QUARTER_HOUR_REGEX.containsMatchIn(text)) return 15
        return null
    }

    // ---------------------------------------------------------------
    // Jour
    // ---------------------------------------------------------------

    /** Décalage en jours si le texte le précise (0, 1, 2), sinon null. */
    private fun detectDayOffset(text: String): Int? = when {
        AFTER_TOMORROW_REGEX.containsMatchIn(text) -> 2
        TOMORROW_REGEX.containsMatchIn(text) -> 1
        TODAY_REGEX.containsMatchIn(text) -> 0
        else -> null
    }

    // ---------------------------------------------------------------
    // Heure de la journée
    // ---------------------------------------------------------------

    private fun parseClockTime(text: String): ClockTime? {
        val isPm = PM_REGEX.containsMatchIn(text)

        CLOCK_REGEX.find(text)?.let { match ->
            val hour = adjustForPeriod(match.groupValues[1].toInt(), isPm)
            val modifier = match.groupValues[2].ifEmpty { null }
            return resolveClock(hour, modifier)
        }

        NOON_REGEX.find(text)?.let { match ->
            return resolveClock(12, match.groupValues[1].ifEmpty { null })
        }

        MIDNIGHT_REGEX.find(text)?.let { match ->
            return resolveClock(0, match.groupValues[1].ifEmpty { null })
        }

        return null
    }

    /** « sept heures du soir » : 7 devient 19. Seules les heures 1 à 11 sont concernées. */
    private fun adjustForPeriod(hour: Int, isPm: Boolean): Int =
        if (isPm && hour in 1..11) hour + 12 else hour

    /**
     * Combine une heure et son complément (« et demie », « moins le quart »
     * ou des minutes en chiffres). Null si les valeurs sont invalides.
     */
    private fun resolveClock(hour: Int, modifier: String?): ClockTime? {
        if (hour !in 0..23) return null
        return when {
            modifier == null -> ClockTime(hour, 0)
            modifier.startsWith("et demi") -> ClockTime(hour, 30)
            modifier == "et quart" -> ClockTime(hour, 15)
            modifier.startsWith("moins") -> ClockTime(if (hour == 0) 23 else hour - 1, 45)
            else -> modifier.toIntOrNull()
                ?.takeIf { it in 0..59 }
                ?.let { ClockTime(hour, it) }
        }
    }

    /** Moments sans heure précise : « ce soir », « demain matin », « demain ». */
    private fun parseNamedMoment(text: String): ClockTime? = when {
        EVENING_REGEX.containsMatchIn(text) -> ClockTime(20, 0)
        AFTERNOON_REGEX.containsMatchIn(text) -> ClockTime(15, 0)
        MORNING_REGEX.containsMatchIn(text) -> ClockTime(9, 0)
        TOMORROW_REGEX.containsMatchIn(text) -> ClockTime(9, 0)
        else -> null
    }

    // ---------------------------------------------------------------
    // Construction de l'instant
    // ---------------------------------------------------------------

    private fun buildInstant(now: Long, dayOffset: Int?, time: ClockTime): Long? {
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        if (dayOffset != null) calendar.add(Calendar.DAY_OF_YEAR, dayOffset)
        calendar.set(Calendar.HOUR_OF_DAY, time.hour)
        calendar.set(Calendar.MINUTE, time.minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        if (calendar.timeInMillis <= now) {
            // Jour explicite déjà passé : on refuse. Sans jour précisé : demain.
            if (dayOffset != null) return null
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }

    companion object {

        private const val MAX_RELATIVE_DELAY_MINUTES = 7 * 24 * 60
        private const val MILLIS_PER_DAY = 86_400_000.0

        private val COMBINING_MARKS = Regex("\\p{Mn}")
        private val NOT_ALPHANUMERIC = Regex("[^a-z0-9:]+")

        private const val MODIFIER = "et demie?|et quart|moins le quart|moins quart"

        private val HOURS_REGEX =
            Regex("""\bdans\s+(\d{1,3})\s*(?:heures?\b|h(?![a-z]))\s*(et demie?|et quart|\d{1,2})?""")
        private val MINUTES_REGEX = Regex("""\bdans\s+(\d{1,3})\s*(?:minutes?|mins?)\b""")
        private val HALF_HOUR_REGEX = Regex("""\bdans\s+(?:1\s+)?demi\s+heure\b""")
        private val QUARTER_HOUR_REGEX = Regex("""\bdans\s+(?:1\s+)?quart\s+d\s+heure\b""")

        private val CLOCK_REGEX =
            Regex("""(?<!\d)(\d{1,2})\s*(?:h(?![a-z])|heures?\b|:)\s*($MODIFIER|\d{1,2}(?!\d))?""")
        private val NOON_REGEX = Regex("""(?<!apres )\bmidi\b(?:\s+($MODIFIER))?""")
        private val MIDNIGHT_REGEX = Regex("""\bminuit\b(?:\s+($MODIFIER))?""")

        private val AFTER_TOMORROW_REGEX = Regex("""\bapres demain\b""")
        private val TOMORROW_REGEX = Regex("""\bdemain\b""")
        private val TODAY_REGEX = Regex("""\b(?:aujourd hui|ce matin|ce midi|ce soir|cet apres midi)\b""")

        private val PM_REGEX = Regex("""\b(?:soir|apres midi)\b""")
        private val EVENING_REGEX = Regex("""\bsoir\b""")
        private val AFTERNOON_REGEX = Regex("""\bapres midi\b""")
        private val MORNING_REGEX = Regex("""\bmatin\b""")

        /** Heure et minute locales d'un instant. */
        fun hourAndMinuteOf(timestampMillis: Long): Pair<Int, Int> {
            val calendar = Calendar.getInstance().apply { timeInMillis = timestampMillis }
            return calendar.get(Calendar.HOUR_OF_DAY) to calendar.get(Calendar.MINUTE)
        }

        /**
         * Formulation orale d'un instant : « à 7h30 », « demain à 9h00 »,
         * « après-demain à 8h15 », ou « le 28/11 à 8h15 » au-delà.
         */
        fun describe(timestampMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
            val target = Calendar.getInstance().apply { timeInMillis = timestampMillis }
            val hour = target.get(Calendar.HOUR_OF_DAY)
            val minute = target.get(Calendar.MINUTE)
            val time = "${hour}h${minute.toString().padStart(2, '0')}"

            val dayDifference = Math.round(
                (startOfDay(timestampMillis) - startOfDay(nowMillis)) / MILLIS_PER_DAY
            ).toInt()

            return when (dayDifference) {
                0 -> "à $time"
                1 -> "demain à $time"
                2 -> "après-demain à $time"
                else -> {
                    val day = target.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
                    val month = (target.get(Calendar.MONTH) + 1).toString().padStart(2, '0')
                    "le $day/$month à $time"
                }
            }
        }

        private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
