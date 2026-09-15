package com.example.itrain_import_export;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Die mitgelieferten Decoder-Vorlagen (seit 2.5).
 * <p>
 * Bis 2.0.1 lagen die Vorlagen als {@code decoder.zip} / {@code decoder_eng.zip}
 * auf Proton Drive und mussten vom Anwender heruntergeladen und über
 * "Decoder-Vorlagen installieren" entpackt werden. Seit 2.5 stecken sie im
 * Programm selbst, als Ressourcen unter
 * {@code decoder-templates/<sprachordner>/}, und werden beim Start in den
 * Decoder-Ordner des Anwenders übertragen. Auf Proton Drive liegen nur noch
 * einzelne, neuere oder geänderte Vorlagen; die installiert man weiterhin
 * über "Decoder-Vorlagen installieren" ({@link DecoderTemplateInstaller}).
 *
 * <h2>Sprachregel</h2>
 * {@link #folderFor(String)} bildet die Programmsprache auf einen
 * Sprachordner ab: Deutsch und Niederländisch bekommen die deutschen
 * Vorlagen, alle anderen Sprachen die englischen. Weitere Übersetzungen
 * kommen als neuer Ordner plus eine Zeile in dieser Methode dazu.
 *
 * <h2>Wann übertragen wird</h2>
 * Beim Programmstart (nach der Ersteinrichtung) und bei jedem Sprachwechsel
 * ({@link #syncIfNeeded()}). Übertragen wird nur, wenn sich seit dem letzten
 * Mal etwas geändert hat - erkennbar an der Kennung aus Sprachordner,
 * Zielordner und einem Hash über den Inhalt aller Dateien
 * ({@link AppSettings#getBundledTemplatesFingerprint()}). Eine neue
 * Programmversion mit überarbeiteten Vorlagen bringt sie so von selbst zu
 * jedem Anwender.
 *
 * <h2>Was mit vorhandenen Dateien passiert</h2>
 * Gleichnamige Dateien im Decoder-Ordner werden <b>ersetzt</b> (sie stammen
 * aus einer früheren Lieferung), alle anderen bleiben unberührt - das sind
 * eigene Vorlagen des Anwenders oder einzeln installierte Aktualisierungen.
 *
 * <h2>Woher das Programm die Dateinamen kennt</h2>
 * Ressourcen lassen sich zur Laufzeit nicht auflisten. Deshalb erzeugt der
 * Build je Sprachordner eine {@code index.txt} mit den Dateinamen (siehe
 * {@code tasks.processResources} in build.gradle.kts).
 */
public final class DecoderTemplateBundle {

    /** Wurzel der Vorlagen-Ressourcen, relativ zum Paket dieser Klasse. */
    private static final String RESOURCE_ROOT = "decoder-templates/";
    private static final String INDEX_FILE = "index.txt";
    private static final String VERSION_FILE = "version.txt";

    /** Sprachen, die die deutschen Vorlagen bekommen; alle anderen die englischen. */
    private static final Map<String, String> LANGUAGE_FOLDERS = Map.of(
            "de", "de",
            "nl", "de"
            // Weitere Uebersetzungen: hier eintragen, z.B. "fr", "fr" - und den
            // Ordner decoder-templates/fr/ mit den Dateien anlegen.
    );
    private static final String DEFAULT_FOLDER = "en";

    private DecoderTemplateBundle() {
    }

    /** Sprachordner für eine Programmsprache (siehe Klassenkommentar). */
    public static String folderFor(String languageCode) {
        return LANGUAGE_FOLDERS.getOrDefault(languageCode, DEFAULT_FOLDER);
    }

    /**
     * Überträgt die mitgelieferten Vorlagen in den Decoder-Ordner, falls
     * nötig. Ohne eingestellten oder vorhandenen Decoder-Ordner passiert
     * nichts - der wird bei der Ersteinrichtung abgefragt; der nächste Start
     * holt die Übertragung nach.
     *
     * @return Anzahl der übertragenen Dateien, 0 wenn nichts zu tun war
     */
    public static int syncIfNeeded() {
        AppSettings settings = AppSettings.getInstance();
        String dir = settings.getDecoderDirectory();
        if (dir == null || dir.isBlank()) {
            return 0;
        }
        File target = new File(dir);
        if (!target.isDirectory()) {
            return 0;
        }

        String folder = folderFor(I18n.getInstance().getCurrentLanguage());
        List<String> names = readIndex(folder);
        if (names.isEmpty()) {
            System.err.println("Mitgelieferte Decoder-Vorlagen: kein Index fuer Sprachordner \"" + folder
                    + "\" gefunden - Build ohne index.txt?");
            return 0;
        }

        String fingerprint;
        try {
            fingerprint = folder + "|" + target.getAbsolutePath() + "|" + contentHash(folder, names);
        } catch (IOException | NoSuchAlgorithmException ex) {
            System.err.println("Mitgelieferte Decoder-Vorlagen: Kennung nicht berechenbar: " + ex.getMessage());
            return 0;
        }
        if (fingerprint.equals(settings.getBundledTemplatesFingerprint()) && allPresent(target, names)) {
            return 0;
        }

        int copied = 0;
        for (String name : names) {
            try (InputStream in = open(folder, name)) {
                if (in == null) {
                    System.err.println("Mitgelieferte Decoder-Vorlage fehlt in den Ressourcen: " + folder + "/" + name);
                    continue;
                }
                Files.copy(in, new File(target, name).toPath(), StandardCopyOption.REPLACE_EXISTING);
                copied++;
            } catch (IOException ex) {
                System.err.println("Decoder-Vorlage konnte nicht uebertragen werden: " + name + " - " + ex.getMessage());
            }
        }

        settings.setBundledTemplatesFingerprint(fingerprint);
        // Herkunftsangabe im Vorlagenfenster: Stand der mitgelieferten
        // Sammlung (version.txt im Sprachordner) und Zeitpunkt der Übertragung.
        settings.setDecoderPackVersion(readVersion(folder));
        settings.setDecoderPackInstalled(
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")));
        return copied;
    }

    /** Dateinamen aus der index.txt des Sprachordners, leer wenn nicht vorhanden. */
    private static List<String> readIndex(String folder) {
        List<String> names = new ArrayList<>();
        try (InputStream in = open(folder, INDEX_FILE)) {
            if (in == null) {
                return names;
            }
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && trimmed.toLowerCase().endsWith(".csv")) {
                    names.add(trimmed);
                }
            }
        } catch (IOException ex) {
            System.err.println("index.txt der Decoder-Vorlagen nicht lesbar: " + ex.getMessage());
        }
        return names;
    }

    private static String readVersion(String folder) {
        try (InputStream in = open(folder, VERSION_FILE)) {
            if (in == null) {
                return "";
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            int newline = text.indexOf('\n');
            return (newline >= 0 ? text.substring(0, newline) : text).trim();
        } catch (IOException ex) {
            return "";
        }
    }

    /** SHA-256 über Dateinamen und Inhalt aller Vorlagen, als Hex-Text. */
    private static String contentHash(String folder, List<String> names)
            throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (String name : names) {
            digest.update(name.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            try (InputStream in = open(folder, name)) {
                if (in != null) {
                    digest.update(in.readAllBytes());
                }
            }
            digest.update((byte) 0);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Sind alle Dateien noch da? Hat der Anwender den Ordner geleert oder
     * einzelne Dateien gelöscht, werden sie trotz unveränderter Kennung
     * erneut übertragen.
     */
    private static boolean allPresent(File target, List<String> names) {
        for (String name : names) {
            if (!new File(target, name).isFile()) {
                return false;
            }
        }
        return true;
    }

    private static InputStream open(String folder, String name) {
        return DecoderTemplateBundle.class.getResourceAsStream(RESOURCE_ROOT + folder + "/" + name);
    }
}
