package com.example.loamomo

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import androidx.annotation.RequiresPermission
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class MyNotificationListener : NotificationListenerService() {
    private val notificationsArray = JSONArray()

    // Biến cho Bluetooth (Nullable và an toàn)
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothSocket: BluetoothSocket? = null
    private var connectedThread: ConnectedThread? = null

    // Biến lưu trữ cấu hình đọc từ SharedPreferences (Đọc 1 lần trong onCreate)
    private var macAddress: String? = null
    private var targetUUID: UUID = DEFAULT_UUID
    private var packageNameFilter: String = "com.mservice.momotransfer"

    private companion object {
        private val DEFAULT_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val RECONNECT_DELAY_MS = 5000L // Đợi 5 giây trước khi kết nối lại
    }

    @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    override fun onCreate() {
        super.onCreate()
        Log.d("ServiceLifecycle", "MyNotificationListener Service STARTED.") // <-- THÊM LOG NÀY

        // *** CẢI TIẾN 1: ĐỌC TẤT CẢ CONFIG TỪ SharedPreferences TRONG onCreate() ***
        val sharedPrefs = applicationContext.getSharedPreferences("AppConfig", MODE_PRIVATE)
        macAddress = sharedPrefs.getString("bluetooth_mac", null)
        val uuidString = sharedPrefs.getString("bluetooth_uuid", null)
        packageNameFilter = sharedPrefs.getString("notification_package", "com.mservice.momotransfer")!!

        if (macAddress.isNullOrEmpty()) {
            Log.e("BluetoothConnection", "MAC Address not configured. Skipping Bluetooth connection.")
            return
        }

        targetUUID = try {
            UUID.fromString(uuidString ?: DEFAULT_UUID.toString())
        } catch (e: IllegalArgumentException) {
            Log.e("BluetoothConnection", "Invalid UUID format: $uuidString. Using default UUID.", e)
            DEFAULT_UUID
        }

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e("BluetoothConnection", "Bluetooth not supported or not enabled.")
            return
        }

        // *** BẮT BUỘC: GỌI KẾT NỐI TRÊN THREAD RIÊNG ***
        connectBluetoothDevice()
    }

    // Hàm riêng để xử lý kết nối Bluetooth (Chạy trên Thread riêng)
    @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    private fun connectBluetoothDevice() {
        if (macAddress == null) return

        Thread {
            Log.d("BluetoothConnection", "Attempting to connect to $macAddress...")
            try {
                // Đóng socket cũ trước khi thử kết nối mới
                closeBluetooth()

                val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(macAddress)
                val newSocket = device.createRfcommSocketToServiceRecord(targetUUID)
                newSocket.connect() // Thao tác blocking

                // Nếu kết nối thành công:
                bluetoothSocket = newSocket
                connectedThread = ConnectedThread(bluetoothSocket!!)
                connectedThread!!.start()

                Log.i("BluetoothConnection", "Connection successful to $macAddress!")

            } catch (e: IOException) {
                Log.e("BluetoothConnection", "Unable to connect: ${e.message}", e)
                closeBluetooth() // Đảm bảo đóng socket nếu thất bại
                // Không cần logic reconnect ở đây, nó sẽ được xử lý trong ConnectedThread nếu bị mất
            } catch (e: SecurityException) {
                Log.e("BluetoothConnection", "Permission missing (BLUETOOTH_CONNECT): ${e.message}", e)
            }
        }.start()
    }

    private fun closeBluetooth() {
        connectedThread?.cancel() // Đóng luồng
        connectedThread = null
        try {
            bluetoothSocket?.close()
        } catch (e: IOException) {
            Log.e("BluetoothConnection", "Error closing socket: ${e.message}")
        } finally {
            bluetoothSocket = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        closeBluetooth() // Đóng kết nối khi service bị hủy
    }

    // (Hàm convertToNoAccent giữ nguyên)
    fun convertToNoAccent(input: String): String {
        val accents = mapOf(
            'á' to 'a', 'à' to 'a', 'ả' to 'a', 'ã' to 'a', 'ạ' to 'a',
            'ấ' to 'a', 'ầ' to 'a', 'ẩ' to 'a', 'ẫ' to 'a', 'ậ' to 'a',
            'ắ' to 'a', 'ằ' to 'a', 'ẳ' to 'a', 'ẵ' to 'a', 'ặ' to 'a',
            'é' to 'e', 'è' to 'e', 'ẻ' to 'e', 'ẽ' to 'e', 'ệ' to 'e',
            'ê' to 'e', 'ế' to 'e', 'ề' to 'e', 'ể' to 'e', 'ễ' to 'e',
            'í' to 'i', 'ì' to 'i', 'ỉ' to 'i', 'ĩ' to 'i', 'ị' to 'i',
            'ó' to 'o', 'ò' to 'o', 'ỏ' to 'o', 'õ' to 'o', 'ọ' to 'o',
            'ố' to 'o', 'ồ' to 'o', 'ổ' to 'o', 'ỗ' to 'o', 'ộ' to 'o',
            'ớ' to 'o', 'ờ' to 'o', 'ở' to 'o', 'ỡ' to 'o', 'ợ' to 'o',
            'ú' to 'u', 'ù' to 'u', 'ủ' to 'u', 'ũ' to 'u', 'ụ' to 'u',
            'ý' to 'y', 'ỳ' to 'y', 'ỷ' to 'y', 'ỹ' to 'y', 'ỵ' to 'y',
            'Đ' to 'D', 'đ' to 'd', 'Á' to 'A', 'À' to 'A', 'Ả' to 'A', 'Ã' to 'A', 'Ạ' to 'A',
            'Ấ' to 'A', 'Ầ' to 'A', 'Ẩ' to 'A', 'Ẫ' to 'A', 'Ậ' to 'A',
            'Ắ' to 'A', 'Ằ' to 'A', 'Ẳ' to 'A', 'Ẵ' to 'A', 'Ặ' to 'A',
            'É' to 'E', 'È' to 'E', 'Ẻ' to 'E', 'Ẽ' to 'E', 'Ệ' to 'E',
            'Ê' to 'E', 'Ế' to 'E', 'Ề' to 'E', 'Ể' to 'E', 'Ễ' to 'E',
            'Í' to 'I', 'Ì' to 'I', 'Ỉ' to 'I', 'Ĩ' to 'I', 'Ị' to 'I',
            'Ó' to 'O', 'Ò' to 'O', 'Ỏ' to 'O', 'Õ' to 'O', 'Ọ' to 'O',
            'Ố' to 'O', 'Ồ' to 'O', 'Ổ' to 'O', 'Ỗ' to 'O', 'Ộ' to 'O',
            'Ớ' to 'O', 'Ờ' to 'O', 'Ở' to 'O', 'Ỡ' to 'O', 'Ợ' to 'O',
            'Ú' to 'U', 'Ù' to 'U', 'Ủ' to 'U', 'Ũ' to 'U', 'Ụ' to 'U',
            'Ý' to 'Y', 'Ỳ' to 'Y', 'Ỷ' to 'Y', 'Ỹ' to 'Y', 'Ỵ' to 'Y'
        )
        return input.map { accents[it] ?: it }.joinToString("")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // *** CẢI TIẾN 2: SỬ DỤNG PACKAGE FILTER ĐÃ ĐỌC TRONG onCreate() ***
        if (sbn.packageName == packageNameFilter) {

            val notificationObject = JSONObject()
            try {
                val extras = sbn.notification.extras
                notificationObject.put("packageName", sbn.packageName)
                notificationObject.put("title", extras.getString("android.title"))

                // Fix lỗi ClassCastException: Dùng getCharSequence()
                val textCharSequence: CharSequence? = extras.getCharSequence("android.text")
                val bigTextCharSequence: CharSequence? = extras.getCharSequence("android.bigText")
                val text: String = (textCharSequence ?: bigTextCharSequence)?.toString() ?: "No Text"

                notificationObject.put("text", convertToNoAccent(text))

                notificationsArray.put(notificationObject)

                val output = convertToNoAccent(text)
                Log.d("MoMoNotification", "Parsed and sending: $output")

                // Gửi dữ liệu qua Bluetooth
                sendData(output)

            } catch (e: Exception) {
                Log.e("NotificationReader", "Error creating JSON", e)
            }
        }
    }

    private fun sendData(data: String) {
        if (connectedThread != null) {
            try {
                connectedThread?.write(data.toByteArray())
            } catch (e: Exception) {
                Log.e("BluetoothSend", "Error writing data: ${e.message}. Attempting reconnect.", e)
                // Kích hoạt logic kết nối lại nếu gửi thất bại
                connectBluetoothDevice()
            }
        } else {
            Log.e("BluetoothSend", "Error: ConnectedThread not ready. Attempting connection.",)
            connectBluetoothDevice() // Thử kết nối nếu chưa có thread
        }
    }

    // (Hàm writeToFile bên ngoài giữ nguyên nếu cần, nhưng không được gọi)
    private fun writeToFile(data: String) {
        val file = File(getExternalFilesDir(null), "notifications.json")
        try {
            FileOutputStream(file, true).use { fos ->  // Append mode
                fos.write(data.toByteArray())
                fos.write('\n'.code) // Optional: Add a newline for each entry
            }
        } catch (e: IOException) {
            Log.e("NotificationReader", "Error writing to file", e)
        }
    }

    private inner class ConnectedThread(private val socket: BluetoothSocket) : Thread() {
        private val inputStream: InputStream = socket.inputStream
        private val outputStream: OutputStream = socket.outputStream
        @Volatile private var isRunning = true // Cờ để kiểm soát vòng lặp run()

        // *** HÀM writeToFile THỪA ĐÃ ĐƯỢC XÓA KHỎI ConnectedThread ***

        fun write(bytes: ByteArray) {
            try {
                // Thêm ký tự xuống dòng (\n)
                val dataWithTerminator = bytes + "\n".toByteArray(Charsets.UTF_8)

                outputStream.write(dataWithTerminator)
                Log.i("ConnectedThread", "Data sent successfully with terminator: ${String(bytes)}")
            } catch (e: IOException) {
                Log.e("BluetoothActivity", "Error sending data", e)
                throw e // Ném lỗi để sendData có thể bắt và kích hoạt reconnect
            }
        }

        // Dùng để dừng thread một cách an toàn
        fun cancel() {
            isRunning = false
            try {
                socket.close() // Việc đóng socket sẽ gây ra IOException và thoát khỏi vòng lặp run()
            } catch (e: IOException) {
                Log.e("ConnectedThread", "close() of connect socket failed", e)
            }
        }

        override fun run() {
            val buffer = ByteArray(1024)
            var bytes: Int
            while (isRunning) { // Dùng cờ isRunning
                try {
                    bytes = inputStream.read(buffer)
                    if (bytes > 0) {
                        val receivedData = String(buffer, 0, bytes)
                        // Xử lý dữ liệu nhận nếu cần (ví dụ: phản hồi từ Pico)
                        Log.d("ConnectedThread", "Received data: $receivedData")
                    }
                } catch (e: IOException) {
                    if (isRunning) { // Chỉ reconnect nếu không phải do cancel()
                        Log.e("BluetoothActivity", "Connection lost, attempting reconnect...", e)
                        // *** KHUYẾN NGHỊ: TỰ ĐỘNG KẾT NỐI LẠI ***
                        try {
                            Thread.sleep(RECONNECT_DELAY_MS)
                        } catch (ie: InterruptedException) {
                            Thread.currentThread().interrupt()
                        }

                        // Gọi hàm kết nối lại trong Service (phải dùng runOnUiThread hoặc Handler)
                        // Do bạn không có Handler, gọi connectBluetoothDevice() là cách đơn giản nhất
                        // Tuy nhiên, việc này sẽ chạy trên Thread hiện tại, hơi phức tạp.
                        // Để đơn giản, ta sẽ chỉ gọi hàm kết nối lại từ luồng chính (Main Thread)
                        // Bắt buộc phải tắt Thread hiện tại và Main Thread sẽ lo reconnect
                        break
                    }
                }
            }
            // Nếu thoát vòng lặp, tự động thử kết nối lại
            if (isRunning) {
                connectBluetoothDevice()
            }
        }
    }
}