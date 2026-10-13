package com.example.itrain_import_export;

import javafx.collections.FXCollections;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Felder des Bearbeitungsfensters fuer eine Lokomotive im Systeme-Fenster
 * (seit 13.10.2026, Vorgabe Andre) - dem Reiter "Optionen" von iTrain
 * nachempfunden:
 * <ul>
 * <li>nur Anzeige: Decoder-Typ, UID, Adresse, Adresse 2, Schnittstelle;</li>
 * <li>aenderbar: Loktyp, Laenge, Zahnrad, Spurweite, Polaritaet, Sichtseite,
 * Symmetrisch, Traegheitssimulation, Rueckmeldungs-Offset,
 * Reaktionsverzoegerung, Wartung/Brennstoff.</li>
 * </ul>
 * XML-Formen an Test1.tcdz (von iTrain gespeichert, alle Optionen gesetzt)
 * und Kurpfalz_860_V6.tcdz abgelesen:
 * {@code <option>rack</option>}, {@code <option>symmetrical</option>};
 * {@code <offset type="occupancy|reed|light_barrier" front rear>} in cm,
 * ohne front/rear wenn 0; Bremsen/Beschleunigen aus = leeres
 * {@code <deceleration/>}; {@code <delay value>} bei gleichen Werten, sonst
 * {@code forward}/{@code backward}; Wartung/Brennstoff in Sekunden.
 * Spurweite {@code n}/{@code m}/{@code e} (H0, H0m, H0e) belegt, {@code m}
 * an Test1.tcdz vom 13.10.2026.
 * <p>
 * Die UID wird nur angezeigt und nie geschrieben (Andre, 13.10.2026: wie
 * iTrain sie speichert, ist noch nicht belegt).
 */
final class LocomotiveOptionsFields {

    private LocomotiveOptionsFields() {
    }

    /** Reihenfolge der Kind-Elemente einer Lok, an echten Dateien abgelesen. */
    private static final List<String> CHILD_ORDER = List.of("description", "id", "interface", "decoder",
            "configuration", "length", "options", "feedback", "delay", "acceleration", "deceleration",
            "speed-limit", "speed-control", "image", "functions", "fuel", "maintenance", "total", "last-used",
            "comment");

    private static final List<String> LOCO_TYPES = List.of("", "steam", "diesel", "electric", "hydrogen", "battery");
    private static final List<String> OFFSET_TYPES = List.of("occupancy", "reed", "light_barrier");

    /** Masstab-Verhaeltnis (aus {@code <settings><scale>}) -> Name wie in der iTrain-Auswahl. */
    private static String scaleName(String scale) {
        if (scale == null) {
            return "";
        }
        switch (scale.trim()) {
            case "19.0": return "SM32";
            case "22.5": return "G";
            case "29.0": return "REA";
            case "32.0": return "I";
            case "43.5": return "0 (UK)";
            case "45.0": return "0";
            case "48.0": return "0 (US)";
            case "64.0": return "S";
            case "76.0": return "00";
            case "87.0": return "H0";
            case "100.0": return "TT (UK)";
            case "120.0": return "TT";
            case "148.0": return "N (UK)";
            case "150.0": return "N (JP)";
            case "160.0": return "N";
            case "220.0": return "Z";
            case "450.0": return "T";
            default: return "";
        }
    }

    /** Globale Spurweite der iTrain-Datei ({@code <settings><train gauge>}), sonst "n". */
    static String globalGauge(XmlNode itrainRoot) {
        XmlNode settings = itrainRoot == null ? null : itrainRoot.findChild("settings");
        XmlNode train = settings == null ? null : settings.findChild("train");
        String gauge = train == null ? null : train.getAttribute("gauge");
        return gauge == null || gauge.isBlank() ? "n" : gauge;
    }

    private static String globalScale(XmlNode itrainRoot) {
        XmlNode settings = itrainRoot == null ? null : itrainRoot.findChild("settings");
        XmlNode scale = settings == null ? null : settings.findChild("scale");
        return scale == null ? null : scale.getTextContent();
    }

    /** Baut die Felder ins Raster und liefert das "Uebernehmen". */
    static Runnable build(GridPane grid, int[] row, SystemsObject object, I18n i18n,
            TextField nameField, TextField descriptionField, XmlNode itrainRoot) {
        XmlNode loco = object.getXml();

        // --- Loktyp, Laenge -------------------------------------------------
        List<SystemsObject.Choice> typeChoices = new ArrayList<>();
        for (String type : LOCO_TYPES) {
            typeChoices.add(new SystemsObject.Choice(type,
                    i18n.t("vehicle.locoType." + (type.isEmpty() ? "other" : type))));
        }
        String currentType = attr(loco, "type");
        if (!LOCO_TYPES.contains(currentType)) {
            typeChoices.add(new SystemsObject.Choice(currentType, currentType));
        }
        ComboBox<SystemsObject.Choice> typeBox = choiceBox(typeChoices, currentType);
        addRow(grid, row, i18n.t("vehicle.fieldLocoType"), typeBox);

        Spinner<Double> lengthSpinner = doubleSpinner(0, 5000, lengthMm(loco), 1);
        addRow(grid, row, i18n.t("systems.fieldLength"), withUnit(lengthSpinner, "mm"));

        // --- Decoder: nur Anzeige --------------------------------------------
        addHeader(grid, row, i18n.t("loco.sectionDecoder"));
        XmlNode decoder = loco.findChild("decoder");
        String decoderLabel = VehicleFieldsDialog.decoderTypeLabel(decoder);
        addRow(grid, row, i18n.t("vehicle.fieldDecoderType"), readOnly(decoderLabel != null ? decoderLabel : object.getType()));
        addRow(grid, row, i18n.t("vehicle.fieldUid"), readOnly(object.getUid().isEmpty() ? "-" : object.getUid()));
        addRow(grid, row, i18n.t("systems.fieldAddress"), readOnly(object.getAddress()));
        addRow(grid, row, i18n.t("loco.address2"), readOnly("0"));
        // Hinter der Schnittstelle: Knopf "Funktionen" - Gegenueberstellung
        // mc2 gegen iTrain (FunctionMappingDialog). Das Ergebnis wird erst
        // beim "Uebernehmen" dieses Fensters geschrieben.
        java.util.concurrent.atomic.AtomicReference<Map<Integer, String>> pendingFunctions =
                new java.util.concurrent.atomic.AtomicReference<>();
        javafx.scene.control.Button functionsButton = new javafx.scene.control.Button(i18n.t("loco.functionsButton"));
        functionsButton.setOnAction(e -> {
            Map<Integer, String> current = pendingFunctions.get() != null ? pendingFunctions.get()
                    : FunctionMappingDialog.namesOf(FunctionMappingDialog.baseFunctions(object));
            FunctionMappingDialog.show(functionsButton.getScene().getWindow(), object, current)
                    .ifPresent(pendingFunctions::set);
        });
        Region interfaceSpacer = new Region();
        HBox.setHgrow(interfaceSpacer, javafx.scene.layout.Priority.ALWAYS);
        HBox interfaceRow = new HBox(10, readOnly(object.getInterfaceName()), interfaceSpacer, functionsButton);
        interfaceRow.setAlignment(Pos.CENTER_LEFT);
        interfaceRow.setMaxWidth(Double.MAX_VALUE);
        addRow(grid, row, i18n.t("systems.colInterface"), interfaceRow);

        // --- Gleis -----------------------------------------------------------
        addHeader(grid, row, i18n.t("loco.sectionTrack"));
        CheckBox rackBox = new CheckBox(i18n.t("loco.rack"));
        rackBox.setSelected(hasOption(loco, "rack"));
        String scale = scaleName(globalScale(itrainRoot));
        List<SystemsObject.Choice> gaugeChoices = new ArrayList<>(List.of(
                new SystemsObject.Choice("n", scale.isEmpty() ? i18n.t("loco.gaugeNormal") : scale),
                new SystemsObject.Choice("m", (scale.isEmpty() ? "" : scale) + "m"),
                new SystemsObject.Choice("e", (scale.isEmpty() ? "" : scale) + "e")));
        String currentGauge = attr(loco, "gauge");
        if (!currentGauge.isEmpty() && gaugeChoices.stream().noneMatch(c -> c.value().equals(currentGauge))) {
            gaugeChoices.add(new SystemsObject.Choice(currentGauge, currentGauge));
        }
        ComboBox<SystemsObject.Choice> gaugeBox = choiceBox(gaugeChoices,
                currentGauge.isEmpty() ? globalGauge(itrainRoot) : currentGauge);
        ComboBox<SystemsObject.Choice> polarityBox = choiceBox(List.of(
                new SystemsObject.Choice("unknown", i18n.t("loco.polarityUnknown")),
                new SystemsObject.Choice("normal", i18n.t("loco.polarityNormal")),
                new SystemsObject.Choice("inverted", i18n.t("loco.polarityInverted"))), attr(loco, "polarity"));
        addRow(grid, row, "", rackBox);
        addRow(grid, row, i18n.t("loco.gauge"), gaugeBox);
        addRow(grid, row, i18n.t("loco.polarity"), polarityBox);

        // --- Fuehrerstand ----------------------------------------------------
        addHeader(grid, row, i18n.t("loco.sectionCab"));
        // Reihenfolge wie in iTrain: Kein, Vorderseite, Rueckseite, Beide.
        // Alle vier an echten Dateien belegt (Test1.tcdz vom 13.10.2026):
        // "Kein" = Attribut cabin fehlt, sonst front/rear/both.
        List<SystemsObject.Choice> cabinChoices = new ArrayList<>(List.of(
                new SystemsObject.Choice("", i18n.t("loco.cabinNone")),
                new SystemsObject.Choice("front", i18n.t("loco.cabinFront")),
                new SystemsObject.Choice("rear", i18n.t("loco.cabinRear")),
                new SystemsObject.Choice("both", i18n.t("loco.cabinBoth"))));
        String currentCabin = attr(loco, "cabin");
        if (!currentCabin.isEmpty() && cabinChoices.stream().noneMatch(c -> c.value().equals(currentCabin))) {
            cabinChoices.add(new SystemsObject.Choice(currentCabin, currentCabin));
        }
        ComboBox<SystemsObject.Choice> cabinBox = choiceBox(cabinChoices, currentCabin);
        CheckBox symmetricalBox = new CheckBox(i18n.t("loco.symmetrical"));
        symmetricalBox.setSelected(hasOption(loco, "symmetrical"));
        addRow(grid, row, i18n.t("loco.cabin"), cabinBox);
        addRow(grid, row, "", symmetricalBox);

        // --- Unterer Teil: eigenes Raster wie in iTrain ----------------------
        // Spalten: Haekchen | Beschriftung | Feld | Beschriftung | Feld - so
        // stehen alle Eingabefelder der vier Abschnitte genau uebereinander.
        GridPane opts = new GridPane();
        opts.setHgap(10);
        opts.setVgap(8);
        javafx.scene.layout.ColumnConstraints c0 = new javafx.scene.layout.ColumnConstraints();
        c0.setHalignment(HPos.LEFT);
        c0.setMinWidth(120);
        javafx.scene.layout.ColumnConstraints c1 = new javafx.scene.layout.ColumnConstraints();
        c1.setHalignment(HPos.RIGHT);
        javafx.scene.layout.ColumnConstraints c2 = new javafx.scene.layout.ColumnConstraints();
        c2.setHalignment(HPos.LEFT);
        javafx.scene.layout.ColumnConstraints c3 = new javafx.scene.layout.ColumnConstraints();
        c3.setHalignment(HPos.RIGHT);
        javafx.scene.layout.ColumnConstraints c4 = new javafx.scene.layout.ColumnConstraints();
        c4.setHalignment(HPos.LEFT);
        opts.getColumnConstraints().addAll(c0, c1, c2, c3, c4);
        grid.add(opts, 0, row[0]++, 2, 1);
        GridPane.setHalignment(opts, HPos.LEFT);
        int[] orow = {0};

        // --- Traegheitssimulation -------------------------------------------
        addOptsHeader(opts, orow, i18n.t("loco.sectionInertia"));
        InertiaRow accelRow = inertiaRow(opts, orow, i18n, i18n.t("loco.accelerate"), loco.findChild("acceleration"));
        InertiaRow decelRow = inertiaRow(opts, orow, i18n, i18n.t("loco.brake"), loco.findChild("deceleration"));

        // --- Rueckmeldungs-Offset -------------------------------------------
        addOptsHeader(opts, orow, i18n.t("loco.sectionOffset"));
        XmlNode feedback = loco.findChild("feedback");
        List<OffsetRow> offsetRows = new ArrayList<>();
        String[] offsetLabels = {i18n.t("loco.offsetOccupancy"), i18n.t("loco.offsetReed"), i18n.t("loco.offsetLight")};
        for (int i = 0; i < OFFSET_TYPES.size(); i++) {
            offsetRows.add(offsetRow(opts, orow, i18n, offsetLabels[i], OFFSET_TYPES.get(i), feedback));
        }

        // --- Reaktionsverzoegerung ------------------------------------------
        addOptsHeader(opts, orow, i18n.t("loco.sectionReaction"));
        XmlNode delay = loco.findChild("delay");
        int forward = delay == null ? LocomotiveXmlFactory.DEFAULT_REACTION_MS
                : parseInt(firstNonEmpty(attr(delay, "forward"), attr(delay, "value")), LocomotiveXmlFactory.DEFAULT_REACTION_MS);
        int backward = delay == null ? LocomotiveXmlFactory.DEFAULT_REACTION_MS
                : parseInt(firstNonEmpty(attr(delay, "backward"), attr(delay, "value")), LocomotiveXmlFactory.DEFAULT_REACTION_MS);
        Spinner<Integer> forwardSpinner = intSpinner(0, 60000, forward, 50);
        Spinner<Integer> backwardSpinner = intSpinner(0, 60000, backward, 50);
        addPair(opts, orow, null, i18n.t("loco.forward"), withUnit(forwardSpinner, "ms"),
                i18n.t("loco.backward"), withUnit(backwardSpinner, "ms"));

        // --- Zeitraum --------------------------------------------------------
        addOptsHeader(opts, orow, i18n.t("loco.sectionPeriod"));
        Spinner<Double> maintenanceSpinner = doubleSpinner(0, 10000,
                hours(loco.findChild("maintenance"), LocomotiveXmlFactory.DEFAULT_MAINTENANCE_H), 0.5);
        Spinner<Double> fuelSpinner = doubleSpinner(0, 10000,
                hours(loco.findChild("fuel"), LocomotiveXmlFactory.DEFAULT_FUEL_H), 0.5);
        addPair(opts, orow, null, i18n.t("loco.maintenance"), withUnit(maintenanceSpinner, "h"),
                i18n.t("loco.fuel"), withUnit(fuelSpinner, "h"));

        return () -> {
            object.setName(nameField.getText());
            object.setDescription(descriptionField.getText());
            // Loktyp direkt ins XML - die Spalte "Typ" der Zeile zeigt das
            // Fahrstufen-Protokoll und bleibt unveraendert.
            setAttrOrRemove(loco, "type", valueOf(typeBox));
            applyLength(loco, lengthSpinner.getValue());
            object.refreshLocomotiveLength();

            setAttrOrRemove(loco, "gauge", valueOf(gaugeBox));
            setAttrOrRemove(loco, "polarity", valueOf(polarityBox));
            setAttrOrRemove(loco, "cabin", valueOf(cabinBox));
            setOption(loco, "rack", rackBox.isSelected());
            setOption(loco, "symmetrical", symmetricalBox.isSelected());

            accelRow.apply(loco, "acceleration");
            decelRow.apply(loco, "deceleration");
            applyOffsets(loco, offsetRows);
            applyDelay(loco, forwardSpinner.getValue(), backwardSpinner.getValue());
            applyPeriod(loco, "maintenance", maintenanceSpinner.getValue());
            applyPeriod(loco, "fuel", fuelSpinner.getValue());
            if (pendingFunctions.get() != null) {
                FunctionMappingDialog.apply(object, pendingFunctions.get());
            }
        };
    }

    // ------------------------------------------------------------------
    // Traegheit
    // ------------------------------------------------------------------

    private record InertiaRow(CheckBox box, Spinner<Integer> delay, Spinner<Integer> step) {
        void apply(XmlNode loco, String tag) {
            XmlNode node = ensureChild(loco, tag);
            node.getAttributes().clear();
            if (box.isSelected()) {
                node.setAttribute("delay", String.valueOf(delay.getValue()));
                node.setAttribute("unit", "ms");
                node.setAttribute("step", String.valueOf(step.getValue()));
            }
        }
    }

    private static InertiaRow inertiaRow(GridPane grid, int[] row, I18n i18n, String label, XmlNode current) {
        boolean active = current != null && !attr(current, "delay").isEmpty();
        CheckBox box = new CheckBox(label);
        box.setSelected(current == null || active);
        Spinner<Integer> delay = intSpinner(0, 10000,
                active ? parseInt(attr(current, "delay"), LocomotiveXmlFactory.DEFAULT_INERTIA_MS)
                        : LocomotiveXmlFactory.DEFAULT_INERTIA_MS, 10);
        Spinner<Integer> step = intSpinner(1, 126,
                active ? parseInt(attr(current, "step"), LocomotiveXmlFactory.DEFAULT_INERTIA_STEP)
                        : LocomotiveXmlFactory.DEFAULT_INERTIA_STEP, 1);
        delay.disableProperty().bind(box.selectedProperty().not());
        step.disableProperty().bind(box.selectedProperty().not());
        addPair(grid, row, box, i18n.t("loco.stepDelay"), withUnit(delay, "ms"),
                i18n.t("loco.stepWidth"), step);
        return new InertiaRow(box, delay, step);
    }

    // ------------------------------------------------------------------
    // Rueckmeldungs-Offset
    // ------------------------------------------------------------------

    private record OffsetRow(String type, CheckBox box, Spinner<Double> front, Spinner<Double> rear) {
    }

    private static OffsetRow offsetRow(GridPane grid, int[] row, I18n i18n, String label, String type, XmlNode feedback) {
        XmlNode current = null;
        if (feedback != null) {
            for (XmlNode offset : feedback.getChildren()) {
                if ("offset".equals(offset.getTagName()) && type.equals(offset.getAttribute("type"))) {
                    current = offset;
                }
            }
        }
        CheckBox box = new CheckBox(label);
        // Neue Lok ohne feedback: nur "Belegt" aktiv (Vorgabe Andre).
        box.setSelected(current != null || (feedback == null && "occupancy".equals(type)));
        double frontMm = current == null ? LocomotiveXmlFactory.DEFAULT_OFFSET_MM : cmToMm(attr(current, "front"));
        double rearMm = current == null ? LocomotiveXmlFactory.DEFAULT_OFFSET_MM : cmToMm(attr(current, "rear"));
        Spinner<Double> front = doubleSpinner(0, 1000, frontMm, 1);
        Spinner<Double> rear = doubleSpinner(0, 1000, rearMm, 1);
        front.disableProperty().bind(box.selectedProperty().not());
        rear.disableProperty().bind(box.selectedProperty().not());
        addPair(grid, row, box, i18n.t("loco.front"), withUnit(front, "mm"),
                i18n.t("loco.rear"), withUnit(rear, "mm"));
        return new OffsetRow(type, box, front, rear);
    }

    private static void applyOffsets(XmlNode loco, List<OffsetRow> rows) {
        XmlNode feedback = ensureChild(loco, "feedback");
        // Fremde Offset-Arten (falls iTrain weitere kennt) unangetastet lassen.
        feedback.getChildren().removeIf(c -> "offset".equals(c.getTagName())
                && OFFSET_TYPES.contains(c.getAttribute("type")));
        int insertAt = 0;
        for (OffsetRow row : rows) {
            if (!row.box().isSelected()) {
                continue;
            }
            XmlNode offset = new XmlNode("offset");
            offset.setAttribute("type", row.type());
            // iTrain laesst front/rear bei 0 weg (Test1.tcdz).
            if (row.front().getValue() > 0) {
                offset.setAttribute("front", SystemsObject.formatNumber(row.front().getValue() / 10.0));
            }
            if (row.rear().getValue() > 0) {
                offset.setAttribute("rear", SystemsObject.formatNumber(row.rear().getValue() / 10.0));
            }
            feedback.getChildren().add(insertAt++, offset);
        }
        feedback.setAttribute("count", String.valueOf(feedback.getChildren().size()));
        feedback.setAttribute("unit", "cm");
        if (feedback.getChildren().isEmpty()) {
            loco.getChildren().remove(feedback);
        }
    }

    // ------------------------------------------------------------------
    // Uebrige Werte
    // ------------------------------------------------------------------

    private static void applyDelay(XmlNode loco, int forward, int backward) {
        XmlNode delay = ensureChild(loco, "delay");
        delay.getAttributes().clear();
        if (forward == backward) {
            delay.setAttribute("value", String.valueOf(forward));
        } else {
            delay.setAttribute("forward", String.valueOf(forward));
            delay.setAttribute("backward", String.valueOf(backward));
        }
        delay.setAttribute("unit", "ms");
    }

    private static void applyPeriod(XmlNode loco, String tag, double hours) {
        XmlNode node = ensureChild(loco, tag);
        if (node.getAttribute("unit") == null) {
            node.setAttribute("unit", "s");
        }
        node.setAttribute("period", String.valueOf(Math.round(hours * 3600)));
        if ("fuel".equals(tag)) {
            // Wie iTrain eine neue Lok schreibt (Test1.tcdz).
            if (node.getAttribute("start") == null) {
                node.setAttribute("start", "0");
            }
            if (node.getAttribute("percentage") == null) {
                node.setAttribute("percentage", "100");
            }
            if (node.getAttribute("derived") == null) {
                node.setAttribute("derived", "true");
            }
        }
    }

    private static double hours(XmlNode node, double fallback) {
        if (node == null) {
            return fallback;
        }
        try {
            double seconds = Double.parseDouble(attr(node, "period"));
            return Math.round(seconds / 360.0) / 10.0;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static double lengthMm(XmlNode loco) {
        XmlNode length = loco.findChild("length");
        if (length == null || length.getTextContent() == null) {
            return 0;
        }
        try {
            double value = Double.parseDouble(length.getTextContent().trim());
            String unit = attr(length, "unit");
            return "mm".equals(unit) ? value : Math.round(value * 100) / 10.0;
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static void applyLength(XmlNode loco, double mm) {
        XmlNode length = ensureChild(loco, "length");
        length.setAttribute("unit", "cm");
        length.setTextContent(SystemsObject.formatNumber(mm / 10.0));
    }

    private static boolean hasOption(XmlNode loco, String option) {
        XmlNode options = loco.findChild("options");
        if (options == null) {
            return false;
        }
        for (XmlNode child : options.getChildren()) {
            if ("option".equals(child.getTagName()) && option.equals(trim(child.getTextContent()))) {
                return true;
            }
        }
        return false;
    }

    private static void setOption(XmlNode loco, String option, boolean on) {
        XmlNode options = ensureChild(loco, "options");
        options.getChildren().removeIf(c -> "option".equals(c.getTagName()) && option.equals(trim(c.getTextContent())));
        if (on) {
            XmlNode child = new XmlNode("option");
            child.setTextContent(option);
            options.getChildren().add(child);
        }
    }

    /** Kind-Element holen oder an der richtigen Stelle (siehe {@link #CHILD_ORDER}) anlegen. */
    private static XmlNode ensureChild(XmlNode loco, String tag) {
        XmlNode existing = loco.findChild(tag);
        if (existing != null) {
            return existing;
        }
        XmlNode created = new XmlNode(tag);
        int ownRank = CHILD_ORDER.indexOf(tag);
        int index = loco.getChildren().size();
        for (int i = 0; i < loco.getChildren().size(); i++) {
            int rank = CHILD_ORDER.indexOf(loco.getChildren().get(i).getTagName());
            if (rank > ownRank) {
                index = i;
                break;
            }
        }
        loco.getChildren().add(index, created);
        return created;
    }

    // ------------------------------------------------------------------
    // Bausteine
    // ------------------------------------------------------------------

    /** Trennstrich plus linksbuendige Abschnittsueberschrift im Hauptraster (2 Spalten). */
    private static void addHeader(GridPane grid, int[] row, String text) {
        addHeader(grid, row, text, 2);
    }

    /** Dasselbe im unteren Raster (5 Spalten). */
    private static void addOptsHeader(GridPane opts, int[] row, String text) {
        addHeader(opts, row, text, 5);
    }

    private static void addHeader(GridPane grid, int[] row, String text, int span) {
        Separator separator = new Separator();
        separator.setMaxWidth(Double.MAX_VALUE);
        grid.add(separator, 0, row[0]++, span, 1);
        GridPane.setFillWidth(separator, true);
        Label header = new Label(text);
        header.setStyle("-fx-font-weight: bold;");
        grid.add(header, 0, row[0]++, span, 1);
        // Die erste Spalte des Hauptrasters ist rechtsbuendig (Feldbeschriftungen)
        // - die Ueberschrift soll trotzdem links stehen.
        GridPane.setHalignment(header, HPos.LEFT);
    }

    /**
     * Eine Zeile im unteren Raster: optional ein Haekchen ganz links, dann
     * Beschriftung + Feld, Beschriftung + Feld - jeweils in festen Spalten.
     */
    private static void addPair(GridPane opts, int[] row, Node lead, String label1, Node field1,
            String label2, Node field2) {
        if (lead != null) {
            opts.add(lead, 0, row[0]);
            GridPane.setHalignment(lead, HPos.LEFT);
        }
        opts.add(new Label(label1), 1, row[0]);
        opts.add(field1, 2, row[0]);
        opts.add(new Label(label2), 3, row[0]);
        opts.add(field2, 4, row[0]);
        row[0]++;
    }

    private static void addRow(GridPane grid, int[] row, String label, Node field) {
        grid.add(new Label(label), 0, row[0]);
        grid.add(field, 1, row[0]);
        if (field instanceof Region region && !(field instanceof HBox)) {
            region.setMaxWidth(Double.MAX_VALUE);
        }
        row[0]++;
    }

    private static HBox withUnit(Node field, String unit) {
        HBox box = new HBox(6, field, new Label(unit));
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private static Label readOnly(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-opacity: 0.85;");
        return label;
    }

    private static Spinner<Integer> intSpinner(int min, int max, int value, int step) {
        Spinner<Integer> spinner = new Spinner<>();
        spinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max,
                Math.max(min, Math.min(max, value)), step));
        spinner.setEditable(true);
        spinner.setPrefWidth(100);
        commitOnFocusLoss(spinner);
        return spinner;
    }

    private static Spinner<Double> doubleSpinner(double min, double max, double value, double step) {
        Spinner<Double> spinner = new Spinner<>();
        spinner.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(min, max,
                Math.max(min, Math.min(max, value)), step));
        spinner.setEditable(true);
        spinner.setPrefWidth(100);
        commitOnFocusLoss(spinner);
        return spinner;
    }

    /** Getippte Werte uebernehmen, auch ohne Enter (sonst gingen sie beim Uebernehmen verloren). */
    private static <T> void commitOnFocusLoss(Spinner<T> spinner) {
        spinner.focusedProperty().addListener((obs, was, is) -> {
            if (!is) {
                commit(spinner);
            }
        });
    }

    private static <T> void commit(Spinner<T> spinner) {
        try {
            String text = spinner.getEditor().getText().replace(',', '.');
            T value = spinner.getValueFactory().getConverter().fromString(text);
            if (value != null) {
                spinner.getValueFactory().setValue(value);
            }
        } catch (RuntimeException ex) {
            spinner.getEditor().setText(spinner.getValueFactory().getConverter().toString(spinner.getValue()));
        }
    }

    private static ComboBox<SystemsObject.Choice> choiceBox(List<SystemsObject.Choice> choices, String current) {
        ComboBox<SystemsObject.Choice> box = new ComboBox<>(FXCollections.observableArrayList(choices));
        box.setMaxWidth(Double.MAX_VALUE);
        for (SystemsObject.Choice choice : choices) {
            if (choice.value().equals(current == null ? "" : current)) {
                box.setValue(choice);
                return box;
            }
        }
        box.setValue(choices.get(0));
        return box;
    }

    private static String valueOf(ComboBox<SystemsObject.Choice> box) {
        return box.getValue() == null ? "" : box.getValue().value();
    }

    private static void setAttrOrRemove(XmlNode node, String attribute, String value) {
        if (value == null || value.isEmpty()) {
            node.removeAttribute(attribute);
        } else {
            node.setAttribute(attribute, value);
        }
    }

    private static String attr(XmlNode node, String name) {
        String value = node == null ? null : node.getAttribute(name);
        return value == null ? "" : value;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String firstNonEmpty(String a, String b) {
        return a == null || a.isEmpty() ? b : a;
    }

    private static int parseInt(String text, int fallback) {
        try {
            return (int) Math.round(Double.parseDouble(text.trim()));
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private static double cmToMm(String cm) {
        if (cm == null || cm.isBlank()) {
            return 0;
        }
        try {
            return Math.round(Double.parseDouble(cm.trim().replace(',', '.')) * 100) / 10.0;
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
