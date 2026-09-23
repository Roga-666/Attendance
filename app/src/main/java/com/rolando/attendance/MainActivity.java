package com.rolando.attendance;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int NAVY = Color.rgb(16, 28, 44);
    private static final int TEAL = Color.rgb(0, 175, 155);
    private static final int CORAL = Color.rgb(255, 90, 82);
    private static final DateTimeFormatter FULL_DATE = DateTimeFormatter.ofPattern("EEEE, MMMM d, uuuu", Locale.US);
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("EEE, MMM d, uuuu", Locale.US);
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM uuuu", Locale.US);
    private static final int EXPORT_BACKUP = 401;
    private static final int IMPORT_BACKUP = 402;
    private static final int DRIVE_CREATE = 403;
    private static final int DRIVE_OPEN = 404;

    private enum Mode { TARDY, HOURS, SETTINGS }
    private enum Filter {
        DAYS_30("Last 30 days"), MONTH("This month"), YEAR("This year"), ALL("All time");
        final String label;
        Filter(String label) { this.label = label; }
    }

    private AttendanceDb db;
    private SharedPreferences prefs;
    private boolean darkMode;
    private int PAPER, INK, MUTED, CARD_COLOR, BORDER;
    private FrameLayout root;
    private LinearLayout content;
    private LinearLayout drawer;
    private View scrim;
    private boolean drawerOpen;
    private float touchDownX, touchDownY;
    private Mode mode = Mode.TARDY;
    private Filter tardyFilter = Filter.DAYS_30;
    private Filter hoursFilter = Filter.DAYS_30;
    private YearMonth selectedTardyMonth = YearMonth.now();
    private YearMonth selectedHoursMonth = YearMonth.now();
    private int selectedTardyYear = LocalDate.now().getYear();
    private int selectedHoursYear = LocalDate.now().getYear();
    private LocalDate selectedTardyDate = LocalDate.now();
    private LocalDate selectedHoursDate = LocalDate.now();

    @Override protected void onCreate(Bundle state) {
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        darkMode = prefs.getBoolean("dark_mode", false);
        setTheme(darkMode ? R.style.AppThemeDark : R.style.AppTheme);
        super.onCreate(state);
        PAPER = darkMode ? Color.rgb(16, 21, 29) : Color.rgb(245, 247, 250);
        INK = darkMode ? Color.rgb(235, 240, 246) : Color.rgb(35, 45, 58);
        MUTED = darkMode ? Color.rgb(166, 179, 193) : Color.rgb(102, 116, 132);
        CARD_COLOR = darkMode ? Color.rgb(28, 37, 50) : Color.WHITE;
        BORDER = darkMode ? Color.rgb(58, 70, 85) : Color.rgb(213, 221, 230);
        Window window = getWindow();
        window.setStatusBarColor(NAVY);
        window.setNavigationBarColor(NAVY);
        db = new AttendanceDb(this);
        buildShell();
        if ("HOURS".equals(prefs.getString("default_mode", "TARDY"))) {
            mode = Mode.HOURS; showHours();
        } else {
            mode = Mode.TARDY; showTardy();
        }
        if (prefs.getBoolean("drive_sync", false) && prefs.contains("drive_uri")) syncDrive(false);
    }

    private void buildShell() {
        root = new FrameLayout(this);
        root.setBackgroundColor(PAPER);
        root.setFitsSystemWindows(true);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, matchFrame());

        scrim = new View(this);
        scrim.setBackgroundColor(Color.argb(145, 0, 0, 0));
        scrim.setVisibility(View.GONE);
        scrim.setOnClickListener(v -> closeDrawer());
        root.addView(scrim, matchFrame());

        drawer = new LinearLayout(this);
        drawer.setOrientation(LinearLayout.VERTICAL);
        drawer.setPadding(dp(18), dp(24), dp(18), dp(18));
        drawer.setBackgroundColor(NAVY);
        FrameLayout.LayoutParams drawerParams = new FrameLayout.LayoutParams(dp(286), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START);
        root.addView(drawer, drawerParams);
        buildDrawer();
        drawer.post(() -> drawer.setTranslationX(-drawer.getWidth()));
        setContentView(root);
    }

    private void buildDrawer() {
        TextView icon = label("◷ !", 34, Color.WHITE, true);
        icon.setGravity(Gravity.CENTER_VERTICAL);
        drawer.addView(icon, lpMatchWrap(dp(12)));
        TextView app = label("Attendance", 23, Color.WHITE, true);
        drawer.addView(app, lpMatchWrap(dp(2)));
        TextView tag = label("Keep your own record.", 13, Color.rgb(174, 201, 204), false);
        drawer.addView(tag, lpMatchWrap(dp(30)));
        drawer.addView(navButton("!   Tardy & call-outs", () -> { mode = Mode.TARDY; closeDrawer(); showTardy(); }));
        drawer.addView(navButton("◷   Hours", () -> { mode = Mode.HOURS; closeDrawer(); showHours(); }));
        drawer.addView(navButton("⚙   Settings", () -> { mode = Mode.SETTINGS; closeDrawer(); showSettings(); }));
        Space space = new Space(this);
        drawer.addView(space, new LinearLayout.LayoutParams(1, 0, 1));
        TextView privacy = label("Private by default • Drive sync optional", 12, Color.rgb(154, 180, 185), false);
        drawer.addView(privacy);
    }

    private Button navButton(String text, Runnable action) {
        Button b = button(text, NAVY, Color.WHITE);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setPadding(dp(16), 0, dp(12), 0);
        b.setOnClickListener(v -> action.run());
        b.setBackground(roundRect(Color.rgb(27, 46, 66), 14));
        LinearLayout.LayoutParams p = lpMatch(dp(54), dp(8));
        b.setLayoutParams(p);
        return b;
    }

    private void toolbar(String title) {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10), dp(6), dp(16), dp(6));
        bar.setBackgroundColor(NAVY);
        Button menu = button("☰", NAVY, Color.WHITE);
        menu.setTextSize(25);
        menu.setContentDescription("Open navigation");
        menu.setOnClickListener(v -> openDrawer());
        bar.addView(menu, new LinearLayout.LayoutParams(dp(54), dp(54)));
        TextView heading = label(title, 20, Color.WHITE, true);
        bar.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        content.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));
    }

    private LinearLayout page(String title, String subtitle) {
        content.removeAllViews();
        toolbar(title);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(22), dp(18), dp(40));
        body.addView(label(subtitle, 14, MUTED, false), lpMatchWrap(dp(18)));
        scroll.addView(body);
        content.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return body;
    }

    private void showTardy() {
        LinearLayout body = page("Tardy", "Record a tardy or call-out for today—or choose an earlier date.");
        body.addView(sectionTitle("DATE"));
        Button date = dateButton(selectedTardyDate);
        date.setOnClickListener(v -> pickDate(selectedTardyDate, picked -> { selectedTardyDate = picked; showTardy(); }));
        body.addView(date, lpMatch(dp(58), dp(18)));

        AttendanceDb.AttendanceEntry existing = db.attendanceFor(selectedTardyDate);
        if (existing != null) {
            String extra = existing.type.equals(AttendanceDb.TARDY) && existing.lateMinutes > 0 ? " • " + PdfExporter.duration(existing.lateMinutes) + " late" : "";
            TextView saved = label("Saved: " + existing.type + extra + ". Saving again will update it.", 13, TEAL, true);
            body.addView(saved, lpMatchWrap(dp(14)));
        }

        body.addView(sectionTitle("SUMMARY"));
        body.addView(filterBar(tardyFilter, true, f -> { tardyFilter = f; showTardy(); }), lpMatchWrap(dp(14)));
        LocalDate[] range = range(tardyFilter, AttendanceDb.TABLE_ATTENDANCE, true);
        List<AttendanceDb.AttendanceEntry> entries = db.attendanceBetween(range[0], range[1]);
        int tardies = 0, calls = 0, lateTotal = 0;
        for (AttendanceDb.AttendanceEntry e : entries) {
            if (e.type.equals(AttendanceDb.TARDY)) { tardies++; lateTotal += e.lateMinutes; } else calls++;
        }
        LinearLayout stats = horizontal();
        stats.addView(statCard(String.valueOf(tardies), "Tardies", CORAL), new LinearLayout.LayoutParams(0, dp(106), 1));
        stats.addView(gap(dp(10)));
        stats.addView(statCard(String.valueOf(calls), "Call-outs", NAVY), new LinearLayout.LayoutParams(0, dp(106), 1));
        body.addView(stats, lpMatchWrap(dp(10)));
        if (prefs.getBoolean("track_late_minutes", false) && lateTotal > 0) body.addView(infoCard(PdfExporter.duration(lateTotal) + " total late in this filter"), lpMatchWrap(dp(14)));

        body.addView(sectionTitle("MARK THIS DATE"));
        boolean trackLate = prefs.getBoolean("track_late_minutes", false);
        EditText lateHours = numberField("Hours", 2);
        EditText lateMinutes = numberField("Minutes", 2);
        if (trackLate) {
            body.addView(label("How late? (optional)", 13, MUTED, true), lpMatchWrap(dp(8)));
            LinearLayout duration = horizontal();
            duration.addView(lateHours, new LinearLayout.LayoutParams(0, dp(58), 1));
            duration.addView(gap(dp(10)));
            duration.addView(lateMinutes, new LinearLayout.LayoutParams(0, dp(58), 1));
            if (existing != null && existing.type.equals(AttendanceDb.TARDY)) {
                lateHours.setText(String.valueOf(existing.lateMinutes / 60));
                lateMinutes.setText(String.valueOf(existing.lateMinutes % 60));
            }
            body.addView(duration, lpMatchWrap(dp(10)));
        }

        LinearLayout actions = horizontal();
        Button call = button("Called Out", NAVY, Color.WHITE);
        call.setOnClickListener(v -> {
            confirm("Mark called out?", "Save a call-out for " + SHORT_DATE.format(selectedTardyDate) + "?", () -> {
                db.saveAttendance(selectedTardyDate, AttendanceDb.CALLED_OUT, 0);
                syncDriveAfterChange();
                toast("Call-out saved"); showTardy();
            });
        });
        Button tardy = button("Tardy", CORAL, Color.WHITE);
        tardy.setOnClickListener(v -> {
            int mins = trackLate ? readDuration(lateHours, lateMinutes) : 0;
            if (mins < 0) return;
            db.saveAttendance(selectedTardyDate, AttendanceDb.TARDY, mins);
            syncDriveAfterChange();
            toast("Tardy saved for " + SHORT_DATE.format(selectedTardyDate));
            showTardy();
        });
        actions.addView(call, new LinearLayout.LayoutParams(0, dp(56), 1));
        actions.addView(gap(dp(10)));
        actions.addView(tardy, new LinearLayout.LayoutParams(0, dp(56), 1));
        body.addView(actions, lpMatchWrap(dp(12)));
        addAttendanceHistory(body, entries);
        Button export = outlineButton("Export this view as PDF");
        export.setOnClickListener(v -> exportAttendance(range, entries));
        body.addView(export, lpMatch(dp(56), dp(4)));
    }

    private void showHours() {
        LinearLayout body = page("Hours", "Add your hours worked and keep a running total.");
        body.addView(sectionTitle("DATE"));
        Button date = dateButton(selectedHoursDate);
        date.setOnClickListener(v -> pickDate(selectedHoursDate, picked -> { selectedHoursDate = picked; showHours(); }));
        body.addView(date, lpMatch(dp(58), dp(18)));

        AttendanceDb.HoursEntry existing = db.hoursFor(selectedHoursDate);
        body.addView(sectionTitle("HOURS WORKED"));
        LinearLayout duration = horizontal();
        EditText hours = numberField("Hours", 3);
        EditText minutes = numberField("Minutes", 2);
        if (existing != null) {
            hours.setText(String.valueOf(existing.minutes / 60));
            minutes.setText(String.valueOf(existing.minutes % 60));
        }
        duration.addView(hours, new LinearLayout.LayoutParams(0, dp(58), 1));
        duration.addView(gap(dp(10)));
        duration.addView(minutes, new LinearLayout.LayoutParams(0, dp(58), 1));
        body.addView(duration, lpMatchWrap(dp(10)));
        Button save = button(existing == null ? "Save hours" : "Update hours", TEAL, Color.WHITE);
        save.setOnClickListener(v -> {
            int total = readDuration(hours, minutes);
            if (total <= 0) { if (total == 0) toast("Enter the time you worked"); return; }
            if (total > 24 * 60) { toast("Hours for one date cannot exceed 24"); return; }
            db.saveHours(selectedHoursDate, total);
            syncDriveAfterChange();
            toast("Hours saved for " + SHORT_DATE.format(selectedHoursDate)); showHours();
        });
        body.addView(save, lpMatch(dp(56), dp(24)));

        body.addView(sectionTitle("TOTAL"));
        body.addView(filterBar(hoursFilter, false, f -> { hoursFilter = f; showHours(); }), lpMatchWrap(dp(14)));
        LocalDate[] range = range(hoursFilter, AttendanceDb.TABLE_HOURS, false);
        List<AttendanceDb.HoursEntry> entries = db.hoursBetween(range[0], range[1]);
        int total = 0;
        for (AttendanceDb.HoursEntry e : entries) total += e.minutes;
        body.addView(statCard(PdfExporter.duration(total), filterLabel(hoursFilter, false), TEAL), lpMatch(dp(112), dp(18)));
        addHoursHistory(body, entries);
        Button export = outlineButton("Export this view as PDF");
        export.setOnClickListener(v -> exportHours(range, entries));
        body.addView(export, lpMatch(dp(56), dp(4)));
    }

    private void showSettings() {
        LinearLayout body = page("Settings", "Personalize Attendance and protect your records.");

        body.addView(sectionTitle("APPEARANCE"));
        body.addView(toggleCard("Dark mode", "Use a dark color scheme throughout the app.", darkMode, (v, checked) -> {
            prefs.edit().putBoolean("dark_mode", checked).apply(); recreate();
        }), lpMatchWrap(dp(18)));

        body.addView(sectionTitle("DEFAULT OPENING SCREEN"));
        LinearLayout startCard = card();
        startCard.addView(label("Choose where Attendance opens", 16, INK, true), lpMatchWrap(dp(10)));
        LinearLayout startButtons = horizontal();
        boolean startsHours = "HOURS".equals(prefs.getString("default_mode", "TARDY"));
        Button startTardy = choiceButton("Tardy", !startsHours);
        Button startHours = choiceButton("Hours", startsHours);
        startTardy.setOnClickListener(v -> { prefs.edit().putString("default_mode", "TARDY").apply(); showSettings(); });
        startHours.setOnClickListener(v -> { prefs.edit().putString("default_mode", "HOURS").apply(); showSettings(); });
        startButtons.addView(startTardy, new LinearLayout.LayoutParams(0, dp(48), 1));
        startButtons.addView(gap(dp(10)));
        startButtons.addView(startHours, new LinearLayout.LayoutParams(0, dp(48), 1));
        startCard.addView(startButtons);
        body.addView(startCard, lpMatchWrap(dp(18)));

        body.addView(sectionTitle("TARDY DETAILS"));
        body.addView(toggleCard("Track exact late time", "Show hours and minutes when marking a tardy.", prefs.getBoolean("track_late_minutes", false), (v, checked) -> prefs.edit().putBoolean("track_late_minutes", checked).apply()), lpMatchWrap(dp(18)));

        body.addView(sectionTitle("BACKUP & TRANSFER"));
        LinearLayout backupCard = card();
        backupCard.addView(label("Move all data between devices", 16, INK, true));
        backupCard.addView(label("Export one backup file containing tardies, call-outs, hours, and deletions. Importing merges the newest records.", 13, MUTED, false), lpMatchWrap(dp(12)));
        LinearLayout backupButtons = horizontal();
        Button export = outlineButton("Export data");
        export.setOnClickListener(v -> startBackupExport());
        Button importData = outlineButton("Import data");
        importData.setOnClickListener(v -> confirm("Import attendance data?", "The newest records from the selected backup will be merged with this device.", this::startBackupImport));
        backupButtons.addView(export, new LinearLayout.LayoutParams(0, dp(50), 1));
        backupButtons.addView(gap(dp(10)));
        backupButtons.addView(importData, new LinearLayout.LayoutParams(0, dp(50), 1));
        backupCard.addView(backupButtons);
        body.addView(backupCard, lpMatchWrap(dp(18)));

        body.addView(sectionTitle("GOOGLE DRIVE"));
        boolean driveEnabled = prefs.getBoolean("drive_sync", false) && prefs.contains("drive_uri");
        body.addView(toggleCard("Google Drive sync", "Link a backup file in Google Drive. Attendance merges it on startup and updates it after changes.", driveEnabled, (v, checked) -> {
            if (checked) chooseDriveSetup();
            else { prefs.edit().putBoolean("drive_sync", false).remove("drive_uri").apply(); toast("Google Drive sync disconnected"); showSettings(); }
        }), lpMatchWrap(dp(10)));
        if (driveEnabled) {
            Button sync = button("Sync now", TEAL, Color.WHITE);
            sync.setOnClickListener(v -> syncDrive(true));
            body.addView(sync, lpMatch(dp(52), dp(18)));
        }

        body.addView(sectionTitle("ABOUT YOUR DATA"));
        body.addView(infoCard("Your database stays private inside the app. Data leaves the device only when you export it or enable a Drive backup file."));
        TextView version = label("Attendance 1.1.0", 12, MUTED, false);
        body.addView(version, lpMatchWrap(dp(16)));
    }

    private void addAttendanceHistory(LinearLayout body, List<AttendanceDb.AttendanceEntry> entries) {
        body.addView(sectionTitle("HISTORY"));
        if (entries.isEmpty()) { body.addView(emptyCard("No tardies or call-outs in this range."), lpMatchWrap(dp(18))); return; }
        for (AttendanceDb.AttendanceEntry e : entries) {
            String detail = e.type + (e.type.equals(AttendanceDb.TARDY) && e.lateMinutes > 0 ? " • " + PdfExporter.duration(e.lateMinutes) + " late" : "");
            body.addView(historyRow(SHORT_DATE.format(e.date), detail, () -> { selectedTardyDate = e.date; showTardy(); }, () -> confirmDelete(() -> { db.deleteAttendance(e.id); syncDriveAfterChange(); showTardy(); })), lpMatchWrap(dp(8)));
        }
    }

    private void addHoursHistory(LinearLayout body, List<AttendanceDb.HoursEntry> entries) {
        body.addView(sectionTitle("HISTORY"));
        if (entries.isEmpty()) { body.addView(emptyCard("No hours saved in this range."), lpMatchWrap(dp(18))); return; }
        for (AttendanceDb.HoursEntry e : entries) {
            body.addView(historyRow(SHORT_DATE.format(e.date), PdfExporter.duration(e.minutes), () -> { selectedHoursDate = e.date; showHours(); }, () -> confirmDelete(() -> { db.deleteHours(e.id); syncDriveAfterChange(); showHours(); })), lpMatchWrap(dp(8)));
        }
    }

    private LinearLayout toggleCard(String title, String subtitle, boolean checked, CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout row = card();
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(label(title, 16, INK, true));
        copy.addView(label(subtitle, 13, MUTED, false));
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Switch toggle = new Switch(this);
        toggle.setChecked(checked);
        toggle.setContentDescription(title);
        toggle.setOnCheckedChangeListener(listener);
        row.addView(toggle, new LinearLayout.LayoutParams(dp(58), ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private Button choiceButton(String text, boolean selected) {
        Button button = button(text, selected ? TEAL : CARD_COLOR, selected ? Color.WHITE : INK);
        button.setBackground(selected ? roundRect(TEAL, 14) : bordered(CARD_COLOR, BORDER, 14));
        return button;
    }

    private void startBackupExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "Attendance-backup-" + LocalDate.now() + ".json");
        startActivityForResult(intent, EXPORT_BACKUP);
    }

    private void startBackupImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, IMPORT_BACKUP);
    }

    private void chooseDriveSetup() {
        new AlertDialog.Builder(this)
                .setTitle("Set up Google Drive sync")
                .setMessage("In the next file picker, choose Google Drive. You can create a new Attendance backup or link an existing one.")
                .setItems(new String[]{"Create new Drive backup", "Link existing Drive backup"}, (dialog, which) -> {
                    Intent intent = new Intent(which == 0 ? Intent.ACTION_CREATE_DOCUMENT : Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/json");
                    if (which == 0) intent.putExtra(Intent.EXTRA_TITLE, "Attendance-Drive-Backup.json");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                    startActivityForResult(intent, which == 0 ? DRIVE_CREATE : DRIVE_OPEN);
                })
                .setNegativeButton("Cancel", (dialog, which) -> showSettings())
                .show();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            if (requestCode == DRIVE_CREATE || requestCode == DRIVE_OPEN) showSettings();
            return;
        }
        Uri uri = data.getData();
        if (requestCode == EXPORT_BACKUP) {
            writeBackup(uri, "Backup exported");
        } else if (requestCode == IMPORT_BACKUP) {
            importBackup(uri);
        } else if (requestCode == DRIVE_CREATE || requestCode == DRIVE_OPEN) {
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (SecurityException ignored) { }
            prefs.edit().putString("drive_uri", uri.toString()).putBoolean("drive_sync", true).apply();
            if (requestCode == DRIVE_CREATE) writeBackup(uri, "Google Drive sync connected");
            else syncDrive(true);
            showSettings();
        }
    }

    private void writeBackup(Uri uri, String successMessage) {
        new Thread(() -> {
            try {
                BackupManager.write(getContentResolver(), uri, BackupManager.createJson(db));
                runOnUiThread(() -> toast(successMessage));
            } catch (Exception e) {
                runOnUiThread(() -> toast("Could not write the backup file"));
            }
        }).start();
    }

    private void importBackup(Uri uri) {
        new Thread(() -> {
            try {
                int merged = BackupManager.mergeJson(db, BackupManager.read(getContentResolver(), uri));
                runOnUiThread(() -> {
                    toast("Backup imported • " + merged + " records checked");
                    refreshCurrentMode();
                    syncDriveAfterChange();
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast("That file is not a valid Attendance backup"));
            }
        }).start();
    }

    private void syncDriveAfterChange() {
        if (prefs.getBoolean("drive_sync", false) && prefs.contains("drive_uri")) syncDrive(false);
    }

    private void syncDrive(boolean showFeedback) {
        String savedUri = prefs.getString("drive_uri", null);
        if (savedUri == null) {
            if (showFeedback) toast("Connect a Google Drive backup first");
            return;
        }
        Uri uri = Uri.parse(savedUri);
        new Thread(() -> {
            try {
                String cloud = BackupManager.read(getContentResolver(), uri);
                if (!cloud.trim().isEmpty()) BackupManager.mergeJson(db, cloud);
                BackupManager.write(getContentResolver(), uri, BackupManager.createJson(db));
                runOnUiThread(() -> {
                    if (showFeedback) toast("Google Drive sync complete");
                    refreshCurrentMode();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (showFeedback) toast("Could not sync the Google Drive backup");
                });
            }
        }).start();
    }

    private void refreshCurrentMode() {
        if (mode == Mode.TARDY) showTardy();
        else if (mode == Mode.HOURS) showHours();
        else showSettings();
    }

    private View historyRow(String title, String detail, Runnable edit, Runnable delete) {
        LinearLayout row = card();
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(label(title, 15, INK, true));
        copy.addView(label(detail, 13, MUTED, false));
        copy.setOnClickListener(v -> edit.run());
        row.addView(copy, new LinearLayout.LayoutParams(0, dp(54), 1));
        Button remove = button("×", Color.TRANSPARENT, CORAL);
        remove.setTextSize(26);
        remove.setContentDescription("Delete entry");
        remove.setOnClickListener(v -> delete.run());
        row.addView(remove, new LinearLayout.LayoutParams(dp(48), dp(48)));
        return row;
    }

    private HorizontalScrollView filterBar(Filter selected, boolean tardyMode, FilterAction action) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = horizontal();
        for (Filter f : Filter.values()) {
            String text = f == Filter.MONTH ? (f == selected ? filterLabel(f, tardyMode) : "Month") + " ▾" : f == Filter.YEAR ? (f == selected ? filterLabel(f, tardyMode) : "Year") + " ▾" : f.label;
            Button b = button(text, f == selected ? NAVY : CARD_COLOR, f == selected ? Color.WHITE : INK);
            b.setBackground(f == selected ? roundRect(NAVY, 20) : bordered(CARD_COLOR, BORDER, 20));
            b.setOnClickListener(v -> {
                if (f == Filter.MONTH) showMonthPicker(tardyMode, action);
                else if (f == Filter.YEAR) showYearPicker(tardyMode, action);
                else action.select(f);
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
            p.setMarginEnd(dp(8)); row.addView(b, p);
        }
        scroll.addView(row);
        return scroll;
    }

    private LocalDate[] range(Filter filter, String table, boolean tardyMode) {
        LocalDate today = LocalDate.now();
        switch (filter) {
            case DAYS_30: return new LocalDate[]{today.minusDays(29), today};
            case MONTH:
                YearMonth month = tardyMode ? selectedTardyMonth : selectedHoursMonth;
                LocalDate monthEnd = month.equals(YearMonth.now()) ? today : month.atEndOfMonth();
                return new LocalDate[]{month.atDay(1), monthEnd};
            case YEAR:
                int year = tardyMode ? selectedTardyYear : selectedHoursYear;
                return new LocalDate[]{LocalDate.of(year, 1, 1), year == today.getYear() ? today : LocalDate.of(year, 12, 31)};
            default: return new LocalDate[]{db.earliestDate(table), today};
        }
    }

    private String filterLabel(Filter filter, boolean tardyMode) {
        if (filter == Filter.MONTH) return MONTH_LABEL.format(tardyMode ? selectedTardyMonth : selectedHoursMonth);
        if (filter == Filter.YEAR) return String.valueOf(tardyMode ? selectedTardyYear : selectedHoursYear);
        return filter.label;
    }

    private void showMonthPicker(boolean tardyMode, FilterAction action) {
        YearMonth selected = tardyMode ? selectedTardyMonth : selectedHoursMonth;
        LinearLayout pickers = horizontal();
        pickers.setPadding(dp(16), dp(4), dp(16), 0);
        NumberPicker month = new NumberPicker(this);
        month.setMinValue(1); month.setMaxValue(12); month.setValue(selected.getMonthValue());
        month.setDisplayedValues(new String[]{"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"});
        NumberPicker year = new NumberPicker(this);
        year.setMinValue(2000); year.setMaxValue(LocalDate.now().getYear()); year.setValue(selected.getYear());
        pickers.addView(month, new LinearLayout.LayoutParams(0, dp(180), 1));
        pickers.addView(year, new LinearLayout.LayoutParams(0, dp(180), 1));
        new AlertDialog.Builder(this).setTitle("Choose month").setView(pickers).setNegativeButton("Cancel", null).setPositiveButton("Use month", (d, w) -> {
            YearMonth choice = YearMonth.of(year.getValue(), month.getValue());
            if (choice.isAfter(YearMonth.now())) { toast("Choose the current month or an earlier month"); return; }
            if (tardyMode) selectedTardyMonth = choice; else selectedHoursMonth = choice;
            action.select(Filter.MONTH);
        }).show();
    }

    private void showYearPicker(boolean tardyMode, FilterAction action) {
        NumberPicker year = new NumberPicker(this);
        year.setMinValue(2000); year.setMaxValue(LocalDate.now().getYear());
        year.setValue(tardyMode ? selectedTardyYear : selectedHoursYear);
        year.setWrapSelectorWheel(false);
        new AlertDialog.Builder(this).setTitle("Choose year").setView(year).setNegativeButton("Cancel", null).setPositiveButton("Use year", (d, w) -> {
            if (tardyMode) selectedTardyYear = year.getValue(); else selectedHoursYear = year.getValue();
            action.select(Filter.YEAR);
        }).show();
    }

    private void exportAttendance(LocalDate[] range, List<AttendanceDb.AttendanceEntry> entries) {
        try { share(PdfExporter.attendance(this, filterLabel(tardyFilter, true), range[0], range[1], entries)); }
        catch (IOException e) { toast("Could not create the PDF"); }
    }

    private void exportHours(LocalDate[] range, List<AttendanceDb.HoursEntry> entries) {
        try { share(PdfExporter.hours(this, filterLabel(hoursFilter, false), range[0], range[1], entries)); }
        catch (IOException e) { toast("Could not create the PDF"); }
    }

    private void share(Uri uri) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("application/pdf");
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, "Share attendance PDF"));
    }

    private void pickDate(LocalDate initial, DateAction action) {
        DatePickerDialog dialog = new DatePickerDialog(this, (v, y, m, d) -> action.select(LocalDate.of(y, m + 1, d)), initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth());
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
    }

    private Button dateButton(LocalDate date) {
        Button b = button(FULL_DATE.format(date) + "   ▾", CARD_COLOR, INK);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setPadding(dp(18), 0, dp(14), 0);
        b.setBackground(bordered(CARD_COLOR, BORDER, 14));
        return b;
    }

    private LinearLayout statCard(String value, String caption, int accent) {
        LinearLayout card = card();
        card.setGravity(Gravity.CENTER_VERTICAL);
        TextView number = label(value, value.length() > 8 ? 25 : 34, accent, true);
        card.addView(number);
        card.addView(label(caption, 13, MUTED, false));
        return card;
    }

    private LinearLayout infoCard(String text) {
        LinearLayout card = card();
        card.setBackground(bordered(darkMode ? Color.rgb(20, 55, 57) : Color.rgb(232, 247, 244), darkMode ? Color.rgb(45, 105, 102) : Color.rgb(182, 228, 220), 14));
        card.addView(label(text, 13, darkMode ? Color.rgb(166, 231, 222) : Color.rgb(20, 105, 95), false));
        return card;
    }

    private LinearLayout emptyCard(String text) {
        LinearLayout card = card();
        card.addView(label(text, 13, MUTED, false));
        return card;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(roundRect(CARD_COLOR, 14));
        card.setElevation(dp(1));
        return card;
    }

    private TextView sectionTitle(String text) {
        TextView v = label(text, 12, MUTED, true);
        v.setLetterSpacing(.09f);
        v.setPadding(0, dp(12), 0, dp(9));
        return v;
    }

    private TextView label(String text, float size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text); v.setTextSize(size); v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private Button button(String text, int background, int color) {
        Button b = new Button(this);
        b.setText(text); b.setTextSize(14); b.setTextColor(color); b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setPadding(dp(14), 0, dp(14), 0);
        b.setBackground(roundRect(background, 14));
        b.setStateListAnimator(null);
        return b;
    }

    private Button outlineButton(String text) {
        int accent = darkMode ? TEAL : NAVY;
        Button b = button(text, Color.TRANSPARENT, accent);
        b.setBackground(bordered(Color.TRANSPARENT, accent, 14));
        return b;
    }

    private EditText numberField(String hint, int maxDigits) {
        EditText e = new EditText(this);
        e.setHint(hint); e.setTextSize(16); e.setTextColor(INK); e.setHintTextColor(MUTED);
        e.setSingleLine(true); e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setPadding(dp(16), 0, dp(12), 0);
        e.setBackground(bordered(CARD_COLOR, BORDER, 14));
        e.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(maxDigits)});
        return e;
    }

    private int readDuration(EditText hours, EditText minutes) {
        try {
            int h = hours.getText().length() == 0 ? 0 : Integer.parseInt(hours.getText().toString());
            int m = minutes.getText().length() == 0 ? 0 : Integer.parseInt(minutes.getText().toString());
            if (m > 59) { toast("Minutes must be between 0 and 59"); return -1; }
            return h * 60 + m;
        } catch (NumberFormatException ex) { toast("Please enter a valid time"); return -1; }
    }

    private GradientDrawable roundRect(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }

    private GradientDrawable bordered(int fill, int stroke, int radius) {
        GradientDrawable d = roundRect(fill, radius); d.setStroke(dp(1), stroke); return d;
    }

    private LinearLayout horizontal() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private Space gap(int width) { Space s = new Space(this); s.setLayoutParams(new LinearLayout.LayoutParams(width, 1)); return s; }
    private FrameLayout.LayoutParams matchFrame() { return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT); }
    private LinearLayout.LayoutParams lpMatch(int height, int bottom) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height); p.bottomMargin = bottom; return p; }
    private LinearLayout.LayoutParams lpMatchWrap(int bottom) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); p.bottomMargin = bottom; return p; }
    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    private void openDrawer() {
        drawerOpen = true; scrim.setVisibility(View.VISIBLE); drawer.bringToFront();
        drawer.animate().translationX(0).setDuration(190).start();
    }

    private void closeDrawer() {
        drawerOpen = false;
        drawer.animate().translationX(-drawer.getWidth()).setDuration(170).withEndAction(() -> { scrim.setVisibility(View.GONE); content.bringToFront(); scrim.bringToFront(); drawer.bringToFront(); }).start();
    }

    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { touchDownX = e.getX(); touchDownY = e.getY(); }
        if (e.getActionMasked() == MotionEvent.ACTION_UP) {
            float dx = e.getX() - touchDownX, dy = Math.abs(e.getY() - touchDownY);
            if (!drawerOpen && touchDownX < dp(36) && dx > dp(70) && Math.abs(dx) > dy) { openDrawer(); return true; }
            if (drawerOpen && dx < -dp(70) && Math.abs(dx) > dy) { closeDrawer(); return true; }
        }
        return super.dispatchTouchEvent(e);
    }

    @Override public void onBackPressed() {
        if (drawerOpen) closeDrawer(); else super.onBackPressed();
    }

    private void confirmDelete(Runnable action) { confirm("Delete this entry?", "This cannot be undone.", action); }
    private void confirm(String title, String message, Runnable action) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("Cancel", null).setPositiveButton("Confirm", (d, w) -> action.run()).show();
    }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }

    private interface DateAction { void select(LocalDate date); }
    private interface FilterAction { void select(Filter filter); }
}
