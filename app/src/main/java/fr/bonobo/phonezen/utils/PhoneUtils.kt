package fr.bonobo.phonezen.utils

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import fr.bonobo.phonezen.data.model.Contact
import fr.bonobo.phonezen.data.model.CallEntry
import fr.bonobo.phonezen.data.model.CallGroup
import android.provider.CallLog

object PhoneUtils {

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
            else -> {
                val mins = seconds / 60
                val secs = seconds % 60
                "%02d:%02d".format(mins, secs)
            }
        }
    }

    fun formatTimestamp(ts: Long): String {
        val now    = System.currentTimeMillis()
        val diff   = now - ts
        val locale = java.util.Locale.FRANCE
        return when {
            diff < 0            -> "Futur"
            diff < 60_000       -> "À l'instant"
            diff < 3_600_000    -> "${diff / 60_000} min"
            diff < 86_400_000   -> java.text.SimpleDateFormat("HH:mm", locale).format(java.util.Date(ts))
            diff < 604_800_000L -> java.text.SimpleDateFormat("EEE HH:mm", locale).format(java.util.Date(ts))
            else                -> java.text.SimpleDateFormat("dd/MM/yy", locale).format(java.util.Date(ts))
        }
    }

    /** Charge contacts dédupliqués par CONTACT_ID */
    fun loadContacts(context: Context, favoriteIds: Set<String>): List<Contact> {
        val map = linkedMapOf<Long, Contact>()
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
                val name  = c.getString(nameIdx) ?: "Inconnu"
                val num   = c.getString(numIdx)  ?: continue
                val type  = c.getInt(typeIdx)
                val photo = if (photoIdx >= 0) c.getString(photoIdx) else null
                val key   = groupKey(num)
                if (!map.containsKey(id) || type == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE) {
                    // On convertit le code 'type' (Int) en libellé (String) comme "Mobile" ou "Maison"
                    val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                        context.resources,
                        type,
                        ""
                    ).toString()

                    map[id] = Contact(
                        contactId   = id,
                        name        = name,
                        phoneNumber = num,
                        photoUri    = photo,
                        isFavorite  = favoriteIds.contains(key),
                        phoneType   = label, // On passe le String ici
                        callCount   = 0      // On initialise le compteur à 0
                    )
                }
            }
        }
        return map.values.toList()
    }

    /** Charge le journal d'appels groupé avec lookup photo temps réel */
    fun loadCallGroups(context: Context, favoriteIds: Set<String>): List<CallGroup> {
        val map      = linkedMapOf<String, MutableList<CallEntry>>()
        val nameMap  = hashMapOf<String, String?>()
        val photoMap = hashMapOf<String, String?>()

        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DURATION,
            CallLog.Calls.DATE,
            CallLog.Calls.CACHED_PHOTO_URI
        )

        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection, null, null,
            "${CallLog.Calls.DATE} DESC"
        )?.use { c ->
            val idIdx    = c.getColumnIndex(CallLog.Calls._ID)
            val numIdx   = c.getColumnIndex(CallLog.Calls.NUMBER)
            val nameIdx  = c.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val typeIdx  = c.getColumnIndex(CallLog.Calls.TYPE)
            val durIdx   = c.getColumnIndex(CallLog.Calls.DURATION)
            val dateIdx  = c.getColumnIndex(CallLog.Calls.DATE)
            val photoIdx = c.getColumnIndex(CallLog.Calls.CACHED_PHOTO_URI)

            while (c.moveToNext()) {
                val num   = c.getString(numIdx) ?: ""
                val key   = groupKey(num)
                val name  = if (nameIdx  >= 0) c.getString(nameIdx)  else null
                val photo = if (photoIdx >= 0) c.getString(photoIdx) else null

                val entry = CallEntry(
                    id        = if (idIdx >= 0) c.getLong(idIdx) else 0L,
                    number    = num,
                    name      = name,
                    type      = c.getInt(typeIdx),
                    duration  = c.getLong(durIdx),
                    timestamp = c.getLong(dateIdx)
                )

                map.getOrPut(key) { mutableListOf() }.add(entry)
                if (name  != null) nameMap[key]  = name
                if (photo != null) photoMap[key] = photo
            }
        }

        return map.entries.map { (key, entries) ->
            val number = entries.first().number

            // Si pas de photo dans le cache du journal → lookup temps réel dans les contacts
            val resolvedPhoto = photoMap[key] ?: lookupContactPhoto(context, number)
            // Idem pour le nom
            val resolvedName  = nameMap[key]  ?: lookupContactName(context, number)

            CallGroup(
                number     = number,
                name       = resolvedName,
                photoUri   = resolvedPhoto,
                calls      = entries,
                isFavorite = favoriteIds.contains(key)
            )
        }
    }

    /** Lookup photo depuis numéro via PhoneLookup */
    fun lookupContactPhoto(context: Context, number: String?): String? {
        if (number.isNullOrBlank()) return null
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            )
            context.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.PhoneLookup.CONTACT_ID,
                    ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI
                ),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val photoIdx = c.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI)
                    if (photoIdx >= 0) c.getString(photoIdx) else null
                } else null
            }
        } catch (e: Exception) { null }
    }

    /** Lookup nom depuis numéro - Utilisé pour l'InCall UI */
    fun lookupContactName(context: Context, number: String?): String? {
        if (number.isNullOrBlank()) return null
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (e: Exception) { null }
    }

    /** Lookup nom + photo en une seule requête — utilisé par InCallScreen */
    fun lookupContact(context: Context, number: String?): Pair<String?, String?> {
        if (number.isNullOrBlank()) return Pair(null, null)
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
                if (c.moveToFirst()) {
                    val name  = c.getString(0)
                    val photo = if (c.columnCount > 1) c.getString(1) else null
                    Pair(name, photo)
                } else Pair(null, null)
            } ?: Pair(null, null)
        } catch (e: Exception) { Pair(null, null) }
    }
}