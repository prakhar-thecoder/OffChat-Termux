package com.termux.app;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.termux.R;

public class SummaryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        findViewById(R.id.cardEmail).setOnClickListener(v ->
                startActivity(new Intent(SummaryActivity.this, EmailQrActivity.class)));

        findViewById(R.id.cardWebsite).setOnClickListener(v ->
                startActivity(new Intent(SummaryActivity.this, WebsiteQrActivity.class)));

        findViewById(R.id.cardWifi).setOnClickListener(v ->
                startActivity(new Intent(SummaryActivity.this, WifiQrActivity.class)));

        findViewById(R.id.cardMultiLinks).setOnClickListener(v ->
                startActivity(new Intent(SummaryActivity.this, MultiLinkQrActivity.class)));

        FloatingActionButton fabScan = findViewById(R.id.fabScan);
        fabScan.setOnClickListener(v ->
                startActivity(new Intent(SummaryActivity.this, ScanQrActivity.class)));
    }
}
