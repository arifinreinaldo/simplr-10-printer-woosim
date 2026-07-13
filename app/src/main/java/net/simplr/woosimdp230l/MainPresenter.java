package net.simplr.woosimdp230l;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;

import com.dantsu.escposprinter.EscPosPrinter;
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection;
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections;
import com.dantsu.escposprinter.exceptions.EscPosBarcodeException;
import com.dantsu.escposprinter.exceptions.EscPosConnectionException;
import com.dantsu.escposprinter.exceptions.EscPosEncodingException;
import com.dantsu.escposprinter.exceptions.EscPosParserException;
import com.dantsu.escposprinter.textparser.PrinterTextParserImg;
import com.dascom.print.ZPL;
import com.dascom.print.utils.BluetoothUtils;
import com.woosim.printer.WoosimCmd;
import com.zebra.sdk.comm.ConnectionException;
import com.zebra.sdk.printer.PrinterStatus;
import com.zebra.sdk.printer.ZebraPrinter;
import com.zebra.sdk.printer.ZebraPrinterFactory;
import com.zebra.sdk.printer.ZebraPrinterLanguageUnknownException;

import net.simplr.woosimdp230l.sunmi.SunmiPrintHelper;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import honeywell.connection.ConnectionBase;
import honeywell.connection.Connection_Bluetooth;
import honeywell.printer.DocumentEZ;
import honeywell.printer.DocumentLP;

public class MainPresenter {
    boolean isThreeInch = false;
    String TAG = "Dascom";
    private BluetoothCustom mIConnection;
    private ZPL zpl;
    private View view;
    private SharedPreferences spData;
    private
    SharedPreferences.Editor editor;
    private final String sp_rec = "recordPrint";
    String oldRecord = "";
    final String separator = "@@";
    ArrayList<String> records = new ArrayList<>();
    private final String sp_mac = "macaddress";


    Handler handler;

    String paramMAC = "";
    String receiptNo = "";
    String receiptDate = "";
    String qty = "";
    String uom = "";
    String lotNo = "";
    String itemCode = "";
    String itemName = "";
    String itemBarcode = "";
    String palletNo = "";
    int index = 0;
    String mac = "";
    int retry = 0;
    ZebraPrinter instance;
    com.zebra.sdk.comm.BluetoothConnection zebraConn;
    // Guards against a second print Intent starting a concurrent ZPL job, which can
    // double-print the same label. STATIC on purpose: startActivityForResult always
    // creates a new MainActivity (singleTop is bypassed when a result is expected),
    // so each tap gets a new presenter — a per-instance guard would never trip.
    private static final AtomicBoolean zplJobRunning = new AtomicBoolean(false);

    MainPresenter(View view, SharedPreferences sp) {
        this.view = view;
        spData = sp;
        editor = spData.edit();
    }

    void onDestroy() {

    }

    void addSinglePrint(String record) {
        records.add(record);
        doPrint();
    }

    void doPrint() {
        index = 0;
        Log.d(TAG, "doPrint: " + records.size());
        if (records.size() > 0) {
            String first = records.get(0);
            mac = first.split(";")[0].trim();
            String savedMac = spData.getString(sp_mac, "");
            mac = savedMac;
            try {
                ExecutorService threadpool = Executors.newCachedThreadPool();
                Future<Boolean> futureTask = threadpool.submit(() -> connectBluetoothNative(mac));

                while (!futureTask.isDone()) {
                    System.out.println("FutureTask is not finished yet...");
                }
                Boolean result = futureTask.get();
                if (result) {
                    doRecursivePrintingNative();
                } else {
                    view.closeActivity(false, "Failed to Connect");
                }
                threadpool.shutdown();
            } catch (Exception e) {
                view.closeActivity(false, e.getMessage());
            }
        }
    }

    private static BluetoothAdapter getAdapter() {
        return BluetoothAdapter.getDefaultAdapter();
    }

    private static BluetoothDevice getBluetoothDevice(String mac) {
        BluetoothAdapter adapter = getAdapter();
        String tempMac = mac.toUpperCase();
        return adapter != null && BluetoothAdapter.checkBluetoothAddress(tempMac) ? adapter.getRemoteDevice(tempMac) : null;
    }

    Boolean connectBluetoothNative(String address) {
        retry++;
        mIConnection = new BluetoothCustom(getBluetoothDevice(address));
        return mIConnection.connect();
    }

    void doRecursivePrintingNative() {
        try {
            String value = records.get(index);
            Log.d(TAG, "doRecursivePrinting: " + value);
            final byte[] cmd_print = WoosimCmd.printData();
            byte[] valByte = value.getBytes("windows-874");
            System.out.println("printText value : " + value + " -- " + value.getBytes().length);
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream(512);
            byteStream.write(WoosimCmd.setCodeTable(WoosimCmd.MCU_RX, WoosimCmd.CT_WIN874, WoosimCmd.FONT_LARGE));
            byteStream.write(valByte);
            byteStream.write(cmd_print);

            mIConnection.send(WoosimCmd.initPrinter(), 0, 0);
            mIConnection.send(byteStream.toByteArray(), 0, 0);
            Thread.sleep(100);
            index++;
            if (index < records.size()) {
                doRecursivePrintingNative();
            } else {
                editor.putString(sp_rec, "");
                editor.apply();
                records.clear();
                doDisconnect();
            }
        } catch (Exception e) {
            view.closeActivity(false, e.getMessage());
        }
    }

    public void doDisconnect() {
        try {
            mIConnection.disconnect();
            view.closeActivity(true, "Done Print");
        } catch (Exception e) {
            view.closeActivity(false, e.getMessage());
        }
    }

    public void clearRecord() {
        editor.putString(sp_rec, "");
        editor.apply();
    }

    public void addRecord(String value) {
        records.add(value);
    }

    public void doPrintAll() {
        if (records.size() == 0) {
            view.closeActivity(false, "No Data to Print");
        } else {
            doPrint();
        }

    }

    public void processArray(String[] values) {
        for (int x = 0; x < values.length; x++) {
            addRecord(values[x]);
        }
        doPrintAll();
    }

    public void processArgument(String action, String value) {
        if (action.equalsIgnoreCase("SinglePrint")) {
            if (value != null) {
                addSinglePrint(value);
            }
        } else {
            if (action.equalsIgnoreCase("startPrint")) {
                clearRecord();
            } else if (action.equalsIgnoreCase("addPrintRecord")) {
                addRecord(value);
            } else if (action.equalsIgnoreCase("doPrint")) {
                doPrintAll();
            }
        }
    }

    public void saveBluetoothAddress(String address) {
        try {
            editor.putString(sp_mac, address);
            editor.apply();
            Thread.sleep(100);
        } catch (Exception e) {

        } finally {
            view.onComplete();
        }
    }

    public void getBluetoothDevice() {
        try {
            view.showLoading();
            Set<BluetoothDevice> data = BluetoothUtils.getBondedDevices();
            view.showBluetoothData(new ArrayList<>(data));
            view.hideLoading();
        } catch (Exception e) {

        }
    }

    public void processOneilData(String[] arrArgs) {
        Long start = System.currentTimeMillis();
        byte[] printData = {0};
        DocumentEZ docEZ = new DocumentEZ("MF185");
        DocumentLP docLP = new DocumentLP("!");
        //=============GENERATING RECEIPT====================================//
        try {
            //Record:TEXT:kalimat nya:TEXT:X:TEXT:Y
            String savedMac = spData.getString(sp_mac, "");
            ConnectionBase conn = Connection_Bluetooth.createClient(savedMac, false);
            for (int i = 0; i < arrArgs.length; i++) {
                String record = arrArgs[i];
                if (record.startsWith("Image")) {
                    String recordData[] = record.split(":IMAGE:");
                    Bitmap bmp = view.getAssetData(recordData[1]);
                    if (bmp != null) {
                        try {
                            docLP.writeImage(bmp, 832);
                        } catch (Exception e) {
                            Log.d(TAG, "processOneilData: ");
                        }
                        printData = docLP.getDocumentData();
                        if (!conn.getIsOpen()) {
                            conn.open();
                        }
                        int bytesWritten = 0;
                        int bytesToWrite = 1024;
                        int totalBytes = printData.length;
                        int remainingBytes = totalBytes;
                        while (bytesWritten < totalBytes) {
                            if (remainingBytes < bytesToWrite)
                                bytesToWrite = remainingBytes;
                            //Send data, 1024 bytes at a time until all data sent
                            conn.write(printData, bytesWritten, bytesToWrite);
                            bytesWritten += bytesToWrite;
                            remainingBytes = remainingBytes - bytesToWrite;
                            Thread.sleep(100);
                        }
                    }
                } else if (record.startsWith("Record")) {
                    String recordData[] = record.split(":TEXT:");
                    double xDouble = Double.parseDouble(recordData[2]);
                    double yDouble = Double.parseDouble(recordData[3]);
                    int x = (int) Math.floor(xDouble);
                    int y = (int) Math.floor(yDouble);
                    docEZ.writeText(recordData[1], x, y);
                }
            }
            printData = docEZ.getDocumentData();
            if (!conn.getIsOpen()) {
                conn.open();
            }
            int bytesWritten = 0;
            int bytesToWrite = 1024;
            int totalBytes = printData.length;
            int remainingBytes = totalBytes;
            while (bytesWritten < totalBytes) {
                if (remainingBytes < bytesToWrite)
                    bytesToWrite = remainingBytes;

                //Send data, 1024 bytes at a time until all data sent
                conn.write(printData, bytesWritten, bytesToWrite);
                bytesWritten += bytesToWrite;
                remainingBytes = remainingBytes - bytesToWrite;
                Thread.sleep(100);
            }

            //signals to close connection
            conn.close();
            Long end = System.currentTimeMillis();
            Log.d(TAG, "processOneilData: " + (end - start));
            view.closeActivity(true, "Done Print");
        } catch (Exception e) {
            view.showError(e.getMessage());
        }
    }

    public void processSunmiData(String[] arrArgs) {
        if (SunmiPrintHelper.getInstance().sunmiPrinter == SunmiPrintHelper.NoSunmiPrinter) {
            view.showError("No Sunmi Printer");
        } else if (SunmiPrintHelper.getInstance().sunmiPrinter == SunmiPrintHelper.CheckSunmiPrinter) {
            view.showError("Connecting");
            handler = new Handler();
            handler.postDelayed(() -> processSunmiData(arrArgs), 2000);
//            handler.postDelayed(new Runnable() {
//                @Override
//                public void run() {
//                    setSubTitle();
//                }
//            }, 2000);
        } else if (SunmiPrintHelper.getInstance().sunmiPrinter == SunmiPrintHelper.FoundSunmiPrinter) {
            view.initSunmiPrinter(arrArgs);
        }
    }

    public void verifyESCPOS() {
        String savedMac = spData.getString(sp_mac, "");
        if (!savedMac.isEmpty()) {
            view.showESCTesting();
        } else {
            view.registerBluetooth();
        }
        ;
    }

    public void printESCText() {
        String savedMac = spData.getString(sp_mac, "");
        BluetoothPrintersConnections bluetoothConnection = new BluetoothPrintersConnections();
        BluetoothConnection[] list = bluetoothConnection.getList();
        BluetoothConnection selectedDevice = null;
        for (int i = 0; i < list.length; i++) {
            BluetoothConnection con = list[i];
            if (con.getDevice().getAddress().equals(savedMac)) {
                selectedDevice = con;
            }
        }

        if (selectedDevice == null) {
            view.showError("Bluetooth is not found");
        } else {
            BluetoothConnection con = null;
            EscPosPrinter printer = null;
            try {
                con = selectedDevice.connect();
                printer = new EscPosPrinter(con, 203, 58, 100);
                printer.printFormattedText("ORDER N°045");
            } catch (EscPosConnectionException esc) {
                view.showError(esc.getMessage());
            } catch (EscPosEncodingException e) {
                view.showError("ESC ENCODING " + e.getMessage());
            } catch (EscPosBarcodeException e) {
                view.showError("ESC Barcode Exc " + e.getMessage());
            } catch (EscPosParserException e) {
                view.showError("ESC Pos Parser " + e.getMessage());
            } finally {
                if (printer != null) {
                    printer.disconnectPrinter();
                }
            }
        }
    }

    public void printESCImage(Context context) {
        String savedMac = spData.getString(sp_mac, "");
        BluetoothPrintersConnections bluetoothConnection = new BluetoothPrintersConnections();
        BluetoothConnection[] list = bluetoothConnection.getList();
        BluetoothConnection selectedDevice = null;
        for (int i = 0; i < list.length; i++) {
            BluetoothConnection con = list[i];
            if (con.getDevice().getAddress().equals(savedMac)) {
                selectedDevice = con;
            }
        }

        if (selectedDevice == null) {
            view.showError("Bluetooth is not found");
        } else {
            BluetoothConnection con = null;
            EscPosPrinter printer = null;
            try {
                con = selectedDevice.connect();
                printer = new EscPosPrinter(con, 203, 58, 100);
                printer.printFormattedText("<img>" + PrinterTextParserImg.bitmapToHexadecimalString(printer, context.getResources().getDrawableForDensity(R.drawable.ic_launcher, DisplayMetrics.DENSITY_MEDIUM)) + "</img>");
            } catch (EscPosConnectionException esc) {
                view.showError(esc.getMessage());
            } catch (EscPosEncodingException e) {
                view.showError("ESC ENCODING " + e.getMessage());
            } catch (EscPosBarcodeException e) {
                view.showError("ESC Barcode Exc " + e.getMessage());
            } catch (EscPosParserException e) {
                view.showError("ESC Pos Parser " + e.getMessage());
            } finally {
                if (printer != null) {
                    printer.disconnectPrinter();
                }
            }
        }
    }

    public String[] wrapText(String sentence, int maxLineLength) {
        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();

        for (String word : sentence.split(" ")) {
            if (currentLine.length() + word.length() + (currentLine.length() == 0 ? 0 : 1) <= maxLineLength) {
                if (currentLine.length() > 0) {
                    currentLine.append(" ");
                }
                currentLine.append(word);
            } else {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            }
        }

        // Add the last line if there's anything left
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        return lines.toArray(new String[0]);
    }

    public String center(String text, int width) {
        if (text.length() >= width) {
            return text; // or truncate if needed
        }

        int totalPadding = width - text.length();
        int paddingStart = totalPadding / 2;
        int paddingEnd = totalPadding - paddingStart;

        return " ".repeat(paddingStart) + text + " ".repeat(paddingEnd);
    }

    // ZPL Template Constants
    private static final String ZPL_INIT = "CT~~CD,~CC^~CT~";
    private static final String ZPL_START = "^XA~TA000~JSN^LT0^MNY^MTD^POI^PMN^LH0,0^PR3,3~SD15^LRN^CI0";
    private static final String ZPL_START_YELLOW = "^XA~TA000~JSN^LT0^MNY^MTD^POI^PMN^LH0,0^PR3,3~SD15^LRN^CI0";
    // BROWN: same standard layout as default, darker burn (~SD30 vs ~SD15) for legibility on dark brown stock.
    private static final String ZPL_START_BROWN = "^XA~TA000~JSN^LT0^MNY^MTD^POI^PMN^LH0,0^PR3,3~SD30^LRN^CI0";
    private static final String ZPL_END = "^XZ";

    /**
     * Label color variant, resolved from the Intent's PRINTERNAME extra. Each variant
     * carries its own start command, whose {@code ~SD} value sets print darkness:
     * BROWN burns darker for legibility on dark brown stock. DEFAULT and BROWN render
     * the standard 4-inch body; YELLOW renders the corner-bracket body.
     */
    private enum LabelVariant {
        DEFAULT(ZPL_START),
        YELLOW(ZPL_START_YELLOW),
        BROWN(ZPL_START_BROWN);

        final String start;

        LabelVariant(String start) {
            this.start = start;
        }

        /**
         * Null-safe: an absent/unknown PRINTERNAME falls back to DEFAULT.
         */
        static LabelVariant from(String printerName) {
            if ("YELLOW".equalsIgnoreCase(printerName)) return YELLOW;
            if ("BROWN".equalsIgnoreCase(printerName)) return BROWN;
            return DEFAULT;
        }
    }

    public void printZPL(String[] paramList, String macAddress, String printerName) {
        if (paramList == null || paramList.length == 0) {
            view.closeActivity(true, "No Data");
            return;
        }

        // A retry Intent arriving during the (slow) connect/retry window must not start
        // a second concurrent job — that is one source of duplicate physical labels.
        if (!zplJobRunning.compareAndSet(false, true)) {
            Log.w(TAG, "printZPL: job already running, ignoring duplicate request");
            // closeActivity, not showError: this duplicate Intent spawned a fresh activity
            // that must finish and answer its caller, or it stays on screen forever.
            view.closeActivity(false, "Still printing - can take up to a minute.\nPlease wait...");
            return;
        }

        view.showPrinting();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler mainHandler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            boolean connected = false;
            int sent = 0;
            int total = 0;
            try {
                List<String> commands = buildZPLCommands(paramList, printerName);
                total = commands.size();

                connectZebra(macAddress);
                connected = true;

                // A paused/head-open/out-of-media printer still buffers ZPL and replays
                // it once fixed — the "error toast, then prints double" report. Refuse
                // to send until the printer is actually ready.
                checkPrinterReady();

                for (String command : commands) {
                    sendZebraCommand(command);
                    sent++;
                }

                // Success must be visible: silent success is what made users re-tap
                // and duplicate labels in the field.
                final String doneMsg = (total == 1)
                        ? "Label sent to printer"
                        : total + " labels sent to printer";
                mainHandler.post(() -> view.closeActivity(true, doneMsg));

            } catch (Exception e) {
                handlePrintError(e, mainHandler, connected, sent, total);
            } finally {
                if (connected) {
                    closeZebraCommand(); // never throws
                }
                zplJobRunning.set(false);
                executor.shutdown();
            }
        });
    }

    /**
     * Throws with a state-specific user message when the printer would accept data
     * without printing it. A sleeping printer wakes on connect but can take a few
     * seconds to report ready, so a transient not-ready/failed query is polled a few
     * times before deciding. Hard faults a human must fix (cover open, out of labels,
     * paused) fail immediately. If the status channel never answers at all, fail
     * closed: a silent printer is the one that buffers the label and replays it
     * later as a surprise duplicate.
     */
    private void checkPrinterReady() throws PrinterNotReadyException {
        final int maxPolls = 4;
        final long pollDelayMs = 1000L;
        // Keep the last ANSWERED status: an observed not-ready must not be erased by a
        // later failed poll, or we would send into a stalled printer that buffers the
        // label and replays it later (the double-label bug).
        PrinterStatus lastAnswered = null;

        // ponytail: cap the status read timeout so four dead polls cost ~9s instead
        // of ~23s; the original value is restored below for the label send.
        final int prevReadTimeout = zebraConn.getMaxTimeoutForRead();
        try {
            zebraConn.setMaxTimeoutForRead(1500);
            for (int i = 1; i <= maxPolls; i++) {
                try {
                    lastAnswered = instance.getCurrentStatus();
                    if (lastAnswered.isReadyToPrint) {
                        return;
                    }
                    if (lastAnswered.isHeadOpen || lastAnswered.isPaperOut
                            || lastAnswered.isPaused || lastAnswered.isRibbonOut) {
                        break; // waiting won't fix these; a human has to act
                    }
                } catch (Exception e) {
                    // possibly still waking from sleep; keep polling
                    Log.w(TAG, "checkPrinterReady: status query failed (poll " + i + "/"
                            + maxPolls + "): " + e.getMessage());
                }
                if (i < maxPolls) {
                    try {
                        Thread.sleep(pollDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } finally {
            zebraConn.setMaxTimeoutForRead(prevReadTimeout);
        }

        if (lastAnswered == null) {
            // Fail closed: a silent printer is the one that buffers this label and
            // replays it later as a surprise duplicate. The power-cycle instructed
            // below also clears that buffer.
            Log.w(TAG, "checkPrinterReady: no status response, refusing to send");
            throw new PrinterNotReadyException("Printer is not responding.\n"
                    + "Turn it off and on, then print again.");
        }
        Log.w(TAG, "checkPrinterReady: not ready - headOpen=" + lastAnswered.isHeadOpen
                + " paperOut=" + lastAnswered.isPaperOut + " paused=" + lastAnswered.isPaused
                + " ribbonOut=" + lastAnswered.isRibbonOut + " headTooHot=" + lastAnswered.isHeadTooHot
                + " bufferFull=" + lastAnswered.isReceiveBufferFull
                + " queuedFormats=" + lastAnswered.numberOfFormatsInReceiveBuffer);
        throw new PrinterNotReadyException(notReadyMessageFor(lastAnswered));
    }

    /**
     * One short, state-specific message per fault. Android 12+ truncates toasts to
     * two lines, so the action must fit in the first two lines of every message.
     */
    static String notReadyMessageFor(PrinterStatus s) {
        if (s.isHeadOpen) {
            return "Printer cover is open.\n"
                    + "Close the cover, then print again.";
        }
        if (s.isPaperOut) {
            return "Printer is out of labels.\n"
                    + "Load a new roll, then print again.";
        }
        if (s.isPaused) {
            if (s.numberOfFormatsInReceiveBuffer > 0) {
                return "Printer paused, " + s.numberOfFormatsInReceiveBuffer
                        + " label(s) already waiting.\n"
                        + "Un-pause it - do NOT print again.";
            }
            return "Printer is paused.\n"
                    + "Press PAUSE (or FEED) on it, then print again.";
        }
        if (s.isRibbonOut) {
            return "Printer ribbon is out.\n"
                    + "Replace the ribbon, then print again.";
        }
        if (s.isHeadTooHot) {
            return "Printer is too hot.\n"
                    + "Wait a minute, then print again.";
        }
        if (s.isReceiveBufferFull) {
            return "Printer is stuck with pending labels.\n"
                    + "Turn it off and on - do NOT print again yet.";
        }
        return "Printer not ready (may be waking up).\n"
                + "Wait a few seconds, then print again.";
    }

    /** Nothing was sent to the printer when this is thrown; safe to reprint. */
    static class PrinterNotReadyException extends Exception {
        PrinterNotReadyException(String friendlyMessage) {
            super(friendlyMessage);
        }
    }

    private List<String> buildZPLCommands(String[] paramList, String printerName) {
        List<String> commands = new ArrayList<>(paramList.length * 50); // Pre-size

        for (String param : paramList) {
            String[] params = param.split(";");
            if (params.length < 14) {
                Log.w(TAG, "Invalid parameter format, skipping: " + param);
                continue;
            }

            String labelCommand = createZPLLabel(params, printerName);
            commands.add(labelCommand);
        }

        return commands;
    }

    public void createZPLTest(boolean isPrinting, String printerName) {
        String param = ";S01R010046;13/11/2025;231;PKT;KFC-2511-32018;SKU-12345;Random cut lettuce Random cut lettuce Random cut lettuce;KDFF439;SEJ301084;18/11/2025;CHILLED;CRECEIVING;PO2511-S00136;07:46:52 AM";
        String[] params = param.split(";");
        if (params.length < 14) {
            Log.w(TAG, "Invalid parameter format, skipping: " + param);
        }
        String labelCommand = createZPLLabel(params, printerName);
        Log.d(TAG, "createZPLTest: \n" + labelCommand);
        if (!isPrinting) return;
        view.showPrinting();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler mainHandler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            boolean connected = false;
            try {

                connectZebra("90:75:DE:17:58:19");
                connected = true;
                sendZebraCommand(labelCommand);


                mainHandler.post(() -> view.closeActivity(true, ""));

            } catch (Exception e) {
                handlePrintError(e, mainHandler, connected, 0, 1);
            } finally {
                if (connected) {
                    closeZebraCommand(); // never throws
                }
                executor.shutdown();
            }
        });
    }

    /*
    CT~~CD,~CC^~CT~
            ^XA~TA000~JSN^LT0^MNT^MTD^POI^PMN^LH0,0^JMA^PR5,5~SD20^JUS^LRN^CI0
^MMT
^PW609
^LL0812
^LS0
^FO10,10^GB590,790,2^FS
^FT20,45^A0N,25,24^FH\^FDPO NO^FS
^FT20,89^A0N,31,31^FH\^FD13^FS
^FT315,45^A0N,25,24^FH\^FDLOCATION CODE^FS
^FT315,89^A0N,31,31^FB285,1,0,C,0^FH\^FD11^FS
^FO10,100^GB590,0,2^FS
^FO304,12^GB0,90,2^FS
^FO10,225^GB590,0,2^FS
^FT20,130^A0N,25,24^FH\^FDSKU^FS
^FT20,195^A0N,31,31^FB380,2,0,L,0^FH\^FDKappa Mozart Alpha Manurung Siagian^FS
^FO405,100^GB0,125,2^FS
^FT415,130^A0N,25,24^FH\^FDPLT SEQ^FS
^FO10,354^GB590,0,2^FS
^FT20,255^A0N,25,24^FH\^FDSKU BARCODE^FS
^BY2,3,79^FT20,345^BCN,,N,N
^FD>:5^FS
^FO10,462^GB590,0,2^FS
^FT20,385^A0N,25,24^FH\^FDDESCRIPTION^FS
^FT20,450^A0N,28,28^FB550,2,0,L,0^^FH\^FDChicken MC Donal storage super long frrozen again no more choice^FS
^FO10,566^GB590,0,2^FS
^FO303,465^GB0,102,2^FS
^FT20,495^A0N,25,24^FH\^FDLOT NO^FS
^FT20,547^A0N,31,31^FH\^FD5^FS
^FT315,495^A0N,25,24^FH\^FDEXPIRY DATE^FS
^FT315,547^A0N,35,35^FB285,1,0,C,0^FH\^FD10^FS
^FO10,668^GB590,0,2^FS
^FO453,566^GB0,102,2^FS
^FT500,603^A0N,25,24^FH\^FDUOM^FS
^FO303,566^GB0,102,2^FS
^FT356,603^A0N,25,24^FH\^FDQTY^FS
^FT315,649^A0N,30,35^FB140,1,0,C,0^FH\^FD3^FS
^FT463,649^A0N,30,35^FB140,1,0,C,0^FH\^FD4^FS
^FT20,603^A0N,25,24^FH\^FDRECEIVED DATE^FS
^FT20,645^A0N,35,35^FB285,1,0,C,0^FH\^FD2^FS
^FT20,711^A0N,25,24^FH\^FDPALLET ID^FS
^FT20,764^A0N,31,31^FH\^FDSEJ-000-000^FS
^BY2,3,99^FT250,785^BCN,,N,N
^FD>:9^FS
^PQ1,0,0,N
^XZ
     */
    private String createZPLLabel(String[] params, String printerName) {
        StringBuilder zpl = new StringBuilder(2000);

        // Variant carries its own start command (and thus print darkness); see LabelVariant.
        LabelVariant variant = LabelVariant.from(printerName);
        zpl.append(ZPL_INIT).append("\n").append(variant.start).append("\n");

        String time = (params.length == 15) ? params[14] : "";

        if (isThreeInch) {
            // DEPRECATED layout — retained for legacy callers (see appendThreeInchBody).
            appendThreeInchBody(zpl, params, time);
        } else if (variant == LabelVariant.YELLOW) {
            appendYellowBody(zpl, params, time);
        } else {
            // DEFAULT and BROWN share the standard 4-inch body; they differ only in
            // print darkness, already applied via the start command above.
            appendStandardBody(zpl, params, time);
        }

        zpl.append(ZPL_END);
        return zpl.toString();
    }

    /**
     * DEPRECATED: 3-inch (PW609) layout. Retained for legacy callers only and
     * superseded by the 4-inch {@link #appendStandardBody}. Do not extend.
     */
    @Deprecated
    private void appendThreeInchBody(StringBuilder zpl, String[] params, String time) {
        zpl.append("^MMT\n")
                .append("^PW609\n")
                .append("^LL0812\n")
                .append("^LS0\n")
                // Outer Border
                .append("^FO10,10^GB590,790,2^FS\n")
                // PO Number Section
                .append("^FT20,45^A0N,25,24^FH\\^FDPO NO^FS\n")
                .append("^FT20,89^A0N,31,31^FH\\^FD").append(params[13]).append("^FS\n")
                // Location Code Section
                .append("^FT315,45^A0N,25,24^FH\\^FDLOCATION CODE^FS\n")
                .append("^FT315,89^A0N,31,31^FB285,1,0,C,0^FH\\^FD").append(params[11]).append("^FS\n")
                // Horizontal Lines
                .append("^FO10,100^GB590,0,2^FS\n")
                .append("^FO304,12^GB0,90,2^FS\n")
                .append("^FO10,225^GB590,0,2^FS\n")
                // SKU Section
                .append("^FT20,130^A0N,25,24^FH\\^FDSKU^FS\n")
                .append("^FT20,195^A0N,31,31^FB380,2,0,L,0^FH\\^FD").append(params[6]).append("^FS\n")
                .append("^FO405,100^GB0,125,2^FS\n")
                // Pallet Sequence Section
                .append("^FT415,130^A0N,25,24^FH\\^FDREC. TIME^FS\n")
//                .append("^FT415,155^A0N,25,24^FH\\^FD").append(params[2]).append("^FS\n")
                .append("^FT415,160^A0N,31,30^FH\\^FD").append(time).append("^FS\n")
                .append("^FO10,354^GB590,0,2^FS\n")
                // SKU Barcode
                .append("^FT20,255^A0N,25,24^FH\\^FDSKU BARCODE^FS\n")
                .append("^BY2,3,79\n")
                .append("^FT20,345^BCN,,N,N\n")
                .append("^FD>:").append(params[6]).append("^FS\n")
                .append("^FO10,462^GB590,0,2^FS\n")
                // Description Section
                .append("^FT20,385^A0N,25,24^FH\\^FDDESCRIPTION^FS\n")
                .append("^FT20,450^A0N,28,28^FB550,2,0,L,0^FH\\^FD").append(params[7]).append("^FS\n")
                .append("^FO10,566^GB590,0,2^FS\n")
                .append("^FO303,465^GB0,102,2^FS\n")
                // Lot and Expiry Date
                .append("^FT20,495^A0N,25,24^FH\\^FDLOT NO^FS\n")
                .append("^FT20,547^A0N,31,31^FH\\^FD").append(params[5]).append("^FS\n")
                .append("^FT315,495^A0N,25,24^FH\\^FDEXPIRY DATE^FS\n")
                .append("^FT315,547^A0N,35,35^FB285,1,0,C,0^FH\\^FD").append(params[10]).append("^FS\n")
                .append("^FO10,668^GB590,0,2^FS\n")
                .append("^FO453,566^GB0,102,2^FS\n")
                .append("^FO303,566^GB0,102,2^FS\n")
                // Quantity & UOM
                .append("^FT500,603^A0N,25,24^FH\\^FDUOM^FS\n")
                .append("^FT356,603^A0N,25,24^FH\\^FDQTY^FS\n")
                .append("^FT315,649^A0N,30,35^FB140,1,0,C,0^FH\\^FD").append(params[3]).append("^FS\n")
                .append("^FT463,649^A0N,30,35^FB140,1,0,C,0^FH\\^FD").append(params[4]).append("^FS\n")
                // Received Date Section
                .append("^FT20,603^A0N,25,24^FH\\^FDRECEIVED DATE^FS\n")
                .append("^FT20,645^A0N,35,35^FB285,1,0,C,0^FH\\^FD").append(params[2]).append("^FS\n")
                // Pallet ID Section
                .append("^FT20,711^A0N,25,24^FH\\^FDPALLET ID^FS\n")
                .append("^FT20,764^A0N,31,31^FH\\^FD").append(params[9]).append("^FS\n")
                // Pallet Barcode
                .append("^BY2,3,99\n")
                .append("^FT250,785^BCN,,N,N\n")
                .append("^FD>:").append(params[9]).append("^FS\n")
                .append("^PQ1,0,0,N\n");
    }

    private void appendYellowBody(StringBuilder zpl, String[] params, String time) {
        zpl.append("^MMT\n")
                .append("^PW812\n")
                .append("^LL0812\n")
                .append("^LS0\n")
                // Corner brackets and section dividers
                .append("^FO10,10^GB25,2,3^FS\n")
                .append("^FO10,10^GB2,25,1^FS\n")
                .append("^FO762,10^GB25,2,3^FS\n")
                .append("^FO787,10^GB2,25,1^FS\n")
                .append("^FO380,10^GB50,2,3^FS\n")
                .append("^FO405,10^GB2,25,1^FS\n")
                .append("^FO10,787^GB25,2,3^FS\n")
                .append("^FO10,762^GB2,25,1^FS\n")
                .append("^FO380,99^GB50,2,3^FS\n")
                .append("^FO405,74^GB2,50,1^FS\n")
                .append("^FO405,200^GB2,25,1^FS\n")
                .append("^FO380,225^GB50,2,3^FS\n")
                .append("^FO787,200^GB2,50,1^FS\n")
                .append("^FO762,225^GB25,2,3^FS\n")
                .append("^FO10,99^GB25,2,3^FS\n")
                .append("^FO10,74^GB2,50,1^FS\n")
                .append("^FO10,200^GB2,50,1^FS\n")
                .append("^FO10,225^GB25,2,3^FS\n")
                .append("^FO10,330^GB2,50,1^FS\n")
                .append("^FO10,355^GB25,2,3^FS\n")
                .append("^FO787,330^GB2,50,1^FS\n")
                .append("^FO762,355^GB25,2,3^FS\n")
                .append("^FO10,440^GB2,50,1^FS\n")
                .append("^FO10,465^GB25,2,3^FS\n")
                .append("^FO787,440^GB2,50,1^FS\n")
                .append("^FO762,465^GB25,2,3^FS\n")
                .append("^FO10,540^GB2,50,1^FS\n")
                .append("^FO10,565^GB25,2,3^FS\n")
                .append("^FO787,540^GB2,50,1^FS\n")
                .append("^FO762,565^GB25,2,3^FS\n")
                .append("^FO762,787^GB25,2,3^FS\n")
                .append("^FO787,762^GB2,25,1^FS\n")
                .append("^FO10,655^GB2,50,1^FS\n")
                .append("^FO10,680^GB25,2,3^FS\n")
                .append("^FO787,655^GB2,50,1^FS\n")
                .append("^FO762,680^GB25,2,3^FS\n")
                .append("^FO380,465^GB50,2,3^FS\n")
                .append("^FO405,465^GB2,25,1^FS\n")
                .append("^FO405,540^GB2,50,1^FS\n")
                .append("^FO380,565^GB50,2,3^FS\n")
                .append("^FO405,655^GB2,25,1^FS\n")
                .append("^FO380,680^GB50,2,3^FS\n")
                .append("^FO603,655^GB2,25,1^FS\n")
                .append("^FO578,680^GB50,2,3^FS\n")
                // PO Number Section
                .append("^FT20,45^A0N,25,24^FH\\^FDPO NO^FS\n")
                .append("^FT20,89^A0N,35,35^FH\\^FD").append(params[13]).append("^FS\n")
                // Location Code Section
                .append("^FT415,45^A0N,25,24^FH\\^FDLOCATION CODE^FS\n")
                .append("^FT415,89^A0N,35,35^FB380,1,0,C,0^FH\\^FD").append(params[11]).append("^FS\n")
                // SKU Section
                .append("^FT20,130^A0N,25,24^FH\\^FDSKU^FS\n")
                .append("^FT20,210^A0N,35,35^FB490,2,0,L,0^FH\\^FD").append(params[6]).append("^FS\n")
                // Pallet Sequence / Received Time Section
                .append("^FT515,130^A0N,25,24^FH\\^FDREC. TIME^FS\n")
                .append("^FT515,175^A0N,35,35^FH\\^FD").append(time).append("^FS\n")
                // SKU Barcode
                .append("^FT20,255^A0N,25,24^FH\\^FDSKU BARCODE^FS\n")
                .append("^BY2,3,79\n")
                .append("^FT20,345^BCN,,N,N\n")
                .append("^FD>:").append(params[6]).append("^FS\n")
                // Description Section
                .append("^FT20,385^A0N,25,24^FH\\^FDDESCRIPTION^FS\n")
                .append("^FT20,450^A0N,35,35^FB750,2,0,L,0^FH\\^FD").append(params[7]).append("^FS\n")
                // Lot and Expiry Date
                .append("^FT20,495^A0N,25,24^FH\\^FDLOT NO^FS\n")
                .append("^FT20,547^A0N,38,38^FH\\^FD").append(params[5]).append("^FS\n")
                .append("^FT425,495^A0N,25,24^FH\\^FDEXPIRY DATE^FS\n")
                .append("^FT425,547^A0N,38,38^FB380,1,0,C,0^FH\\^FD").append(params[10]).append("^FS\n")
                // Quantity & UOM
                .append("^FT420,603^A0N,25,24^FB190,1,0,C,0^FH\\^FDQTY^FS\n")
                .append("^FT420,649^A0N,38,38^FB190,1,0,C,0^FH\\^FD").append(params[3]).append("^FS\n")
                .append("^FT610,603^A0N,25,24^FB190,1,0,C,0^FH\\^FDUOM^FS\n")
                .append("^FT610,649^A0N,35,35^FB190,1,0,C,0^FH\\^FD").append(params[4]).append("^FS\n")
                // Received Date Section
                .append("^FT20,603^A0N,25,24^FH\\^FDRECEIVED DATE^FS\n")
                .append("^FT20,645^A0N,38,38^FB380,1,0,C,0^FH\\^FD").append(params[2]).append("^FS\n")
                // Pallet ID Section
                .append("^FT20,711^A0N,25,24^FH\\^FDPALLET ID^FS\n")
                .append("^FT20,764^A0N,35,35^FH\\^FD").append(params[9]).append("^FS\n")
                // Pallet Barcode
                .append("^BY2,3,99\n")
                .append("^FT350,785^BCN,,N,N\n")
                .append("^FD>:").append(params[9]).append("^FS\n")
                .append("^PQ1,0,0,N\n");
    }

    private void appendStandardBody(StringBuilder zpl, String[] params, String time) {
        zpl.append("^MMT\n")
                .append("^PW812\n")
                .append("^LL0812\n")
                .append("^LS0\n")
                // Outer Border
                .append("^FO10,10^GB790,790,2^FS\n")
                // PO Number Section
                .append("^FT20,45^A0N,25,24^FH\\^FDPO NO^FS\n")
                .append("^FT20,89^A0N,35,35^FH\\^FD").append(params[13]).append("^FS\n")
                // Location Code Section
                .append("^FT415,45^A0N,25,24^FH\\^FDLOCATION CODE^FS\n")
                .append("^FT415,89^A0N,35,35^FB380,1,0,C,0^FH\\^FD").append(params[11]).append("^FS\n")
                // Horizontal Lines
                .append("^FO10,100^GB790,0,2^FS\n")
                .append("^FO395,12^GB0,90,2^FS\n")
                .append("^FO10,225^GB790,0,2^FS\n")
                // SKU Section
                .append("^FT20,130^A0N,25,24^FH\\^FDSKU^FS\n")
                .append("^FT20,210^A0N,35,35^FB490,2,0,L,0^FH\\^FD").append(params[6]).append("^FS\n")
                .append("^FO500,100^GB0,125,2^FS\n")
                // Pallet Sequence / Received Time Section
                .append("^FT515,130^A0N,25,24^FH\\^FDREC. TIME^FS\n")
                .append("^FT515,175^A0N,35,35^FH\\^FD").append(time).append("^FS\n")
                .append("^FO10,354^GB790,0,2^FS\n")
                // SKU Barcode
                .append("^FT20,255^A0N,25,24^FH\\^FDSKU BARCODE^FS\n")
                .append("^BY2,3,79\n")
                .append("^FT20,345^BCN,,N,N\n")
                .append("^FD>:").append(params[6]).append("^FS\n")
                .append("^FO10,462^GB790,0,2^FS\n")
                // Description Section
                .append("^FT20,385^A0N,25,24^FH\\^FDDESCRIPTION^FS\n")
                .append("^FT20,450^A0N,35,35^FB750,2,0,L,0^FH\\^FD").append(params[7]).append("^FS\n")
                .append("^FO10,566^GB790,0,2^FS\n")
                // Lot and Expiry Date
                .append("^FT20,495^A0N,25,24^FH\\^FDLOT NO^FS\n")
                .append("^FT20,547^A0N,38,38^FH\\^FD").append(params[5]).append("^FS\n")
                .append("^FO415,465^GB0,102,2^FS\n")
                .append("^FT425,495^A0N,25,24^FH\\^FDEXPIRY DATE^FS\n")
                .append("^FT425,547^A0N,38,38^FB380,1,0,C,0^FH\\^FD").append(params[10]).append("^FS\n")
                .append("^FO10,668^GB790,0,2^FS\n")
                // Quantity & UOM
                .append("^FO415,566^GB0,102,2^FS\n")
                .append("^FT420,603^A0N,25,24^FB190,1,0,C,0^FH\\^FDQTY^FS\n")
                .append("^FT420,649^A0N,38,38^FB190,1,0,C,0^FH\\^FD").append(params[3]).append("^FS\n")
                .append("^FO605,566^GB0,102,2^FS\n")
                .append("^FT610,603^A0N,25,24^FB190,1,0,C,0^FH\\^FDUOM^FS\n")
                .append("^FT610,649^A0N,35,35^FB190,1,0,C,0^FH\\^FD").append(params[4]).append("^FS\n")
                // Received Date Section
                .append("^FT20,603^A0N,25,24^FH\\^FDRECEIVED DATE^FS\n")
                .append("^FT20,645^A0N,38,38^FB380,1,0,C,0^FH\\^FD").append(params[2]).append("^FS\n")
                // Pallet ID Section
                .append("^FT20,711^A0N,25,24^FH\\^FDPALLET ID^FS\n")
                .append("^FT20,764^A0N,35,35^FH\\^FD").append(params[9]).append("^FS\n")
                // Pallet Barcode
                .append("^BY2,3,99\n")
                .append("^FT350,785^BCN,,N,N\n")
                .append("^FD>:").append(params[9]).append("^FS\n")
                .append("^PQ1,0,0,N\n");
    }

    private void handlePrintError(Exception e, Handler mainHandler, boolean dataMayHaveReachedPrinter,
                                  int sentCount, int totalCount) {
        String technical = e.getMessage();
        Log.e(TAG, "Print error: " + technical, e);

        String friendly = friendlyMessageFor(e, dataMayHaveReachedPrinter, sentCount, totalCount);

        // closeActivity toasts the message itself; an extra showError would show it twice.
        mainHandler.post(() -> view.closeActivity(false, friendly));
    }

    // Android 12+ truncates toasts to two lines: every message must carry its action
    // in the first two lines. Extra detail below that only shows on older devices.
    private String friendlyMessageFor(Exception e, boolean dataMayHaveReachedPrinter,
                                      int sentCount, int totalCount) {
        if (e instanceof PrinterNotReadyException) {
            return e.getMessage();
        }
        if (e instanceof ConnectionException) {
            // Post-connect failure: label bytes may already be in the printer's
            // buffer, so "couldn't connect" would mislead the user into reprinting.
            if (dataMayHaveReachedPrinter) {
                if (sentCount > 0 && totalCount > 1) {
                    return "Sent " + sentCount + " of " + totalCount + " labels, then lost the printer.\n"
                            + "CHECK WHAT PRINTED - reprint only missing labels.";
                }
                return "Connection lost while sending.\n"
                        + "CHECK THE PRINTER - print again only if nothing comes out.";
            }
            return "Couldn't connect to the printer.\n"
                    + "Turn it off and on, move closer, then print again.\n"
                    + "Still failing? Make sure no other phone is connected,\n"
                    + "or forget and re-pair it in Settings > Bluetooth.";
        }
        if (e instanceof ZebraPrinterLanguageUnknownException) {
            return "Printer model not recognized.\n"
                    + "Check the printer is a supported Zebra model and is powered on, then try again.";
        }
        String msg = e.getMessage();
        return (msg != null && !msg.isEmpty()) ? msg : "Printing failed. Please try again.";
    }

    interface View {
        void showLoading();

        void showPrinting();

        void hideLoading();

        void isConnected(boolean bool);

        void closeActivity(boolean bool, String message);

        void showError(String message);

        void onComplete();

        void showBluetoothData(List<BluetoothDevice> devices);

        void initSunmiPrinter(String[] arrArgs);

        Bitmap getAssetData(String fileName);

        void showESCTesting();


        void registerBluetooth();
    }

    public void connectZebra(String overrideMac)
            throws ZebraPrinterLanguageUnknownException, ConnectionException, PrinterNotReadyException {
        String savedMac = spData.getString(sp_mac, "");
        if (overrideMac != null && !overrideMac.isEmpty()) {
            savedMac = overrideMac;
        }

        // Fail fast with an accurate message: retrying open() burns up to ~30s and
        // then blames the printer when the real problem is the phone's radio or a
        // bad MACADDRESS extra from the calling app.
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            throw new PrinterNotReadyException("Bluetooth is off on this phone.\n"
                    + "Turn Bluetooth on, then print again.");
        }
        savedMac = savedMac.toUpperCase(Locale.ROOT);
        if (!BluetoothAdapter.checkBluetoothAddress(savedMac)) {
            throw new PrinterNotReadyException(savedMac.isEmpty()
                    ? "No printer selected.\nOpen this app once to pick a printer, then print again."
                    : "Invalid printer address \"" + savedMac + "\".\nFix the printer setup in the calling app.");
        }

        // A never-paired MAC can't be a working printer here (the picker only lists
        // bonded devices): fail fast instead of burning 3 slow open() attempts.
        try {
            BluetoothDevice device = adapter.getRemoteDevice(savedMac);
            if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                throw new PrinterNotReadyException("Printer is not paired with this phone.\n"
                        + "Pair it in Settings > Bluetooth, then print again.\n"
                        + "Printer: " + savedMac);
            }
        } catch (SecurityException se) {
            // BLUETOOTH_CONNECT denied: open() below would fail the same way but with
            // a technical message. Name the real cause instead.
            throw new PrinterNotReadyException("Bluetooth permission is missing.\n"
                    + "Allow 'Nearby devices' for this app in Settings, then print again.");
        }

        // Retry to recover from android BT classic SDP race ("read failed... read ret: -1").
        final int maxAttempts = 3;
        final long backoffMs = 500L;
        // ponytail: 3s refusal threshold is a field guess; tune after the 2-phone test.
        final long fastRefusalMs = 3000L;
        ConnectionException lastErr = null;
        int failedAttempts = 0;
        boolean allRefusedFast = true;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                if (adapter.isDiscovering()) {
                    adapter.cancelDiscovery();
                }
            } catch (SecurityException ignore) {
                // BLUETOOTH_SCAN not granted on API 31+; harmless to skip
            }
            closeZebraQuietly(); // never reuse a half-open socket from a failed attempt
            final long attemptStart = System.currentTimeMillis();
            try {
                // Insecure RFCOMM: avoids the secure-channel handshake that races on
                // rapid printer switches (3-color fleet) and throws "read ret: -1".
                zebraConn = new com.zebra.sdk.comm.BluetoothConnectionInsecure(savedMac);
                zebraConn.open();
                instance = ZebraPrinterFactory.getInstance(zebraConn);
                Log.d(TAG, "connectZebra: connected on attempt " + attempt);
                return;
            } catch (ZebraPrinterLanguageUnknownException | RuntimeException fatal) {
                // Not retryable. Close the socket or it leaks: each print gets a new
                // presenter, so a socket this instance abandons is unreachable and
                // holds the printer's single BT slot until the process dies — the
                // NEXT print then fails to connect.
                closeZebraQuietly();
                throw fatal;
            } catch (ConnectionException ce) {
                lastErr = ce;
                failedAttempts++;
                final long elapsed = System.currentTimeMillis() - attemptStart;
                if (elapsed >= fastRefusalMs) {
                    allRefusedFast = false; // slow timeout = printer off / out of range
                }
                Log.w(TAG, "connectZebra: attempt " + attempt + "/" + maxAttempts
                        + " failed after " + elapsed + "ms: " + ce.getMessage());
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        closeZebraQuietly();
        // Every attempt refused fast, with Bluetooth on and the printer paired:
        // something answered and said no. Zebra BT printers take one connection at a
        // time, so the usual culprit is another phone holding it. A slow timeout means
        // off/out of range instead — that keeps the generic ConnectionException below.
        if (failedAttempts == maxAttempts && allRefusedFast) {
            throw new PrinterNotReadyException("Printer is in use by another phone.\n"
                    + "Close printing there or power-cycle the printer, then print again.");
        }
        throw lastErr != null ? lastErr
                : new ConnectionException("Failed to connect after " + maxAttempts + " attempts");
    }

    public void sendZebraCommand(String command) throws ConnectionException {
        instance.sendCommand(command);
    }

    public void closeZebraCommand() {
        closeZebraQuietly();
    }

    /** Close and null the Zebra connection; never throws, safe to call in any state. */
    private void closeZebraQuietly() {
        if (zebraConn != null) {
            try {
                zebraConn.close();
            } catch (Exception e) {
                Log.w(TAG, "closeZebraQuietly: " + e.getMessage());
            }
        }
        zebraConn = null;
        instance = null;
    }
}
