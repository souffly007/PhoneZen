// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import fr.bonobo.phonezen.viewmodel.MainViewModel
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CrashHandler
 * ------------
 * Intercepte tous les crashes non gérés et écrit un rapport .txt enrichi
 * dans le stockage interne de l'app (filesDir/crashes/).
 *
 * Installer via CrashHandler.install(this) dans PhoneZenApp.onCreate().
 */
object CrashHandler {

    private const val CRASH_DIR     = "crashes"
    private const val MAX_FILES     = 10
    private lateinit var appContext : Context
    private lateinit var appVersion : String
    private lateinit var appBuild   : String

    // ── Contexte dynamique mis à jour par l'app ───────────────────────────

    /** Dernier numéro analysé (mis à jour par InCallViewModel) */
    @Volatile var lastAnalyzedNumber : String = "—"

    /** Dernière action exécutée (mis à jour aux points clés du code) */
    @Volatile var lastAction         : String = "Démarrage de l'application"

    // ── Installation ──────────────────────────────────────────────────────

    fun install(context: Context) {
        appContext  = context.applicationContext
        val pi = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        appVersion  = pi?.versionName ?: "?"
        appBuild    = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            (pi?.longVersionCode ?: 0).toString()
        else
            @Suppress("DEPRECATION") (pi?.versionCode ?: 0).toString()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashReport(thread, throwable)
            } catch (e: Exception) {
                // Ne pas planter dans le handler de crash
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    // ── Écriture du rapport ───────────────────────────────────────────────

    private fun writeCrashReport(thread: Thread, throwable: Throwable) {
        val crashDir  = File(appContext.filesDir, CRASH_DIR).also { it.mkdirs() }
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.FRANCE).format(Date())
        val file      = File(crashDir, "crash_$timestamp.txt")

        file.bufferedWriter().use { w ->

            w.appendLine("╔══════════════════════════════════════════════════╗")
            w.appendLine("║           PHONEZEN — RAPPORT DE CRASH            ║")
            w.appendLine("╚══════════════════════════════════════════════════╝")
            w.appendLine()

            // ── 1. Informations générales ─────────────────────────────
            w.appendLine("── 1. Informations générales ───────────────────────")
            w.appendLine("Date         : $timestamp")
            w.appendLine("Version app  : $appVersion (build $appBuild)")
            w.appendLine("Thread       : ${thread.name} (id=${thread.id})")
            w.appendLine()

            // ── 2. Appareil ───────────────────────────────────────────
            w.appendLine("── 2. Appareil ─────────────────────────────────────")
            w.appendLine("Fabricant    : ${Build.MANUFACTURER}")
            w.appendLine("Modèle       : ${Build.MODEL} (${Build.DEVICE})")
            w.appendLine("Android      : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            w.appendLine("Build        : ${Build.DISPLAY}")
            w.appendLine("Produit      : ${Build.PRODUCT}")
            w.appendLine()

            // ── 3. Mémoire ────────────────────────────────────────────
            val rt     = Runtime.getRuntime()
            val usedMb = (rt.totalMemory() - rt.freeMemory()) / 1_048_576
            val maxMb  = rt.maxMemory() / 1_048_576
            val totMb  = rt.totalMemory() / 1_048_576
            w.appendLine("── 3. Mémoire ──────────────────────────────────────")
            w.appendLine("Utilisée     : $usedMb Mo")
            w.appendLine("Allouée JVM  : $totMb Mo")
            w.appendLine("Maximum JVM  : $maxMb Mo")
            w.appendLine()

            // ── 4. Contexte d'appel ───────────────────────────────────
            w.appendLine("── 4. Contexte d'appel ─────────────────────────────")
            w.appendLine("Numéro analysé   : $lastAnalyzedNumber")
            w.appendLine("Dernière action  : $lastAction")
            w.appendLine()

            // ── 5. État des modules PhoneZen ──────────────────────────
            w.appendLine("── 5. État des modules PhoneZen ────────────────────")
            val vm = MainViewModel.instance
            if (vm != null) {
                val sd = vm.spamDetector
                w.appendLine("Anti-spam activé      : ${sd.isBlockPrivateEnabled()}")
                w.appendLine("Numéros masqués       : ${sd.isBlockPrivateEnabled()}")
                w.appendLine("Planning actif        : ${sd.isScheduleEnabled()}")
                if (sd.isScheduleEnabled()) {
                    w.appendLine("  Plage               : %02d:%02d → %02d:%02d".format(
                        sd.getScheduleStartHour(), sd.getScheduleStartMinute(),
                        sd.getScheduleEndHour(),   sd.getScheduleEndMinute()
                    ))
                }
                w.appendLine("Blocage communautaire : ${sd.isCommunityBlockEnabled()}")
                w.appendLine("Profil actif          : ${vm.activeProfile.value.name}")
                w.appendLine("Ne pas déranger       : ${vm.doNotDisturb.value}")
                w.appendLine("Masquer bloqués       : ${vm.hideBlocked.value}")
                w.appendLine("Whitelist hôpitaux    : ${vm.hospitalWhitelistEnabled.value}")
                w.appendLine("Nb entrées hôpitaux   : ${vm.hospitalEntriesCount.value}")
                w.appendLine("Mode popup appel      : ${vm.callPopupMode.value.name}")
            } else {
                w.appendLine("(MainViewModel non disponible — crash au démarrage)")
            }
            w.appendLine()

            // ── 6. Permissions ────────────────────────────────────────
            w.appendLine("── 6. Permissions ──────────────────────────────────")
            val permissions = listOf(
                Manifest.permission.READ_CALL_LOG        to "Lire journal d'appels",
                Manifest.permission.WRITE_CALL_LOG       to "Écrire journal d'appels",
                Manifest.permission.READ_CONTACTS        to "Lire contacts",
                Manifest.permission.CALL_PHONE           to "Passer des appels",
                Manifest.permission.ANSWER_PHONE_CALLS   to "Décrocher les appels",
                Manifest.permission.READ_PHONE_STATE     to "État du téléphone",
                Manifest.permission.POST_NOTIFICATIONS   to "Notifications",
                Manifest.permission.RECORD_AUDIO         to "Enregistrement audio",
                Manifest.permission.BLUETOOTH_CONNECT    to "Bluetooth",
            )
            for ((perm, label) in permissions) {
                val granted = ContextCompat.checkSelfPermission(appContext, perm) ==
                        PackageManager.PERMISSION_GRANTED
                w.appendLine("${if (granted) "✅" else "❌"} $label")
            }
            // Permission overlay (hors PackageManager standard)
            val canOverlay = android.provider.Settings.canDrawOverlays(appContext)
            w.appendLine("${if (canOverlay) "✅" else "❌"} Affichage par-dessus les apps (overlay)")
            w.appendLine()

            // ── 7. Stack trace ────────────────────────────────────────
            w.appendLine("── 7. Stack trace ──────────────────────────────────")
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            w.appendLine(sw.toString())

            // ── 8. Causes enchaînées ──────────────────────────────────
            var cause = throwable.cause
            var depth = 1
            while (cause != null && depth <= 5) {
                w.appendLine("── Cause #$depth ────────────────────────────────────")
                val csw = StringWriter()
                cause.printStackTrace(PrintWriter(csw))
                w.appendLine(csw.toString())
                cause = cause.cause
                depth++
            }

            w.appendLine("── Fin du rapport ──────────────────────────────────")
        }

        // Rotation : max MAX_FILES fichiers
        crashDir.listFiles()
            ?.filter { it.name.startsWith("crash_") && it.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_FILES)
            ?.forEach { it.delete() }
    }

    // ── API publique ──────────────────────────────────────────────────────

    fun getCrashReports(context: Context): List<File> {
        val crashDir = File(context.filesDir, CRASH_DIR)
        if (!crashDir.exists()) return emptyList()
        return crashDir.listFiles()
            ?.filter { it.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun clearAll(context: Context) {
        File(context.filesDir, CRASH_DIR).listFiles()?.forEach { it.delete() }
    }

    fun hasCrashReports(context: Context): Boolean =
        getCrashReports(context).isNotEmpty()

    /**
     * Enregistre manuellement une erreur non fatale.
     * Usage : CrashHandler.logError(context, "MonTag", exception)
     */
    fun logError(context: Context, tag: String, throwable: Throwable) {
        try {
            val crashDir  = File(context.filesDir, CRASH_DIR).also { it.mkdirs() }
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.FRANCE).format(Date())
            val file      = File(crashDir, "error_${tag}_$timestamp.txt")

            file.bufferedWriter().use { w ->
                w.appendLine("╔══════════════════════════════════════════════════╗")
                w.appendLine("║         PHONEZEN — ERREUR NON FATALE             ║")
                w.appendLine("╚══════════════════════════════════════════════════╝")
                w.appendLine()
                w.appendLine("Date            : $timestamp")
                w.appendLine("Tag             : $tag")
                w.appendLine("Version         : $appVersion (build $appBuild)")
                w.appendLine("Modèle          : ${Build.MANUFACTURER} ${Build.MODEL}")
                w.appendLine("Android         : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                w.appendLine("Numéro analysé  : $lastAnalyzedNumber")
                w.appendLine("Dernière action : $lastAction")
                w.appendLine()
                w.appendLine("── Stack trace ─────────────────────────────────────")
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                w.appendLine(sw.toString())
            }
        } catch (e: Exception) {
            // Silencieux
        }
    }
}