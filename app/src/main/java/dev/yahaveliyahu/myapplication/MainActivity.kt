package dev.yahaveliyahu.myapplication

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.net.wifi.WifiManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaRecorder
import android.os.BatteryManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import android.content.BroadcastReceiver
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlin.math.abs
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.BundleCompat
import kotlinx.coroutines.CancellableContinuation
import android.util.Log
import android.media.AudioFormat
import android.media.AudioRecord
import android.graphics.Color
import android.media.AudioManager
import android.media.AudioDeviceInfo
import android.os.Build
import androidx.core.graphics.toColorInt


private const val PERMISSION_REQUEST_CODE = 100
private val REQUIRED_PERMISSIONS = mutableListOf(
    android.Manifest.permission.CAMERA,
    android.Manifest.permission.RECORD_AUDIO,
    android.Manifest.permission.ACCESS_FINE_LOCATION,
    android.Manifest.permission.ACCESS_COARSE_LOCATION
).apply {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(android.Manifest.permission.BLUETOOTH_CONNECT)
        add(android.Manifest.permission.BLUETOOTH_SCAN)
    }
}.toTypedArray()


private const val TARGET_WIFI_SSID = "YAHAV" // Condition 5 - The WiFi network you are looking for
private const val TARGET_BT_DEVICE_NAME = "LG-TONE-FP9" // Condition 3 - The Bluetooth connection you are looking for
private const val NOISE_THRESHOLD = 100 // Condition 4

class MainActivity : AppCompatActivity() {

    // Variables for UI elements
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvCheck1plus2: TextView
    private lateinit var tvCheck3: TextView
    private lateinit var tvCheck4: TextView
    private lateinit var tvCheck5: TextView
    private lateinit var tvCheck6: TextView


    // Camera + smile detection
    private lateinit var cameraLauncher: ActivityResultLauncher<Intent>
    private var smileContinuation: CancellableContinuation<Pair<Boolean, String>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        // Links the variables to the Views on the screen + defines a click on the login button
        initViews()
        initSmileCamera()

        // Handling permissions
        if (!hasAllPermissions()) {
            requestPermissions()
        }
    }

    private fun initViews() {
        etPassword = findViewById(R.id.et_password)
        btnLogin = findViewById(R.id.btn_login)
        tvStatus = findViewById(R.id.tv_status)
        tvCheck1plus2 = findViewById(R.id.tv_check_1_plus_2)
        tvCheck3 = findViewById(R.id.tv_check_3)
        tvCheck4 = findViewById(R.id.tv_check_4)
        tvCheck5 = findViewById(R.id.tv_check_5)
        tvCheck6 = findViewById(R.id.tv_check_6)


        // Calls the central authentication function when a user clicks the "Login" button
        btnLogin.setOnClickListener { attemptLogin() }
    }

    private fun updateChecklistItem(index: Int, success: Boolean, label: String) {
        val tv = when (index) {
            0 -> tvCheck1plus2
            1 -> tvCheck3
            2 -> tvCheck4
            3 -> tvCheck5
            4 -> tvCheck6
            else -> null
        } ?: return

        if (success) {
            tv.text = getString(R.string.check_item_success, label)
            tv.setTextColor("#008000".toColorInt()) // green
        } else {
            tv.text = getString(R.string.check_item_failure, label)
            tv.setTextColor(Color.RED)
        }

    }

    private fun resetChecklist() {
        tvCheck1plus2.text = getString(R.string.check_default_1_2)
        tvCheck3.text = getString(R.string.check_default_3)
        tvCheck4.text = getString(R.string.check_default_4)
        tvCheck5.text = getString(R.string.check_default_5)
        tvCheck6.text = getString(R.string.check_default_6)

        val defaultColor = Color.DKGRAY
        tvCheck1plus2.setTextColor(defaultColor)
        tvCheck3.setTextColor(defaultColor)
        tvCheck4.setTextColor(defaultColor)
        tvCheck5.setTextColor(defaultColor)
        tvCheck6.setTextColor(defaultColor)
    }

    // Goes through all the permissions in the REQUIRED_PERMISSIONS array and makes sure that the user has already approved each one
    private fun hasAllPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    // Sends a request to the user to confirm the missing permissions
    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            REQUIRED_PERMISSIONS,
            PERMISSION_REQUEST_CODE
        )
    }

    // Handling the user's response to the permission request
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                tvStatus.text = getString(R.string.status_permissions_granted)
            } else {
                tvStatus.text = getString(R.string.status_permissions_denied)
                Toast.makeText(this, getString(R.string.permissions_denied_toast), Toast.LENGTH_LONG).show()
            }
        }
    }

    // Validation functions (conditions)

   // The function checks both that the battery percentage is between 40%–80%
   // and that the password contains the battery percentage (e.g. 57 → "myPass57!")
    private fun checkBatteryConditions(inputPassword: String): Pair<Boolean, String> {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter -> this.registerReceiver(null, filter)}
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1

        if (level == -1 || scale == -1) {
            return Pair(false, "Failed to read battery level")
        }

        val batteryPct = (level.toFloat() / scale.toFloat() * 100).toInt()
        val batteryPctString = batteryPct.toString()

        // Condition 1: Battery percentage in the range of 40%-80%
        val isBatteryInRange = batteryPct >= 40 && batteryPct <= 80
        if (!isBatteryInRange) {
            return Pair(false, "Battery is $batteryPct% but must be between 40% and 80%.")
        }

        // Condition 2: The password contains the battery percentage
        val isPasswordContextual = inputPassword.contains(batteryPctString)
        if (!isPasswordContextual) {
            return Pair(false, "Password must contain current battery percentage ($batteryPct%).")
        }
        return Pair(true, "Battery and Contextual Password checks passed (Battery: $batteryPct%).")
    }

    // Condition 3: Check if the active audio output is our Bluetooth headphones
    private fun checkBluetoothHeadphonesCondition(): Pair<Boolean, String> {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        // All audio devices used for output
        val outputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        // Looking for a device that is Bluetooth/LE and has a name like TARGET
        val target = outputDevices.firstOrNull { dev ->
            val type = dev.type
            val isBtType =
                type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                                (type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER))
            isBtType && dev.productName.toString().equals(TARGET_BT_DEVICE_NAME, ignoreCase = true)
        }
        return if (target != null) {
            Pair(true, "Bluetooth audio device '$TARGET_BT_DEVICE_NAME' is active.")
        } else {
            Pair(false, "Target Bluetooth device '$TARGET_BT_DEVICE_NAME' is not the active audio output.")
        }
    }

    // Condition 4: Check noise level using MediaRecorder on the microphone
    private fun checkNoiseCondition(): Pair<Boolean, String> {
        // Checks whether the user has permission to use the microphone
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w("NoiseCheck", "RECORD_AUDIO permission NOT granted")
            return Pair(false, "Microphone permission not granted.")
        }
        val sampleRate = 44100 // This is the sample rate (on Hertz) that most Android devices support (CD quality)
        // Calculate how much memory is needed to record in this mode. If the value is small or negative, then there is no support
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (bufferSize <= 0) {
            Log.e("NoiseCheck", "AudioRecord not supported on this device (bufferSize <= 0)")
            return Pair(false, "AudioRecord not supported on this device.")
        }

        // AudioFormat construction
        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()

        // Building an AudioRecord to create an object that allows you to record raw sound from the system
        val audioRecord = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(bufferSize)
            .build()

        // Creating an array that will store the samples to be recorded
        val buffer = ShortArray(bufferSize)

        // Starting to record
        return try {
            audioRecord.startRecording()
            Thread.sleep(300) // Waiting 300ms for the microphone to actually fill with real samples

            val read = audioRecord.read(buffer, 0, buffer.size)
            if (read > 0) {
                val maxAmplitude = buffer.maxOf { abs(it.toInt()) } // Noise intensity calculation
                if (maxAmplitude > NOISE_THRESHOLD) {
                    Pair(true, "Noise level OK (Amplitude: $maxAmplitude)")
                } else {
                    Pair(false, "Too quiet (Amplitude: $maxAmplitude)")
                }
            } else {
                Log.e("NoiseCheck", "Failed to read audio data (read <= 0)")
                Pair(false, "Failed to read audio data.")
            }
        } catch (e: Exception) {
            Log.e("NoiseCheck", "SecurityException: ${e.message}")
            Pair(false, "Noise check failed: ${e.message}")
        // Cleanup and closing resources
        } finally {
            try {
                audioRecord.stop()
            } catch (_: Exception) {
            }
            audioRecord.release()
        }
    }

    // Condition 5: Check for the presence of a specific WiFi network
    @Suppress("DEPRECATION")
    private suspend fun checkWifiCondition(): Pair<Boolean, String> =
        suspendCancellableCoroutine { continuation ->

            // Checking location permission
            val hasLocationPermission = ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!hasLocationPermission) {
                continuation.resume(Pair(false, "Missing Location Permission for WiFi scan."))
                return@suspendCancellableCoroutine
            }

            // Checking that WiFi is on
            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            if (!wifiManager.isWifiEnabled) {
                continuation.resume(Pair(false, "WiFi is disabled."))
                return@suspendCancellableCoroutine
            }

            // Defines a BroadcastReceiver that will receive the scan results
            val wifiScanReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                        unregisterReceiver(this) // When the network is found, the coroutine is released because the application can leak memory

                        try {
                            val scanResults = wifiManager.scanResults
                        // Checks if there is a WiFi network with an SSID that matches the TARGET WIFI_SSID. Android sometimes puts quotes around it, that's why replace("\"", "")
                            val found = scanResults.any { result ->
                                result.SSID.replace("\"", "").equals(TARGET_WIFI_SSID, ignoreCase = true)
                            }
                            if (found) {
                                continuation.resume(Pair(true, "Target WiFi '$TARGET_WIFI_SSID' found."))
                            } else {
                                continuation.resume(Pair(false, "Target WiFi '$TARGET_WIFI_SSID' not found."))
                            }

                        } catch (_: SecurityException) {
                            continuation.resume(Pair(false, "WiFi scan failed: missing permission."))
                        }
                    }
                }
            }

            // Registering Receiver to receive a notification once the WiFi scan is complete
            registerReceiver(wifiScanReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))

            // Start scanning
            val success = try {
                wifiManager.startScan()
            } catch (_: SecurityException) {
                continuation.resume(Pair(false, "WiFi scan blocked: missing permission."))
                return@suspendCancellableCoroutine
            }

            if (!success) {
                unregisterReceiver(wifiScanReceiver)
                continuation.resume(Pair(false, "Failed to start WiFi scan."))
            }

            // If the coroutine is canceled in the middle, remove the Receiver to avoid leaving "garbage" in the system
            continuation.invokeOnCancellation {
                try { unregisterReceiver(wifiScanReceiver) } catch (_: Exception) {}
            }
        }

    // Condition 6: Real smile detection with ML Kit
    private fun initSmileCamera() {
        // Opening a Launcher that is ready in the future to receive the result of taking a picture
        cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                val continuation = smileContinuation ?: return@registerForActivityResult

                // If the user clicked Back or canceled the camera, we will return a failure for the test and clear the variable
                if (result.resultCode != RESULT_OK) {
                    continuation.resume(Pair(false, "Smile check canceled by user."))
                    smileContinuation = null
                    return@registerForActivityResult
                }
                // Trying to get the image as a Bitmap from the Intent. This works when the camera returns a thumbnail
                val bitmap: Bitmap? = result.data?.extras?.let {
                    BundleCompat.getParcelable(it, "data", Bitmap::class.java)
                }

                if (bitmap == null) {
                    continuation.resume(
                        Pair(false, "Failed to capture image for smile detection.")
                    )
                    smileContinuation = null
                    return@registerForActivityResult
                }

                // ML Kit works with InputImage. The number 0 is rotation, meaning no rotation.
                val image = InputImage.fromBitmap(bitmap, 0)

                // Setting facial recognition options
                val options = FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                    .build()

                // Creating an ML Kit object that recognizes faces and smiles
                val detector = FaceDetection.getClient(options)

                // Starting image processing
                detector.process(image)
                    .addOnSuccessListener { faces ->
                        if (faces.isEmpty()) {
                            continuation.resume(Pair(false, "No face detected."))
                        } else {
                            // If at least one face is found. Take the first face and extract Smile Probability — a number between 0 and 1
                            val face = faces[0]
                            val smileProb = face.smilingProbability ?: 0f
                            val smilePercent = (smileProb * 100).toInt()
                            // If the smile is above 70%, then we got success
                            if (smileProb >= 0.7f) {
                                continuation.resume(Pair(true, "Smile detected ($smilePercent%)."))
                            } else {
                                continuation.resume(Pair(false, "Smile too weak ($smilePercent%)."))
                            }
                        }
                        smileContinuation = null // Cleanup after completion to not save old continuation
                    }
                    .addOnFailureListener { e ->
                        continuation.resume(Pair(false, "Smile detection failed: ${e.message}"))
                        smileContinuation = null
                    }
            }
    }
    // To run the camera asynchronously and wait for the result
    private suspend fun checkSmileCondition(): Pair<Boolean, String> = suspendCancellableCoroutine { continuation ->
            smileContinuation = continuation
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            cameraLauncher.launch(cameraIntent)

            continuation.invokeOnCancellation {
                smileContinuation = null
            }
        }

    // The central verification function
    private fun attemptLogin() {
        // If all permissions are not present, we don't continue checking conditions at all.
        // We update the status, request permissions again, and exit the function
        if (!hasAllPermissions()) {
            tvStatus.text = getString(R.string.status_missing_permissions)
            requestPermissions()
            return
        }
        // If the user did not write a password, no conditions are checked
        val inputPassword = etPassword.text.toString()
        if (inputPassword.isBlank()) {
            tvStatus.text = getString(R.string.status_please_enter_password)
            return
        }
        tvStatus.text = getString(R.string.status_running_checks)
        resetChecklist()

        // Open a coroutine on the Main thread and create a list that will store the results of all the conditions
        lifecycleScope.launch(Dispatchers.Main) {
            val results = mutableListOf<Pair<Boolean, String>>()

            // Conditions 1 and 2: Battery percentage check and contextual password
            val (batterySuccess, batteryMessage) = checkBatteryConditions(inputPassword)
            results.add(Pair(batterySuccess, "Conditions 1+2: Battery and Password Check: $batteryMessage"))
            updateChecklistItem(
                index = 0,
                success = batterySuccess,
                label = "Conditions 1+2: Battery and Password – $batteryMessage"
            )

            // Condition 3: Bluetooth headphones check (SUSPEND)
            val (btSuccess, btMessage) = checkBluetoothHeadphonesCondition()
            results.add(Pair(btSuccess, "Condition 3: Bluetooth Headphones Check: $btMessage"))
            updateChecklistItem(
                index = 1,
                success = btSuccess,
                label = "Condition 3: Bluetooth connection – $btMessage"
            )

            // Condition 4: Noise threshold test
            val (noiseSuccess, noiseMessage) = checkNoiseCondition()
            results.add(Pair(noiseSuccess, "Condition 4: Noise Check: $noiseMessage"))
            updateChecklistItem(
                index = 2,
                success = noiseSuccess,
                label = "Condition 4: Noise threshold – $noiseMessage"
            )

            // Condition 5: WiFi connection check (SUSPEND)
            val (wifiSuccess, wifiMessage) = checkWifiCondition()
            results.add(Pair(wifiSuccess, "Condition 5: WiFi Check: $wifiMessage"))
            updateChecklistItem(
                index = 3,
                success = wifiSuccess,
                label = "Condition 5: WiFi Network – $wifiMessage"
            )

            // Condition 6: Smile detection (SUSPEND)
            val (smileSuccess, smileMessage) = checkSmileCondition()
            results.add(Pair(smileSuccess, "Condition 6: Smile Check: $smileMessage"))
            updateChecklistItem(
                index = 4,
                success = smileSuccess,
                label = "Condition 6: Smile Detection – $smileMessage"
            )

            // Summary of results
            val overallSuccess = results.all { it.first } // Did the test pass (true / false)

            // Viewing the final status
            if (overallSuccess) {
                tvStatus.text = getString(R.string.login_success)
                Toast.makeText(this@MainActivity, "Access Granted! All 6 conditions passed.", Toast.LENGTH_LONG).show()
            } else {
                val failedChecks = results.filter { !it.first }
                val failureReason = failedChecks.joinToString("\n") { it.second }

                tvStatus.text = getString(R.string.login_failed)
                Toast.makeText(this@MainActivity, "LOGIN FAILED! Reasons:\n$failureReason", Toast.LENGTH_LONG).show()
            }
        }
    }
}
