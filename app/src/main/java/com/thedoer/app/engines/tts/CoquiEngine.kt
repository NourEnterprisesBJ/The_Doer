package com.thedoer.app.engines.tts

import android.content.Context

/**
 * Moteur TTS avancé (tiers HIGH), basé sur Coqui TTS.
 *
 * STUB — non implémenté avant Phase 2. Nécessite l'intégration
 * d'un runtime Coqui adapté mobile (plus lourd que Piper, justifie
 * la réservation aux appareils HIGH uniquement).
 *
 * En attendant, on délègue à PiperEngine pour garantir un retour
 * vocal fonctionnel même sur les appareils haut de gamme.
 */
class CoquiEngine : TtsEngine {

    private val fallback = PiperEngine()

    override suspend fun init(context: Context) {
        fallback.init(context)
    }

    override suspend fun speak(text: String) {
        fallback.speak(text)
    }

    override fun release() {
        fallback.release()
    }
}
