package com.example.connectiontest;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.connectiontest.BeaconManager.BeaconLocation;
import com.example.connectiontest.JsonOps.JsonOps;
import com.example.connectiontest.TrilaterationUtils.CoordinateUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lightweight View-based dashboard for live thesis testing.
 *
 * The dashboard intentionally shows raw BLE, final rendered/Kalman position, and
 * ground truth separately. That makes it possible to see whether errors come from
 * BLE trilateration, sensor fusion, or incorrect reference setup while testing.
 */
public class TrackingDashboardView extends LinearLayout {

    private static final int COLOR_PAGE = Color.rgb(246, 248, 251);
    private static final int COLOR_PANEL = Color.WHITE;
    private static final int COLOR_TEXT = Color.rgb(28, 35, 45);
    private static final int COLOR_MUTED = Color.rgb(95, 106, 120);
    private static final int COLOR_BORDER = Color.rgb(218, 225, 234);
    private static final int COLOR_GOOD = Color.rgb(20, 125, 92);
    private static final int COLOR_WARN = Color.rgb(180, 107, 23);
    private static final int COLOR_BAD = Color.rgb(176, 55, 55);

    private final TextView modePill;
    private final TextView reasonText;
    private final TextView beaconText;
    private final TextView missingText;
    private final TextView renderText;
    private final TextView bleText;
    private final TextView truthText;
    private final TextView samplesText;
    private final TextView anchorText;
    private final TextView adaptiveText;
    private final TextView trialText;
    private final TextView actionText;
    private final LinearLayout positioningModeControl;
    private final TextView bleModeButton;
    private final TextView pdrModeButton;
    private final TextView fusionModeButton;
    private final TextView adaptiveButton;
    private TextView startTrialButton;
    private TextView stopTrialButton;
    private TextView staticLabelButton;
    private TextView walkLabelButton;
    private TextView bleLabelButton;
    private TextView fusionLabelButton;
    private TextView exportLogsButton;
    private final RoomMapView roomMapView;
    private OnMapTapListener onMapTapListener;
    private OnPositioningModeChangeListener onPositioningModeChangeListener;
    private OnAdaptiveTuningChangeListener onAdaptiveTuningChangeListener;
    private OnRecordingChangeListener onRecordingChangeListener;
    private OnTrialLabelChangeListener onTrialLabelChangeListener;
    private OnExportLogsListener onExportLogsListener;
    private String selectedPositioningMode = "FUSION";
    private boolean adaptiveTuningEnabled = false;
    private boolean recordingEnabled = true;
    private String trialLabel = "UNLABELED";

    public interface OnMapTapListener {
        void onMapTapped(double xMeters, double yMeters);
    }

    public interface OnPositioningModeChangeListener {
        void onPositioningModeSelected(String modeName);
    }

    public interface OnAdaptiveTuningChangeListener {
        void onAdaptiveTuningChanged(boolean enabled);
    }

    public interface OnRecordingChangeListener {
        void onRecordingChanged(boolean enabled);
    }

    public interface OnTrialLabelChangeListener {
        void onTrialLabelChanged(String label);
    }

    public interface OnExportLogsListener {
        void onExportLogsRequested();
    }

    public TrackingDashboardView(@NonNull Context context) {
        super(context);
        setOrientation(VERTICAL);
        setBackgroundColor(COLOR_PAGE);
        setLayoutParams(new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        setPadding(dp(10), dp(8), dp(10), dp(10));

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(false);
        scrollView.setClipToPadding(false);
        scrollView.setLayoutParams(new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        content.setLayoutParams(new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        scrollView.addView(content);
        addView(scrollView);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        header.setLayoutParams(new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(context);
        title.setText("OIPASFT");
        title.setTextColor(COLOR_TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        title.setLayoutParams(new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(title);

        modePill = new TextView(context);
        modePill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        modePill.setTypeface(Typeface.DEFAULT_BOLD);
        modePill.setTextColor(Color.WHITE);
        modePill.setPadding(dp(10), dp(5), dp(10), dp(5));
        header.addView(modePill);
        content.addView(header);

        LinearLayout statusPanel = panel(context);
        LayoutParams statusParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        statusParams.topMargin = dp(8);
        statusPanel.setLayoutParams(statusParams);
        sectionTitle(context, statusPanel, "Status");
        reasonText = valueRow(context, statusPanel, "Reason");
        beaconText = valueRow(context, statusPanel, "Beacons");
        missingText = valueRow(context, statusPanel, "Missing");
        samplesText = valueRow(context, statusPanel, "Samples");
        trialText = valueRow(context, statusPanel, "Trial");
        actionText = valueRow(context, statusPanel, "Action");
        content.addView(statusPanel);

        LinearLayout positionPanel = panel(context);
        LayoutParams positionParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        positionParams.topMargin = dp(8);
        positionPanel.setLayoutParams(positionParams);
        sectionTitle(context, positionPanel, "Position");
        renderText = valueRow(context, positionPanel, "Live");
        renderText.setTypeface(Typeface.DEFAULT_BOLD);
        renderText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        bleText = valueRow(context, positionPanel, "BLE");
        truthText = valueRow(context, positionPanel, "Ref");
        content.addView(positionPanel);

        LinearLayout controlsPanel = panel(context);
        LayoutParams controlsParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        controlsParams.topMargin = dp(8);
        controlsPanel.setLayoutParams(controlsParams);
        sectionTitle(context, controlsPanel, "Experiment Controls");
        LinearLayout modeRow = new LinearLayout(context);
        modeRow.setOrientation(HORIZONTAL);
        modeRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LayoutParams modeRowParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        modeRowParams.topMargin = dp(5);
        modeRow.setLayoutParams(modeRowParams);

        TextView modeLabel = new TextView(context);
        modeLabel.setText("Tracking");
        modeLabel.setTextColor(COLOR_MUTED);
        modeLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        modeLabel.setTypeface(Typeface.DEFAULT_BOLD);
        modeLabel.setLayoutParams(new LayoutParams(dp(72), ViewGroup.LayoutParams.WRAP_CONTENT));
        modeRow.addView(modeLabel);

        positioningModeControl = new LinearLayout(context);
        positioningModeControl.setOrientation(HORIZONTAL);
        positioningModeControl.setPadding(dp(2), dp(2), dp(2), dp(2));
        positioningModeControl.setBackground(modeControlBackground());
        positioningModeControl.setLayoutParams(new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        bleModeButton = modeButton(context, "BLE", "BLE_ONLY");
        pdrModeButton = modeButton(context, "PDR", "PDR_ONLY");
        fusionModeButton = modeButton(context, "Fusion", "FUSION");
        positioningModeControl.addView(bleModeButton);
        positioningModeControl.addView(pdrModeButton);
        positioningModeControl.addView(fusionModeButton);
        modeRow.addView(positioningModeControl);
        controlsPanel.addView(modeRow);
        LinearLayout adaptiveRow = new LinearLayout(context);
        adaptiveRow.setOrientation(HORIZONTAL);
        adaptiveRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LayoutParams adaptiveRowParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        adaptiveRowParams.topMargin = dp(5);
        adaptiveRow.setLayoutParams(adaptiveRowParams);

        TextView adaptiveLabel = new TextView(context);
        adaptiveLabel.setText("Adaptive");
        adaptiveLabel.setTextColor(COLOR_MUTED);
        adaptiveLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        adaptiveLabel.setTypeface(Typeface.DEFAULT_BOLD);
        adaptiveLabel.setLayoutParams(new LayoutParams(dp(72), ViewGroup.LayoutParams.WRAP_CONTENT));
        adaptiveRow.addView(adaptiveLabel);

        adaptiveButton = new TextView(context);
        adaptiveButton.setGravity(android.view.Gravity.CENTER);
        adaptiveButton.setSingleLine(true);
        adaptiveButton.setTypeface(Typeface.DEFAULT_BOLD);
        adaptiveButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        adaptiveButton.setMinHeight(dp(32));
        adaptiveButton.setContentDescription("Adaptive tuning toggle");
        adaptiveButton.setLayoutParams(new LinearLayout.LayoutParams(0, dp(32), 1f));
        adaptiveButton.setOnClickListener(v -> setAdaptiveTuningEnabled(!adaptiveTuningEnabled));
        adaptiveRow.addView(adaptiveButton);
        controlsPanel.addView(adaptiveRow);
        addControlRows(context, controlsPanel);
        adaptiveText = valueRow(context, controlsPanel, "Adaptive");
        anchorText = valueRow(context, controlsPanel, "Tap");
        content.addView(controlsPanel);

        roomMapView = new RoomMapView(context);
        LayoutParams mapParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(430)
        );
        mapParams.topMargin = dp(10);
        mapParams.bottomMargin = dp(10);
        roomMapView.setLayoutParams(mapParams);
        roomMapView.setMinimumHeight(dp(390));
        content.addView(roomMapView);

        refreshAdaptiveButton();
        refreshRecordingButtons();
        refreshLabelButtons();
        updateTelemetry("INIT", "APP_START", 0, 0, new ArrayList<>(),
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0,
                false, Double.NaN, Double.NaN, Double.NaN, "FIXED", true, "UNLABELED");
        updateAction("Ready");
    }

    public void updateTelemetry(
            String mode,
            String reason,
            int liveCount,
            int filteredCount,
            List<String> missingBeaconIds,
            double renderX,
            double renderY,
            double bleX,
            double bleY,
            double truthX,
            double truthY,
            int positionSampleCount,
            boolean adaptiveEnabled,
            double bleQualityScore,
            double pdrQualityScore,
            double bleMeasurementStdMeters,
            String motionState,
            boolean recordingEnabled,
            String trialLabel
    ) {
        String safeMode = safe(mode);
        String safeReason = safe(reason);
        String missing = (missingBeaconIds == null || missingBeaconIds.isEmpty())
                ? "none"
                : String.join(", ", missingBeaconIds);

        post(() -> {
            modePill.setText(safeMode);
            stylePill(modePill, colorForMode(safeMode));
            reasonText.setText(safeReason);
            beaconText.setText(String.format(Locale.US, "live %d / filtered %d", liveCount, filteredCount));
            missingText.setText(missing);
            renderText.setText(formatPosition(renderX, renderY));
            bleText.setText(formatPosition(bleX, bleY));
            truthText.setText(formatPosition(truthX, truthY));
            adaptiveText.setText(formatAdaptive(adaptiveEnabled, bleQualityScore, pdrQualityScore, bleMeasurementStdMeters, motionState));
            samplesText.setText(String.format(Locale.US, "%d", positionSampleCount));
            trialText.setText(String.format(Locale.US, "%s, %s", recordingEnabled ? "recording" : "paused", safe(trialLabel)));
            anchorText.setText("ANCHOR");
            roomMapView.setStatus(safeMode, safeReason);
        });
    }

    public void updateMap(
            int[][] floorGrid,
            List<BeaconLocation> beaconLocations,
            double renderX,
            double renderY,
            double bleX,
            double bleY,
            double truthX,
            double truthY
    ) {
        post(() -> roomMapView.updateMap(floorGrid, beaconLocations, renderX, renderY, bleX, bleY, truthX, truthY));
    }

    public void setOnMapTapListener(OnMapTapListener listener) {
        onMapTapListener = listener;
    }

    public void setOnPositioningModeChangeListener(OnPositioningModeChangeListener listener) {
        onPositioningModeChangeListener = listener;
    }

    public void setOnAdaptiveTuningChangeListener(OnAdaptiveTuningChangeListener listener) {
        onAdaptiveTuningChangeListener = listener;
    }

    public void setOnRecordingChangeListener(OnRecordingChangeListener listener) {
        onRecordingChangeListener = listener;
    }

    public void setOnTrialLabelChangeListener(OnTrialLabelChangeListener listener) {
        onTrialLabelChangeListener = listener;
    }

    public void setOnExportLogsListener(OnExportLogsListener listener) {
        onExportLogsListener = listener;
    }

    public void updatePositioningMode(String modeName) {
        selectedPositioningMode = safe(modeName);
        post(this::refreshModeButtons);
    }

    public void updateAdaptiveTuning(boolean enabled) {
        adaptiveTuningEnabled = enabled;
        post(this::refreshAdaptiveButton);
    }

    public void updateRecordingState(boolean enabled) {
        recordingEnabled = enabled;
        post(this::refreshRecordingButtons);
    }

    public void updateTrialLabel(String label) {
        trialLabel = safe(label);
        post(this::refreshLabelButtons);
    }

    public void updateAction(String message) {
        String safeMessage = safe(message);
        post(() -> actionText.setText(safeMessage));
    }

    private TextView modeButton(Context context, String label, String modeName) {
        TextView button = new TextView(context);
        button.setText(label);
        button.setGravity(android.view.Gravity.CENTER);
        button.setSingleLine(true);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setMinHeight(dp(32));
        button.setContentDescription(label + " positioning mode");
        button.setLayoutParams(new LinearLayout.LayoutParams(0, dp(32), 1f));
        button.setOnClickListener(v -> selectPositioningMode(modeName));
        return button;
    }

    private void selectPositioningMode(String modeName) {
        selectedPositioningMode = safe(modeName);
        refreshModeButtons();
        if (onPositioningModeChangeListener != null) {
            onPositioningModeChangeListener.onPositioningModeSelected(selectedPositioningMode);
        }
    }

    private void setAdaptiveTuningEnabled(boolean enabled) {
        adaptiveTuningEnabled = enabled;
        refreshAdaptiveButton();
        if (onAdaptiveTuningChangeListener != null) {
            onAdaptiveTuningChangeListener.onAdaptiveTuningChanged(enabled);
        }
    }

    private void setRecordingEnabled(boolean enabled) {
        recordingEnabled = enabled;
        refreshRecordingButtons();
        if (onRecordingChangeListener != null) {
            onRecordingChangeListener.onRecordingChanged(enabled);
        }
    }

    private void selectTrialLabel(String label) {
        trialLabel = safe(label);
        refreshLabelButtons();
        if (onTrialLabelChangeListener != null) {
            onTrialLabelChangeListener.onTrialLabelChanged(trialLabel);
        }
    }

    private void refreshModeButtons() {
        styleModeButton(bleModeButton, "BLE_ONLY".equals(selectedPositioningMode));
        styleModeButton(pdrModeButton, "PDR_ONLY".equals(selectedPositioningMode));
        styleModeButton(fusionModeButton, "FUSION".equals(selectedPositioningMode));
    }

    private void styleModeButton(TextView button, boolean selected) {
        button.setTextColor(selected ? Color.WHITE : COLOR_TEXT);
        button.setBackground(selected ? modeSelectedBackground() : modeUnselectedBackground());
        button.setSelected(selected);
    }

    private void refreshAdaptiveButton() {
        adaptiveButton.setText(adaptiveTuningEnabled ? "On" : "Off");
        adaptiveButton.setTextColor(adaptiveTuningEnabled ? Color.WHITE : COLOR_TEXT);
        adaptiveButton.setBackground(adaptiveTuningEnabled ? modeSelectedBackground() : modeUnselectedBackground());
        adaptiveButton.setSelected(adaptiveTuningEnabled);
    }

    private void refreshRecordingButtons() {
        styleModeButton(startTrialButton, recordingEnabled);
        styleModeButton(stopTrialButton, !recordingEnabled);
    }

    private void refreshLabelButtons() {
        styleModeButton(staticLabelButton, "STATIC".equals(trialLabel));
        styleModeButton(walkLabelButton, "WALK".equals(trialLabel));
        styleModeButton(bleLabelButton, "BLE".equals(trialLabel));
        styleModeButton(fusionLabelButton, "FUSION".equals(trialLabel));
    }

    private void addControlRows(Context context, LinearLayout parent) {
        LinearLayout trialRow = controlRow(context, "Trial");
        startTrialButton = compactButton(context, "Start");
        stopTrialButton = compactButton(context, "Stop");
        startTrialButton.setOnClickListener(v -> setRecordingEnabled(true));
        stopTrialButton.setOnClickListener(v -> setRecordingEnabled(false));
        trialRow.addView(startTrialButton);
        trialRow.addView(stopTrialButton);
        parent.addView(trialRow);

        LinearLayout labelRow = controlRow(context, "Label");
        staticLabelButton = compactButton(context, "Static");
        walkLabelButton = compactButton(context, "Walk");
        bleLabelButton = compactButton(context, "BLE");
        fusionLabelButton = compactButton(context, "Fusion");
        staticLabelButton.setOnClickListener(v -> selectTrialLabel("STATIC"));
        walkLabelButton.setOnClickListener(v -> selectTrialLabel("WALK"));
        bleLabelButton.setOnClickListener(v -> selectTrialLabel("BLE"));
        fusionLabelButton.setOnClickListener(v -> selectTrialLabel("FUSION"));
        labelRow.addView(staticLabelButton);
        labelRow.addView(walkLabelButton);
        labelRow.addView(bleLabelButton);
        labelRow.addView(fusionLabelButton);
        parent.addView(labelRow);

        LinearLayout logsRow = controlRow(context, "Logs");
        exportLogsButton = compactButton(context, "Export");
        exportLogsButton.setOnClickListener(v -> {
            if (onExportLogsListener != null) {
                onExportLogsListener.onExportLogsRequested();
            }
        });
        logsRow.addView(exportLogsButton);
        parent.addView(logsRow);
    }

    private LinearLayout controlRow(Context context, String label) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LayoutParams rowParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rowParams.topMargin = dp(5);
        row.setLayoutParams(rowParams);

        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextColor(COLOR_MUTED);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        labelView.setTypeface(Typeface.DEFAULT_BOLD);
        labelView.setLayoutParams(new LayoutParams(dp(72), ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(labelView);
        return row;
    }

    private TextView compactButton(Context context, String label) {
        TextView button = new TextView(context);
        button.setText(label);
        button.setGravity(android.view.Gravity.CENTER);
        button.setSingleLine(true);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        button.setPadding(dp(6), 0, dp(6), 0);
        button.setMinHeight(dp(32));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(32), 1f);
        params.leftMargin = dp(3);
        button.setLayoutParams(params);
        return button;
    }

    private String formatAdaptive(boolean enabled, double bleQuality, double pdrQuality, double bleStd, String motionState) {
        if (!enabled) {
            return "off";
        }
        return String.format(
                Locale.US,
                "on, BLE q %s, PDR q %s, std %s, %s",
                formatMetric(bleQuality),
                formatMetric(pdrQuality),
                formatMetric(bleStd),
                safe(motionState)
        );
    }

    private String formatMetric(double value) {
        return Double.isFinite(value) ? String.format(Locale.US, "%.2f", value) : "-";
    }

    private void sectionTitle(Context context, LinearLayout parent, String title) {
        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextColor(COLOR_TEXT);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        titleView.setLayoutParams(new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        parent.addView(titleView);
    }

    private LinearLayout panel(Context context) {
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(VERTICAL);
        panel.setPadding(dp(10), dp(8), dp(10), dp(8));
        panel.setBackground(panelBackground());
        return panel;
    }

    private TextView valueRow(Context context, LinearLayout parent, String label) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LayoutParams rowParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rowParams.topMargin = parent.getChildCount() <= 1 ? dp(6) : dp(4);
        row.setLayoutParams(rowParams);

        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextColor(COLOR_MUTED);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        labelView.setTypeface(Typeface.DEFAULT_BOLD);
        labelView.setLayoutParams(new LayoutParams(dp(72), ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(labelView);

        TextView valueView = new TextView(context);
        valueView.setTextColor(COLOR_TEXT);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        valueView.setSingleLine(false);
        valueView.setLayoutParams(new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(valueView);

        parent.addView(row);
        return valueView;
    }

    private GradientDrawable panelBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(COLOR_PANEL);
        drawable.setCornerRadius(dp(8));
        drawable.setStroke(dp(1), COLOR_BORDER);
        return drawable;
    }

    private GradientDrawable modeControlBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.rgb(241, 245, 249));
        drawable.setCornerRadius(dp(8));
        drawable.setStroke(dp(1), COLOR_BORDER);
        return drawable;
    }

    private GradientDrawable modeSelectedBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(COLOR_GOOD);
        drawable.setCornerRadius(dp(6));
        return drawable;
    }

    private GradientDrawable modeUnselectedBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.rgb(248, 250, 252));
        drawable.setCornerRadius(dp(6));
        drawable.setStroke(dp(1), Color.rgb(226, 232, 240));
        return drawable;
    }

    private void stylePill(TextView textView, int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(999));
        textView.setBackground(drawable);
    }

    private int colorForMode(String mode) {
        if (mode == null) {
            return COLOR_WARN;
        }
        if (mode.contains("REJECTED")) {
            return COLOR_BAD;
        }
        if (mode.contains("FALLBACK") || mode.contains("INIT")) {
            return COLOR_WARN;
        }
        return COLOR_GOOD;
    }

    private String safe(String value) {
        return value == null || value.isEmpty() ? "-" : value;
    }

    private String formatPosition(double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            return "unavailable";
        }
        double row = CoordinateUtils.yMetersToGridRow(y);
        double col = CoordinateUtils.xMetersToGridCol(x);
        if (Double.isFinite(row) && Double.isFinite(col)) {
            return String.format(Locale.US, "x %.2f m, y %.2f m (col %.2f, row %.2f)", x, y, col, row);
        }
        return String.format(Locale.US, "x %.2f m, y %.2f m", x, y);
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }

    private final class RoomMapView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF roomRect = new RectF();
        private int[][] floorGrid;
        private List<BeaconLocation> beaconLocations = new ArrayList<>();
        private double renderX = Double.NaN;
        private double renderY = Double.NaN;
        private double bleX = Double.NaN;
        private double bleY = Double.NaN;
        private double truthX = Double.NaN;
        private double truthY = Double.NaN;
        private String mode = "INIT";
        private String reason = "APP_START";

        RoomMapView(Context context) {
            super(context);
            setBackground(panelBackground());
            setPadding(dp(14), dp(14), dp(14), dp(14));
            setClickable(true);
        }

        void setStatus(String mode, String reason) {
            this.mode = safe(mode);
            this.reason = safe(reason);
            invalidate();
        }

        void updateMap(
                int[][] floorGrid,
                List<BeaconLocation> beaconLocations,
                double renderX,
                double renderY,
                double bleX,
                double bleY,
                double truthX,
                double truthY
        ) {
            this.floorGrid = floorGrid;
            this.beaconLocations = beaconLocations == null ? new ArrayList<>() : new ArrayList<>(beaconLocations);
            this.renderX = renderX;
            this.renderY = renderY;
            this.bleX = bleX;
            this.bleY = bleY;
            this.truthX = truthX;
            this.truthY = truthY;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            drawTitle(canvas);
            computeRoomRect();
            drawRoom(canvas);
            drawGrid(canvas);
            drawBeacons(canvas);
            drawGroundTruthPoint(canvas);
            drawBlePoint(canvas);
            drawRenderPoint(canvas);
            drawLegend(canvas);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() != MotionEvent.ACTION_UP) {
                return true;
            }
            computeRoomRect();
            if (!roomRect.contains(event.getX(), event.getY())) {
                return true;
            }
            // The map passes exact meter coordinates, not rounded grid cells, so an
            // anchor can represent tape-measured points inside a cell.
            if (onMapTapListener != null) {
                onMapTapListener.onMapTapped(pxToXMeters(event.getX()), pxToYMeters(event.getY()));
            }
            return true;
        }

        private void drawTitle(Canvas canvas) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(COLOR_TEXT);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextSize(sp(15));
            canvas.drawText("Room map", getPaddingLeft(), getPaddingTop() + dp(16), paint);

            paint.setTypeface(Typeface.DEFAULT);
            paint.setTextSize(sp(12));
            paint.setColor(COLOR_MUTED);
            String size = String.format(Locale.US, "%.2f m x %.2f m", JsonOps.roomWidthMeters, JsonOps.roomHeightMeters);
            canvas.drawText(size, getPaddingLeft(), getPaddingTop() + dp(36), paint);
        }

        private void computeRoomRect() {
            // Preserve the room aspect ratio so visual distance on screen matches
            // distance in meters as closely as the viewport allows.
            float left = getPaddingLeft();
            float top = getPaddingTop() + dp(52);
            float right = getWidth() - getPaddingRight();
            float bottom = getHeight() - getPaddingBottom() - dp(38);
            float availableWidth = Math.max(1f, right - left);
            float availableHeight = Math.max(1f, bottom - top);
            double roomW = Math.max(JsonOps.roomWidthMeters, 0.1);
            double roomH = Math.max(JsonOps.roomHeightMeters, 0.1);
            float targetRatio = (float) (roomW / roomH);
            float rectW = availableWidth;
            float rectH = rectW / targetRatio;
            if (rectH > availableHeight) {
                rectH = availableHeight;
                rectW = rectH * targetRatio;
            }
            float centeredLeft = left + (availableWidth - rectW) / 2f;
            float centeredTop = top + (availableHeight - rectH) / 2f;
            roomRect.set(centeredLeft, centeredTop, centeredLeft + rectW, centeredTop + rectH);
        }

        private void drawRoom(Canvas canvas) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(251, 252, 253));
            canvas.drawRoundRect(roomRect, dp(8), dp(8), paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.5f));
            paint.setColor(COLOR_BORDER);
            canvas.drawRoundRect(roomRect, dp(8), dp(8), paint);
        }

        private void drawGrid(Canvas canvas) {
            if (floorGrid == null || floorGrid.length == 0 || floorGrid[0].length == 0) {
                return;
            }
            int rows = floorGrid.length;
            int cols = floorGrid[0].length;
            paint.setColor(Color.rgb(232, 237, 244));
            paint.setStrokeWidth(dp(1));
            for (int c = 1; c < cols - 1; c++) {
                float x = roomRect.left + (roomRect.width() * c / (cols - 1));
                canvas.drawLine(x, roomRect.top, x, roomRect.bottom, paint);
            }
            for (int r = 1; r < rows - 1; r++) {
                float y = roomRect.top + (roomRect.height() * r / (rows - 1));
                canvas.drawLine(roomRect.left, y, roomRect.right, y, paint);
            }
        }

        private void drawBeacons(Canvas canvas) {
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextSize(sp(11));
            for (BeaconLocation beacon : beaconLocations) {
                double xMeters = CoordinateUtils.beaconXMeters(beacon);
                double yMeters = CoordinateUtils.beaconYMeters(beacon);
                float x = xToPx(xMeters);
                float y = yToPx(yMeters);

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.rgb(55, 104, 170));
                canvas.drawCircle(x, y, dp(6), paint);

                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2));
                paint.setColor(Color.WHITE);
                canvas.drawCircle(x, y, dp(6), paint);

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(COLOR_TEXT);
                canvas.drawText(beacon.getUniqueId(), x + dp(9), y - dp(8), paint);
            }
        }

        private void drawBlePoint(Canvas canvas) {
            if (!Double.isFinite(bleX) || !Double.isFinite(bleY)) {
                return;
            }
            float x = xToPx(bleX);
            float y = yToPx(bleY);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.rgb(222, 150, 45));
            canvas.drawCircle(x, y, dp(12), paint);
        }

        private void drawGroundTruthPoint(Canvas canvas) {
            if (!Double.isFinite(truthX) || !Double.isFinite(truthY)) {
                return;
            }
            float x = xToPx(truthX);
            float y = yToPx(truthY);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(3));
            paint.setColor(Color.rgb(107, 75, 184));
            canvas.drawLine(x - dp(10), y, x + dp(10), y, paint);
            canvas.drawLine(x, y - dp(10), x, y + dp(10), paint);
        }

        private void drawRenderPoint(Canvas canvas) {
            if (!Double.isFinite(renderX) || !Double.isFinite(renderY)) {
                drawNoPosition(canvas);
                return;
            }

            float x = xToPx(renderX);
            float y = yToPx(renderY);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(28, 132, 112));
            canvas.drawCircle(x, y, dp(10), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(3));
            paint.setColor(Color.argb(90, 28, 132, 112));
            canvas.drawCircle(x, y, dp(18), paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextSize(sp(12));
            paint.setColor(Color.WHITE);
            canvas.drawText("U", x - dp(4), y + dp(4), paint);
        }

        private void drawNoPosition(Canvas canvas) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(224, 255, 255, 255));
            RectF messageRect = new RectF(
                    roomRect.left + dp(18),
                    roomRect.centerY() - dp(40),
                    roomRect.right - dp(18),
                    roomRect.centerY() + dp(40)
            );
            canvas.drawRoundRect(messageRect, dp(8), dp(8), paint);

            paint.setColor(COLOR_TEXT);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextSize(sp(15));
            drawCenteredText(canvas, "No position fix", messageRect.centerX(), messageRect.centerY() - dp(8));

            paint.setColor(COLOR_MUTED);
            paint.setTypeface(Typeface.DEFAULT);
            paint.setTextSize(sp(12));
            drawCenteredText(canvas, reason, messageRect.centerX(), messageRect.centerY() + dp(14));
        }

        private void drawLegend(Canvas canvas) {
            float y = getHeight() - getPaddingBottom() - dp(14);
            float x = getPaddingLeft();
            drawLegendItem(canvas, x, y, Color.rgb(55, 104, 170), "Beacon");
            drawLegendItem(canvas, x + dp(96), y, Color.rgb(28, 132, 112), "Render");
            drawLegendItem(canvas, x + dp(192), y, Color.rgb(222, 150, 45), "BLE");
        }

        private void drawLegendItem(Canvas canvas, float x, float y, int color, String label) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            canvas.drawCircle(x + dp(6), y - dp(4), dp(5), paint);
            paint.setColor(COLOR_MUTED);
            paint.setTypeface(Typeface.DEFAULT);
            paint.setTextSize(sp(11));
            canvas.drawText(label, x + dp(16), y, paint);
        }

        private void drawCenteredText(Canvas canvas, String text, float centerX, float baselineY) {
            float width = paint.measureText(text);
            canvas.drawText(text, centerX - width / 2f, baselineY, paint);
        }

        private float xToPx(double xMeters) {
            double clamped = clamp(xMeters, 0.0, JsonOps.roomWidthMeters);
            double ratio = JsonOps.roomWidthMeters <= 0.0 ? 0.0 : clamped / JsonOps.roomWidthMeters;
            return roomRect.left + (float) (ratio * roomRect.width());
        }

        private float yToPx(double yMeters) {
            double clamped = clamp(yMeters, 0.0, JsonOps.roomHeightMeters);
            double ratio = JsonOps.roomHeightMeters <= 0.0 ? 0.0 : clamped / JsonOps.roomHeightMeters;
            return roomRect.top + (float) (ratio * roomRect.height());
        }

        private double pxToXMeters(float xPx) {
            double ratio = roomRect.width() <= 0.0f ? 0.0 : (xPx - roomRect.left) / roomRect.width();
            return clamp(ratio, 0.0, 1.0) * JsonOps.roomWidthMeters;
        }

        private double pxToYMeters(float yPx) {
            double ratio = roomRect.height() <= 0.0f ? 0.0 : (yPx - roomRect.top) / roomRect.height();
            return clamp(ratio, 0.0, 1.0) * JsonOps.roomHeightMeters;
        }

        private float sp(float value) {
            return TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP,
                    value,
                    getResources().getDisplayMetrics()
            );
        }
    }

    private int dp(float value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

}
