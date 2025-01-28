package com.example.test;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.snackbar.Snackbar;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class AdminActivity extends AppCompatActivity {

    private static final int REQUEST_PICK_FILE = 123;
    private static final int STORAGE_PERMISSION_CODE = 124;
    private static final String TAG = "AdminActivity";

    private ImageView qrCodeImageView;
    private File attendanceFile;
    private MaterialToolbar toolbar;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private DBHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        dbHelper = new DBHelper(this);
        initViews();
        setupToolbar();
        checkStoragePermission();
        initAttendanceFile();
    }

    private void initViews() {
        qrCodeImageView = findViewById(R.id.qrCodeImageView);
        toolbar = findViewById(R.id.toolbar);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Admin Dashboard");
        }
    }

    private void checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // لا تحتاج إلى إذن صريح في Android 10 وما فوق
            initAttendanceFile();
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    new AlertDialog.Builder(this)
                            .setTitle("Permission Needed")
                            .setMessage("This app needs storage permission to manage attendance files.")
                            .setPositiveButton("OK", (dialog, which) -> {
                                ActivityCompat.requestPermissions(this,
                                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                        STORAGE_PERMISSION_CODE);
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                } else {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            STORAGE_PERMISSION_CODE);
                }
            } else {
                initAttendanceFile();
            }
        }
    }
    private void initAttendanceFile() {
        File directory = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (directory == null) {
            showError("Cannot access storage");
            return;
        }

        if (!directory.exists() && !directory.mkdirs()) {
            showError("Failed to create directory");
            return;
        }

        attendanceFile = new File(directory, "attendance.xlsx");
        Log.d(TAG, "Attendance file path: " + attendanceFile.getAbsolutePath());

        if (!attendanceFile.exists()) {
            createNewExcelFile();
        }
    }

    private void createNewExcelFile() {
        executor.execute(() -> {
            try (Workbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet("Attendance");
                createHeaderRow(sheet);
                saveWorkbook(workbook);
                Log.d(TAG, "New Excel file created");
            } catch (IOException e) {
                Log.e(TAG, "Create file failed", e);
                showErrorAsync("Failed to create attendance file");
            }
        });
    }

    private void createHeaderRow(Sheet sheet) {
        Row headerRow = sheet.createRow(0);
        String[] headers = {"Student ID", "Name", "Attendance"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
        }
    }

    public void onGenerateBarcodeClick(View view) {
        executor.execute(() -> {
            try {
                // تحديد معرف الصف
                String classId = "class_101"; // يمكنك تغييره حسب الفصل

                // إنشاء الباركود باستخدام معرف الصف
                QRCodeWriter writer = new QRCodeWriter();
                BitMatrix bitMatrix = writer.encode(classId, BarcodeFormat.QR_CODE, 512, 512);
                Bitmap bitmap = toBitmap(bitMatrix);

                // عرض الباركود على الواجهة
                mainHandler.post(() -> {
                    qrCodeImageView.setImageBitmap(bitmap);
                    saveQRCodeToGallery(bitmap);
                    showSuccess("QR Code generated successfully for Class ID: " + classId);
                });
            } catch (Exception e) {
                showErrorAsync("Failed to generate QR Code: " + e.getMessage());
            }
        });
    }


    private String getAllStudentIds() {
        StringBuilder ids = new StringBuilder();

        try (FileInputStream fis = new FileInputStream(attendanceFile);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0); // الورقة الأولى
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // تخطي الصف الأول (العناوين)

                Cell idCell = row.getCell(0); // العمود الأول يحتوي على رقم الطالب
                if (idCell != null) {
                    // التحقق من نوع الخلية
                    switch (idCell.getCellType()) {
                        case STRING:
                            ids.append(idCell.getStringCellValue().trim()).append(",");
                            break;
                        case NUMERIC:
                            ids.append((int) idCell.getNumericCellValue()).append(",");
                            break;
                        default:
                            Log.e(TAG, "Unsupported cell type: " + idCell.getCellType());
                            break;
                    }
                }
            }

            // إزالة الفاصلة الأخيرة
            if (ids.length() > 0) {
                ids.setLength(ids.length() - 1);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error reading student IDs", e);
            showErrorAsync("Failed to read student IDs: " + e.getMessage());
        }

        return ids.toString(); // إرجاع السلسلة النصية التي تحتوي على أرقام الطلاب
    }


    private String generateSessionData() {
        return "ATTENDANCE-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(new Date());
    }

    private Bitmap toBitmap(BitMatrix matrix) {
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        int[] pixels = new int[width * height];

        for (int y = 0; y < height; y++) {
            int offset = y * width;
            for (int x = 0; x < width; x++) {
                pixels[offset + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
            }
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }

    private void saveQRCodeToGallery(Bitmap bitmap) {
        ContentResolver resolver = getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, "AttendanceQR_" + System.currentTimeMillis() + ".png");
        values.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AttendanceApp");
        } else {
            File directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES + "/AttendanceApp");
            if (!directory.exists() && !directory.mkdirs()) {
                showErrorAsync("Failed to create directory");
                return;
            }
        }

        try (OutputStream stream = resolver.openOutputStream(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))) {
            if (stream != null && bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                showSuccess("QR Code generated successfully for Class ID: " );
            } else {
                showErrorAsync("Failed to save QR Code");
            }
        } catch (IOException e) {
            showErrorAsync("Save failed: " + e.getMessage());
        }
    }

    public void onViewAttendanceClick(View view) {
        Log.d(TAG, "View Attendance clicked");
        readExcelAndDisplay(true);
    }

    public void onViewStudentListClick(View view) {
        Log.d(TAG, "View Student List clicked");
        readExcelAndDisplay(false);
    }

    public void onUploadExcelClick(View view) {
        Log.d(TAG, "Upload Excel clicked");
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        startActivityForResult(intent, REQUEST_PICK_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_FILE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                Log.d(TAG, "Selected file URI: " + uri.toString());
                copyExcelFile(uri);
            }
        }
    }

    private void copyExcelFile(Uri sourceUri) {
        executor.execute(() -> {
            try (InputStream in = getContentResolver().openInputStream(sourceUri);
                 Workbook workbook = WorkbookFactory.create(in)) {

                Log.d(TAG, "Validating Excel format...");
                Sheet sheet = workbook.getSheetAt(0);
                if (!isValidHeader(sheet.getRow(0))) {
                    showErrorAsync("Invalid Excel format! Required columns: Student ID, Name, Attendance");
                    return;
                }

                try (FileOutputStream out = new FileOutputStream(attendanceFile)) {
                    workbook.write(out);
                    Log.d(TAG, "Excel file updated successfully");
                    showSuccessAsync("Excel file updated");
                }
            } catch (Exception e) {
                Log.e(TAG, "File copy failed", e);
                showErrorAsync("Failed to update Excel file: " + e.getMessage());
            }
        });
    }

    private boolean isValidHeader(Row headerRow) {
        if (headerRow == null) {
            Log.e(TAG, "Header row is null");
            return false;
        }

        String[] expectedHeaders = {"Student ID", "Name", "Attendance"};
        for (int i = 0; i < expectedHeaders.length; i++) {
            Cell cell = headerRow.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            String cellValue = cell.getStringCellValue().trim()
                    .replaceAll("[^a-zA-Z0-9]", "")
                    .toLowerCase();

            String expected = expectedHeaders[i].replaceAll("[^a-zA-Z0-9]", "").toLowerCase();

            Log.d(TAG, "Header Check - Expected: " + expected + " | Found: " + cellValue);

            if (!expected.equals(cellValue)) {
                Log.e(TAG, "Header mismatch at column " + i);
                return false;
            }
        }
        return true;
    }

    private void readExcelAndDisplay(boolean showAttendance) {
        if (!attendanceFile.exists()) {
            showErrorAsync("Attendance file not found!");
            return;
        }



        executor.execute(() -> {
            try (FileInputStream fis = new FileInputStream(attendanceFile);
                 Workbook workbook = WorkbookFactory.create(fis)) {

                Log.d(TAG, "Reading Excel file...");
                Sheet sheet = workbook.getSheetAt(0);
                if (sheet == null) {
                    Log.e(TAG, "Sheet is null");
                    showErrorAsync("No data found");
                    return;
                }

                StringBuilder result = new StringBuilder();
                for (Row row : sheet) {
                    if (row.getRowNum() == 0) continue;

                    Cell idCell = row.getCell(0);
                    Cell nameCell = row.getCell(1);
                    Cell attendanceCell = row.getCell(2);

                    String studentId = (idCell != null) ? getCellStringValue(idCell) : "N/A";
                    String name = (nameCell != null) ? getCellStringValue(nameCell) : "N/A";
                    String attendance = "Absent";

                    if (showAttendance && attendanceCell != null) {
                        Log.d(TAG, "Attendance cell type: " + attendanceCell.getCellType());
                        if (attendanceCell.getCellType() == CellType.NUMERIC) {
                            attendance = (attendanceCell.getNumericCellValue() == 1) ? "Present" : "Absent";
                        }
                    }

                    Log.d(TAG, "Processing row: " + studentId + " - " + name);
                    result.append(studentId).append(" - ").append(name);
                    if (showAttendance) result.append(": ").append(attendance);
                    result.append("\n\n");
                }

                if (result.length() == 0) {
                    Log.e(TAG, "No data found in Excel");
                    showErrorAsync("No students found");
                    return;
                }

                mainHandler.post(() -> {
                    Log.d(TAG, "Launching DisplayActivity");
                    Intent intent = new Intent(AdminActivity.this, DisplayActivity.class);
                    intent.putExtra("excel_data", result.toString());
                    startActivity(intent);
                });

            } catch (Exception e) {
                Log.e(TAG, "Error reading Excel", e);
                showErrorAsync("Error: " + e.getMessage());
            }
        });
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return "N/A";
        try {
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue().trim();
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                .format(cell.getDateCellValue());
                    }
                    return String.valueOf((int) cell.getNumericCellValue()); // تحويل الرقم إلى نص
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                case FORMULA:
                    return cell.getCellFormula();
                default:
                    return "N/A";
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading cell", e);
            return "N/A";
        }
    }


    private void saveWorkbook(Workbook workbook) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(attendanceFile)) {
            workbook.write(fos);
        }
    }

    private void showSuccessAsync(String message) {
        mainHandler.post(() -> showSuccess(message));
    }

    private void showErrorAsync(String message) {
        mainHandler.post(() -> {
            Log.e(TAG, message); // تسجيل الخطأ
            showError(message);
        });
    }

    private void showSuccess(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Storage permission granted", Toast.LENGTH_SHORT).show();
                initAttendanceFile();
            } else {
                Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        dbHelper.close();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
        super.onDestroy();
    }
}