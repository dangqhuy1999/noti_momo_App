# \# MoMo Notifier: Thông báo Thanh toán Qua Bluetooth

# 

# \## Tổng Quan

# Dự án này sử dụng một ứng dụng Android tùy chỉnh để lắng nghe thông báo thanh toán từ ứng dụng MoMo, sau đó gửi nội dung thông báo qua Bluetooth Classic đến một thiết bị ESP32. ESP32 sẽ xử lý dữ liệu và phát âm thanh thông báo qua chip khuếch đại âm thanh MAX98357A.

# 

# \## Cấu Trúc Hệ Thống

# 

# | Thành phần        | Mô tả                                             | Công nghệ                    |

# |-------------------|---------------------------------------------------|------------------------------|

# | Nguồn thông báo   | Ứng dụng MoMo (hoặc bất kỳ ứng dụng nào được cấu hình) | Android Notification System  |

# | Ứng dụng lắng nghe | Android App tùy chỉnh                            | Kotlin, NotificationListenerService, Bluetooth Classic API |

# | Thiết bị nhận \& Phát | Vi điều khiển/Bộ xử lý                          | ESP32, Bluetooth Classic      |

# | Phát âm thanh     | Chip khuếch đại Class D                           | MAX98357A (hoặc DFPlayer)     |

# 

# \## Hướng Dẫn Sử Dụng Ứng Dụng Android

# 

# Ứng dụng Android chịu trách nhiệm quét thiết bị, lưu cấu hình và duy trì kết nối Bluetooth ổn định với ESP32.

# 

# \### Bước 1: Cấp quyền và Bật Bluetooth

# 1\. \*\*Bật Bluetooth\*\*: Đảm bảo Bluetooth trên điện thoại của bạn đã được bật.

# 2\. \*\*Cấp quyền\*\*: Lần đầu mở ứng dụng, Android sẽ yêu cầu các quyền sau. Vui lòng cho phép tất cả để chức năng quét và kết nối hoạt động:

# &nbsp;  - Bluetooth/Thiết bị lân cận (BLUETOOTH\_SCAN, BLUETOOTH\_CONNECT)

# &nbsp;  - Vị trí (Cần thiết để quét thiết bị Bluetooth trên Android hiện đại)

# &nbsp;  - Cấp quyền Lắng nghe Thông báo: Ứng dụng sẽ đưa bạn đến cài đặt hệ thống. Vui lòng tìm ứng dụng com.example.loamomo (hoặc tên ứng dụng của bạn) và bật quyền truy cập thông báo.

# 

# \### Bước 2: Quét và Chọn Thiết bị ESP32

# Trên màn hình chính của ứng dụng, nhấn nút \*\*"Quét \& Chọn Thiết Bị Bluetooth"\*\*.

# 

# 1\. Một hộp thoại sẽ xuất hiện liệt kê các thiết bị Bluetooth được tìm thấy.

# 2\. Chọn thiết bị ESP32/HC-05 của bạn (Tên thường là "ESP32-BT" hoặc "HC-05", v.v.). Địa chỉ MAC của thiết bị đã chọn sẽ hiển thị trong trường \*\*"Địa chỉ MAC"\*\*.

# 

# \### Bước 3: Lưu Cấu hình và Khởi động Service

# 1\. \*\*UUID Dịch vụ\*\*: Giữ nguyên giá trị mặc định `00001101-0000-1000-8000-00805F9B34FB` trừ khi bạn đã cấu hình ESP32 sử dụng UUID khác.

# 2\. \*\*Package App lắng nghe\*\*: Giữ nguyên `com.mservice.momotransfer` để lắng nghe thông báo từ MoMo.

# 3\. Nhấn nút \*\*"Lưu Cấu Hình và Khởi động Service"\*\*.

# 

# \*\*QUAN TRỌNG\*\*: Ứng dụng sẽ tự động gửi lệnh đến Service đang chạy để đọc lại cấu hình mới và tự động kết nối lại Bluetooth ngay lập tức. Bạn không cần phải tắt và mở lại ứng dụng. Trạng thái kết nối sẽ được hiển thị trên TextView ở cuối màn hình.

# 

# \## Mã Nguồn Android

# 

# Dưới đây là một số thay đổi quan trọng trong mã nguồn của ứng dụng Android.

# 

# \### 1. `MainActivity.kt` (Quản lý Cấu hình \& Gửi Lệnh)

# \- Tính năng \*\*Quét Bluetooth\*\* được thêm vào để chọn MAC an toàn, loại bỏ việc nhập thủ công.

# \- Hàm `saveConfig()` không còn dừng và khởi động lại Service một cách cưỡng bức. Thay vào đó, nó gửi một Intent với hành động \*\*ACTION\_RECONNECT\*\* đến Service.

# 

# \### 2. `MyNotificationListener.kt` (Quản lý Service \& Bluetooth)

# \- \*\*Khởi tạo an toàn\*\*: Biến `bluetoothAdapter` được khởi tạo ngay trong `onCreate()` của Service để tránh lỗi \*\*UninitializedPropertyAccessException\*\*.

# \- \*\*Xử lý Lệnh\*\*: Service nhận Intent trong `onStartCommand()`. Nếu action là \*\*ACTION\_RECONNECT\*\*, nó sẽ gọi hàm `connectWithNewConfig()`.

# \- \*\*Tự cập nhật\*\*: `connectWithNewConfig()` đọc lại cấu hình (MAC, UUID) từ \*\*SharedPreferences\*\* và gọi `connectBluetoothDevice()` để thiết lập lại kết nối Bluetooth ngay lập tức.

# 

# \## Cấu Hình ESP32 (Firmware)

# 

# Firmware của ESP32 cần được lập trình để lắng nghe các chuỗi dữ liệu (ví dụ: "so tien: 300 d") qua Bluetooth Serial.

# 

# 1\. Sau khi nhận chuỗi, ESP32 sẽ sử dụng thư viện \*\*MAX98357A/I2S\*\* để phát tệp âm thanh tương ứng với số tiền nhận được.

# 

# \### Hướng Dẫn Cài Đặt Firmware cho ESP32

# 

# 1\. \*\*Kết nối ESP32 với máy tính\*\*.

# 2\. \*\*Cài đặt môi trường phát triển Arduino IDE\*\*.

# 3\. \*\*Cài đặt thư viện MAX98357A\*\* từ Arduino Library Manager.

# 4\. \*\*Upload Firmware\*\* vào ESP32.

# &nbsp;  - Cấu hình ESP32 để nhận chuỗi dữ liệu Bluetooth.

# &nbsp;  - Dựa trên dữ liệu nhận được (ví dụ: số tiền), phát âm thanh tương ứng.

# 

# \## Yêu Cầu Hệ Thống

# 

# \- \*\*Android\*\*: Phiên bản 6.0 (Marshmallow) trở lên.

# \- \*\*ESP32\*\*: Mã nguồn ESP32 phải được lập trình với các thư viện cần thiết.

# 

# \## Các Tính Năng Tiềm Năng

# 

# \- Tự động nhận diện thông báo thanh toán từ nhiều ứng dụng khác nhau.

# \- Tùy chỉnh âm thanh thông báo theo từng loại thanh toán hoặc giá trị.

# \- Hỗ trợ nhiều thiết bị ESP32 kết nối đồng thời.

# 

# \## Liên Hệ

# 

# Nếu bạn có bất kỳ câu hỏi nào về dự án này, vui lòng liên hệ với chúng tôi qua:

# \- Email: example@example.com

# \- GitHub: \[MoMo Notifier GitHub](https://github.com/example)

# 

# ---

# 

# > \*\*Lưu ý:\*\* Đây là một dự án mã nguồn mở. Bạn có thể sao chép, sửa đổi và phân phối lại mã nguồn theo giấy phép \[MIT License](https://opensource.org/licenses/MIT).



