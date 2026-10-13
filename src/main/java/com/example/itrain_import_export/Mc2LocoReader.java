package com.example.itrain_import_export;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Liest die Lokomotiven einer Tams mc2 per FTP aus (Andre am 15.09.: Ordner
 * "config", Datei "loco.ini", siehe {@link Mc2FtpClient}) und macht daraus
 * iTrain-Lokomotiveneintraege - das Gegenstueck zu {@link EcosReader} fuer
 * BiDiB-Anlagen mit einer mc2.
 * <p>
 * Aufbau von loco.ini (Handtest 15.09., 21 KB, 37 Lokomotiven + hunderte
 * leerer Mehrfachtraktions-Slots): je Lokomotive ein Abschnitt
 * {@code [L<Adresse>]} mit Schluesseln {@code fmt} (Format/Fahrstufen, z.B.
 * "DCC/126", "DCC/28", "DCC/14"; "DCC/SDF" bei Adressen, die am Regler
 * angewaehlt aber nie konfiguriert wurden - Fahrstufen dafuer unbekannt,
 * Vorgabe wie DCC/126), optional {@code name}. Weitere Schluessel
 * (config/vid/uid/shortname/vendor/product/HW/FW/icon/image/AdrReq) sind
 * Geraete-/Decodererkennung der mc2-Weboberflaeche, fuer den iTrain-Import
 * ohne Bedeutung. Abschnitte {@code [T<Nr>]} (nach {@code [Consists]}) sind
 * Mehrfachtraktions-Slots - keine Lokomotiven, werden uebersprungen; ebenso
 * jede DCC-Adressierung von Rueckmeldern (S88 usw.) liegt ausserhalb dieser
 * Datei bzw. wird hier nicht ausgewertet (Andre: "werden nicht benoetigt").
 */
public final class Mc2LocoReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(Mc2LocoReader.class);
    private static final String CONFIG_DIR = "/config";
    private static final String LOCO_FILE = "loco.ini";
    private static final Pattern SECTION = Pattern.compile("^\\[(.+)]$");
    private static final Pattern KEY_VALUE = Pattern.compile("^([A-Za-z0-9_()\\[\\].]+)\\s*=\\s*(.*)$");

    private Mc2LocoReader() {
    }

    /**
     * Eine Lokomotive aus loco.ini: Adresse (aus dem Abschnittsnamen), Name
     * (kann fehlen), Format, UID ("" wenn keine, sonst 8-stellig hex wie in
     * iTrain angezeigt), DCC-A-Kennung ({@code config = DCCA}) und die
     * Funktionssymbole ({@code icon(n) = Nummer}, Nummer = Position in der
     * Symbolliste der mc2-Weboberflaeche, siehe {@link #loadIconNames}).
     */
    public record LocoEntry(int address, String name, String fmt, String uid, boolean dccA,
            Map<Integer, Integer> icons) {
    }

    private static final String HTML_DIR = "/html";
    private static final String TEXT_FILE = "texte.js";
    private static final Pattern ICON_KEY = Pattern.compile("icon\\((\\d+)\\)");

    /**
     * true, wenn sich unter dem Host per FTP {@code config/loco.ini} lesen
     * laesst - entscheidet, ob der Knopf "mc2 Lokomotiven auslesen"
     * erscheint. Blockierend (Netzwerk) - nur aus einem Hintergrundfaden
     * aufrufen.
     */
    public static boolean isAvailable(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        try {
            Mc2Http.get(host, CONFIG_DIR + "/" + LOCO_FILE);
            return true;
        } catch (IOException ex) {
            LOGGER.debug("mc2-Lokdaten unter {} nicht lesbar: {}", host, ex.getMessage());
            return false;
        }
    }

    /** Nur das Einlesen und Zerlegen - fuer Handtests und die eigentliche Auslese-Methode. */
    static List<LocoEntry> parse(String text) {
        List<LocoEntry> result = new ArrayList<>();
        String section = null;
        Map<String, String> fields = new LinkedHashMap<>();
        for (String rawLine : text.split("\r?\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith(";") || line.startsWith("#")) {
                continue;
            }
            Matcher sectionMatch = SECTION.matcher(line);
            if (sectionMatch.matches()) {
                flushSection(result, section, fields);
                section = sectionMatch.group(1);
                fields = new LinkedHashMap<>();
                continue;
            }
            Matcher kv = KEY_VALUE.matcher(line);
            if (kv.matches()) {
                fields.put(kv.group(1).toLowerCase(Locale.ROOT), kv.group(2).trim());
            }
        }
        flushSection(result, section, fields);
        return result;
    }

    private static void flushSection(List<LocoEntry> result, String section, Map<String, String> fields) {
        // Nur "L<Adresse>" ist eine Lokomotive - "T<Nr>" (nach [Consists])
        // sind Mehrfachtraktions-Slots, "Consists" selbst eine leere
        // Ueberschrift ohne Adresse.
        if (section == null || !section.matches("L\\d+")) {
            return;
        }
        int address;
        try {
            address = Integer.parseInt(section.substring(1));
        } catch (NumberFormatException ex) {
            return;
        }
        Map<Integer, Integer> icons = new java.util.TreeMap<>();
        for (Map.Entry<String, String> field : fields.entrySet()) {
            Matcher m = ICON_KEY.matcher(field.getKey());
            if (m.matches()) {
                try {
                    icons.put(Integer.parseInt(m.group(1)), Integer.parseInt(field.getValue().trim()));
                } catch (NumberFormatException ignored) {
                    // unlesbarer Eintrag - Funktion bleibt ohne Namen
                }
            }
        }
        result.add(new LocoEntry(address, fields.get("name"), fields.get("fmt"), formatUid(fields.get("uid")),
                "DCCA".equalsIgnoreCase(nzTrim(fields.get("config"))), icons));
    }

    /** "0x75e0807d" -&gt; "75E0807D", "0x2000" -&gt; "00002000" (wie iTrain die UID anzeigt); "" wenn keine. */
    static String formatUid(String raw) {
        String text = nzTrim(raw);
        if (text.isEmpty()) {
            return "";
        }
        String hex = text.toLowerCase(Locale.ROOT).startsWith("0x") ? text.substring(2) : text;
        try {
            return String.format(Locale.ROOT, "%08X", Long.parseUnsignedLong(hex, 16));
        } catch (NumberFormatException ex) {
            return text.toUpperCase(Locale.ROOT);
        }
    }

    private static String nzTrim(String s) {
        return s == null ? "" : s.trim();
    }

    /**
     * Namen der Funktionssymbole aus der Weboberflaeche der mc2
     * ({@code /html/texte.js}, Liste {@code var icons = [ [Deutsch], [English],
     * [Français], [Nederlands] ]}, Eintrag 0 = "---" = kein Symbol). Sprache
     * passend zur Programmsprache, sonst Englisch. Leere Liste, wenn die
     * Datei nicht lesbar ist - die Funktionen bekommen dann keine Namen.
     */
    static List<String> loadIconNames(String host) {
        try {
            return parseIconNames(Mc2Http.get(host, "/" + TEXT_FILE),
                    I18n.getInstance().getCurrentLanguage());
        } catch (IOException | RuntimeException ex) {
            LOGGER.info("Funktionsnamen der mc2 {} nicht lesbar: {}", host, ex.getMessage());
            return List.of();
        }
    }

    static List<String> parseIconNames(String text, String language) {
        List<List<String>> blocks = parseIconBlocks(text);
        if (blocks.isEmpty()) {
            return List.of();
        }
        int index;
        switch (language == null ? "" : language) {
            case "de":
                index = 0;
                break;
            case "fr":
                index = 2;
                break;
            case "nl":
                index = 3;
                break;
            default:
                index = 1;
                break;
        }
        return blocks.get(Math.min(index, blocks.size() - 1));
    }

    /** Alle Sprach-Bloecke der Symbolliste (Deutsch, Englisch, Franzoesisch, Niederlaendisch). */
    static List<List<String>> parseIconBlocks(String text) {
        int start = text.indexOf("var icons");
        if (start < 0) {
            return List.of();
        }
        int open = text.indexOf('[', start);
        // Die Sprach-Bloecke einzeln einsammeln: jeder ist "[ ... ]" mit
        // Zeichenketten in Anfuehrungszeichen, Kommentare dazwischen.
        List<List<String>> blocks = new ArrayList<>();
        int pos = open + 1;
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c == ']') {
                break; // Ende der aeusseren Liste
            }
            if (c == '/' && pos + 1 < text.length() && text.charAt(pos + 1) == '*') {
                int end = text.indexOf("*/", pos + 2);
                pos = end < 0 ? text.length() : end + 2;
                continue;
            }
            if (c == '/' && pos + 1 < text.length() && text.charAt(pos + 1) == '/') {
                int end = text.indexOf('\n', pos);
                pos = end < 0 ? text.length() : end + 1;
                continue;
            }
            if (c == '[') {
                List<String> block = new ArrayList<>();
                pos++;
                while (pos < text.length() && text.charAt(pos) != ']') {
                    char d = text.charAt(pos);
                    if (d == '/' && pos + 1 < text.length() && text.charAt(pos + 1) == '*') {
                        int end = text.indexOf("*/", pos + 2);
                        pos = end < 0 ? text.length() : end + 2;
                    } else if (d == '/' && pos + 1 < text.length() && text.charAt(pos + 1) == '/') {
                        int end = text.indexOf('\n', pos);
                        pos = end < 0 ? text.length() : end + 1;
                    } else if (d == '"') {
                        StringBuilder value = new StringBuilder();
                        pos++;
                        while (pos < text.length() && text.charAt(pos) != '"') {
                            if (text.charAt(pos) == '\\' && pos + 1 < text.length()) {
                                pos++;
                            }
                            value.append(text.charAt(pos));
                            pos++;
                        }
                        block.add(value.toString());
                        pos++;
                    } else {
                        pos++;
                    }
                }
                blocks.add(block);
            }
            pos++;
        }
        return blocks;
    }

    /** Funktionsnummer -&gt; Name fuer eine Lok; Symbole 0 ("---") und unbekannte Nummern ohne Namen werden ausgelassen. */
    static Map<Integer, String> functionNames(LocoEntry entry, List<String> iconNames) {
        Map<Integer, String> result = new java.util.TreeMap<>();
        for (Map.Entry<Integer, Integer> icon : entry.icons().entrySet()) {
            int symbol = icon.getValue();
            if (symbol > 0 && symbol < iconNames.size()) {
                result.put(icon.getKey(), iconNames.get(symbol));
            }
        }
        return result;
    }

    /**
     * Wandelt das Tams-Format ("DCC/126", "DCC/28", "DCC/14", "DCC/SDF" ...)
     * in den ECoS-aehnlichen Protokollcode um, den
     * {@link LocomotiveXmlFactory#parseLocoType} versteht - so teilen sich
     * mc2- und ECoS-Lokomotiven dieselbe Protokoll-Auswahl im
     * Bearbeitungsfenster. "SDF" (unkonfigurierte Adresse, Fahrstufen
     * unbekannt) faellt auf den Standardfall (126 Fahrstufen) zurueck,
     * ebenso alles Unbekannte (z.B. MM/SX-Formate - in Andres Anlage nicht
     * aufgetreten, NICHT geprueft, siehe STATUS.md).
     */
    static String protocolCodeFromFmt(String fmt) {
        String f = fmt == null ? "" : fmt.trim().toUpperCase(Locale.ROOT);
        if (f.equals("DCC/28")) {
            return "DCC28";
        } else if (f.equals("DCC/14")) {
            return "DCC14";
        } else if (f.startsWith("SX") || f.contains("SELECTRIX")) {
            return "SX32";
        }
        return "DCC128";
    }

    /**
     * Liest loco.ini und liefert die iTrain-Lokomotiveneintraege (nur
     * Adressen mit Namen - unbenannte Adressen sind meist nur am Regler
     * angewaehlt, nie als eigene Lok angelegt worden, und wuerden die
     * Tabelle mit Karteileichen fuellen).
     */
    static List<SystemsObject> readObjects(String host, String interfaceName, String gauge) throws IOException {
        // Seit 13.10.2026 per HTTP (Weboberflaeche der mc2) statt FTP.
        String text = Mc2Http.get(host, CONFIG_DIR + "/" + LOCO_FILE);
        List<LocoEntry> entries = parse(text);
        boolean anyIcons = entries.stream().anyMatch(e -> !e.icons().isEmpty());
        List<String> iconNames = anyIcons ? loadIconNames(host) : List.of();
        List<SystemsObject> result = new ArrayList<>();
        for (LocoEntry entry : entries) {
            if (entry.name() == null || entry.name().isBlank()) {
                continue;
            }
            LocomotiveXmlFactory.LocoInfo info = LocomotiveXmlFactory.parseLocoType(protocolCodeFromFmt(entry.fmt()));
            Map<Integer, String> functions = functionNames(entry, iconNames);
            XmlNode node = LocomotiveXmlFactory.createMc2Locomotive(entry.name(), entry.address(), info,
                    interfaceName, gauge, functions);
            SystemsObject object = new SystemsObject(SystemsObject.CATEGORY_LOCOMOTIVES, info.protocolCode(),
                    String.valueOf(entry.address()), entry.name() + " (" + entry.address() + ")", entry.name(),
                    true, "", "", interfaceName, node);
            // UID nur zur Anzeige - wird NICHT in die iTrain-Datei geschrieben
            // (wie iTrain sie speichert, ist noch nicht belegt).
            object.setUid(entry.uid());
            object.setMc2Functions(functions);
            object.refreshLocomotiveLength();
            result.add(object);
        }
        return result;
    }

    /** Mit kleinem Fortschrittsfenster (kein Prozentwert, die FTP-Uebertragung ist zu kurz dafuer) und Fehlerdialog. */
    public static void readWithProgress(Window owner, String host, String interfaceName, String gauge,
            Consumer<List<SystemsObject>> onDone) {
        I18n i18n = I18n.getInstance();
        AppSettings settings = AppSettings.getInstance();

        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle(i18n.t("mc2.readTitle"));
        dialog.setResizable(false);
        Label label = new Label(i18n.t("mc2.reading"));
        ProgressIndicator indicator = new ProgressIndicator();
        indicator.setPrefSize(28, 28);
        VBox content = new VBox(14, label, indicator);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(20));
        Scene scene = new Scene(new BorderPane(content));
        ThemeManager.apply(scene, settings.getTheme());
        dialog.setScene(scene);
        dialog.show();

        Thread worker = new Thread(() -> {
            try {
                List<SystemsObject> result = readObjects(host, interfaceName, gauge);
                Platform.runLater(() -> {
                    dialog.close();
                    onDone.accept(result);
                });
            } catch (IOException | RuntimeException ex) {
                LOGGER.warn("mc2-Lokdaten von {} nicht lesbar: {}", host, ex.getMessage(), ex);
                Platform.runLater(() -> {
                    dialog.close();
                    Alert alert = new Alert(Alert.AlertType.ERROR, String.valueOf(ex.getMessage()));
                    alert.initOwner(owner);
                    alert.setHeaderText(i18n.t("bidib.readFailedTitle"));
                    alert.showAndWait();
                });
            }
        }, "mc2-loco-read");
        worker.setDaemon(true);
        worker.start();
    }
}
