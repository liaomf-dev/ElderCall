package com.example.eldercall

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class Contact(
    var id: Long,
    var name: String,
    var number: String
)

/** 聯絡人存在手機裡（SharedPreferences），照片存在 App 的私人資料夾 */
object Store {

    private fun prefs(c: Context): SharedPreferences =
        c.getSharedPreferences("elder_call", Context.MODE_PRIVATE)

    fun load(c: Context): MutableList<Contact> {
        val list = ArrayList<Contact>()
        try {
            val arr = JSONArray(prefs(c).getString("contacts", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(Contact(o.getLong("id"), o.getString("name"), o.getString("number")))
            }
        } catch (e: Exception) {
            // 資料損毀時回傳空清單
        }
        return list
    }

    fun save(c: Context, list: List<Contact>) {
        val arr = JSONArray()
        for (ct in list) {
            val o = JSONObject()
            o.put("id", ct.id)
            o.put("name", ct.name)
            o.put("number", ct.number)
            arr.put(o)
        }
        prefs(c).edit().putString("contacts", arr.toString()).apply()
    }

    fun get(c: Context, id: Long): Contact? = load(c).firstOrNull { it.id == id }

    fun upsert(c: Context, ct: Contact) {
        val list = load(c)
        val idx = list.indexOfFirst { it.id == ct.id }
        if (idx >= 0) list[idx] = ct else list.add(ct)
        save(c, list)
    }

    fun delete(c: Context, id: Long) {
        save(c, load(c).filter { it.id != id })
        photoFile(c, id).delete()
    }

    /** 比對號碼末 9 碼，讓 0912… 和 +886912… 視為同一支電話 */
    fun findByNumber(c: Context, number: String): Contact? {
        val key = tail(number)
        if (key.length < 6) return null
        return load(c).firstOrNull { tail(it.number) == key }
    }

    private fun tail(s: String): String {
        val d = s.filter { it.isDigit() }
        return if (d.length > 9) d.substring(d.length - 9) else d
    }

    fun photoFile(c: Context, id: Long): File = File(c.filesDir, "photo_$id.jpg")

    fun askedSetup(c: Context): Boolean = prefs(c).getBoolean("asked_setup", false)
    fun setAskedSetup(c: Context) = prefs(c).edit().putBoolean("asked_setup", true).apply()
}
