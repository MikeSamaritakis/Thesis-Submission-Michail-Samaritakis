package com.example.connectiontest.BeaconManager;

import java.util.Locale;

/**
 * Static beacon definition loaded from the calibration JSON.
 *
 * A BeaconLocation is the app's source of truth for a physical transmitter:
 * UUID/major/minor identify the beacon in BLE scans, row/col identify its map cell,
 * optional x/y meters give tape-measured coordinates, and rssi1Meter calibrates the
 * RSSI-to-distance model for the current Tx power.
 */
public class BeaconLocation {
    public String id;
    public String beaconUUID;
    public int major;
    public int minor;
    public int row; // row=y in meters
    public int col; // col=x in meters
    public double xMeters = Double.NaN;
    public double yMeters = Double.NaN;
    public boolean exactPositionMeters;
    public int rssi1Meter;

    public BeaconLocation(String id, String beaconUUID, int major, int minor, int row, int col, int rssi1Meter) {
        this(id, beaconUUID, major, minor, row, col, Double.NaN, Double.NaN, false, rssi1Meter);
    }

    public BeaconLocation(String id, String beaconUUID, int major, int minor, int row, int col,
                          double xMeters, double yMeters, boolean exactPositionMeters, int rssi1Meter) {
        this.id = id;
        this.beaconUUID = beaconUUID;
        this.row = row;
        this.col = col;
        this.xMeters = xMeters;
        this.yMeters = yMeters;
        this.exactPositionMeters = exactPositionMeters;
        this.major = major;
        this.minor = minor;
        this.rssi1Meter = rssi1Meter;
    }

    public BeaconLocation() {}

    public String getUniqueId() {
        return id;
    }

    public int getRssiAtOneMeter() {
        return rssi1Meter;
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    public double getXMeters() {
        return xMeters;
    }

    public double getYMeters() {
        return yMeters;
    }

    public boolean hasExactPositionMeters() {
        return exactPositionMeters;
    }

    /**
     * Physical beacons are matched by UUID + major + minor. The display id is kept
     * separate so tests can rename labels without breaking BLE identity matching.
     */
    public String getCompositeKey() {
        return buildCompositeKey(beaconUUID, major, minor);
    }

    public static String buildCompositeKey(String uuid, int major, int minor) {
        String safeUuid = uuid == null ? "" : uuid.trim().toLowerCase(Locale.ROOT);
        return safeUuid + "|" + major + "|" + minor;
    }
}
