package lk.technocom.util;

/** Input checks that return an error message or null. */
public final class Val {

    private Val() {}

    public static String nic(String s) {
        if (s == null || !s.matches("^(\\d{9}[VvXx]|\\d{12})$")) return "NIC must be 9 digits + V, or 12 digits.";
        return null;
    }

    public static String phone(String s) {
        if (s == null || !s.matches("^0\\d{9}$")) return "Phone must be 10 digits starting with 0.";
        return null;
    }

    public static String email(String s) {
        if (s == null || !s.matches("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$")) return "Enter a valid email address.";
        return null;
    }

    /** First non-null message wins. */
    public static String first(String... msgs) {
        for (String m : msgs) if (m != null) return m;
        return null;
    }
}
