package com.example.eldercall

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.telecom.TelecomManager
import android.widget.Toast

/** 撥號、預設電話 App 設定、號碼查名字 */
object Phone {

    const val REQ_DEFAULT_DIALER = 501

    fun isDefaultDialer(c: Context): Boolean {
        val tm = c.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        return tm.defaultDialerPackage == c.packageName
    }

    /** 跳出系統視窗，請使用者把本 App 設為預設電話 App */
    fun requestDefaultDialer(a: Activity) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val rm = a.getSystemService(RoleManager::class.java)
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                    a.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER), REQ_DEFAULT_DIALER)
                    return
                }
            }
            val i = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
            i.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, a.packageName)
            a.startActivityForResult(i, REQ_DEFAULT_DIALER)
        } catch (e: Exception) {
            openDefaultAppsSettings(a)
        }
    }

    /** 系統不再跳出視窗時，改開「預設應用程式」設定頁讓使用者手動選 */
    fun openDefaultAppsSettings(a: Activity) {
        try {
            a.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            Toast.makeText(a, "請在「電話」或「撥號」項目選擇「長輩電話」", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(a, "請到手機設定 → 應用程式 → 預設應用程式，把電話改成「長輩電話」", Toast.LENGTH_LONG).show()
        }
    }

    /** 撥出電話。是預設電話 App 時經由系統電話服務撥出，通話畫面就會是本 App 的大字畫面 */
    fun call(a: Activity, number: String) {
        val clean = number.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        if (clean.isEmpty()) return
        val uri = Uri.fromParts("tel", clean, null)
        val canCall = a.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        if (canCall && isDefaultDialer(a)) {
            try {
                val tm = a.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                tm.placeCall(uri, Bundle())
                return
            } catch (e: Exception) {
                // 改用下面的方式
            }
        }
        try {
            a.startActivity(Intent(if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri))
        } catch (e: Exception) {
            Toast.makeText(a, "這支手機無法撥打電話", Toast.LENGTH_LONG).show()
        }
    }

    /** 先找本 App 的聯絡人，再找手機通訊錄；都找不到就回傳 null */
    fun nameFor(c: Context, number: String): String? {
        if (number.isEmpty()) return null
        Store.findByNumber(c, number)?.let { return it.name }
        if (c.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            val cur = c.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
            var name: String? = null
            if (cur != null) {
                if (cur.moveToFirst()) name = cur.getString(0)
                cur.close()
            }
            name
        } catch (e: Exception) {
            null
        }
    }

    /** 把號碼排成好讀的樣子，例如 0912 345 678 */
    fun pretty(n: String): String {
        val d = n.filter { it.isDigit() }
        return when {
            d.length == 10 && d.startsWith("09") -> "${d.substring(0, 4)} ${d.substring(4, 7)} ${d.substring(7)}"
            d.length == 10 && d.startsWith("0") -> "${d.substring(0, 2)} ${d.substring(2, 6)} ${d.substring(6)}"
            d.length == 9 && d.startsWith("0") -> "${d.substring(0, 2)} ${d.substring(2, 5)} ${d.substring(5)}"
            else -> n
        }
    }
}
