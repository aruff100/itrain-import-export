package com.example.itrain_import_export;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Funktionen → "Verwaiste Einträge prüfen" (seit 13.10.2026): sucht
 * <ul>
 *   <li>Einträge der Hilfs-Kategorien ({@link #HELPER_CATEGORIES}), auf die
 *       nichts mehr verweist - weder ein anderer Eintrag noch das Stellpult
 *       oder ein anderer Teil der Datei außerhalb von control-items;</li>
 *   <li>alle "~"-Einträge (Überbleibsel eines Imports, siehe
 *       {@code CategoryEditor.onExport}) in jeder Kategorie.</li>
 * </ul>
 * Aktionen, Züge, Fahrstraßen usw. werden bewusst NICHT auf "verwaist"
 * geprüft: Sie werden in iTrain-Dateien normalerweise von nichts anderem
 * referenziert, sie wären also fast alle ein Treffer.
 * <p>
 * Ein "~"-Eintrag, der noch verwendet wird, erscheint mit Angabe der
 * Verwender, lässt sich aber nicht löschen - sonst zeigten Verweise ins Leere
 * und iTrain könnte die Datei nicht mehr öffnen.
 * <p>
 * Verweise werden wie beim Export erkannt (Tag + name-Attribut, ersatzweise
 * nur über den Namen, siehe {@code CategoryEditor.ReferenceIndex}).
 */
public final class OrphanCleanup {

    private OrphanCleanup() {
    }

    /** Kategorien, deren Einträge nur existieren, um von anderen verwendet zu werden. */
    static final Set<String> HELPER_CATEGORIES = Set.of(
            "functions", "interfaces", "feedbacks", "accessories", "memory",
            "boosters", "train-types", "blocks", "stations");

    /** Höchstens so viele Verwender werden in der Grund-Spalte aufgezählt. */
    private static final int MAX_USERS_SHOWN = 3;

    /** Eine Zeile der Tabelle. */
    public static final class Row {
        private final XmlNode categoryNode;
        private final XmlNode entry;
        private final List<String> usedBy;
        private final SimpleBooleanProperty delete = new SimpleBooleanProperty(false);

        Row(XmlNode categoryNode, XmlNode entry, List<String> usedBy) {
            this.categoryNode = categoryNode;
            this.entry = entry;
            this.usedBy = usedBy;
        }

        public XmlNode categoryNode() {
            return categoryNode;
        }

        public XmlNode entry() {
            return entry;
        }

        /** Verwendete "~"-Einträge dürfen nicht gelöscht werden. */
        public boolean isLocked() {
            return !usedBy.isEmpty();
        }

        public boolean isDelete() {
            return delete.get() && !isLocked();
        }
    }

    /** Sucht verwaiste und "~"-Einträge im Dokument; leere Liste, wenn nichts gefunden. */
    public static List<Row> find(XmlNode root) {
        XmlNode controlItems = root.findChild("control-items");
        if (controlItems == null) {
            return List.of();
        }

        // Index aller Einträge: erst Tag+Name, ersatzweise nur Name.
        Map<String, XmlNode> byTagAndName = new HashMap<>();
        Map<String, XmlNode> byName = new HashMap<>();
        for (XmlNode category : controlItems.getChildren()) {
            for (XmlNode entry : category.getChildren()) {
                String name = entry.getAttribute("name");
                if (name == null || name.isBlank()) {
                    continue;
                }
                byTagAndName.putIfAbsent(entry.getTagName() + " " + name, entry);
                byName.putIfAbsent(name, entry);
            }
        }

        // Verwender je Eintrag sammeln (Identität, nicht equals).
        Map<XmlNode, Set<String>> users = new IdentityHashMap<>();
        for (XmlNode category : controlItems.getChildren()) {
            for (XmlNode entry : category.getChildren()) {
                String label = entry.getName().isBlank() ? entry.getTagName() : entry.getName();
                collectReferences(entry, entry, label, byTagAndName, byName, users);
            }
        }
        I18n i18n = I18n.getInstance();
        for (XmlNode part : root.getChildren()) {
            if (part == controlItems) {
                continue;
            }
            String label = "switchboards".equals(part.getTagName())
                    ? i18n.t("cleanup.usedBySwitchboard") : part.getTagName();
            collectReferences(part, null, label, byTagAndName, byName, users);
        }

        List<Row> rows = new ArrayList<>();
        for (XmlNode category : controlItems.getChildren()) {
            boolean helper = HELPER_CATEGORIES.contains(category.getTagName());
            for (XmlNode entry : category.getChildren()) {
                String name = entry.getName();
                boolean tilde = name.startsWith("~");
                Set<String> usedBy = users.getOrDefault(entry, Set.of());
                if (tilde) {
                    rows.add(new Row(category, entry, new ArrayList<>(usedBy)));
                } else if (helper && usedBy.isEmpty() && !name.isBlank()) {
                    rows.add(new Row(category, entry, List.of()));
                }
            }
        }
        return rows;
    }

    /**
     * Alle Nachfahren von {@code node} mit name-Attribut auflösen und beim
     * Ziel {@code label} als Verwender eintragen. {@code self} (der Eintrag
     * selbst) zählt nicht als Verwender seiner selbst.
     */
    private static void collectReferences(XmlNode node, XmlNode self, String label,
                                          Map<String, XmlNode> byTagAndName, Map<String, XmlNode> byName,
                                          Map<XmlNode, Set<String>> users) {
        for (XmlNode child : node.getChildren()) {
            String refName = child.getAttribute("name");
            if (refName != null && !refName.isBlank()) {
                XmlNode target = byTagAndName.get(child.getTagName() + " " + refName);
                if (target == null) {
                    target = byName.get(refName);
                }
                if (target != null && target != self) {
                    users.computeIfAbsent(target, k -> new LinkedHashSet<>()).add(label);
                }
            }
            collectReferences(child, self, label, byTagAndName, byName, users);
        }
    }

    /**
     * Zeigt die Tabelle. Liefert true bei "Weiter und schließen" - dann
     * stehen die Entscheidungen in den {@link Row}-Objekten
     * ({@link Row#isDelete()}); false bei Abbruch.
     */
    public static boolean show(Window owner, I18n i18n, List<Row> rows) {
        AppSettings settings = AppSettings.getInstance();
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(i18n.t("cleanup.title"));
        dialog.setHeaderText(null);
        dialog.setResizable(true);

        Label hint = new Label(i18n.t("cleanup.hint", rows.size()));
        hint.setWrapText(true);

        TableView<Row> table = new TableView<>(FXCollections.observableArrayList(rows));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<Row, String> categoryColumn = new TableColumn<>(i18n.t("editor.importCollisionCategory"));
        categoryColumn.setCellValueFactory(cell -> {
            String category = cell.getValue().categoryNode.getTagName();
            String key = "category." + category;
            String translated = i18n.t(key);
            return new ReadOnlyStringWrapper(translated.equals(key) ? category : translated);
        });
        categoryColumn.setPrefWidth(130);

        TableColumn<Row, String> typeColumn = new TableColumn<>(i18n.t("editor.columnType"));
        typeColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().entry.getTagName()));
        typeColumn.setPrefWidth(100);

        TableColumn<Row, String> nameColumn = new TableColumn<>(i18n.t("editor.columnName"));
        nameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().entry.getName()));
        nameColumn.setPrefWidth(200);

        TableColumn<Row, String> reasonColumn = new TableColumn<>(i18n.t("cleanup.colReason"));
        reasonColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(reasonText(i18n, cell.getValue())));
        reasonColumn.setPrefWidth(260);

        String keepText = i18n.t("cleanup.keep");
        String deleteText = i18n.t("cleanup.delete");
        TableColumn<Row, Row> actionColumn = new TableColumn<>(i18n.t("cleanup.colAction"));
        actionColumn.setCellValueFactory(cell -> new javafx.beans.property.ReadOnlyObjectWrapper<>(cell.getValue()));
        actionColumn.setCellFactory(col -> new TableCell<>() {
            private final ComboBox<String> combo = new ComboBox<>(FXCollections.observableArrayList(keepText, deleteText));
            private Row bound;

            {
                combo.setMaxWidth(Double.MAX_VALUE);
                combo.valueProperty().addListener((obs, o, n) -> {
                    if (bound != null && n != null) {
                        bound.delete.set(deleteText.equals(n));
                    }
                });
            }

            @Override
            protected void updateItem(Row row, boolean empty) {
                super.updateItem(row, empty);
                if (bound != null) {
                    bound.delete.removeListener(syncListener);
                }
                bound = null;
                if (empty || row == null) {
                    setGraphic(null);
                    return;
                }
                combo.setValue(row.delete.get() && !row.isLocked() ? deleteText : keepText);
                combo.setDisable(row.isLocked());
                bound = row;
                row.delete.addListener(syncListener);
                setGraphic(combo);
            }

            // "Alles löschen" ändert die Zeilen direkt - die sichtbare Auswahl nachziehen.
            private final javafx.beans.value.ChangeListener<Boolean> syncListener = (obs, o, n) ->
                    combo.setValue(n && bound != null && !bound.isLocked() ? deleteText : keepText);
        });
        actionColumn.setPrefWidth(130);

        table.getColumns().setAll(List.of(categoryColumn, typeColumn, nameColumn, reasonColumn, actionColumn));
        table.setPrefHeight(Math.min(520, 60 + rows.size() * 30));

        VBox content = new VBox(10, hint, table);
        content.setPadding(new Insets(4));
        VBox.setVgrow(table, Priority.ALWAYS);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(900);

        ButtonType deleteAll = new ButtonType(i18n.t("cleanup.deleteAll"), ButtonBar.ButtonData.LEFT);
        ButtonType cancel = new ButtonType(i18n.t("bidib.abortButton"), ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType apply = new ButtonType(i18n.t("cleanup.apply"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(deleteAll, cancel, apply);
        // "Alles löschen" setzt nur alle (nicht gesperrten) Zeilen auf
        // Löschen und lässt den Dialog offen - ausgeführt wird erst mit
        // "Weiter und schließen".
        Node deleteAllNode = dialog.getDialogPane().lookupButton(deleteAll);
        deleteAllNode.addEventFilter(ActionEvent.ACTION, e -> {
            for (Row row : rows) {
                if (!row.isLocked()) {
                    row.delete.set(true);
                }
            }
            e.consume();
        });

        ThemeManager.apply(dialog.getDialogPane().getScene(), settings.getTheme());
        dialog.getDialogPane().sceneProperty().addListener((obs, oldScene, newScene) ->
                ThemeManager.apply(newScene, settings.getTheme()));
        return dialog.showAndWait().orElse(cancel) == apply;
    }

    private static String reasonText(I18n i18n, Row row) {
        if (!row.entry.getName().startsWith("~")) {
            return i18n.t("cleanup.reasonOrphan");
        }
        if (row.usedBy.isEmpty()) {
            return i18n.t("cleanup.reasonTildeUnused");
        }
        List<String> shown = row.usedBy.subList(0, Math.min(MAX_USERS_SHOWN, row.usedBy.size()));
        String users = String.join(", ", shown) + (row.usedBy.size() > MAX_USERS_SHOWN ? ", …" : "");
        return i18n.t("cleanup.reasonTildeUsed", users);
    }
}
