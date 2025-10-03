# 🔔 MoMo Notifier: Payment Notifications via Bluetooth

## Overview

This project utilizes a custom Android application to listen for payment notifications from the **MoMo app**. It then sends the notification content via **Bluetooth Classic** to an **ESP32** microcontroller. The ESP32 processes the data and plays an audible alert through an audio amplifier chip, specifically the **MAX98357A**.

---

## ✅ System Architecture

| Component | Description | Technology |
| :--- | :--- | :--- |
| **Notification Source** | MoMo application (or any configured app) | Android Notification System |
| **Listening Application** | Custom Android App | **Kotlin, `NotificationListenerService`, Bluetooth Classic API** |
| **Receiver & Player** | Microcontroller/Processor | **ESP32**, Bluetooth Classic |
| **Audio Playback** | Class D Amplifier Chip | **MAX98357A** (or DFPlayer) |

---

## 🛠️ Android Application Usage Guide

The Android application is responsible for device scanning, saving the configuration, and maintaining a stable Bluetooth connection with the ESP32.

### Step 1: Grant Permissions and Enable Notification Access

1.  **Enable Bluetooth & Grant Runtime Permissions:** Ensure Bluetooth is on. The first time you open the app, Android will request **Bluetooth/Nearby Devices** and **Location** permissions. Please **allow all**.
2.  **Grant Notification Access (Crucial Step):** The application will direct you to the system settings. Please find your application (e.g., **`com.example.loamomo`**) and **enable Notification Access**. This step allows the app to read notifications.


### Step 2: Scan and Select the ESP32 Device

1.  On the main screen of the application, press the **"Scan & Select Bluetooth Device"** button.
2.  A dialog box will appear listing found Bluetooth devices.

3.  **Select your ESP32/HC-05 device**. The MAC address of the selected device will be automatically populated in the **"MAC Address"** field.

### Step 3: Save Configuration and Start Service

1.  **Review Configuration:**
    * **MAC Address:** Should be filled in from Step 2.
    * **Bluetooth Service UUID:** Keep the default value (`00001101-0000-1000-8000-00805F9B34FB`) unless configured otherwise on the ESP32.
    * **Listening App Package:** Keep the default `com.mservice.momotransfer`.
2.  Press the **"Save Config and Start Service"** button.
    * **IMPORTANT:** The app will send an `ACTION_RECONNECT` command to the running Service to immediately re-read the configuration and re-establish the Bluetooth connection.
    * The **Connection Status** at the bottom will update to show success.



---

## 💻 Android Source Code (Summary of Changes)

The project uses a safe and stable communication strategy between the Activity and the Service to ensure the Service updates its configuration instantly.

1.  **`MainActivity.kt` (Configuration Management & Command Sending):**
    * Implemented **Bluetooth Scanning** functionality for secure MAC selection.
    * Uses an **Intent with `ACTION_RECONNECT`** to command the Service to re-read the configuration instead of forcefully restarting the Service.

2.  **`MyNotificationListener.kt` (Service & Bluetooth Management):**
    * **Safe Initialization:** The `bluetoothAdapter` is safely initialized inside `onCreate()`.
    * **Command Handling:** The Service receives the Intent in **`onStartCommand()`**. If the `action` is `ACTION_RECONNECT`, it calls the **`connectWithNewConfig()`** function.
    * **Self-Update:** `connectWithNewConfig()` re-reads the configuration (MAC, UUID) from `SharedPreferences` and calls **`connectBluetoothDevice()`** to immediately re-establish the Bluetooth connection.

3.  **ESP32 (Firmware):**
    * The firmware must be programmed to listen for data strings (e.g., `so tien: 300 d`) via **Bluetooth Serial**.
    * It uses the **MAX98357A/I2S** library to play the corresponding audio file based on the received amount.