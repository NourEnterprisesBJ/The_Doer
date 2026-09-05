package com.thedoer.app.engines.stt

import android.content.Context
import com.thedoer.app.core.resourcemanager.DeviceTier
import com.thedoer.app.core.resourcemanager.ResourceManager

/**
 * Contrat commun à tous les moteurs de reconnaissance vocale.
 * VoskEngine (léger) et WhisperEngine (avancé) implémentent ceci ;
 * le reste de l'app ne connaît que cette interface, jamais
 * l'implémentation concrète.
 */
interface SttEngine {

    /** Prépare le moteur (charge le modèle, alloue les ressources). */
    suspend fun init(context: Context)

    /**
     * Transcrit l'audio en continu et retourne le texte final
     * reconnu. Suspend jusqu'à obtenir un résultat exploitable.
     */
    suspend fun transcribe(): String

    /** Libère le modèle et les ressources natives associées. */
    fun release()
}

object SttEngineFactory {

    fun create(context: Context, resourceManager: ResourceManager): SttEngine {
        return when (resourceManager.currentTier()) {
            DeviceTier.LOW, DeviceTier.MID -> VoskEngine()
            DeviceTier.HIGH -> WhisperEngine()
        }
    }
}
