
package com.example.robotcontroller

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.content.Intent
import android.widget.ImageView
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity() {

    companion object {
        private val BT_MODULE_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val TARGET_NAME = "ESP32_Robot"
        private const val PREFS_NAME = "PIDPrefs" // file SharedPreferences, lưu lại giá trị PID
    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var btSocket: BluetoothSocket? = null
    private var isConnected = false
    private lateinit var tvStatus: TextView
    private lateinit var tvSpeedStatus: TextView
    private lateinit var btnForward: Button
    private lateinit var btnBackward: Button
    private lateinit var btnLeft: Button
    private lateinit var btnRight: Button
    private lateinit var btnStop: Button
    private lateinit var btnRotateCW: Button
    private lateinit var btnRotateCCW: Button
    private lateinit var btnReconnect: Button
    private lateinit var btnSpeed1: Button
    private lateinit var btnSpeed2: Button
    private lateinit var btnSpeed3: Button
    private lateinit var btnSendPID: Button
    private lateinit var etKp: EditText
    private lateinit var etKi: EditText
    private lateinit var etKd: EditText
    private lateinit var etDistance: EditText
    private lateinit var btnSendDistance: Button
    private lateinit var prefs: android.content.SharedPreferences

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
                Toast.makeText(this, "App cần quyền Bluetooth để hoạt động", Toast.LENGTH_SHORT).show()
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

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE) // khởi tạo SharedPreferences

        checkAndRequestBluetoothPermissions()  // gọi hàm

        // View bindings
        tvStatus = findViewById(R.id.tvStatus)
        tvSpeedStatus = findViewById(R.id.tvSpeedStatus)
        btnForward = findViewById(R.id.btnForward)
        btnBackward = findViewById(R.id.btnBackward)
        btnLeft = findViewById(R.id.btnLeft)
        btnRight = findViewById(R.id.btnRight)
        btnStop = findViewById(R.id.btnStop)
        btnRotateCW = findViewById(R.id.btnRotateRight)
        btnRotateCCW = findViewById(R.id.btnRotateLeft)
        btnReconnect = findViewById(R.id.btnReconnect)
        btnSpeed1 = findViewById(R.id.btnSpeed1)
        btnSpeed2 = findViewById(R.id.btnSpeed2)
        btnSpeed3 = findViewById(R.id.btnSpeed3)
        btnSendPID = findViewById(R.id.btnSendPID)
        etKp = findViewById(R.id.etKp)
        etKi = findViewById(R.id.etKi)
        etKd = findViewById(R.id.etKd)
        etDistance = findViewById(R.id.etDistance)
        btnSendDistance = findViewById(R.id.btnSendDistance)

        // Load giá trị Kp, Ki, Kd trước đó
        etKp.setText(prefs.getString("Kp", ""))
        etKi.setText(prefs.getString("Ki", ""))
        etKd.setText(prefs.getString("Kd", ""))

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

        // Các nút điều khiển
        btnForward.setOnClickListener { sendCommandWithStatus("F", "Đi tiến") }
        btnBackward.setOnClickListener { sendCommandWithStatus("B", "Đi lùi") }
        btnLeft.setOnClickListener { sendCommandWithStatus("L", "Rẽ trái") }
        btnRight.setOnClickListener { sendCommandWithStatus("R", "Rẽ phải") }
        btnRotateCW.setOnClickListener { sendCommandWithStatus("C", "Xoay tròn phải") }
        btnRotateCCW.setOnClickListener { sendCommandWithStatus("A", "Xoay tròn trái") }
        btnStop.setOnClickListener { sendCommandWithStatus("S", "Stop") }
        btnSpeed1.setOnClickListener { sendCommandWithStatus("TD=20.0", "Tốc độ chậm") }
        btnSpeed2.setOnClickListener { sendCommandWithStatus("TD=30.0", "Tốc độ vừa") }
        btnSpeed3.setOnClickListener { sendCommandWithStatus("TD=40.0", "Tốc độ nhanh") }

        btnSendPID.setOnClickListener {
            val kp = etKp.text.toString().toFloatOrNull() ?: 0f
            val ki = etKi.text.toString().toFloatOrNull() ?: 0f
            val kd = etKd.text.toString().toFloatOrNull() ?: 0f

            // Định dạng gói dữ liệu gửi đi, ví dụ: "PID=Kp,Ki,Kd"
            val pidCommand = "PID=$kp,$ki,$kd"

            sendCommandWithStatus(pidCommand, "Đã gửi giá trị $pidCommand")

            // lưu vào SharedPreferences
            val editor = prefs.edit()
            editor.putString("Kp", kp.toString())
            editor.putString("Ki", ki.toString())
            editor.putString("Kd", kd.toString())
            editor.apply()
        }

        btnSendDistance.setOnClickListener {
            val distance = etDistance.text.toString().toFloatOrNull() ?: 0f

            val distCommand = "DIST=$distance"
            sendCommandWithStatus(distCommand, "Đã gửi quãng đường $distance mm")
        }

        val imgLogo: ImageView = findViewById(R.id.imageLogo)
        imgLogo.setOnClickListener {
            val intent = Intent(this, InfoActivity::class.java)
            startActivity(intent)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {  // chạm ra ngoài để mất con trỏ chuột
        if (currentFocus != null) {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(currentFocus!!.windowToken, 0)
            currentFocus!!.clearFocus()
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun sendCommandWithStatus(cmd: String, status: String) {
        if (!isConnected || btSocket == null) {
            tvStatus.text = "Chưa kết nối ESP32"
            Toast.makeText(this, "Chưa kết nối đến ESP32", Toast.LENGTH_SHORT).show()
            return
        }

        Thread {
            try {
                btSocket?.outputStream?.write((cmd + "\n").toByteArray())
                // Nếu là lệnh di chuyển, hiển thị ngay
                if (status.isNotEmpty()) {
                    runOnUiThread {
                        tvStatus.text = status
                        tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
                        when (cmd) {
                            "TD=20.0" -> tvSpeedStatus.text = "TỐC ĐỘ CHẬM"
                            "TD=30.0" -> tvSpeedStatus.text = "TÓC ĐỘ VỪA"
                            "TD=40.0" -> tvSpeedStatus.text = "TỐC ĐỘ NHANH"
                        }
                        tvSpeedStatus.setTextColor(ContextCompat.getColor(this, android.R.color.white))
                    }
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
            Toast.makeText(this, "Chưa kết nối bluetooth!", Toast.LENGTH_SHORT).show()
            return
        }

        // Kiểm tra quyền BLUETOOTH_CONNECT cho Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                tvStatus.text = "Thiếu quyền BLUETOOTH_CONNECT"
                Toast.makeText(this, "Cần cấp quyền BLUETOOTH_CONNECT", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this, "Kết nối thất bại: ${e.message}", Toast.LENGTH_SHORT).show()
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
