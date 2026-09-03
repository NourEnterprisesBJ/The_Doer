package com.thedoer.app.core.models

/**
 * Les 10 intentions du MVP (périmètre Phase 1).
 * Ajouter une intention = l'ajouter ici + un jeu de règles dans
 * IntentClassifier + un handler dans ActionExecutor. Rien d'autre
 * ne devrait avoir besoin de changer.
 */
enum class IntentType {
    CALL_CONTACT,
    SEND_SMS,
    SET_ALARM,
    SET_REMINDER,
    READ_LAST_MESSAGE,
    GET_TIME_DATE,
    READ_DOCUMENT_OCR,
    DESCRIBE_SCENE,
    ADD_CALENDAR_EVENT,
    EMERGENCY_TRIGGER,
    UNKNOWN
}

data class Slot(
    val name: String,
    val value: String,
    var resolved: Boolean = false,
    var resolvedValue: String? = null
)

data class Intent(
    val intentType: IntentType,
    val confidence: Float,
    val slots: MutableMap<String, Slot> = mutableMapOf(),
    val rawText: String,
    val timestamp: Long = System.currentTimeMillis(),
    val requiresConfirmation: Boolean = defaultConfirmationFor(intentType)
) {
    companion object {
        /**
         * Politique de confirmation centralisée (voir doc Phase 0).
         * EMERGENCY_TRIGGER saute volontairement la confirmation par
         * défaut — configurable plus tard par profil d'accessibilité.
         */
        fun defaultConfirmationFor(type: IntentType): Boolean = when (type) {
            IntentType.CALL_CONTACT,
            IntentType.SEND_SMS,
            IntentType.SET_ALARM,
            IntentType.SET_REMINDER,
            IntentType.ADD_CALENDAR_EVENT -> true
            IntentType.EMERGENCY_TRIGGER,
            IntentType.GET_TIME_DATE,
            IntentType.READ_LAST_MESSAGE,
            IntentType.READ_DOCUMENT_OCR,
            IntentType.DESCRIBE_SCENE,
            IntentType.UNKNOWN -> false
        }
    }
}
