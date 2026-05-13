package com.termux.app;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.termux.R;

import java.io.InputStream;

public class ScanQrActivity extends AppCompatActivity {

    private static final int REQ_CAMERA = 1001;

    private DecoratedBarcodeView barcodeView;

    private boolean flashOn = false;
    private ImageView btnFlash;

    private TextView txtResult;
    private String lastScannedText = "";


    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;
                decodeQrFromImage(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_qr);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        barcodeView = findViewById(R.id.barcodeScanner);
        barcodeView.setStatusText("");

        txtResult = findViewById(R.id.txtResult);

        findViewById(R.id.btnCopy).setOnClickListener(v -> copyResult());
        findViewById(R.id.btnOpen).setOnClickListener(v -> openResult());

        btnFlash = findViewById(R.id.btnFlash);

        findViewById(R.id.btnUpload).setOnClickListener(v -> {
            barcodeView.pause();
            pickImageLauncher.launch("image/*");
        });
        findViewById(R.id.btnReset).setOnClickListener(v -> resetScanner());

        setupFlashButton();

        if (hasCameraPermission()) {
            startCameraScan();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        }
    }

    private void setupFlashButton() {
        boolean hasFlash = getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);

        if (!hasFlash) {
            btnFlash.setAlpha(0.3f);
            btnFlash.setEnabled(false);
            return;
        }

        btnFlash.setOnClickListener(v -> toggleFlash());
        updateFlashIcon();
    }

    private void onQrScanned(String text, boolean fromUpload) {
        if (text == null) return;

        text = text.trim();
        if (text.isEmpty()) return;

        if (text.equals(lastScannedText)) return;

        lastScannedText = text;
        txtResult.setText(text);

        try {
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(60);
                }
            }
        } catch (Exception ignored) {}

        Toast.makeText(this, fromUpload ? "QR Scanned (Image)" : "QR Scanned", Toast.LENGTH_SHORT).show();
    }

    private void toggleFlash() {
        flashOn = !flashOn;

        if (flashOn) barcodeView.setTorchOn();
        else barcodeView.setTorchOff();

        updateFlashIcon();
    }

    private void updateFlashIcon() {
        btnFlash.setImageResource(flashOn ? R.drawable.ic_flash_on : R.drawable.ic_flash_off);
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void startCameraScan() {
        barcodeView.decodeContinuous(result -> {
            if (result == null || result.getText() == null) return;
            onQrScanned(result.getText(), false);
        });
        barcodeView.resume();
    }

    private void copyResult() {
        if (lastScannedText.isEmpty()) {
            Toast.makeText(this, "Nothing to copy", Toast.LENGTH_SHORT).show();
            return;
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("QR Result", lastScannedText));
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
    }

    private void openResult() {
        if (lastScannedText.isEmpty()) {
            Toast.makeText(this, "Nothing to open", Toast.LENGTH_SHORT).show();
            return;
        }

        String text = lastScannedText.trim();

        if (text.startsWith("WIFI:")) {
            openWifi(text);
            return;
        }

        if (text.startsWith("mailto:")) {
            Intent i = new Intent(Intent.ACTION_SENDTO);
            i.setData(Uri.parse(text));
            startActivity(Intent.createChooser(i, "Open Email"));
            return;
        }

        if (android.util.Patterns.WEB_URL.matcher(text).matches() ||
                text.startsWith("http://") || text.startsWith("https://")) {

            if (!text.startsWith("http://") && !text.startsWith("https://")) {
                text = "https://" + text;
            }

            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(text));
            startActivity(i);
            return;
        }

        if (android.util.Patterns.EMAIL_ADDRESS.matcher(text).matches()) {
            Intent i = new Intent(Intent.ACTION_SENDTO);
            i.setData(Uri.parse("mailto:" + text));
            startActivity(Intent.createChooser(i, "Open Email"));
            return;
        }

        Toast.makeText(this, "Unsupported type. Copied instead.", Toast.LENGTH_SHORT).show();
        copyResult();
    }

    private void openWifi(String wifiPayload) {
        try {
            String ssid = "";
            String pass = "";

            String body = wifiPayload.substring(5);
            String[] parts = body.split(";");

            for (String p : parts) {
                if (p.startsWith("S:")) ssid = p.substring(2);
                if (p.startsWith("P:")) pass = p.substring(2);
            }

            ssid = ssid.replace("\\;", ";").replace("\\:", ":").replace("\\,", ",").replace("\\\\", "\\");
            pass = pass.replace("\\;", ";").replace("\\:", ":").replace("\\,", ",").replace("\\\\", "\\");

            txtResult.setText("WiFi SSID: " + ssid + "\nPassword: " + pass + "\n\n" + wifiPayload);

            Intent i = new Intent(Settings.ACTION_WIFI_SETTINGS);
            startActivity(i);

            Toast.makeText(this, "Opening WiFi Settings", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Invalid WiFi QR", Toast.LENGTH_SHORT).show();
        }
    }

    private void resetScanner() {
        lastScannedText = "";
        txtResult.setText("Scan a QR to see result...");

        flashOn = false;
        try { barcodeView.setTorchOff(); } catch (Exception ignored) {}
        updateFlashIcon();

        Toast.makeText(this, "Reset done", Toast.LENGTH_SHORT).show();
    }

    private void decodeQrFromImage(Uri uri) {
        try {
            InputStream stream = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(stream);
            if (bitmap == null) {
                Toast.makeText(this, "Invalid image", Toast.LENGTH_SHORT).show();
                return;
            }

            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

            RGBLuminanceSource source = new RGBLuminanceSource(width, height, pixels);
            BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));

            Result result = new MultiFormatReader().decode(binaryBitmap);

            onQrScanned(result.getText(), true);

        } catch (NotFoundException e) {
            Toast.makeText(this, "No QR found in image", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { barcodeView.setTorchOff(); } catch (Exception ignored) {}
        flashOn = false;
        updateFlashIcon();
        barcodeView.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasCameraPermission()) barcodeView.resume();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCameraScan();
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
