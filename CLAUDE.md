# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Android app (`net.simplr.woosimdp230l`, versionName 1.6) that acts as a **headless print bridge**: external apps fire an Intent at `MainActivity`, the app connects to a Bluetooth thermal printer, prints, returns a result, and closes itself. There is effectively no interactive UI beyond a one-time device picker and loading/printing animations.

Despite the package name (`woosimdp230l`), the **current primary use case is printing warehouse receiving labels to Zebra printers via ZPL**. The Woosim/Honeywell/ESC-POS/Dascom code paths still exist but are dormant in the active Intent flow (see "Active vs. dormant paths" below). The branch `connection-update` is focused on Zebra Bluetooth connection reliability.

## Build & Test

```bash
# Bash (Git Bash). On Windows PowerShell use .\gradlew.bat instead of ./gradlew
./gradlew assembleDebug      # debug APK
./gradlew assembleRelease    # release APK (minifyEnabled = false)
./gradlew installDebug       # install on connected device
./gradlew clean

./gradlew test                                                   # JVM unit tests
./gradlew test --tests "net.simplr.woosimdp230l.ExampleUnitTest" # single test class
./gradlew connectedAndroidTest                                   # instrumented (needs device)
```

Config: minSdk 21, compile/target SDK 33, Java 1.8, ViewBinding on. Most printer SDKs are local JAR/AAR in `app/libs/` (Woosim, Zebra ZSDK, Dascom, O'Neil); ESC/POS, ZXing, Sunmi, PermissionX, Lottie come from Maven. There is essentially no real test coverage — `ExampleUnitTest` is the default stub.

## Architecture

MVP pattern: `MainActivity` is the View (`implements MainPresenter.View`), `MainPresenter` holds all printer logic. `MainActivity` is `singleTop`, so repeated print Intents arrive via `onNewIntent()` → `processData()`.

### Intent dispatch (`MainActivity.processData`)
The entry contract. Recognized extras: `PRINTER_CODE`, `ACTION_PRINT`, `TXT_TO_PRINT`, `ARR_TO_PRINT` (String[]), `MACADDRESS`, `PRINTERNAME`. Routing:

1. **`ARR_TO_PRINT` non-empty** → request BT permissions → if `MACADDRESS` present, `presenter.printZPL(arrArgs, mac, printerName)`. **This is the live path.** `MACADDRESS` is required (no fallback to saved MAC here).
2. **`PRINTER_CODE == "SUNMI_V2"`** → `presenter.processSunmiData(arrArgs)` (built-in Sunmi printer).
3. **otherwise** → toast "Need to call from external application" and `finishAffinity()`.

On cold start, `onCreate` requests permissions then calls `verifyESCPOS()`: if a MAC is already saved in SharedPreferences it proceeds to print (`showESCTesting()` → `processData()`); if not, it shows the bonded-device picker (`registerBluetooth()`) so the user selects+saves a printer once.

### Result protocol
The app reports back via `setResult` + `finish()`/`finishAffinity()` in the `View` callbacks (`closeActivity`, `onComplete`, `isConnected`). Extras used on the result Intent vary by callback (`isSuccess`/`message`, or `selected_value="sukses"`). The app always self-terminates after a print attempt.

### Threading
- **ZPL path** (`printZPL`, `createZPLTest`): `Executors.newSingleThreadExecutor()` for the connect+send work, results posted back via a main-looper `Handler`. This is the correct/modern path.
- **Woosim native path** (`doPrint`): uses a **busy-wait** (`while (!futureTask.isDone())`) on a cached thread pool — a CPU-spinning anti-pattern. Dormant, but don't copy it.

## The ZPL label (core domain logic)

`printZPL` → `buildZPLCommands` → `createZPLLabel`. Each entry in `ARR_TO_PRINT` is **one label**, encoded as a **`;`-delimited string of 14 or 15 fields**. Rows with `< 14` fields are skipped. Field index → meaning (0-based):

| idx | field          | idx | field            |
|-----|----------------|-----|------------------|
| 2   | received date  | 9   | pallet id        |
| 3   | qty            | 10  | expiry date      |
| 4   | uom            | 11  | location code    |
| 5   | lot no         | 13  | PO no            |
| 6   | sku            | 14  | received time (only if 15 fields) |
| 7   | description    |     |                  |

Indexes 0, 1, 8, 12 are present in the delimited string but unused by the label (see the sample in `createZPLTest`). The SKU (idx 6) and pallet id (idx 9) are also rendered as Code 128 barcodes.

**Three layout variants**, selected inside `createZPLLabel`:
- `isThreeInch == true` → 3-inch layout, `^PW609`.
- `printerName.equalsIgnoreCase("YELLOW")` → 4-inch "yellow" layout, `^PW812`, decorative corner-bracket borders. Selected by passing `PRINTERNAME=YELLOW` in the Intent.
- default → standard 4-inch layout, `^PW812`, solid outer border.

When editing layouts, edit the matching branch only — the three are independent literal ZPL builders and share nothing but the field indices. `createZPLTest()` is a **dev-only** helper with a hardcoded sample row and hardcoded MAC `90:75:DE:17:58:19`; it's wired to a commented line in `MainActivity.onCreate`. Don't ship it enabled.

## Zebra connection reliability (`connectZebra`)

The recent work on this branch. `connectZebra(overrideMac)` retries up to **3 times with 500ms backoff** to recover from the Android BT-classic SDP race (`"read failed... read ret: -1"`). Each attempt cancels active discovery and closes/nulls any stale `zebraConn`/`instance` before reopening. It throws the last `ConnectionException` if all attempts fail. `handlePrintError` → `friendlyMessageFor` maps exceptions to user-facing recovery steps (power-cycle, move closer, re-pair). Preserve this retry+cleanup shape when touching Zebra connect logic.

## Active vs. dormant paths

Don't waste time treating all printer brands as live — only two are reachable from the current Intent dispatch:

- **Active:** Zebra/ZPL (`printZPL`), Sunmi (`processSunmiData` / `SunmiPrintHelper`).
- **Dormant (defined but not invoked by `MainActivity`):** Woosim native (`processArgument`/`doPrint`/`doRecursivePrintingNative`, uses `windows-874` Thai encoding via `WoosimCmd`), O'Neil/Honeywell (`processOneilData`), generic ESC/POS (`printESCText`/`printESCImage`, commented out), Dascom (`ZPL` field declared, unused).

If asked to "add a printer" or "fix printing", confirm which path is meant before assuming — the live one is almost always Zebra ZPL.

## State & persistence

SharedPreferences file `"woosimdp230lmac"`:
- `"macaddress"` — selected printer MAC (set once via the device picker, reused on later prints).
- `"recordPrint"` — pending Woosim native print records (dormant path only).

`CounterManger`/`Counter` exist for print counting but are not part of the active ZPL flow.

## Permissions

Runtime-requested via PermissionX, branched by SDK in `getBluetoothPermission()`:
- API 31+ (Android 12+): `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`.
- API ≤30: `BLUETOOTH`, `BLUETOOTH_ADMIN`, `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`.

Manifest declares legacy `BLUETOOTH`/`BLUETOOTH_ADMIN` with `maxSdkVersion="30"`.

## Git conventions

Conventional Commits, lowercase imperative (e.g. `fix: retry zebra connect with cleanup`). Branches `type/short-description`. Don't push or amend without asking.
