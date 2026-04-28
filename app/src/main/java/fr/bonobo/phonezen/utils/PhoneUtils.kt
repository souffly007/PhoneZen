package fr.bonobo.phonezen.utils

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.CallLog
import android.telephony.SubscriptionManager
import android.util.Log
import fr.bonobo.phonezen.data.model.Contact
import fr.bonobo.phonezen.data.model.CallEntry
import fr.bonobo.phonezen.data.model.CallGroup
import java.text.SimpleDateFormat
import java.util.*

object PhoneUtils {

    // ── LOGIQUE DE NETTOYAGE ──────────────────────────────────────────────────
    fun normalizeNumber(number: String?): String {
        if (number == null) return ""
        var n = number.trim().replace(Regex("[^0-9+]"), "")
        if (n.startsWith("+33")) n = "0" + n.substring(3)
        if (n.startsWith("0033")) n = "0" + n.substring(4)
        return n
    }

    fun groupKey(number: String?): String {
        val n = normalizeNumber(number)
        if (n.isEmpty()) return "UNKNOWN"
        val digits = n.filter { it.isDigit() }
        return if (digits.length >= 9) digits.takeLast(9) else digits
    }

    fun formatDuration(seconds: Long): String {
        return when {
            seconds == 0L -> "0s"
            seconds < 60  -> "${seconds}s"
            else -> "%02d:%02d".format(seconds / 60, seconds % 60)
        }
    }

    fun formatTimestamp(ts: Long): String {
        val now  = System.currentTimeMillis()
        val diff = now - ts
        val locale = Locale.FRANCE
        return when {
            diff < 60_000       -> "À l'instant"
            diff < 3_600_000    -> "${diff / 60_000} min"
            diff < 86_400_000   -> SimpleDateFormat("HH:mm", locale).format(Date(ts))
            diff < 604_800_000L -> SimpleDateFormat("EEE HH:mm", locale).format(Date(ts))
            else                -> SimpleDateFormat("dd/MM/yy", locale).format(Date(ts))
        }
    }

    fun isAnonymousNumber(number: String?): Boolean {
        if (number.isNullOrBlank()) return true
        val n = number.lowercase().trim()
        return n == "-1" || n == "-2" || n == "unknown" || n == "private" ||
                n == "hidden" || n.contains("anonymous")
    }

    // ── RÉSOLUTION CONTACT POUR L'ANALYSE SPAM ───────────────────────────────
    fun resolveContactInfo(context: Context, rawNumber: String?): Pair<Boolean, Boolean> {
        if (rawNumber.isNullOrBlank()) return Pair(false, false)
        val normalized = normalizeNumber(rawNumber)
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(normalized)
            )
            context.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.PhoneLookup._ID,
                    ContactsContract.PhoneLookup.STARRED
                ),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val starred = cursor.getInt(
                        cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.STARRED)
                    )
                    Pair(true, starred == 1)
                } else {
                    Pair(false, false)
                }
            } ?: Pair(false, false)
        } catch (e: Exception) {
            Log.e("PhoneUtils", "resolveContactInfo error: ${e.message}")
            Pair(false, false)
        }
    }

    // ── CONTACTS ─────────────────────────────────────────────────────────────
    fun loadContacts(context: Context, favoriteIds: Set<String>): List<Contact> {
        data class ContactData(
            val id: Long,
            val name: String,
            val photoUri: String?,
            val numbers: MutableList<String> = mutableListOf(),
            val types: MutableList<String>   = mutableListOf()
        )
        val map        = linkedMapOf<Long, ContactData>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
        )

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection, null, null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )?.use { c ->
            val idIdx    = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIdx  = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx   = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val typeIdx  = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
            val photoIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)

            while (c.moveToNext()) {
                val id    = c.getLong(idIdx)
                val num   = c.getString(numIdx) ?: continue
                val type  = c.getInt(typeIdx)
                val label = ContactsContract.CommonDataKinds.Phone
                    .getTypeLabel(context.resources, type, "").toString()

                val existing = map[id]
                if (existing == null) {
                    map[id] = ContactData(
                        id       = id,
                        name     = c.getString(nameIdx) ?: "Inconnu",
                        photoUri = if (photoIdx >= 0) c.getString(photoIdx) else null,
                        numbers  = mutableListOf(num),
                        types    = mutableListOf(label)
                    )
                } else {
                    val newKey = groupKey(num)
                    if (existing.numbers.none { groupKey(it) == newKey }) {
                        existing.numbers.add(num)
                        existing.types.add(label)
                    }
                }
            }
        }

        return map.values.map { data ->
            val sortedNumbers = data.numbers.sortedWith(compareByDescending { num ->
                val idx   = data.numbers.indexOf(num)
                val label = data.types.getOrNull(idx)?.lowercase() ?: ""
                if (label.contains("mobile") || label.contains("portable") ||
                    label.contains("cell")) 1 else 0
            })

            val firstNum = sortedNumbers.firstOrNull()
            Contact(
                contactId    = data.id,
                name         = data.name,
                phoneNumbers = sortedNumbers,
                photoUri     = data.photoUri,
                isFavorite   = if (firstNum != null) favoriteIds.contains(groupKey(firstNum)) else false,
                callCount    = 0
            )
        }
    }

    // ── JOURNAL ───────────────────────────────────────────────────────────────

    private fun buildContactsLookupMap(context: Context): Map<String, Pair<String, String?>> {
        val result     = hashMapOf<String, Pair<String, String?>>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
        )
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection, null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0) ?: continue
                val num  = c.getString(1) ?: continue
                val key  = groupKey(num)
                if (!result.containsKey(key)) result[key] = Pair(name, c.getString(2))
            }
        }
        return result
    }

    // ── BUG 3 : lecture du vrai slot SIM via SubscriptionManager ─────────────
    /**
     * Construit une map  accountId (String) → numéro de slot physique (0 ou 1).
     *
     * Sur la plupart des ROMs l'accountId du CallLog correspond au
     * subscriptionId sous forme de chaîne ("1", "2"…).
     * SubscriptionManager nous donne le simSlotIndex réel pour chaque sub.
     *
     * Fallback : si READ_PHONE_STATE n'est pas accordé ou si l'API échoue,
     * la map est vide et loadCallGroups retombe sur simSlot = -1.
     */
    private fun buildSimSlotMap(context: Context): Map<String, Int> {
        val result = hashMapOf<String, Int>()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return result
        return try {
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                    as? SubscriptionManager ?: return result
            val infos = sm.activeSubscriptionInfoList ?: return result
            for (info in infos) {
                val slot  = info.simSlotIndex          // 0 ou 1, fiable
                val subId = info.subscriptionId.toString()
                result[subId] = slot
                // Certains ROMs stockent l'accountId différemment — on couvre les variantes
                result["SIM${slot + 1}"]     = slot
                result["slot$slot"]          = slot
                result["sub${info.subscriptionId}"] = slot
            }
            result
        } catch (e: Exception) {
            Log.e("PhoneUtils", "buildSimSlotMap error: ${e.message}")
            result
        }
    }

    fun loadCallGroups(context: Context, favoriteIds: Set<String>): List<CallGroup> {
        val map            = linkedMapOf<String, MutableList<CallEntry>>()
        val contactsLookup = buildContactsLookupMap(context)
        // Bug 3 : map accountId → slot physique réel
        val simSlotMap     = buildSimSlotMap(context)

        val baseProjection = mutableListOf(
            CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.TYPE,
            CallLog.Calls.DURATION, CallLog.Calls.DATE
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1)
                add(CallLog.Calls.PHONE_ACCOUNT_ID)
        }

        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI, baseProjection.toTypedArray(),
            null, null, "${CallLog.Calls.DATE} DESC"
        )?.use { c ->
            val idIdx   = c.getColumnIndex(CallLog.Calls._ID)
            val numIdx  = c.getColumnIndex(CallLog.Calls.NUMBER)
            val typeIdx = c.getColumnIndex(CallLog.Calls.TYPE)
            val durIdx  = c.getColumnIndex(CallLog.Calls.DURATION)
            val dateIdx = c.getColumnIndex(CallLog.Calls.DATE)
            val simIdx  = if (baseProjection.contains(CallLog.Calls.PHONE_ACCOUNT_ID))
                c.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID) else -1

            while (c.moveToNext()) {
                val rawNum  = c.getString(numIdx) ?: ""
                val entryId = c.getLong(idIdx)
                val key     = if (isAnonymousNumber(rawNum)) "ANON_$entryId" else groupKey(rawNum)

                // ── Bug 3 : résolution slot via SubscriptionManager ───────────
                val simSlot = if (simIdx >= 0) {
                    val accId = c.getString(simIdx) ?: ""
                    // Cherche dans la map construite par SubscriptionManager
                    simSlotMap[accId]
                    // Fallback : extrait le dernier chiffre de l'accountId
                        ?: accId.filter { it.isDigit() }.lastOrNull()
                            ?.digitToInt()
                            ?.let { d -> if (d == 0 || d == 1) d else -1 }
                        ?: -1
                } else -1

                map.getOrPut(key) { mutableListOf() }.add(
                    CallEntry(
                        id        = entryId,
                        number    = rawNum,
                        name      = null,
                        type      = c.getInt(typeIdx),
                        duration  = c.getLong(durIdx),
                        timestamp = c.getLong(dateIdx),
                        simSlot   = simSlot
                    )
                )
            }
        }

        return map.entries.map { (key, entries) ->
            val firstNum = entries.first().number
            val isAnon   = isAnonymousNumber(firstNum)
            val cached   = if (isAnon) null else contactsLookup[key]

            CallGroup(
                number     = firstNum,
                name       = cached?.first,
                photoUri   = cached?.second,
                calls      = entries,
                isFavorite = if (isAnon) false else favoriteIds.contains(key)
            )
        }
    }

    // ── BUG 4 : lookupContact avec double tentative de format ─────────────────
    /**
     * Recherche le nom et la photo d'un contact à partir d'un numéro.
     *
     * PhoneLookup est normalement format-agnostique, mais sur certains ROMs
     * (Realme / OPPO / Xiaomi) la correspondance +33XXXXXXXXX ↔ 0XXXXXXXXX
     * échoue silencieusement.
     *
     * Stratégie :
     *  1. Tentative avec le numéro brut (laisse Android gérer)
     *  2. Si échec → tentative avec le numéro normalisé (0XXXXXXXXX)
     *  3. Si échec → tentative avec le format international (+33XXXXXXXXX)
     */
    fun lookupContact(context: Context, number: String?): Pair<String?, String?> {
        if (number.isNullOrBlank()) return Pair(null, null)

        val candidates = buildList {
            add(number)                          // brut tel que reçu
            val norm = normalizeNumber(number)
            if (norm != number) add(norm)        // format 0XXXXXXXXX
            // format +33XXXXXXXXX si le numéro commence par 0
            if (norm.startsWith("0") && norm.length == 10) {
                add("+33" + norm.substring(1))
            }
        }.distinct()

        for (candidate in candidates) {
            val result = queryPhoneLookup(context, candidate)
            if (result.first != null) return result
        }
        return Pair(null, null)
    }

    private fun queryPhoneLookup(context: Context, number: String): Pair<String?, String?> {
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            )
            context.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.PhoneLookup.DISPLAY_NAME,
                    ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI
                ),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) Pair(c.getString(0), c.getString(1))
                else Pair(null, null)
            } ?: Pair(null, null)
        } catch (e: Exception) {
            Log.e("PhoneUtils", "queryPhoneLookup($number) error: ${e.message}")
            Pair(null, null)
        }
    }

    fun lookupContactName(context: Context, number: String?): String?  = lookupContact(context, number).first
    fun lookupContactPhoto(context: Context, number: String?): String? = lookupContact(context, number).second

    fun lookupContactId(context: Context, number: String): Long? {
        val normalized = normalizeNumber(number)
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(normalized)
        )
        val projection = arrayOf(ContactsContract.PhoneLookup._ID)
        return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst())
                cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup._ID))
            else null
        }
    }
}