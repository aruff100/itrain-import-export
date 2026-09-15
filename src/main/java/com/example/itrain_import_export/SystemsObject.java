package com.example.itrain_import_export;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Eine Zeile der Tabellen im Systeme-Fenster: ein vorbereitetes iTrain-Objekt
 * (Schnittstelle, Booster, Rückmelder oder Zubehör), entstanden aus der
 * Auswahl im Knotenbaum ({@link BidibNodeTreeWindow}) oder geladen aus einer
 * System-Datei ({@link BidibSystemFile}).
 * <p>
 * Links steht der unveränderliche Name aus dem BiDiB-System
 * ({@link #getBidibName()}), rechts die in iTrain benötigten Angaben. Die
 * Tabelle zeigt sie nur; geändert werden sie im Bearbeitungsfenster
 * ({@link SystemsObjectDialog}). Jede Änderung wird sofort in den zugrunde
 * liegenden iTrain-Eintrag ({@link #getXml()}) übernommen - der ist es, der
 * beim Export in die CSV geschrieben und später in die iTrain-Datei
 * importiert wird.
 * <p>
 * Die XML-Formen sind an echten, von iTrain geschriebenen Dateien abgelesen
 * (Scheuerfeld_mc2_604.tcdz, Kellerbahn_20241221): Rückmelder
 * {@code <feedback name type="occupancy|turnout|button" side="left|right"
 * selected>} mit Kindern description, id, {@code <length unit="cm">175.0</length>},
 * interface, options; Weiche {@code <turnout name type initial-state state>};
 * Booster {@code <booster name type="bidib" state="off">} mit
 * {@code <limits>}-Kanälen voltage_track (5 Stufen), current_main (3),
 * temperature (3).
 */
public final class SystemsObject {

    public static final String CATEGORY_INTERFACES = "interfaces";
    public static final String CATEGORY_FEEDBACKS = "feedbacks";
    public static final String CATEGORY_ACCESSORIES = "accessories";
    public static final String CATEGORY_BOOSTERS = "boosters";
    /** Lokomotiven - bisher nur beim Auslesen einer ECoS (siehe EcosReader/EcosItemFactory). */
    public static final String CATEGORY_LOCOMOTIVES = "locomotives";

    /** Reihenfolge der Abschnitte im Systeme-Fenster - wie iTrain sie führt. */
    public static final List<String> CATEGORY_ORDER = List.of(CATEGORY_INTERFACES, CATEGORY_BOOSTERS,
            CATEGORY_FEEDBACKS, CATEGORY_ACCESSORIES, CATEGORY_LOCOMOTIVES);

    /** Eine Wahl fuer eine Auswahlbox: Wert, wie iTrain ihn schreibt, plus Anzeigetext. */
    public record Choice(String value, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Weichenbauformen, wie iTrain sie im type-Attribut schreibt - in der
     * Reihenfolge der iTrain-Auswahlbox. left, right, curved-left/-right,
     * y-type, three-way, cross-fixed, cross-double-slip an echten Dateien abgelesen;
     * five-way und cross-single-slip nach demselben Muster gebildet (nicht
     * belegt, siehe STATUS.md).
     */
    public static final List<String> TURNOUT_TYPES = List.of("left", "right", "y-type", "three-way",
            "curved-left", "curved-right", "five-way", "cross-fixed", "cross-single-slip", "cross-double-slip");

    /**
     * Rueckmelder-Arten (type-Attribut), in der Reihenfolge der
     * iTrain-Auswahlbox: Belegt, Reedkontakt, Hall-Sensor, Lichtschranke,
     * Taste, Weiche, Wert (8-bit), Sonstiges. occupancy, turnout und button
     * an echten Dateien abgelesen; reed, hall, light, value und other sind
     * nach dem Muster gebildet und NICHT belegt (siehe STATUS.md).
     */
    public static final List<String> FEEDBACK_TYPES =
            List.of("occupancy", "reed", "hall", "light", "button", "turnout", "value", "other");

    private final String category;
    private final String bidibName;
    private final StringProperty type = new SimpleStringProperty("");
    private final StringProperty address = new SimpleStringProperty("");
    private final StringProperty name = new SimpleStringProperty("");
    private final BooleanProperty selected = new SimpleBooleanProperty(false);
    private final StringProperty description = new SimpleStringProperty("");
    private final StringProperty length = new SimpleStringProperty("");
    private final StringProperty interfaceName = new SimpleStringProperty("");
    private final XmlNode xml;
    /** Kennung des BiDiB-Knotens, 0 wenn unbekannt. */
    private long nodeUniqueId;
    /**
     * true, solange der Typ noch die Voreinstellung vom Auslesen ist (bei
     * Zubehoer "Weiche links") und nicht vom Nutzer festgelegt wurde - die
     * Tabelle zeigt dann ein "*" vor dem Typ.
     */
    private boolean typeDefault;

    public SystemsObject(String category, String type, String address, String bidibName, String name,
            boolean selected, String description, String length, String interfaceName, XmlNode xml) {
        this.category = category;
        this.bidibName = nz(bidibName);
        this.xml = xml;
        this.type.set(nz(type));
        this.address.set(nz(address));
        this.name.set(nz(name));
        this.selected.set(CATEGORY_INTERFACES.equals(category) || selected);
        this.description.set(nz(description));
        this.length.set(nz(length));
        this.interfaceName.set(nz(interfaceName));
        // Aenderungen in den Eintrag durchreichen.
        this.name.addListener((obs, old, value) -> applyName(nz(value)));
        this.selected.addListener((obs, old, value) -> applySelected(Boolean.TRUE.equals(value)));
        this.type.addListener((obs, old, value) -> applyType(nz(value)));
        this.description.addListener((obs, old, value) -> applyDescription(nz(value)));
        this.length.addListener((obs, old, value) -> applyLength(nz(value)));
        this.interfaceName.addListener((obs, old, value) -> applyInterfaceName(nz(value)));
    }

    /** Aus einer gespeicherten Zeile (siehe {@link BidibSystemFile.ObjectRecord}). */
    public static SystemsObject fromRecord(BidibSystemFile.ObjectRecord record) {
        XmlNode node = null;
        if (record.xml() != null && !record.xml().isBlank()) {
            try {
                node = TcdDocument.xmlStringToNode(record.xml());
            } catch (Exception ex) {
                node = null;
            }
        }
        SystemsObject object = new SystemsObject(record.category(), record.type(), record.address(),
                record.bidibName(), record.name(), record.selected(), record.description(), record.length(),
                record.interfaceName(), node);
        object.typeDefault = record.typeDefault();
        return object;
    }

    public BidibSystemFile.ObjectRecord toRecord() {
        String xmlText = "";
        if (xml != null) {
            try {
                xmlText = TcdDocument.nodeToXmlString(xml);
            } catch (Exception ex) {
                xmlText = "";
            }
        }
        return new BidibSystemFile.ObjectRecord(category, getType(), getAddress(), bidibName, getName(),
                isSelected(), getDescription(), getLength(), getInterfaceName(), xmlText, typeDefault);
    }

    public boolean isTypeDefault() {
        return typeDefault;
    }

    public void setTypeDefault(boolean typeDefault) {
        this.typeDefault = typeDefault;
    }

    public String getCategory() {
        return category;
    }

    public boolean isInterface() {
        return CATEGORY_INTERFACES.equals(category);
    }

    public String getBidibName() {
        return bidibName;
    }

    public XmlNode getXml() {
        return xml;
    }

    public long getNodeUniqueId() {
        return nodeUniqueId;
    }

    public void setNodeUniqueId(long nodeUniqueId) {
        this.nodeUniqueId = nodeUniqueId;
    }

    public StringProperty typeProperty() {
        return type;
    }

    public String getType() {
        return type.get();
    }

    public void setType(String value) {
        type.set(nz(value));
    }

    public StringProperty addressProperty() {
        return address;
    }

    /** Nur bei Lokomotiven editierbar - schreibt die Adresse ins interface-Kind. */
    public void setAddress(String value) {
        address.set(nz(value));
        if (CATEGORY_LOCOMOTIVES.equals(category)) {
            setChildAttribute("interface", "address", value);
        }
    }

    public String getAddress() {
        return address.get();
    }

    public StringProperty nameProperty() {
        return name;
    }

    public String getName() {
        return name.get();
    }

    public void setName(String value) {
        name.set(nz(value));
    }

    public BooleanProperty selectedProperty() {
        return selected;
    }

    public boolean isSelected() {
        return selected.get();
    }

    public void setSelected(boolean value) {
        selected.set(isInterface() || value);
    }

    public StringProperty descriptionProperty() {
        return description;
    }

    public String getDescription() {
        return description.get();
    }

    public void setDescription(String value) {
        description.set(nz(value));
    }

    public StringProperty lengthProperty() {
        return length;
    }

    public String getLength() {
        return length.get();
    }

    public void setLength(String value) {
        length.set(nz(value));
    }

    public StringProperty interfaceNameProperty() {
        return interfaceName;
    }

    public String getInterfaceName() {
        return interfaceName.get();
    }

    public void setInterfaceName(String value) {
        interfaceName.set(nz(value));
    }

    // ------------------------------------------------------------------
    // Anzeigetexte und Auswahlen
    // ------------------------------------------------------------------

    /** Anzeigetext des Typs fuer die Tabelle. */
    public String typeLabel(I18n i18n) {
        switch (category) {
            case CATEGORY_ACCESSORIES:
                return (typeDefault ? "* " : "") + turnoutTypeLabel(getType(), i18n);
            case CATEGORY_FEEDBACKS:
                return feedbackTypeLabel(getType(), i18n);
            case CATEGORY_INTERFACES:
                return "bidib".equals(getType()) ? "bidib (USB)" : getType();
            default:
                return getType();
        }
    }

    public static String turnoutTypeLabel(String type, I18n i18n) {
        switch (nz(type)) {
            case "left":
                return i18n.t("bidib.accessoryTurnoutLeft");
            case "right":
                return i18n.t("bidib.accessoryTurnoutRight");
            case "y-type":
                return i18n.t("bidib.accessoryY");
            case "three-way":
                return i18n.t("bidib.accessoryThreeWay");
            case "curved-left":
                return i18n.t("bidib.accessoryCurvedLeft");
            case "curved-right":
                return i18n.t("bidib.accessoryCurvedRight");
            case "five-way":
                return i18n.t("bidib.accessoryFiveWay");
            case "cross-fixed":
                return i18n.t("bidib.accessoryCrossing");
            case "cross-single-slip":
                return i18n.t("bidib.accessorySingleSlip");
            case "cross-double-slip":
                return i18n.t("bidib.accessoryDoubleSlip");
            default:
                return nz(type);
        }
    }

    public static String feedbackTypeLabel(String type, I18n i18n) {
        switch (nz(type)) {
            case "occupancy":
                return i18n.t("bidib.feedbackTypeOccupancy");
            case "reed":
                return i18n.t("bidib.feedbackTypeReed");
            case "hall":
                return i18n.t("bidib.feedbackTypeHall");
            case "light":
                return i18n.t("bidib.feedbackTypeLight");
            case "button":
                return i18n.t("bidib.feedbackTypeButton");
            case "turnout":
                return i18n.t("bidib.feedbackTypeTurnout");
            case "value":
                return i18n.t("bidib.feedbackTypeValue");
            case "other":
                return i18n.t("bidib.feedbackTypeOther");
            default:
                return nz(type);
        }
    }

    public static List<Choice> turnoutTypeChoices(I18n i18n) {
        List<Choice> result = new ArrayList<>();
        for (String t : TURNOUT_TYPES) {
            result.add(new Choice(t, turnoutTypeLabel(t, i18n)));
        }
        return result;
    }

    public static List<Choice> feedbackTypeChoices(I18n i18n) {
        List<Choice> result = new ArrayList<>();
        for (String t : FEEDBACK_TYPES) {
            result.add(new Choice(t, feedbackTypeLabel(t, i18n)));
        }
        return result;
    }

    /**
     * Die Stellungen einer Weichenbauform, wie iTrain sie in state /
     * initial-state schreibt (an echten Dateien abgelesen; five-way und
     * cross-single-slip nach dem Muster ergaenzt).
     */
    public static List<String> turnoutStates(String type) {
        switch (nz(type)) {
            case "curved-left":
                return List.of("branch_left", "branch_far_left");
            case "curved-right":
                return List.of("branch_right", "branch_far_right");
            case "y-type":
                return List.of("branch_left", "branch_right");
            case "three-way":
                return List.of("straight", "branch_left", "branch_right");
            case "five-way":
                return List.of("straight", "branch_left", "branch_right", "branch_far_left", "branch_far_right");
            case "cross-fixed":
                return List.of("straight_ac", "straight_bd");
            case "cross-single-slip":
                return List.of("straight_ac", "straight_bd", "branch_ad");
            case "cross-double-slip":
                return List.of("straight_ac", "straight_bd", "branch_ad", "branch_bc");
            default:
                return List.of("straight", "branch");
        }
    }

    public static String turnoutStateLabel(String state, I18n i18n) {
        switch (nz(state)) {
            case "straight":
                return i18n.t("bidib.stateStraight");
            case "branch":
                return i18n.t("bidib.stateBranch");
            case "branch_left":
                return i18n.t("bidib.stateBranchLeft");
            case "branch_right":
                return i18n.t("bidib.stateBranchRight");
            case "branch_far_left":
                return i18n.t("bidib.stateBranchFarLeft");
            case "branch_far_right":
                return i18n.t("bidib.stateBranchFarRight");
            case "straight_ac":
                return i18n.t("bidib.stateStraightAc");
            case "straight_bd":
                return i18n.t("bidib.stateStraightBd");
            case "branch_ad":
                return i18n.t("bidib.stateBranchAd");
            case "branch_bc":
                return i18n.t("bidib.stateBranchBc");
            default:
                return nz(state);
        }
    }

    public static List<Choice> turnoutStateChoices(String type, I18n i18n) {
        List<Choice> result = new ArrayList<>();
        for (String s : turnoutStates(type)) {
            result.add(new Choice(s, turnoutStateLabel(s, i18n)));
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Zugriff auf Einzelheiten im Eintrag (Bearbeitungsfenster)
    // ------------------------------------------------------------------

    /** Attribut des Eintrags oder "" - fuer side, initial-state, state, ... */
    public String attribute(String name) {
        return xml == null ? "" : nz(xml.getAttribute(name));
    }

    /** Attribut setzen; leer = Attribut entfernen. */
    public void setAttribute(String name, String value) {
        if (xml == null) {
            return;
        }
        if (value == null || value.isBlank()) {
            xml.removeAttribute(name);
        } else {
            xml.setAttribute(name, value.trim());
        }
    }

    /** Attribut eines Kind-Elements (z.B. socket/host), oder "". */
    public String childAttribute(String childTag, String attribute) {
        XmlNode child = xml == null ? null : xml.findChild(childTag);
        return child == null ? "" : nz(child.getAttribute(attribute));
    }

    public void setChildAttribute(String childTag, String attribute, String value) {
        XmlNode child = xml == null ? null : xml.findChild(childTag);
        if (child != null) {
            child.setAttribute(attribute, nz(value).trim());
        }
    }

    /**
     * Weichenstellungen nachziehen, wenn die Bauform wechselt: state und
     * initial-state muessen zu den Stellungen der neuen Bauform passen,
     * sonst weist iTrain den Eintrag ab.
     */
    public void applyTurnoutType(String newType, String initialState) {
        if (xml == null || !CATEGORY_ACCESSORIES.equals(category)) {
            return;
        }
        xml.setTagName("turnout");
        type.set(nz(newType));
        typeDefault = false;
        List<String> states = turnoutStates(newType);
        String initial = states.contains(initialState) ? initialState : null;
        setAttribute("initial-state", initial);
        String state = attribute("state");
        if (!states.contains(state)) {
            xml.setAttribute("state", initial != null ? initial : states.get(0));
        }
    }

    /**
     * Fahrstufen-Protokoll einer Lokomotive wechseln: {@code <decoder>} und
     * {@code <speed-control>} werden komplett neu geschrieben (siehe
     * {@link LocomotiveXmlFactory#createLocomotive}) - alles andere (Name,
     * Interface, Funktionen) bleibt erhalten.
     */
    public void applyLocomotiveProtocol(String protocolCode) {
        if (xml == null || !CATEGORY_LOCOMOTIVES.equals(category)) {
            return;
        }
        LocomotiveXmlFactory.LocoInfo info = LocomotiveXmlFactory.parseLocoType(protocolCode);
        type.set(info.protocolCode());
        typeDefault = false;
        XmlNode decoder = xml.findChild("decoder");
        if (decoder == null) {
            decoder = new XmlNode("decoder");
            int idx = xml.getChildren().indexOf(xml.findChild("length"));
            xml.getChildren().add(idx >= 0 ? idx : 1, decoder);
        }
        decoder.getAttributes().clear();
        decoder.setAttribute("protocol", info.decoderProtocol());
        decoder.setAttribute("steps", String.valueOf(info.steps()));
        if (info.extended()) {
            decoder.setAttribute("extended", "true");
        }
        XmlNode speedControl = xml.findChild("speed-control");
        if (speedControl == null) {
            speedControl = new XmlNode("speed-control");
            xml.getChildren().add(speedControl);
        }
        speedControl.getChildren().clear();
        int count = info.steps() + 1;
        speedControl.setAttribute("count", String.valueOf(count));
        speedControl.setAttribute("unit", "km_h");
        for (int step = 1; step <= info.steps(); step++) {
            XmlNode speed = new XmlNode("speed");
            speed.setAttribute("step", String.valueOf(step));
            speed.setAttribute("value", formatNumber(100.0 * step / info.steps()));
            speedControl.getChildren().add(speed);
        }
    }

    /**
     * Grenzwerte eines Boosters, so wie iTrain sie schreibt (an drei echten
     * Boostern abgelesen): Gleisspannung v als 5 Stufen v-4 / v-2 / v /
     * v+1,5 / v+3 (emergency, warning, good, warning, emergency);
     * Hauptgleis-Strom c als 0,6c / 0,8c / c (good, warning, emergency);
     * Temperatur t als t / t+20 / 2t (cool, warm, hot). null = Kanal weg.
     */
    public void applyBoosterLimits(Double trackVoltage, Double mainCurrent, Double temperature) {
        if (xml == null || !CATEGORY_BOOSTERS.equals(category)) {
            return;
        }
        XmlNode limits = xml.findChild("limits");
        if (limits == null) {
            limits = new XmlNode("limits");
            xml.getChildren().add(limits);
        }
        limits.getChildren().clear();
        if (trackVoltage != null) {
            double v = trackVoltage;
            limits.getChildren().add(channel("voltage_track", new String[]{"emergency", "warning", "good",
                    "warning", "emergency"}, new double[]{v - 4, v - 2, v, v + 1.5, v + 3}));
        }
        if (mainCurrent != null) {
            double c = mainCurrent;
            limits.getChildren().add(channel("current_main", new String[]{"good", "warning", "emergency"},
                    new double[]{0.6 * c, 0.8 * c, c}));
        }
        if (temperature != null) {
            double t = temperature;
            limits.getChildren().add(channel("temperature", new String[]{"cool", "warm", "hot"},
                    new double[]{t, t + 20, 2 * t}));
        }
        limits.setAttribute("count", String.valueOf(limits.getChildren().size()));
        if (limits.getChildren().isEmpty()) {
            xml.getChildren().remove(limits);
        }
    }

    /** Der "Maximum"-Wert eines Booster-Kanals zurueckgerechnet (good bzw. cool bzw. emergency), oder null. */
    public Double boosterLimit(String channelType) {
        XmlNode limits = xml == null ? null : xml.findChild("limits");
        if (limits == null) {
            return null;
        }
        for (XmlNode channel : limits.getChildren()) {
            if (!"channel".equals(channel.getTagName()) || !channelType.equals(channel.getAttribute("type"))) {
                continue;
            }
            String wanted = "temperature".equals(channelType) ? "cool"
                    : "current_main".equals(channelType) ? "emergency" : "good";
            for (XmlNode limit : channel.getChildren()) {
                if (wanted.equals(limit.getAttribute("quality"))) {
                    try {
                        return Double.parseDouble(nz(limit.getAttribute("value")));
                    } catch (NumberFormatException ex) {
                        return null;
                    }
                }
            }
            // Bei current_main ist "emergency" der Hoechstwert - erster
            // Treffer oben; hier nur noch der Notnagel.
            return null;
        }
        return null;
    }

    private static XmlNode channel(String type, String[] qualities, double[] values) {
        XmlNode channel = new XmlNode("channel");
        channel.setAttribute("type", type);
        channel.setAttribute("min", "0.0");
        channel.setAttribute("count", String.valueOf(values.length));
        for (int i = 0; i < values.length; i++) {
            XmlNode limit = new XmlNode("limit");
            limit.setAttribute("quality", qualities[i]);
            limit.setAttribute("value", formatNumber(values[i]));
            channel.getChildren().add(limit);
        }
        return channel;
    }

    /** Zahl so schreiben, wie iTrain sie schreibt: mindestens eine Nachkommastelle, Punkt. */
    public static String formatNumber(double value) {
        if (value == Math.rint(value)) {
            return String.format(Locale.ROOT, "%.1f", value);
        }
        String s = String.format(Locale.ROOT, "%.3f", value);
        s = s.replaceAll("0+$", "");
        return s.endsWith(".") ? s + "0" : s;
    }

    // ------------------------------------------------------------------
    // Durchreichen in den Eintrag
    // ------------------------------------------------------------------

    private void applyName(String value) {
        if (xml != null) {
            xml.setAttribute("name", value);
        }
    }

    private void applySelected(boolean value) {
        // "selected" schreibt iTrain nur bei Rueckmeldern.
        if (xml != null && "feedback".equals(xml.getTagName())) {
            xml.setAttribute("selected", value ? "true" : "false");
        }
    }

    private void applyType(String value) {
        if (xml != null && !value.isEmpty()) {
            xml.setAttribute("type", value);
        }
    }

    private void applyDescription(String value) {
        if (xml == null) {
            return;
        }
        XmlNode existing = xml.findChild("description");
        if (value.isEmpty()) {
            if (existing != null) {
                xml.getChildren().remove(existing);
            }
            return;
        }
        if (existing == null) {
            existing = new XmlNode("description");
            xml.getChildren().add(0, existing);
        }
        existing.setTextContent(value);
    }

    /**
     * Laenge nur bei Rueckmeldern, als Kind {@code <length unit="cm">}
     * VOR dem interface-Element - so steht es in den iTrain-Dateien. Leer
     * = Element weg.
     */
    private void applyLength(String value) {
        if (xml == null || !"feedback".equals(xml.getTagName())) {
            return;
        }
        XmlNode existing = xml.findChild("length");
        String trimmed = value.trim().replace(',', '.');
        if (trimmed.isEmpty()) {
            if (existing != null) {
                xml.getChildren().remove(existing);
            }
            return;
        }
        double number;
        try {
            number = Double.parseDouble(trimmed);
        } catch (NumberFormatException ex) {
            return;
        }
        if (existing == null) {
            existing = new XmlNode("length");
            int index = xml.getChildren().size();
            for (int i = 0; i < xml.getChildren().size(); i++) {
                String tag = xml.getChildren().get(i).getTagName();
                if ("interface".equals(tag) || "options".equals(tag)) {
                    index = i;
                    break;
                }
            }
            xml.getChildren().add(index, existing);
        }
        existing.setAttribute("unit", "cm");
        existing.setTextContent(formatNumber(number));
    }

    private void applyInterfaceName(String value) {
        if (xml == null) {
            return;
        }
        if ("interface".equals(xml.getTagName())) {
            xml.setAttribute("name", value);
            name.set(value);
            return;
        }
        XmlNode reference = xml.findChild("interface");
        if (reference != null) {
            reference.setAttribute("name", value);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
