package com.example.eldercall

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** 主畫面：上半是聯絡人大按鈕（點一下直接撥出），下半是撥號鍵盤 */
class MainActivity : Activity() {

    private lateinit var grid: LinearLayout
    private lateinit var banner: TextView
    private lateinit var numberText: TextView
    private var typed = StringBuilder()
    private var dialerRequestFailed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = Ui.column(this)

        // ---------- 上半：聯絡人 ----------
        val top = Ui.column(this)
        val header = Ui.row(this)
        header.gravity = Gravity.CENTER_VERTICAL
        banner = Ui.text(this, "點這裡設為預設電話，來電才會顯示大字", 15f, 0xFF5D4200.toInt(), bold = true)
        banner.background = Ui.rounded(0xFFFFE9A8.toInt(), Ui.dp(this, 10))
        banner.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8))
        banner.setOnClickListener {
            if (dialerRequestFailed) Phone.openDefaultAppsSettings(this) else Phone.requestDefaultDialer(this)
        }
        header.addView(banner, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also {
            it.marginEnd = Ui.dp(this, 8)
        })
        val settings = Ui.text(this, "⚙ 設定", 15f, Ui.DIM)
        settings.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6))
        settings.background = Ui.outlined(Ui.BG, Ui.GRAY, this, 10)
        settings.setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage("請「按住」設定鈕 1 秒鐘才能進入。\n（避免不小心改到設定）")
                .setPositiveButton("知道了", null)
                .show()
        }
        settings.setOnLongClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        }
        header.addView(settings)
        top.addView(header)

        val scroll = ScrollView(this)
        grid = Ui.column(this)
        scroll.addView(grid)
        top.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).also {
            it.topMargin = Ui.dp(this, 6)
        })
        root.addView(top, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val line = View(this)
        line.setBackgroundColor(Ui.GRAY)
        root.addView(line, Ui.lp(this, 8, Ui.dp(this, 2)))

        // ---------- 下半：撥號鍵盤 ----------
        val bottom = Ui.column(this)
        val display = Ui.row(this)
        display.gravity = Gravity.CENTER_VERTICAL
        numberText = Ui.text(this, "", 32f, Ui.TEXT, bold = true)
        numberText.gravity = Gravity.CENTER
        numberText.maxLines = 1
        numberText.background = Ui.rounded(Ui.CARD, Ui.dp(this, 14))
        display.addView(numberText, LinearLayout.LayoutParams(0, Ui.dp(this, 56), 1f).also {
            it.setMargins(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0)
        })
        val del = Ui.button(this, "⌫", Ui.GRAY, 28f, Ui.TEXT)
        del.setPadding(0, 0, 0, 0)
        del.setOnClickListener {
            if (typed.isNotEmpty()) typed.setLength(typed.length - 1)
            showNumber()
        }
        del.setOnLongClickListener {
            typed.setLength(0)
            showNumber()
            true
        }
        display.addView(del, LinearLayout.LayoutParams(Ui.dp(this, 72), Ui.dp(this, 56)).also {
            it.setMargins(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0)
        })
        bottom.addView(display, Ui.lp(this, 8))

        val keypad = Ui.keypad(this, 0) { ch ->
            if (typed.length < 20) typed.append(ch)
            showNumber()
        }
        bottom.addView(keypad, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).also {
            it.topMargin = Ui.dp(this, 4)
        })

        val callBtn = Ui.button(this, "📞 撥打", Ui.GREEN, 28f)
        callBtn.setOnClickListener {
            if (typed.isEmpty()) {
                Toast.makeText(this, "請先按電話號碼", Toast.LENGTH_SHORT).show()
            } else {
                Phone.call(this, typed.toString())
                typed.setLength(0)
                showNumber()
            }
        }
        bottom.addView(callBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 64)).also {
            it.setMargins(Ui.dp(this, 3), Ui.dp(this, 6), Ui.dp(this, 3), 0)
        })
        root.addView(bottom, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        Ui.setScreen(this, root)
        handleDialIntent(intent)
        firstRunSetup()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDialIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        buildGrid()
        banner.visibility = if (Phone.isDefaultDialer(this)) View.GONE else View.VISIBLE
    }

    /** 其他 App 要求撥號時（例如點網頁上的電話），把號碼填進鍵盤 */
    private fun handleDialIntent(i: Intent?) {
        val data = i?.data ?: return
        if (data.scheme == "tel") {
            typed.setLength(0)
            typed.append(data.schemeSpecificPart.filter { it.isDigit() || it == '+' || it == '*' || it == '#' })
            showNumber()
        }
    }

    private fun showNumber() {
        val s = typed.toString()
        numberText.text = Phone.pretty(s)
        numberText.textSize = if (s.length > 12) 24f else 32f
    }

    // ---------- 第一次開啟：權限與預設電話 ----------

    private fun firstRunSetup() {
        if (Store.askedSetup(this)) return
        Store.setAskedSetup(this)
        val need = ArrayList<String>()
        for (p in listOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS)) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) need.add(p)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            need.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 1) else askDefaultDialer()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) askDefaultDialer()
    }

    private fun askDefaultDialer() {
        if (Phone.isDefaultDialer(this)) return
        AlertDialog.Builder(this)
            .setTitle("設為預設電話")
            .setMessage("把「長輩電話」設為手機的預設電話後，來電和通話中都會顯示大字畫面。\n\n下一步請選「長輩電話」，再按「設為預設」。")
            .setPositiveButton("好，下一步") { _, _ -> Phone.requestDefaultDialer(this) }
            .setNegativeButton("以後再說", null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == Phone.REQ_DEFAULT_DIALER) {
            if (Phone.isDefaultDialer(this)) {
                banner.visibility = View.GONE
                Toast.makeText(this, "設定完成，來電會顯示大字", Toast.LENGTH_LONG).show()
            } else {
                dialerRequestFailed = true
            }
        }
    }

    // ---------- 聯絡人 ----------

    private fun buildGrid() {
        grid.removeAllViews()
        val list = Store.load(this)
        if (list.isEmpty()) {
            val empty = Ui.text(this, "還沒有聯絡人\n請家人「按住」右上角的「設定」來新增", 20f, Ui.DIM)
            empty.gravity = Gravity.CENTER
            empty.setPadding(0, Ui.dp(this, 30), 0, 0)
            grid.addView(empty, Ui.lp(this, 0))
            return
        }
        var row: LinearLayout? = null
        for ((i, ct) in list.withIndex()) {
            if (i % 2 == 0) {
                row = Ui.row(this)
                grid.addView(row, Ui.lp(this, 0))
            }
            row?.addView(tile(ct), Ui.weighted(this, Ui.dp(this, 84), 4))
        }
        if (list.size % 2 == 1) row?.addView(View(this), Ui.weighted(this, Ui.dp(this, 84), 4))
    }

    private fun tile(ct: Contact): LinearLayout {
        val t = Ui.row(this)
        t.gravity = Gravity.CENTER_VERTICAL
        t.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 6), 0)
        t.background = Ui.outlined(Ui.CARD, Ui.GRAY, this, 16)
        t.addView(Ui.avatar(this, ct, 58))
        val name = Ui.text(this, ct.name, 22f, Ui.TEXT, bold = true)
        name.maxLines = 2
        t.addView(name, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also {
            it.marginStart = Ui.dp(this, 8)
        })
        t.setOnClickListener { Phone.call(this, ct.number) }
        return t
    }
}
