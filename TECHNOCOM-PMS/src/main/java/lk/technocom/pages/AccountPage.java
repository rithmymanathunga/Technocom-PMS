package lk.technocom.pages;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import lk.technocom.service.NotificationService;
import lk.technocom.ui.Views;
import lk.technocom.util.LanguageManager;

/** Account page. Design: /view/AccountView.fxml */
public final class AccountPage implements Views.Ready {

    @FXML private Label lblUnread;

    private AccountPage() {}

    public static Node build() {
        return Views.load("/view/AccountView.fxml", new AccountPage());
    }

    @Override
    public void ready() {
        lblUnread.setText(NotificationService.unread() + " " + LanguageManager.x("unread alerts"));
    }

    @FXML private void onProfile() { AccountPopups.profile(); }
    @FXML private void onPassword() { AccountPopups.changePassword(false); }
    @FXML private void onNotifications() { AccountPopups.notifications(); }
}
