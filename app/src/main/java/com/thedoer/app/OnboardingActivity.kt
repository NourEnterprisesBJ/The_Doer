package com.thedoer.app

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Premier écran vu par l'utilisateur. Demande les permissions une
 * par une avec une explication en français simple avant chaque
 * demande système (meilleure acceptation que de tout demander d'un
 * coup). Layout 100% programmatique — pas de XML — pour rester
 * simple à maintenir depuis github.dev.
 */
class OnboardingActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "thedoer_prefs"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    private data class PermissionStep(
        val permission: String,
        val rationale: String
    )

    private lateinit var prefs: SharedPreferences
    private lateinit var titleView: TextView
    private lateinit var rationaleView: TextView
    private lateinit var continueButton: Button

    private val steps: List<PermissionStep> by lazy { buildPermissionSteps() }
    private var currentStepIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        if (prefs.getBoolean(KEY_ONBOARDING_DONE, false) || allPermissionsAlreadyGranted()) {
            goToMainActivity()
            return
        }

        setContentView(buildLayout())
        showStep(0)
    }

    private fun buildPermissionSteps(): List<PermissionStep> {
        val steps = mutableListOf(
            PermissionStep(
                Manifest.permission.RECORD_AUDIO,
                "The Doer a besoin du micro pour t'écouter et comprendre tes demandes vocales."
            ),
            PermissionStep(
                Manifest.permission.READ_CONTACTS,
                "The Doer a besoin d'accéder à tes contacts pour appeler ou envoyer un message à quelqu'un quand tu le demandes."
            ),
            PermissionStep(
                Manifest.permission.CALL_PHONE,
                "The Doer a besoin de cette permission pour passer des appels à ta demande."
            ),
            PermissionStep(
                Manifest.permission.SEND_SMS,
                "The Doer a besoin de cette permission pour envoyer des messages à ta demande."
            ),
            PermissionStep(
                Manifest.permission.READ_SMS,
                "The Doer a besoin de lire tes messages pour pouvoir te lire ton dernier message reçu."
            ),
            PermissionStep(
                Manifest.permission.CAMERA,
                "The Doer a besoin de la caméra pour lire des documents ou te décrire ce qui t'entoure."
            ),
            PermissionStep(
                Manifest.permission.WRITE_CALENDAR,
                "The Doer a besoin d'accéder à ton calendrier pour y ajouter des événements à ta demande."
            ),
            PermissionStep(
                Manifest.permission.ACCESS_FINE_LOCATION,
                "The Doer a besoin de ta position pour pouvoir la partager en cas d'urgence."
            )
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            steps.add(
                PermissionStep(
                    Manifest.permission.POST_NOTIFICATIONS,
                    "The Doer a besoin d'afficher une notification pendant qu'il t'écoute en arrière-plan."
                )
            )
        }

        return steps
    }

    private fun allPermissionsAlreadyGranted(): Boolean {
        return steps.all { step ->
            ContextCompat.checkSelfPermission(this, step.permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun showStep(index: Int) {
        if (index >= steps.size) {
            prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
            goToMainActivity()
            return
        }

        currentStepIndex = index
        val step = steps[index]

        if (ContextCompat.checkSelfPermission(this, step.permission) == PackageManager.PERMISSION_GRANTED) {
            showStep(index + 1)
            return
        }

        titleView.text = "Étape ${index + 1} sur ${steps.size}"
        rationaleView.text = step.rationale
        continueButton.text = "Continuer"
    }

    private fun requestCurrentPermission() {
        val step = steps[currentStepIndex]
        ActivityCompatRequestPermission(step.permission)
    }

    private fun ActivityCompatRequestPermission(permission: String) {
        androidx.core.app.ActivityCompat.requestPermissions(
            this,
            arrayOf(permission),
            PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // Qu'elle soit accordée ou refusée, on avance : on ne bloque
            // jamais l'utilisateur sur une permission refusée, certaines
            // fonctionnalités seront juste indisponibles.
            showStep(currentStepIndex + 1)
        }
    }

    private fun goToMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun buildLayout(): ScrollView {
        val padding = (24 * resources.displayMetrics.density).toInt()

        titleView = TextView(this).apply {
            textSize = 14f
            setPadding(0, 0, 0, padding / 2)
        }

        rationaleView = TextView(this).apply {
            textSize = 20f
            setPadding(0, 0, 0, padding)
        }

        continueButton = Button(this).apply {
            text = "Continuer"
            setOnClickListener { requestCurrentPermission() }
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(padding, padding, padding, padding)
            addView(titleView)
            addView(rationaleView)
            addView(continueButton)
        }

        return ScrollView(this).apply {
            addView(container)
        }
    }
}
