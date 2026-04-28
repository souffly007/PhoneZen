package fr.bonobo.phonezen.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.CallLog
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.bonobo.phonezen.data.local.*
import fr.bonobo.phonezen.data.model.*
import fr.bonobo.phonezen.data.repository.ReportRepository
import fr.bonobo.phonezen.service.BlockedCallActionReceiver
import fr.bonobo.phonezen.service.VoicemailSmsReceiver
import fr.bonobo.phonezen.utils.PhoneUtils
import fr.bonobo.phonezen.utils.ProfileManager
import fr.bonobo.phonezen.utils.SpamDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs         = app.getSharedPreferences("phonezen_prefs", Context.MODE_PRIVATE)
    private val db            = AppDatabase.getDatabase(app)
    val spamDetector          = SpamDetector(app)
    val profileManager        = ProfileManager(app)
    private val reportRepo    = ReportRepository()

    // ─────────────────────────────────────────────
    // STATE
    // ─────────────────────────────────────────────
    private val _callGroups = MutableStateFlow<List<CallGroup>>(emptyList())
    val callGroups: StateFlow<List<CallGroup>> = _callGroups

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts

    private val _favorites = MutableStateFlow<List<Contact>>(emptyList())
    val favorites: StateFlow<List<Contact>> = _favorites

    // Historique des appels bloqués (table blocked_calls)
    private val _blockedCalls = MutableStateFlow<List<BlockedCall>>(emptyList())
    val blockedCalls: StateFlow<List<BlockedCall>> = _blockedCalls

    // ✅ Liste noire active (table blocked_numbers) — manquait dans l'original
    private val _blockedNumbers = MutableStateFlow<List<BlockedNumber>>(emptyList())
    val blockedNumbers: StateFlow<List<BlockedNumber>> = _blockedNumbers

    private val _notes = MutableStateFlow<Map<String, String>>(emptyMap())
    val notes: StateFlow<Map<String, String>> = _notes

    private val _reportedNumbers = MutableStateFlow<Map<String, ReportedNumber>>(emptyMap())
    val reportedNumbers: StateFlow<Map<String, ReportedNumber>> = _reportedNumbers

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _reportFeedback = MutableStateFlow<String?>(null)
    val reportFeedback: StateFlow<String?> = _reportFeedback

    private val _hasNewVoicemail = MutableStateFlow(prefs.getBoolean("has_voicemail", false))
    val hasNewVoicemail: StateFlow<Boolean> = _hasNewVoicemail

    // ─────────────────────────────────────────────
    // GESTION CONTACT À MULTIPLES NUMÉROS
    // ─────────────────────────────────────────────
    private val _pendingCallContact = MutableStateFlow<Contact?>(null)
    val pendingCallContact: StateFlow<Contact?> = _pendingCallContact

    // ─────────────────────────────────────────────
    // PARAMÈTRES
    // ─────────────────────────────────────────────
    private val _blockPrivate = MutableStateFlow(spamDetector.isBlockPrivateEnabled())
    val blockPrivate: StateFlow<Boolean> = _blockPrivate

    private val _hideBlocked = MutableStateFlow(prefs.getBoolean("hide_blocked", true))
    val hideBlocked: StateFlow<Boolean> = _hideBlocked

    private val _whitelist = MutableStateFlow(spamDetector.getWhitelist())
    val whitelist: StateFlow<Set<String>> = _whitelist

    private val _doNotDisturb = MutableStateFlow(spamDetector.isDoNotDisturbEnabled())
    val doNotDisturb: StateFlow<Boolean> = _doNotDisturb

    private val _scheduleEnabled = MutableStateFlow(spamDetector.isScheduleEnabled())
    val scheduleEnabled: StateFlow<Boolean> = _scheduleEnabled

    private val _scheduleStartHour = MutableStateFlow(spamDetector.getScheduleStartHour())
    val scheduleStartHour: StateFlow<Int> = _scheduleStartHour

    private val _scheduleStartMinute = MutableStateFlow(spamDetector.getScheduleStartMinute())
    val scheduleStartMinute: StateFlow<Int> = _scheduleStartMinute

    private val _scheduleEndHour = MutableStateFlow(spamDetector.getScheduleEndHour())
    val scheduleEndHour: StateFlow<Int> = _scheduleEndHour

    private val _scheduleEndMinute = MutableStateFlow(spamDetector.getScheduleEndMinute())
    val scheduleEndMinute: StateFlow<Int> = _scheduleEndMinute

    // ─────────────────────────────────────────────
    // PROFILS
    // ─────────────────────────────────────────────
    private val _activeProfile = MutableStateFlow(profileManager.getActiveProfile())
    val activeProfile: StateFlow<BlockingProfile> = _activeProfile

    private val _vacationConfig = MutableStateFlow(profileManager.getVacationConfig())
    val vacationConfig: StateFlow<VacationConfig> = _vacationConfig

    private val _dialpadNumber = MutableStateFlow("")
    val dialpadNumber: StateFlow<String> = _dialpadNumber

    private var isLoadingData = false

    // ─────────────────────────────────────────────
    // BROADCAST RECEIVER
    // ─────────────────────────────────────────────
    private val globalUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BlockedCallActionReceiver.ACTION_WHITELIST_UPDATED -> refreshWhitelist()
                VoicemailSmsReceiver.ACTION_VOICEMAIL_RECEIVED -> {
                    _hasNewVoicemail.value = true
                    prefs.edit().putBoolean("has_voicemail", true).apply()
                }
            }
        }
    }

    // ─────────────────────────────────────────────
    // INIT
    // ─────────────────────────────────────────────
    init {
        // Historique des appels bloqués
        viewModelScope.launch {
            db.blockedCallDao().getAllBlockedCalls().collectLatest {
                _blockedCalls.value = it
            }
        }
        // ✅ Liste noire active — manquait dans l'original
        viewModelScope.launch {
            db.blockedNumberDao().getAll().collectLatest {
                _blockedNumbers.value = it
            }
        }
        // Notes d'appels
        viewModelScope.launch {
            db.callNoteDao().getAllNotes().collectLatest {
                _notes.value = it.associate { note -> note.number to note.note }
            }
        }
        // Broadcast receiver
        val filter = IntentFilter().apply {
            addAction(BlockedCallActionReceiver.ACTION_WHITELIST_UPDATED)
            addAction(VoicemailSmsReceiver.ACTION_VOICEMAIL_RECEIVED)
        }
        ContextCompat.registerReceiver(
            app, globalUpdateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    // ─────────────────────────────────────────────
    // RÉPONDEUR
    // ─────────────────────────────────────────────
    fun clearVoicemailIndicator() {
        _hasNewVoicemail.value = false
        prefs.edit().putBoolean("has_voicemail", false).apply()
    }

    // ─────────────────────────────────────────────
    // CHARGEMENT CONTACTS & APPELS
    // ─────────────────────────────────────────────
    fun loadData(ctx: Context) {
        if (isLoadingData) return
        isLoadingData = true
        viewModelScope.launch(Dispatchers.Default) {
            try {
                withContext(Dispatchers.Main) { _isLoading.value = true }
                val favIds       = getFavoriteIds()
                val groups       = PhoneUtils.loadCallGroups(ctx, favIds)
                val contactsList = PhoneUtils.loadContacts(ctx, favIds)
                withContext(Dispatchers.Main) {
                    _callGroups.value  = groups
                    _contacts.value    = contactsList
                    _favorites.value   = contactsList.filter { it.isFavorite }.sortedByDescending { it.callCount }
                    _isLoading.value   = false
                }
                checkReportedNumbers(groups.map { it.number })
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { _isLoading.value = false }
            } finally {
                isLoadingData = false
            }
        }
    }

    fun forceReload(ctx: Context) = loadData(ctx)

    // ─────────────────────────────────────────────
    // RECHERCHE & CLAVIER
    // ─────────────────────────────────────────────
    fun setSearchQuery(q: String) { _searchQuery.value = q }
    fun setDialpadNumber(number: String) { _dialpadNumber.value = number }
    fun clearDialpadNumber() { _dialpadNumber.value = "" }

    fun getSuggestions(input: String): List<Contact> {
        if (input.length < 3) return emptyList()
        val normalized = normalize(input)
        return _contacts.value.filter { c ->
            getContactNumbers(c).any { it.contains(normalized) }
        }.take(3)
    }

    fun findContactsByName(name: String): List<Contact> {
        val q = name.lowercase().trim()
        if (q.isEmpty()) return emptyList()
        return _contacts.value.filter { it.name.lowercase().contains(q) }
    }

    // ─────────────────────────────────────────────
    // ACTIONS LISTE NOIRE (blocked_numbers)
    // ─────────────────────────────────────────────

    /**
     * Ajoute un numéro à la liste noire active.
     * Normalise le numéro avant insertion.
     */
    fun blockNumber(rawNumber: String, label: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            val normalized = normalize(rawNumber)
            db.blockedNumberDao().insert(
                BlockedNumber(number = normalized, label = label)
            )
        }
    }

    /**
     * Supprime un numéro de la liste noire active.
     * C'est le vrai "déblocage" — utilisé par BlockedNumbersScreen.
     */
    fun unblockNumber(entry: BlockedNumber) {
        viewModelScope.launch(Dispatchers.IO) {
            db.blockedNumberDao().delete(entry)
        }
    }

    /**
     * Vérifie si un numéro est dans la liste noire active.
     * Teste les deux formats (+33 et 0X) pour éviter les faux négatifs.
     */
    suspend fun isNumberBlocked(rawNumber: String): Boolean {
        val normalized = normalize(rawNumber)
        val alt = if (normalized.startsWith("+33"))
            "0" + normalized.substring(3)
        else if (normalized.startsWith("0"))
            "+33" + normalized.substring(1)
        else null
        return withContext(Dispatchers.IO) {
            db.blockedNumberDao().isBlocked(normalized) > 0 ||
                    (alt != null && db.blockedNumberDao().isBlocked(alt) > 0)
        }
    }

    // ─────────────────────────────────────────────
    // ACTIONS HISTORIQUE (blocked_calls)
    // ─────────────────────────────────────────────

    /**
     * Supprime une entrée de l'historique des appels bloqués.
     * Ne touche PAS à la liste noire active.
     */
    fun deleteBlockedCall(call: BlockedCall) {
        viewModelScope.launch(Dispatchers.IO) {
            db.blockedCallDao().delete(call)
        }
    }

    fun removeCallGroup(number: String) {
        viewModelScope.launch(Dispatchers.IO) {
            getApplication<Application>().contentResolver.delete(
                CallLog.Calls.CONTENT_URI,
                "${CallLog.Calls.NUMBER} = ?",
                arrayOf(number)
            )
            withContext(Dispatchers.Main) {
                _callGroups.value = _callGroups.value.filter { it.number != number }
            }
        }
    }

    // ─────────────────────────────────────────────
    // SIGNALEMENT COMMUNAUTAIRE
    // ─────────────────────────────────────────────

    /**
     * Signale un numéro :
     * 1. L'ajoute à la liste noire active (blocked_numbers) ✅
     * 2. L'enregistre dans l'historique (blocked_calls)     ✅
     * 3. Le remonte au serveur Supabase                     ✅
     */
    fun reportNumber(number: String, tag: String = "indésirable") {
        viewModelScope.launch {
            val normalized = normalize(number)
            withContext(Dispatchers.IO) {
                // ✅ Liste noire active
                db.blockedNumberDao().insert(
                    BlockedNumber(number = normalized, label = "Signalé: $tag")
                )
                // ✅ Historique
                db.blockedCallDao().insert(
                    BlockedCall(
                        number    = normalized,
                        reason    = "Signalé: $tag",
                        riskLevel = "SPAM"
                    )
                )
            }
            _reportFeedback.value = "⏳ Signalement..."
            val result = reportRepo.reportNumber(normalized, tag)
            _reportFeedback.value = if (result.isSuccess) "✅ Signalé et Bloqué" else "⚠️ Bloqué localement"
        }
    }

    // ─────────────────────────────────────────────
    // GESTION APPEL CONTACT MULTI-NUMÉROS
    // ─────────────────────────────────────────────

    /**
     * Demande à appeler un contact.
     * - 0 numéro  → rien
     * - 1 numéro  → appel direct
     * - 2+ numéros → affiche le sélecteur
     */
    fun requestCall(contact: Contact, onCall: (String) -> Unit) {
        when (contact.phoneNumbers.size) {
            0    -> Unit
            1    -> onCall(contact.phoneNumbers.first())
            else -> _pendingCallContact.value = contact
        }
    }

    /** Ferme le sélecteur de numéro sans appeler. */
    fun dismissPendingCall() {
        _pendingCallContact.value = null
    }

    /** Appelle un numéro spécifique du contact en attente. */
    fun callPendingContactNumber(phoneNumber: String, onCall: (String) -> Unit) {
        _pendingCallContact.value?.let { contact ->
            if (contact.phoneNumbers.contains(phoneNumber)) onCall(phoneNumber)
        }
        dismissPendingCall()
    }

    // ─────────────────────────────────────────────
    // FAVORIS & NOTES
    // ─────────────────────────────────────────────
    fun toggleFavorite(number: String) {
        viewModelScope.launch {
            val key    = PhoneUtils.groupKey(number)
            val favIds = getFavoriteIds().toMutableSet()
            if (favIds.contains(key)) favIds.remove(key) else favIds.add(key)
            withContext(Dispatchers.IO) { prefs.edit().putStringSet("favorites", favIds).commit() }
            val updated = _contacts.value.map { c ->
                val isFav = c.phoneNumbers.any { PhoneUtils.groupKey(it) in favIds }
                c.copy(isFavorite = isFav)
            }
            _contacts.value  = updated
            _favorites.value = updated.filter { it.isFavorite }.sortedByDescending { it.callCount }
        }
    }

    fun saveNote(number: String, note: String) {
        val n = normalize(number)
        viewModelScope.launch(Dispatchers.IO) {
            if (note.isBlank()) db.callNoteDao().deleteNote(n)
            else db.callNoteDao().upsert(CallNote(n, note.trim()))
        }
    }

    // ─────────────────────────────────────────────
    // WHITELIST
    // ─────────────────────────────────────────────
    fun refreshWhitelist() { _whitelist.value = spamDetector.getWhitelist() }

    fun addToWhitelist(number: String) {
        spamDetector.addToWhitelist(normalize(number))
        refreshWhitelist()
    }

    fun removeFromWhitelist(number: String) {
        spamDetector.removeFromWhitelist(normalize(number))
        refreshWhitelist()
    }

    fun isWhitelisted(number: String) = spamDetector.isWhitelisted(normalize(number))

    fun buildNumberToNameMap(): Map<String, String> =
        _contacts.value.flatMap { c -> getContactNumbers(c).map { it to c.name } }.toMap()

    // ─────────────────────────────────────────────
    // SETTERS PARAMÈTRES
    // ─────────────────────────────────────────────
    fun setBlockPrivate(b: Boolean)        { spamDetector.setBlockPrivateNumbers(b); _blockPrivate.value = b }
    fun setHideBlocked(b: Boolean)         { prefs.edit().putBoolean("hide_blocked", b).apply(); _hideBlocked.value = b }
    fun setDoNotDisturb(e: Boolean)        { spamDetector.setDoNotDisturb(e); _doNotDisturb.value = e }
    fun setScheduleEnabled(e: Boolean)     { spamDetector.setScheduleEnabled(e); _scheduleEnabled.value = e }
    fun setScheduleStartHour(h: Int)       { spamDetector.setScheduleStartHour(h); _scheduleStartHour.value = h }
    fun setScheduleStartMinute(m: Int)     { spamDetector.setScheduleStartMinute(m); _scheduleStartMinute.value = m }
    fun setScheduleEndHour(h: Int)         { spamDetector.setScheduleEndHour(h); _scheduleEndHour.value = h }
    fun setScheduleEndMinute(m: Int)       { spamDetector.setScheduleEndMinute(m); _scheduleEndMinute.value = m }

    // ─────────────────────────────────────────────
    // SETTERS PROFILS
    // ─────────────────────────────────────────────
    fun setActiveProfile(profile: BlockingProfile) {
        profileManager.setActiveProfile(profile)
        _activeProfile.value = profileManager.getActiveProfile()
    }

    fun saveVacationConfig(config: VacationConfig) {
        profileManager.saveVacationConfig(config)
        _vacationConfig.value = config
    }

    fun clearVacationEndDate() {
        profileManager.clearVacationEndDate()
        _vacationConfig.value = _vacationConfig.value.copy(endTimestamp = -1L)
    }

    // ─────────────────────────────────────────────
    // UTILS PRIVÉS
    // ─────────────────────────────────────────────
    private fun normalize(number: String?) = PhoneUtils.normalizeNumber(number ?: "")
    private fun getContactNumbers(contact: Contact): List<String> = contact.phoneNumbers.map { normalize(it) }
    private fun getFavoriteIds(): Set<String> = prefs.getStringSet("favorites", emptySet()) ?: emptySet()

    suspend fun getTopReported() = reportRepo.getTopReported()

    private fun checkReportedNumbers(numbers: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val map            = mutableMapOf<String, ReportedNumber>()
            val communityBlock = mutableSetOf<String>()
            numbers
                .map { normalize(it) }
                .filter { it.isNotBlank() }
                .distinct()
                .take(15)
                .forEach { num ->
                    try {
                        val reported = reportRepo.checkNumber(num)
                        if (reported != null && reported.isSuspect()) {
                            map[num] = reported
                            if (reported.reports >= 5) communityBlock.add(num)
                        }
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "Erreur vérification $num", e)
                    }
                }
            spamDetector.setCommunityBlockedNumbers(communityBlock)
            withContext(Dispatchers.Main) { _reportedNumbers.value = map }
        }
    }

    // ─────────────────────────────────────────────
    // LIFECYCLE
    // ─────────────────────────────────────────────
    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(globalUpdateReceiver)
        } catch (e: Exception) {
            Log.w("MainViewModel", "Receiver déjà désenregistré : ${e.message}")
        }
    }
}