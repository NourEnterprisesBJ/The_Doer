package com.thedoer.app.engines.stt

import android.content.Context
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import kotlin.coroutines.resume

/**
 * Moteur STT par défaut (tiers LOW/MID). Utilise Vosk avec un petit
 * modèle français embarqué dans les assets (voir
 * assets/model-vosk/PLACE_MODEL_HERE.txt).
 *
 * Fonctionne 100% hors-ligne, pas d'appel réseau.
 */
class VoskEngine : SttEngine {

    private var model: Model? = null
    private var speechService: SpeechService? = null

    override suspend fun init(context: Context) {
        model = suspendCancellableCoroutine { cont ->
            StorageService.unpack(
                context,
                "model-vosk",
                "model",
                { unpackedModel: Model -> cont.resume(unpackedModel) },
                { exception: java.io.IOException ->
                    cont.cancel(exception)
                }
            )
        }
    }

    override suspend fun transcribe(): String {
        val currentModel = model
            ?: throw IllegalStateException("VoskEngine.init() doit être appelé avant transcribe()")

        return suspendCancellableCoroutine { cont ->
            val recognizer = Recognizer(currentModel, 16000.0f)

            val listener = object : RecognitionListener {
                override fun onResult(hypothesis: String?) {
                    val text = extractText(hypothesis)
                    if (text.isNotBlank()) {
                        speechService?.stop()
                        if (cont.isActive) cont.resume(text)
                    }
                }

                override fun onFinalResult(hypothesis: String?) {
                    val text = extractText(hypothesis)
                    speechService?.stop()
                    if (cont.isActive) cont.resume(text)
                }

                override fun onPartialResult(hypothesis: String?) {
                    // Non utilisé en Sprint 1 — pourra alimenter un
                    // affichage temps réel plus tard.
                }

                override fun onError(exception: Exception?) {
                    if (cont.isActive) cont.cancel(exception ?: Exception("Erreur Vosk inconnue"))
                }

                override fun onTimeout() {
                    speechService?.stop()
                    if (cont.isActive) cont.resume("")
                }
            }

            try {
                speechService = SpeechService(recognizer, 16000.0f)
                speechService?.startListening(listener)
            } catch (e: Exception) {
                if (cont.isActive) cont.cancel(e)
            }

            cont.invokeOnCancellation {
                speechService?.stop()
                speechService?.shutdown()
            }
        }
    }

    override fun release() {
        speechService?.stop()
        speechService?.shutdown()
        speechService = null
        model?.close()
        model = null
    }

    private fun extractText(hypothesisJson: String?): String {
        if (hypothesisJson.isNullOrBlank()) return ""
        return try {
            JSONObject(hypothesisJson).optString("text", "").trim()
        } catch (e: Exception) {
            ""
        }
    }
}
