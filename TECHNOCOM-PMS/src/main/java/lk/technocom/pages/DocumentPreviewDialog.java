package lk.technocom.pages;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Embedded, read-only viewer for PDF, common images and UTF-8 text files. */
public final class DocumentPreviewDialog {
    private DocumentPreviewDialog() {}

    public static void show(Path path) {
        if (path == null || !Files.isRegularFile(path) || Files.isSymbolicLink(path))
            throw new IllegalArgumentException("Preview file is unavailable.");
        String name = path.getFileName().toString();
        String ext = extension(name);
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Document Preview — " + name);
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));
        try {
            if ("pdf".equals(ext)) showPdf(path, root, stage);
            else if (java.util.Set.of("png", "jpg", "jpeg").contains(ext)) {
                ImageView view = new ImageView(new Image(path.toUri().toString(), true));
                view.setPreserveRatio(true);
                view.setFitWidth(1100);
                view.setFitHeight(760);
                root.setCenter(new ScrollPane(new StackPane(view)));
            } else if ("txt".equals(ext)) {
                TextArea text = new TextArea(Files.readString(path, StandardCharsets.UTF_8));
                text.setEditable(false);
                text.setWrapText(false);
                root.setCenter(text);
            } else throw new IllegalArgumentException("Embedded preview supports PDF, PNG, JPG and TXT. Use Download for Office documents.");
        } catch (IOException e) {
            throw new IllegalStateException("Could not render document preview.", e);
        }
        Scene scene = new Scene(root, 1180, 820);
        stage.setScene(scene);
        stage.showAndWait();
    }

    private static void showPdf(Path path, BorderPane root, Stage stage) throws IOException {
        PDDocument document = Loader.loadPDF(path.toFile());
        PDFRenderer renderer = new PDFRenderer(document);
        ImageView pageImage = new ImageView();
        pageImage.setPreserveRatio(true);
        pageImage.setFitWidth(1080);
        pageImage.setFitHeight(720);
        Label pageLabel = new Label();
        Button previous = new Button("Previous");
        Button next = new Button("Next");
        int[] current = {0};
        Runnable render = () -> {
            try {
                BufferedImage buffered = renderer.renderImageWithDPI(current[0], 110);
                int width = buffered.getWidth(), height = buffered.getHeight();
                int[] pixels = buffered.getRGB(0, 0, width, height, null, 0, width);
                WritableImage image = new WritableImage(width, height);
                image.getPixelWriter().setPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
                pageImage.setImage(image);
                pageLabel.setText("Page " + (current[0] + 1) + " of " + document.getNumberOfPages());
                previous.setDisable(current[0] == 0);
                next.setDisable(current[0] >= document.getNumberOfPages() - 1);
            } catch (IOException e) { throw new IllegalStateException("Could not render PDF page.", e); }
        };
        previous.setOnAction(e -> { current[0]--; render.run(); });
        next.setOnAction(e -> { current[0]++; render.run(); });
        Button close = new Button("Close");
        HBox controls = new HBox(10, previous, pageLabel, next, close);
        controls.setPadding(new Insets(8));
        controls.setAlignment(javafx.geometry.Pos.CENTER);
        root.setCenter(new ScrollPane(new StackPane(pageImage)));
        root.setBottom(controls);
        close.setOnAction(e -> stage.close());
        stage.setOnHidden(e -> { try { document.close(); } catch (IOException ignored) {} });
        render.run();
    }

    private static String extension(String name) { int i=name.lastIndexOf('.'); return i<0?"":name.substring(i+1).toLowerCase(Locale.ROOT); }
}
