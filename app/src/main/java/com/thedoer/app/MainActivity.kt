package com.thedoer.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.thedoer.app.core.eventbus.AppEvent
import com.thedoer.app.core.eventbus.EventBus
import com.thedoer.app.core.models.IntentType
import com.thedoer.app.service.VoiceForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Écran principal : tableau de bord en direct de l'assistant.
 *
 * Il ne contient aucune logique métier. Il affiche ce que le service
 * vocal publie sur l'EventBus (phrase entendue, intention comprise,
 * résultat) et permet d'arrêter ou de relancer l'écoute.
 *
 * Accessibilité : textes agrandis, champs annoncés automatiquement par
 * TalkBack à chaque mise à jour (zones "live region").
 */
class MainActivity : AppCompatActivity() {

    private companion object {
        const val PREFS_NAME = "thedoer_prefs"
        const val KEY_LISTENING_ENABLED = "listening_enabled"
    }

    private val prefs by lazy { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }

    private lateinit var statusValue: TextView
    private lateinit var heardValue: TextView
    private lateinit var understoodValue: TextView
    private lateinit var outcomeValue: TextView
    private lateinit var errorBlock: LinearLayout
    private lateinit var errorValue: TextView
    private lateinit var permissionBlock: LinearLayout
    private lateinit var toggleButton: Button

    /** Actif uniquement quand l'écran est visible : aucune collecte en arrière-plan. */
    private var uiScope: CoroutineScope? = null

    // ---------------------------------------------------------------
    // Cycle de vie
    // ---------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildLayout())
    }

    override fun onStart() {
        super.onStart()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        uiScope = scope
        scope.launch {
            EventBus.events.collect { event -> render(event) }
        }
    }

    override fun onResume() {
        super.onResume()
        syncListeningState()
    }

    override fun onStop() {
        uiScope?.cancel()
        uiScope = null
        super.onStop()
    }

    // ---------------------------------------------------------------
    // État de l'écoute
    // ---------------------------------------------------------------

    private fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun isListeningEnabled(): Boolean =
        prefs.getBoolean(KEY_LISTENING_ENABLED, true)

    /**
     * Aligne l'écran et le service sur la réalité : permission micro,
     * choix de l'utilisateur (écoute activée ou arrêtée).
     */
    private fun syncListeningState() {
        val micGranted = hasRecordAudioPermission()
        val enabled = isListeningEnabled()

        permissionBlock.visibility = if (micGranted) View.GONE else View.VISIBLE
        toggleButton.visibility = if (micGranted) View.VISIBLE else View.GONE
        toggleButton.text = if (enabled) "Arrêter l'écoute" else "Démarrer l'écoute"

        statusValue.text = when {
            !micGranted -> "Micro non autorisé"
            !enabled -> "Écoute arrêtée"
            startVoiceService() -> "Écoute active"
            else -> "Impossible de démarrer l'écoute"
        }
    }

    private fun onToggleClicked() {
        val enable = !isListeningEnabled()
        prefs.edit().putBoolean(KEY_LISTENING_ENABLED, enable).apply()
        if (!enable) stopVoiceService()
        syncListeningState()
    }

    /** @return true si le service a été démarré (ou tournait déjà). */
    private fun startVoiceService(): Boolean = try {
        ContextCompat.startForegroundService(
            this,
            Intent(this, VoiceForegroundService::class.java)
        )
        true
    } catch (e: Exception) {
        false
    }

    private fun stopVoiceService() {
        stopService(Intent(this, VoiceForegroundService::class.java))
    }

    private fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            )
        )
    }

    // ---------------------------------------------------------------
    // Affichage des événements
    // ---------------------------------------------------------------

    /**
     * Le "when" est volontairement exhaustif : si un nouvel événement est
     * ajouté à AppEvent, la compilation échoue ici et force à décider
     * s'il doit apparaître à l'écran.
     */
    private fun render(event: AppEvent) {
        when (event) {
            is AppEvent.RawTranscript -> {
                heardValue.text = "« ${event.text} »"
                errorBlock.visibility = View.GONE
            }
            is AppEvent.IntentRecognized ->
                understoodValue.text = describeIntent(event.intent.intentType, event.intent.confidence)
            is AppEvent.ConfirmationRequested ->
                outcomeValue.text = "Confirmation demandée : ${event.promptText}"
            is AppEvent.ConfirmationReceived ->
                outcomeValue.text = if (event.confirmed) "Confirmé : oui" else "Réponse : non, action annulée"
            is AppEvent.ActionExecuted ->
                outcomeValue.text = (if (event.success) "✓ " else "✗ ") + event.resultText
            is AppEvent.ErrorOccurred -> {
                errorValue.text = "${event.source} : ${event.message}"
                errorBlock.visibility = View.VISIBLE
            }
            is AppEvent.IntentResolved,
            is AppEvent.SpeakRequest -> Unit
        }
    }

    private fun describeIntent(type: IntentType, confidence: Float): String {
        if (type == IntentType.UNKNOWN) return "Phrase non comprise"
        return "${labelFor(type)} (${(confidence * 100).toInt()} %)"
    }

    /**
     * Libellés lisibles des intentions. Le "else" évite qu'une intention
     * ajoutée plus tard casse la compilation : elle s'affichera sous son
     * nom technique jusqu'à ce qu'on lui donne un libellé ici.
     */
    private fun labelFor(type: IntentType): String = when (type) {
        IntentType.CALL_CONTACT -> "Appeler un contact"
        IntentType.SEND_SMS -> "Envoyer un SMS"
        IntentType.SET_ALARM -> "Régler une alarme"
        IntentType.SET_REMINDER -> "Créer un rappel"
        IntentType.READ_LAST_MESSAGE -> "Lire le dernier message"
        IntentType.GET_TIME_DATE -> "Donner l'heure ou la date"
        IntentType.READ_DOCUMENT_OCR -> "Lire un document"
        IntentType.DESCRIBE_SCENE -> "Décrire ce qui m'entoure"
        IntentType.ADD_CALENDAR_EVENT -> "Ajouter un événement au calendrier"
        IntentType.EMERGENCY_TRIGGER -> "Urgence"
        else -> type.name
    }

    // ---------------------------------------------------------------
    // Construction de l'interface (100 % programmatique, sans XML)
    // ---------------------------------------------------------------

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun createLabel(label: String) = TextView(this).apply {
        text = label
        textSize = 14f
        alpha = 0.7f
        setPadding(0, dp(16), 0, dp(4))
    }

    private fun createValue(initial: String) = TextView(this).apply {
        text = initial
        textSize = 20f
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }

    private fun buildLayout(): ScrollView {
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.thedoer_logo)
            contentDescription = "Logo The Doer"
            adjustViewBounds = true
            layoutParams = LinearLayout.LayoutParams(dp(140), dp(140)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }

        statusValue = createValue("Démarrage…").apply { textSize = 24f }
        heardValue = createValue("—")
        understoodValue = createValue("—")
        outcomeValue = createValue("—")

        toggleButton = Button(this).apply {
            setOnClickListener { onToggleClicked() }
        }

        permissionBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            addView(TextView(context).apply {
                text = "Le micro est nécessaire pour que The Doer vous écoute. " +
                    "Autorisez-le dans les réglages de l'application."
                textSize = 18f
                setPadding(0, dp(16), 0, dp(8))
            })
            addView(Button(context).apply {
                text = "Ouvrir les réglages"
                setOnClickListener { openAppSettings() }
            })
        }

        errorValue = createValue("")
        errorBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            addView(createLabel("Problème"))
            addView(errorValue)
        }

        val examples = TextView(this).apply {
            text = "« Appelle maman »\n" +
                "« Quelle heure est-il ? »\n" +
                "« Mets une alarme à sept heures »\n" +
                "« Rappelle-moi demain matin »\n" +
                "« Urgence »"
            textSize = 16f
        }

        val voiceHint = TextView(this).apply {
            text = "Pas de voix de réponse ? Installez la voix française : " +
                "Réglages › Langues › Synthèse vocale."
            textSize = 14f
            alpha = 0.7f
            setPadding(0, dp(24), 0, 0)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            addView(logo)
            addView(createLabel("État"))
            addView(statusValue)
            addView(permissionBlock)
            addView(toggleButton)
            addView(createLabel("J'ai entendu"))
            addView(heardValue)
            addView(createLabel("J'ai compris"))
            addView(understoodValue)
            addView(createLabel("Résultat"))
            addView(outcomeValue)
            addView(errorBlock)
            addView(createLabel("Exemples de commandes"))
            addView(examples)
            addView(voiceHint)
        }

        return ScrollView(this).apply { addView(container) }
    }
}
