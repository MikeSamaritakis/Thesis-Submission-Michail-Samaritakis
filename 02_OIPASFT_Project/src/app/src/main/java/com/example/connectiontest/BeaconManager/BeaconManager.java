package com.example.connectiontest.BeaconManager;

import android.util.Log;

import com.kontakt.sdk.android.common.profile.IBeaconDevice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Maintains the current set of known live beacons seen by the Kontakt scan callback.
 *
 * This class deliberately ignores transmitters that are not present in the active
 * calibration JSON. That keeps trilateration tied to documented beacon coordinates
 * and prevents nearby unrelated iBeacons from entering the solve.
 */
public class BeaconManager {
    private static final String TAG = "BeaconManager";
    private static final Object LIVE_BEACONS_LOCK = new Object();
    private static final List<bleBeacon> liveBeacons = new ArrayList<>();

    public static List<bleBeacon> getLiveBeacons() {
        synchronized (LIVE_BEACONS_LOCK) {
            return Collections.unmodifiableList(new ArrayList<>(liveBeacons));
        }
    }

    public static int pruneStaleBeacons(long nowMs, long staleAfterMs) {
        synchronized (LIVE_BEACONS_LOCK) {
            int before = liveBeacons.size();
            liveBeacons.removeIf(beacon -> nowMs - beacon.getLastSeenMs() > staleAfterMs);
            return before - liveBeacons.size();
        }
    }

    /**
     * Upserts one scan result into the live-beacon cache.
     *
     * Existing beacons only get a new RSSI sample. New beacons are created only after
     * matching UUID/major/minor against the loaded calibration list.
     */
    public static void checkAndUpsertBeacon(IBeaconDevice ibeacon, List<BeaconLocation> knownLocations) {
        if (ibeacon == null || knownLocations == null) {
            return;
        }
        String uuid = readBeaconUuid(ibeacon);
        int major = readBeaconMajor(ibeacon);
        int minor = readBeaconMinor(ibeacon);
        String compositeKey = BeaconLocation.buildCompositeKey(uuid, major, minor);
        int rssi = ibeacon.getRssi();
        long nowMs = System.currentTimeMillis();

        synchronized (LIVE_BEACONS_LOCK) {
            for (bleBeacon b : liveBeacons) {
                if (b.getCompositeKey().equals(compositeKey)) {
                    b.updateRssi(rssi, nowMs);
                    return;
                }
            }

            for (BeaconLocation location : knownLocations) {
                if (location.getCompositeKey().equals(compositeKey)) {
                    bleBeacon newBeacon = new bleBeacon(
                            ibeacon.getName(),
                            ibeacon.getAddress(),
                            uuid,
                            major,
                            minor,
                            location.getUniqueId(),
                            location.getRssiAtOneMeter(),
                            location.getRow(),
                            location.getCol(),
                            location.getXMeters(),
                            location.getYMeters(),
                            location.hasExactPositionMeters()
                    );
                    newBeacon.updateRssi(rssi, nowMs);
                    liveBeacons.add(newBeacon);

                    Log.d(TAG, "Beacon " + newBeacon.getCompositeKey() + " is now: " + newBeacon.getDistanceFromUser() + " from user");
                    Log.d(TAG + "BEACON_MAP",
                            "id=" + location.getUniqueId() +
                                    " uuid=" + uuid +
                                    " major=" + major +
                                    " minor=" + minor +
                                    " col(X)=" + location.getCol() +
                                    " row(Y)=" + location.getRow() +
                                    " xMeters=" + location.getXMeters() +
                                    " yMeters=" + location.getYMeters() +
                                    " exactMeters=" + location.hasExactPositionMeters()
                    );

                    return;
                }
            }
        }

        Log.w(TAG, "Beacon not in known locations. key=" + compositeKey + " name=" + ibeacon.getName());
    }

    /**
     * Upserts a decoded iBeacon packet from Android's raw BLE scanner.
     *
     * This is a fallback for valid iBeacon advertisements that the Kontakt SDK sees
     * inconsistently. It still accepts only beacons present in the active calibration
     * JSON, so unrelated nearby transmitters cannot enter positioning.
     */
    public static boolean checkAndUpsertRawIBeacon(
            String address,
            String uuid,
            int major,
            int minor,
            int rssi,
            List<BeaconLocation> knownLocations
    ) {
        if (knownLocations == null) {
            return false;
        }
        String normalizedUuid = normalizeUuid(uuid);
        String compositeKey = BeaconLocation.buildCompositeKey(normalizedUuid, major, minor);
        long nowMs = System.currentTimeMillis();

        synchronized (LIVE_BEACONS_LOCK) {
            for (bleBeacon b : liveBeacons) {
                if (b.getCompositeKey().equals(compositeKey)) {
                    b.updateRssi(rssi, nowMs);
                    return true;
                }
            }

            for (BeaconLocation location : knownLocations) {
                if (location.getCompositeKey().equals(compositeKey)) {
                    bleBeacon newBeacon = new bleBeacon(
                            "RAW_iBeacon",
                            address,
                            normalizedUuid,
                            major,
                            minor,
                            location.getUniqueId(),
                            location.getRssiAtOneMeter(),
                            location.getRow(),
                            location.getCol(),
                            location.getXMeters(),
                            location.getYMeters(),
                            location.hasExactPositionMeters()
                    );
                    newBeacon.updateRssi(rssi, nowMs);
                    liveBeacons.add(newBeacon);

                    Log.d(TAG, "RAW_FALLBACK: Beacon " + newBeacon.getCompositeKey()
                            + " id=" + location.getUniqueId()
                            + " is now: " + newBeacon.getDistanceFromUser() + " from user");
                    Log.d(TAG + "BEACON_MAP",
                            "source=RAW_FALLBACK" +
                                    " id=" + location.getUniqueId() +
                                    " uuid=" + normalizedUuid +
                                    " major=" + major +
                                    " minor=" + minor +
                                    " col(X)=" + location.getCol() +
                                    " row(Y)=" + location.getRow() +
                                    " xMeters=" + location.getXMeters() +
                                    " yMeters=" + location.getYMeters() +
                                    " exactMeters=" + location.hasExactPositionMeters()
                    );

                    return true;
                }
            }
        }

        Log.d(TAG, "RAW_FALLBACK: decoded iBeacon not in known locations. key=" + compositeKey + " address=" + address);
        return false;
    }

    private static String readBeaconUuid(IBeaconDevice ibeacon) {
        UUID proximityUuid = ibeacon.getProximityUUID();
        if (proximityUuid != null) {
            return normalizeUuid(proximityUuid.toString());
        }
        return normalizeUuid(ibeacon.getUniqueId());
    }

    private static int readBeaconMajor(IBeaconDevice ibeacon) {
        return ibeacon.getMajor();
    }

    private static int readBeaconMinor(IBeaconDevice ibeacon) {
        return ibeacon.getMinor();
    }

    private static String normalizeUuid(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
