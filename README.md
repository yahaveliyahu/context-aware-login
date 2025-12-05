# 📱 Context-Aware Login

An Android app that demonstrates a smart login mechanism based on environmental conditions (Context-Aware Authentication).
The user must meet 6 different conditions before being granted "access" — some based on system data, others on sensors or ML-based recognition.
The app was developed as part of a course exercise, and aims to demonstrate the use of several different Android APIs: camera, microphone, sensors, WiFi, Bluetooth, and more.

---

## ✨ Features

The app allows connection only if all 6 conditions are met:

1️⃣+2️⃣ **Battery Level + Password Context**
The condition checks two things:
The battery percentage is between 40% and 80%
The password the user entered contains the battery percentage (e.g.: battery=57 → pass57word")

**Implementation:** BatteryManager + substring check.

3️⃣ **Bluetooth Device Connected**
Checks whether the device is connected to a specific type of Bluetooth headset (e.g. LG-TONE-FP9).
The application scans the active audio devices and verifies the device name.

**Implementation:** AudioManager, AudioDeviceInfo.

4️⃣ **Noise Threshold (Microphone Test)**
Checks if there is enough noise in the environment.
The application records 300ms from the microphone, calculates the maximum amplitude and compares it to the threshold (NOISE_THRESHOLD = 100)

**Implementation:** AudioRecord

5️⃣ **WiFi Scan – Specific SSID**
Checks if a specific WiFi network is nearby (e.g. "YAHAV").
The app performs a WiFi scan, listens for SCAN_RESULTS_AVAILABLE and checks if the SSID matches

**Implementation:** WifiManager, BroadcastReceiver.

6️⃣ **Smile Detection – ML Kit**
Checks if the user is smiling in front of the camera.
Launches device camera, receives thumbnail image, processes it with ML Kit for face recognition and checks if smile level ≥ 70%

**Implementation:** ML Kit Face Detection API.

**All Conditions Summary**
If all 6 conditions are met: "Access Granted" is displayed
Otherwise, a list of reasons for failure is displayed
Control is done within a Coroutine.

---

## 🔐 Permissions Required

The app uses several permissions:
- CAMERA – Smile detection
- RECORD_AUDIO – Noise check
- ACCESS_FINE_LOCATION – WiFi scan
- BLUETOOTH_CONNECT / BLUETOOTH_SCAN – Headphones check

---

## 📸 Example Scenario (Video)

---

## 📂 Project Structure

app/
 ├── src/
 │    ├── main/
 │    │    ├── java/.../MainActivity.kt
 │    │    ├── res/layout/activity_main.xml
 │    │    ├── res/values/strings.xml
 │    │    └── AndroidManifest.xml
 │    └── ...
 └── build.gradle

 ---

## 🎯 Summary

The app demonstrates smart use of Android mechanisms:

✔ WiFi

✔ Bluetoothc

✔ Battery Manager

✔ Microphone AudioRecord

✔ Camera

✔ ML Kit Face Detection

✔ Coroutines

✔ Permissions API

and implements a context-based login system.
