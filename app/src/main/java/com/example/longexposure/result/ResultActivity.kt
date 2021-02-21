package com.example.longexposure.result

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.longexposure.R
import com.example.longexposure.camera.CameraActivity.Companion.RESULT_URI_TAG
import kotlinx.android.synthetic.main.ac_result.*
import java.io.File
import java.io.FileOutputStream


class ResultActivity : AppCompatActivity() {

    private var resultUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.ac_result)

        vSaveButton.setOnClickListener {
            saveToGallery(resultUri)
        }

        resultUri = intent.getParcelableExtra(RESULT_URI_TAG)

        resultUri?.let {
            Glide.with(this).load(it).into(vResultImageView)
        }
    }

    private fun saveToGallery(uri: Uri?) {
        Glide.with(this).asBitmap().load(uri).into(object : CustomTarget<Bitmap>() {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                saveToGallery(resource)
                Toast.makeText(this@ResultActivity, "Image saved to gallery", Toast.LENGTH_SHORT).show()
                finish()
            }

            override fun onLoadCleared(placeholder: Drawable?) {}
        })
    }

    private fun saveToGallery(bitmap: Bitmap) {
        val uri: Uri?

        if (android.os.Build.VERSION.SDK_INT >= 29) {

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MEL")
                put(MediaStore.Images.Media.IS_PENDING, true)
            }

            uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                val outputStream = contentResolver.openOutputStream(uri)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                outputStream?.close()
                values.put(MediaStore.Images.Media.IS_PENDING, false)
                contentResolver.update(uri, values, null, null)
            }
        } else {

            val directory = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "MEL")
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val fileName = System.currentTimeMillis().toString() + ".jpg"

            val file = File(directory, fileName)
            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            outputStream.close()

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                put(MediaStore.Images.Media.DATA, file.absolutePath)
            }
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            uri = Uri.fromFile(file)
        }

        if (uri != null) {
            val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            intent.data = uri
            sendBroadcast(intent)
        }
    }

}