package net.simplr.woosimdp230l;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.dascom.print.connection.BluetoothConnection;
import com.dascom.print.utils.BluetoothUtils;
import com.permissionx.guolindev.PermissionX;

import net.simplr.woosimdp230l.databinding.ActivityMainBinding;
import net.simplr.woosimdp230l.sunmi.BluetoothUtil;
import net.simplr.woosimdp230l.sunmi.SunmiPrintHelper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements MainPresenter.View {
    SharedPreferences sp;
    private final String sp_file = "woosimdp230lmac";
    private final String sp_mac = "macaddress";
    private String mac, printer_code;
    private MainPresenter presenter;
    private String[] arrArgs;
    ActivityMainBinding binding;
    BluetoothConnection bluetoothConnection;
    AdapterDevice adapter;
    List<BluetoothDevice> listDevice = new ArrayList<>();
    String action, value;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        Log.d("Printer", "onNewIntent: ");
        processData();
    }

    private void processData() {
        Intent intent = getIntent();
        printer_code = intent.getStringExtra("PRINTER_CODE");
        action = intent.getStringExtra("ACTION_PRINT");
        value = intent.getStringExtra("TXT_TO_PRINT");
        arrArgs = intent.getStringArrayExtra("ARR_TO_PRINT");
        if (printer_code == null) {
            printer_code = "";
        }
        if (action == null) {
            action = "";
        }
        if (value == null) {
            value = "";
        }
        if (arrArgs == null) {
            arrArgs = new String[0];
        }
        if (arrArgs.length > 0) {
            PermissionX.init(this).permissions(getBluetoothPermission()).request((allGranted, grantedList, deniedList) -> {
                if (allGranted) {
                    presenter.printZPL(arrArgs);
                } else {
                    Toast.makeText(this, String.join(",", deniedList), Toast.LENGTH_SHORT).show();
                }
            });

        } else {
            if (printer_code.equals("SUNMI_V2")) {
                arrArgs = intent.getStringArrayExtra("ARR_TO_PRINT");
                presenter.processSunmiData(arrArgs);
            } else {
                try {
                    Toast.makeText(getApplicationContext(), "Need to call from external application", Toast.LENGTH_SHORT).show();
                    Thread.sleep(500);
                    finishAffinity();
                } catch (Exception e) {

                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        sp = getSharedPreferences(sp_file, Context.MODE_PRIVATE);
        PermissionX.init(this).permissions(getBluetoothPermission()).request((allGranted, grantedList, deniedList) -> {
            if (allGranted) {
                presenter = new MainPresenter(this, sp);
                adapter = new AdapterDevice(this, listDevice);
                adapter.setClickListener((view, position) -> {
                    Toast.makeText(getBaseContext(), "Address selected", Toast.LENGTH_SHORT).show();
                    presenter.saveBluetoothAddress(adapter.getItem(position).getAddress());
                });
                Log.d("Printer", "onCreate: ");
                presenter.verifyESCPOS();
            } else {
                Toast.makeText(this, "These permissions are denied", Toast.LENGTH_LONG).show();
            }
        });
    }

    private List<String> getBluetoothPermission() {
        List<String> permissions = new ArrayList<>();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            // Android 12+ (API 31+)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            // Android 11 and below (API 30 and below)
            permissions.add(Manifest.permission.BLUETOOTH);
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN);
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        return permissions;
    }

    private void registerAddress() {
        mac = sp.getString(sp_mac, "");
        if (mac.isEmpty()) {
            //create adapter
            binding.listDevice.setLayoutManager(new LinearLayoutManager(this));
            binding.listDevice.setAdapter(adapter);
            checkActivateBluetooth();
        } else {
            processData();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private void checkActivateBluetooth() {
        if (!BluetoothUtils.isEnable()) {
            BluetoothUtils.openBluetooth(this, isOn -> {
                if (isOn) {
                    presenter.getBluetoothDevice();
                } else {
                    Toast.makeText(getApplicationContext(), "Failed to activate bluetooth", Toast.LENGTH_SHORT).show();
                    finishAffinity();
                }
            });
        }
        presenter.getBluetoothDevice();
    }

    @Override
    protected void onDestroy() {
        presenter.onDestroy();
        super.onDestroy();
    }

    @Override
    public void showLoading() {
        Log.d("Printer", "showLoading: ");
        binding.loading.setVisibility(View.VISIBLE);
    }

    @Override
    public void showPrinting() {
        Log.d("Printer", "showLoading: ");
        binding.printing.setVisibility(View.VISIBLE);
    }

    @Override
    public void hideLoading() {
        binding.loading.setVisibility(View.GONE);
    }


    @Override
    public void isConnected(boolean bool) {
        Toast.makeText(getApplicationContext(), "isConnected " + bool, Toast.LENGTH_SHORT).show();
        Intent data = new Intent();
        data.putExtra("isSuccess", bool);
        data.putExtra("message", "");
        setResult(RESULT_OK, data);
        finishAffinity();
    }

    @Override
    public void closeActivity(boolean bool, String message) {
//        Intent data = new Intent();
//        data.putExtra("isSuccess", bool);
//        data.putExtra("message", message);
//        setResult(RESULT_OK, data);
        if (!message.isEmpty()) {
            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        }
        Intent resultIntent = new Intent();
        resultIntent.putExtra("selected_value", "sukses");

        setResult(Activity.RESULT_OK, resultIntent);
        finish();
//        System.exit(0);
    }

    @Override
    public void showError(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onComplete() {
        finishAffinity();
//        System.exit(0);
    }

    @Override
    public void showBluetoothData(List<BluetoothDevice> devices) {
        if (!devices.isEmpty()) {
            adapter.setData(devices);
            binding.loading.setVisibility(View.GONE);
            binding.data.setVisibility(View.VISIBLE);
        } else {
            Toast.makeText(getApplicationContext(), "No Bluetooth Found", Toast.LENGTH_SHORT).show();
            finishAffinity();
        }

    }

    @Override
    public void initSunmiPrinter(String[] arrArgs) {
        SunmiPrintHelper.getInstance().initSunmiPrinterService(this);
        showError("Printing");
        //min 16max 40
        if (!BluetoothUtil.isBlueToothPrinter) {
            for (int i = 0; i < arrArgs.length; i++) {
                SunmiPrintHelper.getInstance().printText(arrArgs[i] + "\n", 14, false, false, null);
            }
//            SunmiPrintHelper.getInstance().printText("Text testing", 30, true, true, null);
            SunmiPrintHelper.getInstance().feedPaper();
            closeActivity(true, "Done Print");
        } else {

        }
    }

    @Override
    public Bitmap getAssetData(String fileName) {
        AssetManager assetManager = getAssets();

        InputStream istr;
        Bitmap bitmap = null;
        try {
            istr = assetManager.open(fileName);
            bitmap = BitmapFactory.decodeStream(istr);
        } catch (IOException e) {
            // handle exception
            Log.d("Err", e.getMessage());
        }

        return bitmap;
    }

    @Override
    public void showESCTesting() {
        binding.loading.setVisibility(View.VISIBLE);
        processData();
//        binding.escpos.setVisibility(View.VISIBLE);
//        //ESC Tester
////        binding.btn1.setOnClickListener(view -> {
////            presenter.printESCText();
////        });
////        binding.btn2.setOnClickListener(view -> {
////            presenter.printESCImage(this.getApplicationContext());
////        });
//        //ZPL Printing
//        List<String> commands = new ArrayList<>();
//        commands.add("^XA");
//        commands.add("^PW609");
//        commands.add("^LL0812");
//        commands.add("^LS0");
//        commands.add("^FT591,36^A0I,56,55^FH\\^FDKiri Bawah^FS");
//        commands.add("^FT256,735^A0I,56,55^FH\\^FDKanan Atas^FS");
//        commands.add("^BY5,3,169^FT549,400^BCI,,Y,N");
//        commands.add("^FD>;123456789012^FS");
//        commands.add("^FO13,221^GB575,0,8^FS");
//        commands.add("^FO35,648^GB556,0,8^FS");
//        commands.add("^XZ");
//        binding.btn1.setOnClickListener(view -> {
//            PermissionX.init(this).permissions(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION).request((allGranted, grantedList, deniedList) -> {
//                if (allGranted) {
//                    try {
//                        presenter.connectZebra();
//                        for (int i = 0; i < commands.size(); i++) {
//                            presenter.sendZebraCommand(commands.get(i));
//                        }
//                        presenter.closeZebraCommand();
//                    } catch (ZebraPrinterLanguageUnknownException e) {
//                        throw new RuntimeException(e);
//                    } catch (ConnectionException e) {
//                        Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
//                    }
//                } else {
//                    Toast.makeText(this, String.join(",", deniedList), Toast.LENGTH_SHORT).show();
//                }
//            });
//        });
    }


    @Override
    public void registerBluetooth() {
        registerAddress();
    }
}