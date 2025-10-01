
// MainActivity.kt

package com.example.loamomo

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.core.app.ActivityCompat
import android.Manifest
import android.util.Log

class MainActivity : AppCompatActivity() {

    private lateinit var etMacAddress: EditText
    private lateinit var etUuid: EditText
    private lateinit var etPackageName: EditText
    private lateinit var btnSaveConfig: Button
    private lateinit var tvStatus: TextView
    private lateinit var sharedPrefs: SharedPreferences

    // Khai báo launcher để xử lý kết quả yêu cầu quyền Bluetooth/Location
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Xử lý kết quả yêu cầu quyền
        val bluetoothConnectGranted = permissions[Manifest.permission.BLUETOOTH_CONNECT] ?: false
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false

        if (bluetoothConnectGranted && fineLocationGranted) {
            Toast.makeText(this, "Đã cấp quyền Bluetooth/Vị trí.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Cần cấp quyền Bluetooth và Vị trí để kết nối.", Toast.LENGTH_LONG).show()
        }
        checkAndSetStatus()
    }

    // Khai báo launcher để xử lý kết quả khi người dùng mở Cài đặt thông báo
    private val notificationAccessLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkAndSetStatus()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth)

        // Ánh xạ View từ XML
        etMacAddress = findViewById(R.id.et_mac_address)
        etUuid = findViewById(R.id.et_uuid)
        etPackageName = findViewById(R.id.et_package_name)
        btnSaveConfig = findViewById(R.id.btn_save_config)
        tvStatus = findViewById(R.id.tv_status)

        // Khởi tạo SharedPreferences
        sharedPrefs = getSharedPreferences("AppConfig", MODE_PRIVATE)

        // 1. Load cấu hình đã lưu
        loadConfig()

        // 2. Thiết lập Listener cho nút Lưu
        btnSaveConfig.setOnClickListener {
            saveConfig()
            requestAllPermissions()
        }

        // 3. Kiểm tra và yêu cầu quyền khi ứng dụng khởi động
        requestAllPermissions()
    }

    override fun onResume() {
        super.onResume()
        // Cập nhật trạng thái mỗi khi Activity trở lại foreground
        checkAndSetStatus()
    }

    private fun loadConfig() {
        etMacAddress.setText(sharedPrefs.getString("bluetooth_mac", "B8:D6:1A:B9:E8:B2"))
        etUuid.setText(sharedPrefs.getString("bluetooth_uuid", "00001101-0000-1000-8000-00805F9B34FB"))
        etPackageName.setText(sharedPrefs.getString("notification_package", "com.mservice.momotransfer"))
    }

    private fun saveConfig() {
        val mac = etMacAddress.text.toString().trim()
        val uuid = etUuid.text.toString().trim()
        val pkg = etPackageName.text.toString().trim()

        sharedPrefs.edit().apply {
            putString("bluetooth_mac", mac)
            putString("bluetooth_uuid", uuid)
            putString("notification_package", pkg)
            apply()
        }
        Toast.makeText(this, "Đã lưu cấu hình thành công!", Toast.LENGTH_SHORT).show()
        Log.i("MainActivity", "Saved config: MAC=$mac, UUID=$uuid, Package=$pkg")
    }

    private fun requestAllPermissions() {
        // 1. Yêu cầu quyền Bluetooth (Android 12/API 31+) và Vị trí
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12 trở lên
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PermissionChecker.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        // Vị trí vẫn cần thiết cho quá trình quét/kết nối Bluetooth
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PermissionChecker.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }

        // 2. Kiểm tra và Yêu cầu quyền Notification Listener
        if (!isNotificationServiceEnabled()) {
            showNotificationAccessDialog()
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val cn = ComponentName(this, MyNotificationListener::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(cn.flattenToString())
    }

    private fun showNotificationAccessDialog() {
        AlertDialog.Builder(this)
            .setTitle("Yêu cầu quyền truy cập thông báo")
            .setMessage("Ứng dụng cần quyền truy cập thông báo để lắng nghe thông báo từ MoMo.")
            .setPositiveButton("Cấp quyền") { _, _ ->
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                notificationAccessLauncher.launch(intent)
            }
            .setNegativeButton("Hủy") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun checkAndSetStatus() {
        val isNotifEnabled = isNotificationServiceEnabled()
        var status = "Trạng thái: "
        var statusColor = android.R.color.holo_green_dark // Mặc định là Tốt (Xanh lá)

        // 1. Kiểm tra Quyền Thông báo (QUAN TRỌNG NHẤT - Ưu tiên Cao)
        if (isNotifEnabled) {
            status += "✅ Đã cấp quyền Thông báo."
        } else {
            status += "❌ Thiếu quyền Thông báo."
            statusColor = android.R.color.holo_red_dark // Đặt màu ĐỎ nếu thiếu quyền này

            // Cập nhật trạng thái và DỪNG LẠI nếu quyền quan trọng nhất chưa có
            tvStatus.setTextColor(ContextCompat.getColor(this, statusColor))
            tvStatus.text = status
            return
        }

        // 2. Kiểm tra Quyền Bluetooth (Ưu tiên Trung bình)
        var hasBluetoothPermission = true

        // Kiểm tra quyền BLUETOOTH_CONNECT (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PermissionChecker.PERMISSION_GRANTED) {
            hasBluetoothPermission = false
        }

        // Kiểm tra quyền ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PermissionChecker.PERMISSION_GRANTED) {
            hasBluetoothPermission = false
        }

        if (hasBluetoothPermission) {
            status += "\n✅ Đã cấp quyền Bluetooth."
        } else {
            status += "\n❌ Thiếu quyền Bluetooth/Vị trí."
            statusColor = android.R.color.holo_orange_dark // Đặt màu CAM nếu thiếu quyền này
        }

        // 3. Đặt Text và Màu Sắc LẦN CUỐI (TỔNG KẾT)
        tvStatus.text = status
        // Dòng này được CẢI TIẾN: Chỉ đặt màu ở đây, màu sẽ là Đỏ, Cam, hoặc Xanh lá (giá trị mặc định)
        tvStatus.setTextColor(ContextCompat.getColor(this, statusColor))
    }
}

/*
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth)

        if (!isNotificationServiceEnabled) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        }
    }

    private val isNotificationServiceEnabled: Boolean
        get() {
            val pkgName = packageName
            val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            return flat != null && flat.contains(pkgName)
        }
}

 */

