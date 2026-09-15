package com.example.itrain_import_export;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

/**
 * Schreibt die Protokollausgabe des Programms zusätzlich in eine Datei im
 * Programmverzeichnis, damit sie sich nach einem Verbindungsversuch in Ruhe
 * ansehen lässt - ohne das Konsolenfenster mitlaufen lassen zu müssen.
 *
 * <h2>Wie das Mitschreiben funktioniert</h2>
 * Die BiDiB-Bibliothek schreibt über slf4j-simple auf {@code System.err}.
 * Statt slf4j umzukonfigurieren (dann stünde nichts mehr auf der Konsole),
 * werden {@code System.out} und {@code System.err} durch eine Weiche ersetzt,
 * die jede Zeile an beide Ziele gibt: an die ursprüngliche Konsole UND in die
 * Datei. Das muss vor der ersten Protokollausgabe geschehen - slf4j-simple
 * merkt sich sein Ausgabeziel beim ersten Zugriff -, deshalb steht der Aufruf
 * ganz am Anfang von {@code main}.
 *
 * <h2>Dateiname</h2>
 * {@code bidib_<Interface>_<Zeitstempel>_log.txt}. Der Name des Interface ist
 * beim Start noch nicht bekannt (er kommt erst vom Gerät), deshalb heißt die
 * Datei zunächst {@code bidib_start_...} und wird umbenannt, sobald der Name
 * feststeht ({@link #setInterfaceName}). Der Zeitstempel muss sein: Ohne ihn
 * würde jeder neue Start dieselbe Datei überschreiben, und die geforderten
 * zehn Läufe wären nicht aufzubewahren.
 *
 * <h2>Aufräumen</h2>
 * Beim Start bleiben die {@value #MAX_FILES} neuesten Dateien liegen, ältere
 * werden gelöscht.
 */
public final class BidibLog {

    /** So viele Protokolldateien bleiben erhalten; die ältesten darüber hinaus werden gelöscht. */
    private static final int MAX_FILES = 10;

    private static final String PREFIX = "bidib_";
    private static final String SUFFIX = "_log.txt";

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private static final Object LOCK = new Object();

    private static PrintStream originalOut;
    private static PrintStream originalErr;
    private static FileOutputStream fileStream;
    private static File currentFile;
    private static String timestamp;
    private static boolean interfaceNameSet;

    private BidibLog() {
    }

    /**
     * Startet das Mitschreiben. Mehrfachaufrufe sind wirkungslos. Schlägt das
     * Anlegen der Datei fehl (schreibgeschütztes Verzeichnis), läuft das
     * Programm unverändert weiter - nur ohne Protokolldatei.
     */
    public static void start() {
        synchronized (LOCK) {
            if (fileStream != null) {
                return;
            }
            try {
                pruneOldFiles();
                timestamp = LocalDateTime.now().format(STAMP);
                currentFile = new File(logDirectory(), PREFIX + "start_" + timestamp + SUFFIX);
                fileStream = new FileOutputStream(currentFile, true);

                originalOut = System.out;
                originalErr = System.err;
                System.setOut(tee(originalOut));
                System.setErr(tee(originalErr));

                System.out.println("=== Protokoll " + timestamp + " - " + currentFile.getName() + " ===");
            } catch (Exception ex) {
                // Ohne Datei weiterlaufen; die Konsole bleibt unberührt.
                fileStream = null;
                currentFile = null;
            }
        }
    }

    /**
     * Benennt die laufende Protokolldatei nach dem Interface, sobald dessen
     * Name bekannt ist (erste erfolgreiche Verbindung). Weitere Verbindungen
     * ändern den Namen nicht mehr - eine Datei je Programmlauf.
     */
    public static void setInterfaceName(String name) {
        synchronized (LOCK) {
            if (fileStream == null || interfaceNameSet || name == null || name.isBlank()) {
                return;
            }
            interfaceNameSet = true;
            File target = new File(logDirectory(), PREFIX + sanitize(name) + "_" + timestamp + SUFFIX);
            if (target.equals(currentFile)) {
                return;
            }
            try {
                // Unter Windows lässt sich eine offene Datei nicht umbenennen -
                // deshalb schließen, umbenennen, wieder anhängend öffnen.
                fileStream.flush();
                fileStream.close();
                if (currentFile.renameTo(target)) {
                    currentFile = target;
                }
                fileStream = new FileOutputStream(currentFile, true);
                System.setOut(tee(originalOut));
                System.setErr(tee(originalErr));
                System.out.println("=== Interface: " + name + " ===");
            } catch (Exception ex) {
                // Beim Scheitern ohne Datei weiterschreiben.
                fileStream = null;
                System.setOut(originalOut);
                System.setErr(originalErr);
            }
        }
    }

    /** Verzeichnis, in dem das Programm läuft (bei "gradlew run" der Projektstamm). */
    private static File logDirectory() {
        return new File(System.getProperty("user.dir", "."));
    }

    /** Ersetzt alles, was in einem Dateinamen Ärger macht. */
    private static String sanitize(String name) {
        String cleaned = name.trim().replaceAll("[^\\p{L}\\p{N}._-]+", "-");
        cleaned = cleaned.replaceAll("-{2,}", "-").replaceAll("^-|-$", "");
        if (cleaned.isEmpty()) {
            cleaned = "interface";
        }
        return cleaned.length() > 40 ? cleaned.substring(0, 40) : cleaned;
    }

    /**
     * Behält die {@value #MAX_FILES} neuesten Protokolldateien und löscht den
     * Rest. Läuft beim Start, also bevor die Datei dieses Laufs entsteht -
     * danach liegen höchstens {@value #MAX_FILES} Dateien im Verzeichnis.
     */
    private static void pruneOldFiles() {
        File[] existing = logDirectory().listFiles((dir, name) ->
                name.toLowerCase(Locale.ROOT).startsWith(PREFIX)
                        && name.toLowerCase(Locale.ROOT).endsWith(SUFFIX));
        if (existing == null || existing.length < MAX_FILES) {
            return;
        }
        Arrays.sort(existing, Comparator.comparingLong(File::lastModified).reversed());
        for (int i = MAX_FILES - 1; i < existing.length; i++) {
            // -1, weil gleich eine neue Datei dazukommt.
            existing[i].delete();
        }
    }

    /** Ausgabestrom, der jede Zeile an die Konsole UND in die Datei gibt. */
    private static PrintStream tee(PrintStream console) {
        OutputStream both = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                console.write(b);
                FileOutputStream file = fileStream;
                if (file != null) {
                    file.write(b);
                }
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                console.write(b, off, len);
                FileOutputStream file = fileStream;
                if (file != null) {
                    file.write(b, off, len);
                }
            }

            @Override
            public void flush() throws IOException {
                console.flush();
                FileOutputStream file = fileStream;
                if (file != null) {
                    file.flush();
                }
            }
        };
        return new PrintStream(both, true, StandardCharsets.UTF_8);
    }
}
