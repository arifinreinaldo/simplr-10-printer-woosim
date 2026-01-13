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

public class MainActivity extends AppCompatActivity {
    ActivityMainBinding binding;
    NFCHelper nfcHelper = NFCHelper.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        nfcHelper.init(this);


        if (!nfcHelper.hasNfcHardware()) {
            Toast.makeText(this, "Device does not support NFC", Toast.LENGTH_SHORT).show();
        } else if (!nfcHelper.isAvailable()) {
            Toast.makeText(this, "Please enable NFC in settings", Toast.LENGTH_SHORT).show();
        }

        // Option 1: Read NDEF text (recommended for text data)
        nfcHelper.startNFCSession(new NFCHelper.OnNdefTextReadListener() {
            @Override
            public void onTextRead(String text) {
                runOnUiThread(() -> {
                    binding.nfcText.setText(text);
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    // Show error dialog/toast
                    Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

}