package com.thedoer.app.engines.tts

import android.content.Context
import com.thedoer.app.core.resourcemanager.DeviceTier
import com.thedoer.app.core.resourcemanager.ResourceManager

/**
 * Contrat commun à tous les moteurs de synthèse vocale.
 * Même principe que SttEngine : le reste de l'app ne connaît que
 * cette interface, jamais Piper ou Coqui directement.
 */
interface TtsEngine {

    /** Prépare le moteur (charge le modèle de voix). */
    suspend fun init(context: Context)

    /** Prononce le texte donné. Suspend jusqu'à la fin de la lecture. */
    suspend fun speak(text: String)

    /** Libère le modèle et les ressources natives associées. */
    fun release()
}

object TtsEngineFactory {

    fun create(context: Context, resourceManager: ResourceManager): TtsEngine {
        return when (resourceManager.currentTier()) {
            DeviceTier.LOW, DeviceTier.MID -> PiperEngine()
            DeviceTier.HIGH -> CoquiEngine()
        }
    }
}
