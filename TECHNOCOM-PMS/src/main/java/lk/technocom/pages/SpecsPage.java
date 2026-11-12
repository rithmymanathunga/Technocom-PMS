package lk.technocom.pages;

import javafx.scene.Node;
import lk.technocom.service.FileService;
import lk.technocom.ui.*;
import lk.technocom.ui.Form.F;
import lk.technocom.ui.ListPage.K;
import lk.technocom.util.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public final class SpecsPage {

    private SpecsPage() {}

    private static ListPage page;

    public static Node build() {
        page = new ListPage(Page.SPECS.text(), LanguageManager.x("Specifications - Add/Edit opens as a pop-up"))
                .sql("SELECT s.spec_id, s.spec_no, s.project_id, CONCAT(p.project_code,' ',p.name) AS project, s.title, s.category, "
                        + "s.revision, s.description, s.file_path, IF(s.file_path IS NULL,'NO','YES') AS has_file "
                        + "FROM specifications s JOIN projects p ON p.project_id=s.project_id")
                .orderBy("t.spec_no DESC")
                .stat("Specifications", "SELECT COUNT(*) FROM specifications")
                .stat("With file", "SELECT COUNT(*) FROM specifications WHERE file_path IS NOT NULL")
                .stat("Projects covered", "SELECT COUNT(DISTINCT project_id) FROM specifications")
                .search("spec_no", "title", "project")
                .filterLookup("project_id", "Project", Q.PROJECTS)
                .filterEnum("category", "Category", "HVAC", "ELECTRICAL", "PLUMBING", "FIRE")
                .col("spec_no", "Spec", K.TEXT, 0.8).col("title", "Title", K.TEXT, 1.6)
                .col("project", "Project", K.TEXT, 1.4).col("category", "Category", K.NICE, 0.9)
                .col("revision", "Rev.", K.TEXT, 0.4).col("has_file", "File", K.BADGE, 0.5)
                .actIf("Open", r -> {
                    if (!FileService.open(String.valueOf(r.get("file_path"))))
                        Dialogs.error(LanguageManager.x("The file was not found on this computer."));
                }, r -> r.get("file_path") != null)
                .crud(() -> addEdit(null, SpecsPage::reload), r -> addEdit(r, SpecsPage::reload),
                        r -> Crud.delete("specifications", "spec_id", r.get("spec_id"), "Specs", String.valueOf(r.get("spec_no"))));
        return page.build();
    }

    private static void reload() { page.reload(); }

    public static void addEdit(Map<String, Object> row, Runnable done) {
        boolean add = row == null;
        Map<String, Object> init = add ? null : Db.row("SELECT * FROM specifications WHERE spec_id=?", row.get("spec_id"));
        List<F> f = List.of(
                F.text("spec_no", "Spec no").req().def(add ? Db.nextCode("specifications", "spec_no", "SPEC") : null),
                F.lookup("project_id", "Project", Q.PROJECTS).req(),
                F.text("title", "Title").req().full(),
                F.en("category", "Category", "HVAC", "ELECTRICAL", "PLUMBING", "FIRE").req(),
                F.text("revision", "Revision").req().def("R0"),
                F.area("description", "Description"),
                F.file("file", "Specification file (optional)").full());
        Form.open(add ? LanguageManager.x("Add Spec") : LanguageManager.x("Edit Spec"), f, init, v -> {
            File file = (File) v.remove("file");
            if (file != null) {
                try {
                    v.put("file_path", FileService.store(file, "specs"));
                } catch (IOException e) {
                    return "Could not save the file: " + e.getMessage();
                }
            }
            if (add) {
                Db.insertMap("specifications", v);
                Audit.log("CREATE", "Specs", "Created " + v.get("spec_no"));
            } else {
                Db.updateMap("specifications", "spec_id", row.get("spec_id"), v);
                Audit.log("UPDATE", "Specs", "Updated " + v.get("spec_no"));
            }
            return null;
        }, done);
    }
}
