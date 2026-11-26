package com.thedoer.app.nlu

/**
 * Convertit les nombres écrits en toutes lettres (0 à 99) en chiffres
 * dans un texte français déjà normalisé (minuscules, sans accents).
 *
 * Pourquoi : le moteur de reconnaissance vocale (Vosk) transcrit
 * « sept heures trente » et non « 7h30 ». Les analyseurs qui suivent
 * (DateTimeParser) raisonnent sur des chiffres.
 *
 * Périmètre volontairement borné : 0 à 99, écritures usuelles
 * (« vingt et un », « soixante-dix », « quatre-vingt-dix-sept »), plus les
 * variantes belges et suisses (« septante », « huitante », « nonante »).
 *
 * ATTENTION : cette conversion transforme aussi « un » et « une » en « 1 ».
 * Elle ne doit donc JAMAIS être appliquée au texte utilisé pour reconnaître
 * les commandes (« envoie un sms » deviendrait « envoie 1 sms »). Elle sert
 * uniquement à l'analyse des heures et des durées.
 *
 * Objet sans état et sans dépendance Android : testable directement.
 */
object FrenchNumberParser {

    private val UNITS = mapOf(
        "zero" to 0, "un" to 1, "une" to 1, "deux" to 2, "trois" to 3,
        "quatre" to 4, "cinq" to 5, "six" to 6, "sept" to 7, "huit" to 8, "neuf" to 9
    )

    private val TEENS = mapOf(
        "dix" to 10, "onze" to 11, "douze" to 12, "treize" to 13,
        "quatorze" to 14, "quinze" to 15, "seize" to 16
    )

    private val TENS = mapOf(
        "vingt" to 20, "trente" to 30, "quarante" to 40, "cinquante" to 50,
        "soixante" to 60, "septante" to 70, "huitante" to 80, "octante" to 80,
        "nonante" to 90
    )

    private val QUATRE_VINGT = setOf("vingt", "vingts")

    private const val SOIXANTE = 60

    /** Nombre reconnu : sa valeur et le nombre de mots consommés. */
    private data class Match(val value: Int, val wordCount: Int)

    /**
     * Remplace chaque nombre écrit en lettres par ses chiffres.
     * Les tirets sont traités comme des espaces.
     *
     * Exemple : « mets une alarme a sept heures trente » devient
     * « mets 1 alarme a 7 heures 30 ».
     */
    fun replaceNumberWords(text: String): String {
        val words = text.replace('-', ' ').trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return ""

        val result = ArrayList<String>(words.size)
        var index = 0
        while (index < words.size) {
            val match = matchNumberAt(words, index)
            if (match != null) {
                result.add(match.value.toString())
                index += match.wordCount
            } else {
                result.add(words[index])
                index += 1
            }
        }
        return result.joinToString(" ")
    }

    private fun matchNumberAt(words: List<String>, index: Int): Match? {
        val word = words[index]

        if (word == "zero") return Match(0, 1)

        // 80 à 99 : « quatre vingt(s) » suivi éventuellement de 1 à 19.
        val next = words.getOrNull(index + 1)
        if (word == "quatre" && next != null && next in QUATRE_VINGT) {
            val suffix = matchUnderTwenty(words, index + 2)
            return if (suffix != null) {
                Match(80 + suffix.value, 2 + suffix.wordCount)
            } else {
                Match(80, 2)
            }
        }

        // Dizaines : vingt, trente, ... soixante, septante, huitante, nonante.
        val tens = TENS[word]
        if (tens != null) return matchTensWithSuffix(words, index, tens)

        // 1 à 19.
        return matchUnderTwenty(words, index)
    }

    /** Reconnaît un nombre de 1 à 19 (« dix sept » compte pour deux mots). */
    private fun matchUnderTwenty(words: List<String>, index: Int): Match? {
        val word = words.getOrNull(index) ?: return null

        if (word == "dix") {
            val unit = words.getOrNull(index + 1)?.let { UNITS[it] }
            if (unit != null && unit in 7..9) return Match(10 + unit, 2)
            return Match(10, 1)
        }

        TEENS[word]?.let { return Match(it, 1) }

        val unit = UNITS[word]
        if (unit != null && unit in 1..9) return Match(unit, 1)

        return null
    }

    private fun matchTensWithSuffix(words: List<String>, index: Int, base: Int): Match {
        val next = words.getOrNull(index + 1)

        // « vingt et un », « soixante et onze »...
        if (next == "et") {
            val following = words.getOrNull(index + 2)
            val extra = when {
                following == "un" || following == "une" -> 1
                following == "onze" && base == SOIXANTE -> 11
                else -> null
            }
            return if (extra != null) Match(base + extra, 3) else Match(base, 1)
        }

        // « vingt deux », « soixante dix sept »... Seule la soixantaine
        // admet un complément de 10 à 19 (soixante-dix, soixante-douze...).
        val maxSuffix = if (base == SOIXANTE) 19 else 9
        val suffix = matchUnderTwenty(words, index + 1)
        if (suffix != null && suffix.value <= maxSuffix) {
            return Match(base + suffix.value, 1 + suffix.wordCount)
        }
        return Match(base, 1)
    }
}
