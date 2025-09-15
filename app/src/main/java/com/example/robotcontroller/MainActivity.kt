
package com.example.robotcontroller

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.view.MotionEvent
import android.content.Intent
import android.widget.ImageView
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity() {

    companion object {
        private val BT_MODULE_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val TARGET_NAME = "ESP32_Robot"
    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var btSocket: BluetoothSocket? = null
    private var isConnected = false
    private lateinit var tvStatus: TextView
    private lateinit var btnForward: Button
    private lateinit var btnBackward: Button
    private lateinit var btnLeft: Button
    private lateinit var btnRight: Button
    private lateinit var btnStop: Button
    private lateinit var btnRotateCW: Button
    private lateinit var btnRotateCCW: Button
    private lateinit var btnReconnect: Button

    private val permissionsBluetooth = arrayOf(
        android.Manifest.permission.BLUETOOTH_CONNECT,
        android.Manifest.permission.BLUETOOTH_SCAN
    )

    private val requestBluetoothPermission =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            var granted = true
            permissions.entries.forEach {
                if (!it.value) granted = false
            }
            if (!granted) {
                Toast.makeText(this, "App cần quyền Bluetooth để hoạt động", Toast.LENGTH_LONG).show()
            }
        }

    private fun checkAndRequestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val notGranted = permissionsBluetooth.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (notGranted.isNotEmpty()) {
                requestBluetoothPermission.launch(permissionsBluetooth)
            }
        }
    }

    // Hàm gắn sự kiện nhấn giữ
    private fun setHoldButton(button: Button, command: String, actionText: String) {
        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    sendCommandWithStatus(command, actionText)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    sendCommandWithStatus("S", "Stop")
                    true
                }
                else -> false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkAndRequestBluetoothPermissions()

        // View bindings
        tvStatus = findViewById(R.id.tvStatus)
        btnForward = findViewById(R.id.btnForward)
        btnBackward = findViewById(R.id.btnBackward)
        btnLeft = findViewById(R.id.btnLeft)
        btnRight = findViewById(R.id.btnRight)
        btnStop = findViewById(R.id.btnStop)
        btnRotateCW = findViewById(R.id.btnRotateLeft)
        btnRotateCCW = findViewById(R.id.btnRotateRight)
        btnReconnect = findViewById(R.id.btnReconnect)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Thiết bị không hỗ trợ Bluetooth", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Kết nối ESP32 từ danh sách paired devices khi mở app
        connectPairedDevice()

        // Nút Reconnect
        btnReconnect.setOnClickListener {
            reconnectEsp32()
        }

        // Các nút điều khiển (nhấn giữ để chạy, thả để dừng)
        setHoldButton(btnForward, "F", "Đi tiến")
        setHoldButton(btnBackward, "B", "Đi lùi")
        setHoldButton(btnLeft, "L", "Rẽ trái")
        setHoldButton(btnRight, "R", "Rẽ phải")
        setHoldButton(btnRotateCW, "C", "Xoay tròn trái")
        setHoldButton(btnRotateCCW, "A", "Xoay tròn phải")

        // Nút Stop vẫn giữ nguyên bấm 1 lần
        btnStop.setOnClickListener { sendCommandWithStatus("S", "Stop") }

        val imgLogo: ImageView = findViewById(R.id.imageView2)
        imgLogo.setOnClickListener {
            val intent = Intent(this, InfoActivity::class.java)
            startActivity(intent)
        }

    }

    private fun sendCommandWithStatus(cmd: String, status: String) {
        if (!isConnected || btSocket == null) {
            tvStatus.text = "Chưa kết nối ESP32"
            Toast.makeText(this, "Chưa kết nối đến ESP32", Toast.LENGTH_SHORT).show()
            return
        }

        Thread {
            try {
                btSocket?.outputStream?.write(cmd.toByteArray())
                runOnUiThread {
                    tvStatus.text = status
                    tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
                }
            } catch (e: IOException) {
                e.printStackTrace()
                runOnUiThread {
                    tvStatus.text = "Gửi lệnh thất bại"
                    Toast.makeText(this, "Gửi lệnh thất bại", Toast.LENGTH_SHORT).show()
                    isConnected = false
                    btSocket = null
                }
            }
        }.start()
    }

    private fun connectPairedDevice() {
        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
        val device = pairedDevices?.firstOrNull { it.name == TARGET_NAME }

        if (device == null) {
            tvStatus.text = "Chưa kết nối $TARGET_NAME. Vui lòng bật bluetooth."
            Toast.makeText(this, "Chưa kết nối bluetooth!", Toast.LENGTH_LONG).show()
            return
        }

        // Kiểm tra quyền BLUETOOTH_CONNECT cho Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                tvStatus.text = "Thiếu quyền BLUETOOTH_CONNECT"
                Toast.makeText(this, "Cần cấp quyền BLUETOOTH_CONNECT", Toast.LENGTH_LONG).show()
                return
            }
        }

        Thread {
            try {
                btSocket?.close() // đóng socket cũ nếu có
                btSocket = device.createRfcommSocketToServiceRecord(BT_MODULE_UUID)
                bluetoothAdapter?.cancelDiscovery()
                btSocket?.connect()
                isConnected = true

                runOnUiThread {
                    tvStatus.text = "Đã kết nối: ${device.name}"
                    Toast.makeText(this, "Đã kết nối ESP32!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: IOException) {
                e.printStackTrace()
                try { btSocket?.close() } catch (_: Exception) {}
                btSocket = null
                isConnected = false
                runOnUiThread {
                    tvStatus.text = "Kết nối thất bại"
                    Toast.makeText(this, "Kết nối thất bại: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun reconnectEsp32() {
        tvStatus.text = "Đang kết nối lại ..."
        connectPairedDevice()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { btSocket?.close() } catch (_: Exception) {}
        btSocket = null
        isConnected = false
    }
}
