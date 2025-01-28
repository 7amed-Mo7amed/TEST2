package com.example.test;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class StudentActivity extends AppCompatActivity {
    private static final String TAG = "StudentActivity";
    private static final int CAMERA_PERMISSION_CODE = 101;

    private EditText studentIdEditText;
    private File attendanceFile;
    private DBHelper dbHelper;
    private final Executor executor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<ScanOptions> barcodeLauncher = registerForActivityResult(
            new ScanContract(),
            result -> {
                if (result.getContents() != null) {
                    processScannedData(result.getContents());
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student);

        dbHelper = new DBHelper(this);
        initViews();
        initAttendanceFile();
    }

    private void initViews() {
        studentIdEditText = findViewById(R.id.studentIdEditText);

        findViewById(R.id.btnScanBarcode).setOnClickListener(this::startScan);
        findViewById(R.id.btnManualEntry).setOnClickListener(this::manualEntry);
    }

    private void initAttendanceFile() {
        String filePath = getSharedPreferences("AppPrefs", MODE_PRIVATE)
                .getString("attendance_file_path", null);
        attendanceFile = filePath != null ? new File(filePath) : null;

        if (attendanceFile == null || !attendanceFile.exists()) {
            showError("Attendance file not found. Please contact admin.");
        }
    }

    public void startScan(View view) {
        if (checkCameraPermission()) {
            launchScanner();
        } else {
            showError("Camera permission is required to scan QR codes.");
        }
    }

    private boolean checkCameraPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
            return false;
        }
        return true;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                CAMERA_PERMISSION_CODE);
    }

    private void launchScanner() {
        Log.d(TAG, "Launching QR Scanner");
        ScanOptions options = new ScanOptions();
        options.setPrompt("Scan QR Code");
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        barcodeLauncher.launch(options);
    }

    private void processScannedData(String data) {
        Log.d(TAG, "Scanned data: " + data);
        String classId = data.trim();

        // التحقق من أن الباركود يحتوي على معرف الصف الصحيح
        if ("class_101".equals(classId)) {
            // عرض نافذة إدخال الرقم
            promptStudentId();
        } else {
            showError("Invalid QR Code for this class!");
        }
    }
    private void promptStudentId() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Student ID");

        final EditText input = new EditText(this);
        builder.setView(input);

        builder.setPositiveButton("Submit", (dialog, which) -> {
            String studentId = input.getText().toString().trim();
            if (!studentId.isEmpty()) {
                recordAttendance(studentId);
            } else {
                showError("Student ID cannot be empty!");
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void recordAttendance(String studentId) {
        DBHelper dbHelper = new DBHelper(this);
        if (dbHelper.isStudentExists(studentId)) {
            dbHelper.updateAttendance(studentId);
            showSuccess("Attendance recorded for Student ID: " + studentId);
        } else {
            showError("Student ID not found in the database!");
        }
    }

    public void manualEntry(View view) {
        String studentId = studentIdEditText.getText().toString().trim();
        if (!studentId.isEmpty()) {
            if (dbHelper.isStudentExists(studentId)) {
                updateAttendance(studentId);
            } else {
                showError("Student Not Found in Database");
            }
        } else {
            showError("Please enter Student ID");
        }
    }

    private void updateAttendance(String studentId) {
        final String finalStudentId = studentId.trim().replaceAll("\\s", "");

        executor.execute(() -> {
            boolean studentExists = dbHelper.isStudentExists(finalStudentId);

            if (!studentExists) {
                runOnUiThread(() -> showError("Student Not Found in Database"));
                return;
            }

            try (Workbook workbook = openWorkbook()) {
                Sheet sheet = workbook.getSheetAt(0);
                boolean updated = false;

                for (Row row : sheet) {
                    if (row.getRowNum() == 0) continue;

                    Cell idCell = row.getCell(0);
                    if (idCell != null) {
                        String cellValue = "";

                        switch (idCell.getCellType()) {
                            case STRING:
                                cellValue = idCell.getStringCellValue().trim();
                                break;
                            case NUMERIC:
                                cellValue = String.valueOf((int) idCell.getNumericCellValue()).trim();
                                break;
                            default:
                                Log.e(TAG, "Unsupported cell type: " + idCell.getCellType());
                                continue;
                        }

                        cellValue = cellValue.replaceAll("\\s", "");

                        if (finalStudentId.equals(cellValue)) {
                            row.createCell(2, CellType.NUMERIC).setCellValue(1);
                            updated = true;
                            break;
                        }
                    }
                }

                if (updated) {
                    saveWorkbook(workbook);
                    runOnUiThread(() -> {
                        dbHelper.updateAttendance(finalStudentId);
                        showSuccess("Attendance Updated!");
                    });
                } else {
                    runOnUiThread(() -> showError("Student Not Found in Excel"));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating attendance", e);
                runOnUiThread(() -> showError("Error: " + e.getMessage()));
            }
        });
    }

    private Workbook openWorkbook() throws IOException {
        if (attendanceFile != null && attendanceFile.exists()) {
            return new XSSFWorkbook(new FileInputStream(attendanceFile));
        } else {
            throw new IOException("Attendance file not found");
        }
    }

    private void saveWorkbook(Workbook workbook) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(attendanceFile)) {
            workbook.write(fos);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchScanner();
            } else {
                showError("Camera permission denied");
            }
        }
    }

    private void showSuccess(String message) {
        View rootView = findViewById(android.R.id.content);
        Snackbar snackbar = Snackbar.make(rootView, message, Snackbar.LENGTH_LONG);
        snackbar.setBackgroundTint(ContextCompat.getColor(this, R.color.success_color));
        snackbar.setTextColor(ContextCompat.getColor(this, R.color.background));
        snackbar.show();
    }

    private void showError(String message) {
        View rootView = findViewById(android.R.id.content);
        Snackbar snackbar = Snackbar.make(rootView, message, Snackbar.LENGTH_LONG);
        snackbar.setBackgroundTint(ContextCompat.getColor(this, R.color.error_color));
        snackbar.setTextColor(ContextCompat.getColor(this, R.color.background));
        snackbar.show();
    }
}