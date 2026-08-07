package fr.bonobo.phonezen.service

import android.content.Context
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import fr.bonobo.phonezen.utils.VoicemailNotificationHelper
import java.util.concurrent.Executors

/**
 * Écoute les messages vocaux via :
 * - MWI (Message Waiting Indicator) pour les opérateurs qui le supportent
 *
 * Note: L'accès direct au ContentProvider des messages vocaux n'est pas possible
 * pour les applications tierces. On utilise donc uniquement le MWI et les SMS.
 */
class VoicemailListener(private val context: Context) {

    private val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val TAG = "VoicemailListener"
    private var lastKnownState = false

    // ── API 31+ : TelephonyCallback ──────────────────────────────────────────
    private val modernCallback: TelephonyCallback? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            object : TelephonyCallback(),
                TelephonyCallback.MessageWaitingIndicatorListener {
                override fun onMessageWaitingIndicatorChanged(mwi: Boolean) {
                    Log.d(TAG, "Modern callback - MWI changed: $mwi")
                    handleMwi(mwi)
                }
            }
        } else null

    // ── API < 31 : PhoneStateListener (déprécié mais fonctionnel) ────────────
    @Suppress("DEPRECATION")
    private val legacyListener: PhoneStateListener? =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            object : PhoneStateListener() {
                override fun onMessageWaitingIndicatorChanged(mwi: Boolean) {
                    Log.d(TAG, "Legacy callback - MWI changed: $mwi")
                    handleMwi(mwi)
                }
            }
        } else null

    // ── Logique commune pour MWI ─────────────────────────────────────────────
    private fun handleMwi(mwi: Boolean) {
        if (mwi) {
            Log.d(TAG, "✅ MWI indique un nouveau message vocal")
            if (!lastKnownState) {
                VoicemailNotificationHelper.showVoicemailNotification(context)
                lastKnownState = true
            }
        } else {
            Log.d(TAG, "❌ MWI indique plus de message vocal")
            VoicemailNotificationHelper.cancelVoicemailNotification(context)
            lastKnownState = false
        }
    }

    // ── Vérification manuelle (appelée périodiquement) ───────────────────────
    fun checkCurrentMwi() {
        Log.d(TAG, "checkCurrentMwi: Vérification manuelle")
        // Note: On ne peut pas lire directement le MWI, on attend le callback
        // Les SMS feront le reste du travail
    }

    // ── Enregistrement ────────────────────────────────────────────────────────
    fun register() {
        try {
            // MWI Listener (si disponible)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && modernCallback != null) {
                tm.registerTelephonyCallback(
                    Executors.newSingleThreadExecutor(),
                    modernCallback
                )
                Log.d(TAG, "✅ Registered modern TelephonyCallback (MWI)")
            } else if (legacyListener != null) {
                @Suppress("DEPRECATION")
                tm.listen(
                    legacyListener,
                    PhoneStateListener.LISTEN_MESSAGE_WAITING_INDICATOR
                )
                Log.d(TAG, "✅ Registered legacy PhoneStateListener (MWI)")
            } else {
                Log.w(TAG, "⚠️ Aucun MWI Listener disponible - utilisation des SMS uniquement")
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "❌ SecurityException: Permission READ_PHONE_STATE manquante", e)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error registering VoicemailListener", e)
        }
    }

    // ── Désenregistrement ─────────────────────────────────────────────────────
    fun unregister() {
        try {
            // MWI Listener
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && modernCallback != null) {
                tm.unregisterTelephonyCallback(modernCallback)
                Log.d(TAG, "Unregistered modern TelephonyCallback")
            } else if (legacyListener != null) {
                @Suppress("DEPRECATION")
                tm.listen(legacyListener, PhoneStateListener.LISTEN_NONE)
                Log.d(TAG, "Unregistered legacy PhoneStateListener")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering VoicemailListener", e)
        }
    }
}