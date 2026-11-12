package lk.technocom.controller;

import javafx.stage.Stage;
import lk.technocom.util.Navigator;

/** Forgot password. The screens are in /view/ResetView.fxml (open it in Scene Builder); logic is in ResetController. */
public final class ResetFlow {

    private ResetFlow() {}

    public static void open(Stage stage) {
        Navigator.show(stage.getScene().getRoot(), "/view/ResetView.fxml");
    }
}
