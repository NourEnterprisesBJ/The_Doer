package com.thedoer.app.core.resourcemanager

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager

/**
 * Le principe central de l'architecture : chaque module a un moteur
 * léger par défaut + un moteur avancé activé automatiquement selon
 * la capacité réelle de l'appareil. ResourceManager décide quel
 * "tier" est actif, à tout moment (peut changer en cours de session
 * si la batterie chute par exemple).
 */
enum class DeviceTier {
    LOW,   // RAM faible et/ou batterie critique -> moteurs légers uniquement
    MID,   // capacité correcte -> moteurs légers, éligible avancé au cas par cas
    HIGH   // capacité confortable -> moteurs avancés activables
}

class ResourceManager(private val context: Context) {

    fun currentTier(): DeviceTier {
        val availableRamMb = getAvailableRamMb()
        val batteryPercent = getBatteryPercent()

        // La batterie critique prime sur tout : on rétrograde de force
        // en LOW pour économiser l'énergie, peu importe la RAM.
        if (batteryPercent in 0..14) {
            return DeviceTier.LOW
        }

        return when {
            availableRamMb < 1500 -> DeviceTier.LOW
            availableRamMb < 3000 -> DeviceTier.MID
            else -> DeviceTier.HIGH
        }
    }

    fun canRunAdvancedEngine(): Boolean {
        return currentTier() == DeviceTier.HIGH
    }

    private fun getAvailableRamMb(): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        return memInfo.availMem / (1024 * 1024)
    }

    private fun getBatteryPercent(): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
}
