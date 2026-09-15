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

    /** Eine Lokomotive aus loco.ini: Adresse (aus dem Abschnittsnamen), Name (kann fehlen) und Format. */
    public record LocoEntry(int address, String name, String fmt) {
    }

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
            Mc2FtpClient.fetchText(host, CONFIG_DIR, LOCO_FILE);
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
        result.add(new LocoEntry(address, fields.get("name"), fields.get("fmt")));
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
    static List<SystemsObject> readObjects(String host, String interfaceName) throws IOException {
        String text = Mc2FtpClient.fetchText(host, CONFIG_DIR, LOCO_FILE);
        List<SystemsObject> result = new ArrayList<>();
        for (LocoEntry entry : parse(text)) {
            if (entry.name() == null || entry.name().isBlank()) {
                continue;
            }
            LocomotiveXmlFactory.LocoInfo info = LocomotiveXmlFactory.parseLocoType(protocolCodeFromFmt(entry.fmt()));
            XmlNode node = LocomotiveXmlFactory.createLocomotive(entry.name(), entry.address(), info, interfaceName);
            SystemsObject object = new SystemsObject(SystemsObject.CATEGORY_LOCOMOTIVES, info.protocolCode(),
                    String.valueOf(entry.address()), entry.name() + " (" + entry.address() + ")", entry.name(),
                    true, "", "", interfaceName, node);
            result.add(object);
        }
        return result;
    }

    /** Mit kleinem Fortschrittsfenster (kein Prozentwert, die FTP-Uebertragung ist zu kurz dafuer) und Fehlerdialog. */
    public static void readWithProgress(Window owner, String host, String interfaceName,
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
                List<SystemsObject> result = readObjects(host, interfaceName);
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
