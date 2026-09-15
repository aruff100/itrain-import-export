package com.example.itrain_import_export;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.util.List;

/**
 * Rückfrage beim Import ins Hauptfenster (seit 2.5), wenn Namen der
 * importierten Einträge in der iTrain-Datei bereits vorkommen: eine Tabelle
 * mit einer Zeile je Treffer, in der der Nutzer je Eintrag wählt, ob der
 * vorhandene Eintrag mit dem neuen synchronisiert (ersetzt) wird, der neue
 * zusätzlich unter freiem Namen angelegt wird, oder der neue übersprungen
 * wird. Voreinstellung ist Synchronisieren - das ist der Fall, für den die
 * Abfrage gedacht ist: Objekte aus dem Fenster "Systeme" (Rückmelder,
 * Zubehör, Booster, Schnittstelle) ein zweites Mal einspielen, nachdem sie
 * dort bearbeitet wurden.
 */
public final class ImportCollisionDialog {

    private ImportCollisionDialog() {
    }

    /** Was mit einer kollidierenden Zeile geschehen soll. */
    public enum Decision {
        SYNCHRONIZE, CREATE_NEW, SKIP
    }

    /** Eine kollidierende Zeile des Imports; {@code rowIndex} ist ihre Position in der Datei. */
    public static final class Collision {
        private final int rowIndex;
        private final String category;
        private final String tagName;
        private final String name;
        private final ObjectProperty<Decision> decision = new SimpleObjectProperty<>(Decision.SYNCHRONIZE);

        public Collision(int rowIndex, String category, String tagName, String name) {
            this.rowIndex = rowIndex;
            this.category = category;
            this.tagName = tagName;
            this.name = name;
        }

        public int rowIndex() {
            return rowIndex;
        }

        public Decision getDecision() {
            return decision.get();
        }
    }

    /**
     * Zeigt die Tabelle; die Entscheidungen stehen danach in den
     * {@link Collision}-Objekten. Liefert false, wenn der Nutzer abgebrochen
     * hat - dann darf nichts importiert werden.
     */
    public static boolean show(Window owner, I18n i18n, List<Collision> collisions) {
        AppSettings settings = AppSettings.getInstance();
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(i18n.t("editor.importCollisionTitle"));
        dialog.setHeaderText(null);
        dialog.setResizable(true);

        Label hint = new Label(i18n.t("editor.importCollisionHint", collisions.size()));
        hint.setWrapText(true);

        TableView<Collision> table = new TableView<>(FXCollections.observableArrayList(collisions));
        table.setEditable(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<Collision, String> categoryColumn = new TableColumn<>(i18n.t("editor.importCollisionCategory"));
        categoryColumn.setCellValueFactory(cell -> {
            String key = "category." + cell.getValue().category;
            String translated = i18n.t(key);
            return new ReadOnlyStringWrapper(translated.equals(key) ? cell.getValue().category : translated);
        });
        categoryColumn.setPrefWidth(140);
        categoryColumn.setEditable(false);

        TableColumn<Collision, String> typeColumn = new TableColumn<>(i18n.t("editor.columnType"));
        typeColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().tagName));
        typeColumn.setPrefWidth(110);
        typeColumn.setEditable(false);

        TableColumn<Collision, String> nameColumn = new TableColumn<>(i18n.t("editor.columnName"));
        nameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().name));
        nameColumn.setPrefWidth(220);
        nameColumn.setEditable(false);

        StringConverter<Decision> converter = new StringConverter<>() {
            @Override
            public String toString(Decision d) {
                if (d == null) {
                    return "";
                }
                switch (d) {
                    case CREATE_NEW:
                        return i18n.t("editor.importCollisionCreateNew");
                    case SKIP:
                        return i18n.t("editor.importCollisionSkip");
                    default:
                        return i18n.t("editor.importCollisionSynchronize");
                }
            }

            @Override
            public Decision fromString(String s) {
                for (Decision d : Decision.values()) {
                    if (toString(d).equals(s)) {
                        return d;
                    }
                }
                return Decision.SYNCHRONIZE;
            }
        };
        TableColumn<Collision, Decision> decisionColumn = new TableColumn<>(i18n.t("editor.importCollisionAction"));
        decisionColumn.setCellValueFactory(cell -> cell.getValue().decision);
        decisionColumn.setCellFactory(ComboBoxTableCell.forTableColumn(converter, Decision.values()));
        decisionColumn.setOnEditCommit(e -> e.getRowValue().decision.set(e.getNewValue()));
        decisionColumn.setPrefWidth(200);
        decisionColumn.setEditable(true);

        table.getColumns().setAll(List.of(categoryColumn, typeColumn, nameColumn, decisionColumn));
        table.setPrefHeight(Math.min(420, 60 + collisions.size() * 28));

        VBox content = new VBox(10, hint, table);
        content.setPadding(new Insets(4));
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(720);

        ButtonType importButton = new ButtonType(i18n.t("editor.import"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(i18n.t("bidib.abortButton"), ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(importButton, cancel);
        ThemeManager.apply(dialog.getDialogPane().getScene(), settings.getTheme());
        dialog.getDialogPane().sceneProperty().addListener((obs, oldScene, newScene) ->
                ThemeManager.apply(newScene, settings.getTheme()));
        return dialog.showAndWait().orElse(cancel) == importButton;
    }
}
