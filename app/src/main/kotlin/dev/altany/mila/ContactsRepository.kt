package dev.altany.mila

import android.content.Context
import android.provider.ContactsContract
import dev.altany.mila.match.Contact

/**
 * Reads the phone's contacts. Matching happens in memory rather than in a SQL
 * LIKE query, because a spoken Greek name has to be compared against Greeklish
 * spellings too — something the provider cannot do.
 */
object ContactsRepository {

    fun loadAll(context: Context): List<Contact> {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.STARRED,
        )

        val contacts = mutableListOf<Contact>()
        val seen = mutableSetOf<Pair<String, String>>()

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            null,
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(projection[0])
            val numberIndex = cursor.getColumnIndex(projection[1])
            val starredIndex = cursor.getColumnIndex(projection[2])
            if (nameIndex < 0 || numberIndex < 0) return emptyList()

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex)?.trim().orEmpty()
                val number = cursor.getString(numberIndex)?.trim().orEmpty()
                if (name.isEmpty() || number.isEmpty()) continue

                // The same person shows up once per phone-number row; keep
                // distinct numbers but drop exact duplicates.
                val key = name to number.filter { it.isDigit() || it == '+' }
                if (seen.add(key)) {
                    val starred = starredIndex >= 0 && cursor.getInt(starredIndex) == 1
                    contacts += Contact(name, number, isFavourite = starred)
                }
            }
        }
        return contacts
    }
}
