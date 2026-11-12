package lk.technocom.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lk.technocom.util.Db;
import lk.technocom.util.Fmt;
import lk.technocom.util.LanguageManager;

import java.util.Set;

/** Small factory methods so every screen looks the same. */
public final class UI {

    /** Translates every fixed text (labels, buttons, prompts) of a loaded FXML into the current language. */
    public static void translate(javafx.scene.Node n) {
        if (n instanceof javafx.scene.control.Labeled l && l.getText() != null && !l.getText().isBlank())
            l.setText(LanguageManager.x(l.getText()));
        if (n instanceof javafx.scene.control.TextInputControl t && t.getPromptText() != null && !t.getPromptText().isBlank())
            t.setPromptText(LanguageManager.x(t.getPromptText()));
        if (n instanceof javafx.scene.control.ScrollPane sp && sp.getContent() != null) translate(sp.getContent());
        if (n instanceof javafx.scene.Parent p)
            for (javafx.scene.Node c : p.getChildrenUnmodifiable()) translate(c);
    }


    private UI() {}

    private static final Set<String> GREEN = Set.of("ACTIVE", "PAID", "APPROVED", "DONE", "OK", "HANDED_OVER",
            "FULFILLED", "COMPLETED", "RESOLVED", "SENT", "YES");
    private static final Set<String> RED = Set.of("INACTIVE", "LOCKED", "REJECTED", "CANCELLED", "FAILED",
            "LOW", "OUT", "OVERDUE", "HIGH", "OUT_OF_STOCK");
    private static final Set<String> BLUE = Set.of("SUBMITTED", "ISSUED", "ONGOING", "IN", "ORDERED");

    public static Label lbl(String text, String styleClass) {
        Label l = new Label(text);
        l.getStyleClass().add(styleClass);
        return l;
    }

    public static Label h1(String t) { return lbl(t, "h1"); }
    public static Label sub(String t) { return lbl(t, "sub"); }
    public static Label muted(String t) { return lbl(t, "muted"); }

    public static Button primary(String text, Runnable r) {
        Button b = new Button(text);
        b.getStyleClass().add("btn-primary");
        b.setOnAction(e -> run(r));
        return b;
    }

    public static Button outline(String text, Runnable r) {
        Button b = new Button(text);
        b.getStyleClass().add("btn-outline");
        b.setOnAction(e -> run(r));
        return b;
    }

    public static Button danger(String text, Runnable r) {
        Button b = new Button(text);
        b.getStyleClass().add("btn-danger");
        b.setOnAction(e -> run(r));
        return b;
    }

    public static Hyperlink link(String text, Runnable r) {
        Hyperlink h = new Hyperlink(text);
        h.getStyleClass().add("act-link");
        h.setOnAction(e -> run(r));
        return h;
    }

    public static VBox card(Node... n) {
        VBox v = new VBox(10, n);
        v.getStyleClass().add("card");
        v.setPadding(new Insets(16));
        return v;
    }

    public static VBox stat(String value, String label) {
        VBox v = new VBox(2, lbl(value, "stat-value"), lbl(label, "stat-label"));
        v.getStyleClass().addAll("card", "stat");
        v.setPadding(new Insets(14, 18, 14, 18));
        return v;
    }

    public static Label badge(String raw) {
        Label l = new Label(Fmt.nice(raw));
        l.getStyleClass().add("badge");
        String k = raw == null ? "" : raw.toUpperCase();
        if (GREEN.contains(k)) l.getStyleClass().add("badge-green");
        else if (RED.contains(k)) l.getStyleClass().add("badge-red");
        else if (BLUE.contains(k)) l.getStyleClass().add("badge-blue");
        else l.getStyleClass().add("badge-amber");
        return l;
    }

    public static Region grow() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        VBox.setVgrow(r, Priority.ALWAYS);
        return r;
    }

    public static HBox row(Node... n) {
        HBox h = new HBox(10, n);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    /** Runs UI code and shows database / validation errors in a dialog instead of crashing. */
    public static void run(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            e.printStackTrace();
            Dialogs.error(e instanceof lk.technocom.util.DbException ? e.getMessage() : Db.friendly(e));
        }
    }

    /** "label: value" line used in detail popups. */
    public static VBox kv(String key, Object value) {
        Label v = new Label(value == null || value.toString().isBlank() ? "-" : value.toString());
        v.setWrapText(true);
        v.getStyleClass().add("kv-value");
        VBox b = new VBox(2, lbl(key, "kv-key"), v);
        b.setMinWidth(160);
        return b;
    }
}
