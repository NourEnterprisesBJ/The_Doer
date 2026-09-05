package com.thedoer.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent as AndroidIntent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.thedoer.app.core.eventbus.AppEvent
import com.thedoer.app.core.eventbus.EventBus
import com.thedoer.app.core.models.Intent
import com.thedoer.app.core.resourcemanager.ResourceManager
import com.thedoer.app.engines.stt.SttEngine
import com.thedoer.app.engines.stt.SttEngineFactory
import com.thedoer.app.engines.tts.TtsEngine
import com.thedoer.app.engines.tts.TtsEngineFactory
import com.thedoer.app.execution.ActionExecutor
import com.thedoer.app.nlu.IntentClassifier
import com.thedoer.app.nlu.SlotResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Service au premier plan qui fait tourner tout le pipeline vocal en
 * continu, même quand l'app n'est pas au premier plan. START_STICKY
 * pour que le système le relance si tué (utile sur les appareils bas
 * de gamme qui tuent agressivement les process en arrière-plan).
 */
class VoiceForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "thedoer_voice_channel"
        private const val NOTIFICATION_ID = 1
    }

    private val job = SupervisorJob()
    private val scope = CoroutineScope(job)

    private lateinit var resourceManager: ResourceManager
    private lateinit var sttEngine: SttEngine
    private lateinit var ttsEngine: TtsEngine
    private lateinit var intentClassifier: IntentClassifier
    private lateinit var slotResolver: SlotResolver
    private lateinit var actionExecutor: ActionExecutor

    private var pendingConfirmation: Intent? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        resourceManager = ResourceManager(this)
        sttEngine = SttEngineFactory.create(this, resourceManager)
        ttsEngine = TtsEngineFactory.create(this, resourceManager)
        intentClassifier = IntentClassifier()
        slotResolver = SlotResolver()
        actionExecutor = ActionExecutor(this)

        scope.launch {
            sttEngine.init(this@VoiceForegroundService)
            ttsEngine.init(this@VoiceForegroundService)
            listenForConfirmations()
            startListeningLoop()
        }
    }

    override fun onStartCommand(intent: AndroidIntent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: AndroidIntent?): IBinder? = null

    override fun onDestroy() {
        sttEngine.release()
        ttsEngine.release()
        job.cancel()
        super.onDestroy()
    }

    /**
     * Boucle principale : écoute -> transcrit -> classifie -> résout
     * -> confirme si besoin -> exécute -> répond. Reboucle en continu
     * tant que le service tourne.
     */
    private suspend fun startListeningLoop() {
        while (job.isActive) {
            try {
                val transcript = sttEngine.transcribe()
                if (transcript.isNotBlank()) {
                    EventBus.publish(AppEvent.RawTranscript(transcript))
                    handleTranscript(transcript)
                }
            } catch (e: Exception) {
                EventBus.publish(AppEvent.ErrorOccurred("VoiceForegroundService", e.message ?: "Erreur STT inconnue"))
            }
        }
    }

    private suspend fun handleTranscript(transcript: String) {
        // Si une confirmation est en attente, ce transcript est la réponse
        // oui/non plutôt qu'une nouvelle commande.
        val awaiting = pendingConfirmation
        if (awaiting != null) {
            handleConfirmationResponse(awaiting, transcript)
            return
        }

        val intent = intentClassifier.classify(transcript)
        EventBus.publish(AppEvent.IntentRecognized(intent))

        val resolved = slotResolver.resolve(intent)
        EventBus.publish(AppEvent.IntentResolved(resolved))

        if (resolved.requiresConfirmation) {
            pendingConfirmation = resolved
            val prompt = "Tu veux vraiment que je fasse ça ? Dis oui ou non."
            EventBus.publish(AppEvent.ConfirmationRequested(resolved, prompt))
            ttsEngine.speak(prompt)
        } else {
            actionExecutor.execute(resolved)
        }
    }

    private suspend fun handleConfirmationResponse(intent: Intent, response: String) {
        val normalized = response.lowercase().trim()
        val confirmed = normalized.contains("oui")

        pendingConfirmation = null
        EventBus.publish(AppEvent.ConfirmationReceived(intent, confirmed))

        if (confirmed) {
            actionExecutor.execute(intent)
        } else {
            val message = "D'accord, j'annule."
            EventBus.publish(AppEvent.SpeakRequest(message))
            ttsEngine.speak(message)
        }
    }

    /**
     * Écoute les SpeakRequest publiés par ActionExecutor (résultats
     * d'actions) pour les prononcer, même quand ils ne viennent pas
     * directement de cette boucle (ex: résultat d'une action longue).
     */
    private fun listenForConfirmations() {
        scope.launch {
            EventBus.events.collect { event ->
                if (event is AppEvent.SpeakRequest) {
                    ttsEngine.speak(event.text)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "The Doer - Écoute vocale",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("The Doer")
        .setContentText("J'écoute...")
        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
        .setOngoing(true)
        .build()
}
