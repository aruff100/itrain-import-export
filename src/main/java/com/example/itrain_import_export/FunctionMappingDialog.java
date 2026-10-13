package com.example.itrain_import_export;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Fenster "Funktionen" im Bearbeitungsfenster einer Lokomotive (seit
 * 13.10.2026): Gegenueberstellung "Funktion auf der mc2" (aus loco.ini +
 * Symbolliste der mc2, siehe {@link Mc2LocoReader#functionNames}) gegen
 * "Funktion in iTrain" (Name, der in die iTrain-Datei geschrieben wird -
 * vorbelegt aus dem zugeordneten vorhandenen iTrain-Eintrag, sonst aus dem
 * erzeugten Datensatz). Der Pfeil uebernimmt den mc2-Namen nach rechts; der
 * rechte Name ist frei aenderbar.
 * <p>
 * Liefert nur das Ergebnis (Nummer -&gt; Name); geschrieben wird erst beim
 * "Uebernehmen" des Bearbeitungsfensters ({@link #apply}).
 */
final class FunctionMappingDialog {

    private FunctionMappingDialog() {
    }

    /**
     * Die Funktionsliste, von der die rechte Seite ausgeht: die schon einmal
     * bearbeitete eigene Liste, sonst die des zugeordneten vorhandenen
     * iTrain-Eintrags, sonst die des erzeugten Datensatzes.
     */
    static XmlNode baseFunctions(SystemsObject object) {
        XmlNode source = object.isMatched() && !object.isFunctionsEdited()
                ? object.getMatchedEntry() : object.getXml();
        return source == null ? null : source.findChild("functions");
    }

    /** Name je Funktionsnummer aus einer {@code <functions>}-Liste. */
    static Map<Integer, String> namesOf(XmlNode functions) {
        Map<Integer, String> result = new TreeMap<>();
        if (functions == null) {
            return result;
        }
        for (XmlNode function : functions.getChildren()) {
            if (!"function".equals(function.getTagName())) {
                continue;
            }
            try {
                int nr = Integer.parseInt(function.getAttribute("nr").trim());
                String name = function.getAttribute("name");
                result.put(nr, name == null ? "" : name);
            } catch (RuntimeException ignored) {
                // Eintrag ohne lesbare Nummer - nicht darstellbar
            }
        }
        return result;
    }

    /**
     * Zeigt das Fenster. {@code current} ist der aktuelle Stand der rechten
     * Seite (bei erneutem Oeffnen das letzte Ergebnis). Leer bei Abbruch.
     */
    static Optional<Map<Integer, String>> show(Window owner, SystemsObject object, Map<Integer, String> current) {
        I18n i18n = I18n.getInstance();
        Map<Integer, String> mc2 = object.getMc2Functions();

        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(i18n.t("functions.title") + " - " + object.getName());

        Label hint = new Label(i18n.t("functions.hint"));
        hint.setWrapText(true);
        hint.setMinHeight(Region.USE_PREF_SIZE);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(6);
        ColumnConstraints nrCol = new ColumnConstraints();
        nrCol.setHalignment(HPos.RIGHT);
        nrCol.setMinWidth(40);
        ColumnConstraints mc2Col = new ColumnConstraints();
        mc2Col.setPercentWidth(42);
        ColumnConstraints arrowCol = new ColumnConstraints();
        arrowCol.setHalignment(HPos.CENTER);
        ColumnConstraints itrainCol = new ColumnConstraints();
        itrainCol.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(nrCol, mc2Col, arrowCol, itrainCol);

        Label nrHeader = new Label(i18n.t("functions.colNr"));
        Label mc2Header = new Label(i18n.t("functions.colMc2"));
        Label itrainHeader = new Label(i18n.t("functions.colItrain"));
        for (Label header : new Label[]{nrHeader, mc2Header, itrainHeader}) {
            header.setStyle("-fx-font-weight: bold;");
        }
        grid.add(nrHeader, 0, 0);
        grid.add(mc2Header, 1, 0);
        grid.add(itrainHeader, 3, 0);
        Separator separator = new Separator();
        separator.setMaxWidth(Double.MAX_VALUE);
        grid.add(separator, 0, 1, 4, 1);

        TreeSet<Integer> numbers = new TreeSet<>(mc2.keySet());
        numbers.addAll(current.keySet());
        Map<Integer, TextField> fields = new LinkedHashMap<>();
        int row = 2;
        for (int nr : numbers) {
            String mc2Name = mc2.getOrDefault(nr, "");
            grid.add(new Label("F" + nr), 0, row);
            Label mc2Label = new Label(mc2Name.isEmpty() ? "-" : mc2Name);
            mc2Label.setMaxWidth(Double.MAX_VALUE);
            grid.add(mc2Label, 1, row);
            TextField field = new TextField(current.getOrDefault(nr, ""));
            field.setMaxWidth(Double.MAX_VALUE);
            fields.put(nr, field);
            Button arrow = new Button("▶");
            arrow.setTooltip(new Tooltip(i18n.t("functions.takeTooltip")));
            arrow.setDisable(mc2Name.isEmpty());
            arrow.setOnAction(e -> field.setText(mc2Name));
            grid.add(arrow, 2, row);
            grid.add(field, 3, row);
            row++;
        }
        if (numbers.isEmpty()) {
            grid.add(new Label(i18n.t("functions.none")), 0, row, 4, 1);
        }

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        grid.setPadding(new Insets(0, 12, 0, 0));
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Button takeAll = new Button(i18n.t("functions.takeAll"));
        takeAll.setDisable(mc2.isEmpty());
        takeAll.setOnAction(e -> {
            for (Map.Entry<Integer, String> entry : mc2.entrySet()) {
                TextField field = fields.get(entry.getKey());
                if (field != null && !entry.getValue().isEmpty()) {
                    field.setText(entry.getValue());
                }
            }
        });

        java.util.concurrent.atomic.AtomicReference<Map<Integer, String>> result =
                new java.util.concurrent.atomic.AtomicReference<>();
        Button okButton = new Button(i18n.t("systems.applyButton"));
        okButton.setDefaultButton(true);
        okButton.setOnAction(e -> {
            Map<Integer, String> chosen = new TreeMap<>();
            for (Map.Entry<Integer, TextField> entry : fields.entrySet()) {
                String text = entry.getValue().getText() == null ? "" : entry.getValue().getText().trim();
                chosen.put(entry.getKey(), text);
            }
            result.set(chosen);
            stage.close();
        });
        Button cancelButton = new Button(i18n.t("bidib.abortButton"));
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(e -> stage.close());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(8, takeAll, spacer, cancelButton, okButton);
        buttons.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(12, hint, scroll, buttons);
        content.setPadding(new Insets(16));
        Scene scene = new Scene(content);
        ThemeManager.apply(scene, AppSettings.getInstance().getTheme());
        stage.setScene(scene);
        stage.setMinWidth(620);
        stage.setMaxHeight(javafx.stage.Screen.getPrimary().getVisualBounds().getHeight());
        stage.setResizable(true);
        stage.showAndWait();
        return Optional.ofNullable(result.get());
    }

    /**
     * Schreibt das Ergebnis in den eigenen Eintrag der Zeile: Grundlage ist
     * eine Kopie der Liste von {@link #baseFunctions} (so bleiben weitere
     * Angaben einer Funktion wie Beschreibung erhalten), darin werden die
     * Namen gesetzt bzw. Funktionen ergaenzt. Leerer Name = Attribut weg
     * (eine Funktion ohne weitere Angaben faellt ganz weg).
     */
    static void apply(SystemsObject object, Map<Integer, String> names) {
        XmlNode loco = object.getXml();
        if (loco == null) {
            return;
        }
        XmlNode base = baseFunctions(object);
        XmlNode functions = base == null ? new XmlNode("functions") : base.deepCopy();
        for (Map.Entry<Integer, String> entry : names.entrySet()) {
            XmlNode function = null;
            for (XmlNode candidate : functions.getChildren()) {
                if ("function".equals(candidate.getTagName())
                        && String.valueOf(entry.getKey()).equals(candidate.getAttribute("nr"))) {
                    function = candidate;
                }
            }
            if (entry.getValue().isEmpty()) {
                if (function != null) {
                    function.removeAttribute("name");
                    if (function.getAttributes().size() <= 1 && function.getChildren().isEmpty()) {
                        functions.getChildren().remove(function);
                    }
                }
                continue;
            }
            if (function == null) {
                function = new XmlNode("function");
                function.setAttribute("nr", String.valueOf(entry.getKey()));
                // nach Nummer einsortieren
                int index = functions.getChildren().size();
                for (int i = 0; i < functions.getChildren().size(); i++) {
                    try {
                        int nr = Integer.parseInt(functions.getChildren().get(i).getAttribute("nr"));
                        if (nr > entry.getKey()) {
                            index = i;
                            break;
                        }
                    } catch (RuntimeException ignored) {
                        // ohne Nummer - weiter
                    }
                }
                functions.getChildren().add(index, function);
            }
            function.setAttribute("name", entry.getValue());
        }
        functions.setAttribute("count", String.valueOf(functions.getChildren().size()));

        XmlNode existing = loco.findChild("functions");
        if (existing != null) {
            loco.getChildren().set(loco.getChildren().indexOf(existing), functions);
        } else {
            XmlNode fuel = loco.findChild("fuel");
            loco.getChildren().add(fuel != null ? loco.getChildren().indexOf(fuel) : loco.getChildren().size(), functions);
        }
        object.setFunctionsEdited(true);
    }
}
