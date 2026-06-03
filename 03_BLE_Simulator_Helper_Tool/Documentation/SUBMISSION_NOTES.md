# Submission Notes

This document describes the role of the BLE Simulator repository within the Bachelor thesis submission package. The repository should be treated as a supporting research artifact rather than as the principal thesis implementation.

## Artifact Status

The BLE Simulator is an auxiliary Android application developed to support experimental work involving Bluetooth Low Energy iBeacon advertisements. Its purpose was to provide configurable beacon identity parameters during the testing and observation of the main thesis application.

The main thesis implementation is a separate Android Studio project named OIPASFT. OIPASFT was used as the scanner and detector application, while the BLE Simulator was used on selected Android smartphones as a controllable iBeacon advertisement source.

The BLE Simulator should therefore be presented as a helper tool that contributed to experimental repeatability and evidence collection. It should not be described as the primary software contribution of the thesis.

## Confirmed Device Roles

1. Samsung Galaxy A15: Android 14, BLE Simulator advertiser.

2. Xiaomi Redmi Note 10: Android 13, BLE Simulator advertiser.

3. Ulefone Armor 22: Android 13, scanner and detector device running OIPASFT.

The experimental arrangement was not fixed across all observations. In some cases, a single smartphone running the BLE Simulator application was used as the simulated BLE beacon. In other cases, smartphones running the BLE Simulator application were used within a mixed beacon environment that also contained physical BLE beacons. The Ulefone Armor 22 served as the scanner and detector device for the OIPASFT application.

## Recommended Placement In The Thesis Delivery Package

```text
Thesis_Submission_Samaritakis/
  01_Thesis_Document/
  02_Main_Thesis_Project/
  03_BLE_Simulator_Helper_Tool/
    Source_Code/
    APK/
    Documentation/
    Evidence/
  04_Experiment_Evidence/
```

The `03_BLE_Simulator_Helper_Tool` directory should contain the source code, documentation, optional installable APK, and supporting evidence that explains how the helper tool was used.

## Recommended Included Materials

The following materials are appropriate for inclusion with the BLE Simulator helper tool:

1. Full Android Studio project source code.

2. `Documentation/README.md`.

3. `Documentation/THESIS_DOCUMENTATION.md`.

4. `Documentation/SUBMISSION_NOTES.md`.

5. An installable APK, if required by the supervisor or examiner.

6. `Evidence/DEVICE_INFORMATION.md`.

7. `Evidence/VALIDATION_NOTES.md`.

8. Concise Logcat evidence showing the relevant scanner and detector observations.

9. Full Logcat exports, if they have been reviewed for sensitive metadata.

10. Screenshots from the BLE Simulator and OIPASFT, where available.

## Materials That Should Be Excluded

The following materials should not be included in a public repository or general submission copy:

1. `local.properties`.

2. Keystore files, including files with `.jks` or `.keystore` extensions.

3. Signing passwords.

4. API keys or service credentials.

5. Generated build folders.

6. IDE cache folders.

7. Signed APK metadata created by Android Studio outside the selected delivery APK.

If a signed APK is required for installation, the APK may be placed in the delivery package under `03_BLE_Simulator_Helper_Tool/APK/`. The signing keystore and credentials must remain private.

## Evidence Handling

Evidence files should be presented as supporting material for the experimental use of the helper tool. They should not be modified in a way that changes the meaning of the observations. If redaction is necessary, the redacted file should be clearly labelled, and an unmodified original should be retained privately.

The concise Logcat excerpt may be used to show the relevant detection sequence in a readable form. The full Logcat export may be retained as supporting material, provided that it has been reviewed for device identifiers, addresses, file paths, or other metadata that should not be disclosed.

## Suggested Description For Submission

The BLE Simulator repository contains an auxiliary Android application used during thesis experimentation to generate configurable Bluetooth Low Energy iBeacon advertisements. The application was executed on Android smartphones acting as simulated beacon advertisers. The main thesis application, OIPASFT, was executed on the Ulefone Armor 22 and used as the scanner and detector application. The BLE Simulator provided repeatable beacon identity parameters, including UUID, major value, minor value, measured power, and advertising power level, thereby supporting controlled observation and documentation of scanner behaviour.

This repository should be submitted as a supplementary software artifact associated with the experimental methodology of the thesis.
