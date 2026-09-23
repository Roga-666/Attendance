package com.rolando.attendance;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class AttendanceDb extends SQLiteOpenHelper {
    public static final String TARDY = "Tardy";
    public static final String CALLED_OUT = "Called out";

    public static final class AttendanceEntry {
        public final long id;
        public final LocalDate date;
        public final String type;
        public final int lateMinutes;

        AttendanceEntry(long id, LocalDate date, String type, int lateMinutes) {
            this.id = id;
            this.date = date;
            this.type = type;
            this.lateMinutes = lateMinutes;
        }
    }

    public static final class HoursEntry {
        public final long id;
        public final LocalDate date;
        public final int minutes;

        HoursEntry(long id, LocalDate date, int minutes) {
            this.id = id;
            this.date = date;
            this.minutes = minutes;
        }
    }

    public AttendanceDb(Context context) {
        super(context, "attendance.db", null, 1);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE attendance (id INTEGER PRIMARY KEY AUTOINCREMENT, work_date TEXT NOT NULL UNIQUE, type TEXT NOT NULL, late_minutes INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE hours (id INTEGER PRIMARY KEY AUTOINCREMENT, work_date TEXT NOT NULL UNIQUE, minutes INTEGER NOT NULL)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }

    public void saveAttendance(LocalDate date, String type, int lateMinutes) {
        ContentValues values = new ContentValues();
        values.put("work_date", date.toString());
        values.put("type", type);
        values.put("late_minutes", type.equals(TARDY) ? Math.max(0, lateMinutes) : 0);
        getWritableDatabase().insertWithOnConflict("attendance", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void saveHours(LocalDate date, int minutes) {
        ContentValues values = new ContentValues();
        values.put("work_date", date.toString());
        values.put("minutes", Math.max(0, minutes));
        getWritableDatabase().insertWithOnConflict("hours", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public AttendanceEntry attendanceFor(LocalDate date) {
        try (Cursor c = getReadableDatabase().query("attendance", null, "work_date=?", new String[]{date.toString()}, null, null, null)) {
            return c.moveToFirst() ? attendance(c) : null;
        }
    }

    public HoursEntry hoursFor(LocalDate date) {
        try (Cursor c = getReadableDatabase().query("hours", null, "work_date=?", new String[]{date.toString()}, null, null, null)) {
            return c.moveToFirst() ? hours(c) : null;
        }
    }

    public List<AttendanceEntry> attendanceBetween(LocalDate from, LocalDate to) {
        List<AttendanceEntry> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("attendance", null, "work_date>=? AND work_date<=?", new String[]{from.toString(), to.toString()}, null, null, "work_date DESC")) {
            while (c.moveToNext()) result.add(attendance(c));
        }
        return result;
    }

    public List<HoursEntry> hoursBetween(LocalDate from, LocalDate to) {
        List<HoursEntry> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("hours", null, "work_date>=? AND work_date<=?", new String[]{from.toString(), to.toString()}, null, null, "work_date DESC")) {
            while (c.moveToNext()) result.add(hours(c));
        }
        return result;
    }

    public LocalDate earliestDate(String table) {
        String safe = table.equals("hours") ? "hours" : "attendance";
        try (Cursor c = getReadableDatabase().rawQuery("SELECT MIN(work_date) FROM " + safe, null)) {
            if (c.moveToFirst() && !c.isNull(0)) return LocalDate.parse(c.getString(0));
        }
        return LocalDate.now();
    }

    public void deleteAttendance(long id) { getWritableDatabase().delete("attendance", "id=?", new String[]{String.valueOf(id)}); }
    public void deleteHours(long id) { getWritableDatabase().delete("hours", "id=?", new String[]{String.valueOf(id)}); }

    private AttendanceEntry attendance(Cursor c) {
        return new AttendanceEntry(c.getLong(c.getColumnIndexOrThrow("id")), LocalDate.parse(c.getString(c.getColumnIndexOrThrow("work_date"))), c.getString(c.getColumnIndexOrThrow("type")), c.getInt(c.getColumnIndexOrThrow("late_minutes")));
    }

    private HoursEntry hours(Cursor c) {
        return new HoursEntry(c.getLong(c.getColumnIndexOrThrow("id")), LocalDate.parse(c.getString(c.getColumnIndexOrThrow("work_date"))), c.getInt(c.getColumnIndexOrThrow("minutes")));
    }
}
