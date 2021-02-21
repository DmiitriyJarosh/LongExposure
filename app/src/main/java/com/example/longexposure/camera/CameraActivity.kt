package com.example.longexposure.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.opengl.GLSurfaceView.RENDERMODE_WHEN_DIRTY
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.util.Size
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewTreeObserver
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.longexposure.R
import com.example.longexposure.gl.Renderer
import com.example.longexposure.result.ResultActivity
import kotlinx.android.synthetic.main.ac_camera.*
import kotlinx.android.synthetic.main.w_take_photo_button.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


class CameraActivity : AppCompatActivity() {
    private var cameraSelector: CameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var imageCapture: ImageCapture
    private lateinit var renderer: Renderer
    private var glSurfaceProvider: Preview.SurfaceProvider? = null
    private var previewUsecase: Preview? = null

    private val isCameraPermissionGranted
        get() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private val isGalleryPermissionGranted
        get() = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private var onCameraPermissionGrantedListeners = mutableListOf<(Boolean) -> Unit>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.ac_camera)

        vStartButton.setOnClickListener {
            startButtonClicked()
        }

        vStopButton.setOnClickListener {
            stopButtonClicked()
        }

        vChangeCameraButton.setOnClickListener {
            changeCamera()
        }

        vGlSurfaceView.setEGLContextClientVersion(2)

        renderer = Renderer(this, onCameraTextureCreated = {
            it.surfaceTexture?.setOnFrameAvailableListener {
                vGlSurfaceView.requestRender()
            }
            glSurfaceProvider = it.glSurfaceProvider
            checkAndRequestCameraPermissions { isGranted ->
                if (isGranted) {
                    val future = ProcessCameraProvider.getInstance(this)
                    future.addListener(Runnable {
                        cameraProvider = future.get()
                        cameraProvider?.unbindAll()
                        bindUseCases()
                    }, ContextCompat.getMainExecutor(this))
                } else {
                    finish()
                }
            }
        })
        vGlSurfaceView.setRenderer(renderer)
        vGlSurfaceView.renderMode = RENDERMODE_WHEN_DIRTY
    }

    private fun startButtonClicked() {
        renderer.setMode(Renderer.Mode.LongExposure)
        vGlSurfaceView.requestRender()
        vStopButton.visibility = VISIBLE
        vStartButton.visibility = GONE
        vChangeCameraButton.visibility = GONE
    }

    private fun stopButtonClicked() {
        renderer.requestBitmap { image ->
            val uri = saveTempBitmap(image)
            runOnUiThread {
                reset()
            }
            showResult(uri)
        }
    }

    private fun reset() {
        renderer.setMode(Renderer.Mode.Preview)
        vStopButton.visibility = GONE
        vStartButton.visibility = VISIBLE
        vChangeCameraButton.visibility = VISIBLE
    }

    private fun checkAndRequestCameraPermissions(onPermissionsGranted: (isGranted: Boolean) -> Unit) {
        if (!isCameraPermissionGranted || !isGalleryPermissionGranted) {
            onCameraPermissionGrantedListeners.add(onPermissionsGranted)
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_CAMERA_PERMISSIONS
            )
        } else {
            onPermissionsGranted(true)
        }
    }

    private fun bindUseCases() {
        cameraProvider?.unbindAll()
        bindPreview()
    }

    private fun saveTempBitmap(bitmap: Bitmap): Uri {
        var imageFile: File? = null
        try {
            val timeStamp =
                SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(Date())
            val imageFileNamePrefix = "JPEG_" + timeStamp + "_"

            val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            storageDir?.let {
                if (!storageDir.exists()) {
                    storageDir.mkdirs()
                }

                imageFile = File.createTempFile(imageFileNamePrefix, ".jpg", storageDir)

                val outStream = FileOutputStream(imageFile)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outStream)
                outStream.close()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return Uri.fromFile(imageFile)
    }

    private fun showResult(uri: Uri) {
        val intent = Intent(this, ResultActivity::class.java).apply {
            putExtra(RESULT_URI_TAG, uri)
        }
        startActivity(intent)
    }

    private fun bindTakePhoto() {
        val imageCaptureBuilder = ImageCapture.Builder()

        Camera2Interop.Extender(imageCaptureBuilder).apply {
            setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_OFF
            )
            setCaptureRequestOption(
                CaptureRequest.SENSOR_EXPOSURE_TIME, EXPOSURE_TIME_SEC * NANO_IN_SEC / 2
            )
        }
        val manager = getSystemService(CAMERA_SERVICE) as CameraManager
        for (cameraId in manager.cameraIdList) {
            val chars = manager.getCameraCharacteristics(cameraId)
            Log.e("CameraCharacteristics", "Camera $cameraId range: ${chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE).toString()}")

        }
        imageCapture = imageCaptureBuilder.build()

        cameraProvider?.bindToLifecycle(this as LifecycleOwner, cameraSelector, imageCapture)
    }

    private fun bindPreview() {
        onViewReady(vGlSurfaceView) {
            previewUsecase = Preview.Builder()
                .setTargetResolution(Size(vGlSurfaceView.width, vGlSurfaceView.height))
                .build()
            previewUsecase?.setSurfaceProvider(glSurfaceProvider)
            cameraProvider?.bindToLifecycle(this as LifecycleOwner, cameraSelector, previewUsecase)
        }
    }

    private fun changeCamera() {
        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        cameraProvider?.unbindAll()
        cameraProvider?.bindToLifecycle(this, cameraSelector, previewUsecase)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSIONS) {
            if ((grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED })) {
                onCameraPermissionGrantedListeners.forEach {
                    it.invoke(true)
                }
                onCameraPermissionGrantedListeners.clear()
            } else {
                onCameraPermissionGrantedListeners.forEach {
                    it.invoke(false)
                }
                onCameraPermissionGrantedListeners.clear()
            }
        }
    }

    private fun onViewReady(view: View, action: () -> Unit) {
        if (view.width != 0 || view.height != 0) {
            action()
        } else {
            view.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (view.width != 0 || view.height != 0) {
                        view.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        action()
                    }
                }
            })
        }
    }

    companion object {
        const val RESULT_URI_TAG = "result"
        private const val REQUEST_CAMERA_PERMISSIONS = 100
        private const val EXPOSURE_TIME_SEC = 6
        private const val NANO_IN_SEC = 1_000_000_000L
    }

}