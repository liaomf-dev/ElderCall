package com.example.eldercall

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.telecom.Call
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** 來電與通話中的大字畫面 */
class InCallActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var stateText: TextView
    private lateinit var nameText: TextView
    private lateinit var numberText: TextView
    private lateinit var avatarBox: FrameLayout
    private lateinit var keypad: LinearLayout
    private lateinit var tools: LinearLayout
    private lateinit var speakerBtn: Button
    private lateinit var keypadBtn: Button
    private lateinit var ringingRow: LinearLayout
    private lateinit var hangupBtn: Button

    private var shownNumber: String? = null
    private var finishing = false
    private var proximity: PowerManager.WakeLock? = null

    private val listener = Runnable { handler.post { refresh() } }
    private val tick = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1000)
        }
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 鎖定畫面時也要能顯示並點亮螢幕
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = Ui.column(this)
        root.gravity = Gravity.CENTER_HORIZONTAL

        stateText = Ui.text(this, "", 26f, Ui.DIM)
        stateText.gravity = Gravity.CENTER
        root.addView(stateText, Ui.lp(this, 24))

        avatarBox = FrameLayout(this)
        root.addView(avatarBox, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = Ui.dp(this, 20) })

        nameText = Ui.text(this, "", 42f, Ui.TEXT, bold = true)
        nameText.gravity = Gravity.CENTER
        root.addView(nameText, Ui.lp(this, 16))
        numberText = Ui.text(this, "", 26f, Ui.DIM)
        numberText.gravity = Gravity.CENTER
        root.addView(numberText, Ui.lp(this, 4))

        // 中間：通話中的按鍵盤（例如語音選單要「按 1」）
        keypad = Ui.keypad(this, 60) { key -> sendTone(key) }
        keypad.visibility = View.GONE
        root.addView(keypad, Ui.lp(this, 16))

        val spacer = View(this)
        root.addView(spacer, LinearLayout.LayoutParams(1, 0, 1f))

        // 通話中的工具：擴音、按鍵
        tools = Ui.row(this)
        speakerBtn = Ui.button(this, "擴音", Ui.KEY, 24f, Ui.TEXT)
        speakerBtn.setOnClickListener {
            CallManager.setSpeaker(!CallManager.isSpeakerOn())
            handler.postDelayed({ refresh() }, 300)
        }
        keypadBtn = Ui.button(this, "按鍵", Ui.KEY, 24f, Ui.TEXT)
        keypadBtn.setOnClickListener {
            keypad.visibility = if (keypad.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            refresh()
        }
        tools.addView(speakerBtn, Ui.weighted(this, Ui.dp(this, 72), 6))
        tools.addView(keypadBtn, Ui.weighted(this, Ui.dp(this, 72), 6))
        root.addView(tools, Ui.lp(this, 8))

        // 來電時：拒接、接聽
        ringingRow = Ui.row(this)
        val decline = Ui.button(this, "拒接", Ui.RED, 32f)
        decline.setOnClickListener { CallManager.decline() }
        val answer = Ui.button(this, "接聽", Ui.GREEN, 32f)
        answer.setOnClickListener { CallManager.answer() }
        ringingRow.addView(decline, Ui.weighted(this, Ui.dp(this, 110), 6))
        ringingRow.addView(answer, Ui.weighted(this, Ui.dp(this, 110), 6))
        root.addView(ringingRow, Ui.lp(this, 8))

        // 通話中：掛斷
        hangupBtn = Ui.button(this, "掛斷", Ui.RED, 34f)
        hangupBtn.setOnClickListener { CallManager.hangUp() }
        val hlp = Ui.lp(this, 8, Ui.dp(this, 100))
        hlp.setMargins(Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 6))
        root.addView(hangupBtn, hlp)

        Ui.setScreen(this, root)

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            proximity = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "ElderCall:proximity")
        }
    }

    override fun onResume() {
        super.onResume()
        CallManager.addListener(listener)
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        CallManager.removeListener(listener)
        handler.removeCallbacks(tick)
    }

    override fun onDestroy() {
        setProximity(false)
        super.onDestroy()
    }

    private fun refresh() {
        val call = CallManager.primary()
        val state = if (call != null) CallManager.stateOf(call) else Call.STATE_DISCONNECTED

        if (call != null) {
            val number = CallManager.number(call)
            if (number != shownNumber) {
                shownNumber = number
                val name = Phone.nameFor(this, number)
                nameText.text = name ?: Phone.pretty(number).ifEmpty { "未知號碼" }
                numberText.text = if (name != null) Phone.pretty(number) else ""
                avatarBox.removeAllViews()
                val ct = Store.findByNumber(this, number)
                    ?: Contact(-1, name ?: "☎", number)
                avatarBox.addView(Ui.avatar(this, ct, 150))
            }
        }

        val ringing = state == Call.STATE_RINGING
        val ended = state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING
        stateText.text = when (state) {
            Call.STATE_RINGING -> "來電"
            Call.STATE_DIALING, Call.STATE_CONNECTING -> "撥號中…"
            Call.STATE_ACTIVE -> "通話中  " + elapsed(call)
            Call.STATE_HOLDING -> "保留中"
            else -> "通話結束"
        }
        ringingRow.visibility = if (ringing) View.VISIBLE else View.GONE
        hangupBtn.visibility = if (!ringing && !ended) View.VISIBLE else View.GONE
        tools.visibility = if (!ringing && !ended) View.VISIBLE else View.GONE
        if (ringing || ended) keypad.visibility = View.GONE

        val speaker = CallManager.isSpeakerOn()
        speakerBtn.text = if (speaker) "擴音：開" else "擴音"
        speakerBtn.background = Ui.outlined(if (speaker) 0xFFFFE9A8.toInt() else Ui.KEY, Ui.GRAY, this, 20)
        keypadBtn.text = if (keypad.visibility == View.VISIBLE) "收起按鍵" else "按鍵"

        // 貼近耳朵時關螢幕，避免臉頰誤觸
        setProximity(state == Call.STATE_ACTIVE && !speaker && keypad.visibility != View.VISIBLE)

        if (ended && !finishing) {
            finishing = true
            setProximity(false)
            handler.postDelayed({
                val p = CallManager.primary()
                if (p == null || CallManager.stateOf(p) == Call.STATE_DISCONNECTED) finish() else finishing = false
            }, 1500)
        }
    }

    private fun elapsed(call: Call?): String {
        val start = call?.details?.connectTimeMillis ?: 0L
        if (start <= 0L) return ""
        val sec = ((System.currentTimeMillis() - start) / 1000).coerceAtLeast(0L)
        return String.format("%d:%02d", sec / 60, sec % 60)
    }

    private fun sendTone(key: Char) {
        val call = CallManager.primary() ?: return
        call.playDtmfTone(key)
        handler.postDelayed({ call.stopDtmfTone() }, 200)
    }

    private fun setProximity(on: Boolean) {
        val wl = proximity ?: return
        try {
            if (on && !wl.isHeld) wl.acquire(3 * 60 * 60 * 1000L)
            if (!on && wl.isHeld) wl.release()
        } catch (e: Exception) {
        }
    }
}
