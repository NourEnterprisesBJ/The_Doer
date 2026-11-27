package com.thedoer.app.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import com.thedoer.app.core.models.IntentType

/**
 * Indique de quelles permissions Android chaque intention a besoin, et
 * vérifie leur état à l'exécution. ActionExecutor l'interroge juste avant
 * d'agir ; l'écran d'accueil (OnboardingActivity) demande, lui, la liste
 * complète au premier lancement.
 *
 * Règle : une permission n'est exigée que si l'action la nécessite
 * réellement. Exiger une permission superflue fait répondre « il me manque
 * une autorisation » à tort (c'était le cas du calendrier).
 */
object PermissionManager {

    fun requiredPermissions(intentType: IntentType): List<String> = when (intentType) {

        // ACTION_CALL exige CALL_PHONE ; READ_CONTACTS sert à trouver le numéro.
        IntentType.CALL_CONTACT -> listOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS
        )

        // L'application ouvre l'écran de SMS sans envoyer elle-même : seule la
        // recherche du contact exige une permission. SEND_SMS sera à exiger
        // le jour où l'envoi se fera directement (SmsManager).
        IntentType.SEND_SMS -> listOf(Manifest.permission.READ_CONTACTS)

        IntentType.READ_LAST_MESSAGE -> listOf(Manifest.permission.READ_SMS)

        IntentType.READ_DOCUMENT_OCR,
        IntentType.DESCRIBE_SCENE -> listOf(Manifest.permission.CAMERA)

        IntentType.EMERGENCY_TRIGGER -> listOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        // L'alarme (permission SET_ALARM, accordée à l'installation), le
        // calendrier (on ouvre l'éditeur, sans écrire nous-mêmes), l'heure et
        // les rappels n'exigent aucune permission à l'exécution.
        // Les intentions pas encore implémentées tombent aussi ici.
        else -> emptyList()
    }

    fun hasAllPermissions(context: Context, intentType: IntentType): Boolean =
        missingPermissions(context, intentType).isEmpty()

    fun missingPermissions(context: Context, intentType: IntentType): List<String> =
        requiredPermissions(intentType).filter { permission ->
            context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
        }
}
