
package com.example.connectiontest.initApp;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import com.example.connectiontest.BuildConfig;
import com.kontakt.sdk.android.ble.configuration.ActivityCheckConfiguration;
import com.kontakt.sdk.android.ble.configuration.ScanMode;
import com.kontakt.sdk.android.ble.configuration.ScanPeriod;
import com.kontakt.sdk.android.ble.connection.OnServiceReadyListener;
import com.kontakt.sdk.android.ble.manager.ProximityManager;
import com.kontakt.sdk.android.ble.manager.ProximityManagerFactory;
import com.kontakt.sdk.android.ble.manager.listeners.IBeaconListener;
import com.kontakt.sdk.android.ble.manager.listeners.SpaceListener;
import com.kontakt.sdk.android.common.KontaktSDK;

/**
 * Kontakt SDK and scan-service setup.
 *
 * MainActivity owns Android lifecycle and permissions; InitApp only creates the
 * SDK objects, handlers, and scan configuration needed after prerequisites are met.
 */
public class InitApp {

    private static final String TAG = "InitApp";
    private static final long SCAN_ACTIVE_MS = 3500L;
    private static final long SCAN_PASSIVE_MS = 2500L;
    private static final long ACTIVITY_SCAN_PERIOD_MS = 3500L;
    private static final long ACTIVITY_CHECK_PERIOD_MS = 1500L;

    protected static String apiKey = BuildConfig.KONTAKT_API_KEY;

    public static class InitResult {
        public final Handler backgroundHandler;
        public final Handler uiHandler;
        public final HandlerThread backgroundThread;
        public final ProximityManager proximityManager;

        public InitResult(Handler backgroundHandler, Handler uiHandler, HandlerThread backgroundThread, ProximityManager proximityManager) {
            this.backgroundHandler = backgroundHandler;
            this.uiHandler = uiHandler;
            this.backgroundThread = backgroundThread;
            this.proximityManager = proximityManager;
        }
    }

    public static class HandlerBundle {
        public final Handler background;
        public final Handler ui;
        public final HandlerThread backgroundThread;

        public HandlerBundle(Handler background, Handler ui, HandlerThread backgroundThread) {
            this.background = background;
            this.ui = ui;
            this.backgroundThread = backgroundThread;
        }
    }

    public static boolean initializeSDK() {
        if (!hasValidApiKey()) {
            Log.e(TAG, "Kontakt SDK initialization skipped: KONTAKT_API_KEY is empty. Add it to local.properties and rebuild.");
            return false;
        }
        try {
            String masked = maskApiKey(apiKey);
            Log.d(TAG, "Initializing SDK with API key (masked): " + masked);
            KontaktSDK.initialize(apiKey);
            Log.d(TAG, "SDK initialized successfully");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "SDK initialization failed", e);
            return false;
        }
    }

    public static HandlerBundle initializeHandlers() {
        HandlerThread handlerThread = new HandlerThread("BackgroundHandlerThread");
        handlerThread.start();

        Handler background = new Handler(handlerThread.getLooper());
        Handler ui = new Handler(Looper.getMainLooper());

        return new HandlerBundle(background, ui, handlerThread);
    }

    public static ProximityManager setupProximityManager(
            Context context,
            IBeaconListener iBeaconListener,
            SpaceListener spaceListener
    ) {
        if (!hasValidApiKey()) {
            Log.e(TAG, "Kontakt proximity manager not created: KONTAKT_API_KEY is empty.");
            return null;
        }

        ProximityManager proximityManager;
        try {
            proximityManager = ProximityManagerFactory.create(context);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Kontakt proximity manager not created; check KONTAKT_API_KEY.", e);
            return null;
        }

        proximityManager.setIBeaconListener(iBeaconListener);
        proximityManager.setSpaceListener(spaceListener);

        // Low-latency scanning is intentional for test collection. Beacon advertising
        // interval is configured on the physical beacons, not here.
        proximityManager.configuration()
                .scanMode(ScanMode.LOW_LATENCY)
                // Kontakt requires active > 3000 ms and passive > 2000 ms.
                // Keep a small buffer above the boundary because this SDK validates
                // again when scanning starts and rejects edge values on some devices.
                .scanPeriod(ScanPeriod.create(SCAN_ACTIVE_MS, SCAN_PASSIVE_MS))
                .activityCheckConfiguration(ActivityCheckConfiguration.create(ACTIVITY_SCAN_PERIOD_MS, ACTIVITY_CHECK_PERIOD_MS));

        proximityManager.connect(new OnServiceReadyListener() {
            @Override
            public void onServiceReady() {
                Log.i(TAG, "SERVICE READY - STARTING SCAN");
                try {
                    proximityManager.startScanning();
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "Invalid Kontakt scan configuration; scan not started", e);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to start Kontakt scan", e);
                }
            }
        });

        return proximityManager;
    }

    public static InitResult initApp(Context context, IBeaconListener beaconListener, SpaceListener spaceListener) {
        HandlerBundle handlers = initializeHandlers();
        if (!initializeSDK()) {
            return new InitResult(handlers.background, handlers.ui, handlers.backgroundThread, null);
        }

        ProximityManager manager = setupProximityManager(context, beaconListener, spaceListener);

        return new InitResult(handlers.background, handlers.ui, handlers.backgroundThread, manager);
    }

    public static boolean hasValidApiKey() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    private static String maskApiKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            return "<empty>";
        }
        int visibleChars = Math.min(4, key.length());
        String suffix = key.substring(key.length() - visibleChars);
        return "***" + suffix + " (len=" + key.length() + ")";
    }

}
