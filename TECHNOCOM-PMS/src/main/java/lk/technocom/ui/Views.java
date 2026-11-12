package lk.technocom.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;

import java.io.IOException;

/** Loads a page or pop-up body from /view/*.fxml, translates its texts, then calls controller.ready(). */
public final class Views {

    /** Implement this in a controller to fill the page with data AFTER the texts are translated. */
    public interface Ready {
        void ready();
    }

    private Views() {}

    public static Node load(String fxml, Object controller) {
        try {
            FXMLLoader loader = new FXMLLoader(Views.class.getResource(fxml));
            loader.setControllerFactory(c -> controller);
            Node root = loader.load();
            UI.translate(root);
            if (controller instanceof Ready r) r.ready();
            return root;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load " + fxml + ": " + e.getMessage(), e);
        }
    }
}
