package com.example.eldercall

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView

/** 家人設定：聯絡人清單、新增聯絡人、預設電話狀態 */
class SettingsActivity : Activity() {

    private lateinit var listBox: LinearLayout
    private lateinit var dialerBox: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = Ui.column(this)

        val back = Ui.text(this, "← 返回", 20f, Ui.BLUE)
        back.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4))
        back.setOnClickListener { finish() }
        root.addView(back)
        root.addView(Ui.text(this, "家人設定", 30f, Ui.TEXT, bold = true), Ui.lp(this, 6))

        dialerBox = Ui.column(this)
        root.addView(dialerBox, Ui.lp(this, 12))

        root.addView(Ui.text(this, "聯絡人（點一下可以修改或刪除）", 16f, Ui.DIM), Ui.lp(this, 16))
        val scroll = ScrollView(this)
        listBox = Ui.column(this)
        scroll.addView(listBox)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).also {
            it.topMargin = Ui.dp(this, 6)
        })

        val add = Ui.button(this, "＋ 新增聯絡人", Ui.GREEN, 24f)
        add.setOnClickListener { startActivity(Intent(this, EditContactActivity::class.java)) }
        root.addView(add, Ui.lp(this, 12, Ui.dp(this, 64)))

        Ui.setScreen(this, root)
    }

    override fun onResume() {
        super.onResume()
        buildList()
        buildDialerStatus()
    }

    private fun buildDialerStatus() {
        dialerBox.removeAllViews()
        if (Phone.isDefaultDialer(this)) {
            dialerBox.addView(Ui.text(this, "✓ 已設為預設電話，來電會顯示大字", 17f, Ui.GREEN, bold = true))
        } else {
            val b = Ui.button(this, "設為預設電話（來電顯示大字）", 0xFFFFE9A8.toInt(), 18f, 0xFF5D4200.toInt())
            b.setOnClickListener { Phone.requestDefaultDialer(this) }
            dialerBox.addView(b, Ui.lp(this, 0, Ui.dp(this, 56)))
            val manual = Ui.text(this, "沒有跳出視窗？點這裡到手機設定手動選擇", 14f, Ui.BLUE)
            manual.setPadding(0, Ui.dp(this, 6), 0, 0)
            manual.setOnClickListener { Phone.openDefaultAppsSettings(this) }
            dialerBox.addView(manual)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        buildDialerStatus()
    }

    private fun buildList() {
        listBox.removeAllViews()
        val list = Store.load(this)
        if (list.isEmpty()) {
            listBox.addView(Ui.text(this, "還沒有聯絡人，按下方的「新增聯絡人」", 18f, Ui.DIM), Ui.lp(this, 8))
            return
        }
        for (ct in list) {
            val row = Ui.row(this)
            row.gravity = Gravity.CENTER_VERTICAL
            val p = Ui.dp(this, 10)
            row.setPadding(p, p, p, p)
            row.background = Ui.rounded(Ui.CARD, Ui.dp(this, 14))
            row.addView(Ui.avatar(this, ct, 48))
            val info = Ui.text(this, ct.name + "\n" + Phone.pretty(ct.number), 18f, Ui.TEXT)
            row.addView(info, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also {
                it.marginStart = Ui.dp(this, 12)
            })
            row.addView(Ui.text(this, "›", 30f, Ui.DIM))
            row.setOnClickListener {
                val i = Intent(this, EditContactActivity::class.java)
                i.putExtra(EditContactActivity.EXTRA_ID, ct.id)
                startActivity(i)
            }
            listBox.addView(row, Ui.lp(this, 8))
        }
        val spacer = View(this)
        listBox.addView(spacer, Ui.lp(this, 0, Ui.dp(this, 8)))
    }
}
