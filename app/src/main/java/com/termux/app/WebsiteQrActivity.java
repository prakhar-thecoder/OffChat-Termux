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

public class WebsiteQrActivity extends AppCompatActivity {

    private TextInputEditText etUrl;
    private ImageView imgQr;
    private Bitmap currentQrBitmap = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_website_qr);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        etUrl = findViewById(R.id.etUrl);
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
            QrUtils.shareBitmap(this, currentQrBitmap, "website_qr.png");
        });

        btnSave.setOnClickListener(v -> {
            if (currentQrBitmap == null) {
                Toast.makeText(this, "Generate QR first", Toast.LENGTH_SHORT).show();
                return;
            }
            boolean ok = QrUtils.saveBitmapToGallery(this, currentQrBitmap, "PrimeQR_Website_" + System.currentTimeMillis());
            Toast.makeText(this, ok ? "Saved to Gallery" : "Save failed", Toast.LENGTH_SHORT).show();
        });
    }

    private void generate() {
        String url = etUrl.getText() == null ? "" : etUrl.getText().toString().trim();

        if (url.isEmpty()) {
            Toast.makeText(this, "Enter URL", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }

        currentQrBitmap = QrUtils.generateQrBitmap(url, 900, 900);
        if (currentQrBitmap == null) {
            Toast.makeText(this, "QR generate failed", Toast.LENGTH_SHORT).show();
            return;
        }
        imgQr.setImageBitmap(currentQrBitmap);
    }
}
