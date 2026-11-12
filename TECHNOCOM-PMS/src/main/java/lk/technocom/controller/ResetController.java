package lk.technocom.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Polygon;
import javafx.scene.text.Text;
import javafx.util.Duration;
import lk.technocom.service.EmailService;
import lk.technocom.service.PasswordService;
import lk.technocom.util.Db;
import lk.technocom.util.DarkCombo;
import lk.technocom.util.LanguageManager;
import lk.technocom.util.Navigator;

import java.util.Arrays;

/**
 * Forgot password:  1 username/email -> 2 six digit code -> 3 new password -> 4 done.
 * The design is in /view/ResetView.fxml (every step is one VBox). All texts come from i18n keys "reset.*".
 */
public class ResetController {

    private static final double DESIGN_W = 1440;
    private static final double DESIGN_H = 1024;
    private static final String[] LANG_CODES = {"en", "si", "ta"};
    private static final int CODE_SECONDS = 10 * 60;   // must match PasswordService (code valid 10 minutes)
    private static final int RESEND_SECONDS = 30;

    @FXML private StackPane root;
    @FXML private Pane canvas, photoPane;
    @FXML private Label lblTag1, lblTag2;
    @FXML private ComboBox<String> cbLanguage;
    @FXML private HBox progress;
    @FXML private Region seg1, seg2, seg3;
    @FXML private VBox stepEmail, stepCode, stepPassword, stepDone;

    // step 1
    @FXML private Text txtT1, txtT2;
    @FXML private Label lblEmailSub, lblIdentityLabel, lblNotice, lblEmailError;
    @FXML private TextField tfIdentity;
    @FXML private Button btnSend, btnContact, btnBack1;

    // step 2
    @FXML private Label lblVerifyTitle, lblVerifySub, lblMasked, lblExpires, lblCodeError;
    @FXML private TextField c1, c2, c3, c4, c5, c6;
    @FXML private Button btnResend, btnVerify, btnBack2;

    // step 3
    @FXML private Text txtP1, txtP2;
    @FXML private Label lblPwSub, lblNewLabel, lblConfirmLabel, lblStrength, lblPwError;
    @FXML private PasswordField pfNew, pfConfirm;
    @FXML private TextField tfNew, tfConfirm;
    @FXML private Region str1, str2, str3, str4;
    @FXML private Label chk1, chk2, chk3, chk4;
    @FXML private Button btnEyeNew, btnEyeConfirm, btnResetPw, btnBack3;

    // step 4
    @FXML private Label lblDoneTitle, lblDoneSub;
    @FXML private Button btnDone;

    private TextField[] boxes;
    private Timeline clock;
    private int step = 1;
    private int userId;
    private String maskedEmail = "";
    private String code = "";
    private int expireLeft;
    private int resendLeft;
    private boolean noEmailSetup;

    @FXML
    private void initialize() {
        photoPane.setClip(new Polygon(0, 0, 752, 0, 585, 422, 732, 645, 757, 1024, 0, 1024));
        canvas.scaleXProperty().bind(scale());
        canvas.scaleYProperty().bind(scale());

        cbLanguage.setItems(FXCollections.observableArrayList("English", "සිංහල", "தமிழ்"));
        DarkCombo.apply(cbLanguage);
        cbLanguage.getSelectionModel().select(Math.max(0, Arrays.asList(LANG_CODES).indexOf(LanguageManager.getCode())));
        cbLanguage.setOnAction(e -> {
            int i = cbLanguage.getSelectionModel().getSelectedIndex();
            if (i < 0) return;
            LanguageManager.setLanguage(LANG_CODES[i]);
            applyTexts();
        });

        boxes = new TextField[]{c1, c2, c3, c4, c5, c6};
        for (int i = 0; i < 6; i++) wireCodeBox(i);

        pfNew.textProperty().bindBidirectional(tfNew.textProperty());
        pfConfirm.textProperty().bindBidirectional(tfConfirm.textProperty());
        pfNew.textProperty().addListener((o, a, b) -> updateStrength());

        tfIdentity.setOnAction(e -> onSendCode());
        pfNew.setOnAction(e -> pfConfirm.requestFocus());
        pfConfirm.setOnAction(e -> onResetPassword());
        tfNew.setOnAction(e -> pfConfirm.requestFocus());
        tfConfirm.setOnAction(e -> onResetPassword());

        applyTexts();
        showStep(1);
    }

    private DoubleBinding scale() {
        return Bindings.createDoubleBinding(
                () -> Math.min(root.getWidth() / DESIGN_W, root.getHeight() / DESIGN_H),
                root.widthProperty(), root.heightProperty());
    }

    private static String t(String key) { return LanguageManager.get(key); }

    /** Sets every text in the current language (called at start and when the language changes). */
    private void applyTexts() {
        lblTag1.setText(t("welcome.t1"));
        lblTag2.setText(t("welcome.t2"));

        txtT1.setText(t("reset.title1") + " ");
        txtT2.setText(t("reset.title2"));
        lblEmailSub.setText(t("reset.sub"));
        lblIdentityLabel.setText(t("reset.identity"));
        tfIdentity.setPromptText(t("reset.identityPrompt"));
        lblNotice.setText(t("reset.notice"));
        btnSend.setText(t("reset.send"));
        btnContact.setText(t("reset.contact"));
        btnBack1.setText(t("reset.back"));
        btnBack2.setText(t("reset.back"));
        btnBack3.setText(t("reset.back"));

        lblVerifyTitle.setText(t("reset.verifyTitle"));
        lblVerifySub.setText(t("reset.verifySub"));
        lblMasked.setText(maskedEmail.isEmpty() ? t("reset.yourEmail") : maskedEmail);
        btnVerify.setText(t("reset.verify"));
        tickLabels();

        txtP1.setText(t("reset.newTitle1") + " ");
        txtP2.setText(t("reset.newTitle2"));
        lblPwSub.setText(t("reset.newSub"));
        lblNewLabel.setText(t("reset.newPw"));
        lblConfirmLabel.setText(t("reset.confirmPw"));
        pfNew.setPromptText(t("reset.newPrompt"));
        tfNew.setPromptText(t("reset.newPrompt"));
        pfConfirm.setPromptText(t("reset.confirmPrompt"));
        tfConfirm.setPromptText(t("reset.confirmPrompt"));
        btnResetPw.setText(t("reset.reset"));
        updateStrength();

        lblDoneTitle.setText(t("reset.doneTitle"));
        lblDoneSub.setText(t("reset.doneSub"));
        btnDone.setText(t("reset.toLogin"));
    }

    // ------------------------------------------------------------------ steps

    private void showStep(int n) {
        step = n;
        VBox[] all = {stepEmail, stepCode, stepPassword, stepDone};
        for (int i = 0; i < all.length; i++) {
            all[i].setVisible(i == n - 1);
            all[i].setManaged(i == n - 1);
        }
        progress.setVisible(n < 4);
        progress.setManaged(n < 4);
        seg(seg1, n >= 1);
        seg(seg2, n >= 2);
        seg(seg3, n >= 3);

        if (n == 1) tfIdentity.requestFocus();
        if (n == 2) c1.requestFocus();
        if (n == 3) pfNew.requestFocus();
    }

    private static void seg(Region r, boolean on) {
        r.getStyleClass().setAll("seg", on ? "seg-on" : "seg-off");
    }

    // ------------------------------------------------------------------ step 1: send code

    @FXML
    private void onSendCode() {
        requestCode(false);
    }

    @FXML
    private void onResend() {
        requestCode(true);
    }

    private void requestCode(boolean resend) {
        String who = tfIdentity.getText().trim();
        if (who.isEmpty()) {
            lblEmailError.setText(t("reset.err.identity"));
            showStep(1);
            return;
        }
        lblEmailError.setText("");
        btnSend.setDisable(true);
        btnResend.setDisable(true);
        btnSend.setText(t("reset.sending"));

        new Thread(() -> {
            int id = 0;
            String email = "";
            String problem = null;
            boolean mailReady = true;
            try {
                id = PasswordService.requestCode(who);
                if (id != 0) {
                    Object e = Db.scalar("SELECT email FROM users WHERE user_id=?", id);
                    email = e == null ? "" : e.toString();
                }
                mailReady = EmailService.isConfigured();
            } catch (RuntimeException ex) {
                problem = ex.getMessage() == null ? ex.toString() : ex.getMessage();
            }
            final int uid = id;
            final String mail = email;
            final String err = problem;
            final boolean ready = mailReady;
            Platform.runLater(() -> {
                btnSend.setDisable(false);
                btnSend.setText(t("reset.send"));
                if (err != null) {
                    lblEmailError.setText(err);
                    showStep(1);
                    return;
                }
                userId = uid;                       // 0 = no such account; we do not tell the visitor (same screen either way)
                maskedEmail = uid == 0 ? "" : mask(mail);
                noEmailSetup = !ready;
                lblMasked.setText(maskedEmail.isEmpty() ? t("reset.yourEmail") : maskedEmail);
                clearBoxes();
                startClock();
                showStep(2);
                if (noEmailSetup) message(lblCodeError, t("reset.noEmail"), false);
                else if (resend) message(lblCodeError, t("reset.resent"), true);
                else message(lblCodeError, "", false);
            });
        }, "reset-code").start();
    }

    /** b•••••@technocom.lk */
    private static String mask(String email) {
        int at = email.indexOf('@');
        if (at < 1) return email;
        return email.charAt(0) + "•".repeat(Math.max(3, at - 1)) + email.substring(at);
    }

    // ------------------------------------------------------------------ step 2: verify code

    private void wireCodeBox(int i) {
        TextField box = boxes[i];
        box.setTextFormatter(new TextFormatter<String>(ch -> ch.getControlNewText().matches("\\d{0,6}") ? ch : null));
        box.textProperty().addListener((o, old, v) -> {
            box.getStyleClass().remove("code-box-error");
            if (v.length() > 1) {                              // pasted a whole code
                Platform.runLater(() -> fillFrom(i, v));
            } else if (v.length() == 1 && i < 5) {
                boxes[i + 1].requestFocus();
            }
        });
        box.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.BACK_SPACE && box.getText().isEmpty() && i > 0) {
                boxes[i - 1].requestFocus();
                boxes[i - 1].clear();
            } else if (e.getCode() == KeyCode.ENTER) {
                onVerify();
            }
        });
    }

    private void fillFrom(int start, String digits) {
        for (int k = 0; k < digits.length() && start + k < 6; k++) {
            boxes[start + k].setText(String.valueOf(digits.charAt(k)));
        }
        boxes[Math.min(5, start + digits.length() - 1)].requestFocus();
    }

    private String typedCode() {
        StringBuilder sb = new StringBuilder();
        for (TextField b : boxes) sb.append(b.getText());
        return sb.toString();
    }

    private void clearBoxes() {
        for (TextField b : boxes) {
            b.clear();
            b.getStyleClass().remove("code-box-error");
        }
    }

    @FXML
    private void onVerify() {
        String typed = typedCode();
        if (typed.length() != 6) {
            message(lblCodeError, t("reset.err.code6"), false);
            return;
        }
        if (!PasswordService.checkCode(userId, typed)) {
            message(lblCodeError, t("reset.err.invalid"), false);
            for (TextField b : boxes) if (!b.getStyleClass().contains("code-box-error")) b.getStyleClass().add("code-box-error");
            return;
        }
        code = typed;
        stopClock();
        showStep(3);
    }

    private void startClock() {
        stopClock();
        expireLeft = CODE_SECONDS;
        resendLeft = RESEND_SECONDS;
        tickLabels();
        clock = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (expireLeft > 0) expireLeft--;
            if (resendLeft > 0) resendLeft--;
            tickLabels();
        }));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();
    }

    private void stopClock() {
        if (clock != null) clock.stop();
    }

    private void tickLabels() {
        lblExpires.setText(expireLeft > 0 ? t("reset.expires") + " " + mmss(expireLeft) : t("reset.expired"));
        btnResend.setDisable(resendLeft > 0);
        btnResend.setText(resendLeft > 0 ? t("reset.resendIn") + " " + mmss(resendLeft) : t("reset.resend"));
    }

    private static String mmss(int s) {
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    // ------------------------------------------------------------------ step 3: new password

    @FXML
    private void onEyeNew() {
        toggle(pfNew, tfNew);
    }

    @FXML
    private void onEyeConfirm() {
        toggle(pfConfirm, tfConfirm);
    }

    private static void toggle(PasswordField hidden, TextField shown) {
        boolean show = !shown.isVisible();
        shown.setVisible(show);
        shown.setManaged(show);
        hidden.setVisible(!show);
        hidden.setManaged(!show);
        (show ? shown : hidden).requestFocus();
    }

    private void updateStrength() {
        String p = pfNew.getText() == null ? "" : pfNew.getText();
        boolean len = p.length() >= 8;
        boolean mixed = p.matches(".*[A-Z].*") && p.matches(".*[a-z].*");
        boolean num = p.matches(".*\\d.*");
        boolean sym = p.matches(".*[^A-Za-z0-9].*");
        int score = (len ? 1 : 0) + (mixed ? 1 : 0) + (num ? 1 : 0) + (sym ? 1 : 0);

        check(chk1, len, t("reset.chk1"));
        check(chk2, mixed, t("reset.chk2"));
        check(chk3, num, t("reset.chk3"));
        check(chk4, sym, t("reset.chk4"));

        String[] names = {"", t("reset.weak"), t("reset.fair"), t("reset.medium"), t("reset.strong")};
        String[] cls = {"seg-off", "seg-weak", "seg-fair", "seg-medium", "seg-strong"};
        Region[] segs = {str1, str2, str3, str4};
        int shown = p.isEmpty() ? 0 : Math.max(1, score);
        for (int i = 0; i < 4; i++) segs[i].getStyleClass().setAll("seg", i < shown ? cls[shown] : "seg-off");
        lblStrength.setText(t("reset.strength") + (shown == 0 ? "" : " " + names[shown]));
    }

    private static void check(Label l, boolean ok, String text) {
        l.setText((ok ? "✓ " : "○ ") + text);
        l.getStyleClass().setAll("chk");
        if (ok) l.getStyleClass().add("chk-ok");
    }

    @FXML
    private void onResetPassword() {
        String p1 = pfNew.getText();
        String p2 = pfConfirm.getText();
        if (!p1.equals(p2)) {
            lblPwError.setText(t("reset.err.match"));
            return;
        }
        String err = PasswordService.resetWithCode(userId, code, p1);
        if (err != null) {
            lblPwError.setText(LanguageManager.x(err));
            return;
        }
        lblPwError.setText("");
        pfNew.clear();
        pfConfirm.clear();
        showStep(4);
    }

    // ------------------------------------------------------------------ other buttons

    @FXML
    private void onContact() {
        ContactAdminController.open(root.getScene().getWindow());
    }

    @FXML
    private void onBack() {
        stopClock();
        Navigator.show(root, "/view/LoginView.fxml");
    }

    private static void message(Label l, String text, boolean ok) {
        l.getStyleClass().setAll(ok ? "reset-ok" : "login-error");
        l.setText(text);
    }
}
