package com.example.itrain_import_export;

/**
 * Baut einen vollstaendigen iTrain-Lokomotiveneintrag aus Name, Adresse und
 * einem Protokollcode - herkunftsunabhaengig: benutzt sowohl beim Auslesen
 * einer ESU ECoS ({@link EcosReader}/{@link BidibNodeTreeWindow}) als auch
 * beim Auslesen einer Tams mc2 per FTP ({@link Mc2LocoReader}). Die
 * Protokollcodes ("DCC128", "DCC28", "DCC14", "SX32") sind die der ECoS
 * (siehe {@link #parseLocoType}); {@link Mc2LocoReader} rechnet das
 * Tams-eigene Format ("DCC/126" etc.) dorthin um, damit beide Quellen
 * dieselbe Protokoll-Auswahl im Bearbeitungsfenster ({@code
 * SystemsObjectDialog.LOCOMOTIVE_PROTOCOLS}) teilen.
 * <p>
 * Grundgeruest an echten iTrain-Dateien abgelesen (Scheuerfeld_mc2_604.tcdz,
 * tools/inspect_locos.ps1, kuerzestes Beispiel "Dampfgenerator +"), OHNE
 * {@code <id type="module">} (das ist BiDiB-spezifisch, hier unbekannt).
 */
public final class LocomotiveXmlFactory {

    private LocomotiveXmlFactory() {
    }

    /** Decoder-Angaben einer Lokomotive, aus einem Protokollcode abgeleitet (siehe {@link #parseLocoType}). */
    public record LocoInfo(String protocolCode, String decoderProtocol, int steps, boolean extended) {
    }

    /**
     * Liest einen Protokollcode (z.B. "DCC128", "DCC14", "SX32") in
     * Decoder-Angaben fuer iTrain um. An echten iTrain-Dateien abgelesen
     * (Scheuerfeld_mc2_604.tcdz): {@code <decoder protocol="dcc" steps="126"
     * extended="true"/>} fuer 128 Fahrstufen, {@code steps="28"} als weitere
     * belegte Variante. DCC14 (steps=14, ohne extended) und Selectrix/SX32
     * (protocol "selectrix", 31 Fahrstufen) sind nach demselben Muster
     * gebildet - NICHT an einer echten Datei geprueft (siehe STATUS.md).
     */
    public static LocoInfo parseLocoType(String protocolCode) {
        String p = protocolCode == null ? "" : protocolCode.trim().toUpperCase(java.util.Locale.ROOT);
        if (p.startsWith("SX")) {
            return new LocoInfo(p, "selectrix", 31, false);
        } else if (p.equals("DCC14")) {
            return new LocoInfo(p, "dcc", 14, false);
        } else if (p.equals("DCC28")) {
            return new LocoInfo(p, "dcc", 28, false);
        }
        // DCC128 und alles Unbekannte: Standardfall mit 126 Fahrstufen.
        return new LocoInfo(p.isEmpty() ? "DCC128" : p, "dcc", 126, true);
    }

    /**
     * iTrain-Lokomotiveneintrag - siehe Klassenkommentar fuer die Herkunft
     * der Struktur. Nur Funktion F0 ("Licht vorn/hinten") wird angelegt -
     * weitere Funktionsnamen kennt weder die ECoS- noch die mc2-Abfrage hier
     * (siehe STATUS.md); der Nutzer kann sie in iTrain ergaenzen. Lineare
     * Geschwindigkeitstabelle 0..100 km/h auf die Fahrstufen verteilt, wie
     * bei allen elf Referenzloks beobachtet.
     * (Gilt fuer {@link #createLocomotive} weiter unten.)
     */
    // Vorgaben fuer neue mc2-Lokomotiven (Andre, 13.10.2026).
    /** Traegheit: Stufenverzoegerung in ms. */
    public static final int DEFAULT_INERTIA_MS = 100;
    public static final int DEFAULT_INERTIA_STEP = 1;
    /** Rueckmeldungs-Offset vorn/hinten in mm (iTrain speichert cm). */
    public static final double DEFAULT_OFFSET_MM = 5.0;
    public static final int DEFAULT_REACTION_MS = 200;
    public static final double DEFAULT_MAINTENANCE_H = 40.0;
    public static final double DEFAULT_FUEL_H = 5.0;

    /**
     * Vollstaendiger iTrain-Lokomotiveneintrag fuer eine Lok aus der Tams mc2
     * (seit 13.10.2026) - mit den Vorgaben fuer Traegheit, Rueckmeldungs-
     * Offset (nur "Belegt"), Reaktionsverzoegerung, Wartung und Brennstoff.
     * Element-Reihenfolge und XML-Formen an Test1.tcdz (von iTrain
     * gespeichert) und Kurpfalz_860_V6.tcdz abgelesen: description,
     * interface, decoder, length, options, feedback, delay, acceleration,
     * deceleration, speed-control, functions, fuel, maintenance. Wartung und
     * Brennstoff stehen in Sekunden (40 h = 144000, 5 h = 18000).
     *
     * @param gauge         Spurweite ("n", "m", "e") - Vorgabe ist die globale
     *                      Spurweite der iTrain-Datei
     * @param functionNames Funktionsnummer -&gt; Name; leer = nur F0
     */
    public static XmlNode createMc2Locomotive(String name, int address, LocoInfo info, String interfaceName,
            String gauge, java.util.Map<Integer, String> functionNames) {
        XmlNode loco = new XmlNode("locomotive");
        loco.setAttribute("name", name);
        loco.setAttribute("gauge", gauge == null || gauge.isBlank() ? "n" : gauge);
        loco.setAttribute("cabin", "both");
        loco.setAttribute("polarity", "unknown");
        loco.setAttribute("direction", "forward");

        XmlNode iface = new XmlNode("interface");
        iface.setAttribute("name", interfaceName);
        iface.setAttribute("address", String.valueOf(address));
        loco.getChildren().add(iface);

        XmlNode decoder = new XmlNode("decoder");
        decoder.setAttribute("protocol", info.decoderProtocol());
        decoder.setAttribute("steps", String.valueOf(info.steps()));
        if (info.extended()) {
            decoder.setAttribute("extended", "true");
        }
        loco.getChildren().add(decoder);

        XmlNode length = new XmlNode("length");
        length.setAttribute("unit", "cm");
        length.setTextContent("1.0");
        loco.getChildren().add(length);

        loco.getChildren().add(new XmlNode("options"));

        XmlNode feedback = new XmlNode("feedback");
        feedback.setAttribute("count", "1");
        feedback.setAttribute("unit", "cm");
        XmlNode offset = new XmlNode("offset");
        offset.setAttribute("type", "occupancy");
        offset.setAttribute("front", SystemsObject.formatNumber(DEFAULT_OFFSET_MM / 10.0));
        offset.setAttribute("rear", SystemsObject.formatNumber(DEFAULT_OFFSET_MM / 10.0));
        feedback.getChildren().add(offset);
        loco.getChildren().add(feedback);

        XmlNode delay = new XmlNode("delay");
        delay.setAttribute("value", String.valueOf(DEFAULT_REACTION_MS));
        delay.setAttribute("unit", "ms");
        loco.getChildren().add(delay);

        for (String tag : new String[]{"acceleration", "deceleration"}) {
            XmlNode inertia = new XmlNode(tag);
            inertia.setAttribute("delay", String.valueOf(DEFAULT_INERTIA_MS));
            inertia.setAttribute("unit", "ms");
            inertia.setAttribute("step", String.valueOf(DEFAULT_INERTIA_STEP));
            loco.getChildren().add(inertia);
        }

        int count = info.steps() + 1;
        XmlNode speedControl = new XmlNode("speed-control");
        speedControl.setAttribute("count", String.valueOf(count));
        speedControl.setAttribute("unit", "km_h");
        for (int step = 1; step <= info.steps(); step++) {
            XmlNode speed = new XmlNode("speed");
            speed.setAttribute("step", String.valueOf(step));
            speed.setAttribute("value", SystemsObject.formatNumber(100.0 * step / info.steps()));
            speedControl.getChildren().add(speed);
        }
        loco.getChildren().add(speedControl);

        java.util.Map<Integer, String> names = new java.util.TreeMap<>(functionNames == null
                ? java.util.Map.of() : functionNames);
        names.putIfAbsent(0, "Licht vorn/hinten");
        XmlNode functions = new XmlNode("functions");
        functions.setAttribute("count", String.valueOf(names.size()));
        for (java.util.Map.Entry<Integer, String> entry : names.entrySet()) {
            XmlNode function = new XmlNode("function");
            function.setAttribute("nr", String.valueOf(entry.getKey()));
            function.setAttribute("name", entry.getValue());
            functions.getChildren().add(function);
        }
        loco.getChildren().add(functions);

        XmlNode fuel = new XmlNode("fuel");
        fuel.setAttribute("unit", "s");
        fuel.setAttribute("start", "0");
        fuel.setAttribute("period", String.valueOf(Math.round(DEFAULT_FUEL_H * 3600)));
        fuel.setAttribute("percentage", "100");
        fuel.setAttribute("derived", "true");
        loco.getChildren().add(fuel);

        XmlNode maintenance = new XmlNode("maintenance");
        maintenance.setAttribute("unit", "s");
        maintenance.setAttribute("period", String.valueOf(Math.round(DEFAULT_MAINTENANCE_H * 3600)));
        loco.getChildren().add(maintenance);

        return loco;
    }

    public static XmlNode createLocomotive(String name, int address, LocoInfo info, String interfaceName) {
        XmlNode loco = new XmlNode("locomotive");
        loco.setAttribute("name", name);
        loco.setAttribute("gauge", "n");
        loco.setAttribute("cabin", "both");
        loco.setAttribute("polarity", "normal");
        loco.setAttribute("direction", "forward");

        XmlNode iface = new XmlNode("interface");
        iface.setAttribute("name", interfaceName);
        iface.setAttribute("address", String.valueOf(address));
        loco.getChildren().add(iface);

        XmlNode decoder = new XmlNode("decoder");
        decoder.setAttribute("protocol", info.decoderProtocol());
        decoder.setAttribute("steps", String.valueOf(info.steps()));
        if (info.extended()) {
            decoder.setAttribute("extended", "true");
        }
        loco.getChildren().add(decoder);

        XmlNode length = new XmlNode("length");
        length.setAttribute("unit", "cm");
        length.setTextContent("1.0");
        loco.getChildren().add(length);

        loco.getChildren().add(new XmlNode("options"));

        XmlNode feedback = new XmlNode("feedback");
        feedback.setAttribute("count", "1");
        feedback.setAttribute("unit", "cm");
        XmlNode offset = new XmlNode("offset");
        offset.setAttribute("type", "occupancy");
        offset.setAttribute("front", "0.5");
        offset.setAttribute("rear", "0.5");
        feedback.getChildren().add(offset);
        loco.getChildren().add(feedback);

        XmlNode delay = new XmlNode("delay");
        delay.setAttribute("value", "200");
        delay.setAttribute("unit", "ms");
        loco.getChildren().add(delay);

        loco.getChildren().add(new XmlNode("acceleration"));
        loco.getChildren().add(new XmlNode("deceleration"));

        int count = info.steps() + 1;
        XmlNode speedControl = new XmlNode("speed-control");
        speedControl.setAttribute("count", String.valueOf(count));
        speedControl.setAttribute("unit", "km_h");
        for (int step = 1; step <= info.steps(); step++) {
            XmlNode speed = new XmlNode("speed");
            speed.setAttribute("step", String.valueOf(step));
            speed.setAttribute("value", SystemsObject.formatNumber(100.0 * step / info.steps()));
            speedControl.getChildren().add(speed);
        }
        loco.getChildren().add(speedControl);

        XmlNode functions = new XmlNode("functions");
        functions.setAttribute("count", "1");
        functions.setAttribute("value", "0");
        XmlNode f0 = new XmlNode("function");
        f0.setAttribute("nr", "0");
        f0.setAttribute("name", "Licht vorn/hinten");
        functions.getChildren().add(f0);
        loco.getChildren().add(functions);

        return loco;
    }
}
