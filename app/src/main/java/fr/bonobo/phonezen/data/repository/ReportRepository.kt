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
 */
class ReportRepository {

    private val supabaseUrl = BuildConfig.SUPABASE_URL
    private val supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    private val tableUrl    = "$supabaseUrl/rest/v1/${ReportedNumber.TABLE}"

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

    private fun nowIso(): String =
        DateTimeFormatter.ISO_INSTANT.format(Instant.now())

    /**
     * Calcul dynamique du TTL selon le nombre de signalements :
     *   10–19  → 30 jours
     *   20–49  → 60 jours
     *   50–99  → 90 jours
     *   100+   → 180 jours
     *
     * Appelé APRÈS l'incrément donc on passe déjà le nouveau total.
     */
    private fun expiresIso(reports: Long): String {
        val days = when {
            reports >= 100 -> 180L
            reports >= 50  -> 90L
            reports >= 20  -> 60L
            else           -> 30L
        }
        return DateTimeFormatter.ISO_INSTANT.format(
            Instant.now().plusSeconds(days * 24 * 60 * 60)
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SIGNALER UN NUMÉRO
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Signale un numéro comme indésirable.
     * - Existe déjà → incrément + recalcul TTL dynamique
     * - Nouveau     → insertion avec TTL initial (< 10 signalements → 30 jours)
     */
    suspend fun reportNumber(number: String, tag: String = "indésirable"): Result<Unit> {
        return try {
            val normalized = PhoneUtils.normalizeNumber(number)
            val existing   = fetchFromSupabase(normalized)

            if (existing != null) {
                val newReports = existing.reports + 1
                val newTags    = (existing.tags + tag).distinct()
                client.patch("$tableUrl?number=eq.$normalized") {
                    supabaseHeaders()
                    setBody("""
                        {
                          "reports":       $newReports,
                          "last_reported": "${nowIso()}",
                          "expires_at":    "${expiresIso(newReports)}",
                          "tags":          ${tagsToJson(newTags)}
                        }
                    """.trimIndent())
                }
            } else {
                // Premier signalement → 30 jours (< 10 signalements)
                client.post(tableUrl) {
                    supabaseHeaders()
                    header("Prefer", "return=minimal")
                    setBody("""
                        {
                          "number":        "$normalized",
                          "reports":       1,
                          "last_reported": "${nowIso()}",
                          "expires_at":    "${expiresIso(1)}",
                          "tags":          ${tagsToJson(listOf(tag))}
                        }
                    """.trimIndent())
                }
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
            val nowIsoStr = nowIso()
            val response: HttpResponse = client.get(tableUrl) {
                supabaseHeaders()
                parameter("order",      "reports.desc")
                parameter("limit",      limit.toString())
                parameter("select",     "*")
                // FIX : filtrer les expirés directement côté Supabase
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

    private fun tagsToJson(tags: List<String>): String =
        "[${tags.joinToString(",") { "\"$it\"" }}]"
}