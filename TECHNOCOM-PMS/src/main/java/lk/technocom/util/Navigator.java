package lk.technocom.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

public final class Navigator {

    private Navigator() {}

    /** Loads an FXML with the currently selected language (%key texts). */
    public static Parent load(String fxml) throws IOException {
        URL url = Navigator.class.getResource(fxml);
        if (url == null) {
            throw new IOException("FXML not found: " + fxml);
        }
        return FXMLLoader.load(url, LanguageManager.getBundle());
    }

    /** Replaces the window content. Returns false if the page does not exist yet. */
    public static boolean show(Node anyNodeInWindow, String fxml) {
        if (Navigator.class.getResource(fxml) == null) {
            return false;
        }
        try {
            Stage stage = (Stage) anyNodeInWindow.getScene().getWindow();
            Parent page = load(fxml);
            stage.setScene(new Scene(page, stage.getScene().getWidth(), stage.getScene().getHeight()));
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
