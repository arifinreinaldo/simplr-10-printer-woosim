package net.simplr.woosimdp230l;

import android.app.Activity;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Bundle;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class NFCHelper implements NfcAdapter.ReaderCallback {
    private static final String TAG = "NFCHelper";
    private static NFCHelper instance;

    private NfcAdapter nfcAdapter;
    private Activity activity;
    private OnTagDiscoveredListener tagListener;
    private OnNdefTextReadListener ndefTextListener;
    private boolean isSessionActive = false;

    // Callback interface for raw tag
    public interface OnTagDiscoveredListener {
        void onTagDiscovered(Tag tag);
    }

    // Callback interface for NDEF text result
    public interface OnNdefTextReadListener {
        void onTextRead(String text);
        void onError(String errorMessage);
    }

    // Private constructor (Singleton)
    private NFCHelper() {}

    // Get singleton instance
    public static synchronized NFCHelper getInstance() {
        if (instance == null) {
            instance = new NFCHelper();
        }
        return instance;
    }

    // Initialize with activity context
    public void init(Activity activity) {
        this.activity = activity;
        this.nfcAdapter = NfcAdapter.getDefaultAdapter(activity);
    }

    // Check if NFC is available and enabled
    public boolean isAvailable() {
        return nfcAdapter != null && nfcAdapter.isEnabled();
    }

    // Check if device has NFC hardware
    public boolean hasNfcHardware() {
        return nfcAdapter != null;
    }

    // Start NFC session with raw tag callback - replaces active session if exists
    public boolean startNFCSession(OnTagDiscoveredListener listener) {
        return startNFCSessionInternal(listener, null);
    }

    // Start NFC session with NDEF text callback - replaces active session if exists
    public boolean startNFCSession(OnNdefTextReadListener listener) {
        return startNFCSessionInternal(null, listener);
    }

    // Internal method to start NFC session using Reader Mode (no onNewIntent needed)
    private boolean startNFCSessionInternal(OnTagDiscoveredListener tagListener, OnNdefTextReadListener ndefListener) {
        if (activity == null || nfcAdapter == null) {
            Log.e(TAG, "NFCHelper not initialized or NFC not available");
            return false;
        }

        if (!isAvailable()) {
            Log.e(TAG, "NFC is not enabled");
            return false;
        }

        try {
            // If session is already active, stop it first and replace with new one
            if (isSessionActive) {
                Log.d(TAG, "Replacing active NFC session with new one");
                nfcAdapter.disableReaderMode(activity);
            }

            this.tagListener = tagListener;
            this.ndefTextListener = ndefListener;

            // Reader mode flags for ISO 14443 and ISO 15693
            int flags = NfcAdapter.FLAG_READER_NFC_A |      // ISO 14443-3A
                        NfcAdapter.FLAG_READER_NFC_B |      // ISO 14443-3B
                        NfcAdapter.FLAG_READER_NFC_V |      // ISO 15693
                        NfcAdapter.FLAG_READER_NFC_F;       // ISO 14443-4 (FeliCa)

            Bundle options = new Bundle();
            // Delay before presence check (ms) - prevents multiple reads
            options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250);

            // Enable reader mode - callback is called directly, no onNewIntent needed
            nfcAdapter.enableReaderMode(activity, this, flags, options);
            isSessionActive = true;

            Log.d(TAG, "NFC session started (Reader Mode)");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error starting NFC session: " + e.getMessage());
            return false;
        }
    }

    // Stop NFC session
    public void stopNFCSession() {
        if (activity == null || nfcAdapter == null) {
            return;
        }

        try {
            if (isSessionActive) {
                nfcAdapter.disableReaderMode(activity);
                isSessionActive = false;
                tagListener = null;
                ndefTextListener = null;
                Log.d(TAG, "NFC session stopped");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping NFC session: " + e.getMessage());
        }
    }

    // NfcAdapter.ReaderCallback - called directly when tag is discovered (no onNewIntent needed)
    @Override
    public void onTagDiscovered(Tag tag) {
        if (tag == null) return;

        // Run on UI thread for callbacks
        activity.runOnUiThread(() -> {
            // Raw tag callback
            if (tagListener != null) {
                tagListener.onTagDiscovered(tag);
            }

            // NDEF text processing callback
            if (ndefTextListener != null) {
                processNdefTag(tag);
            }
        });
    }

    // Process NDEF tag and extract text
    private void processNdefTag(Tag tag) {
        Ndef ndef = Ndef.get(tag);

        if (ndef == null) {
            ndefTextListener.onError("Tag is not in NDEF Format");
            return;
        }

        try {
            ndef.connect();
            NdefMessage ndefMessage = ndef.getNdefMessage();
            ndef.close();

            if (ndefMessage == null || ndefMessage.getRecords().length == 0) {
                ndefTextListener.onError("Tag does not have data");
                return;
            }

            // Get the first record
            NdefRecord ndefRecord = ndefMessage.getRecords()[0];

            // Check if it's a well-known text record (TNF = 0x01, Type = "T" / 0x54)
            if (ndefRecord.getTnf() == NdefRecord.TNF_WELL_KNOWN &&
                Arrays.equals(ndefRecord.getType(), NdefRecord.RTD_TEXT)) {

                byte[] payload = ndefRecord.getPayload();
                if (payload == null || payload.length == 0) {
                    ndefTextListener.onError("Tag does not have data");
                    return;
                }

                // First byte contains status byte (encoding and language code length)
                int languageCodeLength = payload[0] & 0x3F;

                // Extract text (skip status byte and language code)
                String text = new String(
                    payload,
                    1 + languageCodeLength,
                    payload.length - 1 - languageCodeLength,
                    StandardCharsets.UTF_8
                );

                ndefTextListener.onTextRead(text);

            } else if (ndefRecord.getTnf() == NdefRecord.TNF_MIME_MEDIA) {
                byte[] payload = ndefRecord.getPayload();
                String text = new String(payload, StandardCharsets.UTF_8);

                ndefTextListener.onTextRead(text);
            } else {
                ndefTextListener.onError("Tag does not contain text record");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error reading NDEF: " + e.getMessage());
            ndefTextListener.onError("Error reading tag data");
        }
    }

    // Get tag ID as hex string
    public static String getTagIdHex(Tag tag) {
        if (tag == null) return null;
        byte[] id = tag.getId();
        StringBuilder sb = new StringBuilder();
        for (byte b : id) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    // Check if session is active
    public boolean isSessionActive() {
        return isSessionActive;
    }
}
