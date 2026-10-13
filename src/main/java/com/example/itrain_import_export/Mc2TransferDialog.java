package com.example.itrain_import_export;

import javafx.collections.FXCollections;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Fenster "Lokomotiven zur mc2 uebertragen" (seit 13.10.2026): links die
 * Lokomotiven der geladenen iTrain-Datei, rechts das eingeschraenkte Profil,
 * das die mc2 in loco.ini fuehrt - Adresse, Name, Format (DCC/14, /28, /126)
 * und je Funktion ein Symbol aus ihrer festen Liste.
 * <p>
 * Je Zeile: Uebertragen bzw. Ueberschreiben oder Ueberspringen. Ist die
 * Adresse auf der mc2 schon mit einer benannten Lok belegt, ist
 * "Ueberspringen" vorgewaehlt (Andre, 13.10.2026). Abschnitte ohne Namen
 * (nur einmal am Regler angewaehlte Adressen) gelten als frei. Loks mit
 * anderem Protokoll als DCC oder ohne gueltige Adresse sind nicht
 * uebertragbar und werden nur angezeigt.
 * <p>
 * Funktionen: Stimmt ein iTrain-Funktionsname mit einem Namen der
 * mc2-Symbolliste ueberein (in einer der vier Sprachen, Gross-/Klein-
 * schreibung egal), ist dieses Symbol vorgewaehlt; alle anderen sind im
 * Fenster "Funktionen" der Zeile waehlbar ("---" = kein Symbol).
 */
final class Mc2TransferDialog {

    private Mc2TransferDialog() {
    }

    private static final class Row {
        final String itrainName;
        final int address;
        final String fmt;
        final String reason;
        final String decoderLabel;
        final String mc2Name;
        final boolean occupied;
        final Map<Integer, String> itrainFunctions;
        final Map<Integer, Integer> icons = new TreeMap<>();
        final TextField nameField = new TextField();
        final ComboBox<String> actionBox = new ComboBox<>();

        Row(String itrainName, int address, String fmt, String reason, String decoderLabel, String mc2Name,
                boolean occupied, Map<Integer, String> itrainFunctions) {
            this.itrainName = itrainName;
            this.decoderLabel = decoderLabel;
            this.address = address;
            this.fmt = fmt;
            this.reason = reason;
            this.mc2Name = mc2Name;
            this.occupied = occupied;
            this.itrainFunctions = itrainFunctions;
        }

        boolean transferable() {
            return reason == null;
        }
    }

    /**
     * Zeigt das Fenster. Liefert die zu schreibenden Profile, oder leer bei
     * Abbruch bzw. wenn keine Zeile uebertragen werden soll.
     *
     * @param itrainRoot Wurzel der geladenen iTrain-Datei
     * @param mc2Locos   vorhandene Loks der mc2 (Adresse -&gt; Daten aus cmd=info)
     * @param texteJs    Inhalt von texte.js (Symbolliste), darf leer sein
     */
    static Optional<List<Mc2LocoWriter.Profile>> show(Window owner, XmlNode itrainRoot,
            Map<Integer, Mc2Http.LocoInfo> mc2Locos, String texteJs) {
        Map<Integer, String> names = new HashMap<>();
        mc2Locos.forEach((address, info) -> names.put(address, info.name() == null ? "" : info.name()));
        return show(owner, itrainRoot, names, texteJs == null ? "" : texteJs, "mc2", Mc2LocoWriter::fmtFor);
    }

    /**
     * Allgemeine Fassung fuer jede Zentrale (seit 13.10.2026 auch ECoS).
     *
     * @param existingNames Adresse -&gt; Name der Loks, die schon in der Zentrale stehen
     * @param texteJs       Symbolliste der mc2; null = Zentrale ohne Funktionssymbole (kein Knopf "Funktionen")
     * @param centralName   "mc2" bzw. "ECoS" fuer die Beschriftungen
     * @param formatFor     Format bzw. Protokoll der Zentrale aus dem iTrain-Decoder, null = nicht uebertragbar
     */
    static Optional<List<Mc2LocoWriter.Profile>> show(Window owner, XmlNode itrainRoot,
            Map<Integer, String> existingNames, String texteJs, String centralName,
            java.util.function.Function<XmlNode, String> formatFor) {
        I18n i18n = I18n.getInstance();
        boolean withIcons = texteJs != null;
        List<List<String>> iconBlocks = Mc2LocoReader.parseIconBlocks(texteJs == null ? "" : texteJs);
        List<String> iconNames = Mc2LocoReader.parseIconNames(texteJs == null ? "" : texteJs, i18n.getCurrentLanguage());

        List<Row> rows = new ArrayList<>();
        XmlNode controlItems = itrainRoot == null ? null : itrainRoot.findChild("control-items");
        XmlNode locomotives = controlItems == null ? null : controlItems.findChild("locomotives");
        if (locomotives != null) {
            for (XmlNode loco : locomotives.getChildren()) {
                if (!"locomotive".equals(loco.getTagName())) {
                    continue;
                }
                XmlNode iface = loco.findChild("interface");
                int address = -1;
                try {
                    address = Integer.parseInt(iface == null ? "" : iface.getAttribute("address").trim());
                } catch (RuntimeException ignored) {
                    // keine Adresse
                }
                String fmt = formatFor.apply(loco.findChild("decoder"));
                String decoderLabel = VehicleFieldsDialog.decoderTypeLabel(loco.findChild("decoder"));
                decoderLabel = decoderLabel == null ? "?" : decoderLabel;
                String reason = null;
                if (address < 1 || address > 10239) {
                    reason = i18n.t("mc2.writeNoAddress");
                } else if (fmt == null) {
                    reason = i18n.t("central.writeUnsupported", decoderLabel, centralName);
                }
                String existingName = address > 0 ? existingNames.get(address) : null;
                String mc2Name = existingName == null ? "" : existingName;
                // ECoS: jede vorhandene Lok zaehlt als belegt (es gibt dort
                // keine unbenannten Adress-Eintraege wie bei der mc2).
                boolean occupied = withIcons ? !mc2Name.isBlank() : existingName != null;
                Row row = new Row(loco.getName(), address, fmt, reason, decoderLabel, mc2Name, occupied,
                        FunctionMappingDialog.namesOf(loco.findChild("functions")));
                for (Map.Entry<Integer, String> function : row.itrainFunctions.entrySet()) {
                    int icon = iconFor(function.getValue(), iconBlocks);
                    if (icon > 0) {
                        row.icons.put(function.getKey(), icon);
                    }
                }
                rows.add(row);
            }
        }

        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(i18n.t("central.writeTitle", centralName));

        Label hint = new Label(i18n.t(withIcons ? "central.writeHintMc2" : "central.writeHint", centralName));
        hint.setWrapText(true);
        hint.setMinHeight(Region.USE_PREF_SIZE);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(6);
        String[] headers = {i18n.t("mc2.writeColItrain"), i18n.t("systems.fieldAddress"),
                i18n.t("vehicle.fieldDecoderType"), "", i18n.t("central.colName", centralName),
                i18n.t("mc2.writeColFormat"), i18n.t("central.colStatus", centralName),
                i18n.t("mc2.writeColAction"), ""};
        for (int c = 0; c < headers.length; c++) {
            Label header = new Label(headers[c]);
            header.setStyle("-fx-font-weight: bold;");
            grid.add(header, c, 0);
        }
        Separator separator = new Separator();
        separator.setMaxWidth(Double.MAX_VALUE);
        grid.add(separator, 0, 1, headers.length, 1);

        String transferText = i18n.t("mc2.writeActionTransfer");
        String overwriteText = i18n.t("mc2.writeActionOverwrite");
        String skipText = i18n.t("mc2.writeActionSkip");
        int r = 2;
        for (Row row : rows) {
            grid.add(new Label(row.itrainName), 0, r);
            Label addressLabel = new Label(row.address > 0 ? String.valueOf(row.address) : "-");
            grid.add(addressLabel, 1, r);
            GridPane.setHalignment(addressLabel, HPos.RIGHT);
            grid.add(new Label(row.decoderLabel), 2, r);
            Label arrow = new Label("▶");
            grid.add(arrow, 3, r);
            if (!row.transferable()) {
                Label reason = new Label(row.reason);
                reason.setStyle("-fx-opacity: 0.7;");
                grid.add(reason, 4, r, 5, 1);
                r++;
                continue;
            }
            row.nameField.setText(row.itrainName);
            row.nameField.setPrefColumnCount(16);
            grid.add(row.nameField, 4, r);
            grid.add(new Label(row.fmt), 5, r);
            Label status = new Label(row.occupied ? i18n.t("mc2.writeStatusOccupied", row.mc2Name)
                    : i18n.t("mc2.writeStatusFree"));
            status.setStyle(row.occupied ? "-fx-text-fill: #e65100;" : "-fx-text-fill: #2e7d32;");
            grid.add(status, 6, r);
            row.actionBox.setItems(FXCollections.observableArrayList(
                    row.occupied ? overwriteText : transferText, skipText));
            row.actionBox.setValue(row.occupied ? skipText : transferText);
            grid.add(row.actionBox, 7, r);
            if (!withIcons) {
                r++;
                continue;
            }
            Button functionsButton = new Button(i18n.t("loco.functionsButton") + " (" + row.icons.size() + ")");
            functionsButton.setDisable(row.itrainFunctions.isEmpty());
            functionsButton.setTooltip(new Tooltip(i18n.t("mc2.writeFunctionsTooltip")));
            functionsButton.setOnAction(e -> {
                editIcons(stage, row, iconNames);
                functionsButton.setText(i18n.t("loco.functionsButton") + " (" + row.icons.size() + ")");
            });
            grid.add(functionsButton, 8, r);
            r++;
        }
        if (rows.isEmpty()) {
            grid.add(new Label(i18n.t("mc2.writeNoLocos")), 0, r, headers.length, 1);
        }

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        grid.setPadding(new Insets(0, 12, 0, 0));
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Button allFree = new Button(i18n.t("mc2.writeAllFree"));
        allFree.setOnAction(e -> {
            for (Row row : rows) {
                if (row.transferable() && !row.occupied) {
                    row.actionBox.setValue(transferText);
                }
            }
        });
        Button allSkip = new Button(i18n.t("mc2.writeAllSkip"));
        allSkip.setOnAction(e -> {
            for (Row row : rows) {
                if (row.transferable()) {
                    row.actionBox.setValue(skipText);
                }
            }
        });

        List<Mc2LocoWriter.Profile> result = new ArrayList<>();
        boolean[] accepted = {false};
        Button writeButton = new Button(i18n.t("central.writeButton", centralName));
        writeButton.setDefaultButton(true);
        writeButton.setOnAction(e -> {
            result.clear();
            for (Row row : rows) {
                if (!row.transferable() || skipText.equals(row.actionBox.getValue())) {
                    continue;
                }
                String name = Mc2LocoWriter.sanitize(row.nameField.getText());
                result.add(new Mc2LocoWriter.Profile(row.address, name.isEmpty() ? row.itrainName : name,
                        row.fmt, new TreeMap<>(row.icons)));
            }
            accepted[0] = true;
            stage.close();
        });
        Button cancelButton = new Button(i18n.t("bidib.abortButton"));
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(e -> stage.close());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(8, allFree, allSkip, spacer, cancelButton, writeButton);
        buttons.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(12, hint, scroll, buttons);
        content.setPadding(new Insets(16));
        Scene scene = new Scene(content);
        ThemeManager.apply(scene, AppSettings.getInstance().getTheme());
        stage.setScene(scene);
        stage.setMinWidth(980);
        stage.setMaxHeight(javafx.stage.Screen.getPrimary().getVisualBounds().getHeight());
        stage.setResizable(true);
        stage.showAndWait();
        return accepted[0] && !result.isEmpty() ? Optional.of(result) : Optional.empty();
    }

    /** Symbolnummer zu einem Funktionsnamen (in einer der Sprachen), 0 = kein Treffer. */
    static int iconFor(String functionName, List<List<String>> iconBlocks) {
        if (functionName == null || functionName.isBlank()) {
            return 0;
        }
        String wanted = functionName.trim().toLowerCase(Locale.ROOT);
        for (List<String> block : iconBlocks) {
            for (int i = 1; i < block.size(); i++) {
                if (block.get(i).trim().toLowerCase(Locale.ROOT).equals(wanted)) {
                    return i;
                }
            }
        }
        return 0;
    }

    /** Unterfenster "Funktionen": je iTrain-Funktion ein Symbol aus der mc2-Liste waehlen. */
    private static void editIcons(Window owner, Row row, List<String> iconNames) {
        I18n i18n = I18n.getInstance();
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(i18n.t("functions.title") + " - " + row.itrainName);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(6);
        String[] headers = {i18n.t("functions.colNr"), i18n.t("functions.colItrain"), "", i18n.t("functions.colMc2")};
        for (int c = 0; c < headers.length; c++) {
            Label header = new Label(headers[c]);
            header.setStyle("-fx-font-weight: bold;");
            grid.add(header, c, 0);
        }
        List<String> choices = new ArrayList<>();
        for (int i = 0; i < iconNames.size(); i++) {
            choices.add(i == 0 ? "---" : i + "  " + iconNames.get(i));
        }
        if (choices.isEmpty()) {
            choices.add("---");
        }
        Map<Integer, ComboBox<String>> boxes = new TreeMap<>();
        int r = 1;
        for (Map.Entry<Integer, String> function : row.itrainFunctions.entrySet()) {
            grid.add(new Label("F" + function.getKey()), 0, r);
            grid.add(new Label(function.getValue().isEmpty() ? "-" : function.getValue()), 1, r);
            grid.add(new Label("▶"), 2, r);
            ComboBox<String> box = new ComboBox<>(FXCollections.observableArrayList(choices));
            box.setVisibleRowCount(15);
            int icon = row.icons.getOrDefault(function.getKey(), 0);
            box.setValue(icon > 0 && icon < choices.size() ? choices.get(icon) : choices.get(0));
            grid.add(box, 3, r);
            boxes.put(function.getKey(), box);
            r++;
        }
        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Button ok = new Button(i18n.t("systems.applyButton"));
        ok.setDefaultButton(true);
        ok.setOnAction(e -> {
            row.icons.clear();
            for (Map.Entry<Integer, ComboBox<String>> entry : boxes.entrySet()) {
                int index = choices.indexOf(entry.getValue().getValue());
                if (index > 0) {
                    row.icons.put(entry.getKey(), index);
                }
            }
            stage.close();
        });
        Button cancel = new Button(i18n.t("bidib.abortButton"));
        cancel.setCancelButton(true);
        cancel.setOnAction(e -> stage.close());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(8, spacer, cancel, ok);
        VBox content = new VBox(12, scroll, buttons);
        content.setPadding(new Insets(16));
        Scene scene = new Scene(content);
        ThemeManager.apply(scene, AppSettings.getInstance().getTheme());
        stage.setScene(scene);
        stage.setMinWidth(560);
        stage.setMaxHeight(javafx.stage.Screen.getPrimary().getVisualBounds().getHeight());
        stage.showAndWait();
    }
}
