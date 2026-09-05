package com.thedoer.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Activité principale, lancée après l'onboarding. Son rôle est
 * volontairement minimal : vérifier que le micro est bien accessible
 * puis démarrer le service vocal en arrière-plan. Toute l'intelligence
 * réelle vit dans VoiceForegroundService, pas ici — l'UI visible sert
 * surtout de filet de sécurité et de point d'entrée.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildLayout())

        if (hasRecordAudioPermission()) {
            startVoiceService()
        } else {
            // Filet de sécurité : si on arrive ici sans la permission
            // micro (ex: onboarding interrompu ou permission révoquée
            // depuis les réglages système), on retourne à l'onboarding
            // plutôt que de démarrer un service qui plantera.
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
        }
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startVoiceService() {
        val serviceIntent = Intent(this, com.thedoer.app.service.VoiceForegroundService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun buildLayout(): LinearLayout {
        val statusView = TextView(this).apply {
            text = "The Doer est actif et t'écoute en arrière-plan."
            textSize = 18f
            gravity = Gravity.CENTER
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            addView(statusView)
        }
    }
}
