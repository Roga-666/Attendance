package com.rolando.attendance;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PdfExporter {
    private static final int WIDTH = 612, HEIGHT = 792, LEFT = 46, RIGHT = 566;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.US);

    private PdfExporter() { }

    public static Uri attendance(Context context, String filterName, LocalDate from, LocalDate to, List<AttendanceDb.AttendanceEntry> entries) throws IOException {
        int tardies = 0, calls = 0, late = 0;
        List<String[]> rows = new ArrayList<>();
        for (AttendanceDb.AttendanceEntry e : entries) {
            if (e.type.equals(AttendanceDb.TARDY)) { tardies++; late += e.lateMinutes; } else calls++;
            rows.add(new String[]{DATE.format(e.date), e.type, e.type.equals(AttendanceDb.TARDY) && e.lateMinutes > 0 ? duration(e.lateMinutes) + " late" : "—"});
        }
        String summary = tardies + " tardies  •  " + calls + " call-outs" + (late > 0 ? "  •  " + duration(late) + " total late" : "");
        return create(context, "Tardy & call-out report", filterName, from, to, summary, new String[]{"Date", "Status", "Late by"}, rows, "attendance");
    }

    public static Uri hours(Context context, String filterName, LocalDate from, LocalDate to, List<AttendanceDb.HoursEntry> entries) throws IOException {
        int total = 0;
        List<String[]> rows = new ArrayList<>();
        for (AttendanceDb.HoursEntry e : entries) { total += e.minutes; rows.add(new String[]{DATE.format(e.date), duration(e.minutes)}); }
        return create(context, "Hours worked report", filterName, from, to, duration(total) + " total", new String[]{"Date", "Hours worked"}, rows, "hours");
    }

    private static Uri create(Context context, String title, String filter, LocalDate from, LocalDate to, String summary, String[] columns, List<String[]> rows, String prefix) throws IOException {
        PdfDocument doc = new PdfDocument();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int pageNo = 0, rowIndex = 0;
        do {
            pageNo++;
            PdfDocument.Page page = doc.startPage(new PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, pageNo).create());
            Canvas canvas = page.getCanvas();
            canvas.drawColor(Color.WHITE);
            int y = header(canvas, paint, title, filter, from, to, summary, pageNo);
            y = tableHeader(canvas, paint, columns, y);
            while (rowIndex < rows.size() && y <= 730) {
                String[] row = rows.get(rowIndex++);
                if ((rowIndex & 1) == 0) {
                    paint.setColor(Color.rgb(245, 247, 250));
                    canvas.drawRect(LEFT, y - 21, RIGHT, y + 9, paint);
                }
                paint.setColor(Color.rgb(35, 45, 58));
                paint.setTextSize(10.5f);
                for (int i = 0; i < row.length; i++) canvas.drawText(row[i], colX(i, columns.length), y, paint);
                y += 31;
            }
            if (rows.isEmpty()) {
                paint.setColor(Color.DKGRAY); paint.setTextSize(12);
                canvas.drawText("No records in this date range.", LEFT, y + 22, paint);
            }
            paint.setColor(Color.GRAY); paint.setTextSize(8.5f);
            canvas.drawText("Created by Attendance on " + DATE.format(LocalDate.now()), LEFT, 766, paint);
            doc.finishPage(page);
        } while (rowIndex < rows.size());

        File dir = new File(context.getCacheDir(), "exports");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Could not create export folder");
        String filename = prefix + "_" + LocalDate.now() + ".pdf";
        File file = new File(dir, filename);
        try (FileOutputStream out = new FileOutputStream(file)) { doc.writeTo(out); }
        finally { doc.close(); }
        return Uri.parse("content://" + context.getPackageName() + ".files/" + filename);
    }

    private static int header(Canvas canvas, Paint p, String title, String filter, LocalDate from, LocalDate to, String summary, int page) {
        p.setColor(Color.rgb(16, 28, 44)); canvas.drawRect(0, 0, WIDTH, 112, p);
        p.setColor(Color.WHITE); p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD)); p.setTextSize(23);
        canvas.drawText(title, LEFT, 44, p);
        p.setTypeface(Typeface.DEFAULT); p.setTextSize(10.5f); p.setColor(Color.rgb(205, 225, 226));
        canvas.drawText(filter + "  •  " + DATE.format(from) + " – " + DATE.format(to), LEFT, 68, p);
        p.setColor(Color.WHITE); p.setTextSize(12.5f); canvas.drawText(summary, LEFT, 94, p);
        if (page > 1) { p.setTextSize(9); canvas.drawText("Page " + page, RIGHT - 35, 94, p); }
        return 144;
    }

    private static int tableHeader(Canvas canvas, Paint p, String[] columns, int y) {
        p.setColor(Color.rgb(0, 150, 136)); canvas.drawRect(LEFT, y - 19, RIGHT, y + 10, p);
        p.setColor(Color.WHITE); p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD)); p.setTextSize(10);
        for (int i = 0; i < columns.length; i++) canvas.drawText(columns[i], colX(i, columns.length), y, p);
        p.setTypeface(Typeface.DEFAULT);
        return y + 37;
    }

    private static float colX(int index, int count) {
        if (count == 2) return index == 0 ? LEFT + 8 : 330;
        return index == 0 ? LEFT + 8 : index == 1 ? 255 : 420;
    }

    public static String duration(int minutes) {
        int h = minutes / 60, m = minutes % 60;
        if (h == 0) return m + "m";
        if (m == 0) return h + "h";
        return h + "h " + m + "m";
    }
}
