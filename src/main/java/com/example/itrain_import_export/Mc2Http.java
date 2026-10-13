package com.example.itrain_import_export;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Befehle an die Weboberflaeche der Tams mc2 (seit 13.10.2026). Die
 * Oberflaeche selbst schickt einfache GET-Anfragen an {@code /cgi/command}
 * bzw. {@code /cgi/query} (abgelesen an html/experts.html, decoder.html,
 * scripts/loks.js); eine Anmeldung ist nicht noetig (keine Zugangssperre in
 * .htaccess, config/passwd.js leer).
 * <p>
 * Handtest 13.10.2026 an Andres mc2: Lok 8888 mit {@code fmt=7} angelegt,
 * Name mit Umlaut und Funktionssymbole gesetzt, per {@code cmd=info}
 * zurueckgelesen, mit {@code removeLoco} wieder entfernt - alles wirksam
 * ohne Neustart; die mc2 schreibt ihre loco.ini dabei selbst.
 */
public final class Mc2Http {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final int EVENT_TIMEOUT_MS = 5000;

    /** Formatnummern der mc2 (scripts/loks.js, {@code const fmt}). */
    static final Map<String, Integer> FORMAT_NUMBERS = Map.of(
            "DCC/14", 6, "DCC/28", 7, "DCC/126", 8, "DCC/SDF", 9);

    private Mc2Http() {
    }

    /** Was die mc2 zu einer Lok meldet ({@code /cgi/query?lok=ADR&cmd=info}). */
    public record LocoInfo(int address, String name, String fmt, long uid, long vid,
            Map<Integer, int[]> funcIcons) {
        /** Symbolnummer je Funktion (ohne Tastzeit). */
        Map<Integer, Integer> icons() {
            Map<Integer, Integer> result = new TreeMap<>();
            funcIcons.forEach((nr, v) -> result.put(nr, v[0]));
            return result;
        }
    }

    /**
     * Startet die mc2 neu - wie der Knopf "reboot mc²" auf der Seite
     * "Experten" ({@code GET /cgi/command?cmd=reboot}). Die Gleisspannung
     * faellt dabei kurz weg. Blockierend (Netzwerk).
     */
    public static void reboot(String host) throws IOException {
        command(host, "cmd=reboot");
    }

    /** {@code GET http://host/cgi/command?<query>} - Antwort wird nicht ausgewertet. */
    static void command(String host, String query) throws IOException {
        try {
            get(host, "/cgi/command?" + query);
        } catch (java.net.http.HttpTimeoutException ex) {
            // Beim Neustart antwortet die mc2 oft gar nicht mehr - das ist
            // dann kein Fehler, der Befehl ist angekommen.
            if (!query.contains("cmd=reboot")) {
                throw ex;
            }
        }
    }

    /** {@code GET http://host<path>} als Text (UTF-8). */
    static String get(String host, String pathAndQuery) throws IOException {
        HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://" + host + pathAndQuery))
                .timeout(TIMEOUT).GET().build();
        try {
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new IOException("HTTP " + response.statusCode() + " von " + host + pathAndQuery);
            }
            return response.body();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("unterbrochen", ex);
        }
    }

    /**
     * Adressen aller Loks der mc2 - aus dem Ereignisstrom
     * {@code /cgi/events?locodb} (erste Meldung {@code data: {"addresses": [...]}},
     * so liest es auch die Fahrzeugseite der Weboberflaeche).
     */
    static List<Integer> locoAddresses(String host) throws IOException {
        HttpURLConnection connection = (HttpURLConnection)
                URI.create("http://" + host + "/cgi/events?locodb").toURL().openConnection();
        connection.setConnectTimeout(EVENT_TIMEOUT_MS);
        connection.setReadTimeout(EVENT_TIMEOUT_MS);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int at = line.indexOf("\"addresses\"");
                if (!line.startsWith("data:") || at < 0) {
                    continue;
                }
                int open = line.indexOf('[', at);
                int close = line.indexOf(']', open);
                List<Integer> result = new ArrayList<>();
                if (open >= 0 && close > open) {
                    for (String part : line.substring(open + 1, close).split(",")) {
                        if (!part.isBlank()) {
                            result.add(Integer.parseInt(part.trim()));
                        }
                    }
                }
                return result;
            }
            throw new IOException("Keine Lokliste von " + host + " erhalten.");
        } finally {
            connection.disconnect();
        }
    }

    private static final Pattern NAME = Pattern.compile("\"name\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern FMT = Pattern.compile("\"fmt\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern UID = Pattern.compile("\"uid\"\\s*:\\s*(\\d+)");
    private static final Pattern VID = Pattern.compile("\"vid\"\\s*:\\s*(\\d+)");
    private static final Pattern ICONS = Pattern.compile("\"funcicons\"\\s*:\\s*\\[([^\\]]*)]");

    /** Daten einer Lok, oder null, wenn die mc2 die Adresse nicht kennt (leere Antwort). */
    static LocoInfo locoInfo(String host, int address) throws IOException {
        String body = get(host, "/cgi/query?lok=" + address + "&cmd=info");
        if (body == null || body.isBlank() || !body.contains("\"lok\"")) {
            return null;
        }
        Map<Integer, int[]> icons = new TreeMap<>();
        Matcher iconMatch = ICONS.matcher(body);
        if (iconMatch.find()) {
            String[] parts = iconMatch.group(1).split(",");
            for (int i = 0; i + 2 < parts.length; i += 3) {
                try {
                    icons.put(Integer.parseInt(parts[i].trim()),
                            new int[]{Integer.parseInt(parts[i + 1].trim()), Integer.parseInt(parts[i + 2].trim())});
                } catch (NumberFormatException ignored) {
                    // unlesbares Tripel
                }
            }
        }
        return new LocoInfo(address, unescape(find(NAME, body)), find(FMT, body),
                parseLong(find(UID, body)), parseLong(find(VID, body)), icons);
    }

    /**
     * Lok anlegen bzw. ueberschreiben - dieselben drei Aufrufe wie die
     * Fahrzeugseite der Weboberflaeche: Format (mit vid/uid - bei einer
     * vorhandenen Lok deren Werte, damit DCC-A-Kennungen erhalten bleiben),
     * Name, Funktionssymbole ({@code fuico=nr|Symbol|Tastzeit nr|...}; die
     * Tastzeit einer schon vorhandenen Funktion bleibt erhalten).
     */
    static void writeLoco(String host, Mc2LocoWriter.Profile profile, LocoInfo existing) throws IOException {
        Integer format = FORMAT_NUMBERS.get(profile.fmt());
        if (format == null) {
            throw new IOException("Format " + profile.fmt() + " wird nicht unterstuetzt.");
        }
        long vid = existing == null ? 0 : existing.vid();
        long uid = existing == null ? 0 : existing.uid();
        command(host, "lok=" + profile.address() + "&vid=" + vid + "&uid=" + uid + "&fmt=" + format + "&maxfunc=32");
        command(host, "lok=" + profile.address() + "&name=" + encode(profile.name()));
        StringBuilder icons = new StringBuilder();
        for (Map.Entry<Integer, Integer> icon : new TreeMap<>(profile.icons()).entrySet()) {
            int time = 0;
            if (existing != null && existing.funcIcons().containsKey(icon.getKey())) {
                time = existing.funcIcons().get(icon.getKey())[1];
            }
            icons.append(icon.getKey()).append('|').append(icon.getValue()).append('|').append(time).append(' ');
        }
        command(host, "lok=" + profile.address() + "&fuico=" + encode(icons.toString()));
    }

    /** Lok loeschen ({@code cmd=removeLoco}, wie die Fahrzeugseite der Weboberflaeche). */
    static void removeLoco(String host, int address) throws IOException {
        command(host, "cmd=removeLoco&lok=" + address);
    }

    /** Wert fuer eine Abfrage-Zeichenkette kodieren. */
    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String find(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : "";
    }

    private static String unescape(String text) {
        return text.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static long parseLong(String text) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
