package com.thedoer.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Table des surnoms de contacts : permet à l'utilisateur de dire
 * "appelle maman" plutôt que le nom exact enregistré dans ses
 * contacts Android. Alimentée soit automatiquement (première
 * correspondance trouvée, proposée en confirmation), soit
 * manuellement plus tard via un écran de gestion (pas encore prévu).
 */
@Entity(tableName = "contact_nicknames")
data class ContactNickname(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Le surnom tel que prononcé, normalisé (minuscule, sans accents). */
    val nickname: String,

    /** L'identifiant du contact réel dans le carnet d'adresses Android. */
    val contactId: String,

    /** Le numéro de téléphone résolu, mis en cache pour éviter une
     *  nouvelle requête ContactsContract à chaque appel. */
    val phoneNumber: String,

    /** Nom complet du contact tel qu'affiché dans les contacts,
     *  utile pour confirmer oralement ("J'appelle Marie Dupont ?"). */
    val displayName: String
)
