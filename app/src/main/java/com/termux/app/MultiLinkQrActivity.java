package com.termux.app;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.termux.R;

public class MultiLinkQrActivity extends AppCompatActivity {

    private LinearLayout container;
    private ImageView imgQr;
    private Bitmap currentQrBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multi_link_qr);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        container = findViewById(R.id.containerLinks);
        imgQr = findViewById(R.id.imgQr);

        MaterialButton btnAdd = findViewById(R.id.btnAdd);
        MaterialButton btnGenerate = findViewById(R.id.btnGenerate);
        MaterialButton btnShare = findViewById(R.id.btnShare);
        MaterialButton btnSave = findViewById(R.id.btnSave);

        btnAdd.setOnClickListener(v -> addRow());
        btnGenerate.setOnClickListener(v -> generate());

        btnShare.setOnClickListener(v -> {
            if (currentQrBitmap == null) {
                Toast.makeText(this, "Generate QR first", Toast.LENGTH_SHORT).show();
                return;
            }
            QrUtils.shareBitmap(this, currentQrBitmap, "multilink_qr.png");
        });

        btnSave.setOnClickListener(v -> {
            if (currentQrBitmap == null) {
                Toast.makeText(this, "Generate QR first", Toast.LENGTH_SHORT).show();
                return;
            }
            boolean ok = QrUtils.saveBitmapToGallery(this, currentQrBitmap,
                    "PrimeQR_Multi_" + System.currentTimeMillis());
            Toast.makeText(this, ok ? "Saved to Gallery" : "Save failed", Toast.LENGTH_SHORT).show();
        });

        addRow();
    }

    private void addRow() {
        View row = LayoutInflater.from(this).inflate(R.layout.item_link, container, false);
        ImageView delete = row.findViewById(R.id.btnDelete);
        delete.setOnClickListener(v -> container.removeView(row));
        container.addView(row);
    }

    private void generate() {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < container.getChildCount(); i++) {
            View row = container.getChildAt(i);
            EditText et = row.findViewById(R.id.etLink);
            String link = et.getText().toString().trim();

            if (!link.isEmpty()) {
                if (!link.startsWith("http://") && !link.startsWith("https://")) {
                    link = "https://" + link;
                }
                sb.append(link).append("\n");
            }
        }

        if (sb.length() == 0) {
            Toast.makeText(this, "Add at least one link", Toast.LENGTH_SHORT).show();
            return;
        }

        currentQrBitmap = QrUtils.generateQrBitmap(sb.toString().trim(), 900, 900);
        imgQr.setImageBitmap(currentQrBitmap);
    }
}
