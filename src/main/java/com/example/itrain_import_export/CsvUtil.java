package com.example.itrain_import_export;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Kleiner, abhängigkeitsfreier CSV-Lese-/Schreib-Helfer.
 * <p>
 * Trennzeichen ist Semikolon (üblich für deutsche Excel-Installationen,
 * da das Komma dort das Dezimaltrennzeichen ist). Jedes Feld wird immer
 * in Anführungszeichen gesetzt (RFC 4180), damit auch mehrzeilige Felder
 * (z.B. ein komplettes, eingerücktes XML-Fragment als Zellwert) sicher
 * funktionieren. Die Datei wird mit UTF-8-BOM geschrieben, damit Excel
 * die Kodierung automatisch korrekt erkennt.
 * <p>
 * <b>ZIP-Hülle (seit 2.5):</b> Exporte werden als {@code <name>.zip}
 * geschrieben, das genau eine Datei {@code <name>.csv} enthält - eine
 * .csv lässt sich in vielen Foren nicht als Anhang hochladen, eine .zip
 * schon. Der Inhalt bleibt dieselbe CSV. {@link #readAny} liest beide
 * Formen, damit ältere .csv-Exporte weiter importierbar sind.
 */
public final class CsvUtil {

    private static final char DELIMITER = ';';

    private CsvUtil() {
    }

    public static void write(File file, List<String> header, List<List<String>> rows) throws IOException {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writeAll(writer, header, rows);
        }
    }

    /**
     * Schreibt die CSV als einzigen Eintrag {@code entryName} in die
     * ZIP-Datei {@code zipFile}. Der Eintragsname sollte der Dateiname der
     * ZIP mit Endung .csv sein, damit ein entpackender Anwender eine
     * erwartbar benannte Datei vorfindet.
     */
    public static void writeZipped(File zipFile, String entryName, List<String> header,
                                   List<List<String>> rows) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (Writer writer = new OutputStreamWriter(buffer, StandardCharsets.UTF_8)) {
            writeAll(writer, header, rows);
        }
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(zipFile))) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(buffer.toByteArray());
            zip.closeEntry();
        }
    }

    /** Eine CSV-Tabelle (Kopfzeile + Zeilen) - fuer ZIPs mit mehreren Eintraegen. */
    public record CsvTable(List<String> header, List<List<String>> rows) {
    }

    /**
     * Schreibt MEHRERE CSV-Tabellen als je einen Eintrag in eine ZIP-Datei
     * (System-Dateien des Systeme-Fensters: interface.csv, nodes.csv,
     * objects.csv). Reihenfolge der Eintraege = Reihenfolge der Map.
     */
    public static void writeZipped(File zipFile, java.util.Map<String, CsvTable> entries) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(zipFile))) {
            for (java.util.Map.Entry<String, CsvTable> entry : entries.entrySet()) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                try (Writer writer = new OutputStreamWriter(buffer, StandardCharsets.UTF_8)) {
                    writeAll(writer, entry.getValue().header(), entry.getValue().rows());
                }
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(buffer.toByteArray());
                zip.closeEntry();
            }
        }
    }

    /** Liest alle .csv-Eintraege einer ZIP: Eintragsname -> Zeilen (Kopfzeile eingeschlossen). */
    public static java.util.Map<String, List<List<String>>> readZipEntries(File zipFile) throws IOException {
        java.util.Map<String, List<List<String>>> result = new java.util.LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(zipFile)) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().toLowerCase().endsWith(".csv")) {
                    continue;
                }
                try (InputStream in = zip.getInputStream(entry)) {
                    result.put(entry.getName(), parse(in.readAllBytes()));
                }
            }
        }
        return result;
    }

    private static void writeAll(Writer writer, List<String> header, List<List<String>> rows) throws IOException {
        writer.write(0xFEFF); // UTF-8-BOM, damit Excel die Kodierung erkennt
        writeRow(writer, header);
        for (List<String> row : rows) {
            writeRow(writer, row);
        }
    }

    private static void writeRow(Writer writer, List<String> fields) throws IOException {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                writer.write(DELIMITER);
            }
            writer.write(quote(fields.get(i)));
        }
        writer.write("\r\n");
    }

    private static String quote(String value) {
        String v = value == null ? "" : value;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    /** Liest die komplette CSV-Datei ein, eine Zeile der Rückgabe je CSV-Zeile (Header eingeschlossen). */
    public static List<List<String>> read(File file) throws IOException {
        return parse(Files.readAllBytes(file.toPath()));
    }

    /**
     * Liest eine Export-Datei unabhängig von ihrer Hülle: eine {@code .zip}
     * wird geöffnet und der erste {@code .csv}-Eintrag darin gelesen, alles
     * andere wird direkt als CSV gelesen. Entschieden wird nach dem Inhalt
     * (ZIP-Kennung {@code PK}), nicht nach der Endung - eine umbenannte
     * Datei soll nicht an der Endung scheitern.
     *
     * @throws IOException wenn eine ZIP keinen .csv-Eintrag enthält
     */
    public static List<List<String>> readAny(File file) throws IOException {
        if (!looksLikeZip(file)) {
            return read(file);
        }
        try (ZipFile zip = new ZipFile(file)) {
            ZipEntry csvEntry = zip.stream()
                    .filter(e -> !e.isDirectory() && e.getName().toLowerCase().endsWith(".csv"))
                    .findFirst()
                    .orElseThrow(() -> new IOException(
                            I18n.getInstance().t("editor.importZipWithoutCsv", file.getName())));
            try (InputStream in = zip.getInputStream(csvEntry)) {
                return parse(in.readAllBytes());
            }
        }
    }

    /** ZIP-Dateien beginnen mit der Kennung "PK" (0x50 0x4B). */
    private static boolean looksLikeZip(File file) throws IOException {
        try (InputStream in = Files.newInputStream(file.toPath())) {
            byte[] head = in.readNBytes(2);
            return head.length == 2 && head[0] == 0x50 && head[1] == 0x4B;
        }
    }

    private static List<List<String>> parse(byte[] bytes) {
        String content = new String(bytes, StandardCharsets.UTF_8);
        if (!content.isEmpty() && content.codePointAt(0) == 0xFEFF) {
            content = content.substring(1);
        }

        List<List<String>> rows = new ArrayList<>();
        List<String> currentRow = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        int i = 0;
        int n = content.length();

        while (i < n) {
            char c = content.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < n && content.charAt(i + 1) == '"') {
                        field.append('"');
                        i += 2;
                    } else {
                        inQuotes = false;
                        i++;
                    }
                } else {
                    field.append(c);
                    i++;
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                    i++;
                } else if (c == DELIMITER) {
                    currentRow.add(field.toString());
                    field.setLength(0);
                    i++;
                } else if (c == '\r') {
                    i++;
                } else if (c == '\n') {
                    currentRow.add(field.toString());
                    field.setLength(0);
                    rows.add(currentRow);
                    currentRow = new ArrayList<>();
                    i++;
                } else {
                    field.append(c);
                    i++;
                }
            }
        }
        if (field.length() > 0 || !currentRow.isEmpty()) {
            currentRow.add(field.toString());
            rows.add(currentRow);
        }
        return rows;
    }
}
