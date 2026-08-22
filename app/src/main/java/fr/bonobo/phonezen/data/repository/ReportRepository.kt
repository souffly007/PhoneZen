package fr.bonobo.phonezen.data.repository

import android.util.Log
import fr.bonobo.phonezen.BuildConfig
import fr.bonobo.phonezen.data.model.ReportedNumber
import fr.bonobo.phonezen.utils.PhoneUtils
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Repository pour la liste participative des numéros indésirables.
 * Backend : Supabase (PostgreSQL REST API).
 * Compatible F-Droid — aucune dépendance Google/Firebase.
 *
 * Sécurité : l'incrément de signalement passe par la fonction RPC
 * `report_number` (SECURITY DEFINER côté Postgres) plutôt que par un
 * UPDATE direct sur la table — la clé publishable étant lisible par
 * quiconque décompile l'APK, un UPDATE libre permettrait de falsifier
 * les compteurs. La RPC ne permet que l'opération "incrémenter de 1".
 */
class ReportRepository {

    private val supabaseUrl = BuildConfig.SUPABASE_URL
    private val supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
    private val tableUrl    = "$supabaseUrl/rest/v1/${ReportedNumber.TABLE}"
    private val rpcUrl      = "$supabaseUrl/rest/v1/rpc/report_number"

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }

    private val cache = mutableMapOf<String, ReportedNumber?>()

    private fun HttpRequestBuilder.supabaseHeaders() {
        header("apikey",        supabaseKey)
        header("Authorization", "Bearer $supabaseKey")
        header("Content-Type",  "application/json")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SIGNALER UN NUMÉRO
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Signale un numéro comme indésirable via la fonction RPC sécurisée
     * `report_number`. Le serveur gère lui-même l'incrément, le TTL
     * dynamique et la fusion des tags — le client ne peut plus écrire
     * de valeur arbitraire dans `reports` ou `expires_at`.
     */
    suspend fun reportNumber(number: String, tag: String = "indésirable"): Result<Unit> {
        return try {
            val normalized = PhoneUtils.normalizeNumber(number)

            client.post(rpcUrl) {
                supabaseHeaders()
                header("Prefer", "return=minimal")
                setBody("""
                    {
                      "p_number": "$normalized",
                      "p_tag":    "$tag"
                    }
                """.trimIndent())
            }

            cache.remove(normalized)
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e("ReportRepository", "reportNumber failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VÉRIFIER UN NUMÉRO
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun checkNumber(number: String): ReportedNumber? {
        return try {
            val normalized = PhoneUtils.normalizeNumber(number)
            if (cache.containsKey(normalized)) return cache[normalized]
            val result = fetchFromSupabase(normalized)
            cache[normalized] = result
            result
        } catch (e: Exception) {
            Log.e("ReportRepository", "checkNumber failed: ${e.message}", e)
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TOP SIGNALÉS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Récupère les numéros les plus signalés (top 50).
     * Filtre côté Supabase : exclut les entrées expirées (expires_at < now).
     */
    suspend fun getTopReported(limit: Int = 50): List<ReportedNumber> {
        return try {
            val nowIsoStr = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
            val response: HttpResponse = client.get(tableUrl) {
                supabaseHeaders()
                parameter("order",      "reports.desc")
                parameter("limit",      limit.toString())
                parameter("select",     "*")
                parameter("expires_at", "gt.$nowIsoStr")
            }
            val body = response.bodyAsText()
            Json { ignoreUnknownKeys = true }.decodeFromString<List<ReportedNumber>>(body)
        } catch (e: Exception) {
            Log.e("ReportRepository", "getTopReported failed: ${e.message}", e)
            emptyList()
        }
    }

    fun clearCache() = cache.clear()

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVÉ
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun fetchFromSupabase(normalized: String): ReportedNumber? {
        val response: HttpResponse = client.get(tableUrl) {
            supabaseHeaders()
            parameter("number", "eq.$normalized")
            parameter("select", "*")
            parameter("limit",  "1")
        }
        val body = response.bodyAsText()
        return Json { ignoreUnknownKeys = true }
            .decodeFromString<List<ReportedNumber>>(body)
            .firstOrNull()
    }
}