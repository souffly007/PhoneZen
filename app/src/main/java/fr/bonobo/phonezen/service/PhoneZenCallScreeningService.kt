package fr.bonobo.phonezen.service

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import fr.bonobo.phonezen.data.local.AppDatabase
import fr.bonobo.phonezen.data.local.BlockedCall
import fr.bonobo.phonezen.utils.PhoneUtils
import fr.bonobo.phonezen.utils.SpamDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PhoneZenCallScreeningService : CallScreeningService() {

    private val TAG = "PhoneZenService"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // On instancie le détecteur une seule fois
    private lateinit var detector: SpamDetector

    override fun onCreate() {
        super.onCreate()
        detector = SpamDetector(applicationContext)
    }

    override fun onScreenCall(callDetails: Call.Details) {
        // 1. Autoriser immédiatement les appels sortants
        if (callDetails.callDirection == Call.Details.DIRECTION_OUTGOING) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        // 2. Récupérer le numéro
        val number = callDetails.handle?.schemeSpecificPart ?: ""

        // 3. LA WHITELIST : Vérifier si c'est un contact connu
        // On ne bloque JAMAIS un contact enregistré
        val contactName = PhoneUtils.lookupContactName(applicationContext, number)
        if (contactName != null) {
            Log.d(TAG, "Appel autorisé : $number est un contact ($contactName)")
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        // 4. Analyser avec le SpamDetector
        val result = detector.analyze(number)

        val response = if (result.isSpam) {
            Log.w(TAG, "BLOCAGE : $number (${result.reason})")

            // Enregistrement asynchrone dans Room
            serviceScope.launch {
                try {
                    val db = AppDatabase.getDatabase(applicationContext)
                    db.blockedCallDao().insert(
                        BlockedCall(
                            number = number,
                            reason = result.reason ?: "Démarchage détecté",
                            riskLevel = result.riskLevel.name
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Erreur enregistrement BDD : ${e.message}")
                }
            }

            // Réponse de blocage stricte
            CallResponse.Builder()
                .setDisallowCall(true)      // Bloque l'appel
                .setRejectCall(true)        // Rejette l'appel (raccroche)
                .setSilenceCall(true)       // Coupe la sonnerie
                .setSkipCallLog(false)      // Laisse une trace dans l'historique Android
                .setSkipNotification(true)  // Ne pas afficher la notif système (PhoneZen s'en chargera via l'UI)
                .build()
        } else {
            Log.d(TAG, "Appel autorisé : $number ne semble pas être un spam")
            CallResponse.Builder().build()
        }

        respondToCall(callDetails, response)
    }
}