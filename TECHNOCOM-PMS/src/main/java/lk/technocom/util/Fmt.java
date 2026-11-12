package lk.technocom.util;

import java.math.BigDecimal;
import java.sql.Time;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class Fmt {

    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");
    public static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
    public static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private Fmt() {}

    public static LocalDate date(Object o) {
        if (o == null) return null;
        if (o instanceof LocalDate d) return d;
        if (o instanceof java.sql.Date d) return d.toLocalDate();
        if (o instanceof java.sql.Timestamp t) return t.toLocalDateTime().toLocalDate();
        if (o instanceof LocalDateTime t) return t.toLocalDate();
        return null;
    }

    public static LocalTime time(Object o) {
        if (o == null) return null;
        if (o instanceof LocalTime t) return t;
        if (o instanceof Time t) return t.toLocalTime();
        return null;
    }

    public static BigDecimal dec(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal b) return b;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(o.toString().replace(",", "").trim()); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    public static String num(Object o) {
        if (o == null) return "";
        BigDecimal b = dec(o).stripTrailingZeros();
        if (b.scale() < 0) b = b.setScale(0);
        return new DecimalFormat("#,##0.##").format(b);
    }

    public static String money(Object o) {
        if (o == null) return "";
        return "Rs " + new DecimalFormat("#,##0.00").format(dec(o));
    }

    /** Rs 14M, Rs 250K style. */
    public static String compact(double v) {
        if (v >= 1_000_000) return "Rs " + trim(v / 1_000_000) + "M";
        if (v >= 1_000) return "Rs " + trim(v / 1_000) + "K";
        return "Rs " + trim(v);
    }

    private static String trim(double v) {
        return new DecimalFormat("#,##0.#").format(v);
    }

    /** ONGOING -> Ongoing, BANK_TRANSFER -> Bank Transfer (translated when a key exists). */
    public static String nice(Object o) {
        if (o == null) return "";
        String s = o.toString();
        String key = "enum." + s;
        String t = LanguageManager.get(key);
        if (!t.equals(key)) return t;
        StringBuilder sb = new StringBuilder();
        for (String w : s.split("_")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    public static String str(Object o) {
        if (o == null) return "";
        if (o instanceof LocalDate || o instanceof java.sql.Date) return date(o).format(DATE);
        if (o instanceof LocalDateTime t) return t.format(DATETIME);
        if (o instanceof java.sql.Timestamp t) return t.toLocalDateTime().format(DATETIME);
        if (o instanceof Time || o instanceof LocalTime) return time(o).format(TIME);
        if (o instanceof BigDecimal) return num(o);
        if (o instanceof Boolean b) return b ? "Yes" : "No";
        return o.toString();
    }

    public static String relativeDay(Object o) {
        LocalDate d = date(o);
        if (d == null) return "-";
        LocalDate today = LocalDate.now();
        if (d.equals(today)) return LanguageManager.x("Today");
        if (d.equals(today.minusDays(1))) return LanguageManager.x("Yesterday");
        return d.format(DateTimeFormatter.ofPattern("dd MMM"));
    }

    public static String relativeDayOrDate(Object o) {
        return relativeDay(o);
    }

    public static String lastLogin(Object o) {
        return o == null ? "-" : relativeDay(o);
    }
}
