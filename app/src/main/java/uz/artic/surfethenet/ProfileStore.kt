package uz.artic.surfethenet

import android.content.Context
import org.json.JSONObject

/** Profil ma'lumotlari faqat shu telefonda (SharedPreferences) saqlanadi. */
object ProfileStore {
    private const val PREFS = "profile"
    private const val DEFAULT_COLOR = "#8ab4f8"

    fun json(c: Context): String {
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return JSONObject()
            .put("name", p.getString("name", ""))
            .put("email", p.getString("email", ""))
            .put("emoji", p.getString("emoji", ""))
            .put("color", p.getString("color", DEFAULT_COLOR))
            .toString()
    }

    fun save(c: Context, json: String) {
        val j = try { JSONObject(json) } catch (e: Exception) { return }
        val color = j.optString("color", DEFAULT_COLOR)
            .let { if (Regex("^#[0-9a-fA-F]{6}$").matches(it)) it else DEFAULT_COLOR }
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("name", j.optString("name").take(40))
            .putString("email", j.optString("email").take(100))
            .putString("emoji", j.optString("emoji").take(8))
            .putString("color", color)
            .apply()
    }
}
