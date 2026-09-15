package com.example.itrain_import_export;

import javafx.collections.FXCollections;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
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

/**
 * Bearbeitungsfenster für eine Zeile des Systeme-Fensters - dem
 * iTrain-Dialog nachempfunden, damit die Eingaben vertraut sind: Felder
 * untereinander, mit TAB / Umschalt+TAB durchlaufbar, unten rechts
 * "Übernehmen", links daneben "Abbruch". Je Kategorie andere Felder:
 * <ul>
 * <li>Rückmelder: Name, Beschreibung, Typ (Belegt/Weiche/Taster), Seite
 * (Keine/Links/Rechts), Länge in cm, Schnittstelle; Knoten und Port nur
 * zur Anzeige.</li>
 * <li>Zubehör (Weiche): Name, Beschreibung, Typ (die zehn Weichenbauformen
 * der iTrain-Auswahlbox), Grundstellung (passend zur Bauform),
 * Schnittstelle; Knoten und Port zur Anzeige.</li>
 * <li>Booster: Name, Beschreibung, Typ (bidib), Schnittstelle, Knoten;
 * Grenzwerte Gleisspannung (V), Hauptgleis (A), Temperatur (°C) je mit
 * Ankreuzkasten - abgewählt heißt: kein Grenzwert-Kanal im Eintrag.</li>
 * <li>Schnittstelle: Name, Beschreibung, Typ, Adresse (Host/Port bzw.
 * COM-Port).</li>
 * </ul>
 * Nur was iTrain nachweislich in die Datei schreibt, ist hier änderbar (an
 * echten Dateien abgelesen, siehe {@link SystemsObject}); "Invertiert",
 * Versorgungsspannung, Programmiergleis und das Booster-"Zubehör" aus dem
 * iTrain-Dialog fehlen bewusst, weil ihre XML-Form nicht belegt ist.
 */
public final class SystemsObjectDialog {

    private SystemsObjectDialog() {
    }

    /** Zeigt das Fenster; true, wenn mit "Uebernehmen" geschlossen (die Zeile ist dann geaendert). */
    public static boolean show(Window owner, SystemsObject object) {
        I18n i18n = I18n.getInstance();
        AppSettings settings = AppSettings.getInstance();

        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(i18n.t("systems.editRow") + " - " + object.getBidibName());

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

        TextField nameField = new TextField(object.getName());
        TextField descriptionField = new TextField(object.getDescription());
        addRow(grid, row, i18n.t("systems.fieldName"), nameField);
        addRow(grid, row, i18n.t("systems.fieldDescription"), descriptionField);

        Runnable apply;
        switch (object.getCategory()) {
            case SystemsObject.CATEGORY_FEEDBACKS:
                apply = feedbackFields(grid, row, object, i18n, nameField, descriptionField);
                break;
            case SystemsObject.CATEGORY_ACCESSORIES:
                apply = accessoryFields(grid, row, object, i18n, nameField, descriptionField);
                break;
            case SystemsObject.CATEGORY_BOOSTERS:
                apply = boosterFields(grid, row, object, i18n, nameField, descriptionField);
                break;
            case SystemsObject.CATEGORY_LOCOMOTIVES:
                apply = locomotiveFields(grid, row, object, i18n, nameField, descriptionField);
                break;
            default:
                apply = interfaceFields(grid, row, object, i18n, nameField, descriptionField);
                break;
        }

        boolean[] accepted = {false};
        Button okButton = new Button(i18n.t("systems.applyButton"));
        okButton.setDefaultButton(true);
        Button cancelButton = new Button(i18n.t("bidib.abortButton"));
        cancelButton.setCancelButton(true);
        okButton.setOnAction(e -> {
            apply.run();
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
    // Felder je Kategorie - jede Methode liefert das "Uebernehmen".
    // ------------------------------------------------------------------

    private static Runnable feedbackFields(GridPane grid, int[] row, SystemsObject object, I18n i18n,
            TextField nameField, TextField descriptionField) {
        ComboBox<SystemsObject.Choice> typeBox = choiceBox(SystemsObject.feedbackTypeChoices(i18n), object.getType());
        ComboBox<SystemsObject.Choice> sideBox = choiceBox(List.of(
                new SystemsObject.Choice("", i18n.t("systems.sideNone")),
                new SystemsObject.Choice("left", i18n.t("systems.sideLeft")),
                new SystemsObject.Choice("right", i18n.t("systems.sideRight"))), object.attribute("side"));
        TextField lengthField = new TextField(object.getLength());
        lengthField.setPrefColumnCount(8);
        HBox lengthRow = new HBox(6, lengthField, new Label("cm"));
        lengthRow.setAlignment(Pos.CENTER_LEFT);
        TextField interfaceField = new TextField(object.getInterfaceName());

        addRow(grid, row, i18n.t("systems.fieldType"), typeBox);
        addRow(grid, row, i18n.t("systems.fieldSide"), sideBox);
        addRow(grid, row, i18n.t("systems.fieldLength"), lengthRow);
        addSeparator(grid, row);
        addRow(grid, row, i18n.t("systems.colInterface"), interfaceField);
        addRow(grid, row, i18n.t("systems.fieldNode"), readOnly(object.getBidibName()));
        addRow(grid, row, i18n.t("systems.fieldPort"), readOnly(portOf(object.getAddress())));

        return () -> {
            object.setName(nameField.getText());
            object.setDescription(descriptionField.getText());
            object.setType(valueOf(typeBox));
            object.setAttribute("side", valueOf(sideBox));
            object.setLength(lengthField.getText());
            object.setInterfaceName(interfaceField.getText());
        };
    }

    private static Runnable accessoryFields(GridPane grid, int[] row, SystemsObject object, I18n i18n,
            TextField nameField, TextField descriptionField) {
        ComboBox<SystemsObject.Choice> typeBox = choiceBox(SystemsObject.turnoutTypeChoices(i18n), object.getType());
        ComboBox<SystemsObject.Choice> initialBox = new ComboBox<>();
        initialBox.setMaxWidth(Double.MAX_VALUE);
        Runnable fillStates = () -> {
            String type = valueOf(typeBox);
            String current = initialBox.getValue() != null ? initialBox.getValue().value()
                    : object.attribute("initial-state");
            List<SystemsObject.Choice> choices = new java.util.ArrayList<>();
            choices.add(new SystemsObject.Choice("", i18n.t("systems.sideNone")));
            choices.addAll(SystemsObject.turnoutStateChoices(type, i18n));
            initialBox.setItems(FXCollections.observableArrayList(choices));
            select(initialBox, current);
        };
        fillStates.run();
        typeBox.setOnAction(e -> fillStates.run());
        TextField interfaceField = new TextField(object.getInterfaceName());

        addRow(grid, row, i18n.t("systems.fieldType"), typeBox);
        addRow(grid, row, i18n.t("systems.fieldInitialState"), initialBox);
        addSeparator(grid, row);
        addRow(grid, row, i18n.t("systems.colInterface"), interfaceField);
        addRow(grid, row, i18n.t("systems.fieldNode"), readOnly(object.getBidibName()));
        addRow(grid, row, i18n.t("systems.fieldPort"), readOnly(portOf(object.getAddress())));

        return () -> {
            object.setName(nameField.getText());
            object.setDescription(descriptionField.getText());
            object.applyTurnoutType(valueOf(typeBox), valueOf(initialBox));
            object.setInterfaceName(interfaceField.getText());
        };
    }

    private static Runnable boosterFields(GridPane grid, int[] row, SystemsObject object, I18n i18n,
            TextField nameField, TextField descriptionField) {
        TextField interfaceField = new TextField(object.getInterfaceName());
        addRow(grid, row, i18n.t("systems.fieldType"), readOnly("BiDiB"));
        addSeparator(grid, row);
        addRow(grid, row, i18n.t("systems.colInterface"), interfaceField);
        addRow(grid, row, i18n.t("systems.fieldNode"), readOnly(object.getBidibName()));
        addSeparator(grid, row);

        Label limitsHeader = new Label(i18n.t("systems.limitsHeader"));
        limitsHeader.setStyle("-fx-font-weight: bold;");
        grid.add(limitsHeader, 0, row[0]++, 2, 1);

        Double voltage = object.boosterLimit("voltage_track");
        Double current = object.boosterLimit("current_main");
        Double temperature = object.boosterLimit("temperature");
        LimitRow voltageRow = limitRow(grid, row, i18n.t("systems.limitTrackVoltage"), voltage, 22.0, 0, 30, 0.5, "V");
        LimitRow currentRow = limitRow(grid, row, i18n.t("systems.limitMainCurrent"), current, 6.0, 0, 20, 0.1, "A");
        LimitRow temperatureRow = limitRow(grid, row, i18n.t("systems.limitTemperature"), temperature, 60.0, 0, 150, 1, "°C");

        return () -> {
            object.setName(nameField.getText());
            object.setDescription(descriptionField.getText());
            object.setInterfaceName(interfaceField.getText());
            object.applyBoosterLimits(voltageRow.value(), currentRow.value(), temperatureRow.value());
        };
    }

    private static final List<SystemsObject.Choice> LOCOMOTIVE_PROTOCOLS = List.of(
            new SystemsObject.Choice("DCC128", "DCC (128)"),
            new SystemsObject.Choice("DCC28", "DCC (28)"),
            new SystemsObject.Choice("DCC14", "DCC (14)"),
            new SystemsObject.Choice("SX32", "Selectrix (31)"));

    private static Runnable locomotiveFields(GridPane grid, int[] row, SystemsObject object, I18n i18n,
            TextField nameField, TextField descriptionField) {
        TextField addressField = new TextField(object.getAddress());
        addressField.setPrefColumnCount(8);
        ComboBox<SystemsObject.Choice> protocolBox = choiceBox(LOCOMOTIVE_PROTOCOLS, object.getType());
        TextField interfaceField = new TextField(object.getInterfaceName());

        addRow(grid, row, i18n.t("systems.fieldAddress"), addressField);
        addRow(grid, row, i18n.t("systems.fieldProtocol"), protocolBox);
        addSeparator(grid, row);
        addRow(grid, row, i18n.t("systems.colInterface"), interfaceField);

        return () -> {
            object.setName(nameField.getText());
            object.setDescription(descriptionField.getText());
            object.setAddress(addressField.getText());
            object.applyLocomotiveProtocol(valueOf(protocolBox));
            object.setInterfaceName(interfaceField.getText());
        };
    }

    private static Runnable interfaceFields(GridPane grid, int[] row, SystemsObject object, I18n i18n,
            TextField nameField, TextField descriptionField) {
        boolean serial = "bidib".equals(object.getType());
        addRow(grid, row, i18n.t("systems.fieldType"), readOnly(serial ? "bidib (USB)" : "netbidib"));
        TextField hostField = new TextField(serial ? object.childAttribute("serial", "port")
                : object.childAttribute("socket", "host"));
        TextField portField = new TextField(object.childAttribute("socket", "port"));
        portField.setPrefColumnCount(6);
        if (serial) {
            addRow(grid, row, "COM-Port", hostField);
        } else {
            addRow(grid, row, stripColon(i18n.t("bidib.ipLabel")), hostField);
            addRow(grid, row, stripColon(i18n.t("bidib.portLabel")), portField);
        }
        return () -> {
            // Bei der Schnittstelle sind Name und Schnittstellen-Name dasselbe.
            object.setInterfaceName(nameField.getText());
            object.setDescription(descriptionField.getText());
            if (serial) {
                object.setChildAttribute("serial", "port", hostField.getText());
            } else {
                object.setChildAttribute("socket", "host", hostField.getText());
                object.setChildAttribute("socket", "port", portField.getText());
            }
        };
    }

    // ------------------------------------------------------------------
    // Bausteine
    // ------------------------------------------------------------------

    private record LimitRow(CheckBox box, Spinner<Double> spinner) {
        Double value() {
            return box.isSelected() ? spinner.getValue() : null;
        }
    }

    private static LimitRow limitRow(GridPane grid, int[] row, String label, Double current, double fallback,
            double min, double max, double step, String unit) {
        CheckBox box = new CheckBox(label);
        box.setSelected(current != null);
        Spinner<Double> spinner = new Spinner<>();
        spinner.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(min, max,
                current != null ? current : fallback, step));
        spinner.setEditable(true);
        spinner.setPrefWidth(110);
        spinner.disableProperty().bind(box.selectedProperty().not());
        Label maxLabel = new Label(I18n.getInstance().t("systems.limitMaximum"));
        HBox line = new HBox(8, maxLabel, spinner, new Label(unit));
        line.setAlignment(Pos.CENTER_LEFT);
        grid.add(box, 0, row[0]);
        grid.add(line, 1, row[0]);
        GridPane.setHalignment(box, HPos.LEFT);
        row[0]++;
        return new LimitRow(box, spinner);
    }

    private static void addRow(GridPane grid, int[] row, String label, Node field) {
        grid.add(new Label(label), 0, row[0]);
        grid.add(field, 1, row[0]);
        if (field instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }
        row[0]++;
    }

    private static void addSeparator(GridPane grid, int[] row) {
        Separator separator = new Separator();
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
        select(box, current);
        return box;
    }

    private static void select(ComboBox<SystemsObject.Choice> box, String value) {
        for (SystemsObject.Choice choice : box.getItems()) {
            if (choice.value().equals(value == null ? "" : value)) {
                box.setValue(choice);
                return;
            }
        }
        if (!box.getItems().isEmpty()) {
            box.setValue(box.getItems().get(0));
        }
    }

    private static String valueOf(ComboBox<SystemsObject.Choice> box) {
        return box.getValue() == null ? "" : box.getValue().value();
    }

    private static String stripColon(String label) {
        return label.endsWith(":") ? label.substring(0, label.length() - 1) : label;
    }

    /** "1 / 7" -> "7"; alles andere unveraendert. */
    private static String portOf(String address) {
        int slash = address.lastIndexOf(" / ");
        return slash >= 0 ? address.substring(slash + 3) : "";
    }
}
