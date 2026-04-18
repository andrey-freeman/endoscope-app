package com.example.endoscope

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.*
import android.os.Bundle
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private lateinit var surfaceView: SurfaceView
    private lateinit var tvStatus: TextView
    private lateinit var btnConnect: Button

    private var usbManager: UsbManager? = null
    private var usbDevice: UsbDevice? = null
    private var usbConnection: UsbDeviceConnection? = null
    private var surface: Surface? = null

    companion object {
        private const val ACTION_USB_PERMISSION = "com.example.endoscope.USB_PERMISSION"
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.let {
                            tvStatus.text = "Разрешение получено, подключаю..."
                            connectToDevice(it)
                        }
                    } else {
                        tvStatus.text = "Разрешение отклонено.\nПопробуй ещё раз."
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        surfaceView = findViewById(R.id.surfaceView)
        tvStatus = findViewById(R.id.tvStatus)
        btnConnect = findViewById(R.id.btnConnect)

        surfaceView.holder.addCallback(this)
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        registerReceiver(usbReceiver, filter, RECEIVER_EXPORTED)

        btnConnect.setOnClickListener {
            findAndConnectCamera()
        }
    }

    private fun findAndConnectCamera() {
        val deviceList = usbManager?.deviceList
        if (deviceList.isNullOrEmpty()) {
            tvStatus.text = "USB устройства не найдены.\nПодключи камеру и нажми снова."
            return
        }

        tvStatus.text = "Найдено устройств: ${deviceList.size}"

        val camera = deviceList.values.firstOrNull { device ->
            device.deviceClass == 239 || device.deviceClass == 14 || device.deviceClass == 0
        } ?: deviceList.values.first()

        usbDevice = camera
        tvStatus.text = "Устройство: ${camera.productName ?: "USB #${camera.deviceId}"}\nЗапрашиваю доступ..."

        val permissionIntent = PendingIntent.getBroadcast(
            this,
            camera.deviceId,
            Intent(ACTION_USB_PERMISSION).apply {
                putExtra(UsbManager.EXTRA_DEVICE, camera)
            },
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        if (usbManager?.hasPermission(camera) == true) {
            tvStatus.text = "Доступ уже есть, подключаю..."
            connectToDevice(camera)
        } else {
            usbManager?.requestPermission(camera, permissionIntent)
            tvStatus.text = "Ожидаю разрешения...\nДолжен появиться диалог"
        }
    }

    private fun connectToDevice(device: UsbDevice) {
        val connection = usbManager?.openDevice(device)
        if (connection == null) {
            tvStatus.text = "Не удалось открыть устройство"
            return
        }
        usbConnection = connection
        tvStatus.text = "✓ Камера подключена!\n${device.productName ?: "USB камера"}"
        btnConnect.text = "Отключить"
    }

    private fun disconnect() {
        usbConnection?.close()
        usbConnection = null
        usbDevice = null
        btnConnect.text = "Подключить"
        tvStatus.text = "Отключено"
    }

    override fun surfaceCreated(holder: SurfaceHolder) { surface = holder.surface }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    override fun surfaceDestroyed(holder: SurfaceHolder) { surface = null }

    override fun onDestroy() {
        super.onDestroy()
        disconnect()
        unregisterReceiver(usbReceiver)
    }
}
