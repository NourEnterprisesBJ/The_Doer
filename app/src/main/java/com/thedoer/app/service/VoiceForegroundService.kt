package com.thedoer.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent as AndroidIntent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.thedoer.app.core.eventbus.AppEvent
import com.thedoer.app.core.eventbus.EventBus
import com.thedoer.app.core.models.Intent
import com.thedoer.app.core.models.IntentType
import com.thedoer.app.core.resourcemanager.ResourceManager
import com.thedoer.app.engines.stt.SttEngine
import com.thedoer.app.engines.stt.SttEngineFactory
import com.thedoer.app.engines.tts.TtsEngine
import com.thedoer.app.engines.tts.TtsEngineFactory
import com.thedoer.app.execution.ActionExecutor
import com.thedoer.app.nlu.IntentClassifier
import com.thedoer.app.nlu.SlotResolver
import java.text.Normalizer
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Service au premier plan qui fait tourner le pipeline vocal en continu :
 * écoute -> classification -> résolution -> confirmation -> exécution -> voix.
 *
 * Principes de conception :
 *  - UN SEUL chemin de parole : tout ce qui doit être dit passe par un
 *    AppEvent.SpeakRequest, consommé à un seul endroit (speak()).
 *    Cela évite les phrases prononcées en double.
 *  - L'assistant ne doit pas s'entendre lui-même : l'écoute attend la fin
 *    de la parole, et toute transcription captée pendant (ou juste après)
 *    une phrase de l'assistant est écartée.
 *  - Aucune erreur ne doit tuer le service : chaque étape est isolée, les
 *    erreurs sont publiées sur le bus pour être affichées à l'écran.
 */
class VoiceForegroundService : Service() {

    private companion object {
        const val TAG = "VoiceForegroundService"
        const val CHANNEL_ID = "thedoer_voice_channel"
        const val NOTIFICATION_ID = 1

        /** Délai laissé après une phrase de l'assistant pour que l'écho s'éteigne. */
        const val ECHO_GUARD_MS = 600L

        /** Au-delà, une confirmation restée sans réponse est abandonnée. */
        const val CONFIRMATION_TIMEOUT_MS = 20_000L

        /** Pause avant de réessayer après une erreur de reconnaissance. */
        const val ERROR_BACKOFF_MS = 1_000L

        val YES_WORDS = setOf("oui", "ouais", "ok", "okay", "accord", "confirme", "confirmer", "valide")
        val NO_WORDS = setOf("non", "annule", "annuler", "stop", "arrete")

        val NON_LETTERS = Regex("[^a-z]+")
        val COMBINING_MARKS = Regex("\\p{Mn}")
    }

    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(
        serviceJob + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            Log.e(TAG, "Erreur non gérée dans le service vocal", error)
        }
    )

    private lateinit var sttEngine: SttEngine
    private lateinit var ttsEngine: TtsEngine
    private lateinit var intentClassifier: IntentClassifier
    private lateinit var slotResolver: SlotResolver
    private lateinit var actionExecutor: ActionExecutor

    // --- État de la parole (anti-écho) ---
    private val speechCounter = AtomicLong(0)
    @Volatile private var isSpeaking = false
    @Volatile private var lastSpeechEndMs = 0L
    @Volatile private var ttsReady = false

    // --- État du dialogue de confirmation (touché uniquement par la boucle d'écoute) ---
    private var pending: PendingConfirmation? = null

    private data class PendingConfirmation(
        val intent: Intent,
        val requestedAtMs: Long = SystemClock.elapsedRealtime()
    ) {
        fun isExpired(): Boolean =
            SystemClock.elapsedRealtime() - requestedAtMs > CONFIRMATION_TIMEOUT_MS
    }

    private enum class Answer { YES, NO, UNCLEAR }

    // ---------------------------------------------------------------
    // Cycle de vie
    // ---------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } catch (e: Exception) {
            // Ex. : démarrage refusé par le système depuis l'arrière-plan.
            Log.e(TAG, "Impossible de passer au premier plan", e)
            stopSelf()
            return
        }

        val resourceManager = ResourceManager(this)
        sttEngine = SttEngineFactory.create(this, resourceManager)
        ttsEngine = TtsEngineFactory.create(this, resourceManager)
        intentClassifier = IntentClassifier()
        slotResolver = SlotResolver(this)
        actionExecutor = ActionExecutor(this)

        scope.launch { runPipeline() }
    }

    override fun onStartCommand(intent: AndroidIntent?, flags: Int, startId: Int): Int =
        START_STICKY

    override fun onBind(intent: AndroidIntent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        if (::sttEngine.isInitialized) sttEngine.release()
        if (::ttsEngine.isInitialized) ttsEngine.release()
        super.onDestroy()
    }

    // ---------------------------------------------------------------
    // Pipeline
    // ---------------------------------------------------------------

    private suspend fun runPipeline() {
        // Sans voix de sortie, l'assistant reste utilisable : l'écran affiche tout.
        ttsReady = attempt("Synthèse vocale") { ttsEngine.init(this@VoiceForegroundService) }

        val sttReady = attempt("Reconnaissance vocale") { sttEngine.init(this@VoiceForegroundService) }
        if (!sttReady) {
            stopSelf()
            return
        }

        launchSpeechCollector()
        listenLoop()
    }

    private suspend fun listenLoop() {
        while (currentCoroutineContext().isActive) {
            awaitSpeechIdle()
            val speechesBefore = speechCounter.get()

            val transcript = try {
                sttEngine.transcribe()
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                reportError("Reconnaissance vocale", e)
                delay(ERROR_BACKOFF_MS)
                continue
            }

            if (transcript.isBlank() || heardOwnVoice(speechesBefore)) continue

            EventBus.publish(AppEvent.RawTranscript(transcript))
            attempt("Traitement de la commande") { handleTranscript(transcript) }
        }
    }

    private suspend fun handleTranscript(transcript: String) {
        val awaiting = pending
        pending = null

        if (awaiting != null && !awaiting.isExpired()) {
            when (parseAnswer(transcript)) {
                Answer.YES -> {
                    EventBus.publish(AppEvent.ConfirmationReceived(awaiting.intent, true))
                    actionExecutor.execute(awaiting.intent)
                    return
                }
                Answer.NO -> {
                    EventBus.publish(AppEvent.ConfirmationReceived(awaiting.intent, false))
                    say("D'accord, j'annule.")
                    return
                }
                Answer.UNCLEAR -> {
                    if (intentClassifier.classify(transcript).intentType == IntentType.UNKNOWN) {
                        pending = awaiting
                        say("Je n'ai pas compris. Dis oui ou non.")
                        return
                    }
                    // Nouvelle commande reconnue : l'ancienne demande est abandonnée.
                }
            }
        }

        processCommand(transcript)
    }

    private suspend fun processCommand(transcript: String) {
        val recognized = intentClassifier.classify(transcript)
        EventBus.publish(AppEvent.IntentRecognized(recognized))

        // Écoute permanente : une phrase qui n'est pas une commande ne
        // déclenche aucune réponse vocale, sinon l'assistant répondrait à
        // toute conversation ambiante. L'écran l'indique ("Phrase non comprise").
        if (recognized.intentType == IntentType.UNKNOWN) return

        val resolved = slotResolver.resolve(recognized)
        EventBus.publish(AppEvent.IntentResolved(resolved))

        // Inutile de demander confirmation si un élément manque :
        // ActionExecutor expliquera directement ce qui n'a pas été compris.
        val readyToConfirm = resolved.requiresConfirmation && resolved.slots.values.all { it.resolved }

        if (readyToConfirm) {
            pending = PendingConfirmation(resolved)
            val prompt = confirmationPrompt(resolved)
            EventBus.publish(AppEvent.ConfirmationRequested(resolved, prompt))
            say(prompt)
        } else {
            actionExecutor.execute(resolved)
        }
    }

    // ---------------------------------------------------------------
    // Parole et anti-écho
    // ---------------------------------------------------------------

    /** Demande à l'assistant de parler (via le bus : chemin unique). */
    private suspend fun say(text: String) = EventBus.publish(AppEvent.SpeakRequest(text))

    private fun launchSpeechCollector() {
        // Le cache de relecture du bus contient le dernier événement émis
        // AVANT ce démarrage : on ne le prononce pas une seconde fois.
        val stale = EventBus.events.replayCache.lastOrNull()
        scope.launch {
            EventBus.events.collect { event ->
                if (event === stale || event !is AppEvent.SpeakRequest) return@collect
                speak(event.text)
            }
        }
    }

    private suspend fun speak(text: String) {
        if (!ttsReady) return
        speechCounter.incrementAndGet()
        isSpeaking = true
        try {
            ttsEngine.speak(text)
        } finally {
            isSpeaking = false
            lastSpeechEndMs = SystemClock.elapsedRealtime()
        }
    }

    private suspend fun awaitSpeechIdle() {
        while (isSpeaking || SystemClock.elapsedRealtime() - lastSpeechEndMs < ECHO_GUARD_MS) {
            delay(50)
        }
    }

    /**
     * Vrai si l'assistant a parlé pendant la capture (ou vient de finir) :
     * la transcription contient alors probablement sa propre voix.
     */
    private fun heardOwnVoice(speechesBefore: Long): Boolean =
        isSpeaking ||
            speechCounter.get() != speechesBefore ||
            SystemClock.elapsedRealtime() - lastSpeechEndMs < ECHO_GUARD_MS

    // ---------------------------------------------------------------
    // Dialogue de confirmation
    // ---------------------------------------------------------------

    private fun confirmationPrompt(intent: Intent): String {
        // On annonce le nom prononcé ("maman"), jamais le numéro résolu.
        val target = intent.slots["target"]?.value
        val time = intent.slots["time"]?.let { it.resolvedValue ?: it.value }

        val action = when (intent.intentType) {
            IntentType.CALL_CONTACT -> target?.let { "appeler $it" } ?: "passer un appel"
            IntentType.SEND_SMS -> target?.let { "envoyer un message à $it" } ?: "envoyer un message"
            IntentType.SET_ALARM -> time?.let { "régler une alarme à $it" } ?: "régler une alarme"
            IntentType.SET_REMINDER -> time?.let { "créer un rappel pour $it" } ?: "créer un rappel"
            IntentType.ADD_CALENDAR_EVENT -> "ajouter l'événement au calendrier"
            else -> "faire cette action"
        }
        return "Je vais $action. C'est bien ça ? Dis oui ou non."
    }

    /** En cas de doute, "non" l'emporte : on ne déclenche jamais une action par erreur. */
    private fun parseAnswer(text: String): Answer {
        val words = normalize(text).split(NON_LETTERS).filter { it.isNotEmpty() }.toSet()
        return when {
            words.any { it in NO_WORDS } -> Answer.NO
            words.any { it in YES_WORDS } -> Answer.YES
            else -> Answer.UNCLEAR
        }
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase().trim(), Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")

    // ---------------------------------------------------------------
    // Gestion des erreurs
    // ---------------------------------------------------------------

    /**
     * Exécute une étape en isolant ses erreurs : elles sont publiées sur le
     * bus au lieu de tuer le service.
     *
     * ensureActive() distingue l'arrêt réel du service (on propage) d'une
     * annulation interne à un moteur : VoskEngine signale un modèle absent
     * par cancel(), qui lève une CancellationException alors que le service
     * est toujours vivant. Ce cas est traité comme une vraie erreur.
     *
     * @return true si l'étape s'est bien déroulée.
     */
    private suspend fun attempt(source: String, block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (e: Exception) {
        currentCoroutineContext().ensureActive()
        reportError(source, e)
        false
    }

    private suspend fun reportError(source: String, error: Throwable) {
        val detail = error.cause?.message ?: error.message ?: error.javaClass.simpleName
        Log.e(TAG, "$source : $detail", error)
        EventBus.publish(AppEvent.ErrorOccurred(source, detail))
    }

    // ---------------------------------------------------------------
    // Notification
    // ---------------------------------------------------------------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "The Doer - Écoute vocale",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("The Doer")
            .setContentText("J'écoute…")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
}
