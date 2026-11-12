package lk.technocom.controller;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.GridPane;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Polygon;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import lk.technocom.service.AuthService;
import lk.technocom.workflow.ClientInvitationService;
import lk.technocom.ui.Dialogs;
import lk.technocom.ui.AppShell;
import lk.technocom.util.DarkCombo;
import lk.technocom.util.LanguageManager;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.prefs.Preferences;

public class LoginController {

    private static final double DESIGN_W = 1380;
    private static final double DESIGN_H = 752;
    private static final Preferences PREFS = Preferences.userNodeForPackage(LoginController.class);
    private static final String[] LANG_CODES = {"en", "si", "ta"};

    @FXML private StackPane root;
    @FXML private Pane canvas;
    @FXML private Pane photoPane;
    @FXML private ComboBox<String> cbLanguage;
    @FXML private Text txtWelcome1;
    @FXML private Text txtWelcome2;
    @FXML private Label lblSubtitle;
    @FXML private TextField tfUsername;
    @FXML private PasswordField pfPassword;
    @FXML private TextField tfPasswordVisible;
    @FXML private Label lblError;
    @FXML private CheckBox cbRemember;
    @FXML private Button btnForgot;
    @FXML private Button btnLogin;
    @FXML private Label lblNoAccount;
    @FXML private Button btnContact;
    @FXML private Button btnPortalInvite;

    @FXML
    private void initialize() {
        photoPane.setClip(new Polygon(0, 0, 696, 0, 696, 752, 0, 752));

        canvas.scaleXProperty().bind(scaleBinding());
        canvas.scaleYProperty().bind(scaleBinding());

        tfPasswordVisible.textProperty().bindBidirectional(pfPassword.textProperty());

        cbLanguage.setItems(FXCollections.observableArrayList("English", "සිංහල", "தமிழ்"));
        DarkCombo.apply(cbLanguage);          // dropdown letters visible (dark list, white / gold text)
        cbLanguage.getSelectionModel().select(
                Math.max(0, Arrays.asList(LANG_CODES).indexOf(LanguageManager.getCode())));
        cbLanguage.setOnAction(e -> {
            int i = cbLanguage.getSelectionModel().getSelectedIndex();
            if (i < 0) return;
            LanguageManager.setLanguage(LANG_CODES[i]);
            applyLanguage();
        });

        tfUsername.setText(PREFS.get("username", ""));
        cbRemember.setSelected(true);

        tfUsername.setOnAction(e -> pfPassword.requestFocus());
        pfPassword.setOnAction(e -> onLogin());
        tfPasswordVisible.setOnAction(e -> onLogin());

        applyLanguage();
    }

    private DoubleBinding scaleBinding() {
        return Bindings.createDoubleBinding(
                () -> Math.min(root.getWidth() / DESIGN_W, root.getHeight() / DESIGN_H),
                root.widthProperty(), root.heightProperty());
    }

    private void applyLanguage() {
        txtWelcome1.setText(LanguageManager.get("login.welcome1"));
        txtWelcome2.setText(LanguageManager.get("login.welcome2"));
        lblSubtitle.setText(LanguageManager.get("login.subtitle"));
        tfUsername.setPromptText(LanguageManager.get("login.username"));
        pfPassword.setPromptText(LanguageManager.get("login.password"));
        tfPasswordVisible.setPromptText(LanguageManager.get("login.password"));
        cbRemember.setText(LanguageManager.get("login.remember"));
        btnForgot.setText(LanguageManager.get("login.forgot"));
        btnLogin.setText(LanguageManager.get("login.button"));
        lblNoAccount.setText(LanguageManager.get("login.noAccount"));
        btnContact.setText(LanguageManager.get("login.contact"));
        btnPortalInvite.setText("Accept Client Invitation");
        lblError.setText("");
    }

    @FXML
    private void onToggleEye() {
        boolean showing = tfPasswordVisible.isVisible();
        tfPasswordVisible.setVisible(!showing);
        tfPasswordVisible.setManaged(!showing);
        pfPassword.setVisible(showing);
        pfPassword.setManaged(showing);
        (showing ? pfPassword : tfPasswordVisible).requestFocus();
    }

    @FXML
    private void onLogin() {
        String username = tfUsername.getText().trim();
        String password = pfPassword.getText();

        if (username.isEmpty() || password.isEmpty()) {
            lblError.setText(LanguageManager.get("login.error.empty"));
            return;
        }

        try {
            AuthService.login(username, password);
        } catch (AuthService.AuthException e) {
            lblError.setText(LanguageManager.get(e.getMessage()));
            pfPassword.clear();
            return;
        } catch (SQLException e) {
            e.printStackTrace();
            lblError.setText(LanguageManager.get("login.error.db"));
            return;
        }

        if (cbRemember.isSelected()) {
            PREFS.put("username", username);
        } else {
            PREFS.remove("username");
        }

        lblError.setText("");
        try {
            AppShell.open((Stage) btnLogin.getScene().getWindow());
        } catch (RuntimeException ex) {
            ex.printStackTrace();
            info(ex.getMessage());
        }
    }

    @FXML
    private void onForgot() {
        ResetFlow.open((Stage) btnLogin.getScene().getWindow());
    }

    @FXML
    private void onContact() {
        ContactAdminController.open(btnLogin.getScene().getWindow());
    }

    @FXML
    private void onPortalInvite() {
        TextField code = new TextField(); code.setPromptText("Invitation code from email");
        TextField name = new TextField(); name.setPromptText("Full name");
        TextField nic = new TextField(); nic.setPromptText("NIC");
        TextField phone = new TextField(); phone.setPromptText("Phone number");
        TextField address = new TextField(); address.setPromptText("Address (optional)");
        TextField username = new TextField(); username.setPromptText("Username");
        PasswordField password = new PasswordField(); password.setPromptText("Password (minimum 10 characters)");
        PasswordField confirm = new PasswordField(); confirm.setPromptText("Confirm password");
        GridPane form = new GridPane(); form.setHgap(10); form.setVgap(9); form.setPadding(new Insets(18));
        Node[] fields = {code,name,nic,phone,address,username,password,confirm};
        String[] labels = {"Invitation code","Full name","NIC","Phone","Address","Username","Password","Confirm password"};
        for(int i=0;i<fields.length;i++){form.add(new javafx.scene.control.Label(labels[i]),0,i);form.add(fields[i],1,i);GridPane.setHgrow(fields[i],javafx.scene.layout.Priority.ALWAYS);}
        javafx.scene.control.Button submit = new javafx.scene.control.Button("Create Client Portal Account");
        submit.getStyleClass().add("btn-primary"); form.add(submit,1,8);
        javafx.stage.Stage dialog=Dialogs.show("Client Portal Registration",form,560,590);
        submit.setOnAction(e->{
            if(!password.getText().equals(confirm.getText())){Dialogs.error("Passwords do not match.");return;}
            try{
                ClientInvitationService.accept(code.getText(),name.getText(),nic.getText(),phone.getText(),address.getText(),username.getText(),password.getText());
                dialog.close(); Dialogs.info("Registration complete","Your Client Portal account has been created. You can now sign in.");
            }catch(Exception ex){Dialogs.error(lk.technocom.util.Db.friendly(ex));}
        });
    }

    private void info(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.setHeaderText(null);
        alert.show();
    }
}
