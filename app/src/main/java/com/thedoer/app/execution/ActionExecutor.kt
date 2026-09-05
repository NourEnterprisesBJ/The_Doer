package com.thedoer.app.execution

import android.content.Context
import android.content.Intent as AndroidIntent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import com.thedoer.app.core.eventbus.AppEvent
import com.thedoer.app.core.eventbus.EventBus
import com.thedoer.app.core.models.Intent
import com.thedoer.app.core.models.IntentType
import com.thedoer.app.permissions.PermissionManager

/**
 * Dernière étape du pipeline : transforme un Intent résolu en action
 * système réelle (appel, SMS, alarme, calendrier...). Publie
 * ActionExecuted + SpeakRequest sur l'EventBus dans tous les cas,
 * succès ou échec, pour que l'utilisateur ait toujours un retour vocal.
 */
class ActionExecutor(private val context: Context) {

    suspend fun execute(intent: Intent) {
        if (!PermissionManager.hasAllPermissions(context, intent.intentType)) {
            val message = "Il me manque une permission pour faire ça."
            publishResult(intent, success = false, message = message)
            return
        }

        try {
            when (intent.intentType) {
                IntentType.CALL_CONTACT -> executeCall(intent)
                IntentType.SEND_SMS -> executeSms(intent)
                IntentType.SET_ALARM -> executeSetAlarm(intent)
                IntentType.ADD_CALENDAR_EVENT -> executeCalendarEvent(intent)
                IntentType.GET_TIME_DATE -> executeGetTimeDate(intent)
                IntentType.SET_REMINDER -> executeReminderStub(intent)
                IntentType.READ_LAST_MESSAGE -> executeReadLastMessageStub(intent)
                IntentType.READ_DOCUMENT_OCR -> executeOcrStub(intent)
                IntentType.DESCRIBE_SCENE -> executeDescribeSceneStub(intent)
                IntentType.EMERGENCY_TRIGGER -> executeEmergencyStub(intent)
                IntentType.UNKNOWN -> publishResult(
                    intent, success = false,
                    message = "Je n'ai pas compris cette demande."
                )
            }
        } catch (e: Exception) {
            publishResult(intent, success = false, message = "Une erreur est survenue : ${e.message}")
        }
    }

    private suspend fun executeCall(intent: Intent) {
        val target = intent.slots["target"]?.resolvedValue
        if (target.isNullOrBlank()) {
            publishResult(intent, success = false, message = "Je ne sais pas qui appeler.")
            return
        }
        val androidIntent = AndroidIntent(AndroidIntent.ACTION_CALL, Uri.parse("tel:$target")).apply {
            addFlags(AndroidIntent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(androidIntent)
        publishResult(intent, success = true, message = "J'appelle $target.")
    }

    private suspend fun executeSms(intent: Intent) {
        val target = intent.slots["target"]?.resolvedValue
        val message = intent.slots["message"]?.resolvedValue ?: ""
        if (target.isNullOrBlank()) {
            publishResult(intent, success = false, message = "Je ne sais pas à qui envoyer le message.")
            return
        }
        val androidIntent = AndroidIntent(AndroidIntent.ACTION_SENDTO, Uri.parse("smsto:$target")).apply {
            putExtra("sms_body", message)
            addFlags(AndroidIntent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(androidIntent)
        publishResult(intent, success = true, message = "Message envoyé à $target.")
    }

    private suspend fun executeSetAlarm(intent: Intent) {
        val time = intent.slots["time"]?.resolvedValue
        val (hour, minute) = parseHourMinute(time) ?: run {
            publishResult(intent, success = false, message = "Je n'ai pas compris l'heure de l'alarme.")
            return
        }
        val androidIntent = AndroidIntent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(AndroidIntent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(androidIntent)
        publishResult(intent, success = true, message = "Alarme réglée à ${hour}h${minute.toString().padStart(2, '0')}.")
    }

    private suspend fun executeCalendarEvent(intent: Intent) {
        val title = intent.slots["title"]?.resolvedValue ?: intent.rawText
        val androidIntent = AndroidIntent(AndroidIntent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            addFlags(AndroidIntent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(androidIntent)
        publishResult(intent, success = true, message = "Événement ajouté au calendrier.")
    }

    private suspend fun executeGetTimeDate(intent: Intent) {
        val now = java.util.Calendar.getInstance()
        val hour = now.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = now.get(java.util.Calendar.MINUTE)
        publishResult(
            intent, success = true,
            message = "Il est ${hour}h${minute.toString().padStart(2, '0')}."
        )
    }

    private fun parseHourMinute(time: String?): Pair<Int, Int>? {
        if (time.isNullOrBlank()) return null
        val regex = Regex("""(\d{1,2})\s*[h:]\s*(\d{0,2})""")
        val match = regex.find(time) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        return hour to minute
    }

    // --- Stubs Sprint 3/4/5 : intentions pas encore câblées ---

    private suspend fun executeReminderStub(intent: Intent) {
        publishResult(intent, success = false, message = "Les rappels seront disponibles bientôt.")
    }

    private suspend fun executeReadLastMessageStub(intent: Intent) {
        publishResult(intent, success = false, message = "La lecture des messages sera disponible bientôt.")
    }

    private suspend fun executeOcrStub(intent: Intent) {
        publishResult(intent, success = false, message = "La lecture de documents sera disponible bientôt.")
    }

    private suspend fun executeDescribeSceneStub(intent: Intent) {
        publishResult(intent, success = false, message = "La description de scène sera disponible bientôt.")
    }

    private suspend fun executeEmergencyStub(intent: Intent) {
        publishResult(intent, success = false, message = "Le mode urgence sera disponible bientôt.")
    }

    private suspend fun publishResult(intent: Intent, success: Boolean, message: String) {
        EventBus.publish(AppEvent.ActionExecuted(intent, success, message))
        EventBus.publish(AppEvent.SpeakRequest(message))
    }
}
