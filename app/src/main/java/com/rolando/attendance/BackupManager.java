package com.rolando.attendance;

import android.content.ContentResolver;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

public final class BackupManager {
    private BackupManager() { }

    public static String createJson(AttendanceDb db) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("format", "attendance-backup");
        root.put("version", 2);
        root.put("exportedAt", System.currentTimeMillis());

        JSONArray attendance = new JSONArray();
        for (AttendanceDb.AttendanceEntry e : db.allAttendance()) {
            JSONObject item = new JSONObject();
            item.put("date", e.date.toString()); item.put("type", e.type);
            item.put("lateMinutes", e.lateMinutes); item.put("modifiedAt", e.modifiedAt);
            attendance.put(item);
        }
        root.put("attendance", attendance);

        JSONArray hours = new JSONArray();
        for (AttendanceDb.HoursEntry e : db.allHours()) {
            JSONObject item = new JSONObject();
            item.put("date", e.date.toString()); item.put("minutes", e.minutes); item.put("modifiedAt", e.modifiedAt);
            hours.put(item);
        }
        root.put("hours", hours);

        JSONArray deleted = new JSONArray();
        for (AttendanceDb.Tombstone t : db.allTombstones()) {
            JSONObject item = new JSONObject();
            item.put("table", t.table); item.put("date", t.date.toString()); item.put("deletedAt", t.deletedAt);
            deleted.put(item);
        }
        root.put("deleted", deleted);
        return root.toString(2);
    }

    public static int mergeJson(AttendanceDb db, String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        if (!"attendance-backup".equals(root.optString("format"))) throw new JSONException("Not an Attendance backup");
        int merged = 0;
        JSONArray attendance = root.optJSONArray("attendance");
        if (attendance != null) for (int i = 0; i < attendance.length(); i++) {
            JSONObject item = attendance.getJSONObject(i);
            db.mergeAttendance(LocalDate.parse(item.getString("date")), item.getString("type"), item.optInt("lateMinutes", 0), item.optLong("modifiedAt", 1));
            merged++;
        }
        JSONArray hours = root.optJSONArray("hours");
        if (hours != null) for (int i = 0; i < hours.length(); i++) {
            JSONObject item = hours.getJSONObject(i);
            db.mergeHours(LocalDate.parse(item.getString("date")), item.getInt("minutes"), item.optLong("modifiedAt", 1));
            merged++;
        }
        JSONArray deleted = root.optJSONArray("deleted");
        if (deleted != null) for (int i = 0; i < deleted.length(); i++) {
            JSONObject item = deleted.getJSONObject(i);
            db.applyTombstone(item.getString("table"), LocalDate.parse(item.getString("date")), item.getLong("deletedAt"));
        }
        return merged;
    }

    public static void write(ContentResolver resolver, Uri uri, String json) throws IOException {
        try (OutputStream out = resolver.openOutputStream(uri, "wt")) {
            if (out == null) throw new IOException("Unable to open backup file");
            out.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }

    public static String read(ContentResolver resolver, Uri uri) throws IOException {
        StringBuilder result = new StringBuilder();
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) throw new IOException("Unable to open backup file");
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) result.append(line).append('\n');
        }
        return result.toString();
    }
}
