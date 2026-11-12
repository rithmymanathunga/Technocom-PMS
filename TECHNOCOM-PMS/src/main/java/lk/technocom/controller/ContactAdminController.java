package lk.technocom.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import lk.technocom.service.EmailService;
import lk.technocom.util.Db;
import lk.technocom.util.LanguageManager;
import lk.technocom.util.Navigator;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** "Contact Administrator" pop-up (login + forgot-password screens). Design: /view/ContactAdminView.fxml */
public class ContactAdminController {

    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");

    @FXML private StackPane root;
    @FXML private Label lblTitle, lblSub, lblName, lblEmail, lblMessage, lblStatus;
    @FXML private Label lblAdminName, lblAdminEmail, lblAdminPhone;
    @FXML private TextField tfName, tfEmail;
    @FXML private TextArea taMessage;
    @FXML private Button btnClose, btnSend;

    /** Opens the pop-up on top of the given window. */
    public static void open(Window owner) {
        try {
            Parent view = Navigator.load("/view/ContactAdminView.fxml");
            Stage st = new Stage(StageStyle.TRANSPARENT);
            if (owner != null) {
                st.initOwner(owner);
                st.initModality(Modality.WINDOW_MODAL);
            }
            Scene sc = new Scene(view);
            sc.setFill(Color.TRANSPARENT);
            st.setScene(sc);

            double[] d = new double[2];
            view.setOnMousePressed(e -> { d[0] = e.getSceneX(); d[1] = e.getSceneY(); });
            view.setOnMouseDragged(e -> { st.setX(e.getScreenX() - d[0]); st.setY(e.getScreenY() - d[1]); });

            st.setOnShown(e -> {
                if (owner != null) {
                    st.setX(owner.getX() + (owner.getWidth() - st.getWidth()) / 2);
                    st.setY(owner.getY() + (owner.getHeight() - st.getHeight()) / 2);
                }
            });
            st.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void initialize() {
        lblTitle.setText(LanguageManager.get("contact.title"));
        lblSub.setText(LanguageManager.get("contact.sub"));
        lblName.setText(LanguageManager.get("contact.fullName"));
        lblEmail.setText(LanguageManager.get("contact.email"));
        lblMessage.setText(LanguageManager.get("contact.message"));
        taMessage.setPromptText(LanguageManager.get("contact.msgPrompt"));
        btnClose.setText(LanguageManager.get("contact.close"));
        btnSend.setText(LanguageManager.get("contact.send"));
        lblStatus.setText("");

        // administrator details come from the database (first active Admin), then from company settings
        String name = "", email = "", phone = "";
        try {
            Map<String, Object> a = Db.row("SELECT u.full_name, u.email, u.phone FROM users u JOIN roles r ON r.role_id=u.role_id "
                    + "WHERE r.role_name='Admin' AND u.status='ACTIVE' ORDER BY u.user_id LIMIT 1");
            if (a != null) {
                name = str(a.get("full_name"));
                email = str(a.get("email"));
                phone = str(a.get("phone"));
            }
            Map<String, Object> c = Db.row("SELECT email, phone FROM company_settings WHERE setting_id=1");
            if (c != null) {
                if (!str(c.get("email")).isBlank()) email = str(c.get("email"));
                if (!str(c.get("phone")).isBlank()) phone = str(c.get("phone"));
            }
        } catch (RuntimeException ignored) {
            // database not reachable: the form still works, the details stay empty
        }
        show(lblAdminName, name);
        show(lblAdminEmail, email);
        show(lblAdminPhone, phone);
    }

    private static String str(Object o) { return o == null ? "" : o.toString(); }

    private static void show(Label l, String text) {
        l.setText(text);
        l.setVisible(!text.isBlank());
        l.setManaged(!text.isBlank());
    }

    @FXML
    private void onClose() {
        ((Stage) btnClose.getScene().getWindow()).close();
    }

    @FXML
    private void onSend() {
        String name = tfName.getText().trim();
        String email = tfEmail.getText().trim();
        String msg = taMessage.getText().trim();

        if (name.length() < 2) { error("contact.err.name"); tfName.requestFocus(); return; }
        if (!EMAIL.matcher(email).matches()) { error("contact.err.email"); tfEmail.requestFocus(); return; }
        if (msg.length() < 5) { error("contact.err.message"); taMessage.requestFocus(); return; }

        btnSend.setDisable(true);
        lblStatus.getStyleClass().setAll("contact-status");
        lblStatus.setText(LanguageManager.get("contact.sending"));

        new Thread(() -> {
            boolean delivered = false;
            try {
                List<Map<String, Object>> admins = Db.rows("SELECT u.user_id, u.email FROM users u JOIN roles r ON r.role_id=u.role_id "
                        + "WHERE r.role_name='Admin' AND u.status='ACTIVE'");
                String note = "Account request from " + name + " <" + email + ">: " + msg.replace('\n', ' ');
                if (note.length() > 250) note = note.substring(0, 250);
                boolean mail = EmailService.isConfigured();
                for (Map<String, Object> a : admins) {
                    // 1) always visible inside the app (bell icon)
                    Db.exec("INSERT INTO notifications (user_id, category, message) VALUES (?,?,?)",
                            ((Number) a.get("user_id")).intValue(), "GENERAL", note);
                    delivered = true;
                    // 2) plus an email when SMTP is set up
                    if (mail && !str(a.get("email")).isBlank()) {
                        EmailService.send(str(a.get("email")), "Technocom PMS - account request from " + name,
                                "Name: " + name + "\nEmail: " + email + "\n\n" + msg, "CONTACT");
                    }
                }
            } catch (RuntimeException ex) {
                ex.printStackTrace();
            }
            boolean ok = delivered;
            Platform.runLater(() -> {
                if (ok) {
                    lblStatus.getStyleClass().setAll("contact-status", "contact-status-ok");
                    lblStatus.setText(LanguageManager.get("contact.ok"));
                    tfName.clear();
                    tfEmail.clear();
                    taMessage.clear();
                } else {
                    error("contact.err.fail");
                }
                btnSend.setDisable(false);
            });
        }, "contact-admin").start();
    }

    private void error(String key) {
        lblStatus.getStyleClass().setAll("contact-status", "contact-status-err");
        lblStatus.setText(LanguageManager.get(key));
    }
}
