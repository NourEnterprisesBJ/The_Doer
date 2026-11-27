package com.thedoer.app.execution

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent as AndroidIntent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import com.thedoer.app.core.eventbus.AppEvent
import com.thedoer.app.core.eventbus.EventBus
import com.thedoer.app.core.models.Intent
import com.thedoer.app.core.models.IntentType
import com.thedoer.app.core.models.Slot
import com.thedoer.app.nlu.DateTimeParser
import com.thedoer.app.nlu.SlotResolver
import com.thedoer.app.permissions.PermissionManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dernière étape du pipeline : transforme une intention résolue en action
 * système réelle (appel, SMS, alarme, calendrier, heure).
 *
 * Contrat : execute() publie TOUJOURS un résultat sur l'EventBus (succès ou
 * échec) accompagné d'une phrase à dire. La phrase décrit uniquement ce qui
 * s'est réellement passé : « J'ouvre l'écran de message » et non
 * « Message envoyé » quand l'utilisateur doit encore appuyer sur Envoyer.
 *
 * Limite connue (Android 10+) : lancer un écran (appel, alarme, SMS,
 * calendrier) depuis l'arrière-plan est restreint par le système. Ces
 * actions fonctionnent quand l'application est visible à l'écran. Un
 * fonctionnement mains libres écran éteint demandera d'autres mécanismes
 * (TelecomManager pour l'appel, SmsManager pour le SMS, AlarmManager pour
 * l'alarme) : chantier prévu après les premiers tests.
 */
class ActionExecutor(private val context: Context) {

    /** Issue d'une action : succès ou échec, et phrase à prononcer. */
    private data class Outcome(val success: Boolean, val message: String)

    suspend fun execute(intent: Intent) {
        val outcome = try {
            perform(intent)
        } catch (e: ActivityNotFoundException) {
            Outcome(false, "Aucune application du téléphone ne peut faire cela.")
        } catch (e: SecurityException) {
            EventBus.publish(AppEvent.ErrorOccurred(SOURCE, detailOf(e)))
            Outcome(false, "Android a refusé cette action. Vérifie les autorisations de l'application.")
        } catch (e: Exception) {
            EventBus.publish(AppEvent.ErrorOccurred(SOURCE, detailOf(e)))
            Outcome(false, "Une erreur est survenue.")
        }

        EventBus.publish(AppEvent.ActionExecuted(intent, outcome.success, outcome.message))
        EventBus.publish(AppEvent.SpeakRequest(outcome.message))
    }

    // ---------------------------------------------------------------
    // Aiguillage
    // ---------------------------------------------------------------

    private fun perform(intent: Intent): Outcome {
        if (!PermissionManager.hasAllPermissions(context, intent.intentType)) {
            return Outcome(
                false,
                "Il me manque une autorisation pour faire cela. Active-la dans les réglages de l'application."
            )
        }

        return when (intent.intentType) {
            IntentType.CALL_CONTACT -> call(intent)
            IntentType.SEND_SMS -> composeSms(intent)
            IntentType.SET_ALARM -> setAlarm(intent)
            IntentType.ADD_CALENDAR_EVENT -> openCalendarEditor(intent)
            IntentType.GET_TIME_DATE -> tellTimeAndDate()

            IntentType.UNKNOWN -> Outcome(false, "Je n'ai pas compris cette demande.")

            // Pas encore branchées : on le dit franchement plutôt que d'échouer en silence.
            IntentType.SET_REMINDER -> Outcome(false, "Les rappels ne sont pas encore disponibles.")
            IntentType.READ_LAST_MESSAGE -> Outcome(false, "La lecture des messages n'est pas encore disponible.")
            IntentType.READ_DOCUMENT_OCR -> Outcome(false, "La lecture de documents n'est pas encore disponible.")
            IntentType.DESCRIBE_SCENE -> Outcome(false, "La description de ce qui m'entoure n'est pas encore disponible.")
            IntentType.EMERGENCY_TRIGGER -> Outcome(
                false,
                "Le mode urgence n'est pas encore disponible. Compose toi-même le numéro des secours."
            )

            else -> Outcome(false, "Cette fonction n'est pas encore disponible.")
        }
    }

    // ---------------------------------------------------------------
    // Actions
    // ---------------------------------------------------------------

    private fun call(intent: Intent): Outcome {
        val target = intent.slots[SlotResolver.SLOT_TARGET]
        val number = resolvedNumber(target) ?: return contactFailure(target, "appeler")

        launch(AndroidIntent(AndroidIntent.ACTION_CALL, Uri.fromParts("tel", number, null)))
        return Outcome(true, "J'appelle ${target?.value}.")
    }

    private fun composeSms(intent: Intent): Outcome {
        val target = intent.slots[SlotResolver.SLOT_TARGET]
        val number = resolvedNumber(target) ?: return contactFailure(target, "contacter")
        val body = intent.slots[SLOT_MESSAGE]?.resolvedValue.orEmpty()

        val action = AndroidIntent(AndroidIntent.ACTION_SENDTO, Uri.fromParts("smsto", number, null))
            .putExtra("sms_body", body)
        launch(action)

        // Le message n'est PAS envoyé : l'écran de rédaction s'ouvre, l'envoi reste manuel.
        return Outcome(true, "J'ouvre l'écran de message pour ${target?.value}.")
    }

    private fun setAlarm(intent: Intent): Outcome {
        val timestamp = SlotResolver.resolvedTimestamp(intent.slots[SlotResolver.SLOT_TIME])
            ?: return Outcome(false, "Je n'ai pas compris l'heure de l'alarme.")
        val (hour, minute) = DateTimeParser.hourAndMinuteOf(timestamp)

        val action = AndroidIntent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        launch(action)

        return Outcome(true, "Alarme réglée ${DateTimeParser.describe(timestamp)}.")
    }

    private fun openCalendarEditor(intent: Intent): Outcome {
        val title = intent.slots[SLOT_TITLE]?.resolvedValue ?: intent.rawText

        val action = AndroidIntent(AndroidIntent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title)
        SlotResolver.resolvedTimestamp(intent.slots[SlotResolver.SLOT_TIME])?.let { begin ->
            action.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
        }
        launch(action)

        // L'événement n'est PAS encore enregistré : l'éditeur s'ouvre, l'enregistrement reste manuel.
        return Outcome(true, "J'ouvre le calendrier pour ajouter l'événement.")
    }

    private fun tellTimeAndDate(): Outcome {
        val now = Date()
        val time = SimpleDateFormat("H'h'mm", Locale.FRENCH).format(now)
        val date = SimpleDateFormat("EEEE d MMMM yyyy", Locale.FRENCH).format(now)
        return Outcome(true, "Il est $time, nous sommes le $date.")
    }

    // ---------------------------------------------------------------
    // Utilitaires
    // ---------------------------------------------------------------

    private fun launch(action: AndroidIntent) {
        action.addFlags(AndroidIntent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(action)
    }

    private fun resolvedNumber(target: Slot?): String? =
        target?.takeIf { it.resolved }?.resolvedValue?.takeIf { it.isNotBlank() }

    /** Distingue « rien compris » de « contact introuvable ou ambigu ». */
    private fun contactFailure(target: Slot?, verb: String): Outcome =
        if (target == null || target.value.isBlank()) {
            Outcome(false, "Je n'ai pas compris qui $verb.")
        } else {
            Outcome(false, "Je n'ai pas trouvé un contact unique nommé ${target.value}.")
        }

    private fun detailOf(error: Throwable): String =
        error.message ?: error.javaClass.simpleName

    private companion object {
        const val SOURCE = "ActionExecutor"
        const val SLOT_MESSAGE = "message"
        const val SLOT_TITLE = "title"
    }
}
