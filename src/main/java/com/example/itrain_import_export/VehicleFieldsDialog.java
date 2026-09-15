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
 * ersten Eintrag "unveraendert": anders als beim Typ-Feld wuerde ein
 * geaenderter Decoder-Typ nicht nur ein Attribut, sondern Protokoll,
 * Fahrstufenzahl UND die komplette Fahrstufentabelle neu schreiben (siehe
 * {@link #applyDecoderProtocol}, nachgebildet nach
 * {@link SystemsObject#applyLocomotiveProtocol}) - das darf nur auf
 * ausdruecklichen Wunsch passieren, nie als Nebenwirkung eines bloss
 * angezeigten Feldes. Erkennt das Fenster die aktuellen Decoder-Werte als
 * eine der vier unterstuetzten Kombinationen (DCC 128/28/14, Selectrix 31 -
 * siehe {@link LocomotiveXmlFactory#parseLocoType}), ist diese vorgewaehlt;
 * sonst bleibt "unveraendert" stehen (Motorola, FMZ, SX2, "multi", "analog"
 * und aehnliche in echten Dateien beobachtete Protokolle werden NIE
 * automatisch auf DCC umgeschrieben).
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

    /** Lokomotiven-Typen, an echten Dateien abgelesen (siehe Klassenkommentar). */
    private static final List<String> LOCOMOTIVE_TYPES = List.of("steam", "diesel");

    /**
     * Wagen-Typen: {@code coach} (Personenwagen) sowie die deutschen
     * Gueterwagen-Gattungsbuchstaben, an echten Dateien abgelesen.
     */
    private static final List<String> WAGON_TYPES = List.of("coach", "e", "g", "i", "t", "u", "z");

    /** Wie {@link SystemsObjectDialog#LOCOMOTIVE_PROTOCOLS} - bewusst dieselben vier Codes. */
    private static final List<SystemsObject.Choice> DECODER_PROTOCOLS = List.of(
            new SystemsObject.Choice("DCC128", "DCC (128)"),
            new SystemsObject.Choice("DCC28", "DCC (28)"),
            new SystemsObject.Choice("DCC14", "DCC (14)"),
            new SystemsObject.Choice("SX32", "Selectrix (31)"));

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

        // Typ (Lok- bzw. Wagentyp) - editierbare Auswahl, siehe Klassenkommentar.
        List<String> typeChoices = isWagon ? WAGON_TYPES : LOCOMOTIVE_TYPES;
        ComboBox<String> typeBox = new ComboBox<>(FXCollections.observableArrayList(typeChoices));
        typeBox.setEditable(true);
        typeBox.setMaxWidth(Double.MAX_VALUE);
        typeBox.setValue(nz(vehicle.getAttribute("type")));
        if (isWagon) {
            typeBox.setTooltip(new Tooltip(i18n.t("vehicle.wagonTypeHint")));
        }
        addRow(grid, row, i18n.t("systems.fieldType"), typeBox);

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

        // Decoder-Typ: siehe Klassenkommentar - "unveraendert" plus die vier
        // bekannten Kombinationen; vorgewaehlt ist die erkannte aktuelle
        // Kombination, sonst "unveraendert".
        XmlNode currentDecoder = vehicle.findChild("decoder");
        String detectedCode = detectProtocolCode(currentDecoder);
        java.util.List<SystemsObject.Choice> decoderChoices = new java.util.ArrayList<>();
        decoderChoices.add(new SystemsObject.Choice("", i18n.t("vehicle.decoderKeep")));
        decoderChoices.addAll(DECODER_PROTOCOLS);
        ComboBox<SystemsObject.Choice> decoderBox = choiceBox(decoderChoices, detectedCode != null ? detectedCode : "");
        addRow(grid, row, i18n.t("vehicle.fieldDecoderType"), decoderBox);

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
     * Schreibt {@code <decoder>} und {@code <speed-control>} komplett neu -
     * nachgebildet nach {@link SystemsObject#applyLocomotiveProtocol}, hier
     * direkt auf dem rohen {@code XmlNode} statt ueber die Systeme-Huelle,
     * damit dieselbe Logik auch fuer beliebige, bereits in einer iTrain-Datei
     * vorhandene Fahrzeuge greift. Alles andere (Name, Interface, UID,
     * Funktionen) bleibt erhalten - die UID wird nach diesem Aufruf separat
     * neu gesetzt (siehe {@link #applyUid}), weil hier alle Decoder-Attribute
     * geloescht werden.
     */
    private static void applyDecoderProtocol(XmlNode vehicle, String protocolCode) {
        LocomotiveXmlFactory.LocoInfo info = LocomotiveXmlFactory.parseLocoType(protocolCode);
        XmlNode decoder = vehicle.findChild("decoder");
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
        decoder.setAttribute("protocol", info.decoderProtocol());
        decoder.setAttribute("steps", String.valueOf(info.steps()));
        if (info.extended()) {
            decoder.setAttribute("extended", "true");
        }
        XmlNode speedControl = vehicle.findChild("speed-control");
        if (speedControl == null) {
            speedControl = new XmlNode("speed-control");
            vehicle.getChildren().add(speedControl);
        }
        speedControl.getChildren().clear();
        int count = info.steps() + 1;
        speedControl.setAttribute("count", String.valueOf(count));
        speedControl.setAttribute("unit", "km_h");
        for (int step = 1; step <= info.steps(); step++) {
            XmlNode speed = new XmlNode("speed");
            speed.setAttribute("step", String.valueOf(step));
            speed.setAttribute("value", SystemsObject.formatNumber(100.0 * step / info.steps()));
            speedControl.getChildren().add(speed);
        }
    }

    /**
     * Erkennt, ob ein vorhandener {@code <decoder>}-Knoten genau einer der
     * vier unterstuetzten Kombinationen entspricht (siehe
     * {@link LocomotiveXmlFactory#parseLocoType}) - liefert {@code null} bei
     * jedem anderen oder fehlenden Decoder, damit dieser NIE ungefragt
     * ueberschrieben wird.
     */
    private static String detectProtocolCode(XmlNode decoder) {
        if (decoder == null) {
            return null;
        }
        String protocol = nz(decoder.getAttribute("protocol")).toLowerCase(Locale.ROOT);
        int steps = parseIntOrDefault(decoder.getAttribute("steps"), -1);
        boolean extended = "true".equals(decoder.getAttribute("extended"));
        if ("dcc".equals(protocol) && steps == 126 && extended) {
            return "DCC128";
        }
        if ("dcc".equals(protocol) && steps == 28) {
            return "DCC28";
        }
        if ("dcc".equals(protocol) && steps == 14) {
            return "DCC14";
        }
        if (("selectrix".equals(protocol) || "sx".equals(protocol)) && steps == 31) {
            return "SX32";
        }
        return null;
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
