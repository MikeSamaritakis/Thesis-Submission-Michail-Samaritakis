# Thesis Documentation: Android BLE iBeacon Simulator

## Abstract

This application implements an Android based simulator for Bluetooth Low Energy iBeacon advertisements. It was developed to support Bachelor's thesis experimentation by providing configurable BLE beacon identity parameters during testing, demonstration, and validation activities.

## Role in Thesis Methodology

The main thesis project is separate from this repository. The main thesis project is OIPASFT. This repository was used only as a helper tool. The BLE Simulator provided controlled iBeacon advertisement inputs for testing, demonstration, and validation. The simulator is not the primary thesis contribution, and it should not be treated as a calibrated RF reference instrument.

In this context, the simulator is intended to reproduce beacon identity data, such as UUID, major, minor, and measured power fields, rather than to provide laboratory grade radio frequency measurements. Android smartphones differ in Bluetooth chipset, antenna design, firmware behaviour, transmission power control, and operating system handling of BLE advertising. As a result, the emitted radio signal and the RSSI observed by a scanner may vary between devices and environments even when the same iBeacon parameters are configured. The tool is therefore suitable for validating detection logic and repeatable beacon identity configuration, but it is not suitable for deriving calibrated propagation models, comparing absolute transmission power, or treating RSSI values as measurements from a certified RF source.

The BLE Simulator helper tool was executed on Android smartphones used as simulated beacon advertisers. The Samsung Galaxy A15 and Xiaomi Redmi Note 10 were used as devices capable of running the BLE Simulator application and advertising configurable iBeacon payloads. The Ulefone Armor 22 was used as the scanner and detector device running the custom thesis application OIPASFT. In some tests, only one smartphone was used at a time as a simulated BLE beacon. In other tests, the setup combined physical BLE beacons and smartphones running the BLE Simulator software.

## Implementation Overview

The application is implemented as a single Android activity using Jetpack Compose for the user interface. The BLE advertising functionality is provided by Android's `BluetoothLeAdvertiser` API. When the user starts advertising, the application validates the entered beacon parameters, checks Bluetooth capability and permission state, constructs the iBeacon manufacturer payload, and submits a BLE advertisement request that is not connectable.

The simulator is designed for foreground experimental use. Advertising stops when the user presses the stop control or when the activity is destroyed. This behaviour makes the advertising period explicit and easier to correlate with experimental logs.

## iBeacon Data Model

The transmitted iBeacon payload contains the following fields.

1. Fixed iBeacon prefix.
2. Proximity UUID.
3. Major value.
4. Minor value.
5. Measured power value.

The application also exposes a Beacon ID field. This field is used as a local human readable label in the interface and in Logcat, but it is not a transmitted iBeacon field. External scanners should therefore verify the simulated beacon through UUID, major, minor, and measured power.

Sample defaults:

```text
Beacon ID: SamA15
UUID: f7826da6-4fa2-4e98-8024-bc5b71e0893e
Major: 20641
Minor: 50361
Measured power: -80
TX power level: 3
```

These values are configurable experimental parameters and may be changed for each device or test session.

## Experimental Devices

The following Android devices were involved in thesis experimentation.

1. Samsung Galaxy A15: beacon advertiser running the BLE Simulator application, Android 14.
2. Xiaomi Redmi Note 10: beacon advertiser running the BLE Simulator application, Android 13.
3. Ulefone Armor 22: scanner and detector running OIPASFT, Android 13.

The devices were not always used in the same configuration. In some tests, one smartphone running the BLE Simulator was used as the simulated beacon. In other tests, smartphones running the BLE Simulator were used together with physical BLE beacons. The Ulefone Armor 22 was used as the scanner and detector device.

## Logcat Instrumentation

The application records operational events under the following Logcat tag.

```text
BLEBeaconSimulator
```

Recommended observation command:

```powershell
adb logcat -s BLEBeaconSimulator
```

The logging strategy supports experimental traceability by recording the following events.

1. Activity creation and destruction.
2. Start and stop advertising requests.
3. Runtime Bluetooth permission results.
4. Invalid configuration values.
5. Bluetooth adapter availability and state.
6. BLE advertising capability checks.
7. Advertisement start success.
8. Advertisement start failure with Android error code interpretation.
9. Advertisement shutdown.

No signing credentials, passwords, private keystore information, or sensitive secrets should be logged.

## Expected Successful Log Sequence

A typical successful session should contain entries similar to the following example.

```text
I/BLEBeaconSimulator: Activity created for BLE beacon simulation.
I/BLEBeaconSimulator: User requested advertising start.
D/BLEBeaconSimulator: All required runtime Bluetooth permissions are granted.
I/BLEBeaconSimulator: Validated beacon configuration: beaconId=SamA15, uuid=f7826da6-4fa2-4e98-8024-bc5b71e0893e, major=20641, minor=50361, measuredPower=-80, txPowerLevel=3
D/BLEBeaconSimulator: Bluetooth adapter detected.
D/BLEBeaconSimulator: Bluetooth adapter is enabled.
D/BLEBeaconSimulator: BLE multiple advertisement support is available.
I/BLEBeaconSimulator: Submitting iBeacon advertisement request: beaconId=SamA15, uuid=f7826da6-4fa2-4e98-8024-bc5b71e0893e, major=20641, minor=50361, measuredPower=-80, txPowerLevel=3
I/BLEBeaconSimulator: Advertising started successfully: beaconId=SamA15, uuid=f7826da6-4fa2-4e98-8024-bc5b71e0893e, major=20641, minor=50361, measuredPower=-80, txPowerLevel=3
I/BLEBeaconSimulator: Stopping BLE advertisement due to: user request.
I/BLEBeaconSimulator: Advertising state cleared.
```

## Common Failure Cases

If Bluetooth is disabled:

```text
W/BLEBeaconSimulator: Bluetooth adapter is present but disabled.
```

If the device does not support BLE advertising:

```text
W/BLEBeaconSimulator: BLE multiple advertisement support is not available on this device.
```

If an invalid UUID is entered:

```text
W/BLEBeaconSimulator: Configuration validation failed: invalid UUID value '<entered value>'.
```

If Android rejects the advertisement request:

```text
E/BLEBeaconSimulator: Advertising failed with code <code> (<interpreted reason>).
```

## Reproducibility Notes

For reproducible measurements, the following information should be recorded.

1. Date of experiment.
2. Advertising device used: Samsung Galaxy A15 or Xiaomi Redmi Note 10.
3. Android version of the advertising device: Android 14 for Samsung Galaxy A15 or Android 13 for Xiaomi Redmi Note 10.
4. Scanner and detector device: Ulefone Armor 22.
5. Android version of the scanner and detector device: Android 13.
6. Beacon ID.
7. UUID, major, minor, measured power, and TX power level.
8. Whether physical BLE beacons were also used in the test.
9. Approximate physical placement of the simulated beacon device.
10. Logcat output for the advertising session.
11. Scanner and detector evidence from OIPASFT.

RSSI values observed by receivers are expected to vary because of device orientation, antenna placement, environmental interference, multipath propagation, and differences between device chipsets. For this reason, RSSI values collected with this helper tool should be interpreted as practical experimental observations from the tested smartphone setup, not as calibrated RF measurements. When RSSI is discussed in the thesis, it should be presented together with the device model, placement, environment, and scanner configuration used during the experiment.

Repository level validation support files are provided in the `validation/` directory. These files are intended to store device information, validation notes, and real Logcat evidence without creating artificial test results.
