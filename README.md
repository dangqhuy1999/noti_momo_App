# 🔔 MoMo Notifier: Thông báo Thanh toán Qua Bluetooth

## Tổng quan

Dự án này sử dụng một ứng dụng Android tùy chỉnh để lắng nghe thông báo thanh toán từ ứng dụng MoMo, sau đó gửi nội dung thông báo qua Bluetooth Classic đến một thiết bị ESP32. ESP32 sẽ xử lý dữ liệu và phát âm thanh thông báo qua chip khuếch đại âm thanh **MAX98357A**.

---

## ✅ Cấu trúc hệ thống (Cập nhật)

| Thành phần | Mô tả | Công nghệ |
| :--- | :--- | :--- |
| **Nguồn thông báo** | Ứng dụng MoMo (hoặc bất kỳ ứng dụng nào được cấu hình) | Android Notification System |
| **Ứng dụng lắng nghe** | Android App tùy chỉnh | **Kotlin, `NotificationListenerService`, Bluetooth Classic API** |
| **Thiết bị nhận & Phát** | Vi điều khiển/Bộ xử lý | **ESP32**, Bluetooth Classic |
| **Phát âm thanh** | Chip khuếch đại Class D | **MAX98357A** (hoặc DFPlayer) |

---

## 🛠️ Hướng dẫn sử dụng ứng dụng Android

Ứng dụng Android chịu trách nhiệm quét thiết bị, lưu cấu hình và duy trì kết nối Bluetooth ổn định với ESP32.

### Bước 1: Cấp quyền và Bật Bluetooth

1.  **Bật Bluetooth:** Đảm bảo Bluetooth trên điện thoại của bạn đã được bật.
2.  **Cấp quyền:** Lần đầu mở ứng dụng, Android sẽ yêu cầu các quyền sau. Vui lòng **cho phép tất cả** để chức năng quét và kết nối hoạt động:
    * **Bluetooth/Thiết bị lân cận** (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`)
    * **Vị trí** (Cần thiết để quét thiết bị Bluetooth trên Android hiện đại)
3.  **Cấp quyền Lắng nghe Thông báo:** Ứng dụng sẽ đưa bạn đến cài đặt hệ thống. Vui lòng tìm ứng dụng của bạn (ví dụ: **`com.example.loamomo`**) và **bật quyền truy cập thông báo**.

### Bước 2: Quét và Chọn Thiết bị ESP32

1.  Trên màn hình chính của ứng dụng, nhấn nút **"Quét & Chọn Thiết Bị Bluetooth"**.
2.  Một hộp thoại sẽ xuất hiện liệt kê các thiết bị Bluetooth được tìm thấy.
3.  **Chọn thiết bị ESP32/HC-05** của bạn. Địa chỉ MAC của thiết bị đã chọn sẽ hiển thị trong trường **"Địa chỉ MAC"**.

### Bước 3: Lưu Cấu hình và Khởi động Service

1.  **UUID Dịch vụ:** Giữ nguyên giá trị mặc định (`00001101-0000-1000-8000-00805F9B34FB`) trừ khi bạn đã cấu hình ESP32 sử dụng UUID khác.
2.  **Package App lắng nghe:** Giữ nguyên `com.mservice.momotransfer` để lắng nghe thông báo từ MoMo.
3.  Nhấn nút **"Lưu Cấu Hình và Khởi động Service"**.
    * **QUAN TRỌNG:** Ứng dụng sẽ tự động gửi lệnh đến Service đang chạy để **đọc lại cấu hình mới và tự động kết nối lại** Bluetooth ngay lập tức. Bạn không cần phải tắt và mở lại ứng dụng.
    * Trạng thái kết nối sẽ được hiển thị trên màn hình.

---

## 💻 Mã nguồn Android (Tóm tắt thay đổi)

Dự án sử dụng chiến lược giao tiếp an toàn và ổn định giữa Activity và Service để đảm bảo Service cập nhật cấu hình ngay lập tức.

1.  **`MainActivity.kt` (Quản lý Cấu hình & Gửi lệnh):**
    * Đã triển khai tính năng **Quét Bluetooth** để chọn MAC an toàn.
    * Sử dụng **Intent với `ACTION_RECONNECT`** để ra lệnh cho Service đọc lại cấu hình thay vì khởi động lại Service.

2.  **`MyNotificationListener.kt` (Quản lý Service & Bluetooth):**
    * **Khởi tạo an toàn:** Khởi tạo `bluetoothAdapter` trong `onCreate()` để tránh lỗi.
    * **Tự cập nhật:** Nhận `Intent` có `ACTION_RECONNECT` trong `onStartCommand()`, sau đó Service tự động đọc cấu hình mới và thiết lập lại kết nối Bluetooth.

3.  **ESP32 (Firmware):**
    * Cần lập trình để lắng nghe chuỗi dữ liệu (ví dụ: `so tien: 300 d`) qua Bluetooth Serial.
    * Sử dụng thư viện **MAX98357A/I2S** để phát tệp âm thanh tương ứng với số tiền nhận được.
