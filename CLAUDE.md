# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an Android application for thermal printer integration, specifically targeting Woosim DP230L and other thermal printers. The app serves as a bridge for external applications to print receipts, labels, and barcodes via Intent-based communication.

## Architecture

### Core Components
- **MainActivity**: Entry point that handles Intent-based printing requests from external apps
- **MainPresenter**: Contains all business logic for printer operations using MVP pattern  
- **BaseApp**: Application class that initializes Sunmi printer services

### Printer Support
The app supports multiple printer brands through dedicated libraries:
- **Woosim printers**: Native Woosim DP230L support via `WoosimLib261.jar`
- **Zebra printers**: ZPL command support via `ZSDK_ANDROID_API.jar`
- **ESC/POS printers**: General thermal printer support via ESC/POS commands
- **Sunmi printers**: Built-in Sunmi device support
- **Honeywell printers**: O'Neil printer support via `oneil_lib_2.4.9.aar`
- **Dascom printers**: Support via `dascom2.5.4.6620.jar`

## Development Commands

### Build Commands
```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK  
./gradlew assembleRelease

# Clean build
./gradlew clean

# Install debug on connected device
./gradlew installDebug
```

### Testing
The project uses standard Android testing framework:
```bash
# Run unit tests
./gradlew test

# Run instrumented tests on device
./gradlew connectedAndroidTest
```

## Key Configuration

### Dependencies
- **Target SDK**: 33
- **Min SDK**: 21
- **ViewBinding**: Enabled
- **Java Version**: 1.8

### Required Permissions
The app requires Bluetooth permissions for printer connectivity:
- `BLUETOOTH` and `BLUETOOTH_ADMIN` (legacy, maxSdkVersion 30)
- `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` (API 31+)
- Location permissions for Bluetooth device discovery

## Intent-Based API

External applications communicate with this printer app via Intents with these parameters:

### Basic Printing
- `PRINTER_CODE`: Specifies printer type ("SUNMI_V2" for Sunmi printers)
- `ACTION_PRINT`: Print action type
- `TXT_TO_PRINT`: Single text to print
- `ARR_TO_PRINT`: String array for multiple print commands

### Printer Types
- **Woosim**: Default native printing using WoosimCmd
- **Sunmi**: Built-in thermal printer on Sunmi devices  
- **ZPL**: Zebra printer label format
- **ESC/POS**: Standard thermal printer commands
- **O'Neil**: Honeywell portable printer support

## Code Structure

### Package Organization
```
net.simplr.woosimdp230l/
├── MainActivity.java           # Main activity handling Intents
├── MainPresenter.java         # Core business logic and printer operations
├── AdapterDevice.java         # Bluetooth device list adapter
├── BluetoothCustom.java       # Custom Bluetooth connection handling
├── CounterManger.java         # Print counter management
├── base/
│   └── BaseApp.java          # Application initialization
└── sunmi/                    # Sunmi printer utilities
    ├── SunmiPrintHelper.java # Sunmi printer service wrapper
    ├── BluetoothUtil.java    # Bluetooth utility functions
    ├── BitmapUtil.java       # Image processing utilities
    ├── BytesUtil.java        # Byte array utilities
    └── ESCUtil.java          # ESC/POS command utilities
```

### Shared Preferences
- File: "woosimdp230lmac"
- Key: "macaddress" - Stores selected Bluetooth printer MAC address
- Key: "recordPrint" - Stores pending print records

## Bluetooth Connection Flow

1. Check for saved MAC address in SharedPreferences
2. If none saved, scan for paired Bluetooth devices
3. Present device list to user for selection
4. Save selected MAC address for future use
5. Establish connection and execute print commands
6. Close connection and return result to calling app

## Development Notes

- The app uses MVP pattern with MainActivity as View and MainPresenter containing business logic
- All printer operations run on background threads with UI updates on main thread
- Intent parameters are processed in `processData()` method
- Bluetooth permissions are requested at runtime using PermissionX library
- The app automatically closes after printing completion or error

## External Library Integration

The project includes several JAR/AAR files in `/libs` for printer SDK integration. These handle low-level printer communication protocols and should be updated when newer versions become available from printer manufacturers.