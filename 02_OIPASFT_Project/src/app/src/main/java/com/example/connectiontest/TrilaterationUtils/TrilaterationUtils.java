package com.example.connectiontest.TrilaterationUtils;

import android.util.Log;

import com.example.connectiontest.BeaconManager.bleBeacon;

import java.util.List;
import java.util.Locale;

/**
 * Weighted least-squares multilateration over accepted beacon distances.
 *
 * Inputs are already filtered by PositioningEngine. This class only solves the
 * geometry problem: given beacon coordinates and RSSI-derived distances, estimate
 * the user's x/y position in meters.
 */
public class TrilaterationUtils {

    private static final String TAG = "TrilaterationUtils";

    // Weight floor avoids letting a near-field RSSI estimate dominate all other
    // beacons. BLE RSSI below 1 m is especially unstable in this small room.
    private static final double MIN_WEIGHT_DIST_M = 0.85;

    public static double[] trilaterationImplementation(List<bleBeacon> beacons) {
        return trilaterationImplementation(beacons, 1);
    }

    public static double[] trilaterationImplementation(List<bleBeacon> beacons, int rssiAverageWindowSize) {
        if (beacons == null || beacons.size() < 3) {
            Log.d(TAG, "Need at least 3 beacons for multilateration");
            return null;
        }

        // Use the closest valid beacon as reference to reduce bias from noisy far beacons.
        bleBeacon ref = chooseReferenceBeacon(beacons, rssiAverageWindowSize);
        if (ref == null) {
            Log.d(TAG, "No valid reference beacon found. Skip solve.");
            return null;
        }

        double x1 = CoordinateUtils.beaconXMeters(ref);
        double y1 = CoordinateUtils.beaconYMeters(ref);
        double r1 = ref.getDistanceFromAveragedRssi(rssiAverageWindowSize);
        if (!Double.isFinite(x1) || !Double.isFinite(y1) || !Double.isFinite(r1) || r1 <= 0.0) {
            Log.d(TAG, "Reference beacon has invalid geometry or distance. Skip solve.");
            return null;
        }

        // Normal equation terms for 2x2 system:
        // [a00 a01] [x] = [b0]
        // [a01 a11] [y]   [b1]
        double a00 = 0.0;
        double a01 = 0.0;
        double a11 = 0.0;
        double b0 = 0.0;
        double b1 = 0.0;

        int used = 0;

        for (bleBeacon b : beacons) {
            if (b == ref) {
                continue;
            }

            double xi = CoordinateUtils.beaconXMeters(b);
            double yi = CoordinateUtils.beaconYMeters(b);
            double ri = b.getDistanceFromAveragedRssi(rssiAverageWindowSize);
            if (!Double.isFinite(xi) || !Double.isFinite(yi) || !Double.isFinite(ri) || ri <= 0.0) {
                continue;
            }

            // Linearized circle subtraction:
            // 2(xi-x1)x + 2(yi-y1)y = r1^2 - ri^2 - x1^2 + xi^2 - y1^2 + yi^2
            double Ai = 2.0 * (xi - x1);
            double Bi = 2.0 * (yi - y1);
            double Ci = (r1 * r1) - (ri * ri) - (x1 * x1) + (xi * xi) - (y1 * y1) + (yi * yi);

            // Weight closer beacons more, but keep the floor above to avoid overfitting one beacon.
            double effectiveDist = Math.max(ri, MIN_WEIGHT_DIST_M);
            double wi = 1.0 / (effectiveDist * effectiveDist);
            Log.d(TAG, String.format(Locale.US,
                    "BLE_SOLVE: ref=%s input=%s xi=%.2f yi=%.2f ri=%.2f weight=%.2f",
                    ref.getJsonId(),
                    b.getJsonId(),
                    xi,
                    yi,
                    ri,
                    wi
            ));

            a00 += wi * Ai * Ai;
            a01 += wi * Ai * Bi;
            a11 += wi * Bi * Bi;
            b0  += wi * Ai * Ci;
            b1  += wi * Bi * Ci;

            used++;
        }

        if (used < 2) {
            Log.d(TAG, "Not enough equations for multilateration");
            return null;
        }

        double det = a00 * a11 - a01 * a01;
        if (Math.abs(det) < 1e-9) {
            Log.d(TAG, "Multilateration failed: singular matrix");
            return null;
        }

        double xMeters = (b0 * a11 - b1 * a01) / det;
        double yMeters = (a00 * b1 - a01 * b0) / det;
        if (!Double.isFinite(xMeters) || !Double.isFinite(yMeters)) {
            Log.d(TAG, "Multilateration failed: non-finite output");
            return null;
        }

        Log.d(TAG, String.format(Locale.US, "Multilateration result (m): x=%.2f y=%.2f using %d beacons avgWindow=%d",
                xMeters, yMeters, used + 1, rssiAverageWindowSize));
        StringBuilder beaconNames = new StringBuilder();
        for (bleBeacon b : beacons) {
            if (beaconNames.length() > 0) beaconNames.append(", ");
            beaconNames.append(b.getName());
        }
        Log.d(TAG, "Multilateration beacons: " + beaconNames);

        return new double[]{xMeters, yMeters};
    }

    private static bleBeacon chooseReferenceBeacon(List<bleBeacon> beacons, int rssiAverageWindowSize) {
        bleBeacon best = null;
        double bestDistance = Double.POSITIVE_INFINITY;

        for (bleBeacon beacon : beacons) {
            if (beacon == null) {
                continue;
            }
            double distance = beacon.getDistanceFromAveragedRssi(rssiAverageWindowSize);
            double x = CoordinateUtils.beaconXMeters(beacon);
            double y = CoordinateUtils.beaconYMeters(beacon);
            if (!Double.isFinite(distance) || distance <= 0.0 || !Double.isFinite(x) || !Double.isFinite(y)) {
                continue;
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                best = beacon;
            }
        }

        return best;
    }
}
