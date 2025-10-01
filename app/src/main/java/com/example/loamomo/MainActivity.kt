
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
import android.annotation.SuppressLint
import android.util.Log

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.app.ProgressDialog // Hoặc dùng Material/AppCompat Dialog nếu cần tùy chỉnh cao hơn
import android.widget.ArrayAdapter


class MainActivity : AppCompatActivity() {

    private lateinit var etUuid: EditText
    private lateinit var etPackageName: EditText
    private lateinit var btnSaveConfig: Button
    private lateinit var tvStatus: TextView
    private lateinit var sharedPrefs: SharedPreferences

    // *** KHAI BÁO MỚI CHO TÍNH NĂNG QUÉT ***
    private lateinit var tvSelectedMacAddress: TextView // View mới hiển thị MAC đã chọn
    private lateinit var btnScanDevice: Button         // Button Quét mới
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var progressDialog: ProgressDialog // Biến mới

    private var selectedMacAddress: String? = null // Biến lưu trữ MAC đã chọn

    // Danh sách thiết bị tìm thấy (dùng để hiển thị cho người dùng)
    private val foundDevices = mutableListOf<BluetoothDevice>()
    private val deviceNameList = mutableListOf<String>()

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
        tvSelectedMacAddress = findViewById(R.id.tv_selected_mac_address) // Ánh xạ mới
        btnScanDevice = findViewById(R.id.btn_scan_device)               // Ánh xạ mới
        etUuid = findViewById(R.id.et_uuid)
        etPackageName = findViewById(R.id.et_package_name)
        btnSaveConfig = findViewById(R.id.btn_save_config)
        tvStatus = findViewById(R.id.tv_status)

        // Khởi tạo các đối tượng Bluetooth
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        // Khởi tạo SharedPreferences
        sharedPrefs = getSharedPreferences("AppConfig", MODE_PRIVATE)

        // Khởi tạo Dialog
        progressDialog = ProgressDialog(this).apply {
            setMessage("Đang tìm kiếm thiết bị Bluetooth...")
            setCancelable(false) // Không cho hủy bằng cách nhấn ngoài hoặc nút Back
            setProgressStyle(ProgressDialog.STYLE_SPINNER) // Kiểu vòng tròn quay
        }

        // 1. Load cấu hình đã lưu
        loadConfig()

        // 2. Thiết lập Listener cho nút Lưu
        btnSaveConfig.setOnClickListener {
            saveConfig()
            requestAllPermissions()
        }
        btnScanDevice.setOnClickListener {
            // Chỉ quét nếu đã có quyền cần thiết
            if (checkBluetoothPermissions()) {
                startScanning()
            } else {
                Toast.makeText(this, "Vui lòng cấp quyền Bluetooth/Vị trí trước.", Toast.LENGTH_SHORT).show()
                requestAllPermissions()
            }
        }
        // 3. Kiểm tra và yêu cầu quyền khi ứng dụng khởi động
        requestAllPermissions()

        // Đăng ký BroadcastReceiver
        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        registerReceiver(bluetoothReceiver, filter)

    }
    override fun onDestroy() {
        super.onDestroy()
        // Hủy đăng ký BroadcastReceiver khi Activity bị hủy
        unregisterReceiver(bluetoothReceiver)
    }
    override fun onResume() {
        super.onResume()
        // Cập nhật trạng thái mỗi khi Activity trở lại foreground
        checkAndSetStatus()
    }

    private fun loadConfig() {
        // Đọc MAC đã lưu vào biến mới
        selectedMacAddress = sharedPrefs.getString("bluetooth_mac", null)

        // Hiển thị MAC đã lưu trên TextView mới
        tvSelectedMacAddress.text = "Địa chỉ MAC: ${selectedMacAddress ?: "Chưa chọn/Đang dùng giá trị mặc định"}"

        // Giữ nguyên UUID và Package Name
        etUuid.setText(sharedPrefs.getString("bluetooth_uuid", "00001101-0000-1000-8000-00805F9B34FB"))
        etPackageName.setText(sharedPrefs.getString("notification_package", "com.mservice.momotransfer"))
    }

    private fun saveConfig() {
        // *** LƯU MAC TỪ BIẾN selectedMacAddress ĐÃ CHỌN TRONG QUÁ TRÌNH QUÉT ***
        val mac = selectedMacAddress // Sử dụng MAC đã chọn qua chức năng quét
        val uuid = etUuid.text.toString().trim()
        val pkg = etPackageName.text.toString().trim()

        if (mac.isNullOrEmpty()) {
            Toast.makeText(this, "Vui lòng Quét và Chọn thiết bị Bluetooth trước!", Toast.LENGTH_LONG).show()
            return
        }

        sharedPrefs.edit().apply {
            putString("bluetooth_mac", mac)
            putString("bluetooth_uuid", uuid)
            putString("notification_package", pkg)
            apply()
        }
        Toast.makeText(this, "Đã lưu cấu hình thành công! Đang kết nối lại...", Toast.LENGTH_SHORT).show()
        Log.i("MainActivity", "Saved config. Sending reconnect command to Service.")

        // ------------------------------------------------------------------
        // *** PHẦN CẢI TIẾN: GỬI LỆNH TÍN HIỆU (Intent) ***
        // ------------------------------------------------------------------
        val reconnectIntent = Intent(this, MyNotificationListener::class.java).apply {
            action = "com.example.loamomo.ACTION_RECONNECT"
        }

        // Gửi lệnh: Service sẽ nhận Intent này trong onStartCommand và chạy connectWithNewConfig()
        startService(reconnectIntent)

        // ------------------------------------------------------------------

        Log.i("MainActivity", "Saved config: MAC=$mac, UUID=$uuid, Package=$pkg")
    }

    private fun requestAllPermissions() {
        // 1. Yêu cầu quyền Bluetooth (Android 12/API 31+) và Vị trí
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12 trở lên
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PermissionChecker.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            // *** THÊM QUYỀN BLUETOOTH_SCAN ***
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PermissionChecker.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
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
    private fun checkBluetoothPermissions(): Boolean {
        var hasPermission = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PermissionChecker.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PermissionChecker.PERMISSION_GRANTED) {
                hasPermission = false
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PermissionChecker.PERMISSION_GRANTED) {
            hasPermission = false
        }
        return hasPermission
    }

    private fun stopNotificationService() {
        val serviceIntent = Intent(this, MyNotificationListener::class.java)
        stopService(serviceIntent)
        Log.i("MainActivity", "Notification Listener Service stopped.")
    }

    private fun startNotificationService() {
        // Lưu ý: Mặc dù Android 8+ có giới hạn cho Service nền,
        // Service này là NotificationListenerService nên nó sẽ được khởi động khi có thông báo,
        // nhưng ta vẫn nên gọi startService để đảm bảo Service được khởi tạo và chạy onCreate()
        val serviceIntent = Intent(this, MyNotificationListener::class.java)

        // Đối với NotificationListenerService, bạn thường không cần phải gọi startServiceForeground
        // Chỉ cần gọi startService để đảm bảo onCreate() chạy lại (hoặc onStartCommand)
        // Tuy nhiên, đối với Service thông thường, bạn cần cân nhắc startForegroundService

        // Vì đây là NotificationListenerService, việc gọi startService() chỉ hoạt động
        // nếu Service chưa chạy. Cách chắc chắn hơn là buộc nó phải chết rồi khởi động lại.
        // Tạm thời chỉ cần gọi startService() để Service chạy onStartCommand (nếu đã chạy) hoặc onCreate()
        // Tuy nhiên, để buộc nó chạy lại onCreate() (đọc config mới), ta cần stop trước.
        startService(serviceIntent)
        Log.i("MainActivity", "Notification Listener Service started/restarted command sent.")
    }

    // MainActivity.kt

    @SuppressLint("MissingPermission")
    private fun startScanning() {
        // 1. Kiểm tra Quyền Tổng Thể trước khi tiến hành
        if (!checkBluetoothPermissions()) {
            Log.e("BluetoothScan", "Permission check failed before starting discovery.")
            Toast.makeText(this, "Vui lòng cấp đủ quyền Bluetooth và Vị trí để quét.", Toast.LENGTH_LONG).show()
            requestAllPermissions()
            return
        }

        // --- PHẦN CẢI TIẾN: QUẢN LÝ DIALOG KHI BẮT ĐẦU ---
        // Khóa nút (Đã làm ở bước trước, có thể giữ lại hoặc chỉ dùng Dialog)
        btnScanDevice.isEnabled = false
        progressDialog.show()
        // -----------------------------------------------------------

        if (bluetoothAdapter.isDiscovering) {
            // Cần BLUETOOTH_SCAN để gọi cancelDiscovery()
            try {
                bluetoothAdapter.cancelDiscovery()
            } catch (e: SecurityException) {
                Log.e("BluetoothScan", "Permission BLUETOOTH_SCAN missing for cancelDiscovery().", e)
                return // Dừng lại nếu lỗi SecurityException xảy ra
            }
        }

        foundDevices.clear()
        deviceNameList.clear()

        Toast.makeText(this, "Đang quét thiết bị... (12 giây)", Toast.LENGTH_LONG).show()
        Log.d("BluetoothScan", "Starting device discovery.")

        try {
            // Cần BLUETOOTH_SCAN để gọi startDiscovery()
            bluetoothAdapter.startDiscovery()
        } catch (e: SecurityException) {
            Log.e("BluetoothScan", "Permission BLUETOOTH_SCAN missing or denied.", e)
            Toast.makeText(this, "Lỗi: Thiếu quyền quét Bluetooth.", Toast.LENGTH_SHORT).show()
            // --- PHỤC HỒI NẾU LỖI NGAY LẬP TỨC ---
            if (progressDialog.isShowing) {
                progressDialog.dismiss()
            }
            btnScanDevice.isEnabled = true
        }
    }
    // MainActivity.kt (Tiếp tục)

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    // Khi tìm thấy thiết bị
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    // Yêu cầu quyền BLUETOOTH_CONNECT để truy cập tên
                    try {
                        if (device != null && device.name != null && !foundDevices.contains(device)) {
                            foundDevices.add(device)
                            val deviceName = device.name ?: "Thiết bị không tên"
                            deviceNameList.add("$deviceName (${device.address})")
                            Log.d("BluetoothScan", "Found device: $deviceName (${device.address})")
                        }
                    } catch (e: SecurityException) {
                        Log.e("BluetoothScan", "Permission BLUETOOTH_CONNECT missing for getting device name.", e)
                    }
                    // --- CẢI TIẾN: CẬP NHẬT TRẠNG THÁI TRÊN DIALOG ---
                    if (progressDialog.isShowing) {
                        progressDialog.setMessage("Đang tìm kiếm thiết bị... (${foundDevices.size} thiết bị đã tìm thấy)")
                    }
                    // --------------------------------------------------
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    // Khi quá trình quét kết thúc
                    Log.d("BluetoothScan", "Device discovery finished. Found ${foundDevices.size} devices.")
                    // 1. ĐÓNG DIALOG QUAY VÒNG
                    if (progressDialog.isShowing) {
                        progressDialog.dismiss()
                    }

                    // 2. PHỤC HỒI NÚT VÀ HIỂN THỊ HỘP THOẠI CHỌN
                    resetScanButton()
                    showDeviceSelectionDialog()
                }
            }
        }
    }
    // MainActivity.kt (Tiếp tục)

    private fun resetScanButton() {
        btnScanDevice.isEnabled = true
        btnScanDevice.text = "Quét & Chọn Thiết Bị"
        loadConfig() // Tải lại MAC đã chọn (hoặc mặc định)
    }

    private fun showDeviceSelectionDialog() {
        if (foundDevices.isEmpty()) {
            Toast.makeText(this, "Không tìm thấy thiết bị Bluetooth nào.", Toast.LENGTH_LONG).show()
            return
        }

        val items = deviceNameList.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Chọn Thiết Bị Pico/ESP32")
            .setItems(items) { dialog, which ->
                // Xử lý khi người dùng chọn một thiết bị
                val selectedDevice = foundDevices[which]
                try {
                    selectedMacAddress = selectedDevice.address
                    val selectedName = selectedDevice.name ?: "Thiết bị không tên"

                    tvSelectedMacAddress.text = "Địa chỉ MAC: $selectedName ($selectedMacAddress)"
                    Toast.makeText(this, "Đã chọn thiết bị: $selectedName", Toast.LENGTH_SHORT).show()
                    Log.i("BluetoothScan", "Device selected: $selectedMacAddress")

                } catch (e: SecurityException) {
                    Log.e("BluetoothScan", "Permission BLUETOOTH_CONNECT missing.", e)
                    Toast.makeText(this, "Lỗi: Thiếu quyền kết nối Bluetooth.", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Hủy", null)
            .show()
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

