package com.lin.camera

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.lin.camera.databinding.ActivityMainBinding
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

typealias LumaListener = (luma: Double) -> Unit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var imageCapture: ImageCapture ?= null
    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        //setContentView(R.layout.activity_main)
        setContentView(binding.root)

        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (isRequiredPermissionsGranted()) {
            //Toast.makeText(this, "success", Toast.LENGTH_SHORT).show()
            //已授权直接启动相机
            startCamera()
        } else {
            //询问获取权限
            ActivityCompat.requestPermissions(this, Constants.REQUIRED_PERMISSIONS,
            Constants.REQUEST_CODE_PERMISSIONS)
        }

        binding.buttonTakePhoto.setOnClickListener {
            takePhoto()
        }
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>,
            grantResults: IntArray) {
        if (requestCode != Constants.REQUEST_CODE_PERMISSIONS) return

        if (!isRequiredPermissionsGranted()) {
            Toast.makeText(this, "please open camera permission", Toast.LENGTH_SHORT).show()
            finish()
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val photoFile = File(outputDirectory,
            SimpleDateFormat(Constants.FILE_NAME_FORMAT,
            Locale.getDefault())
                .format(System.currentTimeMillis()) + ".jpg"
        )
        val outputOption = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOption, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val fromFile = Uri.fromFile(photoFile)
                    val msg = "Photo saved to"

                    Toast.makeText(
                        this@MainActivity,
                        "$msg $fromFile",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.i(Constants.TAG, "image saved location:$fromFile")
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(Constants.TAG, "save error:", exception)
                }
            }
        )
    }

    private fun getOutputDirectory(): File {
        val file = externalMediaDirs.firstOrNull()?.let { f ->
            File(f, resources.getString(R.string.app_name)).apply {
                mkdirs()
            }
        }
        return if (file != null && file.exists()) file
            else filesDir
    }

    private fun startCamera() {
        val instance = ProcessCameraProvider.getInstance(this)
        instance.addListener({
            val provider = instance.get()
            val preview = Preview.Builder()
                .build()
                .also {
                        // cameraView 就是 PreviewView 控件的 id
                        p -> p.setSurfaceProvider(binding.cameraView.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder().build()
            // 亮度分析，由线程池处理
            val imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { luma ->
                        Log.d(Constants.TAG, "Average luminosity: $luma")
                    })
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer)
            } catch (e: Exception) {
                Log.i("dev", "start camera fail:", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun isRequiredPermissionsGranted() = Constants.REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private class LuminosityAnalyzer(private val listener: LumaListener) : ImageAnalysis.Analyzer {

        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }

        override fun analyze(image: ImageProxy) {
            Log.e(Constants.TAG, "analyze: ${image.height} ${image.width}")
            val buffer = image.planes[0].buffer
            val data = buffer.toByteArray()
            val pixels = data.map { it.toInt() and 0xFF }
            val luma = pixels.average()

            listener(luma)

            image.close()
        }
    }
}
