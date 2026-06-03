# BLE Simulator Helper Tool

The BLE Simulator is an auxiliary Android application used during thesis experimentation to advertise configurable iBeacon payloads from Android smartphones. It supported controlled BLE observations for the main OIPASFT prototype.

This tool is not the primary thesis contribution. It should be reviewed as supporting experimental infrastructure.

## What To Review

1. `THESIS_DOCUMENTATION.md`
   - Formal description of the helper tool and its role in the thesis work.

2. `SUBMISSION_NOTES.md`
   - Notes explaining how this helper tool fits into the submission package.

3. `../Evidence/`
   - Device information, validation notes, screenshots, and Logcat evidence.

4. `../src/BLE_Simulator/`
   - Android Studio source project for the helper application.

5. `../APK/app-release.apk`
   - Installable helper-tool APK.

## Experimental Role

The simulator was used to transmit configurable iBeacon parameters during selected tests. The main OIPASFT application ran on the scanner/detector device and observed these advertisements alongside, or instead of, physical BLE beacons.

Documented device roles:

- Samsung Galaxy A15, Android 14: BLE Simulator advertiser.
- Xiaomi Redmi Note 10, Android 13: BLE Simulator advertiser.
- Ulefone Armor 22, Android 13: OIPASFT scanner/detector.

## Configurable Beacon Parameters

- UUID
- Major value
- Minor value
- Measured power
- Android advertising power level
- Local beacon label used by the application UI and logs

The local beacon label is not part of the transmitted iBeacon frame.

## Limitations

- BLE advertising support depends on the phone hardware and firmware.
- RSSI is affected by distance, orientation, antenna placement, interference, and multipath effects.
- The simulator is a practical configurable beacon source, not a calibrated RF reference instrument.

## Evidence Handling

Logcat exports and screenshots are retained as supporting evidence. They should be reviewed before public upload because device logs may contain metadata.
