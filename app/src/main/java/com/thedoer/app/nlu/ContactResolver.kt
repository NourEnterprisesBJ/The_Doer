package com.thedoer.app.nlu

import android.content.Context
import android.provider.ContactsContract
import com.thedoer.app.data.local.AppDatabase
import com.thedoer.app.data.local.ContactNickname
import java.text.Normalizer

/**
 * Résolution réelle des contacts. Stratégie en 2 temps :
 *  1. Vérifie si ce surnom a déjà été résolu et enregistré (rapide,
 *     pas d'accès au carnet d'adresses)
 *  2. Sinon, cherche dans ContactsContract par correspondance
 *     approximative sur le nom, et enregistre le résultat pour la
 *     prochaine fois si trouvé.
 *
 * Résultat toujours ambigu par nature (plusieurs "Marie" possibles,
 * faute de frappe du STT, etc.) — d'où le retour d'une liste de
 * candidats plutôt que d'un résultat unique.
 */
class ContactResolver(private val context: Context) {

    data class ContactMatch(
        val contactId: String,
        val displayName: String,
        val phoneNumber: String
    )

    private val dao by lazy { AppDatabase.getInstance(context).contactNicknameDao() }

    /**
     * Retourne un contact résolu si on peut trancher sans ambiguïté
     * (surnom déjà connu, ou une seule correspondance trouvée), ou
     * null si rien trouvé ou si plusieurs candidats se valent
     * (dans ce cas, ActionExecutor devra demander une clarification
     * — TODO Sprint 3+).
     */
    suspend fun resolve(spokenName: String): ContactMatch? {
        val normalized = normalize(spokenName)
        if (normalized.isBlank()) return null

        dao.findByNickname(normalized)?.let { known ->
            return ContactMatch(known.contactId, known.displayName, known.phoneNumber)
        }

        val candidates = searchContacts(normalized)
        if (candidates.size == 1) {
            val match = candidates.first()
            dao.save(
                ContactNickname(
                    nickname = normalized,
                    contactId = match.contactId,
                    phoneNumber = match.phoneNumber,
                    displayName = match.displayName
                )
            )
            return match
        }

        // 0 ou plusieurs candidats : on ne devine pas, on renvoie null.
        return null
    }

    private fun searchContacts(normalized: String): List<ContactMatch> {
        val results = mutableListOf<ContactMatch>()
        val resolver = context.contentResolver

        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$normalized%")

        resolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (cursor.moveToNext()) {
                results.add(
                    ContactMatch(
                        contactId = cursor.getString(idIndex),
                        displayName = cursor.getString(nameIndex),
                        phoneNumber = cursor.getString(numberIndex)
                    )
                )
            }
        }

        return results.distinctBy { it.contactId }
    }

    private fun normalize(text: String): String {
        val lower = text.lowercase().trim()
        val decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{Mn}"), "")
    }
}
