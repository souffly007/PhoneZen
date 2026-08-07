// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
package fr.bonobo.phonezen.service

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import fr.bonobo.phonezen.data.model.Contact
import fr.bonobo.phonezen.utils.PhoneUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.sqrt

class DrivingModeManager(private val context: Context) : SensorEventListener {

    companion object {
        private const val TAG                = "DrivingModeManager"
        private const val PREFS_NAME         = "driving_mode_prefs"
        private const val KEY_MANUAL_ACTIVE  = "manual_active"
        private const val KEY_AUTO_ENABLED   = "auto_enabled"
        private const val SPEED_THRESHOLD_KMH = 20f
        private const val ACCEL_THRESHOLD    = 11.5f
        private const val ACCEL_CONFIRM_COUNT = 5

        const val SMS_AUTO_REPLY =
            "Je conduis en ce moment, je vous rappelle dès que possible. 🚗"
    }

    // ─── État public ──────────────────────────────────────────────────────────

    private val _isDriving = MutableStateFlow(false)
    val isDriving: StateFlow<Boolean> = _isDriving

    private val _isAutoDetectionEnabled = MutableStateFlow(true)
    val isAutoDetectionEnabled: StateFlow<Boolean> = _isAutoDetectionEnabled

    // ─── Internes ─────────────────────────────────────────────────────────────

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null

    private var accelHighCount = 0
    private var isGpsMoving    = false
    private var isAccelMoving  = false

    // ─── Init ─────────────────────────────────────────────────────────────────

    init {
        val manualActive = prefs.getBoolean(KEY_MANUAL_ACTIVE, false)
        val autoEnabled  = prefs.getBoolean(KEY_AUTO_ENABLED,  true)
        _isDriving.value              = manualActive
        _isAutoDetectionEnabled.value = autoEnabled
        Log.d(TAG, "Init — manualActive=$manualActive autoEnabled=$autoEnabled")
    }

    // ─── API publique ─────────────────────────────────────────────────────────

    fun setManualDriving(active: Boolean) {
        Log.i(TAG, "Override manuel — conduite=$active")
        _isDriving.value = active
        prefs.edit().putBoolean(KEY_MANUAL_ACTIVE, active).apply()
    }

    fun setAutoDetectionEnabled(enabled: Boolean) {
        _isAutoDetectionEnabled.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_ENABLED, enabled).apply()
        if (enabled) startAutoDetection() else stopAutoDetection()
        Log.i(TAG, "Détection auto — enabled=$enabled")
    }

    fun startAutoDetection() {
        if (!_isAutoDetectionEnabled.value) return
        startAccelerometer()
        startGps()
        Log.d(TAG, "Détection automatique démarrée")
    }

    fun stopAutoDetection() {
        sensorManager.unregisterListener(this)
        locationCallback?.let { fusedLocationClient?.removeLocationUpdates(it) }
        Log.d(TAG, "Détection automatique arrêtée")
    }

    // ─── Exemption SMS ────────────────────────────────────────────────────────

    /**
     * Retourne true si le numéro est exempté du SMS automatique :
     * - contact favori OU dans la whitelist PhoneZen
     */
    fun isExemptFromAutoSms(
        number   : String,
        whitelist: Set<String>,
        favorites: List<Contact>
    ): Boolean {
        val normalized = PhoneUtils.normalizeNumber(number)

        // 1. Whitelist
        if (whitelist.contains(normalized)) {
            Log.d(TAG, "SMS auto ignoré — $number est dans la whitelist")
            return true
        }

        // 2. Favoris
        val isFav = favorites.any { contact ->
            contact.isFavorite &&
                    contact.phoneNumbers.any { num ->
                        PhoneUtils.normalizeNumber(num) == normalized
                    }
        }
        if (isFav) {
            Log.d(TAG, "SMS auto ignoré — $number est un favori")
            return true
        }

        return false
    }

    // ─── Accéléromètre ────────────────────────────────────────────────────────

    private fun startAccelerometer() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        } ?: Log.w(TAG, "Accéléromètre non disponible")
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

        if (magnitude > ACCEL_THRESHOLD) {
            accelHighCount++
            if (accelHighCount >= ACCEL_CONFIRM_COUNT && !isAccelMoving) {
                isAccelMoving = true
                Log.d(TAG, "Accéléromètre : mouvement détecté (magnitude=$magnitude)")
                evaluateDrivingState()
            }
        } else {
            accelHighCount = 0
            if (isAccelMoving) {
                isAccelMoving = false
                evaluateDrivingState()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ─── GPS ──────────────────────────────────────────────────────────────────

    private fun startGps() {
        try {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

            val request = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 5_000L
            ).setMinUpdateIntervalMillis(3_000L).build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { onLocationUpdate(it) }
                }
            }

            fusedLocationClient?.requestLocationUpdates(
                request,
                locationCallback!!,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission GPS manquante", e)
        }
    }

    private fun onLocationUpdate(location: Location) {
        val speedKmh  = location.speed * 3.6f
        val wasMoving = isGpsMoving
        isGpsMoving   = speedKmh >= SPEED_THRESHOLD_KMH
        if (isGpsMoving != wasMoving) {
            Log.d(TAG, "GPS : vitesse=${speedKmh}km/h → conduite=$isGpsMoving")
            evaluateDrivingState()
        }
    }

    // ─── Logique de décision ──────────────────────────────────────────────────

    private fun evaluateDrivingState() {
        if (!_isAutoDetectionEnabled.value) return
        if (prefs.getBoolean(KEY_MANUAL_ACTIVE, false)) return

        val detected = isGpsMoving || isAccelMoving
        if (_isDriving.value != detected) {
            _isDriving.value = detected
            Log.i(TAG, "État conduite → $detected (GPS=$isGpsMoving Accel=$isAccelMoving)")
        }
    }
}