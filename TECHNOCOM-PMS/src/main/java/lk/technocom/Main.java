package lk.technocom;

import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import lk.technocom.ui.Dialogs;
import lk.technocom.util.Bootstrap;
import lk.technocom.util.Navigator;
import lk.technocom.workflow.ClientPortalApiServer;
import lk.technocom.workflow.ConfigurableApprovalService;
import lk.technocom.workflow.DocumentRetentionService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import java.io.InputStream;

/** Application entry. Checks the external MySQL connection, then opens the Welcome page. */
public class Main extends Application {
    private ScheduledExecutorService approvalScheduler;

    @Override
    public void start(Stage stage) throws Exception {
        Dialogs.setPrimary(stage);

        String problem = Bootstrap.run();
        if (problem != null) {
            Alert a = new Alert(Alert.AlertType.WARNING, problem, ButtonType.OK);
            a.setHeaderText("Technocom PMS - database");
            a.showAndWait();
        } else {
            approvalScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "technocom-approval-escalation");
                t.setDaemon(true);
                return t;
            });
            approvalScheduler.scheduleWithFixedDelay(() -> {
                try { ConfigurableApprovalService.escalateOverdueAutomatically(); }
                catch (RuntimeException ignored) { /* transient database errors are retried on the next cycle */ }
            }, 1, 5, TimeUnit.MINUTES);
            approvalScheduler.scheduleWithFixedDelay(() -> {
                try { DocumentRetentionService.runScheduled(); }
                catch (RuntimeException ignored) { /* transient database errors are retried on the next daily cycle */ }
            }, 1, 1, TimeUnit.DAYS);
        }
        if (problem == null && Boolean.getBoolean("technocom.clientApi.enabled")) {
            try {
                int port = Integer.getInteger("technocom.clientApi.port", 8787);
                ClientPortalApiServer.start(port);
            } catch (RuntimeException apiError) {
                Alert apiAlert = new Alert(Alert.AlertType.WARNING,
                        "The client API could not start. Check the configured port and API logs.", ButtonType.OK);
                apiAlert.setHeaderText("Technocom PMS - Client API");
                apiAlert.showAndWait();
            }
        }

        Parent root = Navigator.load("/view/WelcomeView.fxml");
        Scene scene = new Scene(root, 1280, 860);
        stage.setTitle("Technocom PMS");
        stage.setMinWidth(1000);
        stage.setMinHeight(700);
        try (InputStream in = Main.class.getResourceAsStream("/images/icon_design.png")) {
            if (in != null) stage.getIcons().add(new Image(in));
        }
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> {
            ClientPortalApiServer.stop();
            if (approvalScheduler != null) approvalScheduler.shutdownNow();
        });
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
