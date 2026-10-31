package com.example.eldercall

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

/** 新增或修改一位聯絡人：照片、名字、電話，也可以從手機通訊錄匯入 */
class EditContactActivity : Activity() {

    companion object {
        const val EXTRA_ID = "id"
        private const val REQ_PHOTO = 11
        private const val REQ_CONTACT = 12
    }

    private var existing: Contact? = null
    private lateinit var nameEdit: EditText
    private lateinit var numberEdit: EditText
    private lateinit var avatarBox: FrameLayout
    private lateinit var pendingPhoto: File
    private var photoChanged = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingPhoto = File(cacheDir, "pending_photo.jpg")
        pendingPhoto.delete()
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        existing = if (id >= 0) Store.get(this, id) else null

        val root = Ui.column(this)
        val back = Ui.text(this, "← 返回", 20f, Ui.BLUE)
        back.setOnClickListener { finish() }
        root.addView(back)
        root.addView(
            Ui.text(this, if (existing == null) "新增聯絡人" else "編輯聯絡人", 30f, Ui.TEXT, bold = true),
            Ui.lp(this, 6)
        )

        // 照片
        val photoRow = Ui.row(this)
        photoRow.gravity = Gravity.CENTER_VERTICAL
        avatarBox = FrameLayout(this)
        photoRow.addView(avatarBox)
        val pick = Ui.button(this, "選擇照片", Ui.CARD, 20f, Ui.TEXT)
        pick.background = Ui.outlined(Ui.CARD, Ui.GRAY, this, 14)
        pick.setOnClickListener {
            val i = Intent(Intent.ACTION_GET_CONTENT)
            i.type = "image/*"
            try {
                startActivityForResult(i, REQ_PHOTO)
            } catch (e: Exception) {
                Toast.makeText(this, "找不到相簿", Toast.LENGTH_SHORT).show()
            }
        }
        photoRow.addView(pick, LinearLayout.LayoutParams(0, Ui.dp(this, 56), 1f).also { it.marginStart = Ui.dp(this, 14) })
        root.addView(photoRow, Ui.lp(this, 14))

        // 名字、電話
        root.addView(Ui.text(this, "名字", 17f, Ui.DIM), Ui.lp(this, 16))
        nameEdit = field("例如：女兒 小美")
        nameEdit.inputType = InputType.TYPE_CLASS_TEXT
        root.addView(nameEdit, Ui.lp(this, 4))
        root.addView(Ui.text(this, "電話號碼", 17f, Ui.DIM), Ui.lp(this, 12))
        numberEdit = field("例如：0912345678")
        numberEdit.inputType = InputType.TYPE_CLASS_PHONE
        root.addView(numberEdit, Ui.lp(this, 4))

        val importBtn = Ui.button(this, "從手機通訊錄匯入", Ui.CARD, 20f, Ui.BLUE)
        importBtn.background = Ui.outlined(Ui.CARD, Ui.BLUE, this, 14)
        importBtn.setOnClickListener {
            try {
                startActivityForResult(
                    Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI), REQ_CONTACT
                )
            } catch (e: Exception) {
                Toast.makeText(this, "找不到通訊錄", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(importBtn, Ui.lp(this, 16, Ui.dp(this, 56)))

        val save = Ui.button(this, "儲存", Ui.GREEN, 26f)
        save.setOnClickListener { save() }
        root.addView(save, Ui.lp(this, 24, Ui.dp(this, 64)))

        if (existing != null) {
            val del = Ui.button(this, "刪除這位聯絡人", Ui.CARD, 20f, Ui.RED)
            del.background = Ui.outlined(Ui.CARD, Ui.RED, this, 14)
            del.setOnClickListener { confirmDelete() }
            root.addView(del, Ui.lp(this, 12, Ui.dp(this, 54)))
        }

        existing?.let {
            nameEdit.setText(it.name)
            numberEdit.setText(it.number)
        }
        refreshAvatar()

        val scroll = ScrollView(this)
        scroll.addView(root)
        Ui.setScreen(this, scroll)
    }

    private fun field(hint: String): EditText {
        val e = EditText(this)
        e.hint = hint
        e.textSize = 24f
        e.setTextColor(Ui.TEXT)
        e.background = Ui.outlined(Ui.CARD, Ui.GRAY, this, 12)
        val p = Ui.dp(this, 12)
        e.setPadding(p, p, p, p)
        e.isSingleLine = true
        return e
    }

    private fun refreshAvatar() {
        avatarBox.removeAllViews()
        val name = nameEdit.text.toString().ifEmpty { existing?.name ?: "?" }
        val ct = Contact(existing?.id ?: -1L, name, "")
        avatarBox.addView(Ui.avatar(this, ct, 96, if (photoChanged) pendingPhoto else null))
    }

    private fun save() {
        val name = nameEdit.text.toString().trim()
        val number = numberEdit.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "請輸入名字", Toast.LENGTH_SHORT).show()
            return
        }
        if (number.count { it.isDigit() } < 3) {
            Toast.makeText(this, "請輸入電話號碼", Toast.LENGTH_SHORT).show()
            return
        }
        val ct = existing ?: Contact(System.currentTimeMillis(), name, number)
        ct.name = name
        ct.number = number
        if (photoChanged && pendingPhoto.exists()) {
            pendingPhoto.copyTo(Store.photoFile(this, ct.id), overwrite = true)
            pendingPhoto.delete()
        }
        Store.upsert(this, ct)
        Toast.makeText(this, "已儲存", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun confirmDelete() {
        val ct = existing ?: return
        AlertDialog.Builder(this)
            .setTitle("刪除「${ct.name}」？")
            .setPositiveButton("刪除") { _, _ ->
                Store.delete(this, ct.id)
                finish()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        when (requestCode) {
            REQ_PHOTO -> {
                if (savePhotoFrom(uri)) {
                    photoChanged = true
                    refreshAvatar()
                } else {
                    Toast.makeText(this, "無法讀取這張照片", Toast.LENGTH_SHORT).show()
                }
            }
            REQ_CONTACT -> importContact(uri)
        }
    }

    private fun importContact(uri: Uri) {
        try {
            val cols = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI
            )
            val cur = contentResolver.query(uri, cols, null, null, null) ?: return
            if (cur.moveToFirst()) {
                nameEdit.setText(cur.getString(0) ?: "")
                numberEdit.setText(cur.getString(1) ?: "")
                val photo = cur.getString(2)
                if (photo != null && savePhotoFrom(Uri.parse(photo))) photoChanged = true
            }
            cur.close()
            refreshAvatar()
        } catch (e: Exception) {
            Toast.makeText(this, "匯入失敗", Toast.LENGTH_SHORT).show()
        }
    }

    /** 讀取照片、依拍攝方向轉正、裁成正方形 600×600 後暫存 */
    private fun savePhotoFrom(uri: Uri): Boolean {
        return try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return false
            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 600 && bounds.outHeight / (sample * 2) >= 600) sample *= 2
            val opts = BitmapFactory.Options()
            opts.inSampleSize = sample
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return false

            val orientation = try {
                ExifInterface(ByteArrayInputStream(bytes))
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } catch (e: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            val size = min(bmp.width, bmp.height)
            val m = Matrix()
            val scale = if (size > 600) 600f / size else 1f
            m.setScale(scale, scale)
            m.postRotate(degrees)
            val square = Bitmap.createBitmap(bmp, (bmp.width - size) / 2, (bmp.height - size) / 2, size, size, m, true)
            FileOutputStream(pendingPhoto).use { square.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            true
        } catch (e: Exception) {
            false
        }
    }
}
