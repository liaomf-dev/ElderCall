package com.example.eldercall

import android.app.Activity
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

/** 共用的大字體元件與配色（高對比、按鈕大，適合長輩） */
object Ui {
    val BG = 0xFFF6F3EE.toInt()
    val CARD = 0xFFFFFFFF.toInt()
    val TEXT = 0xFF1C1C1C.toInt()
    val DIM = 0xFF5F6368.toInt()
    val GREEN = 0xFF1E8E3E.toInt()
    val RED = 0xFFC62828.toInt()
    val BLUE = 0xFF1565C0.toInt()
    val GRAY = 0xFFDAD6CF.toInt()
    val KEY = 0xFFFFFFFF.toInt()

    private val AVATAR_COLORS = intArrayOf(
        0xFF1565C0.toInt(), 0xFF2E7D32.toInt(), 0xFFAD1457.toInt(), 0xFFEF6C00.toInt(),
        0xFF6A1B9A.toInt(), 0xFF00838F.toInt(), 0xFF5D4037.toInt(), 0xFF283593.toInt()
    )

    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()

    fun text(c: Context, s: CharSequence, size: Float, color: Int = TEXT, bold: Boolean = false): TextView {
        val t = TextView(c)
        t.text = s
        t.textSize = size
        t.setTextColor(color)
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        return t
    }

    fun rounded(color: Int, radiusPx: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = radiusPx.toFloat()
        return g
    }

    fun outlined(fill: Int, stroke: Int, c: Context, radiusDp: Int): GradientDrawable {
        val g = rounded(fill, dp(c, radiusDp))
        g.setStroke(dp(c, 2), stroke)
        return g
    }

    fun button(c: Context, s: String, bg: Int, size: Float = 26f, fg: Int = Color.WHITE): Button {
        val b = Button(c)
        b.text = s
        b.isAllCaps = false
        b.textSize = size
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        b.setTextColor(fg)
        b.background = rounded(bg, dp(c, 20))
        b.stateListAnimator = null
        return b
    }

    fun column(c: Context): LinearLayout {
        val l = LinearLayout(c)
        l.orientation = LinearLayout.VERTICAL
        return l
    }

    fun row(c: Context): LinearLayout {
        val l = LinearLayout(c)
        l.orientation = LinearLayout.HORIZONTAL
        return l
    }

    fun lp(c: Context, topDp: Int, heightPx: Int = LinearLayout.LayoutParams.WRAP_CONTENT): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, heightPx)
        p.topMargin = dp(c, topDp)
        return p
    }

    fun weighted(c: Context, heightPx: Int, marginDp: Int): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(0, heightPx, 1f)
        val m = dp(c, marginDp)
        p.setMargins(m, m, m, m)
        return p
    }

    /** 圓形大頭照；沒有照片時顯示名字的第一個字 */
    fun avatar(c: Context, ct: Contact, sizeDp: Int, photo: File? = null): View {
        val size = dp(c, sizeDp)
        val f = photo ?: Store.photoFile(c, ct.id)
        val oval = GradientDrawable()
        oval.shape = GradientDrawable.OVAL
        if (f.exists()) {
            val bmp = BitmapFactory.decodeFile(f.absolutePath)
            if (bmp != null) {
                val iv = ImageView(c)
                oval.setColor(GRAY)
                iv.background = oval
                iv.setImageBitmap(bmp)
                iv.scaleType = ImageView.ScaleType.CENTER_CROP
                iv.clipToOutline = true
                iv.layoutParams = LinearLayout.LayoutParams(size, size)
                return iv
            }
        }
        val idx = (ct.name.hashCode() and 0x7fffffff) % AVATAR_COLORS.size
        oval.setColor(AVATAR_COLORS[idx])
        val t = TextView(c)
        t.background = oval
        t.text = if (ct.name.isNotEmpty()) ct.name.substring(0, 1) else "?"
        t.gravity = Gravity.CENTER
        t.setTextColor(Color.WHITE)
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        t.textSize = sizeDp * 0.42f
        t.layoutParams = LinearLayout.LayoutParams(size, size)
        return t
    }

    /** 新版 Android 畫面會延伸到狀態列下方，這裡自動加上留白 */
    @Suppress("DEPRECATION")
    fun fitSystemBars(v: View, padDp: Int) {
        val p = dp(v.context, padDp)
        v.setPadding(p, p, p, p)
        v.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(
                p + insets.systemWindowInsetLeft,
                p + insets.systemWindowInsetTop,
                p + insets.systemWindowInsetRight,
                p + insets.systemWindowInsetBottom
            )
            insets
        }
    }

    /** 3×4 大數字鍵盤；keyHeightDp 為 0 時平均分配高度 */
    fun keypad(c: Context, keyHeightDp: Int, onKey: (Char) -> Unit): LinearLayout {
        val box = column(c)
        val rows = arrayOf("123", "456", "789", "*0#")
        for (r in rows) {
            val row = row(c)
            for (ch in r) {
                val k = button(c, ch.toString(), KEY, 32f, TEXT)
                k.background = outlined(KEY, GRAY, c, 16)
                k.setPadding(0, 0, 0, 0)
                k.setOnClickListener { onKey(ch) }
                row.addView(k, weighted(c, if (keyHeightDp > 0) dp(c, keyHeightDp) else LinearLayout.LayoutParams.MATCH_PARENT, 3))
            }
            val lp = if (keyHeightDp > 0) {
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            } else {
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            }
            box.addView(row, lp)
        }
        return box
    }

    fun setScreen(a: Activity, root: View) {
        root.setBackgroundColor(BG)
        fitSystemBars(root, 16)
        a.setContentView(root)
    }
}
