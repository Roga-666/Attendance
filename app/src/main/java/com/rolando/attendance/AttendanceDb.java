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
    public static final String TABLE_ATTENDANCE = "attendance";
    public static final String TABLE_HOURS = "hours";

    public static final class AttendanceEntry {
        public final long id;
        public final LocalDate date;
        public final String type;
        public final int lateMinutes;
        public final long modifiedAt;

        AttendanceEntry(long id, LocalDate date, String type, int lateMinutes, long modifiedAt) {
            this.id = id; this.date = date; this.type = type; this.lateMinutes = lateMinutes; this.modifiedAt = modifiedAt;
        }
    }

    public static final class HoursEntry {
        public final long id;
        public final LocalDate date;
        public final int minutes;
        public final long modifiedAt;

        HoursEntry(long id, LocalDate date, int minutes, long modifiedAt) {
            this.id = id; this.date = date; this.minutes = minutes; this.modifiedAt = modifiedAt;
        }
    }

    public static final class Tombstone {
        public final String table;
        public final LocalDate date;
        public final long deletedAt;

        Tombstone(String table, LocalDate date, long deletedAt) {
            this.table = table; this.date = date; this.deletedAt = deletedAt;
        }
    }

    public AttendanceDb(Context context) { super(context, "attendance.db", null, 2); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE attendance (id INTEGER PRIMARY KEY AUTOINCREMENT, work_date TEXT NOT NULL UNIQUE, type TEXT NOT NULL, late_minutes INTEGER NOT NULL DEFAULT 0, modified_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE hours (id INTEGER PRIMARY KEY AUTOINCREMENT, work_date TEXT NOT NULL UNIQUE, minutes INTEGER NOT NULL, modified_at INTEGER NOT NULL)");
        createTombstones(db);
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            long now = System.currentTimeMillis();
            db.execSQL("ALTER TABLE attendance ADD COLUMN modified_at INTEGER NOT NULL DEFAULT " + now);
            db.execSQL("ALTER TABLE hours ADD COLUMN modified_at INTEGER NOT NULL DEFAULT " + now);
            createTombstones(db);
        }
    }

    private void createTombstones(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS tombstones (table_name TEXT NOT NULL, work_date TEXT NOT NULL, deleted_at INTEGER NOT NULL, PRIMARY KEY(table_name, work_date))");
    }

    public void saveAttendance(LocalDate date, String type, int lateMinutes) { putAttendance(date, type, lateMinutes, System.currentTimeMillis()); }
    public void saveHours(LocalDate date, int minutes) { putHours(date, minutes, System.currentTimeMillis()); }

    public void mergeAttendance(LocalDate date, String type, int lateMinutes, long modifiedAt) {
        if (modifiedAt > latestTimestamp(TABLE_ATTENDANCE, date)) putAttendance(date, type, lateMinutes, modifiedAt);
    }

    public void mergeHours(LocalDate date, int minutes, long modifiedAt) {
        if (modifiedAt > latestTimestamp(TABLE_HOURS, date)) putHours(date, minutes, modifiedAt);
    }

    private void putAttendance(LocalDate date, String type, int lateMinutes, long modifiedAt) {
        SQLiteDatabase database = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("work_date", date.toString()); values.put("type", type);
        values.put("late_minutes", type.equals(TARDY) ? Math.max(0, lateMinutes) : 0); values.put("modified_at", modifiedAt);
        database.insertWithOnConflict(TABLE_ATTENDANCE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        database.delete("tombstones", "table_name=? AND work_date=?", new String[]{TABLE_ATTENDANCE, date.toString()});
    }

    private void putHours(LocalDate date, int minutes, long modifiedAt) {
        SQLiteDatabase database = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("work_date", date.toString()); values.put("minutes", Math.max(0, minutes)); values.put("modified_at", modifiedAt);
        database.insertWithOnConflict(TABLE_HOURS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        database.delete("tombstones", "table_name=? AND work_date=?", new String[]{TABLE_HOURS, date.toString()});
    }

    public AttendanceEntry attendanceFor(LocalDate date) {
        try (Cursor c = getReadableDatabase().query(TABLE_ATTENDANCE, null, "work_date=?", new String[]{date.toString()}, null, null, null)) { return c.moveToFirst() ? attendance(c) : null; }
    }

    public HoursEntry hoursFor(LocalDate date) {
        try (Cursor c = getReadableDatabase().query(TABLE_HOURS, null, "work_date=?", new String[]{date.toString()}, null, null, null)) { return c.moveToFirst() ? hours(c) : null; }
    }

    public List<AttendanceEntry> attendanceBetween(LocalDate from, LocalDate to) {
        List<AttendanceEntry> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(TABLE_ATTENDANCE, null, "work_date>=? AND work_date<=?", new String[]{from.toString(), to.toString()}, null, null, "work_date DESC")) { while (c.moveToNext()) result.add(attendance(c)); }
        return result;
    }

    public List<HoursEntry> hoursBetween(LocalDate from, LocalDate to) {
        List<HoursEntry> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(TABLE_HOURS, null, "work_date>=? AND work_date<=?", new String[]{from.toString(), to.toString()}, null, null, "work_date DESC")) { while (c.moveToNext()) result.add(hours(c)); }
        return result;
    }

    public List<AttendanceEntry> allAttendance() { return attendanceBetween(LocalDate.of(1970, 1, 1), LocalDate.of(9999, 12, 31)); }
    public List<HoursEntry> allHours() { return hoursBetween(LocalDate.of(1970, 1, 1), LocalDate.of(9999, 12, 31)); }

    public List<Tombstone> allTombstones() {
        List<Tombstone> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("tombstones", null, null, null, null, null, null)) {
            while (c.moveToNext()) result.add(new Tombstone(c.getString(c.getColumnIndexOrThrow("table_name")), LocalDate.parse(c.getString(c.getColumnIndexOrThrow("work_date"))), c.getLong(c.getColumnIndexOrThrow("deleted_at"))));
        }
        return result;
    }

    public LocalDate earliestDate(String table) {
        String safe = table.equals(TABLE_HOURS) ? TABLE_HOURS : TABLE_ATTENDANCE;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT MIN(work_date) FROM " + safe, null)) { if (c.moveToFirst() && !c.isNull(0)) return LocalDate.parse(c.getString(0)); }
        return LocalDate.now();
    }

    public void deleteAttendance(long id) { deleteById(TABLE_ATTENDANCE, id); }
    public void deleteHours(long id) { deleteById(TABLE_HOURS, id); }

    private void deleteById(String table, long id) {
        SQLiteDatabase database = getWritableDatabase();
        try (Cursor c = database.query(table, new String[]{"work_date"}, "id=?", new String[]{String.valueOf(id)}, null, null, null)) {
            if (c.moveToFirst()) applyTombstone(table, LocalDate.parse(c.getString(0)), System.currentTimeMillis());
        }
    }

    public void applyTombstone(String table, LocalDate date, long deletedAt) {
        String safe = table.equals(TABLE_HOURS) ? TABLE_HOURS : TABLE_ATTENDANCE;
        if (deletedAt <= latestTimestamp(safe, date)) return;
        SQLiteDatabase database = getWritableDatabase();
        database.delete(safe, "work_date=?", new String[]{date.toString()});
        ContentValues values = new ContentValues();
        values.put("table_name", safe); values.put("work_date", date.toString()); values.put("deleted_at", deletedAt);
        database.insertWithOnConflict("tombstones", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private long latestTimestamp(String table, LocalDate date) {
        long latest = 0;
        try (Cursor c = getReadableDatabase().query(table, new String[]{"modified_at"}, "work_date=?", new String[]{date.toString()}, null, null, null)) { if (c.moveToFirst()) latest = c.getLong(0); }
        try (Cursor c = getReadableDatabase().query("tombstones", new String[]{"deleted_at"}, "table_name=? AND work_date=?", new String[]{table, date.toString()}, null, null, null)) { if (c.moveToFirst()) latest = Math.max(latest, c.getLong(0)); }
        return latest;
    }

    private AttendanceEntry attendance(Cursor c) {
        return new AttendanceEntry(c.getLong(c.getColumnIndexOrThrow("id")), LocalDate.parse(c.getString(c.getColumnIndexOrThrow("work_date"))), c.getString(c.getColumnIndexOrThrow("type")), c.getInt(c.getColumnIndexOrThrow("late_minutes")), c.getLong(c.getColumnIndexOrThrow("modified_at")));
    }

    private HoursEntry hours(Cursor c) {
        return new HoursEntry(c.getLong(c.getColumnIndexOrThrow("id")), LocalDate.parse(c.getString(c.getColumnIndexOrThrow("work_date"))), c.getInt(c.getColumnIndexOrThrow("minutes")), c.getLong(c.getColumnIndexOrThrow("modified_at")));
    }
}
