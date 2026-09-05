package com.thedoer.app.engines.stt

import android.content.Context

/**
 * Moteur STT avancé (tiers HIGH), basé sur whisper.cpp.
 *
 * STUB — non implémenté en Sprint 1. L'intégration réelle nécessite :
 *   - la lib whisper.cpp compilée en natif (JNI/NDK)
 *   - un modèle Whisper quantisé (ex: ggml-tiny ou ggml-base, .bin)
 *     dans assets/model-whisper/
 *   - un binding Kotlin/JNI pour appeler le moteur C++
 *
 * TODO (Sprint 5 / Phase 2) : implémenter réellement, une fois le
 * pipeline de compilation NDK ajouté au workflow GitHub Actions.
 *
 * En attendant, on délègue à VoskEngine pour ne jamais laisser
 * l'utilisateur sans reconnaissance vocale fonctionnelle, même sur
 * un appareil haut de gamme.
 */
class WhisperEngine : SttEngine {

    private val fallback = VoskEngine()

    override suspend fun init(context: Context) {
        // TODO Phase 2: charger le modèle Whisper natif ici.
        fallback.init(context)
    }

    override suspend fun transcribe(): String {
        // TODO Phase 2: appeler whisper.cpp via JNI ici.
        return fallback.transcribe()
    }

    override fun release() {
        fallback.release()
    }
}
