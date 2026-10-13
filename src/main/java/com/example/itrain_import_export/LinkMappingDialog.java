package com.example.itrain_import_export;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Zuordnung der mit-exportierten Verknüpfungen beim Import (seit 13.10.2026).
 * <p>
 * Ein Export enthält neben den markierten Einträgen alle verknüpften
 * Einträge (umbenannt in "~Name", siehe {@code CategoryEditor.onExport}).
 * Beim Import in eine Datei, die diese Einträge schon hat, sollen sie nicht
 * doppelt als "~Name" angelegt werden: Je Verknüpfung wählt der Nutzer
 * rechts einen vorhandenen Eintrag derselben Kategorie und desselben Typs
 * und übernimmt ihn mit dem Pfeil-Knopf. Der "~"-Datensatz wird dann nicht
 * importiert, und alle Verweise darauf zeigen auf den vorhandenen Eintrag.
 * Nicht ersetzte Verknüpfungen werden wie bisher als "~Name" neu angelegt.
 * <p>
 * Platzhalter (Export "Ohne Verknüpfungen") haben keinen Inhalt - der Import
 * ist erst möglich, wenn jeder Platzhalter ersetzt oder bewusst als leerer
 * "~Name"-Eintrag neu eingetragen ist ("Offene Ersetzung als Neu eintragen").
 */
public final class LinkMappingDialog {

    private LinkMappingDialog() {
    }

    /** Eine Auswahl in der rechten Liste: entweder ein vorhandener Eintrag oder "neu anlegen". */
    public record Choice(String name, boolean isNew) {
    }

    /** Eine Verknüpfung aus dem Export. */
    public static final class LinkRow {
        private final String category;
        private final String tagName;
        /** Name, wie er nach dem Einlesen im Import steht (z.B. "~RM1" oder "~2RM1"). */
        private final String importName;
        /** Name in der Quelldatei, ohne "~" - Grundlage für die automatische Zuordnung. */
        private final String originalName;
        private final boolean placeholder;
        private final List<String> candidates;
        private final ObjectProperty<Choice> selected = new SimpleObjectProperty<>();
        /** Tatsächlich übernommene Entscheidung; null = noch offen (nur bei Platzhaltern möglich). */
        private final ObjectProperty<Choice> applied = new SimpleObjectProperty<>();

        public LinkRow(String category, String tagName, String importName, String originalName,
                       boolean placeholder, List<String> candidates) {
            this.category = category;
            this.tagName = tagName;
            this.importName = importName;
            this.originalName = originalName;
            this.placeholder = placeholder;
            this.candidates = candidates;
            if (hasExactMatch()) {
                Choice exact = new Choice(originalName, false);
                selected.set(exact);
                applied.set(exact);
            } else if (!placeholder) {
                selected.set(newChoice());
                applied.set(newChoice());
            }
        }

        public boolean hasExactMatch() {
            return candidates.contains(originalName);
        }

        public boolean isPlaceholder() {
            return placeholder;
        }

        public String importName() {
            return importName;
        }

        public String category() {
            return category;
        }

        /** Name des vorhandenen Eintrags, durch den ersetzt wird, oder null (neu anlegen bzw. offen). */
        public String replacementName() {
            Choice c = applied.get();
            return c == null || c.isNew() ? null : c.name();
        }

        private Choice newChoice() {
            return new Choice(importName, true);
        }

        private void applySelection() {
            if (selected.get() != null) {
                applied.set(selected.get());
            }
        }
    }

    /**
     * Zeigt die Zuordnungstabelle. Liefert die Zuordnung Import-Name →
     * vorhandener Name für alle ersetzten Verknüpfungen, oder leer, wenn der
     * Nutzer abgebrochen hat (dann darf nichts importiert werden).
     */
    public static Optional<Map<String, String>> show(Window owner, I18n i18n, List<LinkRow> rows) {
        AppSettings settings = AppSettings.getInstance();
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(i18n.t("editor.importLinksTitle"));
        dialog.setHeaderText(null);
        dialog.setResizable(true);

        Label hint = new Label(i18n.t("editor.importLinksHint", rows.size()));
        hint.setWrapText(true);
        hint.setMinHeight(Region.USE_PREF_SIZE);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(4);
        grid.setPadding(new Insets(6));
        ColumnConstraints left = new ColumnConstraints();
        left.setPercentWidth(46);
        ColumnConstraints middle = new ColumnConstraints();
        middle.setPercentWidth(8);
        middle.setHalignment(HPos.CENTER);
        ColumnConstraints right = new ColumnConstraints();
        right.setPercentWidth(46);
        grid.getColumnConstraints().addAll(left, middle, right);

        Label exportHeader = new Label(i18n.t("editor.importLinksExport"));
        exportHeader.setStyle("-fx-font-weight: bold;");
        Label targetHeader = new Label(i18n.t("editor.importLinksTarget"));
        targetHeader.setStyle("-fx-font-weight: bold;");
        int r = 0;
        grid.add(exportHeader, 0, r);
        grid.add(targetHeader, 2, r);
        r++;

        // Nach Kategorie gruppiert, in der Reihenfolge des ersten Auftretens im
        // Export - nur Kategorien, die im Export tatsächlich vorkommen.
        Map<String, List<LinkRow>> byCategory = new LinkedHashMap<>();
        for (LinkRow row : rows) {
            byCategory.computeIfAbsent(row.category, k -> new ArrayList<>()).add(row);
        }
        for (Map.Entry<String, List<LinkRow>> group : byCategory.entrySet()) {
            String key = "category." + group.getKey();
            String translated = i18n.t(key);
            Label categoryLabel = new Label((translated.equals(key) ? group.getKey() : translated)
                    + " (" + group.getValue().size() + ")");
            categoryLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 1.1em;");
            VBox categoryBox = new VBox(2, new Separator(), categoryLabel);
            grid.add(categoryBox, 0, r, 3, 1);
            r++;
            for (LinkRow row : group.getValue()) {
                addRow(grid, r++, row, i18n);
            }
        }

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(Math.min(520, 60 + rows.size() * 34));

        // "Offene Ersetzung als Neu eintragen": nur Zeilen, die noch "Bitte
        // ersetzen" zeigen (offene Platzhalter), werden als "~Name" neu
        // angelegt - als leerer Eintrag, da ein Platzhalter keinen Inhalt hat.
        Button allNew = new Button(i18n.t("editor.importLinksAllNew"));
        allNew.setOnAction(e -> {
            for (LinkRow row : rows) {
                if (row.applied.get() == null) {
                    row.selected.set(row.newChoice());
                    row.applySelection();
                }
            }
        });
        // "Alle Ersetzungen übernehmen": die aktuelle Auswahl der rechten
        // Seite wird für jede Zeile übernommen (wie der Pfeil-Knopf je Zeile).
        Button takeAll = new Button(i18n.t("editor.importLinksTakeAll"));
        takeAll.setOnAction(e -> {
            for (LinkRow row : rows) {
                row.applySelection();
            }
        });
        Label openLabel = new Label();
        openLabel.setStyle("-fx-text-fill: #c62828;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bulkButtons = new HBox(8, allNew, takeAll, spacer, openLabel);
        bulkButtons.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(10, hint, scroll, bulkButtons);
        content.setPadding(new Insets(4));
        VBox.setVgrow(scroll, Priority.ALWAYS);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(820);

        ButtonType importButton = new ButtonType(i18n.t("editor.import"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(i18n.t("bidib.abortButton"), ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(importButton, cancel);
        Node importNode = dialog.getDialogPane().lookupButton(importButton);

        // Import erst möglich, wenn kein Platzhalter mehr offen ist.
        Runnable updateOpen = () -> {
            long open = rows.stream().filter(row -> row.applied.get() == null).count();
            importNode.setDisable(open > 0);
            openLabel.setText(open > 0 ? i18n.t("editor.importLinksOpen", open) : "");
        };
        for (LinkRow row : rows) {
            row.applied.addListener((obs, o, n) -> updateOpen.run());
        }
        updateOpen.run();

        ThemeManager.apply(dialog.getDialogPane().getScene(), settings.getTheme());
        dialog.getDialogPane().sceneProperty().addListener((obs, oldScene, newScene) ->
                ThemeManager.apply(newScene, settings.getTheme()));
        if (dialog.showAndWait().orElse(cancel) != importButton) {
            return Optional.empty();
        }
        Map<String, String> mapping = new LinkedHashMap<>();
        for (LinkRow row : rows) {
            String replacement = row.replacementName();
            if (replacement != null) {
                mapping.put(row.importName, replacement);
            }
        }
        return Optional.of(mapping);
    }

    private static void addRow(GridPane grid, int rowIndex, LinkRow row, I18n i18n) {
        // Links: Inhalt des Exports + aktueller Status.
        String shownName = row.placeholder ? row.originalName : row.importName;
        Label name = new Label(row.tagName + "  " + shownName);
        Label status = new Label();
        status.setStyle("-fx-font-size: 0.9em;");
        Runnable updateStatus = () -> {
            String replacement = row.replacementName();
            if (replacement != null) {
                status.setText(i18n.t("editor.importLinksStatusReplaced", replacement));
                status.setStyle("-fx-font-size: 0.9em; -fx-text-fill: #2e7d32;");
            } else if (row.applied.get() == null) {
                status.setText(i18n.t("editor.importLinksPleaseReplace"));
                status.setStyle("-fx-font-size: 0.9em; -fx-text-fill: #c62828;");
            } else {
                status.setText(i18n.t("editor.importLinksStatusNew"));
                status.setStyle("-fx-font-size: 0.9em; -fx-text-fill: #e65100;");
            }
        };
        row.applied.addListener((obs, o, n) -> updateStatus.run());
        updateStatus.run();
        grid.add(new VBox(1, name, status), 0, rowIndex);

        // Rechts: Liste der vorhandenen Einträge gleichen Typs, zusätzlich
        // "~Name (neu)" - bei Platzhaltern bleibt die Liste anfangs leer
        // ("Bitte ersetzen"), "neu" ist dort nur bewusst wählbar.
        List<Choice> items = new ArrayList<>();
        items.add(row.newChoice());
        for (String candidate : row.candidates) {
            items.add(new Choice(candidate, false));
        }
        ComboBox<Choice> combo = new ComboBox<>(FXCollections.observableArrayList(items));
        combo.setMaxWidth(Double.MAX_VALUE);
        combo.setVisibleRowCount(15);
        combo.setPromptText(i18n.t("editor.importLinksPleaseReplace"));
        combo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Choice c) {
                if (c == null) {
                    return "";
                }
                return c.isNew() ? i18n.t("editor.importLinksNewOption", c.name()) : c.name();
            }

            @Override
            public Choice fromString(String s) {
                return null;
            }
        });
        combo.valueProperty().bindBidirectional(row.selected);
        grid.add(combo, 2, rowIndex);

        // Mitte: Pfeil-Knopf übernimmt die rechte Auswahl. Solange die rechte
        // Auswahl noch nicht übernommen ist, wird der Knopf hervorgehoben.
        Button arrow = new Button("◀");
        arrow.setTooltip(new Tooltip(i18n.t("editor.importLinksReplaceTooltip")));
        arrow.setOnAction(e -> row.applySelection());
        Runnable updateArrow = () -> {
            boolean pending = row.selected.get() != null && !row.selected.get().equals(row.applied.get());
            arrow.setDisable(row.selected.get() == null);
            arrow.setStyle(pending ? "-fx-base: #ffb300;" : "");
        };
        row.selected.addListener((obs, o, n) -> updateArrow.run());
        row.applied.addListener((obs, o, n) -> updateArrow.run());
        updateArrow.run();
        grid.add(arrow, 1, rowIndex);
    }
}
