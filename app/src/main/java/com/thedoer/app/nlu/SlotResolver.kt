package com.thedoer.app.nlu

import android.content.Context
import com.thedoer.app.core.models.Intent
import com.thedoer.app.core.models.IntentType
import com.thedoer.app.core.models.Slot

/**
 * Transforme les slots bruts d'une intention en valeurs exploitables.
 *
 * Contrat des slots après résolution (slot.resolved == true) :
 *  - "target" : resolvedValue = numéro de téléphone. Le nom prononcé
 *    (« maman ») reste dans slot.value, pour pouvoir le répéter à voix
 *    haute sans lire un numéro.
 *  - "time"   : resolvedValue = instant en millisecondes (texte décimal),
 *    à lire avec [resolvedTimestamp]. slot.value contient la phrase d'origine.
 *  - autres   : resolvedValue = value, sans transformation.
 *
 * Un slot qui n'a pas pu être résolu garde resolved == false.
 *
 * @param dateTimeParser analyseur d'heures, injectable pour les tests.
 */
class SlotResolver(
    context: Context,
    private val dateTimeParser: DateTimeParser = DateTimeParser()
) {

    private val contactResolver = ContactResolver(context)

    suspend fun resolve(intent: Intent): Intent {
        for ((name, slot) in intent.slots) {
            when (name) {
                SLOT_TARGET -> resolveContact(slot)
                SLOT_TIME -> resolveTime(slot)
                else -> markResolved(slot, slot.value)
            }
        }
        return intent
    }

    /**
     * Vrai si l'intention peut être exécutée : tous ses slots obligatoires
     * existent et tous les slots présents sont résolus. Permet de ne pas
     * demander de confirmation pour une action vouée à l'échec.
     */
    fun isComplete(intent: Intent): Boolean {
        val required = REQUIRED_SLOTS[intent.intentType].orEmpty()
        val requiredPresent = required.all { name -> intent.slots[name]?.resolved == true }
        return requiredPresent && intent.slots.values.all { it.resolved }
    }

    private suspend fun resolveContact(slot: Slot) {
        if (slot.value.isBlank()) {
            markUnresolved(slot)
            return
        }

        // Sans permission « contacts », la requête lève SecurityException :
        // on traite cela comme un contact introuvable. ActionExecutor
        // signalera la permission manquante au moment d'agir.
        val match = try {
            contactResolver.resolve(slot.value)
        } catch (e: SecurityException) {
            null
        }

        if (match == null) {
            // Introuvable ou ambigu : on ne devine jamais un contact.
            markUnresolved(slot)
        } else {
            markResolved(slot, match.phoneNumber)
        }
    }

    private fun resolveTime(slot: Slot) {
        val parsed = dateTimeParser.parse(slot.value)
        if (parsed == null) {
            markUnresolved(slot)
        } else {
            markResolved(slot, parsed.timestampMillis.toString())
        }
    }

    private fun markResolved(slot: Slot, value: String) {
        slot.resolved = true
        slot.resolvedValue = value
    }

    private fun markUnresolved(slot: Slot) {
        slot.resolved = false
        slot.resolvedValue = null
    }

    companion object {

        /** Noms de slots partagés avec IntentClassifier : toute modification doit être faite aux deux endroits. */
        const val SLOT_TARGET = "target"
        const val SLOT_TIME = "time"

        /**
         * Slots sans lesquels une intention ne peut pas aboutir.
         * À compléter quand une intention gagne un slot obligatoire.
         */
        private val REQUIRED_SLOTS: Map<IntentType, Set<String>> = mapOf(
            IntentType.CALL_CONTACT to setOf(SLOT_TARGET),
            IntentType.SEND_SMS to setOf(SLOT_TARGET),
            IntentType.SET_ALARM to setOf(SLOT_TIME),
            IntentType.SET_REMINDER to setOf(SLOT_TIME)
        )

        /** Instant (ms) d'un slot "time" résolu, ou null s'il est absent ou non résolu. */
        fun resolvedTimestamp(slot: Slot?): Long? =
            slot?.takeIf { it.resolved }?.resolvedValue?.toLongOrNull()
    }
}
