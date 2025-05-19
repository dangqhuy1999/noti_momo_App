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
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothSocket: BluetoothSocket
    private lateinit var connectedThread: ConnectedThread

    private companion object {
        //com.android.chrome
        //com.mservice.momotransfer
        //com.skype.raider
        private const val MOMO_PACKAGE_NAME = "com.skype.raider"
        private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    override fun onCreate() {
        super.onCreate()
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice("98:D3:02:96:70:F0") // Địa chỉ MAC của thiết bị Bluetooth
        bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID)
        try {
            bluetoothSocket.connect()
            connectedThread = ConnectedThread(bluetoothSocket)
            connectedThread.start()
        } catch (e: IOException) {
            Log.e("BluetoothConnection", "Unable to connect: ${e.message}", e)
        }
    }
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
        if (sbn.packageName == MOMO_PACKAGE_NAME) {
            val notificationObject = JSONObject()
            try {
                val extras = sbn.notification.extras
                notificationObject.put("packageName", sbn.packageName)
                notificationObject.put("title", extras.getString("android.title"))

                val text = extras.getString("android.text") ?: extras.getString("android.bigText") ?: "No Text"
                notificationObject.put("text", convertToNoAccent(text))
                //notificationObject.put("text", text)
                //convertToNoAccent(text)
                notificationsArray.put(notificationObject)
                writeToFile(notificationsArray.toString())

                val output = convertToNoAccent(text)

                // Gửi dữ liệu qua Bluetooth
                sendData(output)
                // object.toString())
                //sendData(output)
            } catch (e: Exception) {
                Log.e("NotificationReader", "Error creating JSON", e)
            }
        }
    }
    private fun sendData(data: String) {
        connectedThread.write(data.toByteArray())
    }
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
    private inner class ConnectedThread(socket: BluetoothSocket) : Thread() {
        private val inputStream: InputStream = socket.inputStream
        private val outputStream: OutputStream = socket.outputStream
        private fun writeToFile(data: String) {
            val file = File(getExternalFilesDir(null), "notifications.txt")
            try {
                FileOutputStream(file, true).use { fos ->  // Append mode
                    fos.write(data.toByteArray())
                    fos.write('\n'.code) // Optional: Add a newline for each entry
                }
            } catch (e: IOException) {
                Log.e("NotificationReader", "Error writing to file", e)
            }
        }
        fun write(bytes: ByteArray) {
            try {
                outputStream.write(bytes)
            } catch (e: IOException) {
                Log.e("BluetoothActivity", "Error sending data", e)
            }
        }
        override fun run() {
            val buffer = ByteArray(1024)
            var bytes: Int
            while (true) {
                try {
                    bytes = inputStream.read(buffer)
                    val receivedData = String(buffer, 0, bytes)
                    writeToFile(receivedData)
                    // Xử lý dữ liệu nhận nếu cần
                } catch (e: IOException) {
                    Log.e("BluetoothActivity", "Connection lost", e)
                    break
                }
            }
        }
    }
}