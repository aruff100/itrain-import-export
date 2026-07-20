package com.example.itrain_import_export;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Kleiner, abhängigkeitsfreier CSV-Lese-/Schreib-Helfer.
 * <p>
 * Trennzeichen ist Semikolon (üblich für deutsche Excel-Installationen,
 * da das Komma dort das Dezimaltrennzeichen ist). Jedes Feld wird immer
 * in Anführungszeichen gesetzt (RFC 4180), damit auch mehrzeilige Felder
 * (z.B. ein komplettes, eingerücktes XML-Fragment als Zellwert) sicher
 * funktionieren. Die Datei wird mit UTF-8-BOM geschrieben, damit Excel
 * die Kodierung automatisch korrekt erkennt.
 */
public final class CsvUtil {

    private static final char DELIMITER = ';';

    private CsvUtil() {
    }

    public static void write(File file, List<String> header, List<List<String>> rows) throws IOException {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(0xFEFF); // UTF-8-BOM, damit Excel die Kodierung erkennt
            writeRow(writer, header);
            for (List<String> row : rows) {
                writeRow(writer, row);
            }
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
        byte[] bytes = Files.readAllBytes(file.toPath());
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
