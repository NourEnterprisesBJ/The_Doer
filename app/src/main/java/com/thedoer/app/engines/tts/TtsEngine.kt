package com.thedoer.app.engines.tts

import android.content.Context
import com.thedoer.app.core.resourcemanager.DeviceTier
import com.thedoer.app.core.resourcemanager.ResourceManager

/**
 * Contrat commun à tous les moteurs de synthèse vocale.
 * Le reste de l'application ne connaît que cette interface,
 * jamais l'implémentation concrète.
 */
interface TtsEngine {

    /** Prépare le moteur (charge la voix). */
    suspend fun init(context: Context)

    /** Prononce le texte donné. Suspend jusqu'à la fin de la lecture. */
    suspend fun speak(text: String)

    /** Libère les ressources associées. */
    fun release()
}

/**
 * Choisit le moteur de synthèse selon la capacité de l'appareil.
 *
 * Moteurs disponibles :
 *  - AndroidTtsEngine : moteur par défaut, hors-ligne, léger.
 *  - CoquiEngine      : réservé aux appareils HIGH (délègue pour l'instant
 *                       à AndroidTtsEngine).
 *  - PiperEngine      : non branché tant que l'intégration ONNX n'est pas
 *                       réalisée. À rebrancher ici le moment venu.
 */
object TtsEngineFactory {

    fun create(context: Context, resourceManager: ResourceManager): TtsEngine {
        return when (resourceManager.currentTier()) {
            DeviceTier.LOW, DeviceTier.MID -> AndroidTtsEngine()
            DeviceTier.HIGH -> CoquiEngine()
        }
    }
}
