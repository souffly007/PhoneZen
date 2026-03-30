package fr.bonobo.phonezen.data.model

data class Contact(
    val contactId: Long,
    val name: String,
    val phoneNumbers: List<String> = emptyList(),
    val photoUri: String?,
    val isFavorite: Boolean = false,
    val callCount: Int = 0,
    val phoneType: String? = null
)