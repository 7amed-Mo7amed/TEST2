package com.example.test;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class DBHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "attendance.db";
    private static final int DATABASE_VERSION = 1;

    public DBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // إنشاء جدول الطلاب
        db.execSQL("CREATE TABLE students (" +
                "student_id TEXT PRIMARY KEY," +
                "name TEXT," +
                "attendance INTEGER DEFAULT 0)");

    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS students");
        onCreate(db);
    }

    // إضافة طالب جديد
    public void addStudent(String studentId, String name) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("student_id", studentId.trim().toLowerCase()); // تخزين ID بتنسيق موحد (حروف صغيرة ومزالة المسافات)
        values.put("name", name.trim());
        long result = db.insert("students", null, values);
        if (result == -1) {
            Log.d("DBHelper", "Failed to add student: " + studentId);
        } else {
            Log.d("DBHelper", "Student added: " + studentId);
        }
        db.close();
    }

    // تحديث الحضور
    public void updateAttendance(String studentId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("attendance", 1); // 1 تعني حاضر

        int rowsUpdated = db.update("students", values, "TRIM(LOWER(student_id)) = ?", new String[]{studentId.trim().toLowerCase()});
        if (rowsUpdated > 0) {
            Log.d("DBHelper", "Attendance updated for student ID: " + studentId);
        } else {
            Log.d("DBHelper", "Failed to update attendance for student ID: " + studentId);
        }
        db.close();
    }

    // التحقق إذا كان الطالب موجودًا
    public boolean isStudentExists(String studentId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT 1 FROM students WHERE TRIM(LOWER(student_id)) = ?";
        Log.d("DBHelper", "Executing query: " + query + " with studentId: " + studentId);

        Cursor cursor = db.rawQuery(query, new String[]{studentId.trim().toLowerCase()});
        boolean exists = cursor != null && cursor.moveToFirst();
        if (cursor != null) cursor.close();
        db.close();

        Log.d("DBHelper", "Student exists: " + exists);
        return exists;
    }

    // الحصول على قائمة الطلاب
    public List<String> getStudents() {
        List<String> students = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query("students", new String[]{"student_id", "name", "attendance"}, null, null, null, null, null);

        if (cursor != null) {
            while (cursor.moveToNext()) {
                String studentId = cursor.getString(0);
                String name = cursor.getString(1);
                int attendance = cursor.getInt(2);
                String attendanceStatus = (attendance == 1) ? "Present" : "Absent";
                students.add(studentId + " - " + name + " - " + attendanceStatus);
            }
            cursor.close();
        }
        db.close();
        return students;
    }

    // تسجيل جميع الطلاب في Logcat
    public void logAllStudents() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query("students", new String[]{"student_id", "name", "attendance"}, null, null, null, null, null);

        if (cursor != null) {
            Log.d("DBHelper", "Logging all students:");
            while (cursor.moveToNext()) {
                String studentId = cursor.getString(0);
                String name = cursor.getString(1);
                int attendance = cursor.getInt(2);
                String attendanceStatus = (attendance == 1) ? "Present" : "Absent";
                Log.d("DBHelper", "Student: ID=" + studentId + ", Name=" + name + ", Attendance=" + attendanceStatus);
            }
            cursor.close();
        }
        db.close();
    }

}
