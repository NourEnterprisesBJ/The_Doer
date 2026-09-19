package com.thedoer.app.nlu

import java.util.Calendar

/**
 * Interprétation des expressions temporelles en français, relatives
 * ou absolues, en timestamp exploitable. Remplace l'extraction
 * regex basique de IntentClassifier (qui ne gérait que "16h30").
 *
 * Volontairement limité à un jeu d'expressions courantes plutôt que
 * d'essayer de tout couvrir — mieux vaut échouer proprement (renvoyer
 * null) sur une formulation non reconnue que de deviner une heure
 * fausse pour une alarme ou un rappel.
 */
class DateTimeParser {

    data class ParsedTime(val timestampMillis: Long, val displayText: String)

    fun parse(normalizedText: String): ParsedTime? {
        return parseAbsoluteHour(normalizedText)
            ?: parseRelativeMinutesOrHours(normalizedText)
            ?: parseNamedMoment(normalizedText)
    }

    /** Ex: "16h30", "16h", "16:30" -> aujourd'hui ou demain à cette heure. */
    private fun parseAbsoluteHour(text: String): ParsedTime? {
        val regex = Regex("""(\d{1,2})\s*[h:]\s*(\d{0,2})""")
        val match = regex.find(text) ?: return null

        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        if (hour !in 0..23 || minute !in 0..59) return null

        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis

        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        // Si l'heure demandée est déjà passée aujourd'hui, on suppose
        // que c'est pour demain plutôt que de programmer dans le passé.
        if (calendar.timeInMillis <= now) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        return ParsedTime(
            calendar.timeInMillis,
            "${hour}h${minute.toString().padStart(2, '0')}"
        )
    }

    /** Ex: "dans une heure", "dans 30 minutes", "dans 2 heures". */
    private fun parseRelativeMinutesOrHours(text: String): ParsedTime? {
        val minutesRegex = Regex("""dans\s+(\d+|une|un)\s*minutes?""")
        val hoursRegex = Regex("""dans\s+(\d+|une|un)\s*heures?""")

        minutesRegex.find(text)?.let { match ->
            val amount = wordToNumber(match.groupValues[1]) ?: return null
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.MINUTE, amount)
            return ParsedTime(calendar.timeInMillis, "dans $amount minute(s)")
        }

        hoursRegex.find(text)?.let { match ->
            val amount = wordToNumber(match.groupValues[1]) ?: return null
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.HOUR_OF_DAY, amount)
            return ParsedTime(calendar.timeInMillis, "dans $amount heure(s)")
        }

        return null
    }

    /** Ex: "ce soir", "demain matin", "demain", "ce matin". */
    private fun parseNamedMoment(text: String): ParsedTime? {
        val calendar = Calendar.getInstance()

        val isTomorrow = text.contains("demain")
        if (isTomorrow) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        val (hour, label) = when {
            text.contains("ce soir") || text.contains("demain soir") -> 20 to "soir"
            text.contains("cet apres midi") || text.contains("demain apres midi") -> 15 to "après-midi"
            text.contains("ce matin") || text.contains("demain matin") -> 9 to "matin"
            isTomorrow -> 9 to null // "demain" seul -> 9h par défaut
            else -> return null
        }

        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val displayText = buildString {
            if (isTomorrow) append("demain") else append("aujourd'hui")
            if (label != null) append(" $label")
        }

        return ParsedTime(calendar.timeInMillis, displayText)
    }

    private fun wordToNumber(word: String): Int? {
        return when (word.lowercase()) {
            "un", "une" -> 1
            else -> word.toIntOrNull()
        }
    }
}
