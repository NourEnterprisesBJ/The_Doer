package com.thedoer.app.core.eventbus

import com.thedoer.app.core.models.Intent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Tous les événements qui circulent dans l'app passent par ici.
 * Un seul bus in-process (Kotlin Flow) — pas de dépendance externe,
 * pas de réseau, tout reste sur l'appareil.
 */
sealed class AppEvent {
    data class RawTranscript(val text: String) : AppEvent()
    data class IntentRecognized(val intent: Intent) : AppEvent()
    data class IntentResolved(val intent: Intent) : AppEvent()
    data class ConfirmationRequested(val intent: Intent, val promptText: String) : AppEvent()
    data class ConfirmationReceived(val intent: Intent, val confirmed: Boolean) : AppEvent()
    data class ActionExecuted(val intent: Intent, val success: Boolean, val resultText: String) : AppEvent()
    data class SpeakRequest(val text: String) : AppEvent()
    data class ErrorOccurred(val source: String, val message: String) : AppEvent()
}

/**
 * Singleton global. replay=1 permet à un nouveau collecteur de recevoir
 * immédiatement le dernier événement (utile si un écran se relance après
 * rotation ou changement de process). extraBufferCapacity=32 évite de
 * bloquer l'émetteur si personne ne collecte encore.
 */
object EventBus {
    private val _events = MutableSharedFlow<AppEvent>(
        replay = 1,
        extraBufferCapacity = 32
    )

    val events: SharedFlow<AppEvent> = _events

    suspend fun publish(event: AppEvent) {
        _events.emit(event)
    }

    fun tryPublish(event: AppEvent): Boolean {
        return _events.tryEmit(event)
    }
}
