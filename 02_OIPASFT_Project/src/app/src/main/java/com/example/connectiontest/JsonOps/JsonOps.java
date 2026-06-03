package com.example.connectiontest.JsonOps;

import android.content.Context;
import android.util.Log;

import com.example.connectiontest.BeaconManager.BeaconLocation;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Loads the active room calibration asset into shared runtime state.
 *
 * The app intentionally keeps the parsed floor plan, room size, and beacon list
 * in static fields because most positioning utilities are small Java helpers that
 * need the same coordinate frame. Treat this class as the single parser/validator
 * for calibration JSON.
 */
public class JsonOps {
    private static final String TAG = "JsonOps";
    private static final double DEFAULT_ROOM_WIDTH_METERS = 5.0;
    private static final double DEFAULT_ROOM_HEIGHT_METERS = 5.0;
    private static final double DEFAULT_PATH_LOSS_EXPONENT = 2.0;
    private static final double DEFAULT_HEADING_OFFSET_DEGREES = 0.0;
    private static final double DEFAULT_STEP_LENGTH_METERS = 0.62;
    private static final double DEFAULT_MIN_STEP_LENGTH_METERS = 0.40;
    private static final double DEFAULT_MAX_STEP_LENGTH_METERS = 0.95;
    private static final long DEFAULT_MIN_STEP_INTERVAL_MS = 650L;
    private static final double DEFAULT_CUSTOM_STEP_PEAK_THRESHOLD = 1.40;
    private static final double DEFAULT_CUSTOM_STEP_RESET_THRESHOLD = 0.35;
    private static final double DEFAULT_CUSTOM_STEP_MAX_GYRO = 0.95;
    private static final double DEFAULT_CUSTOM_STEP_MAX_ABS_PITCH_DEGREES = 35.0;
    private static final double DEFAULT_CUSTOM_STEP_MAX_ABS_ROLL_DEGREES = 35.0;
    private static final int DEFAULT_RSSI_AVERAGE_WINDOW_SIZE = 3;
    private static final double DEFAULT_BLE_MIN_DISTANCE_METERS = 0.08;
    private static final double DEFAULT_BLE_DISTANCE_MARGIN_METERS = 1.25;
    private static final double DEFAULT_BLE_BOUNDARY_CLAMP_MARGIN_METERS = 0.20;
    private static final double DEFAULT_BLE_MAX_JUMP_METERS = 2.5;
    private static final double DEFAULT_BLE_RESIDUAL_REJECT_METERS = 0.85;

    public static int[][] floorPlan = null;
    public static ArrayList<BeaconLocation> beaconLocation = new ArrayList<>(); // Contains the given data, by the JSON, for the beacons.
    public static double roomWidthMeters = DEFAULT_ROOM_WIDTH_METERS;
    public static double roomHeightMeters = DEFAULT_ROOM_HEIGHT_METERS;
    public static double pathLossExponent = DEFAULT_PATH_LOSS_EXPONENT;
    public static double headingOffsetDegrees = DEFAULT_HEADING_OFFSET_DEGREES;
    public static double stepLengthMeters = DEFAULT_STEP_LENGTH_METERS;
    public static double minStepLengthMeters = DEFAULT_MIN_STEP_LENGTH_METERS;
    public static double maxStepLengthMeters = DEFAULT_MAX_STEP_LENGTH_METERS;
    public static long minStepIntervalMs = DEFAULT_MIN_STEP_INTERVAL_MS;
    public static double customStepPeakThreshold = DEFAULT_CUSTOM_STEP_PEAK_THRESHOLD;
    public static double customStepResetThreshold = DEFAULT_CUSTOM_STEP_RESET_THRESHOLD;
    public static double customStepMaxGyro = DEFAULT_CUSTOM_STEP_MAX_GYRO;
    public static double customStepMaxAbsPitchDegrees = DEFAULT_CUSTOM_STEP_MAX_ABS_PITCH_DEGREES;
    public static double customStepMaxAbsRollDegrees = DEFAULT_CUSTOM_STEP_MAX_ABS_ROLL_DEGREES;
    public static int rssiAverageWindowSize = DEFAULT_RSSI_AVERAGE_WINDOW_SIZE;
    public static double bleMinDistanceMeters = DEFAULT_BLE_MIN_DISTANCE_METERS;
    public static double bleDistanceMarginMeters = DEFAULT_BLE_DISTANCE_MARGIN_METERS;
    public static double bleBoundaryClampMarginMeters = DEFAULT_BLE_BOUNDARY_CLAMP_MARGIN_METERS;
    public static double bleMaxJumpMeters = DEFAULT_BLE_MAX_JUMP_METERS;
    public static double bleResidualRejectMeters = DEFAULT_BLE_RESIDUAL_REJECT_METERS;

    /**
     * Parses a calibration asset and resets previously loaded calibration state.
     * The JSON must define room dimensions, a rectangular floorPlan, and beacons.
     */
    public static int[][] JsonTo2DArray(Context context, String fileName) {
        floorPlan = null;
        beaconLocation.clear();
        roomWidthMeters = DEFAULT_ROOM_WIDTH_METERS;
        roomHeightMeters = DEFAULT_ROOM_HEIGHT_METERS;
        pathLossExponent = DEFAULT_PATH_LOSS_EXPONENT;
        resetPositioningModelDefaults();
        try {
            Log.d(TAG, "Opening JSON file: " + fileName);
            String jsonString;
            try (InputStream inputStream = context.getAssets().open(fileName)) {
                jsonString = readFully(inputStream);
            }
            Log.d(TAG, "Successfully read the JSON file.");

            JSONObject jsonObject = new JSONObject(jsonString);

            if (jsonObject.has("room")) {
                JSONObject room = jsonObject.getJSONObject("room");
                roomWidthMeters  = room.optDouble("width",  Double.NaN);
                roomHeightMeters = room.optDouble("height", Double.NaN);
                validateRoomDimensions(roomWidthMeters, roomHeightMeters);

                Log.d(TAG, "Room width: " + roomWidthMeters);
                Log.d(TAG, "Room height: " + roomHeightMeters);
            }

            if (jsonObject.has("model")) {
                JSONObject model = jsonObject.getJSONObject("model");
                pathLossExponent = model.optDouble("pathLossExponent", DEFAULT_PATH_LOSS_EXPONENT);
                validatePathLossExponent(pathLossExponent);
                Log.d(TAG, "Path-loss exponent: " + pathLossExponent);
                parsePositioningModel(model);
            }

            JSONArray floorPlanArray = jsonObject.getJSONArray("floorPlan");
            if (floorPlanArray.length() == 0) {
                throw new JSONException("floorPlan must contain at least one row");
            }

            int rows = floorPlanArray.length();
            int cols = floorPlanArray.getJSONArray(0).length();
            if (cols == 0) {
                throw new JSONException("floorPlan rows must contain at least one column");
            }
            floorPlan = new int[rows][cols];

            // Keep floorPlan rectangular so row/col to meter conversion is stable.
            for (int i = 0; i < rows; i++) {
                JSONArray row = floorPlanArray.getJSONArray(i);
                if (row.length() != cols) {
                    throw new JSONException("floorPlan rows must all have the same length");
                }
                for (int j = 0; j < cols; j++) {
                    floorPlan[i][j] = row.getInt(j);
                }
            }

            JsonToBeaconArray(jsonObject);

            Log.d(TAG, "Successfully parsed JSON into 2D array.");
        } catch (Exception e) {
            Log.e(TAG, "Error while parsing JSON file.", e);
            floorPlan = null;
            beaconLocation.clear();
            roomWidthMeters = DEFAULT_ROOM_WIDTH_METERS;
            roomHeightMeters = DEFAULT_ROOM_HEIGHT_METERS;
            pathLossExponent = DEFAULT_PATH_LOSS_EXPONENT;
            resetPositioningModelDefaults();
        }

        return floorPlan;
    }

    private static String readFully(InputStream inputStream) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        return outputStream.toString(StandardCharsets.UTF_8.name());
    }

    public static void JsonToBeaconArray(JSONObject jsonObject){
        JSONArray beaconsArray;
        try {
            beaconsArray = jsonObject.getJSONArray("beacons");
            ArrayList<BeaconLocation> parsedLocations = new ArrayList<>();
            Set<String> seenKeys = new HashSet<>();
            Set<String> seenIds = new HashSet<>();
            Map<String, String> seenCells = new HashMap<>();
            for (int i = 0; i < beaconsArray.length(); i++) {
                JSONObject beaconJson = beaconsArray.getJSONObject(i);

                BeaconLocation loc = new BeaconLocation();
                loc.id = beaconJson.getString("id");
                loc.beaconUUID = beaconJson.getString("uuid");
                Log.d(TAG, "UUID from JSON: " + loc.beaconUUID);
                loc.major = beaconJson.getInt("major");
                loc.minor = beaconJson.getInt("minor");

                JSONObject position = beaconJson.getJSONObject("position");
                loc.row = position.getInt("row");
                loc.col = position.getInt("col");
                loc.xMeters = CoordinateFallback.gridColToXMeters(loc.col);
                loc.yMeters = CoordinateFallback.gridRowToYMeters(loc.row);
                loc.exactPositionMeters = false;
                if (beaconJson.has("positionMeters")) {
                    JSONObject positionMeters = beaconJson.getJSONObject("positionMeters");
                    loc.xMeters = positionMeters.getDouble("x");
                    loc.yMeters = positionMeters.getDouble("y");
                    loc.exactPositionMeters = true;
                }

                loc.rssi1Meter = beaconJson.getInt("rssi1Meter");
                validateBeaconLocation(loc);

                if (!seenIds.add(loc.id)) {
                    throw new JSONException("Duplicate beacon id: " + loc.id);
                }

                String key = loc.getCompositeKey();
                if (!seenKeys.add(key)) {
                    throw new JSONException("Duplicate beacon definition for key=" + key + " id=" + loc.id);
                }

                String cellKey = loc.row + "|" + loc.col;
                String existingId = seenCells.putIfAbsent(cellKey, loc.id);
                if (existingId != null) {
                    throw new JSONException("Two beacons share cell (" + loc.row + "," + loc.col + "): " + existingId + ", " + loc.id);
                }
                if (floorPlan != null && floorPlan[loc.row][loc.col] != 1) {
                    throw new JSONException("Beacon " + loc.id + " is not placed on a beacon cell in floorPlan");
                }

                parsedLocations.add(loc);
            }
            beaconLocation.clear();
            beaconLocation.addAll(parsedLocations);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    private static void validateRoomDimensions(double widthMeters, double heightMeters) throws JSONException {
        if (!Double.isFinite(widthMeters) || !Double.isFinite(heightMeters)) {
            throw new JSONException("Room width/height must be finite numbers");
        }
        if (widthMeters <= 0.0 || heightMeters <= 0.0) {
            throw new JSONException("Room width/height must be positive");
        }
    }

    private static void validateBeaconLocation(BeaconLocation loc) throws JSONException {
        if (floorPlan == null || floorPlan.length == 0 || floorPlan[0].length == 0) {
            throw new JSONException("floorPlan must be parsed before beacon locations");
        }
        if (loc.row < 0 || loc.row >= floorPlan.length || loc.col < 0 || loc.col >= floorPlan[0].length) {
            throw new JSONException("Beacon " + loc.id + " position is outside floorPlan bounds");
        }
        if (!Double.isFinite(loc.xMeters) || !Double.isFinite(loc.yMeters)) {
            throw new JSONException("Beacon " + loc.id + " positionMeters must be finite");
        }
        if (loc.xMeters < 0.0 || loc.xMeters > roomWidthMeters ||
                loc.yMeters < 0.0 || loc.yMeters > roomHeightMeters) {
            throw new JSONException("Beacon " + loc.id + " positionMeters is outside room bounds");
        }
    }

    private static void validatePathLossExponent(double exponent) throws JSONException {
        if (!Double.isFinite(exponent)) {
            throw new JSONException("Path-loss exponent must be a finite number");
        }
        if (exponent < 1.0 || exponent > 6.0) {
            throw new JSONException("Path-loss exponent must be between 1.0 and 6.0");
        }
    }

    private static void resetPositioningModelDefaults() {
        headingOffsetDegrees = DEFAULT_HEADING_OFFSET_DEGREES;
        stepLengthMeters = DEFAULT_STEP_LENGTH_METERS;
        minStepLengthMeters = DEFAULT_MIN_STEP_LENGTH_METERS;
        maxStepLengthMeters = DEFAULT_MAX_STEP_LENGTH_METERS;
        minStepIntervalMs = DEFAULT_MIN_STEP_INTERVAL_MS;
        customStepPeakThreshold = DEFAULT_CUSTOM_STEP_PEAK_THRESHOLD;
        customStepResetThreshold = DEFAULT_CUSTOM_STEP_RESET_THRESHOLD;
        customStepMaxGyro = DEFAULT_CUSTOM_STEP_MAX_GYRO;
        customStepMaxAbsPitchDegrees = DEFAULT_CUSTOM_STEP_MAX_ABS_PITCH_DEGREES;
        customStepMaxAbsRollDegrees = DEFAULT_CUSTOM_STEP_MAX_ABS_ROLL_DEGREES;
        rssiAverageWindowSize = DEFAULT_RSSI_AVERAGE_WINDOW_SIZE;
        bleMinDistanceMeters = DEFAULT_BLE_MIN_DISTANCE_METERS;
        bleDistanceMarginMeters = DEFAULT_BLE_DISTANCE_MARGIN_METERS;
        bleBoundaryClampMarginMeters = DEFAULT_BLE_BOUNDARY_CLAMP_MARGIN_METERS;
        bleMaxJumpMeters = DEFAULT_BLE_MAX_JUMP_METERS;
        bleResidualRejectMeters = DEFAULT_BLE_RESIDUAL_REJECT_METERS;
    }

    private static void parsePositioningModel(JSONObject model) {
        headingOffsetDegrees = model.optDouble("headingOffsetDegrees", headingOffsetDegrees);
        stepLengthMeters = model.optDouble("stepLengthMeters", stepLengthMeters);
        minStepLengthMeters = model.optDouble("minStepLengthMeters", minStepLengthMeters);
        maxStepLengthMeters = model.optDouble("maxStepLengthMeters", maxStepLengthMeters);
        minStepIntervalMs = model.optLong("minStepIntervalMs", minStepIntervalMs);
        customStepPeakThreshold = model.optDouble("customStepPeakThreshold", customStepPeakThreshold);
        customStepResetThreshold = model.optDouble("customStepResetThreshold", customStepResetThreshold);
        customStepMaxGyro = model.optDouble("customStepMaxGyro", customStepMaxGyro);
        customStepMaxAbsPitchDegrees = model.optDouble("customStepMaxAbsPitchDegrees", customStepMaxAbsPitchDegrees);
        customStepMaxAbsRollDegrees = model.optDouble("customStepMaxAbsRollDegrees", customStepMaxAbsRollDegrees);
        rssiAverageWindowSize = Math.max(1, model.optInt("rssiAverageWindowSize", rssiAverageWindowSize));
        bleMinDistanceMeters = model.optDouble("bleMinDistanceMeters", bleMinDistanceMeters);
        bleDistanceMarginMeters = model.optDouble("bleDistanceMarginMeters", bleDistanceMarginMeters);
        bleBoundaryClampMarginMeters = model.optDouble("bleBoundaryClampMarginMeters", bleBoundaryClampMarginMeters);
        bleMaxJumpMeters = model.optDouble("bleMaxJumpMeters", bleMaxJumpMeters);
        bleResidualRejectMeters = model.optDouble("bleResidualRejectMeters", bleResidualRejectMeters);

        Log.d(TAG, "CONFIG: headingOffsetDegrees=" + headingOffsetDegrees
                + " stepLengthMeters=" + stepLengthMeters
                + " minStepIntervalMs=" + minStepIntervalMs
                + " rssiAverageWindowSize=" + rssiAverageWindowSize
                + " bleMinDistanceMeters=" + bleMinDistanceMeters
                + " bleResidualRejectMeters=" + bleResidualRejectMeters);
    }

    private static final class CoordinateFallback {
        private CoordinateFallback() {}

        /**
         * Fallback conversion used while JsonOps is still parsing. After loading,
         * CoordinateUtils should be preferred by the rest of the app.
         */
        static double gridColToXMeters(int col) {
            if (floorPlan == null || floorPlan.length == 0 || floorPlan[0].length <= 1) {
                return Double.NaN;
            }
            return col * (roomWidthMeters / (floorPlan[0].length - 1));
        }

        static double gridRowToYMeters(int row) {
            if (floorPlan == null || floorPlan.length <= 1) {
                return Double.NaN;
            }
            return row * (roomHeightMeters / (floorPlan.length - 1));
        }
    }

}
