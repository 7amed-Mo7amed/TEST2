package com.example.test;

import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class DisplayActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display);

        setupToolbar();
        displayData();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void displayData() {
        TextView textView = findViewById(R.id.displayTextView);
        textView.setMovementMethod(new ScrollingMovementMethod());

        String rawData = getIntent().getStringExtra("excel_data");
        if (rawData == null || rawData.isEmpty()) {
            textView.setText("No data available");
            return;
        }

        SpannableStringBuilder formattedData = new SpannableStringBuilder();
        for (String line : rawData.split("\n")) {
            SpannableString spannableLine = new SpannableString(line + "\n");
            applyTextColor(spannableLine, "Present", Color.GREEN);
            applyTextColor(spannableLine, "Absent", Color.RED);
            formattedData.append(spannableLine);
        }

        textView.setText(formattedData);
    }

    private void applyTextColor(SpannableString text, String target, int color) {
        int start = text.toString().indexOf(target);
        while (start != -1) {
            int end = start + target.length();
            text.setSpan(new ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            start = text.toString().indexOf(target, end);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish(); // العودة إلى الشاشة السابقة (AdminActivity)
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}