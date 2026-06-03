package com.example.ble_simulator

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ble_simulator.ui.theme.BLE_SimulatorTheme
import java.nio.ByteBuffer
import java.util.UUID

class MainActivity : ComponentActivity() {
    private var advertiserCallback: AdvertiseCallback? = null

    private var beaconIdText by mutableStateOf(DEFAULT_BEACON_ID)
    private var uuidText by mutableStateOf(DEFAULT_UUID)
    private var majorText by mutableStateOf(DEFAULT_MAJOR)
    private var minorText by mutableStateOf(DEFAULT_MINOR)
    private var measuredPowerText by mutableStateOf(DEFAULT_MEASURED_POWER)
    private var txPowerLevelText by mutableStateOf("3")
    private var statusText by mutableStateOf("Ready to advertise.")
    private var isAdvertising by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(LOG_TAG, "Activity created for BLE beacon simulation.")
        enableEdgeToEdge()

        setContent {
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { grants ->
                if (grants.values.all { it }) {
                    Log.i(LOG_TAG, "Runtime Bluetooth permissions granted.")
                    startBeaconAdvertising()
                } else {
                    val deniedPermissions = grants
                        .filterValues { granted -> !granted }
                        .keys
                        .joinToString()
                    Log.w(LOG_TAG, "Runtime Bluetooth permissions denied: $deniedPermissions")
                    statusText = "Bluetooth advertising permission is required."
                }
            }

            BLE_SimulatorTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BeaconSimulatorScreen(
                        beaconIdText = beaconIdText,
                        onBeaconIdChange = { beaconIdText = it },
                        uuidText = uuidText,
                        onUuidChange = { uuidText = it },
                        majorText = majorText,
                        onMajorChange = { majorText = it },
                        minorText = minorText,
                        onMinorChange = { minorText = it },
                        measuredPowerText = measuredPowerText,
                        onMeasuredPowerChange = { measuredPowerText = it },
                        txPowerLevelText = txPowerLevelText,
                        onTxPowerLevelChange = { txPowerLevelText = it },
                        statusText = statusText,
                        isAdvertising = isAdvertising,
                        onStart = {
                            Log.i(LOG_TAG, "User requested advertising start.")
                            if (hasRequiredPermissions()) {
                                startBeaconAdvertising()
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                Log.i(LOG_TAG, "Requesting Bluetooth advertising permissions at runtime.")
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.BLUETOOTH_ADVERTISE,
                                        Manifest.permission.BLUETOOTH_CONNECT
                                    )
                                )
                            }
                        },
                        onStop = { stopBeaconAdvertising("user request") },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        Log.i(LOG_TAG, "Activity is being destroyed; advertising resources will be released.")
        stopBeaconAdvertising("activity destruction")
        super.onDestroy()
    }

    /**
     * Evaluates the Bluetooth runtime permissions required by Android 12 and later.
     * Earlier Android versions rely on manifest-declared Bluetooth permissions.
     */
    private fun hasRequiredPermissions(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Log.d(LOG_TAG, "Runtime Bluetooth permission check skipped for Android version below 12.")
            return true
        }

        val missingPermissions = listOf(
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT
        ).filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            Log.d(LOG_TAG, "All required runtime Bluetooth permissions are granted.")
            return true
        }

        Log.w(LOG_TAG, "Missing runtime Bluetooth permissions: ${missingPermissions.joinToString()}")
        return false
    }

    /**
     * Starts a non-connectable BLE advertisement that contains an iBeacon manufacturer
     * payload. The UI-level beacon ID is retained for experimental traceability, while
     * only UUID, major, minor, and measured power are encoded in the iBeacon frame.
     */
    @SuppressLint("MissingPermission")
    private fun startBeaconAdvertising() {
        val config = readBeaconConfig() ?: return
        Log.i(LOG_TAG, "Validated beacon configuration: ${config.toLogString()}")

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter

        if (adapter == null) {
            Log.e(LOG_TAG, "Bluetooth adapter is unavailable on this device.")
            statusText = "Bluetooth is not available on this device."
            return
        }
        Log.d(LOG_TAG, "Bluetooth adapter detected.")

        if (!adapter.isEnabled) {
            Log.w(LOG_TAG, "Bluetooth adapter is present but disabled.")
            statusText = "Bluetooth is turned off."
            return
        }
        Log.d(LOG_TAG, "Bluetooth adapter is enabled.")

        if (!adapter.isMultipleAdvertisementSupported) {
            Log.w(LOG_TAG, "BLE multiple advertisement support is not available on this device.")
            statusText = "BLE advertising is not supported on this device."
            return
        }
        Log.d(LOG_TAG, "BLE multiple advertisement support is available.")

        val advertiser = adapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            Log.e(LOG_TAG, "BluetoothLeAdvertiser object is unavailable despite adapter support.")
            statusText = "BLE advertiser is not available."
            return
        }

        stopBeaconAdvertising("restart before new configuration")

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(config.txPowerLevel)
            .setConnectable(false)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addManufacturerData(APPLE_COMPANY_ID, config.toIBeaconPayload())
            .build()

        Log.i(LOG_TAG, "Submitting iBeacon advertisement request: ${config.toLogString()}")
        advertiserCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                runOnUiThread {
                    Log.i(LOG_TAG, "Advertising started successfully: ${config.toLogString()}")
                    isAdvertising = true
                    statusText = "Advertising ${config.beaconId} iBeacon: ${config.uuid}, major ${config.major}, minor ${config.minor}."
                }
            }

            override fun onStartFailure(errorCode: Int) {
                runOnUiThread {
                    Log.e(
                        LOG_TAG,
                        "Advertising failed with code $errorCode (${advertiseErrorMessage(errorCode)})."
                    )
                    isAdvertising = false
                    advertiserCallback = null
                    statusText = "Advertising failed: ${advertiseErrorMessage(errorCode)}."
                }
            }
        }

        statusText = "Starting BLE advertisement..."
        advertiser.startAdvertising(settings, data, advertiserCallback)
    }

    /**
     * Stops the current BLE advertisement, if one is active. This method is invoked
     * both by explicit user action and by the activity lifecycle to avoid stale
     * advertising sessions during experimental runs.
     */
    @SuppressLint("MissingPermission")
    private fun stopBeaconAdvertising(reason: String) {
        val callback = advertiserCallback
        if (callback == null) {
            Log.d(LOG_TAG, "Advertising stop requested ($reason), but no active advertiser callback exists.")
            return
        }

        Log.i(LOG_TAG, "Stopping BLE advertisement due to: $reason.")
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val advertiser = bluetoothManager.adapter?.bluetoothLeAdvertiser

        advertiser?.stopAdvertising(callback)
        if (advertiser == null) {
            Log.w(LOG_TAG, "Advertiser object was unavailable during stop request.")
        }

        advertiserCallback = null
        isAdvertising = false
        statusText = "Advertising stopped."
        Log.i(LOG_TAG, "Advertising state cleared.")
    }

    /**
     * Converts editable UI fields into a validated configuration object. Validation
     * failures are logged because they affect reproducibility during thesis testing.
     */
    private fun readBeaconConfig(): BeaconConfig? {
        val beaconId = beaconIdText.trim()
        if (beaconId.isBlank()) {
            Log.w(LOG_TAG, "Configuration validation failed: beacon ID is blank.")
            statusText = "Enter a beacon ID."
            return null
        }

        val uuid = runCatching { UUID.fromString(uuidText.trim()) }.getOrNull()
        if (uuid == null) {
            Log.w(LOG_TAG, "Configuration validation failed: invalid UUID value '$uuidText'.")
            statusText = "Enter a valid UUID."
            return null
        }

        val major = majorText.toIntOrNull()
        if (major == null || major !in 0..65535) {
            Log.w(LOG_TAG, "Configuration validation failed: major value '$majorText' is outside 0..65535.")
            statusText = "Major must be between 0 and 65535."
            return null
        }

        val minor = minorText.toIntOrNull()
        if (minor == null || minor !in 0..65535) {
            Log.w(LOG_TAG, "Configuration validation failed: minor value '$minorText' is outside 0..65535.")
            statusText = "Minor must be between 0 and 65535."
            return null
        }

        val measuredPower = measuredPowerText.toIntOrNull()
        if (measuredPower == null || measuredPower !in -128..127) {
            Log.w(
                LOG_TAG,
                "Configuration validation failed: measured power '$measuredPowerText' is outside -128..127."
            )
            statusText = "Measured power must be between -128 and 127."
            return null
        }

        val txPowerLevel = when (txPowerLevelText.toIntOrNull()) {
            0 -> AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW
            1 -> AdvertiseSettings.ADVERTISE_TX_POWER_LOW
            2 -> AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM
            3 -> AdvertiseSettings.ADVERTISE_TX_POWER_HIGH
            else -> {
                Log.w(LOG_TAG, "Configuration validation failed: TX power level '$txPowerLevelText' is invalid.")
                statusText = "TX power level must be 0, 1, 2, or 3."
                return null
            }
        }

        return BeaconConfig(
            beaconId = beaconId,
            uuid = uuid,
            major = major,
            minor = minor,
            measuredPower = measuredPower,
            txPowerLevel = txPowerLevel
        )
    }

    private companion object {
        const val LOG_TAG = "BLEBeaconSimulator"
        const val APPLE_COMPANY_ID = 0x004C
        const val DEFAULT_BEACON_ID = "SamA15"
        const val DEFAULT_UUID = "f7826da6-4fa2-4e98-8024-bc5b71e0893e"
        const val DEFAULT_MAJOR = "20641"
        const val DEFAULT_MINOR = "50361"
        const val DEFAULT_MEASURED_POWER = "-80"

        fun advertiseErrorMessage(errorCode: Int): String {
            return when (errorCode) {
                AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "data too large"
                AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "too many advertisers"
                AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "already started"
                AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "internal error"
                AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "feature unsupported"
                else -> "error code $errorCode"
            }
        }
    }
}

/**
 * Represents the editable experimental parameters for the simulated beacon. The
 * beacon ID is an application-level label for documentation and Logcat analysis;
 * it is not part of the iBeacon payload transmitted over BLE.
 */
private data class BeaconConfig(
    val beaconId: String,
    val uuid: UUID,
    val major: Int,
    val minor: Int,
    val measuredPower: Int,
    val txPowerLevel: Int
) {
    /**
     * Constructs the 23-byte iBeacon manufacturer payload:
     * prefix, UUID, major, minor, and measured power at one metre.
     */
    fun toIBeaconPayload(): ByteArray {
        val buffer = ByteBuffer.allocate(23)
        buffer.put(0x02)
        buffer.put(0x15)
        buffer.putLong(uuid.mostSignificantBits)
        buffer.putLong(uuid.leastSignificantBits)
        buffer.putShort(major.toShort())
        buffer.putShort(minor.toShort())
        buffer.put(measuredPower.toByte())
        return buffer.array()
    }

    fun toLogString(): String {
        return "beaconId=$beaconId, uuid=$uuid, major=$major, minor=$minor, " +
            "measuredPower=$measuredPower, txPowerLevel=$txPowerLevel"
    }
}

@Composable
private fun BeaconSimulatorScreen(
    beaconIdText: String,
    onBeaconIdChange: (String) -> Unit,
    uuidText: String,
    onUuidChange: (String) -> Unit,
    majorText: String,
    onMajorChange: (String) -> Unit,
    minorText: String,
    onMinorChange: (String) -> Unit,
    measuredPowerText: String,
    onMeasuredPowerChange: (String) -> Unit,
    txPowerLevelText: String,
    onTxPowerLevelChange: (String) -> Unit,
    statusText: String,
    isAdvertising: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "$beaconIdText BLE Beacon Simulator",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "Beacon ID: $beaconIdText",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = beaconIdText,
            onValueChange = onBeaconIdChange,
            label = { Text("Beacon ID") },
            singleLine = true,
            enabled = !isAdvertising,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = uuidText,
            onValueChange = onUuidChange,
            label = { Text("UUID") },
            singleLine = true,
            enabled = !isAdvertising,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            BeaconNumberField(
                value = majorText,
                onValueChange = onMajorChange,
                label = "Major",
                enabled = !isAdvertising,
                modifier = Modifier.weight(1f)
            )
            BeaconNumberField(
                value = minorText,
                onValueChange = onMinorChange,
                label = "Minor",
                enabled = !isAdvertising,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            BeaconNumberField(
                value = measuredPowerText,
                onValueChange = onMeasuredPowerChange,
                label = "Measured power",
                enabled = !isAdvertising,
                modifier = Modifier.weight(1f)
            )
            BeaconNumberField(
                value = txPowerLevelText,
                onValueChange = onTxPowerLevelChange,
                label = "TX level 0-3",
                enabled = !isAdvertising,
                modifier = Modifier.weight(1f)
            )
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isAdvertising) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (isAdvertising) {
            TextButton(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Stop advertising")
            }
        } else {
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start advertising")
            }
        }

        Text(
            text = "TX level: 0 ultra low, 1 low, 2 medium, 3 high.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BeaconNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            onValueChange(
                input.filterIndexed { index, char ->
                    char.isDigit() || (char == '-' && index == 0)
                }
            )
        },
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
private fun BeaconSimulatorPreview() {
    BLE_SimulatorTheme {
        BeaconSimulatorScreen(
            beaconIdText = "SamA15",
            onBeaconIdChange = {},
            uuidText = "f7826da6-4fa2-4e98-8024-bc5b71e0893e",
            onUuidChange = {},
            majorText = "20641",
            onMajorChange = {},
            minorText = "50361",
            onMinorChange = {},
            measuredPowerText = "-80",
            onMeasuredPowerChange = {},
            txPowerLevelText = "3",
            onTxPowerLevelChange = {},
            statusText = "Ready to advertise.",
            isAdvertising = false,
            onStart = {},
            onStop = {}
        )
    }
}
