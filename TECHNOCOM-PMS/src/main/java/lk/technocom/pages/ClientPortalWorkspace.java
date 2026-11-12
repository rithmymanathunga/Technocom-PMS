package lk.technocom.pages;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.scene.layout.*;

import lk.technocom.ui.Dialogs;
import lk.technocom.ui.UI;
import lk.technocom.util.Db;
import lk.technocom.util.Session;
import lk.technocom.workflow.ClientPortalService;
import lk.technocom.workflow.ClientRequestService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class ClientPortalWorkspace {

    private ClientPortalWorkspace() {}

    public static Node build() {

        if (!Session.isClient() || Session.getClientId() <= 0) {
            throw new SecurityException("A linked client account is required.");
        }

        List<Map<String, Object>> projects = ClientPortalService.projects();

        ComboBox<ProjectChoice> project = new ComboBox<>();

        for (Map<String, Object> p : projects) {
            project.getItems().add(new ProjectChoice(
                    ((Number) p.get("project_id")).intValue(),
                    p.get("project_code") + " — " + p.get("name")
            ));
        }

        if (!project.getItems().isEmpty()) {
            project.getSelectionModel().selectFirst();
        }

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        String[][][] defs = {
                {
                        {"milestone_id", "ID"}, {"title", "Milestone"},
                        {"planned_start", "Start"}, {"planned_end", "End"},
                        {"status", "Status"}, {"completion_percent", "Progress"}
                },
                {
                        {"report_date", "Date"}, {"weather", "Weather"},
                        {"work_done", "Work completed"}, {"issues", "Issues"},
                        {"progress_percent", "Progress"}
                },
                {
                        {"drawing_no", "Drawing No."}, {"title", "Title"},
                        {"type", "Type"}, {"current_revision", "Revision"},
                        {"remarks", "Remarks"}
                },
                {
                        {"spec_no", "Spec No."}, {"title", "Title"},
                        {"category", "Category"}, {"revision", "Revision"}
                },
                {
                        {"payment_no", "Payment"}, {"type", "Type"},
                        {"amount", "Amount"}, {"due_date", "Due"},
                        {"paid_date", "Paid"}, {"status", "Status"}
                },
                {
                        {"document_id", "ID"}, {"document_category", "Category"},
                        {"title", "Title"}, {"original_file_name", "File"},
                        {"version_no", "Version"}, {"visibility", "Review status"},
                        {"uploaded_at", "Uploaded"}
                },
                {
                        {"meeting_id", "ID"}, {"title", "Title"},
                        {"meeting_date", "Date"}, {"meeting_time", "Time"},
                        {"venue", "Venue"}, {"status", "Status"}
                },
                {
                        {"reported_date", "Reported"}, {"defect", "Defect"},
                        {"priority", "Priority"}, {"status", "Status"},
                        {"visit_date", "Visit"}, {"notes", "Notes"}
                },
                {
                        {"record_type", "Record"}, {"item", "Checklist item"},
                        {"is_done", "Done"}, {"handover_date", "Handover date"},
                        {"dlp_end_date", "DLP ends"}, {"accepted_by", "Accepted by"},
                        {"status", "Status"}
                }
        };

        String[] names = {
                "Milestones", "Progress Reports", "Approved Drawings",
                "Approved Specs", "Payments", "Documents", "Meetings",
                "DLP", "Handover & DLP Warranty"
        };

        @SuppressWarnings("unchecked")
        TableView<Map<String, Object>>[] docsTable = new TableView[1];

        for (int i = 0; i < names.length; i++) {
            final int ix = i;

            TableView<Map<String, Object>> table = table(defs[i]);

            if (i == 5) {
                docsTable[0] = table;
            }

            Tab tab = new Tab(names[i], table);

            tab.setOnSelectionChanged(e -> {
                if (tab.isSelected()) {
                    load(table, ix, project.getValue());
                }
            });

            tabs.getTabs().add(tab);
        }

        TableView<Map<String, Object>> requests = table(new String[][]{
                {"request_id", "ID"}, {"request_type", "Type"},
                {"subject", "Subject"}, {"preferred_date", "Preferred date"},
                {"status", "Status"}, {"staff_response", "Staff response"},
                {"proposed_date", "Proposed date"}
        });

        Tab requestTab = new Tab("Requests", requests);
        requestTab.setOnSelectionChanged(e -> {
            if (requestTab.isSelected()) {
                requests.getItems().setAll(ClientPortalService.requests());
            }
        });
        tabs.getTabs().add(requestTab);

        TableView<Map<String, Object>> feedback = table(new String[][]{
                {"feedback_id", "ID"}, {"category", "Category"},
                {"subject", "Subject"}, {"message", "Message"},
                {"status", "Status"}, {"response", "Response"},
                {"created_at", "Submitted"}
        });

        Tab feedbackTab = new Tab("Feedback", feedback);
        feedbackTab.setOnSelectionChanged(e -> {
            if (feedbackTab.isSelected()) {
                feedback.getItems().setAll(ClientPortalService.feedback());
            }
        });
        tabs.getTabs().add(feedbackTab);

        TableView<Map<String, Object>> warranty = table(new String[][]{
                {"claim_no", "Claim"}, {"description", "Description"},
                {"reported_date", "Reported"}, {"status", "Status"},
                {"staff_response", "Staff response"}, {"reviewed_at", "Reviewed"}
        });

        Tab warrantyTab = new Tab("Warranty Claims", warranty);
        warrantyTab.setOnSelectionChanged(e -> {
            if (warrantyTab.isSelected()) {
                warranty.getItems().setAll(ClientPortalService.warrantyClaims());
            }
        });
        tabs.getTabs().add(warrantyTab);

        TableView<Map<String, Object>> notifications = table(new String[][]{
                {"notification_id", "ID"}, {"title", "Title"},
                {"message", "Message"}, {"is_read", "Read"},
                {"created_at", "Date"}
        });

        Tab notificationTab = new Tab("Notifications", notifications);
        notificationTab.setOnSelectionChanged(e -> {
            if (notificationTab.isSelected()) {
                notifications.getItems().setAll(ClientPortalService.notifications());
            }
        });
        tabs.getTabs().add(notificationTab);

        TableView<Map<String, Object>> variations = table(new String[][]{
                {"change_request_id", "ID"}, {"project_code", "Project"},
                {"title", "Variation"}, {"cost_impact", "Cost impact"},
                {"time_impact_days", "Days"}, {"status", "Review status"},
                {"review_comment", "Staff decision"},
                {"client_consent", "Client consent"},
                {"client_consent_comment", "Client comment"}
        });

        Tab variationTab = new Tab("Variations", variations);
        variationTab.setOnSelectionChanged(e -> {
            if (variationTab.isSelected()) {
                variations.getItems().setAll(ClientPortalService.variations());
            }
        });
        tabs.getTabs().add(variationTab);

        Button newRequest = new Button("Submit a Project Request");
        newRequest.setOnAction(e -> submitRequest(project, requests));

        Button newVariation = new Button("Submit Variation");
        newVariation.setOnAction(e -> submitVariation(project, variations));

        Button variationDecision =
                new Button("Accept / Decline Approved Variation");
        variationDecision.setOnAction(e -> respondVariation(variations));

        Button newFeedback = new Button("Send Feedback");
        newFeedback.setOnAction(e -> submitFeedback(project, feedback));

        Button newWarranty = new Button("Submit Warranty Claim");
        newWarranty.setOnAction(e -> submitWarranty(project, warranty));

        Button acceptHandover = new Button("Accept Project Handover");
        acceptHandover.setOnAction(e -> acceptHandover(project, tabs));

        Button uploadDocument = new Button("Upload Document for Review");
        uploadDocument.setOnAction(e -> {
            ProjectChoice p = project.getValue();
            if (p == null) return;

            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select a project document");

            java.io.File file = chooser.showOpenDialog(Dialogs.primary());
            if (file == null) return;

            TextInputDialog title = new TextInputDialog(file.getName());
            title.setHeaderText("Document title");
            var t = title.showAndWait();
            if (t.isEmpty()) return;

            TextInputDialog category = new TextInputDialog("CLIENT_SUBMISSION");
            category.setHeaderText("Document category");
            var c = category.showAndWait();
            if (c.isEmpty()) return;

            try {
                ClientPortalService.uploadDocument(p.id, file, t.get(), c.get());
                Dialogs.info(
                        "Document uploaded",
                        "Your document is awaiting Technocom review. " +
                                "It will only be shared after approval."
                );
            } catch (Exception ex) {
                Dialogs.error(Db.friendly(ex));
            }
        });

        Button openDocument = new Button("Open selected approved document");
        openDocument.setOnAction(e -> {
            Map<String, Object> doc =
                    docsTable[0].getSelectionModel().getSelectedItem();

            if (doc == null) return;

            String path = String.valueOf(doc.get("storage_path"));

            if (!lk.technocom.service.FileService.open(path)) {
                Dialogs.error(
                        "The approved document file is not available on this computer."
                );
            }
        });

        Button markRead = new Button("Mark selected notification as read");
        markRead.setOnAction(e -> {
            Map<String, Object> n =
                    notifications.getSelectionModel().getSelectedItem();

            if (n == null) return;

            try {
                ClientPortalService.markNotificationRead(
                        ((Number) n.get("notification_id")).longValue()
                );
                notifications.getItems().setAll(ClientPortalService.notifications());
            } catch (Exception ex) {
                Dialogs.error(Db.friendly(ex));
            }
        });

        tabs.getSelectionModel().selectedIndexProperty().addListener(
                (obs, oldValue, newValue) ->
                        refreshTab(
                                tabs, newValue.intValue(), project.getValue(),
                                requests, feedback, notifications
                        )
        );

        project.valueProperty().addListener((obs, oldValue, newValue) ->
                refreshTab(
                        tabs, tabs.getSelectionModel().getSelectedIndex(),
                        newValue, requests, feedback, notifications
                )
        );

        HBox actions = new HBox(
                8, newRequest, newVariation, variationDecision,
                newFeedback, newWarranty, acceptHandover,
                uploadDocument, openDocument, markRead
        );
        actions.setPadding(new Insets(2, 0, 2, 0));

        if (!tabs.getTabs().isEmpty()) {
            refreshTab(
                    tabs, 0, project.getValue(),
                    requests, feedback, notifications
            );
        }

        VBox root = new VBox(
                12,
                UI.lbl("MY TECHNOCOM CLIENT PORTAL", "h1"),
                UI.muted(
                        "Only information belonging to your linked client account is available here."
                ),
                new HBox(10, new Label("Project:"), project),
                actions,
                tabs
        );

        root.setPadding(new Insets(18));
        VBox.setVgrow(tabs, Priority.ALWAYS);

        return root;
    }

    private static void refreshTab(
            TabPane tabs,
            int ix,
            ProjectChoice p,
            TableView<Map<String, Object>> requests,
            TableView<Map<String, Object>> feedback,
            TableView<Map<String, Object>> notifications
    ) {
        if (ix >= 0 && ix < 9) {
            TableView<Map<String, Object>> t =
                    (TableView<Map<String, Object>>) tabs.getTabs().get(ix).getContent();
            load(t, ix, p);

        } else if (ix == 9 && requests != null) {
            requests.getItems().setAll(ClientPortalService.requests());

        } else if (ix == 10 && feedback != null) {
            feedback.getItems().setAll(ClientPortalService.feedback());

        } else if (ix == 11) {
            TableView<Map<String, Object>> t =
                    (TableView<Map<String, Object>>) tabs.getTabs().get(ix).getContent();
            t.getItems().setAll(ClientPortalService.warrantyClaims());

        } else if (ix == 12 && notifications != null) {
            notifications.getItems().setAll(ClientPortalService.notifications());
        }
    }

    private static void load(
            TableView<Map<String, Object>> t,
            int ix,
            ProjectChoice p
    ) {
        if (p == null) {
            t.getItems().clear();
            return;
        }

        try {
            List<Map<String, Object>> rows = switch (ix) {
                case 0 -> ClientPortalService.milestones(p.id);
                case 1 -> ClientPortalService.progressReports(p.id);
                case 2 -> ClientPortalService.approvedDrawings(p.id);
                case 3 -> ClientPortalService.approvedSpecifications(p.id);
                case 4 -> ClientPortalService.payments(p.id);
                case 5 -> ClientPortalService.approvedDocuments(p.id);
                case 6 -> ClientPortalService.clientMeetings(p.id);
                case 7 -> ClientPortalService.dlpVisits(p.id);
                case 8 -> handoverRows(p.id);
                default -> List.of();
            };

            t.getItems().setAll(rows);

        } catch (Exception ex) {
            Dialogs.error(Db.friendly(ex));
        }
    }

    private static List<Map<String, Object>> handoverRows(int projectId) {
        java.util.ArrayList<Map<String, Object>> rows = new java.util.ArrayList<>();

        Map<String, Object> handover = ClientPortalService.handover(projectId);

        if (handover != null) {
            java.util.HashMap<String, Object> row =
                    new java.util.HashMap<>(handover);
            row.put("record_type", "Handover");
            rows.add(row);
        }

        for (Map<String, Object> item :
                ClientPortalService.handoverChecklist(projectId)) {
            java.util.HashMap<String, Object> row =
                    new java.util.HashMap<>(item);
            row.put("record_type", "Checklist");
            rows.add(row);
        }

        return rows;
    }

    private static void submitRequest(
            ComboBox<ProjectChoice> project,
            TableView<Map<String, Object>> table
    ) {
        ProjectChoice p = project.getValue();

        if (p == null) {
            Dialogs.error("No active project is linked to this account.");
            return;
        }

        ChoiceDialog<String> type = new ChoiceDialog<>(
                "GENERAL",
                List.of(
                        "GENERAL", "MEETING", "SITE_VISIT", "MAINTENANCE",
                        "DLP_DEFECT", "CHANGE_WORK", "SERVICE_REQUEST"
                )
        );

        type.setHeaderText("Request type");
        var selected = type.showAndWait();
        if (selected.isEmpty()) return;

        TextInputDialog subject = new TextInputDialog();
        subject.setHeaderText("Request subject");
        var s = subject.showAndWait();
        if (s.isEmpty()) return;

        TextInputDialog description = new TextInputDialog();
        description.setHeaderText("Describe your request");
        var d = description.showAndWait();
        if (d.isEmpty()) return;

        try {
            ClientRequestService.submit(
                    p.id, selected.get(), s.get(), d.get(),
                    LocalDate.now().plusDays(7), null
            );

            table.getItems().setAll(ClientPortalService.requests());

            Dialogs.info(
                    "Request submitted",
                    "Your request has been sent to the Technocom team for review. " +
                            "It is not confirmed until staff respond."
            );

        } catch (Exception ex) {
            Dialogs.error(Db.friendly(ex));
        }
    }

    private static void acceptHandover(
            ComboBox<ProjectChoice> project,
            TabPane tabs
    ) {
        ProjectChoice p = project.getValue();

        if (p == null) {
            Dialogs.error("No active project is linked to this account.");
            return;
        }

        if (!Dialogs.confirm(
                "Accept the handover for " + p.label +
                        "? This confirms that you have reviewed and accepted the completed handover checklist."
        )) {
            return;
        }

        try {
            ClientPortalService.acceptHandover(p.id);

            // FIXED: refreshTab accepts six arguments.
            refreshTab(tabs, 8, p, null, null, null);

            Dialogs.info(
                    "Handover accepted",
                    "Your acceptance has been recorded. " +
                            "The project handover is now marked as HANDED_OVER."
            );

        } catch (Exception ex) {
            Dialogs.error(Db.friendly(ex));
        }
    }

    private static void submitVariation(
            ComboBox<ProjectChoice> project,
            TableView<Map<String, Object>> table
    ) {
        ProjectChoice p = project.getValue();
        if (p == null) {
            Dialogs.error("No active project is linked to this account.");
            return;
        }

        TextInputDialog title = new TextInputDialog();
        title.setHeaderText("Variation title");
        var t = title.showAndWait();
        if (t.isEmpty()) return;

        TextInputDialog scope = new TextInputDialog();
        scope.setHeaderText("Detailed scope and justification");
        var d = scope.showAndWait();
        if (d.isEmpty()) return;

        TextInputDialog cost = new TextInputDialog("0");
        cost.setHeaderText("Estimated cost impact");
        var c = cost.showAndWait();
        if (c.isEmpty()) return;

        TextInputDialog days = new TextInputDialog("0");
        days.setHeaderText("Estimated schedule impact (days)");
        var day = days.showAndWait();
        if (day.isEmpty()) return;

        try {
            ClientPortalService.submitVariation(
                    p.id, t.get(), d.get(),
                    Double.parseDouble(c.get()),
                    Integer.parseInt(day.get())
            );

            table.getItems().setAll(ClientPortalService.variations());

            Dialogs.info(
                    "Variation submitted",
                    "Your variation has been submitted for Technocom review. " +
                            "It is not approved or part of the contract until reviewed and accepted through the formal process."
            );

        } catch (Exception ex) {
            Dialogs.error(Db.friendly(ex));
        }
    }

    private static void respondVariation(
            TableView<Map<String, Object>> table
    ) {
        Map<String, Object> row =
                table.getSelectionModel().getSelectedItem();

        if (row == null) {
            Dialogs.error("Select an approved variation first.");
            return;
        }

        ChoiceDialog<String> decision =
                new ChoiceDialog<>("ACCEPTED", List.of("ACCEPTED", "DECLINED"));

        decision.setHeaderText("Client consent decision");
        var choice = decision.showAndWait();
        if (choice.isEmpty()) return;

        TextInputDialog comment = new TextInputDialog();
        comment.setHeaderText("Mandatory client response comment");
        var note = comment.showAndWait();
        if (note.isEmpty()) return;

        try {
            ClientPortalService.respondToVariation(
                    ((Number) row.get("change_request_id")).longValue(),
                    choice.get(),
                    note.get()
            );

            table.getItems().setAll(ClientPortalService.variations());

            Dialogs.info(
                    "Response recorded",
                    "Your consent response has been recorded."
            );

        } catch (Exception ex) {
            Dialogs.error(Db.friendly(ex));
        }
    }

    private static void submitWarranty(
            ComboBox<ProjectChoice> project,
            TableView<Map<String, Object>> table
    ) {
        ProjectChoice p = project.getValue();
        if (p == null) return;

        TextInputDialog d = new TextInputDialog();
        d.setHeaderText("Describe the warranty issue");
        var v = d.showAndWait();
        if (v.isEmpty()) return;

        try {
            ClientPortalService.submitWarrantyClaim(p.id, v.get());
            table.getItems().setAll(ClientPortalService.warrantyClaims());

            Dialogs.info(
                    "Warranty Claim Submitted",
                    "Your claim has been sent to Technocom for review. " +
                            "A service visit is not confirmed until staff schedule it."
            );

        } catch (Exception ex) {
            Dialogs.error(Db.friendly(ex));
        }
    }

    private static void submitFeedback(
            ComboBox<ProjectChoice> project,
            TableView<Map<String, Object>> table
    ) {
        ProjectChoice p = project.getValue();
        if (p == null) return;

        TextInputDialog category = new TextInputDialog("GENERAL");
        category.setHeaderText("Feedback category");
        var c = category.showAndWait();
        if (c.isEmpty()) return;

        TextInputDialog subject = new TextInputDialog();
        subject.setHeaderText("Subject");
        var s = subject.showAndWait();
        if (s.isEmpty()) return;

        TextInputDialog message = new TextInputDialog();
        message.setHeaderText("Your feedback");
        var m = message.showAndWait();
        if (m.isEmpty()) return;

        try {
            ClientPortalService.submitFeedback(
                    p.id, c.get(), s.get(), m.get()
            );

            table.getItems().setAll(ClientPortalService.feedback());

            Dialogs.info(
                    "Feedback sent",
                    "Your feedback has been submitted."
            );

        } catch (Exception ex) {
            Dialogs.error(Db.friendly(ex));
        }
    }

    private static TableView<Map<String, Object>> table(String[][] defs) {
        TableView<Map<String, Object>> t = new TableView<>();

        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        t.setPlaceholder(new Label("No records found"));

        for (String[] d : defs) {
            TableColumn<Map<String, Object>, String> c =
                    new TableColumn<>(d[1]);

            c.setCellValueFactory(v ->
                    new javafx.beans.property.SimpleStringProperty(
                            v.getValue().get(d[0]) == null
                                    ? ""
                                    : String.valueOf(v.getValue().get(d[0]))
                    )
            );

            t.getColumns().add(c);
        }

        return t;
    }

    private record ProjectChoice(int id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }
}