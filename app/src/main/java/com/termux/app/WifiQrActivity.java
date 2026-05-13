package com.termux.app;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.termux.R;

public class WifiQrActivity extends AppCompatActivity {

    private TextInputEditText etSsid, etPass;
    private ImageView imgQr;
    private Bitmap currentQrBitmap = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifi_qr);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        etSsid = findViewById(R.id.etSsid);
        etPass = findViewById(R.id.etPass);
        imgQr = findViewById(R.id.imgQr);

        MaterialButton btnGenerate = findViewById(R.id.btnGenerate);
        MaterialButton btnShare = findViewById(R.id.btnShare);
        MaterialButton btnSave = findViewById(R.id.btnSave);

        btnGenerate.setOnClickListener(v -> generate());

        btnShare.setOnClickListener(v -> {
            if (currentQrBitmap == null) {
                Toast.makeText(this, "Generate QR first", Toast.LENGTH_SHORT).show();
                return;
            }
            QrUtils.shareBitmap(this, currentQrBitmap, "wifi_qr.png");
        });

        btnSave.setOnClickListener(v -> {
            if (currentQrBitmap == null) {
                Toast.makeText(this, "Generate QR first", Toast.LENGTH_SHORT).show();
                return;
            }
            boolean ok = QrUtils.saveBitmapToGallery(this, currentQrBitmap, "PrimeQR_Wifi_" + System.currentTimeMillis());
            Toast.makeText(this, ok ? "Saved to Gallery" : "Save failed", Toast.LENGTH_SHORT).show();
        });
    }

    private void generate() {
        String ssid = etSsid.getText() == null ? "" : etSsid.getText().toString().trim();
        String pass = etPass.getText() == null ? "" : etPass.getText().toString();

        if (ssid.isEmpty()) {
            Toast.makeText(this, "Enter SSID", Toast.LENGTH_SHORT).show();
            return;
        }

        String escapedSsid = escapeWifiValue(ssid);
        String escapedPass = escapeWifiValue(pass);

        String payload = "WIFI:T:WPA;S:" + escapedSsid + ";P:" + escapedPass + ";;";

        currentQrBitmap = QrUtils.generateQrBitmap(payload, 900, 900);
        if (currentQrBitmap == null) {
            Toast.makeText(this, "QR generate failed", Toast.LENGTH_SHORT).show();
            return;
        }

        imgQr.setImageBitmap(currentQrBitmap);
    }

    private String escapeWifiValue(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace(":", "\\:")
                .replace("\"", "\\\"");
    }
}
