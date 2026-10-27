package uz.artic.surfethenet

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/**
 * Surfer o'qishi uchun profil: faqat ism, emoji va rang (email berilmaydi).
 * content://uz.artic.surfethenet.profile/profile
 */
class ProfileProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?
    ): Cursor {
        val p = context!!.getSharedPreferences("profile", Context.MODE_PRIVATE)
        val c = MatrixCursor(arrayOf("name", "emoji", "color"), 1)
        c.addRow(arrayOf(
            p.getString("name", ""),
            p.getString("emoji", ""),
            p.getString("color", "#8ab4f8")
        ))
        return c
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.uz.artic.surfethenet.profile"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
}
