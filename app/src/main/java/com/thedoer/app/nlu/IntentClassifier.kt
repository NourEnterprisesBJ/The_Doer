package com.thedoer.app.nlu

import com.thedoer.app.core.models.Intent
import com.thedoer.app.core.models.IntentType
import com.thedoer.app.core.models.Slot
import java.text.Normalizer

/**
 * Classifieur hybride : règles/mots-clés en premier passage (rapide,
 * couvre les 10 intentions MVP), puis fallback vers un petit modèle
 * d'embeddings pour les formulations non couvertes (Phase 2).
 */
class IntentClassifier {

    private val embeddingClassifier = EmbeddingClassifier()

    fun classify(rawText: String): Intent {
        val normalized = normalize(rawText)
        val ruleResult = ruleBasedMatch(normalized, rawText)

        if (ruleResult != null) {
            return ruleResult
        }

        return embeddingClassifier.classify(rawText)
    }

    /**
     * Supprime les accents et met en minuscule pour fiabiliser le
     * matching de mots-clés (ex: "Appelle" et "appelle" et "APPELLE"
     * doivent tous matcher pareil).
     */
    private fun normalize(text: String): String {
        val lower = text.lowercase().trim()
        val decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{Mn}"), "")
    }

    private fun ruleBasedMatch(normalized: String, rawText: String): Intent? {
        return when {
            containsAny(normalized, "urgence", "sos", "aide moi", "au secours") ->
                buildIntent(IntentType.EMERGENCY_TRIGGER, rawText, confidence = 0.95f)

            containsAny(normalized, "appelle", "telephone a", "compose le numero") ->
                buildIntent(
                    IntentType.CALL_CONTACT, rawText, confidence = 0.85f,
                    slots = mapOf("target" to extractTarget(normalized, listOf("appelle", "telephone a")))
                )

            containsAny(normalized, "envoie un sms", "envoie un message", "ecris a") ->
                buildIntent(
                    IntentType.SEND_SMS, rawText, confidence = 0.8f,
                    slots = mapOf("target" to extractTarget(normalized, listOf("envoie un sms a", "envoie un message a", "ecris a")))
                )

            containsAny(normalized, "mets une alarme", "reveille moi", "alarme a") ->
                buildIntent(
                    IntentType.SET_ALARM, rawText, confidence = 0.85f,
                    slots = mapOf("time" to extractTime(normalized))
                )

            containsAny(normalized, "rappelle moi", "rappel de") ->
                buildIntent(
                    IntentType.SET_REMINDER, rawText, confidence = 0.8f,
                    slots = mapOf("time" to extractTime(normalized))
                )

            containsAny(normalized, "lis mon dernier message", "lis le dernier message", "quel est mon dernier message") ->
                buildIntent(IntentType.READ_LAST_MESSAGE, rawText, confidence = 0.85f)

            containsAny(normalized, "quelle heure", "quel jour", "quelle est la date") ->
                buildIntent(IntentType.GET_TIME_DATE, rawText, confidence = 0.9f)

            containsAny(normalized, "lis ce document", "lis ce texte", "scanne ce document", "lis moi ca") ->
                buildIntent(IntentType.READ_DOCUMENT_OCR, rawText, confidence = 0.75f)

            containsAny(normalized, "decris ce que tu vois", "qu'est ce que tu vois", "decris la scene") ->
                buildIntent(IntentType.DESCRIBE_SCENE, rawText, confidence = 0.75f)

            containsAny(normalized, "ajoute un evenement", "ajoute au calendrier", "planifie un rendez vous") ->
                buildIntent(
                    IntentType.ADD_CALENDAR_EVENT, rawText, confidence = 0.8f,
                    slots = mapOf("time" to extractTime(normalized))
                )

            else -> null
        }
    }

    private fun containsAny(text: String, vararg keywords: String): Boolean {
        return keywords.any { text.contains(it) }
    }

    private fun extractTarget(normalized: String, triggers: List<String>): String {
        for (trigger in triggers) {
            val index = normalized.indexOf(trigger)
            if (index != -1) {
                return normalized.substring(index + trigger.length).trim()
            }
        }
        return ""
    }

    /**
     * Extraction d'heure très basique (ex: "16h30", "16h", "16:30").
     * TODO (Sprint 2) : gérer les expressions relatives ("dans une heure",
     * "demain matin", etc.) — voir SlotResolver.resolveTime().
     */
    private fun extractTime(normalized: String): String {
        val regex = Regex("""(\d{1,2})\s*[h:]\s*(\d{0,2})""")
        val match = regex.find(normalized)
        return match?.value?.trim() ?: ""
    }

    private fun buildIntent(
        type: IntentType,
        rawText: String,
        confidence: Float,
        slots: Map<String, String> = emptyMap()
    ): Intent {
        val slotMap = slots
            .filterValues { it.isNotBlank() }
            .mapValues { (name, value) -> Slot(name = name, value = value) }
            .toMutableMap()

        return Intent(
            intentType = type,
            confidence = confidence,
            slots = slotMap,
            rawText = rawText
        )
    }
}

/**
 * Fallback pour les formulations non couvertes par les règles.
 *
 * STUB — non implémenté en Sprint 1. Nécessite un petit modèle
 * d'embeddings (type MiniLM/DistilBERT quantisé, TFLite) pour
 * mesurer la similarité sémantique avec des phrases exemples par
 * intention.
 *
 * TODO (Phase 2) : intégrer le vrai modèle. En attendant, retourne
 * toujours UNKNOWN plutôt que de deviner à tort.
 */
class EmbeddingClassifier {

    fun classify(rawText: String): Intent {
        return Intent(
            intentType = IntentType.UNKNOWN,
            confidence = 0f,
            slots = mutableMapOf(),
            rawText = rawText
        )
    }
}
