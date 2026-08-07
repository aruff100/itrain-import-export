package com.example.itrain_import_export;

import java.util.Locale;
import java.util.Map;

/**
 * Entscheidet anhand des Digital-Protokolls eines Fahrzeugs, ob und wie viele
 * CV-Einträge aus einer Decoder-Vorlage übernommen werden dürfen.
 * <p>
 * Das Protokoll steht in jeder Lokomotive und jedem Wagen im
 * {@code <decoder>}-Element:
 * <pre>
 * &lt;locomotive name="BR24" ...&gt;
 *   &lt;interface name="IFnet" address="24"/&gt;
 *   &lt;decoder protocol="dcc" steps="126"/&gt;      &lt;-- hier
 *   &lt;configuration count="86"&gt; ...
 * </pre>
 * <p>
 * <b>Warum diese Prüfung sein muss:</b> Die Vorlagen enthalten CV-Listen von
 * DCC-Decodern mit teils über hundert Einträgen. Ein Motorola- oder
 * Analog-"Decoder" kennt gar keine CVs, ein SX1-Decoder nur eine Handvoll.
 * Würde eine volle DCC-Vorlage dort hineingeschrieben, stünde in der Datei
 * eine Konfiguration, die das Fahrzeug nicht haben kann - iTrain zeigt sie an,
 * die Werte lassen sich aber nie auf den Decoder übertragen.
 *
 * <h2>Regeln</h2>
 * <table border="1">
 *   <tr><th>protocol</th><th>Verhalten</th></tr>
 *   <tr><td>{@code dcc}</td><td>alle Einträge</td></tr>
 *   <tr><td>{@code fmz}</td><td>alle Einträge</td></tr>
 *   <tr><td>{@code ctc}</td><td>alle Einträge</td></tr>
 *   <tr><td>{@code sx2}</td><td>alle Einträge</td></tr>
 *   <tr><td>{@code sx1}</td><td>höchstens {@value #LIMITED_MAX} Einträge</td></tr>
 *   <tr><td>{@code mot}</td><td>kein Import</td></tr>
 *   <tr><td>{@code multi}</td><td>kein Import</td></tr>
 *   <tr><td>{@code analog}</td><td>kein Import</td></tr>
 * </table>
 * <p>
 * <b>Nicht aufgeführte Protokolle</b> (etwa {@code mfx}) gelten als
 * uneingeschränkt. Das ist bewusst durchlässig statt sperrend: iTrain kann
 * jederzeit weitere Protokolle bekommen, und eine neue Bezeichnung soll dann
 * nicht dazu führen, dass ein völlig normaler Import plötzlich abgewiesen
 * wird.
 * <p>
 * <b>Schreibweisen:</b> Verglichen wird kleingeschrieben und ohne Leerzeichen.
 * Zusätzlich sind naheliegende Varianten hinterlegt ({@code sx}/{@code selectrix}
 * für SX1, {@code mm}/{@code motorola} für Motorola) - denn bestätigt ist
 * bislang nur {@code dcc} aus einer echten Datei. Sollte iTrain eine andere
 * Bezeichnung schreiben als hier erwartet, fällt das Protokoll unter
 * "nicht aufgeführt" und der Import liefe ungebremst durch; die Varianten
 * verkleinern dieses Risiko.
 */
public final class DecoderProtocol {

    /** Tag des Elements, das das Protokoll trägt. */
    public static final String DECODER_TAG = "decoder";

    /** Attribut mit dem Protokollnamen. */
    public static final String PROTOCOL_ATTRIBUTE = "protocol";

    /** Obergrenze für Protokolle, die nur wenige Konfigurationswerte kennen. */
    public static final int LIMITED_MAX = 5;

    /** Was mit einer Vorlage geschehen darf. */
    public enum Rule {
        /** Alle Einträge der Vorlage werden übernommen. */
        ALL,
        /** Höchstens {@link #LIMITED_MAX} Einträge, der Rest wird verworfen. */
        LIMITED,
        /** Kein Import; der Anwender bekommt eine Fehlermeldung. */
        NONE
    }

    /**
     * Protokoll (kleingeschrieben) auf Regel. Die Schlüssel sind die
     * Bezeichnungen, wie sie im {@code protocol}-Attribut stehen, plus die
     * oben erläuterten Schreibvarianten.
     */
    private static final Map<String, Rule> RULES = Map.ofEntries(
            Map.entry("dcc", Rule.ALL),
            Map.entry("fmz", Rule.ALL),
            Map.entry("ctc", Rule.ALL),
            Map.entry("sx2", Rule.ALL),
            Map.entry("selectrix2", Rule.ALL),
            Map.entry("sx1", Rule.LIMITED),
            Map.entry("sx", Rule.LIMITED),
            Map.entry("selectrix", Rule.LIMITED),
            Map.entry("selectrix1", Rule.LIMITED),
            Map.entry("mot", Rule.NONE),
            Map.entry("mm", Rule.NONE),
            Map.entry("motorola", Rule.NONE),
            Map.entry("multi", Rule.NONE),
            Map.entry("analog", Rule.NONE));

    private DecoderProtocol() {
    }

    /**
     * Liest das Protokoll aus einem Fahrzeug-Knoten.
     *
     * @return der Protokollname, oder {@code null}, wenn das Fahrzeug kein
     *         {@code <decoder>}-Element hat oder dort kein Protokoll steht
     */
    public static String read(XmlNode vehicle) {
        if (vehicle == null) {
            return null;
        }
        XmlNode decoder = vehicle.findChild(DECODER_TAG);
        if (decoder == null) {
            return null;
        }
        String protocol = decoder.getAttribute(PROTOCOL_ATTRIBUTE);
        return protocol == null || protocol.isBlank() ? null : protocol.trim();
    }

    /**
     * Regel für ein Protokoll. Ein unbekanntes oder fehlendes Protokoll
     * liefert {@link Rule#ALL} - siehe Klassenkommentar.
     */
    public static Rule ruleFor(String protocol) {
        if (protocol == null || protocol.isBlank()) {
            return Rule.ALL;
        }
        String key = protocol.trim().toLowerCase(Locale.ROOT).replace(" ", "").replace("-", "");
        return RULES.getOrDefault(key, Rule.ALL);
    }

    /** Bequemlichkeit: Regel direkt aus dem Fahrzeug-Knoten. */
    public static Rule ruleForVehicle(XmlNode vehicle) {
        return ruleFor(read(vehicle));
    }

    /**
     * Wie viele Einträge das Protokoll höchstens aufnimmt.
     *
     * @return {@link Integer#MAX_VALUE} bei {@link Rule#ALL},
     *         {@value #LIMITED_MAX} bei {@link Rule#LIMITED}, sonst 0
     */
    public static int maxParameters(String protocol) {
        return switch (ruleFor(protocol)) {
            case ALL -> Integer.MAX_VALUE;
            case LIMITED -> LIMITED_MAX;
            case NONE -> 0;
        };
    }

    /**
     * Protokollname für Meldungen an den Anwender. Fehlt das Protokoll, wird
     * ein sprachabhängiger Platzhalter geliefert statt eines leeren Feldes -
     * "Protokoll: " ohne Wert wirkte wie ein Anzeigefehler.
     */
    public static String displayName(String protocol) {
        if (protocol == null || protocol.isBlank()) {
            return I18n.getInstance().t("decoderProtocol.unknown");
        }
        return protocol.trim();
    }
}
