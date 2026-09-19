package com.thedoer.app.nlu

import android.content.Context
import com.thedoer.app.core.models.Intent

/**
 * Prend un Intent avec des slots bruts (ex: target="maman",
 * time="16h30") et les résout en valeurs exploitables : target ->
 * numéro de téléphone réel via ContactResolver, time -> timestamp
 * absolu via DateTimeParser.
 *
 * Sprint 2 : résolution réelle branchée (fini les stubs qui
 * renvoyaient la valeur brute telle quelle).
 */
class SlotResolver(context: Context) {

    private val contactResolver = ContactResolver(context)
    private val dateTimeParser = DateTimeParser()

    suspend fun resolve(intent: Intent): Intent {
        for ((name, slot) in intent.slots) {
            when (name) {
                "target" -> resolveContact(slot)
                "time" -> resolveTime(slot)
                else -> {
                    // Slots génériques (ex: "content", "message") :
                    // pas de résolution nécessaire, valides tels quels.
                    slot.resolved = true
                    slot.resolvedValue = slot.value
                }
            }
        }
        return intent
    }

    private suspend fun resolveContact(slot: com.thedoer.app.core.models.Slot) {
        if (slot.value.isBlank()) {
            slot.resolved = false
            return
        }

        val match = contactResolver.resolve(slot.value)
        if (match == null) {
            // Pas trouvé, ou ambigu (plusieurs candidats) : on ne
            // devine pas. ActionExecutor verra resolved=false et
            // répondra qu'il ne connaît pas ce contact.
            slot.resolved = false
            return
        }

        slot.resolved = true
        slot.resolvedValue = match.phoneNumber
    }

    private fun resolveTime(slot: com.thedoer.app.core.models.Slot) {
        if (slot.value.isBlank()) {
            slot.resolved = false
            return
        }

        val parsed = dateTimeParser.parse(slot.value)
        if (parsed == null) {
            slot.resolved = false
            return
        }

        slot.resolved = true
        // On garde le format "16h30" pour ActionExecutor (AlarmClock
        // attend heure/minute, pas un timestamp), mais on pourrait
        // aussi exposer parsed.timestampMillis si un futur intent
        // en a besoin (ex: SET_REMINDER en Sprint 3).
        slot.resolvedValue = parsed.displayText
    }
}
