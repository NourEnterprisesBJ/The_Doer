package com.thedoer.app.engines.tts

import android.content.Context

/**
 * Moteur TTS avancé (appareils HIGH), basé à terme sur Coqui TTS.
 *
 * STUB : l'intégration réelle est prévue en Phase 2. En attendant, il
 * délègue à AndroidTtsEngine pour garantir un retour vocal fonctionnel
 * sur les appareils haut de gamme.
 */
class CoquiEngine : TtsEngine {

    private val fallback = AndroidTtsEngine()

    override suspend fun init(context: Context) = fallback.init(context)

    override suspend fun speak(text: String) = fallback.speak(text)

    override fun release() = fallback.release()
}
