package com.example.connectiontest;

import static com.example.connectiontest.JsonOps.JsonOps.beaconLocation;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.util.SparseArray;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.example.connectiontest.BeaconManager.BeaconManager;
import com.example.connectiontest.BeaconManager.BeaconLocation;
import com.example.connectiontest.BeaconManager.bleBeacon;
import com.example.connectiontest.JsonOps.JsonOps;
import com.example.connectiontest.Positioning.PositioningEngine;
import com.example.connectiontest.SensorManager.SensorManager;
import com.example.connectiontest.TrilaterationUtils.CoordinateUtils;
import com.example.connectiontest.initApp.InitApp;
import com.kontakt.sdk.android.ble.manager.ProximityManager;
import com.kontakt.sdk.android.ble.manager.listeners.IBeaconListener;
import com.kontakt.sdk.android.ble.manager.listeners.SpaceListener;
import com.kontakt.sdk.android.ble.manager.listeners.simple.SimpleIBeaconListener;
import com.kontakt.sdk.android.common.profile.IEddystoneNamespace;
import com.kontakt.sdk.android.common.profile.IBeaconDevice;
import com.kontakt.sdk.android.common.profile.IBeaconRegion;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Main Android entry point and runtime coordinator.
 *
 * This class wires together permissions, Kontakt BLE scanning, calibration loading,
 * BLE positioning, sensor/Kalman fusion, dashboard rendering, and session logging.
 * Keep heavy math in helper classes; MainActivity should decide when each subsystem
 * runs and which result is shown/logged.
 */
public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int REQUEST_ENABLE_LOCATION = 3;
    private static final String TAG = "MainActivity";
    private static final long NO_BEACON_RESTART_MS = 15000L;
    private static final long BEACON_STALE_MS = 12000L;
    private static final long MAX_SOLVE_BEACON_AGE_MS = 4500L;
    private static final long SENSOR_RENDER_INTERVAL_MS = 1000L;
    private static final boolean ENABLE_RAW_BLE_DEBUG_SCAN = true;
    private static final long RAW_BLE_DEBUG_LOG_INTERVAL_MS = 3000L;
    private static final int IBEACON_MANUFACTURER_ID = 0x004C;

    private ProximityManager proximityManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner rawBleScanner;
    private ScanCallback rawBleScanCallback;
    private boolean rawBleDebugScanRunning = false;
    private final Map<String, Long> rawBleDebugLastLogByDevice = new HashMap<>();
    private TrackingDashboardView dashboardView;
    private boolean isBleInitialized = false;
    private boolean isBluetoothReceiverRegistered = false;
    private final Handler watchdogHandler = new Handler(Looper.getMainLooper());
    private long lastBeaconCallbackMs = 0L;
    private long lastBleRenderMs = 0L;

    private Handler backgroundHandler;
    private Handler uiHandler;
    private HandlerThread backgroundThread;
    private boolean scanStoppedForLifecycle = false;

    private int[][] floorGrid;
    private SensorManager sensorFusionManager;
    private UserPositionHistory userPositionHistory;
    private PositioningEngine positioningEngine;
    private final List<String> lastMissingBeaconIds = new ArrayList<>();
    private int lastLiveCount = 0;
    private int lastFilteredCount = 0;
    private String lastMode = "INIT";
    private String lastReason = "APP_START";
    private double lastBleXMeters = Double.NaN;
    private double lastBleYMeters = Double.NaN;
    private double lastRenderXMeters = Double.NaN;
    private double lastRenderYMeters = Double.NaN;
    private double lastManualAnchorXMeters = Double.NaN;
    private double lastManualAnchorYMeters = Double.NaN;
    private boolean adaptiveTuningEnabled = false;
    private double lastBleQualityScore = Double.NaN;
    private double lastPdrQualityScore = Double.NaN;
    private double lastRssiNoiseDb = Double.NaN;
    private double lastBleMeasurementStdMeters = Double.NaN;
    private double lastBleResidualRmsMeters = Double.NaN;
    private double lastBleResidualMaxMeters = Double.NaN;
    private String lastMotionState = "FIXED";
    private boolean recordingEnabled = true;
    private String trialLabel = "UNLABELED";

    // Debug gates for a ~5x5m room
    private static final double MIN_DIST_M = 0.01; // Allows starting very close to a beacon in the small test room
    private static final double DIST_MARGIN_M = 1.25; // extra tolerance for noisy 3-beacon RSSI solves
    private static final double MAX_JUMP_M = 2.5;
    private static final int RSSI_AVERAGE_WINDOW_SIZE = 3;
    private static final double MANUAL_HEADING_CALIBRATION_MIN_DELTA_M = 0.75;
    private static final boolean ENABLE_MANUAL_HEADING_CALIBRATION = false;
    private static final boolean ENABLE_BLE_HEADING_AUTO_ALIGN = false;
    private static final double STATIC_ANCHOR_BLE_REJECT_M = 0.75;

    public enum PositioningMode {
        BLE_ONLY,
        PDR_ONLY,
        FUSION
    }

    private PositioningMode activePositioningMode = PositioningMode.FUSION;

    /**
     * Keeps the dashboard moving when BLE has no fresh accepted fix. In this mode
     * SensorManager/Kalman is the source of truth for render position.
     */
    private final Runnable sensorRenderRunnable = new Runnable() {
        @Override
        public void run() {
            refreshSensorOnlyPosition();
            watchdogHandler.postDelayed(this, SENSOR_RENDER_INTERVAL_MS);
        }
    };

    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                return;
            }

            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            if (state == BluetoothAdapter.STATE_ON) {
                Log.d(TAG, "Bluetooth turned ON.");
                maybeStartAfterPrereqs();
            } else if (state == BluetoothAdapter.STATE_OFF) {
                Log.d(TAG, "Bluetooth turned OFF.");
            }
        }
    };
    private final Runnable scanWatchdogRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isBleInitialized || proximityManager == null) {
                watchdogHandler.postDelayed(this, NO_BEACON_RESTART_MS);
                return;
            }
            long now = System.currentTimeMillis();
            long ageMs = now - lastBeaconCallbackMs;
            if (lastBeaconCallbackMs == 0L || ageMs > NO_BEACON_RESTART_MS) {
                Log.d(TAG, "BLE_WATCHDOG: no beacon callbacks for " + ageMs + "ms, restarting scan.");
                try {
                    proximityManager.stopScanning();
                } catch (Exception ignored) {}
                try {
                    proximityManager.startScanning();
                } catch (Exception e) {
                    Log.e(TAG, "BLE_WATCHDOG: startScanning failed", e);
                }
            }
            watchdogHandler.postDelayed(this, NO_BEACON_RESTART_MS);
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        dashboardView = new TrackingDashboardView(this);
        dashboardView.setOnMapTapListener(this::setManualPosition);
        setContentView(dashboardView);
        dashboardView.setOnPositioningModeChangeListener(this::setPositioningMode);
        dashboardView.setOnAdaptiveTuningChangeListener(this::setAdaptiveTuningEnabled);
        dashboardView.setOnRecordingChangeListener(this::setRecordingEnabled);
        dashboardView.setOnTrialLabelChangeListener(this::setTrialLabel);
        dashboardView.setOnExportLogsListener(this::exportCurrentLogs);
        dashboardView.updatePositioningMode(activePositioningMode.name());
        dashboardView.updateAdaptiveTuning(adaptiveTuningEnabled);
        dashboardView.updateRecordingState(recordingEnabled);
        dashboardView.updateTrialLabel(trialLabel);

        userPositionHistory = new UserPositionHistory(this);
        userPositionHistory.setRecordingEnabled(recordingEnabled);
        userPositionHistory.setTrialLabel(trialLabel);
        userPositionHistory.configureAlgorithmContext(
                MIN_DIST_M,
                DIST_MARGIN_M,
                MAX_JUMP_M,
                BEACON_STALE_MS,
                MAX_SOLVE_BEACON_AGE_MS,
                SENSOR_RENDER_INTERVAL_MS,
                RSSI_AVERAGE_WINDOW_SIZE,
                MANUAL_HEADING_CALIBRATION_MIN_DELTA_M,
                ENABLE_MANUAL_HEADING_CALIBRATION,
                ENABLE_BLE_HEADING_AUTO_ALIGN
        );
        positioningEngine = new PositioningEngine(new PositioningEngine.Config(
                MIN_DIST_M,
                DIST_MARGIN_M,
                MAX_JUMP_M,
                RSSI_AVERAGE_WINDOW_SIZE
        ));
        Log.d(TAG, "onCreate started.");

        sensorFusionManager = new SensorManager(this);
        sensorFusionManager.setAdaptiveTuningEnabled(adaptiveTuningEnabled);
        sensorFusionManager.setBleBlendAlpha(0.65);
        sensorFusionManager.configurePdr(
                JsonOps.stepLengthMeters,
                JsonOps.minStepLengthMeters,
                JsonOps.maxStepLengthMeters,
                JsonOps.minStepIntervalMs,
                JsonOps.customStepPeakThreshold,
                JsonOps.customStepResetThreshold,
                JsonOps.customStepMaxGyro,
                JsonOps.customStepMaxAbsPitchDegrees,
                JsonOps.customStepMaxAbsRollDegrees
        );
        sensorFusionManager.setHeadingOffsetDegrees(JsonOps.headingOffsetDegrees);
        Log.d(TAG, "FUSION: configured initial defaults; JSON calibration is applied after current_test.json loads");
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        checkPermissions();
    }

    private void initAfterPermissions() {
        if (isBleInitialized) {
            return;
        }
        InitApp.InitResult result = InitApp.initApp(this, createIBeaconListener(), createSpaceListener());
        backgroundHandler = result.backgroundHandler;
        uiHandler = result.uiHandler;
        backgroundThread = result.backgroundThread;
        proximityManager = result.proximityManager;
        isBleInitialized = true;
        scanStoppedForLifecycle = false;
        lastBeaconCallbackMs = 0L;
        watchdogHandler.removeCallbacks(scanWatchdogRunnable);
        if (proximityManager != null) {
            watchdogHandler.postDelayed(scanWatchdogRunnable, NO_BEACON_RESTART_MS);
        } else {
            lastMode = "INIT";
            lastReason = "KONTAKT_API_KEY_MISSING";
            Log.e(TAG, "BLE scan disabled: KONTAKT_API_KEY is empty. Add KONTAKT_API_KEY to local.properties and rebuild.");
        }

        displayJsonData();
    }

    private void checkPermissions() {
        boolean activityRecognition = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED;
        boolean fineLoc = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean btScan = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
        boolean btConnect = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;

        Log.d(TAG, "Permissions -> FINE_LOCATION: " + fineLoc + ", SCAN: " + btScan + ", CONNECT: " + btConnect + ", ACTIVITY: " + activityRecognition);

        List<String> missing = new ArrayList<>();
        if (!fineLoc) missing.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!btScan) missing.add(Manifest.permission.BLUETOOTH_SCAN);
            if (!btConnect) missing.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (!activityRecognition && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            missing.add(Manifest.permission.ACTIVITY_RECOGNITION);
        }

        if (!missing.isEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toArray(new String[0]), PERMISSION_REQUEST_CODE);
            return;
        }

        Log.d(TAG, "All requested runtime permissions already granted");
        maybeStartAfterPrereqs();
    }

    private boolean hasCoreBlePermissions() {
        boolean fineLoc = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean btScan = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
        boolean btConnect = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        return fineLoc && btScan && btConnect;
    }

    private boolean hasActivityRecognitionPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED;
    }

    private void maybeStartAfterPrereqs() {
        if (!hasCoreBlePermissions()) {
            Log.w(TAG, "Core BLE permissions missing. Waiting.");
            return;
        }

        if (bluetoothAdapter == null) {
            Log.e(TAG, "Device has no Bluetooth adapter.");
            return;
        }

        boolean bluetoothEnabled;
        try {
            bluetoothEnabled = bluetoothAdapter.isEnabled();
        } catch (SecurityException e) {
            Log.w(TAG, "Cannot query Bluetooth adapter state due to permission restrictions.", e);
            return;
        }

        if (!bluetoothEnabled) {
            Log.w(TAG, "Bluetooth is OFF. Requesting user to enable it.");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return;
        }

        if (!isLocationServiceEnabled()) {
            Log.w(TAG, "Location services are OFF. Requesting user to enable location.");
            Intent locationIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivityForResult(locationIntent, REQUEST_ENABLE_LOCATION);
            return;
        }

        if (!hasActivityRecognitionPermission()) {
            Log.w(TAG, "ACTIVITY_RECOGNITION denied. Step-based fusion will be limited.");
        }

        startRawBleDebugScan();

        if (!isBleInitialized) {
            initAfterPermissions();
            return;
        }

        if (proximityManager != null) {
            if (lastBeaconCallbackMs == 0L && !scanStoppedForLifecycle) {
                Log.d(TAG, "BLE startup in progress; waiting for initial scan callback before manual restart.");
                return;
            }
            try {
                proximityManager.startScanning();
                scanStoppedForLifecycle = false;
                Log.d(TAG, "Requested scan restart after prerequisites became valid.");
                watchdogHandler.removeCallbacks(scanWatchdogRunnable);
                watchdogHandler.postDelayed(scanWatchdogRunnable, NO_BEACON_RESTART_MS);
            } catch (Exception e) {
                Log.e(TAG, "Failed to restart scanning", e);
            }
        }
    }

    private void startRawBleDebugScan() {
        if (!ENABLE_RAW_BLE_DEBUG_SCAN || rawBleDebugScanRunning) {
            return;
        }
        if (!hasCoreBlePermissions() || bluetoothAdapter == null) {
            return;
        }
        try {
            rawBleScanner = bluetoothAdapter.getBluetoothLeScanner();
        } catch (SecurityException e) {
            Log.w(TAG, "RAW_BLE: cannot access BluetoothLeScanner due to permissions", e);
            return;
        }
        if (rawBleScanner == null) {
            Log.w(TAG, "RAW_BLE: BluetoothLeScanner unavailable");
            return;
        }

        rawBleScanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                logRawBleScanResult(result);
            }

            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                if (results == null) {
                    return;
                }
                for (ScanResult result : results) {
                    logRawBleScanResult(result);
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                rawBleDebugScanRunning = false;
                Log.w(TAG, "RAW_BLE: debug scan failed, errorCode=" + errorCode);
            }
        };

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        try {
            rawBleScanner.startScan(Collections.<ScanFilter>emptyList(), settings, rawBleScanCallback);
            rawBleDebugScanRunning = true;
            Log.i(TAG, "RAW_BLE: debug scan started; watch for RAW_BLE iBeacon lines");
        } catch (SecurityException e) {
            Log.w(TAG, "RAW_BLE: debug scan blocked by permissions", e);
        } catch (Exception e) {
            Log.w(TAG, "RAW_BLE: debug scan could not start", e);
        }
    }

    private void stopRawBleDebugScan() {
        if (!rawBleDebugScanRunning || rawBleScanner == null || rawBleScanCallback == null) {
            rawBleDebugScanRunning = false;
            return;
        }
        try {
            rawBleScanner.stopScan(rawBleScanCallback);
        } catch (SecurityException e) {
            Log.w(TAG, "RAW_BLE: debug scan stop blocked by permissions", e);
        } catch (Exception e) {
            Log.w(TAG, "RAW_BLE: debug scan stop failed", e);
        }
        rawBleDebugScanRunning = false;
    }

    private void logRawBleScanResult(ScanResult result) {
        if (result == null) {
            return;
        }
        ScanRecord record = result.getScanRecord();
        if (record == null) {
            return;
        }
        SparseArray<byte[]> manufacturerData = record.getManufacturerSpecificData();
        if (manufacturerData == null || manufacturerData.size() == 0) {
            return;
        }

        String address = safeDeviceAddress(result);
        long nowMs = System.currentTimeMillis();
        Long lastLogMs = rawBleDebugLastLogByDevice.get(address);
        boolean shouldLogGeneric = lastLogMs == null || nowMs - lastLogMs > RAW_BLE_DEBUG_LOG_INTERVAL_MS;

        for (int i = 0; i < manufacturerData.size(); i++) {
            int manufacturerId = manufacturerData.keyAt(i);
            byte[] data = manufacturerData.valueAt(i);
            if (data == null || data.length == 0) {
                continue;
            }

            IBeaconPacket packet = parseIBeaconPacket(manufacturerId, data);
            if (packet != null) {
                boolean acceptedByFallback = BeaconManager.checkAndUpsertRawIBeacon(
                        address,
                        packet.uuid,
                        packet.major,
                        packet.minor,
                        result.getRssi(),
                        beaconLocation
                );
                rawBleDebugLastLogByDevice.put(address, nowMs);
                Log.i(TAG, "RAW_BLE: iBeacon address=" + address
                        + " rssi=" + result.getRssi()
                        + " uuid=" + packet.uuid
                        + " major=" + packet.major
                        + " minor=" + packet.minor
                        + " measuredPower=" + packet.measuredPower
                        + " key=" + BeaconLocation.buildCompositeKey(packet.uuid, packet.major, packet.minor)
                        + " fallbackAccepted=" + acceptedByFallback);
                continue;
            }

            if (shouldLogGeneric) {
                rawBleDebugLastLogByDevice.put(address, nowMs);
                Log.d(TAG, "RAW_BLE: manufacturer address=" + address
                        + " rssi=" + result.getRssi()
                        + " manufacturerId=0x" + String.format(Locale.US, "%04X", manufacturerId)
                        + " bytes=" + bytesToHex(data));
            }
        }
    }

    private IBeaconPacket parseIBeaconPacket(int manufacturerId, byte[] data) {
        if (manufacturerId != IBEACON_MANUFACTURER_ID || data.length < 23) {
            return null;
        }
        if ((data[0] & 0xFF) != 0x02 || (data[1] & 0xFF) != 0x15) {
            return null;
        }

        String hex = bytesToHex(data);
        String uuid = String.format(Locale.US,
                "%s-%s-%s-%s-%s",
                hex.substring(4, 12),
                hex.substring(12, 16),
                hex.substring(16, 20),
                hex.substring(20, 24),
                hex.substring(24, 36)
        ).toLowerCase(Locale.US);
        int major = ((data[18] & 0xFF) << 8) | (data[19] & 0xFF);
        int minor = ((data[20] & 0xFF) << 8) | (data[21] & 0xFF);
        int measuredPower = data[22];
        return new IBeaconPacket(uuid, major, minor, measuredPower);
    }

    private String safeDeviceAddress(ScanResult result) {
        BluetoothDevice device = result.getDevice();
        if (device == null) {
            return "unknown";
        }
        try {
            String address = device.getAddress();
            return address == null ? "unknown" : address;
        } catch (SecurityException e) {
            return "permission-blocked";
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format(Locale.US, "%02X", value & 0xFF));
        }
        return builder.toString();
    }

    private static class IBeaconPacket {
        final String uuid;
        final int major;
        final int minor;
        final int measuredPower;

        IBeaconPacket(String uuid, int major, int minor, int measuredPower) {
            this.uuid = uuid;
            this.major = major;
            this.minor = minor;
            this.measuredPower = measuredPower;
        }
    }

    private boolean isLocationServiceEnabled() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return locationManager.isLocationEnabled();
        }
        boolean gps = false;
        boolean network = false;
        try {
            gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception ignored) {}
        try {
            network = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception ignored) {}
        return gps || network;
    }

    private void displayJsonData() {
        floorGrid = JsonOps.JsonTo2DArray(this, "CalibrationTests/current_test.json");
        // Load the active room/beacon calibration from bundled assets.

        if (floorGrid != null) {
            applyPositioningConfigFromJson();
            userPositionHistory.configureExperimentContext(
                    "CalibrationTests/current_test.json",
                    floorGrid,
                    beaconLocation
            );
            // Initial dashboard draw with no solved position yet.
            renderDashboard();
        } else {
            Log.w(TAG, "Floor grid is null, cannot display JSON data.");
        }
    }

    /**
     * Single UI update path:
     * pushes current telemetry + map state to the dashboard renderer.
     */
    private void renderDashboard() {
        if (backgroundHandler == null || uiHandler == null || dashboardView == null || floorGrid == null) {
            return;
        }
        updateAdaptiveTelemetryForLog();
        recordRenderedPosition();
        dashboardView.updateTelemetry(
                lastMode,
                lastReason,
                lastLiveCount,
                lastFilteredCount,
                new ArrayList<>(lastMissingBeaconIds),
                lastRenderXMeters,
                lastRenderYMeters,
                lastBleXMeters,
                lastBleYMeters,
                userPositionHistory == null ? Double.NaN : userPositionHistory.getCurrentGroundTruthXMeters(),
                userPositionHistory == null ? Double.NaN : userPositionHistory.getCurrentGroundTruthYMeters(),
                userPositionHistory == null ? 0 : userPositionHistory.size(),
                adaptiveTuningEnabled,
                lastBleQualityScore,
                lastPdrQualityScore,
                lastBleMeasurementStdMeters,
                lastMotionState,
                recordingEnabled,
                trialLabel
        );

        dashboardView.updateMap(
                floorGrid,
                beaconLocation,
                lastRenderXMeters,
                lastRenderYMeters,
                lastBleXMeters,
                lastBleYMeters,
                userPositionHistory == null ? Double.NaN : userPositionHistory.getCurrentGroundTruthXMeters(),
                userPositionHistory == null ? Double.NaN : userPositionHistory.getCurrentGroundTruthYMeters()
        );
    }

    private void recordRenderedPosition() {
        if (userPositionHistory == null) {
            return;
        }
        if ("BLE_REJECTED".equals(lastMode)) {
            return;
        }

        boolean added = userPositionHistory.addPosition(
                lastRenderXMeters,
                lastRenderYMeters,
                lastBleXMeters,
                lastBleYMeters,
                lastMode,
                lastReason,
                lastLiveCount,
                lastFilteredCount,
                backgroundHandler
        );
        if (added) {
            Log.d(TAG, "POSITION_OUTPUT: samples="
                    + userPositionHistory.size()
                    + " csv=" + userPositionHistory.getOutputFile()
                    + " json=" + userPositionHistory.getJsonOutputFile());
        }
    }

    private void updateAdaptiveTelemetryForLog() {
        if (sensorFusionManager != null) {
            lastPdrQualityScore = sensorFusionManager.getPdrQualityScore();
            lastBleMeasurementStdMeters = sensorFusionManager.getLastBleMeasurementStdMeters();
            lastMotionState = sensorFusionManager.getMotionStateLabel();
        }
        if (userPositionHistory != null) {
            userPositionHistory.updateAdaptiveTelemetry(
                    adaptiveTuningEnabled,
                    lastBleQualityScore,
                    lastPdrQualityScore,
                    lastRssiNoiseDb,
                    lastBleMeasurementStdMeters,
                    lastBleResidualRmsMeters,
                    lastBleResidualMaxMeters,
                    lastMotionState
            );
        }
    }

    private void applyPositioningConfigFromJson() {
        sensorFusionManager.configurePdr(
                JsonOps.stepLengthMeters,
                JsonOps.minStepLengthMeters,
                JsonOps.maxStepLengthMeters,
                JsonOps.minStepIntervalMs,
                JsonOps.customStepPeakThreshold,
                JsonOps.customStepResetThreshold,
                JsonOps.customStepMaxGyro,
                JsonOps.customStepMaxAbsPitchDegrees,
                JsonOps.customStepMaxAbsRollDegrees
        );
        sensorFusionManager.setHeadingOffsetDegrees(JsonOps.headingOffsetDegrees);
        positioningEngine = new PositioningEngine(new PositioningEngine.Config(
                JsonOps.bleMinDistanceMeters,
                JsonOps.bleDistanceMarginMeters,
                JsonOps.bleBoundaryClampMarginMeters,
                JsonOps.bleMaxJumpMeters,
                JsonOps.rssiAverageWindowSize,
                JsonOps.bleResidualRejectMeters
        ));
        if (userPositionHistory != null) {
            userPositionHistory.configureAlgorithmContext(
                    JsonOps.bleMinDistanceMeters,
                    JsonOps.bleDistanceMarginMeters,
                    JsonOps.bleMaxJumpMeters,
                    BEACON_STALE_MS,
                    MAX_SOLVE_BEACON_AGE_MS,
                    SENSOR_RENDER_INTERVAL_MS,
                    JsonOps.rssiAverageWindowSize,
                    MANUAL_HEADING_CALIBRATION_MIN_DELTA_M,
                    ENABLE_MANUAL_HEADING_CALIBRATION,
                    ENABLE_BLE_HEADING_AUTO_ALIGN
            );
        }
        Log.d(TAG, "CONFIG: applied current_test positioning config mode=" + activePositioningMode
                + " headingOffsetDegrees=" + JsonOps.headingOffsetDegrees
                + " stepLengthMeters=" + JsonOps.stepLengthMeters
                + " minStepIntervalMs=" + JsonOps.minStepIntervalMs
                + " rssiAverageWindowSize=" + JsonOps.rssiAverageWindowSize);
    }

    private void refreshSensorOnlyPosition() {
        if (sensorFusionManager == null || floorGrid == null) {
            return;
        }
        if (activePositioningMode == PositioningMode.BLE_ONLY) {
            return;
        }
        double[] fused = sensorFusionManager.getFusedPositionMeters();
        if (fused == null) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        if (nowMs - lastBleRenderMs < SENSOR_RENDER_INTERVAL_MS) {
            return;
        }
        if (!Double.isFinite(lastRenderXMeters) || !Double.isFinite(lastRenderYMeters) ||
                Math.hypot(fused[0] - lastRenderXMeters, fused[1] - lastRenderYMeters) >= 0.03) {
            lastRenderXMeters = fused[0];
            lastRenderYMeters = fused[1];
            lastBleXMeters = Double.NaN;
            lastBleYMeters = Double.NaN;
            lastMode = activePositioningMode == PositioningMode.PDR_ONLY ? "PDR_ONLY" : "FUSION";
            lastReason = "DEAD_RECKONING";
            discardOneShotReference("SENSOR_MOVEMENT");
            renderDashboard();
        }
    }

    public List<UserPositionSample> getUserPositionHistorySnapshot() {
        if (userPositionHistory == null) {
            return Collections.emptyList();
        }
        return userPositionHistory.getSnapshot();
    }

    private void setPositioningMode(String modeName) {
        PositioningMode previousMode = activePositioningMode;
        PositioningMode nextMode;
        String normalizedMode = modeName == null ? "" : modeName.trim().toUpperCase(Locale.US);
        if ("BLE".equals(normalizedMode)) {
            normalizedMode = PositioningMode.BLE_ONLY.name();
        } else if ("PDR".equals(normalizedMode)) {
            normalizedMode = PositioningMode.PDR_ONLY.name();
        } else if ("FUSION".equals(normalizedMode)) {
            normalizedMode = PositioningMode.FUSION.name();
        }
        try {
            nextMode = PositioningMode.valueOf(normalizedMode);
        } catch (Exception ignored) {
            nextMode = PositioningMode.FUSION;
        }
        activePositioningMode = nextMode;
        lastReason = "MODE_SELECTED";
        if (activePositioningMode == PositioningMode.PDR_ONLY
                && (sensorFusionManager == null || !sensorFusionManager.hasFusedPosition())) {
            lastRenderXMeters = Double.NaN;
            lastRenderYMeters = Double.NaN;
            lastMode = "PDR_ONLY";
            lastReason = "PDR_WAITING_FOR_ANCHOR";
            Log.d(TAG, "CONFIG: PDR_ONLY selected without an initialized origin; tap a known map point before PDR tracking.");
        }
        if (previousMode == PositioningMode.BLE_ONLY
                && activePositioningMode == PositioningMode.FUSION
                && Double.isFinite(lastRenderXMeters)
                && Double.isFinite(lastRenderYMeters)) {
            if (sensorFusionManager != null) {
                sensorFusionManager.setManualPosition(lastRenderXMeters, lastRenderYMeters);
            }
            if (positioningEngine != null) {
                positioningEngine.resetLastAcceptedPosition(lastRenderXMeters, lastRenderYMeters);
            }
            Log.d(TAG, String.format(Locale.US,
                    "CONFIG: fusion resume anchor=(%.2f, %.2f)",
                    lastRenderXMeters,
                    lastRenderYMeters
            ));
        }
        Log.d(TAG, "CONFIG: positioningMode=" + activePositioningMode);
        if (dashboardView != null) {
            dashboardView.updatePositioningMode(activePositioningMode.name());
        }
        renderDashboard();
    }

    private void setAdaptiveTuningEnabled(boolean enabled) {
        adaptiveTuningEnabled = enabled;
        if (sensorFusionManager != null) {
            sensorFusionManager.setAdaptiveTuningEnabled(enabled);
        }
        if (!enabled) {
            lastBleQualityScore = Double.NaN;
            lastPdrQualityScore = Double.NaN;
            lastRssiNoiseDb = Double.NaN;
            lastBleResidualRmsMeters = Double.NaN;
            lastBleResidualMaxMeters = Double.NaN;
            lastMotionState = "FIXED";
        }
        updateAdaptiveTelemetryForLog();
        if (dashboardView != null) {
            dashboardView.updateAdaptiveTuning(enabled);
        }
        Log.d(TAG, "CONFIG: adaptiveTuningEnabled=" + enabled);
        renderDashboard();
    }

    private void setRecordingEnabled(boolean enabled) {
        recordingEnabled = enabled;
        if (userPositionHistory != null) {
            userPositionHistory.setRecordingEnabled(enabled);
        }
        if (dashboardView != null) {
            dashboardView.updateRecordingState(enabled);
            dashboardView.updateAction(enabled ? "Recording started" : "Recording paused");
        }
        Log.d(TAG, "CONFIG: recordingEnabled=" + enabled);
        renderDashboard();
    }

    private void setTrialLabel(String label) {
        trialLabel = label == null || label.trim().isEmpty()
                ? "UNLABELED"
                : label.trim().toUpperCase(Locale.US);
        if (userPositionHistory != null) {
            userPositionHistory.setTrialLabel(trialLabel);
        }
        if (dashboardView != null) {
            dashboardView.updateTrialLabel(trialLabel);
            dashboardView.updateAction("Label " + trialLabel);
        }
        Log.d(TAG, "CONFIG: trialLabel=" + trialLabel);
        renderDashboard();
    }

    private void exportCurrentLogs() {
        if (userPositionHistory == null || userPositionHistory.getJsonOutputFile() == null) {
            Toast.makeText(this, "No log file available yet", Toast.LENGTH_SHORT).show();
            if (dashboardView != null) {
                dashboardView.updateAction("No logs available");
            }
            return;
        }
        ArrayList<Uri> uris = new ArrayList<>();
        addShareUri(uris, userPositionHistory.getJsonOutputFile());
        addShareUri(uris, userPositionHistory.getOutputFile());
        if (uris.isEmpty()) {
            Toast.makeText(this, "No log file available yet", Toast.LENGTH_SHORT).show();
            if (dashboardView != null) {
                dashboardView.updateAction("No logs available");
            }
            return;
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        shareIntent.setType("text/*");
        shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (dashboardView != null) {
            dashboardView.updateAction("Exporting logs");
        }
        startActivity(Intent.createChooser(shareIntent, "Export thesis logs"));
    }

    private void addShareUri(ArrayList<Uri> uris, File file) {
        if (file == null || !file.exists()) {
            return;
        }
        Uri uri = FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",
                file
        );
        uris.add(uri);
    }

    private void setManualPosition(double xMeters, double yMeters) {
        if (sensorFusionManager == null) {
            return;
        }
        maybeCalibrateHeadingFromManualAnchor(xMeters, yMeters);
        // Map taps are tracker anchors. They reset Kalman exactly, but their
        // ground truth is not evaluation evidence because the state is forced to it.
        sensorFusionManager.setManualPosition(xMeters, yMeters);
        if (positioningEngine != null) {
            positioningEngine.resetLastAcceptedPosition(xMeters, yMeters);
        }
        lastRenderXMeters = xMeters;
        lastRenderYMeters = yMeters;
        lastBleXMeters = Double.NaN;
        lastBleYMeters = Double.NaN;
        lastMode = "ANCHOR";
        lastReason = "MAP_TAP";
        if (userPositionHistory != null) {
            userPositionHistory.setGroundTruthMeters(xMeters, yMeters);
            userPositionHistory.addPositionWithTruth(
                    xMeters,
                    yMeters,
                    Double.NaN,
                    Double.NaN,
                    xMeters,
                    yMeters,
                    UserPositionSample.TRUTH_MANUAL_ANCHOR,
                    false,
                    lastMode,
                    lastReason,
                    lastLiveCount,
                    lastFilteredCount,
                    backgroundHandler
            );
        }
        if (dashboardView != null) {
            dashboardView.updateAction("Anchor set");
        }
        Log.d(TAG, String.format(Locale.US,
                "ANCHOR: source=MAP_TAP col,row=(%.2f, %.2f) meters(x,y)=(%.2f, %.2f) previousAnchor=(%.2f, %.2f)",
                CoordinateUtils.xMetersToGridCol(xMeters),
                CoordinateUtils.yMetersToGridRow(yMeters),
                xMeters,
                yMeters,
                lastManualAnchorXMeters,
                lastManualAnchorYMeters
        ));
        renderDashboard();
        lastManualAnchorXMeters = xMeters;
        lastManualAnchorYMeters = yMeters;
    }

    private void maybeCalibrateHeadingFromManualAnchor(double xMeters, double yMeters) {
        if (!ENABLE_MANUAL_HEADING_CALIBRATION) {
            return;
        }
        if (!Double.isFinite(lastManualAnchorXMeters) || !Double.isFinite(lastManualAnchorYMeters)) {
            return;
        }

        double deltaX = xMeters - lastManualAnchorXMeters;
        double deltaY = yMeters - lastManualAnchorYMeters;
        double distance = Math.hypot(deltaX, deltaY);
        if (distance < MANUAL_HEADING_CALIBRATION_MIN_DELTA_M) {
            return;
        }

        boolean aligned = sensorFusionManager.alignHeadingToMotion(deltaX, deltaY);
        Log.d(TAG, String.format(Locale.US,
                "FUSION: manual heading calibration delta=(%.2f, %.2f) distance=%.2f aligned=%s",
                deltaX,
                deltaY,
                distance,
                aligned
        ));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (permissions.length == 0 || grantResults.length == 0 || permissions.length != grantResults.length) {
                Log.w(TAG, "Permission callback received empty or malformed results.");
                if (hasCoreBlePermissions()) {
                    maybeStartAfterPrereqs();
                } else {
                    Log.w(TAG, "Core BLE permissions still missing after malformed callback.");
                }
                return;
            }

            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted && hasCoreBlePermissions()) {
                Log.d(TAG, "All requested permissions granted in callback");
                maybeStartAfterPrereqs();
            } else {
                if (hasCoreBlePermissions()) {
                    Log.w(TAG, "Optional permission denied (likely ACTIVITY_RECOGNITION). Continuing with BLE.");
                    maybeStartAfterPrereqs();
                } else {
                    Log.w(TAG, "Core BLE permissions denied. Cannot initialize BLE scanning.");
                }
            }
        }
    }

    private IBeaconListener createIBeaconListener() {
        return new SimpleIBeaconListener() {
            @Override
            public void onIBeaconDiscovered(IBeaconDevice ibeacon, IBeaconRegion region) {
                lastBeaconCallbackMs = System.currentTimeMillis();
                BeaconManager.checkAndUpsertBeacon(ibeacon, beaconLocation);
            }

            @Override
            public void onIBeaconsUpdated(List<IBeaconDevice> list, IBeaconRegion region) {
                long nowMs = System.currentTimeMillis();
                lastBeaconCallbackMs = nowMs;
                List<IBeaconDevice> safeList = list == null ? Collections.emptyList() : list;
                Log.d(TAG, "BLE_SCAN: onIBeaconsUpdated listSize=" + safeList.size());
                for (IBeaconDevice ibeacon : safeList) {
                    BeaconManager.checkAndUpsertBeacon(ibeacon, beaconLocation);
                }
                int pruned = BeaconManager.pruneStaleBeacons(nowMs, BEACON_STALE_MS);
                if (pruned > 0) {
                    Log.d(TAG, "BLE_SCAN: pruned stale beacons count=" + pruned + " ttlMs=" + BEACON_STALE_MS);
                }

                List<bleBeacon> currentLiveBeacons = BeaconManager.getLiveBeacons();
                List<bleBeacon> solveBeacons = selectSolveBeacons(currentLiveBeacons, safeList, nowMs);
                logGroundTruthBeaconDiagnostics(solveBeacons);

                // PositioningEngine is the only gate for raw BLE fixes. Only accepted
                // results are allowed into SensorManager/Kalman fusion.
                PositioningEngine.Result result = positioningEngine.process(
                        currentLiveBeacons,
                        solveBeacons,
                        beaconLocation,
                        floorGrid,
                        adaptiveTuningEnabled
                );
                lastMissingBeaconIds.clear();
                lastMissingBeaconIds.addAll(result.missingBeaconIds);
                lastLiveCount = result.liveCount;
                lastFilteredCount = result.filteredCount;
                lastMode = result.mode;
                lastReason = result.reason;
                lastBleXMeters = result.bleXMeters;
                lastBleYMeters = result.bleYMeters;
                lastBleQualityScore = result.bleQualityScore;
                lastRssiNoiseDb = result.rssiNoiseDb;
                lastBleResidualRmsMeters = result.residualRmsMeters;
                lastBleResidualMaxMeters = result.residualMaxMeters;

                if (result.needsFusionFallback) {
                    if (activePositioningMode == PositioningMode.BLE_ONLY) {
                        lastRenderXMeters = Double.NaN;
                        lastRenderYMeters = Double.NaN;
                        lastMode = "BLE_ONLY";
                        lastReason = result.reason;
                        Log.d(TAG, "FUSION_OUTPUT: mode=BLE_ONLY source=BLE no accepted BLE fix reason=" + result.reason);
                        renderDashboard();
                        return;
                    }
                    double[] fallback = sensorFusionManager.getFusedPositionMeters();
                    if (fallback != null && floorGrid != null) {
                        lastRenderXMeters = fallback[0];
                        lastRenderYMeters = fallback[1];
                        lastMode = activePositioningMode == PositioningMode.PDR_ONLY ? "PDR_ONLY" : "FUSION";
                        Log.d(TAG, String.format(Locale.US, "FUSION_OUTPUT: mode=%s fallback render (%.2f, %.2f)", activePositioningMode, fallback[0], fallback[1]));
                    }
                    renderDashboard();
                    return;
                }

                if (!result.acceptedBle) {
                    renderDashboard();
                    return;
                }

                double xMeters = result.bleXMeters;
                double yMeters = result.bleYMeters;
                if (shouldHoldStaticManualAnchor(xMeters, yMeters)) {
                    double[] fused = sensorFusionManager.getFusedPositionMeters();
                    if (fused != null) {
                        lastRenderXMeters = fused[0];
                        lastRenderYMeters = fused[1];
                    } else {
                        lastRenderXMeters = lastManualAnchorXMeters;
                        lastRenderYMeters = lastManualAnchorYMeters;
                    }
                    double anchorDelta = Math.hypot(xMeters - lastManualAnchorXMeters, yMeters - lastManualAnchorYMeters);
                    lastMode = "BLE_REJECTED";
                    lastReason = "STATIC_ANCHOR_HOLD";
                    Log.d(TAG, String.format(Locale.US,
                            "FUSION_OUTPUT: reject BLE while stationary at manual anchor anchor=(%.2f,%.2f) ble=(%.2f,%.2f) delta=%.2f threshold=%.2f",
                            lastManualAnchorXMeters,
                            lastManualAnchorYMeters,
                            xMeters,
                            yMeters,
                            anchorDelta,
                            STATIC_ANCHOR_BLE_REJECT_M
                    ));
                    renderDashboard();
                    return;
                }
                Log.i(TAG, String.format(Locale.US, "FUSION_OUTPUT: positioningMode=%s source=BLE raw=(%.2f,%.2f)", activePositioningMode, xMeters, yMeters));
                discardOneShotReference("BLE_FIX");

                if (activePositioningMode == PositioningMode.BLE_ONLY) {
                    lastRenderXMeters = xMeters;
                    lastRenderYMeters = yMeters;
                    lastMode = "BLE_ONLY";
                    lastReason = "BLE_ONLY_RAW";
                    lastBleRenderMs = System.currentTimeMillis();
                    Log.d(TAG, String.format(Locale.US, "FUSION_OUTPUT: mode=BLE_ONLY source=BLE render=(%.2f,%.2f)", xMeters, yMeters));
                    renderDashboard();
                    return;
                }

                if (activePositioningMode == PositioningMode.PDR_ONLY) {
                    double[] pdr = sensorFusionManager.getFusedPositionMeters();
                    if (pdr != null) {
                        lastRenderXMeters = pdr[0];
                        lastRenderYMeters = pdr[1];
                    }
                    lastMode = "PDR_ONLY";
                    lastReason = "BLE_IGNORED_BY_MODE";
                    Log.d(TAG, String.format(Locale.US,
                            "FUSION_OUTPUT: mode=PDR_ONLY source=PDR bleIgnored=(%.2f,%.2f) render=(%.2f,%.2f)",
                            xMeters,
                            yMeters,
                            lastRenderXMeters,
                            lastRenderYMeters
                    ));
                    renderDashboard();
                    return;
                }

                if (ENABLE_BLE_HEADING_AUTO_ALIGN && result.hadPreviousAccepted) {
                    double deltaX = xMeters - result.previousAcceptedXMeters;
                    double deltaY = yMeters - result.previousAcceptedYMeters;
                    boolean aligned = sensorFusionManager.alignHeadingToMotion(deltaX, deltaY);
                    Log.d(TAG, String.format(Locale.US,
                            "FUSION: heading align delta=(%.2f, %.2f) aligned=%s",
                            deltaX, deltaY, aligned
                    ));
                }
                sensorFusionManager.onBlePosition(xMeters, yMeters, result.filteredCount, result.bleQualityScore);
                double[] fused = sensorFusionManager.getFusedPositionMeters();

                lastRenderXMeters = xMeters;
                lastRenderYMeters = yMeters;
                if (fused != null) {
                    lastRenderXMeters = fused[0];
                    lastRenderYMeters = fused[1];
                    Log.d(TAG, String.format(Locale.US, "FUSION_OUTPUT: mode=FUSION source=FUSED render=(%.2f, %.2f) ble=(%.2f, %.2f)", fused[0], fused[1], xMeters, yMeters));
                } else {
                    Log.d(TAG, String.format(Locale.US, "FUSION_OUTPUT: mode=FUSION source=BLE_BOOTSTRAP render=(%.2f, %.2f)", xMeters, yMeters));
                }
                lastMode = (fused != null) ? "FUSION" : "BLE_ONLY";
                lastReason = "OK";
                lastBleRenderMs = System.currentTimeMillis();

                renderDashboard();
            }

        };
    }

    private List<bleBeacon> selectSolveBeacons(List<bleBeacon> currentLiveBeacons, List<IBeaconDevice> safeList, long nowMs) {
        Set<String> sdkBatchKeys = new HashSet<>();
        for (IBeaconDevice device : safeList) {
            if (device == null) {
                continue;
            }
            String uuid = device.getProximityUUID() != null
                    ? device.getProximityUUID().toString()
                    : device.getUniqueId();
            sdkBatchKeys.add(BeaconLocation.buildCompositeKey(uuid, device.getMajor(), device.getMinor()));
        }

        // Solve from every fresh calibrated beacon in the shared live cache. The cache
        // is fed by both the Kontakt SDK and the raw Android iBeacon fallback, so
        // beacons like Red10 still participate when the SDK callback omits them.
        List<bleBeacon> solveBeacons = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        for (bleBeacon beacon : currentLiveBeacons) {
            if (beacon == null) {
                continue;
            }
            String key = beacon.getCompositeKey();
            if (!seenKeys.add(key)) {
                continue;
            }
            long ageMs = nowMs - beacon.getLastSeenMs();
            if (ageMs <= MAX_SOLVE_BEACON_AGE_MS) {
                solveBeacons.add(beacon);
                if (!sdkBatchKeys.contains(key)) {
                    Log.d(TAG, "BLE_POS: include fresh fallback solve beacon id="
                            + beacon.getJsonId() + " key=" + key + " ageMs=" + ageMs);
                }
            } else {
                Log.d(TAG, "BLE_POS: drop stale solve beacon id="
                        + beacon.getJsonId() + " key=" + key + " ageMs=" + ageMs);
            }
        }
        return solveBeacons;
    }

    private boolean shouldHoldStaticManualAnchor(double bleXMeters, double bleYMeters) {
        if (activePositioningMode != PositioningMode.FUSION || sensorFusionManager == null) {
            return false;
        }
        if (!Double.isFinite(lastManualAnchorXMeters) || !Double.isFinite(lastManualAnchorYMeters) ||
                !Double.isFinite(bleXMeters) || !Double.isFinite(bleYMeters)) {
            return false;
        }
        if (!sensorFusionManager.isCurrentlyStationary()) {
            return false;
        }
        double anchorDelta = Math.hypot(bleXMeters - lastManualAnchorXMeters, bleYMeters - lastManualAnchorYMeters);
        return anchorDelta > STATIC_ANCHOR_BLE_REJECT_M;
    }

    private void logGroundTruthBeaconDiagnostics(List<bleBeacon> solveBeacons) {
        if (userPositionHistory == null || !userPositionHistory.hasGroundTruth() || solveBeacons == null) {
            return;
        }

        // These logs help tune rssi1Meter during thesis calibration runs. They compare
        // configured distance against the distance implied by the current ground truth.
        double truthX = userPositionHistory.getCurrentGroundTruthXMeters();
        double truthY = userPositionHistory.getCurrentGroundTruthYMeters();
        for (bleBeacon beacon : solveBeacons) {
            double beaconX = CoordinateUtils.beaconXMeters(beacon);
            double beaconY = CoordinateUtils.beaconYMeters(beacon);
            if (!Double.isFinite(beaconX) || !Double.isFinite(beaconY)) {
                continue;
            }

            double actualDistance = Math.hypot(truthX - beaconX, truthY - beaconY);
            double avgRssi = beacon.getAverageRssi(JsonOps.rssiAverageWindowSize);
            double impliedRssi1m = Double.NaN;
            if (Double.isFinite(avgRssi) && actualDistance > 0.0) {
                double pathLossExponent = JsonOps.pathLossExponent;
                if (!Double.isFinite(pathLossExponent) || pathLossExponent <= 0.0) {
                    pathLossExponent = 2.0;
                }
                impliedRssi1m = avgRssi + (10.0 * pathLossExponent * Math.log10(actualDistance));
            }
            Log.d(TAG, String.format(Locale.US,
                    "BLE_CAL: id=%s truth=(%.2f,%.2f) beacon=(%.2f,%.2f) actualDist=%.2f avgRssi=%.1f configuredRssi1m=%d impliedRssi1m=%.1f estimatedDist=%.2f samples=%d",
                    beacon.getJsonId(),
                    truthX,
                    truthY,
                    beaconX,
                    beaconY,
                    actualDistance,
                    avgRssi,
                    beacon.getRssi1Meter(),
                    impliedRssi1m,
                    beacon.getDistanceFromAveragedRssi(JsonOps.rssiAverageWindowSize),
                    beacon.getRssiSampleCount()
            ));
        }
    }

    private void discardOneShotReference(String reason) {
        if (userPositionHistory == null || !userPositionHistory.hasGroundTruth()) {
            return;
        }
        userPositionHistory.clearGroundTruth();
        Log.d(TAG, "REFERENCE: cleared one-shot anchor truth after " + reason);
    }

    private SpaceListener createSpaceListener() {
        return new SpaceListener() {
            @Override
            public void onRegionEntered(IBeaconRegion region) {
                Log.i(TAG, "Entered region " + region.getIdentifier());
            }

            @Override
            public void onRegionAbandoned(IBeaconRegion region) {
                Log.i(TAG, "Left region " + region.getIdentifier());
            }

            @Override
            public void onNamespaceEntered(IEddystoneNamespace namespace) {}

            @Override
            public void onNamespaceAbandoned(IEddystoneNamespace namespace) {}
        };
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "FUSION: activity onStop -> stopping sensors");
        sensorFusionManager.stop();
        watchdogHandler.removeCallbacks(sensorRenderRunnable);
        watchdogHandler.removeCallbacks(scanWatchdogRunnable);
        if (isBluetoothReceiverRegistered) {
            unregisterReceiver(bluetoothStateReceiver);
            isBluetoothReceiverRegistered = false;
        }
        if (proximityManager != null) {
            proximityManager.stopScanning();
            scanStoppedForLifecycle = true;
        }
        stopRawBleDebugScan();
        super.onStop();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        if (!isBluetoothReceiverRegistered) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(bluetoothStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(bluetoothStateReceiver, filter);
            }
            isBluetoothReceiverRegistered = true;
        }
        Log.d(TAG, "FUSION: activity onStart -> starting sensors");
        sensorFusionManager.start();
        watchdogHandler.removeCallbacks(sensorRenderRunnable);
        watchdogHandler.postDelayed(sensorRenderRunnable, SENSOR_RENDER_INTERVAL_MS);
        maybeStartAfterPrereqs();
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                Log.d(TAG, "Bluetooth enable request accepted.");
                maybeStartAfterPrereqs();
            } else {
                Log.d(TAG, "Bluetooth enable request declined.");
            }
            return;
        }
        if (requestCode == REQUEST_ENABLE_LOCATION) {
            Log.d(TAG, "Returned from location settings, rechecking prerequisites.");
            maybeStartAfterPrereqs();
        }
    }

    @Override
    protected void onDestroy() {
        stopRawBleDebugScan();
        if (proximityManager != null) {
            proximityManager.disconnect();
            proximityManager = null;
        }
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            backgroundThread = null;
            backgroundHandler = null;
        }
        super.onDestroy();
    }

}
