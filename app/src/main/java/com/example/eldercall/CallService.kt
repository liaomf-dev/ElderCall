package com.example.eldercall

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile

/** 目前所有通話的共用狀態，讓通話畫面可以讀取與操作 */
object CallManager {
    val calls = ArrayList<Call>()
    var service: CallService? = null
    private val listeners = ArrayList<Runnable>()

    fun addListener(l: Runnable) { listeners.add(l) }
    fun removeListener(l: Runnable) { listeners.remove(l) }
    fun notifyChanged() { for (l in ArrayList(listeners)) l.run() }

    @Suppress("DEPRECATION")
    fun stateOf(c: Call): Int = c.state

    /** 要顯示的那通：響鈴中的優先，其次是最後一通還沒結束的 */
    fun primary(): Call? =
        calls.firstOrNull { stateOf(it) == Call.STATE_RINGING }
            ?: calls.lastOrNull { stateOf(it) != Call.STATE_DISCONNECTED }
            ?: calls.lastOrNull()

    fun number(c: Call): String = c.details?.handle?.schemeSpecificPart ?: ""

    fun answer() {
        calls.firstOrNull { stateOf(it) == Call.STATE_RINGING }?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    fun decline() {
        calls.firstOrNull { stateOf(it) == Call.STATE_RINGING }?.reject(false, null)
    }

    fun hangUp() {
        primary()?.disconnect()
    }

    @Suppress("DEPRECATION")
    fun isSpeakerOn(): Boolean = service?.callAudioState?.route == CallAudioState.ROUTE_SPEAKER

    @Suppress("DEPRECATION")
    fun setSpeaker(on: Boolean) {
        service?.setAudioRoute(if (on) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_WIRED_OR_EARPIECE)
    }
}

/** 系統電話服務把每一通電話（撥出與來電）交給這裡 */
class CallService : InCallService() {

    companion object {
        private const val NOTIF_ID = 7
        private const val CH_INCOMING = "incoming_call"
        private const val CH_ONGOING = "ongoing_call"
    }

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            update()
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            update()
        }
    }

    override fun onCreate() {
        super.onCreate()
        CallManager.service = this
    }

    override fun onDestroy() {
        if (CallManager.service === this) CallManager.service = null
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.calls.add(call)
        call.registerCallback(callback)
        update()
        openCallScreen()
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callback)
        CallManager.calls.remove(call)
        update()
    }

    @Deprecated("Deprecated in Java")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        CallManager.notifyChanged()
    }

    private fun update() {
        CallManager.notifyChanged()
        updateNotification()
    }

    private fun openCallScreen() {
        try {
            val i = Intent(this, InCallActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } catch (e: Exception) {
            // 背景無法直接開畫面時，靠通知的全螢幕提醒顯示
        }
    }

    @Suppress("DEPRECATION")
    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val call = CallManager.primary()
        if (call == null || CallManager.stateOf(call) == Call.STATE_DISCONNECTED) {
            nm.cancel(NOTIF_ID)
            return
        }
        val ringing = CallManager.stateOf(call) == Call.STATE_RINGING
        val number = CallManager.number(call)
        val name = Phone.nameFor(this, number) ?: Phone.pretty(number).ifEmpty { "未知號碼" }

        val openIntent = Intent(this, InCallActivity::class.java)
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val open = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder: Notification.Builder
        if (Build.VERSION.SDK_INT >= 26) {
            val incoming = NotificationChannel(CH_INCOMING, "來電", NotificationManager.IMPORTANCE_HIGH)
            incoming.setSound(null, null)   // 鈴聲由系統負責
            nm.createNotificationChannel(incoming)
            nm.createNotificationChannel(NotificationChannel(CH_ONGOING, "通話中", NotificationManager.IMPORTANCE_LOW))
            builder = Notification.Builder(this, if (ringing) CH_INCOMING else CH_ONGOING)
        } else {
            builder = Notification.Builder(this)
            builder.setPriority(if (ringing) Notification.PRIORITY_MAX else Notification.PRIORITY_LOW)
        }
        builder.setSmallIcon(R.drawable.ic_call)
            .setContentTitle(name)
            .setContentText(if (ringing) "來電" else "通話中，點這裡回到通話畫面")
            .setOngoing(true)
            .setContentIntent(open)
            .setCategory(Notification.CATEGORY_CALL)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        if (ringing) {
            builder.setFullScreenIntent(open, true)
            builder.addAction(R.drawable.ic_call, "拒接", actionIntent(CallActionReceiver.ACTION_DECLINE, 1))
            builder.addAction(R.drawable.ic_call, "接聽", actionIntent(CallActionReceiver.ACTION_ANSWER, 2))
        } else {
            builder.addAction(R.drawable.ic_call, "掛斷", actionIntent(CallActionReceiver.ACTION_HANGUP, 3))
        }
        try {
            nm.notify(NOTIF_ID, builder.build())
        } catch (e: Exception) {
            // 沒有通知權限時忽略
        }
    }

    private fun actionIntent(action: String, code: Int): PendingIntent {
        val i = Intent(this, CallActionReceiver::class.java)
        i.action = action
        return PendingIntent.getBroadcast(this, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

/** 處理通知上的「接聽／拒接／掛斷」按鈕 */
class CallActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_ANSWER = "com.example.eldercall.ANSWER"
        const val ACTION_DECLINE = "com.example.eldercall.DECLINE"
        const val ACTION_HANGUP = "com.example.eldercall.HANGUP"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ANSWER -> {
                CallManager.answer()
                val i = Intent(context, InCallActivity::class.java)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try { context.startActivity(i) } catch (e: Exception) { }
            }
            ACTION_DECLINE -> CallManager.decline()
            ACTION_HANGUP -> CallManager.hangUp()
        }
    }
}
