package com.rolando.attendance;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
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
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int NAVY = Color.rgb(16, 28, 44);
    private static final int TEAL = Color.rgb(0, 175, 155);
    private static final int CORAL = Color.rgb(255, 90, 82);
    private static final int PAPER = Color.rgb(245, 247, 250);
    private static final int INK = Color.rgb(35, 45, 58);
    private static final int MUTED = Color.rgb(102, 116, 132);
    private static final DateTimeFormatter FULL_DATE = DateTimeFormatter.ofPattern("EEEE, MMMM d, uuuu", Locale.US);
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("EEE, MMM d, uuuu", Locale.US);

    private enum Mode { TARDY, HOURS, SETTINGS }
    private enum Filter {
        DAYS_30("Last 30 days"), MONTH("This month"), YEAR("This year"), ALL("All time");
        final String label;
        Filter(String label) { this.label = label; }
    }

    private AttendanceDb db;
    private SharedPreferences prefs;
    private FrameLayout root;
    private LinearLayout content;
    private LinearLayout drawer;
    private View scrim;
    private boolean drawerOpen;
    private float touchDownX, touchDownY;
    private Mode mode = Mode.TARDY;
    private Filter tardyFilter = Filter.DAYS_30;
    private Filter hoursFilter = Filter.ALL;
    private LocalDate selectedTardyDate = LocalDate.now();
    private LocalDate selectedHoursDate = LocalDate.now();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window window = getWindow();
        window.setStatusBarColor(NAVY);
        window.setNavigationBarColor(NAVY);
        db = new AttendanceDb(this);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        buildShell();
        showTardy();
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
        TextView privacy = label("Stored only on this device", 12, Color.rgb(154, 180, 185), false);
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

        boolean trackLate = prefs.getBoolean("track_late_minutes", false);
        EditText lateHours = numberField("Hours", 2);
        EditText lateMinutes = numberField("Minutes", 2);
        if (trackLate) {
            body.addView(sectionTitle("HOW LATE?  (OPTIONAL)"));
            LinearLayout duration = horizontal();
            duration.addView(lateHours, new LinearLayout.LayoutParams(0, dp(58), 1));
            duration.addView(gap(dp(10)));
            duration.addView(lateMinutes, new LinearLayout.LayoutParams(0, dp(58), 1));
            if (existing != null && existing.type.equals(AttendanceDb.TARDY)) {
                lateHours.setText(String.valueOf(existing.lateMinutes / 60));
                lateMinutes.setText(String.valueOf(existing.lateMinutes % 60));
            }
            body.addView(duration, lpMatchWrap(dp(18)));
        }

        body.addView(sectionTitle("MARK THIS DATE"));
        LinearLayout actions = horizontal();
        Button tardy = button("Mark tardy", CORAL, Color.WHITE);
        tardy.setOnClickListener(v -> {
            int mins = trackLate ? readDuration(lateHours, lateMinutes) : 0;
            if (mins < 0) return;
            db.saveAttendance(selectedTardyDate, AttendanceDb.TARDY, mins);
            toast("Tardy saved for " + SHORT_DATE.format(selectedTardyDate));
            showTardy();
        });
        Button call = button("Called out", NAVY, Color.WHITE);
        call.setOnClickListener(v -> {
            confirm("Mark called out?", "Save a call-out for " + SHORT_DATE.format(selectedTardyDate) + "?", () -> {
                db.saveAttendance(selectedTardyDate, AttendanceDb.CALLED_OUT, 0);
                toast("Call-out saved"); showTardy();
            });
        });
        actions.addView(tardy, new LinearLayout.LayoutParams(0, dp(56), 1));
        actions.addView(gap(dp(10)));
        actions.addView(call, new LinearLayout.LayoutParams(0, dp(56), 1));
        body.addView(actions, lpMatchWrap(dp(24)));

        body.addView(sectionTitle("SUMMARY"));
        body.addView(filterBar(tardyFilter, f -> { tardyFilter = f; showTardy(); }), lpMatchWrap(dp(14)));
        LocalDate[] range = range(tardyFilter, "attendance");
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
        if (trackLate && lateTotal > 0) body.addView(infoCard(PdfExporter.duration(lateTotal) + " total late in this filter"), lpMatchWrap(dp(14)));

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
            toast("Hours saved for " + SHORT_DATE.format(selectedHoursDate)); showHours();
        });
        body.addView(save, lpMatch(dp(56), dp(24)));

        body.addView(sectionTitle("TOTAL"));
        body.addView(filterBar(hoursFilter, f -> { hoursFilter = f; showHours(); }), lpMatchWrap(dp(14)));
        LocalDate[] range = range(hoursFilter, "hours");
        List<AttendanceDb.HoursEntry> entries = db.hoursBetween(range[0], range[1]);
        int total = 0;
        for (AttendanceDb.HoursEntry e : entries) total += e.minutes;
        body.addView(statCard(PdfExporter.duration(total), hoursFilter.label, TEAL), lpMatch(dp(112), dp(18)));
        addHoursHistory(body, entries);
        Button export = outlineButton("Export this view as PDF");
        export.setOnClickListener(v -> exportHours(range, entries));
        body.addView(export, lpMatch(dp(56), dp(4)));
    }

    private void showSettings() {
        LinearLayout body = page("Settings", "Choose how much detail you want to track.");
        LinearLayout card = card();
        LinearLayout line = horizontal();
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(label("Track exact late time", 16, INK, true));
        copy.addView(label("Show hours and minutes when marking a tardy.", 13, MUTED, false));
        Switch toggle = new Switch(this);
        toggle.setChecked(prefs.getBoolean("track_late_minutes", false));
        toggle.setOnCheckedChangeListener((v, checked) -> prefs.edit().putBoolean("track_late_minutes", checked).apply());
        line.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        line.addView(toggle, new LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(line);
        body.addView(card, lpMatchWrap(dp(18)));
        body.addView(sectionTitle("ABOUT YOUR DATA"));
        body.addView(infoCard("Attendance stores everything locally on this phone. PDF sharing only happens when you tap Export and choose where to send it."));
        TextView version = label("Attendance 1.0.0", 12, MUTED, false);
        body.addView(version, lpMatchWrap(dp(16)));
    }

    private void addAttendanceHistory(LinearLayout body, List<AttendanceDb.AttendanceEntry> entries) {
        body.addView(sectionTitle("HISTORY"));
        if (entries.isEmpty()) { body.addView(emptyCard("No tardies or call-outs in this range."), lpMatchWrap(dp(18))); return; }
        for (AttendanceDb.AttendanceEntry e : entries) {
            String detail = e.type + (e.type.equals(AttendanceDb.TARDY) && e.lateMinutes > 0 ? " • " + PdfExporter.duration(e.lateMinutes) + " late" : "");
            body.addView(historyRow(SHORT_DATE.format(e.date), detail, () -> { selectedTardyDate = e.date; showTardy(); }, () -> confirmDelete(() -> { db.deleteAttendance(e.id); showTardy(); })), lpMatchWrap(dp(8)));
        }
    }

    private void addHoursHistory(LinearLayout body, List<AttendanceDb.HoursEntry> entries) {
        body.addView(sectionTitle("HISTORY"));
        if (entries.isEmpty()) { body.addView(emptyCard("No hours saved in this range."), lpMatchWrap(dp(18))); return; }
        for (AttendanceDb.HoursEntry e : entries) {
            body.addView(historyRow(SHORT_DATE.format(e.date), PdfExporter.duration(e.minutes), () -> { selectedHoursDate = e.date; showHours(); }, () -> confirmDelete(() -> { db.deleteHours(e.id); showHours(); })), lpMatchWrap(dp(8)));
        }
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

    private HorizontalScrollView filterBar(Filter selected, FilterAction action) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = horizontal();
        for (Filter f : Filter.values()) {
            Button b = button(f.label, f == selected ? NAVY : Color.WHITE, f == selected ? Color.WHITE : INK);
            b.setBackground(f == selected ? roundRect(NAVY, 20) : bordered(Color.WHITE, Color.rgb(215, 221, 228), 20));
            b.setOnClickListener(v -> action.select(f));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
            p.setMarginEnd(dp(8)); row.addView(b, p);
        }
        scroll.addView(row);
        return scroll;
    }

    private LocalDate[] range(Filter filter, String table) {
        LocalDate today = LocalDate.now();
        switch (filter) {
            case DAYS_30: return new LocalDate[]{today.minusDays(29), today};
            case MONTH: return new LocalDate[]{today.withDayOfMonth(1), today};
            case YEAR: return new LocalDate[]{today.withDayOfYear(1), today};
            default: return new LocalDate[]{db.earliestDate(table), today};
        }
    }

    private void exportAttendance(LocalDate[] range, List<AttendanceDb.AttendanceEntry> entries) {
        try { share(PdfExporter.attendance(this, tardyFilter.label, range[0], range[1], entries)); }
        catch (IOException e) { toast("Could not create the PDF"); }
    }

    private void exportHours(LocalDate[] range, List<AttendanceDb.HoursEntry> entries) {
        try { share(PdfExporter.hours(this, hoursFilter.label, range[0], range[1], entries)); }
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
        Button b = button(FULL_DATE.format(date) + "   ▾", Color.WHITE, INK);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setPadding(dp(18), 0, dp(14), 0);
        b.setBackground(bordered(Color.WHITE, Color.rgb(213, 221, 230), 14));
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
        card.setBackground(bordered(Color.rgb(232, 247, 244), Color.rgb(182, 228, 220), 14));
        card.addView(label(text, 13, Color.rgb(20, 105, 95), false));
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
        card.setBackground(roundRect(Color.WHITE, 14));
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
        Button b = button(text, Color.TRANSPARENT, NAVY);
        b.setBackground(bordered(Color.TRANSPARENT, NAVY, 14));
        return b;
    }

    private EditText numberField(String hint, int maxDigits) {
        EditText e = new EditText(this);
        e.setHint(hint); e.setTextSize(16); e.setTextColor(INK); e.setHintTextColor(MUTED);
        e.setSingleLine(true); e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setPadding(dp(16), 0, dp(12), 0);
        e.setBackground(bordered(Color.WHITE, Color.rgb(213, 221, 230), 14));
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
