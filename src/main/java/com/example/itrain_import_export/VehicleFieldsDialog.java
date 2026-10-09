package com.example.itrain_import_export;

import javafx.collections.FXCollections;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
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

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Bearbeitungsfenster fuer eine Lokomotive oder einen Wagen im Hauptfenster
 * bzw. im Decoder-Fenster (beide teilen sich {@link CategoryEditor}) - dem
 * Bearbeitungsfenster des Systeme-Fensters ({@link SystemsObjectDialog})
 * nachempfunden (gleicher Aufbau: Felder untereinander, unten rechts
 * "Uebernehmen", links daneben "Abbruch"), aber fuer ein beliebiges, aus
 * einer echten iTrain-Datei geladenes Fahrzeug statt nur fuer frisch aus
 * BiDiB/ECoS erzeugte Eintraege.
 * <p>
 * Gezeigt werden nur die haeufig gebrauchten Felder (Name, Beschreibung,
 * Typ, Laenge, Decoder-Typ/UID/Adresse, Schnittstelle, Knoten); alle
 * uebrigen Angaben eines Fahrzeugs (Fahrstufentabelle im Detail,
 * Verzoegerung, Bild, Funktionen, ...) bleiben, wie im Hauptfenster, ueber
 * den ohnehin vorhandenen "Daten aendern"-Bereich erreichbar (Einstellungen
 * -&gt; Ansicht -&gt; "Daten aendern").
 * <p>
 * "Typ" ist eine EDITIERBARE Auswahl: die angebotenen Eintraege sind an
 * echten iTrain-Dateien abgelesen (Lokomotiven: {@code steam}/{@code diesel}
 * - "electric" kommt in keiner der drei geprueften Dateien vor und ist
 * deshalb NICHT vorbelegt, siehe STATUS.md; Wagen: {@code coach}
 * (Personenwagen) sowie die deutschen Gueterwagen-Gattungsbuchstaben
 * {@code e/g/i/t/u/z}, ebenfalls an echten Wagen mit passenden Namen wie
 * "Kohle"/"Holz" (E) oder "Koenigsbacher" (I, Kuehlwagen) abgelesen). Ein
 * bereits vorhandener anderer Wert bleibt beim Oeffnen unangetastet stehen
 * und wird beim Uebernehmen unveraendert zurueckgeschrieben, wenn er nicht
 * bearbeitet wird.
 * <p>
 * "Decoder-Typ" ist dagegen bewusst eine GESCHLOSSENE Auswahl mit einem
 * ersten Eintrag "unveraendert" und denselben Typen wie in iTrain (siehe
 * {@link #DECODER_VARIANTS}); ein Wechsel schreibt die Decoder-Attribute
 * neu (siehe {@link #applyDecoderProtocol}) und darf deshalb nur auf
 * ausdruecklichen Wunsch passieren. Unbekannte Kombinationen bleiben bei
 * "unveraendert" und werden nie angetastet.
 * <p>
 * Das Attribut {@code uid} am {@code <decoder>}-Element (Feld "UID") ist in
 * keiner der geprueften echten iTrain-Dateien belegt - vermutlich schreibt
 * iTrain es nur, wenn eine per RailCom erkannte Decoder-Kennung vorliegt.
 * Das Feld liest/schreibt es dennoch (der Nutzer hat es ausdruecklich als
 * Feld genannt), einfach leer, wenn nicht vorhanden - siehe STATUS.md.
 * <p>
 * "Knoten" zeigt, anders als im Systeme-Fenster, keinen aufgeloesten
 * BiDiB-Geraetenamen (der ist offline, ohne laufende Verbindung, nicht
 * bekannt) - nur die rohe {@code <id type="module">}-Kennung, falls
 * vorhanden; ohne diese Kennung (ECoS/mc2/Wizard-Herkunft oder von Hand
 * angelegt) entfaellt die Zeile ganz.
 */
public final class VehicleFieldsDialog {

    /**
     * Ein Loktyp wie in iTrain: XML-Wert des Attributs {@code type} ("" =
     * Attribut fehlt = "Sonstiges"), Uebersetzungsschluessel, Kennbuchstabe
     * und Farbe des Symbols (wie in der Auswahl von iTrain).
     * Alle Werte an von iTrain gespeicherten Dateien abgelesen
     * (electric/hydrogen/battery aus Neue_N_Anlage.tcdz).
     */
    private record LocoType(String xml, String key, String letter, String color) {
    }

    private static final List<LocoType> LOCO_TYPES = List.of(
            new LocoType("", "vehicle.locoType.other", "-", "#808080"),
            new LocoType("steam", "vehicle.locoType.steam", "S", "-fx-text-background-color"),
            new LocoType("diesel", "vehicle.locoType.diesel", "D", "#c62828"),
            new LocoType("electric", "vehicle.locoType.electric", "E", "#2e7d32"),
            new LocoType("hydrogen", "vehicle.locoType.hydrogen", "H", "#1565c0"),
            new LocoType("battery", "vehicle.locoType.battery", "B", "#6a1b9a"));

    /**
     * Wagen-Typen: {@code coach} (Personenwagen) sowie die deutschen
     * Gueterwagen-Gattungsbuchstaben, an echten Dateien abgelesen.
     */
    private static final List<String> WAGON_TYPES = List.of("coach", "e", "g", "i", "t", "u", "z");

    /**
     * Ein Decoder-Typ wie in der Auswahl von iTrain: Anzeigename und die
     * genauen Attribute des {@code <decoder>}-Elements. Alle Werte an einer
     * von iTrain gespeicherten Datei abgelesen, in der je eine Lok mit jedem
     * Typ angelegt war (N-Anlage5.tcdz), "Motorola 27a (C90X)" aus
     * Neue_N_Anlage.tcdz.
     */
    private record DecoderVariant(String label, String protocol, int steps, String... flags) {
        boolean matches(XmlNode decoder) {
            if (!protocol.equals(nz(decoder.getAttribute("protocol")).toLowerCase(Locale.ROOT))
                    || steps != parseIntOrDefault(decoder.getAttribute("steps"), -1)) {
                return false;
            }
            for (String flag : FLAG_NAMES) {
                String expected = null;
                for (int i = 0; i < flags.length; i += 2) {
                    if (flags[i].equals(flag)) {
                        expected = flags[i + 1];
                    }
                }
                String actual = decoder.getAttribute(flag);
                if (expected == null ? actual != null && !"false".equals(actual) : !expected.equals(actual)) {
                    return false;
                }
            }
            return true;
        }
    }

    /** Attribute, die die Varianten unterscheiden. */
    private static final List<String> FLAG_NAMES = List.of("extended", "old", "type", "susi", "dynamic");

    private static final List<DecoderVariant> DECODER_VARIANTS = List.of(
            new DecoderVariant("DCC 14", "dcc", 14, "old", "true"),
            new DecoderVariant("DCC 27", "dcc", 27, "old", "true"),
            new DecoderVariant("DCC 28", "dcc", 28),
            new DecoderVariant("DCC 126", "dcc", 126),
            new DecoderVariant("DCC 126 Extended", "dcc", 126, "extended", "true"),
            new DecoderVariant("Motorola I (old)", "mot", 14, "old", "true"),
            new DecoderVariant("Motorola II (Delta/C80)", "mot", 14, "type", "c80"),
            new DecoderVariant("Motorola 14 (C90)", "mot", 14),
            new DecoderVariant("Motorola 27a (C90X)", "mot", 27, "old", "true"),
            new DecoderVariant("Motorola 27b", "mot", 27),
            new DecoderVariant("Motorola 28", "mot", 28),
            new DecoderVariant("MFX", "mfx", 126),
            new DecoderVariant("SX1", "sx1", 31),
            new DecoderVariant("Selectrix (SUSI)", "sx1", 31, "susi", "true"),
            new DecoderVariant("Selectrix AD", "sx1", 31, "dynamic", "true"),
            new DecoderVariant("Selectrix AD (SUSI)", "sx1", 31, "susi", "true", "dynamic", "true"),
            new DecoderVariant("SX2", "sx2", 127),
            new DecoderVariant("Selectrix 2 (31)", "sx2", 31),
            new DecoderVariant("FMZ", "fmz", 15),
            new DecoderVariant("CTC", "ctc", 1023),
            new DecoderVariant("Multi", "multi", 126),
            new DecoderVariant("Analog", "analog", 63, "kickstart", "0"));

    private VehicleFieldsDialog() {
    }

    /**
     * Zeigt das Fenster fuer ein Fahrzeug, das bereits Teil des Dokuments ist
     * (Doppelklick / Rechtsklick -&gt; Bearbeiten) oder es gerade erst werden
     * soll ("+", noch nicht in die Kategorie eingehaengt).
     *
     * @param vehicle        das zu bearbeitende {@code <locomotive>}- oder
     *                       {@code <wagon>}-Element
     * @param isWagon        steuert die Typ-Auswahl (Wagen- statt Lok-Typen)
     * @param interfaceNames Namen der im Dokument vorhandenen Schnittstellen,
     *                       fuer die Auswahlbox "Schnittstelle"
     * @param requireName    true, wenn ein leerer Name beim Uebernehmen
     *                       abgelehnt werden soll (neuer Eintrag)
     * @param beforeApply    wird genau einmal aufgerufen, nachdem die
     *                       Namensprüfung bestanden hat, aber NOCH VOR der
     *                       ersten Änderung an {@code vehicle} - für den
     *                       Undo-Schnappschuss des Aufrufers
     *                       ({@code CategoryEditor.beforeChange}); bleibt bei
     *                       Abbruch unaufgerufen. Darf {@code null} sein.
     * @return true, wenn mit "Uebernehmen" geschlossen wurde - die
     *         Attribute/Kinder von {@code vehicle} sind dann bereits
     *         angepasst; der Aufrufer muss nur noch selbst
     *         {@code onModified} auslösen und den Eintrag ggf. in die
     *         Kategorie einhaengen.
     */
    public static boolean show(Window owner, XmlNode vehicle, boolean isWagon, List<String> interfaceNames,
            boolean requireName, Runnable beforeApply) {
        I18n i18n = I18n.getInstance();
        AppSettings settings = AppSettings.getInstance();

        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        String titleName = vehicle.getName().isEmpty() ? i18n.t("editor.newEntryDefaultName") : vehicle.getName();
        stage.setTitle(i18n.t("systems.editRow") + " - " + titleName);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        ColumnConstraints labels = new ColumnConstraints();
        labels.setHalignment(HPos.RIGHT);
        labels.setMinWidth(110);
        ColumnConstraints fields = new ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);
        fields.setFillWidth(true);
        grid.getColumnConstraints().addAll(labels, fields);
        int[] row = {0};

        TextField nameField = new TextField(vehicle.getName());
        XmlNode descriptionNode = vehicle.findChild("description");
        TextField descriptionField = new TextField(descriptionNode != null && descriptionNode.getTextContent() != null
                ? descriptionNode.getTextContent() : "");
        addRow(grid, row, i18n.t("systems.fieldName"), nameField);
        addRow(grid, row, i18n.t("systems.fieldDescription"), descriptionField);

        // Wagen: Typ als editierbare Auswahl (Gattungsbuchstaben).
        // Lokomotiven: "Loktyp" als feste Auswahl wie in iTrain, mit
        // farbigem Kennbuchstaben; ein unbekannter vorhandener Wert wird als
        // eigener Eintrag angezeigt und unveraendert zurueckgeschrieben.
        ComboBox<String> typeBox;
        String currentType = nz(vehicle.getAttribute("type"));
        if (isWagon) {
            typeBox = new ComboBox<>(FXCollections.observableArrayList(WAGON_TYPES));
            typeBox.setEditable(true);
            typeBox.setValue(currentType);
            typeBox.setTooltip(new Tooltip(i18n.t("vehicle.wagonTypeHint")));
        } else {
            List<String> values = new java.util.ArrayList<>();
            for (LocoType type : LOCO_TYPES) {
                values.add(type.xml());
            }
            if (!values.contains(currentType)) {
                values.add(currentType);
            }
            typeBox = new ComboBox<>(FXCollections.observableArrayList(values));
            typeBox.setCellFactory(list -> new LocoTypeCell(i18n));
            typeBox.setButtonCell(new LocoTypeCell(i18n));
            typeBox.setValue(currentType);
        }
        typeBox.setMaxWidth(Double.MAX_VALUE);
        addRow(grid, row, i18n.t(isWagon ? "systems.fieldType" : "vehicle.fieldLocoType"), typeBox);

        // Laenge - reiner Textwert plus die vorhandene (oder neue) Einheit,
        // wie beim Rueckmelder-Laengenfeld im Systeme-Fenster.
        XmlNode lengthNode = vehicle.findChild("length");
        String lengthUnit = lengthNode != null && lengthNode.getAttribute("unit") != null
                ? lengthNode.getAttribute("unit") : "cm";
        TextField lengthField = new TextField(
                lengthNode != null && lengthNode.getTextContent() != null ? lengthNode.getTextContent() : "");
        lengthField.setPrefColumnCount(8);
        HBox lengthRow = new HBox(6, lengthField, new Label(lengthUnit));
        lengthRow.setAlignment(Pos.CENTER_LEFT);
        addRow(grid, row, i18n.t("systems.fieldLength"), lengthRow);

        addSeparator(grid, row);

        // Decoder-Typ: "unveraendert" plus alle Typen wie in iTrain;
        // vorgewaehlt ist der erkannte aktuelle Typ, sonst "unveraendert".
        XmlNode currentDecoder = vehicle.findChild("decoder");
        String detectedCode = detectProtocolCode(currentDecoder);
        java.util.List<SystemsObject.Choice> decoderChoices = new java.util.ArrayList<>();
        decoderChoices.add(new SystemsObject.Choice("", i18n.t("vehicle.decoderKeep")));
        for (DecoderVariant variant : DECODER_VARIANTS) {
            decoderChoices.add(new SystemsObject.Choice(variant.label(), variant.label()));
        }
        ComboBox<SystemsObject.Choice> decoderBox = choiceBox(decoderChoices, detectedCode != null ? detectedCode : "");
        addRow(grid, row, i18n.t("vehicle.fieldDecoderType"), decoderBox);

        // Decoder-Vorlage: "unveraendert" oder eine der Vorlagen aus dem
        // eingestellten Ordner - beim Uebernehmen wird ihre Konfiguration
        // (CV-Liste) eingesetzt, siehe DecoderTemplateSupport.apply. Darunter
        // steht, welche Vorlage das Fahrzeug derzeit vermutlich hat.
        List<DecoderTemplate> templates = new java.util.ArrayList<>();
        for (DecoderTemplateSupport.Known known : DecoderTemplateSupport.known()) {
            templates.add(known.template());
        }
        ComboBox<Object> templateBox = new ComboBox<>();
        String keepLabel = i18n.t("vehicle.decoderKeep");
        templateBox.getItems().add(keepLabel);
        templateBox.getItems().addAll(templates);
        templateBox.setValue(keepLabel);
        templateBox.setMaxWidth(Double.MAX_VALUE);
        templateBox.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Object value) {
                return value instanceof DecoderTemplate t ? t.getName() : String.valueOf(value);
            }

            @Override
            public Object fromString(String text) {
                return text;
            }
        });
        addRow(grid, row, i18n.t("vehicle.fieldTemplate"), templateBox);
        String currentTemplateText = DecoderTemplateSupport.templateColumnText(vehicle, i18n);
        Label currentTemplate = new Label(currentTemplateText.isEmpty()
                ? i18n.t("vehicle.templateNone") : i18n.t("vehicle.templateCurrent", currentTemplateText));
        currentTemplate.setStyle("-fx-opacity: 0.75;");
        addRow(grid, row, "", currentTemplate);

        TextField uidField = new TextField(currentDecoder != null ? nz(currentDecoder.getAttribute("uid")) : "");
        uidField.setPrefColumnCount(12);
        addRow(grid, row, i18n.t("vehicle.fieldUid"), uidField);

        XmlNode currentInterface = vehicle.findChild("interface");
        TextField addressField = new TextField(currentInterface != null ? nz(currentInterface.getAttribute("address")) : "");
        addressField.setPrefColumnCount(8);
        addRow(grid, row, i18n.t("systems.fieldAddress"), addressField);

        ComboBox<String> interfaceBox = new ComboBox<>(FXCollections.observableArrayList(interfaceNames));
        interfaceBox.setEditable(true);
        interfaceBox.setMaxWidth(Double.MAX_VALUE);
        interfaceBox.setValue(currentInterface != null ? nz(currentInterface.getAttribute("name")) : "");
        addRow(grid, row, i18n.t("systems.colInterface"), interfaceBox);

        XmlNode idNode = vehicle.findChild("id");
        String nodeId = idNode != null && idNode.getTextContent() != null ? idNode.getTextContent() : "";
        if (!nodeId.isBlank()) {
            addRow(grid, row, i18n.t("systems.fieldNode"), readOnly(nodeId));
        }

        boolean[] accepted = {false};
        Button okButton = new Button(i18n.t("systems.applyButton"));
        okButton.setDefaultButton(true);
        Button cancelButton = new Button(i18n.t("bidib.abortButton"));
        cancelButton.setCancelButton(true);
        okButton.setOnAction(e -> {
            String newName = nameField.getText() == null ? "" : nameField.getText().trim();
            if (requireName && newName.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.WARNING, i18n.t("editor.editNameEmpty"));
                alert.setHeaderText(null);
                alert.showAndWait();
                return;
            }
            if (beforeApply != null) {
                beforeApply.run();
            }
            setAttrOrRemove(vehicle, "name", newName);
            applyDescription(vehicle, descriptionField.getText() == null ? "" : descriptionField.getText().trim());
            setAttrOrRemove(vehicle, "type", typeBox.getValue() == null ? "" : typeBox.getValue().trim());
            applyLength(vehicle, lengthField.getText() == null ? "" : lengthField.getText().trim(), lengthUnit);

            String chosenProtocol = decoderBox.getValue() == null ? "" : decoderBox.getValue().value();
            if (!chosenProtocol.isEmpty()) {
                applyDecoderProtocol(vehicle, chosenProtocol);
            }
            if (templateBox.getValue() instanceof DecoderTemplate chosenTemplate) {
                DecoderTemplateSupport.Result result = DecoderTemplateSupport.apply(vehicle, chosenTemplate, "dcc");
                Alert info;
                if (result == DecoderTemplateSupport.Result.APPLIED) {
                    XmlNode configuration = vehicle.findChild(DecoderTemplate.CONFIGURATION_TAG);
                    int count = configuration == null ? 0 : configuration.getChildren().size();
                    info = new Alert(Alert.AlertType.WARNING,
                            i18n.t("editor.decoderImportSuccess", count, vehicle.getName())
                                    + "\n\n" + i18n.t("editor.decoderAddressWarning"));
                } else if (result == DecoderTemplateSupport.Result.PROTOCOL_BLOCKED) {
                    info = new Alert(Alert.AlertType.ERROR, i18n.t("editor.decoderProtocolBlocked",
                            vehicle.getName(), DecoderProtocol.displayName(DecoderProtocol.read(vehicle))));
                } else {
                    info = new Alert(Alert.AlertType.ERROR, i18n.t("editor.importErrorTitle"));
                }
                info.initOwner(stage);
                info.setHeaderText(null);
                info.setTitle(i18n.t("vehicle.fieldTemplate"));
                info.showAndWait();
            }
            applyUid(vehicle, uidField.getText() == null ? "" : uidField.getText().trim());
            applyInterface(vehicle,
                    interfaceBox.getValue() == null ? "" : interfaceBox.getValue().trim(),
                    addressField.getText() == null ? "" : addressField.getText().trim());

            accepted[0] = true;
            stage.close();
        });
        cancelButton.setOnAction(e -> stage.close());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(8, spacer, cancelButton, okButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox content = new VBox(14, grid, buttons);
        content.setPadding(new Insets(16));
        javafx.scene.Scene scene = new javafx.scene.Scene(content);
        ThemeManager.apply(scene, settings.getTheme());
        stage.setScene(scene);
        stage.setMinWidth(560);
        stage.setResizable(true);
        nameField.requestFocus();
        stage.showAndWait();
        return accepted[0];
    }

    // ------------------------------------------------------------------
    // Anwenden der Felder auf das XmlNode
    // ------------------------------------------------------------------

    private static void applyDescription(XmlNode vehicle, String text) {
        XmlNode description = vehicle.findChild("description");
        if (text.isEmpty()) {
            if (description != null) {
                vehicle.getChildren().remove(description);
            }
            return;
        }
        if (description == null) {
            description = new XmlNode("description");
            vehicle.getChildren().add(0, description);
        }
        description.setTextContent(text);
    }

    private static void applyLength(XmlNode vehicle, String text, String unit) {
        XmlNode length = vehicle.findChild("length");
        if (text.isEmpty()) {
            if (length != null) {
                vehicle.getChildren().remove(length);
            }
            return;
        }
        if (length == null) {
            length = new XmlNode("length");
            length.setAttribute("unit", unit);
            // Reihenfolge an echten Dateien abgelesen: length steht direkt
            // nach configuration (falls vorhanden) bzw. nach decoder.
            XmlNode anchor = vehicle.findChild("configuration");
            if (anchor == null) {
                anchor = vehicle.findChild("decoder");
            }
            int idx = anchor != null ? vehicle.getChildren().indexOf(anchor) + 1 : vehicle.getChildren().size();
            vehicle.getChildren().add(idx, length);
        }
        length.setTextContent(text);
    }

    private static void applyUid(XmlNode vehicle, String uid) {
        XmlNode decoder = vehicle.findChild("decoder");
        if (decoder == null) {
            // Kein Decoder (weder vorhanden noch gerade angelegt) - eine UID
            // ohne zugehoeriges decoder-Element waere nicht unterzubringen;
            // dafuer muss zuerst ein Decoder-Typ gewaehlt werden.
            return;
        }
        if (uid.isEmpty()) {
            decoder.removeAttribute("uid");
        } else {
            decoder.setAttribute("uid", uid);
        }
    }

    private static void applyInterface(XmlNode vehicle, String name, String address) {
        XmlNode iface = vehicle.findChild("interface");
        if (iface == null) {
            if (name.isEmpty() && address.isEmpty()) {
                return;
            }
            iface = new XmlNode("interface");
            // Reihenfolge an echten Dateien abgelesen: interface steht ganz
            // vorn, hoechstens nach description.
            XmlNode description = vehicle.findChild("description");
            int idx = description != null ? vehicle.getChildren().indexOf(description) + 1 : 0;
            vehicle.getChildren().add(idx, iface);
        }
        if (name.isEmpty()) {
            iface.removeAttribute("name");
        } else {
            iface.setAttribute("name", name);
        }
        if (address.isEmpty()) {
            iface.removeAttribute("address");
        } else {
            iface.setAttribute("address", address);
        }
    }

    /**
     * Setzt den gewaehlten Decoder-Typ: die Attribute von {@code <decoder>}
     * werden genau so geschrieben, wie iTrain sie fuer diesen Typ speichert
     * (siehe {@link #DECODER_VARIANTS}); die UID wird danach separat gesetzt
     * ({@link #applyUid}). Aendert sich die Fahrstufenzahl, wird eine
     * vorhandene Fahrstufentabelle ({@code <speed-control>}) entfernt - sie
     * passt dann nicht mehr; iTrain legt ohne sie selbst eine an (in von
     * iTrain gespeicherten Dateien fehlt sie oft).
     */
    private static void applyDecoderProtocol(XmlNode vehicle, String label) {
        DecoderVariant variant = null;
        for (DecoderVariant candidate : DECODER_VARIANTS) {
            if (candidate.label().equals(label)) {
                variant = candidate;
            }
        }
        if (variant == null) {
            return;
        }
        XmlNode decoder = vehicle.findChild("decoder");
        if (decoder != null && variant.matches(decoder)) {
            return;
        }
        int oldSteps = decoder == null ? -1 : parseIntOrDefault(decoder.getAttribute("steps"), -1);
        if (decoder == null) {
            decoder = new XmlNode("decoder");
            XmlNode description = vehicle.findChild("description");
            XmlNode iface = vehicle.findChild("interface");
            XmlNode id = vehicle.findChild("id");
            XmlNode anchor = id != null ? id : iface != null ? iface : description;
            int idx = anchor != null ? vehicle.getChildren().indexOf(anchor) + 1 : 0;
            vehicle.getChildren().add(idx, decoder);
        }
        decoder.getAttributes().clear();
        decoder.setAttribute("protocol", variant.protocol());
        decoder.setAttribute("steps", String.valueOf(variant.steps()));
        for (int i = 0; i < variant.flags().length; i += 2) {
            decoder.setAttribute(variant.flags()[i], variant.flags()[i + 1]);
        }
        if (oldSteps != variant.steps()) {
            XmlNode speedControl = vehicle.findChild("speed-control");
            if (speedControl != null) {
                vehicle.getChildren().remove(speedControl);
            }
        }
    }

    /** Liefert den Anzeigenamen des Decoder-Typs, oder null bei unbekannter Kombination. */
    private static String detectProtocolCode(XmlNode decoder) {
        if (decoder == null) {
            return null;
        }
        for (DecoderVariant variant : DECODER_VARIANTS) {
            if (variant.matches(decoder)) {
                return variant.label();
            }
        }
        return null;
    }

    /** Name des Decoder-Typs wie in iTrain, oder null - auch fuer die Tabellenspalte "Decoder". */
    static String decoderTypeLabel(XmlNode decoder) {
        return detectProtocolCode(decoder);
    }

    /** Zelle der Loktyp-Auswahl: farbiger Kennbuchstabe plus uebersetzter Name. */
    private static final class LocoTypeCell extends javafx.scene.control.ListCell<String> {
        private final I18n i18n;

        LocoTypeCell(I18n i18n) {
            this.i18n = i18n;
        }

        @Override
        protected void updateItem(String value, boolean empty) {
            super.updateItem(value, empty);
            if (empty || value == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            LocoType type = null;
            for (LocoType candidate : LOCO_TYPES) {
                if (candidate.xml().equals(value)) {
                    type = candidate;
                }
            }
            if (type == null) {
                setText(value);
                setGraphic(null);
                return;
            }
            Label letter = new Label(type.letter());
            letter.setMinWidth(14);
            letter.setStyle("-fx-font-weight: bold; -fx-text-fill: " + type.color() + ";");
            setGraphic(letter);
            setText(i18n.t(type.key()));
        }
    }

    private static int parseIntOrDefault(String text, int fallback) {
        try {
            return text == null ? fallback : Integer.parseInt(text.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static void setAttrOrRemove(XmlNode node, String attribute, String value) {
        if (value == null || value.isEmpty()) {
            node.removeAttribute(attribute);
        } else {
            node.setAttribute(attribute, value);
        }
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    // ------------------------------------------------------------------
    // Bausteine - wie SystemsObjectDialog, hier als eigene, kleine Kopien.
    // ------------------------------------------------------------------

    private static void addRow(GridPane grid, int[] row, String label, javafx.scene.Node field) {
        grid.add(new Label(label), 0, row[0]);
        grid.add(field, 1, row[0]);
        if (field instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }
        row[0]++;
    }

    private static void addSeparator(GridPane grid, int[] row) {
        javafx.scene.control.Separator separator = new javafx.scene.control.Separator();
        grid.add(separator, 0, row[0]++, 2, 1);
    }

    private static Label readOnly(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-opacity: 0.85;");
        return label;
    }

    private static ComboBox<SystemsObject.Choice> choiceBox(List<SystemsObject.Choice> choices, String current) {
        ComboBox<SystemsObject.Choice> box = new ComboBox<>(FXCollections.observableArrayList(choices));
        box.setMaxWidth(Double.MAX_VALUE);
        for (SystemsObject.Choice choice : box.getItems()) {
            if (choice.value().equals(current == null ? "" : current)) {
                box.setValue(choice);
                return box;
            }
        }
        if (!box.getItems().isEmpty()) {
            box.setValue(box.getItems().get(0));
        }
        return box;
    }
}
