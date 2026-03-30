package fr.bonobo.phonezen.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import fr.bonobo.phonezen.data.model.ReportedNumber
import fr.bonobo.phonezen.utils.PhoneUtils
import kotlinx.coroutines.tasks.await
import android.util.Log

/**
 * Repository pour la liste participative des numéros indésirables.
 * Toutes les opérations sont suspending (appelables depuis une coroutine).
 */
class ReportRepository {

    private val db         = FirebaseFirestore.getInstance()
    private val collection = db.collection(ReportedNumber.COLLECTION)

    // ── Cache local pour éviter trop d'appels Firestore ──
    private val cache = mutableMapOf<String, ReportedNumber?>()

    /**
     * Signale un numéro comme indésirable.
     * - Si le numéro existe déjà → incrémente le compteur + remet le TTL à 40 jours
     * - Si nouveau → crée le document
     */
    suspend fun reportNumber(number: String, tag: String = "indésirable"): Result<Unit> {
        return try {
            val normalized = PhoneUtils.normalizeNumber(number)
            val docRef     = collection.document(normalized)
            val snapshot   = docRef.get().await()
            val now        = Timestamp.now()
            val expiresAt  = Timestamp(now.seconds + ReportedNumber.TTL_SECONDS, 0)

            if (snapshot.exists()) {
                // Numéro déjà signalé → incrémente + remet le TTL (glissant)
                docRef.update(
                    mapOf(
                        ReportedNumber.FIELD_REPORTS       to FieldValue.increment(1),
                        ReportedNumber.FIELD_LAST_REPORTED to now,
                        ReportedNumber.FIELD_EXPIRES_AT    to expiresAt,
                        ReportedNumber.FIELD_TAGS          to FieldValue.arrayUnion(tag)
                    )
                ).await()
            } else {
                // Nouveau numéro signalé
                val reported = ReportedNumber(
                    number       = normalized,
                    reports      = 1,
                    lastReported = now,
                    expiresAt    = expiresAt,
                    tags         = listOf(tag)
                )
                docRef.set(reported.toMap()).await()
            }

            // Invalide le cache
            cache.remove(normalized)
            Result.success(Unit)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Vérifie si un numéro est signalé par la communauté.
     * Retourne null si inconnu, ou le ReportedNumber s'il existe.
     * Utilise un cache pour ne pas interroger Firestore à chaque appel entrant.
     */
    suspend fun checkNumber(number: String): ReportedNumber? {
        return try {
            val normalized = PhoneUtils.normalizeNumber(number)

            // Retourne le cache si disponible
            if (cache.containsKey(normalized)) return cache[normalized]

            val snapshot = collection.document(normalized).get().await()
            val result   = if (snapshot.exists()) {
                ReportedNumber(
                    number       = snapshot.getString(ReportedNumber.FIELD_NUMBER) ?: normalized,
                    reports      = snapshot.getLong(ReportedNumber.FIELD_REPORTS) ?: 0,
                    lastReported = snapshot.getTimestamp(ReportedNumber.FIELD_LAST_REPORTED) ?: Timestamp.now(),
                    expiresAt    = snapshot.getTimestamp(ReportedNumber.FIELD_EXPIRES_AT) ?: Timestamp.now(),
                    tags         = (snapshot.get(ReportedNumber.FIELD_TAGS) as? List<*>)
                        ?.filterIsInstance<String>() ?: emptyList()
                )
            } else null

            cache[normalized] = result
            result

        } catch (e: Exception) {
            null
        }
    }

    /**
     * Récupère les numéros les plus signalés (top 50).
     * Utile pour un écran "numéros dangereux" futur.
     */
    suspend fun getTopReported(limit: Long = 50): List<ReportedNumber> {
        return try {
            val snapshot = collection
                .orderBy(ReportedNumber.FIELD_REPORTS, com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                if (!doc.exists()) return@mapNotNull null
                ReportedNumber(
                    number       = doc.getString(ReportedNumber.FIELD_NUMBER) ?: "",
                    reports      = doc.getLong(ReportedNumber.FIELD_REPORTS) ?: 0,
                    lastReported = doc.getTimestamp(ReportedNumber.FIELD_LAST_REPORTED) ?: Timestamp.now(),
                    expiresAt    = doc.getTimestamp(ReportedNumber.FIELD_EXPIRES_AT) ?: Timestamp.now(),
                    tags         = (doc.get(ReportedNumber.FIELD_TAGS) as? List<*>)
                        ?.filterIsInstance<String>() ?: emptyList()
                )
            }
        } catch (e: Exception) {
            Log.e("ReportRepository", "getTopReported FAILED: ${e.javaClass.simpleName} — ${e.message}", e)
            emptyList()
        }
    }

    /** Vide le cache local */
    fun clearCache() = cache.clear()
}