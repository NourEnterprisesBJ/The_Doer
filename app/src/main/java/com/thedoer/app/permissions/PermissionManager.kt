package com.thedoer.app.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.thedoer.app.core.models.IntentType

/**
 * Centralise la question "de quelles permissions Android a besoin
 * telle intention ?" et vérifie leur état à l'exécution. L'UI
 * d'onboarding (fichier 18) utilise une liste large et explicite ;
 * ici c'est le mapping fin utilisé par ActionExecutor juste avant
 * d'agir, en filet de sécurité.
 */
object PermissionManager {

    fun requiredPermissions(intentType: IntentType): List<String> {
        return when (intentType) {
            IntentType.CALL_CONTACT -> listOf(
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_CONTACTS
            )

            IntentType.SEND_SMS -> listOf(
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_CONTACTS
            )

            IntentType.SET_ALARM -> emptyList() // ACTION_SET_ALARM ne nécessite pas de permission runtime

            IntentType.SET_REMINDER -> emptyList()

            IntentType.READ_LAST_MESSAGE -> listOf(
                Manifest.permission.READ_SMS
            )

            IntentType.GET_TIME_DATE -> emptyList()

            IntentType.READ_DOCUMENT_OCR -> listOf(
                Manifest.permission.CAMERA
            )

            IntentType.DESCRIBE_SCENE -> listOf(
                Manifest.permission.CAMERA
            )

            IntentType.ADD_CALENDAR_EVENT -> listOf(
                Manifest.permission.WRITE_CALENDAR,
                Manifest.permission.READ_CALENDAR
            )

            IntentType.EMERGENCY_TRIGGER -> listOfNotNull(
                Manifest.permission.CALL_PHONE,
                Manifest.permission.SEND_SMS,
                Manifest.permission.ACCESS_FINE_LOCATION
            )

            IntentType.UNKNOWN -> emptyList()
        }
    }

    fun hasAllPermissions(context: Context, intentType: IntentType): Boolean {
        return requiredPermissions(intentType).all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun missingPermissions(context: Context, intentType: IntentType): List<String> {
        return requiredPermissions(intentType).filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }
}
