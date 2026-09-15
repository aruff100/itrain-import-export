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
     */
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
