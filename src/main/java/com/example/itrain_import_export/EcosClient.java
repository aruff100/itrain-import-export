package com.example.itrain_import_export;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verbindung zu einer ESU ECoS (50100/50200/50210/50220, auch Märklin CS1)
 * über das ECoS-Netzprotokoll: eine TCP-Verbindung auf Port {@value #PORT},
 * reiner Text, eine Zeile je Befehl. Jede Antwort beginnt mit
 * {@code <REPLY befehl>}, dann eine Zeile je Objekt im Muster
 * {@code <id> schluessel[wert] schluessel["text"] ...}, zum Schluss
 * {@code <END code (text)>} - Code 0 heißt OK.
 * <p>
 * Benutzt werden nur lesende Befehle: {@code get(1, info)} für Name und
 * Version, {@code queryObjects(26, ports)} für die Rückmeldemodule
 * (Objekte 100-299: S88 ab 100, ECoSDetector ab 200), {@code queryObjects(11,
 * ...)} für Zubehör (20000-29999, Fahrstraßen 30000+), {@code queryObjects(10,
 * ...)} für Lokomotiven (1000-1999). Kein {@code request(..., view)} - wir
 * wollen keine Ereignisse, nur den Bestand. Befehle und Antworten laufen
 * über {@link #addTrafficListener} auch ins RX/TX-Fenster.
 */
public final class EcosClient implements AutoCloseable {

    public static final int PORT = 15471;

    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 4000;
    private static final Pattern FIELD = Pattern.compile("([A-Za-z0-9_]+)\\[((?:\"[^\"]*\")|[^\\]]*)\\]");

    /** Eine Zeile einer Antwort: Objektnummer und die Felder in gemeldeter Reihenfolge. */
    public record Line(int objectId, Map<String, String> fields) {
        public String get(String key) {
            return fields.get(key);
        }

        public int getInt(String key, int fallback) {
            String v = fields.get(key);
            if (v == null) {
                return fallback;
            }
            try {
                return Integer.parseInt(v.trim());
            } catch (NumberFormatException ex) {
                return fallback;
            }
        }
    }

    /** Antwort: Kopfzeile, Zeilen, Ergebniscode und -text. */
    public record Reply(String command, List<Line> lines, int code, String message) {
        public boolean isOk() {
            return code == 0;
        }
    }

    private final String host;
    private final Socket socket;
    private final BufferedReader reader;
    private final OutputStream out;
    private final List<java.util.function.BiConsumer<String, String>> trafficListeners = new ArrayList<>();

    public EcosClient(String host) throws IOException {
        this.host = host;
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, PORT), CONNECT_TIMEOUT_MS);
        socket.setSoTimeout(READ_TIMEOUT_MS);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = socket.getOutputStream();
    }

    public String getHost() {
        return host;
    }

    /** Richtung ("TX"/"RX") und Zeile - fuer die Mitschrift. */
    public void addTrafficListener(java.util.function.BiConsumer<String, String> listener) {
        trafficListeners.add(listener);
    }

    private void traffic(String direction, String line) {
        for (java.util.function.BiConsumer<String, String> listener : trafficListeners) {
            listener.accept(direction, line);
        }
    }

    /** Befehl senden und die komplette Antwort bis {@code <END ...>} einsammeln. */
    public synchronized Reply send(String command) throws IOException {
        traffic("TX", command);
        out.write((command + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();

        String header = null;
        List<Line> lines = new ArrayList<>();
        while (true) {
            String raw = reader.readLine();
            if (raw == null) {
                throw new IOException("Verbindung zur ECoS beendet");
            }
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            traffic("RX", line);
            if (line.startsWith("<REPLY")) {
                header = line;
                continue;
            }
            if (line.startsWith("<EVENT")) {
                // Ereignisse (nicht angefordert) bis zum naechsten END ueberlesen.
                header = line;
                continue;
            }
            if (line.startsWith("<END")) {
                if (header != null && header.startsWith("<EVENT")) {
                    header = null;
                    lines.clear();
                    continue;
                }
                return new Reply(command, lines, parseEndCode(line), parseEndMessage(line));
            }
            lines.add(parseLine(line));
        }
    }

    private static int parseEndCode(String end) {
        Matcher m = Pattern.compile("<END\\s+(\\d+)").matcher(end);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    private static String parseEndMessage(String end) {
        int open = end.indexOf('(');
        int close = end.lastIndexOf(')');
        return open >= 0 && close > open ? end.substring(open + 1, close) : end;
    }

    /** {@code 20003 name1["Weiche 3"] addr[3] protocol[DCC]} -> Objekt 20003 mit drei Feldern. */
    static Line parseLine(String line) {
        int space = line.indexOf(' ');
        String idText = space < 0 ? line : line.substring(0, space);
        int id;
        try {
            id = Integer.parseInt(idText.trim());
        } catch (NumberFormatException ex) {
            id = -1;
        }
        Map<String, String> fields = new LinkedHashMap<>();
        if (space >= 0) {
            String rest = line.substring(space + 1).trim();
            Matcher m = FIELD.matcher(rest);
            if (!m.find()) {
                // Zeile ohne Felder, z.B. "1 ECoS2" bei get(1, info): der
                // Rest ist der Geraetetyp.
                if (!rest.isEmpty()) {
                    fields.put("_text", rest);
                }
                return new Line(id, fields);
            }
            m.reset();
            while (m.find()) {
                String value = m.group(2);
                if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                // Mehrfach vorkommende Schluessel (z.B. mehrere name-Felder)
                // haengen wir mit " / " aneinander.
                fields.merge(m.group(1), value, (a, b) -> a + " / " + b);
            }
        }
        return new Line(id, fields);
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // beim Schliessen unerheblich
        }
    }
}
