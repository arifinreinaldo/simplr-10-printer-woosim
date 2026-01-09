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
import android.widget.Toast;

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
import com.zebra.sdk.printer.ZebraPrinter;
import com.zebra.sdk.printer.ZebraPrinterFactory;
import com.zebra.sdk.printer.ZebraPrinterLanguageUnknownException;

import net.simplr.woosimdp230l.sunmi.SunmiPrintHelper;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
    private static final String ZPL_START = "^XA~TA000~JSN^LT0^MNT^MTD^POI^PMN^LH0,0^JMA^PR5,5~SD20^JUS^LRN^CI0";
    private static final String ZPL_END = "^XZ";

    public void printZPL(String[] paramList) {
        if (paramList == null || paramList.length == 0) {
            view.closeActivity(true, "No Data");
            return;
        }

        view.showPrinting();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler mainHandler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            boolean connected = false;
            try {
                List<String> commands = buildZPLCommands(paramList);

                connectZebra();
                connected = true;

                for (String command : commands) {
                    sendZebraCommand(command);
                }

                mainHandler.post(() -> view.closeActivity(true, ""));

            } catch (Exception e) {
                handlePrintError(e, mainHandler);
            } finally {
                if (connected) {
                    try {
                        closeZebraCommand();
                    } catch (Exception e) {
                        Log.e(TAG, "Error closing Zebra connection", e);
                    }
                }
                executor.shutdown();
            }
        });
    }

    private List<String> buildZPLCommands(String[] paramList) {
        List<String> commands = new ArrayList<>(paramList.length * 50); // Pre-size

        for (String param : paramList) {
            String[] params = param.split(";");
            if (params.length < 14) {
                Log.w(TAG, "Invalid parameter format, skipping: " + param);
                continue;
            }

            String labelCommand = createZPLLabel(params);
            commands.add(labelCommand);
        }

        return commands;
    }

    public void createZPLTest(boolean isPrinting) {
        String param = ";S01R010046;13/11/2025;231;PKT;KFC-2511-32018;Random cut lettuce Random cut lettuce Random cut lettuce;RANDOM CUT LETTUCE ;KDFF439;SEJ301084;18/11/2025;CHILLED;CRECEIVING;PO2511-S00136;07:46:52 AM";
        String[] params = param.split(";");
        if (params.length < 14) {
            Log.w(TAG, "Invalid parameter format, skipping: " + param);
        }
        String labelCommand = createZPLLabel(params);
        Log.d(TAG, "createZPLTest: \n" + labelCommand);
        if (!isPrinting) return;
        view.showPrinting();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler mainHandler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            boolean connected = false;
            try {

                connectZebra();
                connected = true;
                sendZebraCommand(labelCommand);


                mainHandler.post(() -> view.closeActivity(true, ""));

            } catch (Exception e) {
                handlePrintError(e, mainHandler);
            } finally {
                if (connected) {
                    try {
                        closeZebraCommand();
                    } catch (Exception e) {
                        Log.e(TAG, "Error closing Zebra connection", e);
                    }
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
    private String createZPLLabel(String[] params) {
        StringBuilder zpl = new StringBuilder(2000);
        zpl.append(ZPL_INIT).append("\n").append(ZPL_START).append("\n");
        String time = "";
        if (params.length == 15) {
            time = params[14];
        }
        if (isThreeInch) {
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
        } else {
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
        zpl.append(ZPL_END);

        return zpl.toString();
    }

    private void handlePrintError(Exception e, Handler mainHandler) {
        String errorMsg = e.getMessage();
        Log.e(TAG, "Print error: " + errorMsg, e);

        mainHandler.post(() -> {
            view.showError(errorMsg);
            view.closeActivity(false, errorMsg);
        });
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

    public void connectZebra() throws ZebraPrinterLanguageUnknownException, ConnectionException {
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

        zebraConn = new com.zebra.sdk.comm.BluetoothConnection(savedMac);
        zebraConn.open();
        instance = ZebraPrinterFactory.getInstance(zebraConn);
    }

    public void sendZebraCommand(String command) throws ConnectionException {
        instance.sendCommand(command);
    }

    public void closeZebraCommand() throws ConnectionException {
        if (instance != null) {
            zebraConn.close();
            zebraConn = null;
            instance = null;
        }
    }
}
