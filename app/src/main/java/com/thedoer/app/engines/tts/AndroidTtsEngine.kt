package com.thedoer.app.engines.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Moteur de synthèse vocale basé sur le TextToSpeech natif d'Android.
 *
 * Fonctionne 100 % hors-ligne dès que la voix française est installée
 * sur l'appareil (Réglages > Langues > Synthèse vocale). Sert de moteur
 * par défaut tant que Piper (ONNX) n'est pas réellement intégré.
 *
 * Contrat de robustesse : speak() ne lève jamais d'exception si la voix
 * est indisponible. Il journalise et rend la main, pour ne jamais
 * interrompre la boucle vocale du service.
 */
class AndroidTtsEngine : TtsEngine {

    private companion object {
        const val TAG = "AndroidTtsEngine"
    }

    private var tts: TextToSpeech? = null

    @Volatile
    private var frenchVoiceAvailable = false

    /** Énoncés en cours de lecture, débloqués par le listener à la fin. */
    private val pendingUtterances = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

    /** Permet à l'interface d'avertir l'utilisateur si la voix manque. */
    val isFrenchVoiceAvailable: Boolean
        get() = frenchVoiceAvailable

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) = completeUtterance(utteranceId)

        override fun onStop(utteranceId: String?, interrupted: Boolean) =
            completeUtterance(utteranceId)

        override fun onError(utteranceId: String?, errorCode: Int) =
            completeUtterance(utteranceId)

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = completeUtterance(utteranceId)
    }

    override suspend fun init(context: Context) {
        if (tts != null) return

        val initStatus = CompletableDeferred<Int>()
        val engine = TextToSpeech(context.applicationContext) { status ->
            initStatus.complete(status)
        }
        tts = engine

        if (initStatus.await() != TextToSpeech.SUCCESS) {
            release()
            throw IllegalStateException(
                "Le moteur de synthèse vocale Android n'a pas pu démarrer."
            )
        }

        engine.setOnUtteranceProgressListener(progressListener)

        val languageStatus = engine.setLanguage(Locale.FRENCH)
        frenchVoiceAvailable = languageStatus != TextToSpeech.LANG_MISSING_DATA &&
            languageStatus != TextToSpeech.LANG_NOT_SUPPORTED

        if (!frenchVoiceAvailable) {
            Log.w(TAG, "Voix française non installée : aucune synthèse vocale possible.")
        }
    }

    override suspend fun speak(text: String) {
        val engine = tts
        if (engine == null || !frenchVoiceAvailable || text.isBlank()) {
            Log.w(TAG, "Synthèse ignorée (moteur non prêt, voix absente ou texte vide).")
            return
        }

        val utteranceId = UUID.randomUUID().toString()
        val finished = CompletableDeferred<Unit>()
        pendingUtterances[utteranceId] = finished

        try {
            val status = engine.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
            if (status == TextToSpeech.ERROR) {
                Log.e(TAG, "Le moteur a refusé la demande de synthèse.")
                return
            }
            finished.await()
        } finally {
            pendingUtterances.remove(utteranceId)
        }
    }

    override fun release() {
        pendingUtterances.values.forEach { it.complete(Unit) }
        pendingUtterances.clear()
        tts?.stop()
        tts?.shutdown()
        tts = null
        frenchVoiceAvailable = false
    }

    private fun completeUtterance(utteranceId: String?) {
        utteranceId?.let { pendingUtterances[it]?.complete(Unit) }
    }
}
