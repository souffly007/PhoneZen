package fr.bonobo.phonezen.viewmodel

import android.app.Application
import android.content.Context
import android.provider.CallLog
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.bonobo.phonezen.data.local.AppDatabase
import fr.bonobo.phonezen.data.local.BlockedCall
import fr.bonobo.phonezen.data.model.CallGroup
import fr.bonobo.phonezen.data.model.Contact
import fr.bonobo.phonezen.data.model.ReportedNumber
import fr.bonobo.phonezen.data.repository.ReportRepository
import fr.bonobo.phonezen.utils.PhoneUtils
import fr.bonobo.phonezen.utils.SpamDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs      = app.getSharedPreferences("phonezen_prefs", Context.MODE_PRIVATE)
    private val db         = AppDatabase.getDatabase(app)
    val spamDetector       = SpamDetector(app)
    private val reportRepo = ReportRepository()

    // ── Données principales ──
    private val _callGroups   = MutableStateFlow<List<CallGroup>>(emptyList())
    val callGroups: StateFlow<List<CallGroup>> = _callGroups

    private val _contacts     = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts

    private val _favorites    = MutableStateFlow<List<Contact>>(emptyList())
    val favorites: StateFlow<List<Contact>> = _favorites

    private val _blockedCalls = MutableStateFlow<List<BlockedCall>>(emptyList())
    val blockedCalls: StateFlow<List<BlockedCall>> = _blockedCalls

    private val _searchQuery  = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _isLoading    = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // ── Paramètres protection ──
    private val _blockPrivate = MutableStateFlow(spamDetector.isBlockPrivateEnabled())
    val blockPrivate: StateFlow<Boolean> = _blockPrivate

    private val _hideBlocked  = MutableStateFlow(prefs.getBoolean("hide_blocked", true))
    val hideBlocked: StateFlow<Boolean> = _hideBlocked

    // ── Horaires de blocage ──
    private val _scheduleEnabled     = MutableStateFlow(spamDetector.isScheduleEnabled())
    val scheduleEnabled: StateFlow<Boolean> = _scheduleEnabled

    private val _scheduleStartHour   = MutableStateFlow(spamDetector.getScheduleStartHour())
    val scheduleStartHour: StateFlow<Int> = _scheduleStartHour

    private val _scheduleStartMinute = MutableStateFlow(spamDetector.getScheduleStartMinute())
    val scheduleStartMinute: StateFlow<Int> = _scheduleStartMinute

    private val _scheduleEndHour     = MutableStateFlow(spamDetector.getScheduleEndHour())
    val scheduleEndHour: StateFlow<Int> = _scheduleEndHour

    private val _scheduleEndMinute   = MutableStateFlow(spamDetector.getScheduleEndMinute())
    val scheduleEndMinute: StateFlow<Int> = _scheduleEndMinute

    // ── Mode Ne pas déranger ──
    private val _doNotDisturb = MutableStateFlow(spamDetector.isDoNotDisturbEnabled())
    val doNotDisturb: StateFlow<Boolean> = _doNotDisturb

    // ── Liste blanche ──
    private val _whitelist = MutableStateFlow<Set<String>>(spamDetector.getWhitelist())
    val whitelist: StateFlow<Set<String>> = _whitelist

    // ── Numéros signalés (cache local) ──
    private val _reportedNumbers = MutableStateFlow<Map<String, ReportedNumber>>(emptyMap())
    val reportedNumbers: StateFlow<Map<String, ReportedNumber>> = _reportedNumbers

    // ── Feedback signalement ──
    private val _reportFeedback = MutableStateFlow<String?>(null)
    val reportFeedback: StateFlow<String?> = _reportFeedback

    init {
        viewModelScope.launch {
            db.blockedCallDao().getAllBlockedCalls().collectLatest { list ->
                _blockedCalls.value = list
            }
        }
    }

    // ─────────────────────────────────────────────
    // DONNÉES
    // ─────────────────────────────────────────────
    fun loadData(ctx: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            val favIds       = getFavoriteIds()
            val groups       = withContext(Dispatchers.IO) { PhoneUtils.loadCallGroups(ctx, favIds) }
            val contactsList = withContext(Dispatchers.IO) { PhoneUtils.loadContacts(ctx, favIds) }
            _callGroups.value = groups
            _contacts.value   = contactsList
            _favorites.value  = contactsList.filter { it.isFavorite }.sortedByDescending { it.callCount }
            _isLoading.value  = false
            checkReportedNumbers(groups.map { it.number })
        }
    }

    private fun checkReportedNumbers(numbers: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val map            = mutableMapOf<String, ReportedNumber>()
            val communityBlock = mutableSetOf<String>()
            numbers.forEach { number ->
                val reported = reportRepo.checkNumber(number)
                if (reported != null && reported.isSuspect()) {
                    val normalized = PhoneUtils.normalizeNumber(number)
                    map[normalized] = reported
                    if (reported.reports >= SpamDetector.COMMUNITY_BLOCK_THRESHOLD) {
                        communityBlock.add(normalized)
                    }
                }
            }
            spamDetector.setCommunityBlockedNumbers(communityBlock)
            withContext(Dispatchers.Main) {
                _reportedNumbers.value = map
            }
        }
    }

    fun setSearchQuery(q: String) { _searchQuery.value = q }

    fun filteredContacts(): List<Contact> {
        val q = _searchQuery.value.lowercase().trim()
        return if (q.isEmpty()) _contacts.value
        else _contacts.value.filter {
            it.name.lowercase().contains(q) || it.phoneNumber.contains(q)
        }
    }

    // ─────────────────────────────────────────────
    // LISTE PARTICIPATIVE
    // ─────────────────────────────────────────────
    fun reportNumber(number: String, tag: String = "indésirable") {
        viewModelScope.launch {
            val result = reportRepo.reportNumber(number, tag)
            if (result.isSuccess) {
                _reportFeedback.value = "✅ Numéro signalé à la communauté"
                val reported = reportRepo.checkNumber(number)
                if (reported != null) {
                    val normalized = PhoneUtils.normalizeNumber(number)
                    _reportedNumbers.value = _reportedNumbers.value + (normalized to reported)
                }
            } else {
                _reportFeedback.value = "❌ Erreur lors du signalement"
            }
        }
    }

    fun clearReportFeedback() { _reportFeedback.value = null }

    fun isReported(number: String): ReportedNumber? =
        _reportedNumbers.value[PhoneUtils.normalizeNumber(number)]

    // ─────────────────────────────────────────────
    // FAVORIS
    // ─────────────────────────────────────────────
    fun toggleFavorite(number: String) {
        val key    = PhoneUtils.groupKey(number)
        val favIds = getFavoriteIds().toMutableSet()
        if (favIds.contains(key)) favIds.remove(key) else favIds.add(key)
        prefs.edit().putStringSet("favorites", favIds).apply()
        _contacts.value   = _contacts.value.map {
            it.copy(isFavorite = favIds.contains(PhoneUtils.groupKey(it.phoneNumber)))
        }
        _favorites.value  = _contacts.value.filter { it.isFavorite }.sortedByDescending { it.callCount }
        _callGroups.value = _callGroups.value.map {
            it.copy(isFavorite = favIds.contains(PhoneUtils.groupKey(it.number)))
        }
    }

    private fun getFavoriteIds(): Set<String> =
        prefs.getStringSet("favorites", emptySet()) ?: emptySet()

    // ─────────────────────────────────────────────
    // BLOCAGE MANUEL
    // ─────────────────────────────────────────────
    fun blockNumber(number: String, reason: String = "Bloqué manuellement") {
        viewModelScope.launch(Dispatchers.IO) {
            db.blockedCallDao().insert(
                BlockedCall(
                    number    = PhoneUtils.normalizeNumber(number),
                    reason    = reason,
                    riskLevel = "MANUAL"
                )
            )
        }
    }

    fun deleteBlockedCall(call: BlockedCall) {
        viewModelScope.launch(Dispatchers.IO) { db.blockedCallDao().deleteById(call.id) }
    }

    // ─────────────────────────────────────────────
    // SUPPRESSION RÉCENTS
    // ─────────────────────────────────────────────
    fun removeCallGroup(number: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val resolver   = getApplication<Application>().contentResolver
            val normalized = PhoneUtils.normalizeNumber(number)
            resolver.delete(CallLog.Calls.CONTENT_URI, "${CallLog.Calls.NUMBER} = ?", arrayOf(number))
            if (normalized != number)
                resolver.delete(CallLog.Calls.CONTENT_URI, "${CallLog.Calls.NUMBER} = ?", arrayOf(normalized))
            withContext(Dispatchers.Main) {
                _callGroups.value = _callGroups.value.filter { it.number != number }
            }
        }
    }

    // ─────────────────────────────────────────────
    // PARAMÈTRES — Protection
    // ─────────────────────────────────────────────
    fun setBlockPrivate(block: Boolean) {
        spamDetector.setBlockPrivateNumbers(block)
        _blockPrivate.value = block
    }

    fun setHideBlocked(hide: Boolean) {
        prefs.edit().putBoolean("hide_blocked", hide).apply()
        _hideBlocked.value = hide
    }

    // ─────────────────────────────────────────────
    // PARAMÈTRES — Horaires
    // ─────────────────────────────────────────────
    fun setScheduleEnabled(enabled: Boolean) {
        spamDetector.setScheduleEnabled(enabled)
        _scheduleEnabled.value = enabled
    }

    fun setScheduleStartHour(h: Int) {
        spamDetector.setScheduleStartHour(h)
        _scheduleStartHour.value = h
    }

    fun setScheduleStartMinute(m: Int) {
        spamDetector.setScheduleStartMinute(m)
        _scheduleStartMinute.value = m
    }

    fun setScheduleEndHour(h: Int) {
        spamDetector.setScheduleEndHour(h)
        _scheduleEndHour.value = h
    }

    fun setScheduleEndMinute(m: Int) {
        spamDetector.setScheduleEndMinute(m)
        _scheduleEndMinute.value = m
    }

    // ─────────────────────────────────────────────
    // PARAMÈTRES — Ne pas déranger
    // ─────────────────────────────────────────────
    fun setDoNotDisturb(enabled: Boolean) {
        spamDetector.setDoNotDisturb(enabled)
        _doNotDisturb.value = enabled
    }

    // ─────────────────────────────────────────────
    // LISTE BLANCHE
    // ─────────────────────────────────────────────
    fun addToWhitelist(number: String) {
        val normalized = PhoneUtils.normalizeNumber(number)
        spamDetector.addToWhitelist(normalized)
        _whitelist.value = spamDetector.getWhitelist()
    }

    fun removeFromWhitelist(number: String) {
        val normalized = PhoneUtils.normalizeNumber(number)
        spamDetector.removeFromWhitelist(normalized)
        if (_whitelist.value.contains(number)) spamDetector.removeFromWhitelist(number)
        _whitelist.value = spamDetector.getWhitelist()
    }

    fun buildNumberToNameMap(): Map<String, String> =
        _contacts.value.associate {
            PhoneUtils.normalizeNumber(it.phoneNumber) to it.name
        }

    // ─────────────────────────────────────────────
    // SUGGESTIONS CLAVIER
    // ─────────────────────────────────────────────
    /**
     * Retourne les contacts dont le numéro contient la saisie en cours.
     * Utilisé par KeypadScreen pour les suggestions.
     */
    fun getSuggestions(input: String): List<Contact> {
        if (input.length < 3) return emptyList()
        return _contacts.value
            .filter { it.phoneNumber.contains(input) }
            .take(3)
    }

    // ─────────────────────────────────────────────
    // TOP SIGNALÉS
    // ─────────────────────────────────────────────
    suspend fun getTopReported() = reportRepo.getTopReported()
}