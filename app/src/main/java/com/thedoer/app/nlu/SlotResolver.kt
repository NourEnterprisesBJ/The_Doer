package com.thedoer.app.nlu

import com.thedoer.app.core.models.Intent
import com.thedoer.app.core.models.Slot

/**
 * Prend un Intent avec des slots bruts (ex: target="maman",
 * time="16h30") et tente de les résoudre en valeurs exploitables
 * (ex: target -> numéro de téléphone réel, time -> timestamp absolu).
 *
 * Sprint 1 : implémentation minimale qui marque juste les slots
 * comme résolus/non résolus sans vraie résolution métier.
 * Sprint 2/5 : vraie résolution contacts (ContactsContract) et
 * dates relatives.
 */
class SlotResolver {

    fun resolve(intent: Intent): Intent {
        for ((name, slot) in intent.slots) {
            when (name) {
                "target" -> resolveContact(slot)
                "time" -> resolveTime(slot)
                else -> {
                    // Slots génériques (ex: "content", "message") :
                    // pas de résolution nécessaire, on les considère
                    // valides tels quels.
                    slot.resolved = true
                    slot.resolvedValue = slot.value
                }
            }
        }
        return intent
    }

    /**
     * TODO (Sprint 5) : chercher dans une table Room de surnoms
     * ("maman" -> contact_id) puis fallback sur ContactsContract
     * pour trouver le numéro réel. Pour l'instant, on considère le
     * slot non résolu si vide, résolu tel quel sinon (le nom brut
     * sera utilisé comme recherche approximative par ActionExecutor).
     */
    private fun resolveContact(slot: Slot) {
        if (slot.value.isBlank()) {
            slot.resolved = false
            return
        }
        slot.resolved = true
        slot.resolvedValue = slot.value
    }

    /**
     * TODO (Sprint 2) : vraie interprétation des expressions
     * relatives ("dans une heure", "demain matin", "ce soir") en
     * timestamp absolu. Pour l'instant, on ne résout que le format
     * explicite déjà extrait par IntentClassifier (ex: "16h30").
     */
    private fun resolveTime(slot: Slot) {
        if (slot.value.isBlank()) {
            slot.resolved = false
            return
        }
        slot.resolved = true
        slot.resolvedValue = slot.value
    }
}
