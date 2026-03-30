package fr.bonobo.phonezen.viewmodel

import android.app.Application
import android.content.Context
import android.provider.CallLog
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.bonobo.phonezen.data.local.*
import fr.bonobo.phonezen.data.model.*
import fr.bonobo.phonezen.data.repository.ReportRepository
import fr.bonobo.phonezen.utils.PhoneUtils
import fr.bonobo.phonezen.utils.SpamDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("phonezen_prefs", Context.MODE_PRIVATE)
    private val db = AppDatabase.getDatabase(app)
    val spamDetector = SpamDetector(app)
    private val reportRepo = ReportRepository()

    // ───────────── STATE ─────────────
    private val _callGroups = MutableStateFlow<List<CallGroup>>(emptyList())
    val callGroups: StateFlow<List<CallGroup>> = _callGroups

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts

    private val _favorites = MutableStateFlow<List<Contact>>(emptyList())
    val favorites: StateFlow<List<Contact>> = _favorites

    private val _blockedCalls = MutableStateFlow<List<BlockedCall>>(emptyList())
    val blockedCalls: StateFlow<List<BlockedCall>> = _blockedCalls

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

    // ───────────── DIALPAD (intent tel:) ─────────────
    private val _dialpadNumber = MutableStateFlow("")
    val dialpadNumber: StateFlow<String> = _dialpadNumber

    fun setDialpadNumber(number: String) {
        _dialpadNumber.value = number
    }

    fun clearDialpadNumber() {
        _dialpadNumber.value = ""
    }

    // ───────────── PARAMÈTRES (PROTECTION) ─────────────
    private val _blockPrivate = MutableStateFlow(spamDetector.isBlockPrivateEnabled())
    val blockPrivate: StateFlow<Boolean> = _blockPrivate

    private val _hideBlocked = MutableStateFlow(prefs.getBoolean("hide_blocked", true))
    val hideBlocked: StateFlow<Boolean> = _hideBlocked

    private val _whitelist = MutableStateFlow(spamDetector.getWhitelist())
    val whitelist: StateFlow<Set<String>> = _whitelist

    // ───────────── PARAMÈTRES (HORAIRES & DND) ─────────────
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

    // ───────────── GARDE ANTI-RECHARGEMENT ─────────────
    private var isLoadingData = false

    // ───────────── INIT ─────────────
    init {
        viewModelScope.launch {
            db.blockedCallDao().getAllBlockedCalls().collectLatest {
                _blockedCalls.value = it
            }
        }
        viewModelScope.launch {
            db.callNoteDao().getAllNotes().collectLatest {
                _notes.value = it.associate { note -> note.number to note.note }
            }
        }
    }

    // ───────────── UTILS NUMÉROS ─────────────
    private fun normalize(number: String?) =
        PhoneUtils.normalizeNumber(number ?: "")

    private fun getContactNumbers(contact: Contact): List<String> =
        contact.phoneNumbers
            .map { normalize(it) }
            .filter { it.isNotBlank() }
            .distinct()

    // ───────────── LOAD (VERSION OPTIMISÉE) ─────────────
    fun loadData(ctx: Context) {
        if (isLoadingData) return

        isLoadingData = true
        viewModelScope.launch(Dispatchers.Default) {
            try {
                withContext(Dispatchers.Main) {
                    _isLoading.value = true
                }

                val favIds = withContext(Dispatchers.IO) { getFavoriteIds() }

                val groupsDeferred = async(Dispatchers.IO) {
                    PhoneUtils.loadCallGroups(ctx, favIds)
                }
                val contactsDeferred = async(Dispatchers.IO) {
                    PhoneUtils.loadContacts(ctx, favIds)
                }

                val groups = groupsDeferred.await()
                val contactsList = contactsDeferred.await()

                withContext(Dispatchers.Main) {
                    _callGroups.value = groups
                    _contacts.value = contactsList
                    _favorites.value = contactsList.filter { it.isFavorite }.sortedByDescending { it.callCount }
                    _isLoading.value = false
                }

                checkReportedNumbers(groups.map { it.number })

            } catch (e: Exception) {
                Log.e("MainViewModel", "Error loading data", e)
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                }
            } finally {
                isLoadingData = false
            }
        }
    }

    fun forceReload(ctx: Context) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                withContext(Dispatchers.Main) {
                    _isLoading.value = true
                }

                val favIds = withContext(Dispatchers.IO) { getFavoriteIds() }

                val groupsDeferred = async(Dispatchers.IO) {
                    PhoneUtils.loadCallGroups(ctx, favIds)
                }
                val contactsDeferred = async(Dispatchers.IO) {
                    PhoneUtils.loadContacts(ctx, favIds)
                }

                val groups = groupsDeferred.await()
                val contactsList = contactsDeferred.await()

                withContext(Dispatchers.Main) {
                    _callGroups.value = groups
                    _contacts.value = contactsList
                    _favorites.value = contactsList.filter { it.isFavorite }.sortedByDescending { it.callCount }
                    _isLoading.value = false
                }

                checkReportedNumbers(groups.map { it.number })

            } catch (e: Exception) {
                Log.e("MainViewModel", "Error reloading data", e)
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                }
            }
        }
    }

    private fun checkReportedNumbers(numbers: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val map = mutableMapOf<String, ReportedNumber>()
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
                            if (reported.reports >= SpamDetector.COMMUNITY_BLOCK_THRESHOLD) {
                                communityBlock.add(num)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "Error checking number $num", e)
                    }
                }

            spamDetector.setCommunityBlockedNumbers(communityBlock)
            withContext(Dispatchers.Main) {
                _reportedNumbers.value = map
            }
        }
    }

    // ───────────── SIGNALEMENT & BLOCAGE ─────────────
    fun reportNumber(number: String, tag: String = "indésirable") {
        viewModelScope.launch {
            val normalized = normalize(number)
            withContext(Dispatchers.IO) {
                db.blockedCallDao().insert(
                    BlockedCall(
                        id = 0,
                        number = normalized,
                        reason = "Signalé: $tag",
                        riskLevel = "SPAM",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }

            _reportFeedback.value = "⏳ Signalement..."
            val result = reportRepo.reportNumber(normalized, tag)

            if (result.isSuccess) {
                _reportFeedback.value = "✅ Signalé et Bloqué"
                reportRepo.checkNumber(normalized)?.let {
                    _reportedNumbers.value = _reportedNumbers.value + (normalized to it)
                }
            } else {
                _reportFeedback.value = "⚠️ Bloqué localement (Erreur Cloud)"
            }
        }
    }

    fun blockNumber(number: String, reason: String = "Bloqué manuellement") {
        viewModelScope.launch {
            val normalized = normalize(number)
            withContext(Dispatchers.IO) {
                db.blockedCallDao().insert(
                    BlockedCall(
                        id = 0,
                        number = normalized,
                        reason = reason,
                        riskLevel = "MANUAL",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
            reportRepo.reportNumber(normalized, "bloqué")
        }
    }

    fun deleteBlockedCall(call: BlockedCall) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                db.blockedCallDao().delete(call)
                Log.d("ZEN_DEBUG", "Numéro ${call.number} débloqué avec succès")
            } catch (e: Exception) {
                Log.e("ZEN_DEBUG", "Erreur lors du déblocage: ${e.message}")
            }
        }
    }

    // ───────────── FAVORIS & NOTES ─────────────
    fun toggleFavorite(number: String) {
        viewModelScope.launch {
            val key = PhoneUtils.groupKey(number)
            val favIds = getFavoriteIds().toMutableSet()
            if (favIds.contains(key)) favIds.remove(key) else favIds.add(key)
            withContext(Dispatchers.IO) {
                prefs.edit().putStringSet("favorites", favIds).commit()
            }

            val (contactsUpdated, favs) = withContext(Dispatchers.IO) {
                val updated = _contacts.value.map { c ->
                    val isFav = getContactNumbers(c).any { favIds.contains(PhoneUtils.groupKey(it)) }
                    c.copy(isFavorite = isFav)
                }
                updated to updated.filter { it.isFavorite }.sortedByDescending { it.callCount }
            }
            _contacts.value = contactsUpdated
            _favorites.value = favs
        }
    }

    fun saveNote(number: String, note: String) {
        val n = normalize(number)
        viewModelScope.launch(Dispatchers.IO) {
            if (note.isBlank()) db.callNoteDao().deleteNote(n)
            else db.callNoteDao().upsert(CallNote(n, note.trim()))
        }
    }

    // ───────────── SETTERS PARAMÈTRES ─────────────
    fun setBlockPrivate(b: Boolean) {
        spamDetector.setBlockPrivateNumbers(b)
        _blockPrivate.value = b
    }

    fun setHideBlocked(b: Boolean) {
        prefs.edit().putBoolean("hide_blocked", b).apply()
        _hideBlocked.value = b
    }

    fun setDoNotDisturb(enabled: Boolean) {
        spamDetector.setDoNotDisturb(enabled)
        _doNotDisturb.value = enabled
    }

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

    // ───────────── RECHERCHE & AUTRES ─────────────
    fun setSearchQuery(q: String) {
        _searchQuery.value = q
    }

    fun filteredContacts(): List<Contact> {
        val q = _searchQuery.value.lowercase().trim()
        if (q.isEmpty()) return _contacts.value

        return _contacts.value
            .filter { contact ->
                contact.name.lowercase().contains(q) ||
                        getContactNumbers(contact).any { it.contains(q) }
            }
            .sortedWith(compareBy(
                // Priorité 1 : nom commence par la query → en tête de liste
                { !it.name.lowercase().startsWith(q) },
                // Priorité 2 : ordre alphabétique
                { it.name.lowercase() }
            ))
    }

    fun addToWhitelist(number: String) {
        val n = normalize(number)
        spamDetector.addToWhitelist(n)
        _whitelist.value = spamDetector.getWhitelist()
    }

    fun removeFromWhitelist(number: String) {
        val n = normalize(number)
        spamDetector.removeFromWhitelist(n)
        _whitelist.value = spamDetector.getWhitelist()
    }

    fun removeCallGroup(number: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            resolver.delete(CallLog.Calls.CONTENT_URI, "${CallLog.Calls.NUMBER} = ?", arrayOf(number))
            withContext(Dispatchers.Main) {
                _callGroups.value = _callGroups.value.filter { it.number != number }
            }
        }
    }

    fun getNote(number: String) = _notes.value[normalize(number)]
    fun isReported(number: String) = _reportedNumbers.value[normalize(number)]
    fun clearReportFeedback() { _reportFeedback.value = null }
    fun isWhitelisted(number: String) = spamDetector.isWhitelisted(normalize(number))

    private fun getFavoriteIds(): Set<String> =
        prefs.getStringSet("favorites", emptySet()) ?: emptySet()

    fun buildNumberToNameMap(): Map<String, String> =
        _contacts.value.flatMap { c -> getContactNumbers(c).map { it to c.name } }.toMap()

    // ───────────── SUGGESTIONS CLAVIER ─────────────
    fun getSuggestions(input: String): List<Contact> {
        if (input.length < 3) return emptyList()
        val normalized = normalize(input)
        if (normalized.length < 3) return emptyList()

        return _contacts.value
            .filter { contact ->
                getContactNumbers(contact).any { num ->
                    num.startsWith(normalized) ||
                            num.removePrefix("+33").startsWith(normalized.removePrefix("+33")) ||
                            num.removePrefix("0").startsWith(normalized.removePrefix("0"))
                }
            }
            .sortedByDescending { it.callCount }
            .take(3)
    }

    // ───────────── SUGGESTIONS AVEC RECHERCHE ÉTENDUE (POUR KEYPAD) ─────────────
    fun getContactsMatchingNumber(dialedNumber: String): List<Contact> {
        if (dialedNumber.isEmpty()) return emptyList()

        val normalizedDialed = normalize(dialedNumber)
        if (normalizedDialed.isEmpty()) return emptyList()

        return _contacts.value.filter { contact ->
            getContactNumbers(contact).any { phone ->
                phone.startsWith(normalizedDialed) ||
                        contact.name.contains(dialedNumber, ignoreCase = true) ||
                        phone.contains(normalizedDialed)
            }
        }.sortedBy { contact ->
            val exactMatch = getContactNumbers(contact).any {
                it.startsWith(normalizedDialed)
            }
            if (exactMatch) 0 else 1
        }.take(5)
    }

    suspend fun getTopReported() = reportRepo.getTopReported()
}