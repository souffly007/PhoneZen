// ContactCache.kt
package fr.bonobo.phonezen.utils

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract

object ContactCache {
    private val cache = mutableMapOf<String, ContactInfo>()
    private val CACHE_DURATION = 5 * 60 * 1000 // 5 minutes

    data class ContactInfo(
        val name: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    fun put(number: String, name: String) {
        val normalized = PhoneUtils.normalizeNumber(number)
        cache[normalized] = ContactInfo(name)
    }

    fun get(number: String): String? {
        val normalized = PhoneUtils.normalizeNumber(number)
        val info = cache[normalized]
        if (info == null) return null
        if (System.currentTimeMillis() - info.timestamp > CACHE_DURATION) {
            cache.remove(normalized)
            return null
        }
        return info.name
    }

    fun clear() {
        cache.clear()
    }

    // Optionnel : pré-remplir le cache depuis les contacts
    fun preloadFromContacts(contentResolver: ContentResolver) {
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        )
        val cursor: Cursor? = contentResolver.query(uri, projection, null, null, null)
        cursor?.use {
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            while (it.moveToNext()) {
                val number = it.getString(numberIndex)
                val name = it.getString(nameIndex)
                put(number, name)
            }
        }
    }
}