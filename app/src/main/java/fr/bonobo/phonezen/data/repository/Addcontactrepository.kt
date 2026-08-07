// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
//
// PhoneZen is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License.class Addcontactrepository {

package fr.bonobo.phonezen.data.repository

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.RawContacts

data class NewContactData(
    val firstName : String,
    val lastName  : String,
    val phones    : List<String>,
    val emails    : List<String>,
    val account   : Account?
)

class AddContactRepository(private val context: Context) {

    private val contentResolver: ContentResolver get() = context.contentResolver

    /** Retourne la liste des comptes Android disponibles */
    fun getAvailableAccounts(): List<Account> {
        return AccountManager.get(context).accounts.toList()
    }

    /** Crée un nouveau contact. Retourne true si succès. */
    fun createContact(data: NewContactData): Boolean {
        return try {
            val ops = ArrayList<ContentProviderOperation>()

            // 1. RawContact lié au compte choisi (null = stockage local)
            ops.add(
                ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                    .withValue(RawContacts.ACCOUNT_TYPE, data.account?.type)
                    .withValue(RawContacts.ACCOUNT_NAME, data.account?.name)
                    .build()
            )

            // 2. Nom structuré
            val fullName = "${data.firstName} ${data.lastName}".trim()
            if (fullName.isNotBlank()) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                        .withValue(StructuredName.GIVEN_NAME,   data.firstName.trim())
                        .withValue(StructuredName.FAMILY_NAME,  data.lastName.trim())
                        .withValue(StructuredName.DISPLAY_NAME, fullName)
                        .build()
                )
            }

            // 3. Téléphones
            data.phones.filter { it.isNotBlank() }.forEach { phone ->
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                        .withValue(Phone.NUMBER, phone.trim())
                        .withValue(Phone.TYPE,   Phone.TYPE_MOBILE)
                        .build()
                )
            }

            // 4. Emails
            data.emails.filter { it.isNotBlank() }.forEach { email ->
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, Email.CONTENT_ITEM_TYPE)
                        .withValue(Email.ADDRESS, email.trim())
                        .withValue(Email.TYPE,    Email.TYPE_HOME)
                        .build()
                )
            }

            contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}