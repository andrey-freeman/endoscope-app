package com.example.endoscope

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.camera2.*
import android.os.Bundle
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private lateinit var surfaceView: SurfaceView
    private lateinit var tvStatus: TextView
    private lateinit var btnConnect: Button

    private var cameraManager: CameraManager? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        surfaceView = findViewById(R.id.surfaceView)
        tvStatus = findViewById(R.id.tvStatus)
        btnConnect = findViewById(R.id.btnConnect)

        surfaceView.holder.addCallback(this)
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager

        btnConnect.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
            } else {
                findExternalCamera()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST &&
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            findExternalCamera()
        } else {
            tvStatus.text = "Разрешение на камеру отклонено"
        }
    }

    private fun findExternalCamera() {
    val manager = cameraManager ?: return
    val cameraIds = manager.cameraIdList

    var info = "Камер найдено: ${cameraIds.size}\n"
    for (id in cameraIds) {
        val chars = manager.getCameraCharacteristics(id)
        val facing = chars.get(CameraCharacteristics.LENS_FACING)
        val facingStr = when(facing) {
            CameraCharacteristics.LENS_FACING_FRONT -> "фронт"
            CameraCharacteristics.LENS_FACING_BACK -> "основная"
            CameraCharacteristics.LENS_FACING_EXTERNAL -> "внешняя"
            else -> "неизвестно"
        }
        info += "ID $id: $facingStr\n"
    }
    tvStatus.text = info
}

        // Ищем внешнюю камеру
        var externalCameraId: String? = null
        for (id in cameraIds) {
            val chars = manager.getCameraCharacteristics(id)
            val facing = chars.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_EXTERNAL) {
                externalCameraId = id
                break
            }
        }

        // Если внешней нет — берём камеру с наибольшим ID (обычно это USB)
        val cameraId = externalCameraId ?: cameraIds.lastOrNull() ?: run {
            tvStatus.text = "Камеры не найдены"
            return
        }

        tvStatus.text = "Открываю камеру ID: $cameraId"
        openCamera(cameraId)
    }

    private fun openCamera(cameraId: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) return

        cameraManager?.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                runOnUiThread { tvStatus.text = "✓ Камера открыта!" }
                startPreview(camera)
            }
            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
                cameraDevice = null
                runOnUiThread { tvStatus.text = "Камера отключена" }
            }
            override fun onError(camera: CameraDevice, error: Int) {
                camera.close()
                cameraDevice = null
                runOnUiThread { tvStatus.text = "Ошибка камеры: $error" }
            }
        }, null)
    }

    private fun startPreview(camera: CameraDevice) {
        val surface = surfaceView.holder.surface ?: return
        val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        builder.addTarget(surface)

        camera.createCaptureSession(listOf(surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    session.setRepeatingRequest(builder.build(), null, null)
                    runOnUiThread { tvStatus.text = "✓ Трансляция активна!" }
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    runOnUiThread { tvStatus.text = "Ошибка настройки камеры" }
                }
            }, null)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {}
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        captureSession?.close()
        cameraDevice?.close()
    }

    override fun onDestroy() {
        super.onDestroy()
        captureSession?.close()
        cameraDevice?.close()
    }
}
