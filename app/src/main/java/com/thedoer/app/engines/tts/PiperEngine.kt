package com.thedoer.app.engines.tts

import android.content.Context

/**
 * Moteur TTS par défaut (tiers LOW/MID), basé sur Piper (ONNX).
 *
 * STUB — non implémenté en Sprint 1. L'intégration réelle nécessite :
 *   - ONNX Runtime Mobile (dépendance à ajouter dans app/build.gradle.kts)
 *   - un modèle de voix Piper français (.onnx + .onnx.json)
 *     dans assets/piper/
 *
 * TODO (Sprint 1 fin / Sprint 2) : implémenter réellement une fois
 * le modèle de voix choisi et téléchargé (même procédure manuelle
 * que pour Vosk : téléchargement + upload GitHub par dossier).
 */
class PiperEngine : TtsEngine {

    override suspend fun init(context: Context) {
        // TODO: charger le modèle ONNX Piper ici.
    }

    override suspend fun speak(text: String) {
        // TODO: appeler ONNX Runtime pour synthétiser et jouer l'audio.
        // Pour l'instant, ne fait rien (silence) — à remplacer avant
        // la fin du Sprint 1 pour avoir un retour vocal réel.
    }

    override fun release() {
        // TODO: libérer la session ONNX.
    }
}
