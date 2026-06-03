package com.example.connectiontest.TrilaterationUtils;

import android.util.Log;

import com.example.connectiontest.BeaconManager.BeaconLocation;
import com.example.connectiontest.BeaconManager.bleBeacon;
import com.example.connectiontest.JsonOps.JsonOps;

/**
 * Converts between the map grid used by the JSON/UI and the meter coordinates
 * used by positioning math.
 *
 * Convention used across the project:
 * - grid row maps to yMeters
 * - grid col maps to xMeters
 * - row=0,col=0 maps to x=0,y=0
 */
public final class CoordinateUtils {
    private static final String TAG = "CoordinateUtils";

    private CoordinateUtils() {}

    public static double metersPerCol() {
        if (!hasValidGridAndRoom()) {
            return Double.NaN;
        }
        int cols = JsonOps.floorPlan[0].length;
        return JsonOps.roomWidthMeters / (cols - 1);
    }

    public static double metersPerRow() {
        if (!hasValidGridAndRoom()) {
            return Double.NaN;
        }
        int rows = JsonOps.floorPlan.length;
        return JsonOps.roomHeightMeters / (rows - 1);
    }

    // Grid (row,col) -> meters (x,y).
    public static double gridColToXMeters(int col) {
        double metersPerCol = metersPerCol();
        if (!Double.isFinite(metersPerCol)) {
            return Double.NaN;
        }
        return col * metersPerCol;
    }

    public static double gridRowToYMeters(int row) {
        double metersPerRow = metersPerRow();
        if (!Double.isFinite(metersPerRow)) {
            return Double.NaN;
        }
        return row * metersPerRow;
    }

    // Meters (x,y) -> grid (row,col) as doubles; callers decide whether to round.
    public static double xMetersToGridCol(double xMeters) {
        double metersPerCol = metersPerCol();
        if (!Double.isFinite(metersPerCol) || metersPerCol == 0.0 || Double.isNaN(xMeters)) {
            return Double.NaN;
        }
        return xMeters / metersPerCol;
    }

    public static double yMetersToGridRow(double yMeters) {
        double metersPerRow = metersPerRow();
        if (!Double.isFinite(metersPerRow) || metersPerRow == 0.0 || Double.isNaN(yMeters)) {
            return Double.NaN;
        }
        return yMeters / metersPerRow;
    }

    public static double beaconXMeters(BeaconLocation beacon) {
        if (beacon == null) {
            return Double.NaN;
        }
        double exact = beacon.getXMeters();
        if (Double.isFinite(exact)) {
            return exact;
        }
        return gridColToXMeters(beacon.getCol());
    }

    public static double beaconYMeters(BeaconLocation beacon) {
        if (beacon == null) {
            return Double.NaN;
        }
        double exact = beacon.getYMeters();
        if (Double.isFinite(exact)) {
            return exact;
        }
        return gridRowToYMeters(beacon.getRow());
    }

    public static double beaconXMeters(bleBeacon beacon) {
        if (beacon == null) {
            return Double.NaN;
        }
        double exact = beacon.getBeaconXMeters();
        if (Double.isFinite(exact)) {
            return exact;
        }
        return gridColToXMeters(beacon.getBeaconCol());
    }

    public static double beaconYMeters(bleBeacon beacon) {
        if (beacon == null) {
            return Double.NaN;
        }
        double exact = beacon.getBeaconYMeters();
        if (Double.isFinite(exact)) {
            return exact;
        }
        return gridRowToYMeters(beacon.getBeaconRow());
    }

    private static boolean hasValidGridAndRoom() {
        if (JsonOps.floorPlan == null || JsonOps.floorPlan.length == 0 || JsonOps.floorPlan[0].length == 0) {
            Log.w(TAG, "Invalid floor plan for coordinate conversion (null/empty).");
            return false;
        }
        if (JsonOps.floorPlan.length <= 1 || JsonOps.floorPlan[0].length <= 1) {
            Log.w(TAG, "Invalid floor plan dimensions for coordinate conversion: rows="
                    + JsonOps.floorPlan.length + ", cols=" + JsonOps.floorPlan[0].length);
            return false;
        }
        if (JsonOps.roomWidthMeters <= 0 || JsonOps.roomHeightMeters <= 0) {
            Log.w(TAG, "Invalid room dimensions for coordinate conversion: width="
                    + JsonOps.roomWidthMeters + ", height=" + JsonOps.roomHeightMeters);
            return false;
        }
        return true;
    }
}
