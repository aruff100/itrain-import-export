package com.example.itrain_import_export;

import javafx.application.Platform;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prüft auf eine neue Programmversion, indem eine kleine, öffentlich
 * abrufbare Versions-Datei (siehe {@link #MANIFEST_URL}) per einfachem
 * HTTPS-GET gelesen wird - bewusst OHNE jede Verbindung zu Proton Drive
 * selbst (Proton Drive erlaubt keinen unauthentifizierten, programmatischen
 * Zugriff auf Freigabelinks; ein Zugangsdaten-Login im verteilten Programm
 * würde diese Zugangsdaten allen Nutzern zugänglich machen - siehe
 * Besprechung vom 23.07.2026 in STATUS.md).
 * <p>
 * Stattdessen liegt auf einem öffentlichen GitHub Gist eine winzige
 * JSON-Datei mit genau drei Feldern:
 * <pre>
 * {
 *   "version": "1.12",
 *   "url": "https://drive.proton.me/urls/DEIN-FREIGABELINK",
 *   "notes": "Kurze Beschreibung der Neuerungen (optional, darf fehlen)"
 * }
 * </pre>
 * Bei jedem neuen Release muss NUR diese eine Datei von Hand aktualisiert
 * werden (Gist bearbeiten, "version"/"url"/"notes" anpassen, speichern) -
 * am Proton-Drive-Freigabelink selbst ändert sich nichts, der Anwender lädt
 * die neue Version weiterhin ganz normal über den Browser herunter, die App
 * öffnet dafür nur den Link.
 */
public final class UpdateChecker {

    /**
     * "Raw"-URL des GitHub Gists von Andre (aruff100), der das
     * Update-Manifest enthält. Bewusst OHNE Dateinamen und OHNE
     * Commit-Hash am Ende: bei Ein-Datei-Gists liefert diese Form immer
     * den NEUESTEN Stand der Datei und übersteht auch ein späteres
     * Umbenennen der Datei im Gist. Bei jedem Release nur den Gist
     * bearbeiten (version/notes, ggf. url) - diese URL bleibt für immer
     * gleich, ein neuer Build ist dafür nicht nötig.
     */
    private static final String MANIFEST_URL =
            "https://gist.githubusercontent.com/aruff100/5535471917759f7b5adaddf26a930e06/raw";

    private static final Duration TIMEOUT = Duration.ofSeconds(6);

    private UpdateChecker() {
    }

    /** Ergebnis einer Update-Prüfung, siehe {@link #checkAsync(Consumer)}. */
    public static final class UpdateResult {
        public final boolean success;
        public final boolean updateAvailable;
        public final String currentVersion;
        public final String latestVersion;
        public final String downloadUrl;
        public final String notes;
        public final String errorMessage;

        private UpdateResult(boolean success, boolean updateAvailable, String currentVersion,
                              String latestVersion, String downloadUrl, String notes, String errorMessage) {
            this.success = success;
            this.updateAvailable = updateAvailable;
            this.currentVersion = currentVersion;
            this.latestVersion = latestVersion;
            this.downloadUrl = downloadUrl;
            this.notes = notes;
            this.errorMessage = errorMessage;
        }

        private static UpdateResult ok(String currentVersion, String latestVersion, String downloadUrl, String notes) {
            boolean available = compareVersions(latestVersion, currentVersion) > 0;
            return new UpdateResult(true, available, currentVersion, latestVersion, downloadUrl, notes, null);
        }

        private static UpdateResult error(String currentVersion, String message) {
            return new UpdateResult(false, false, currentVersion, null, null, null, message);
        }
    }

    /**
     * Führt die Prüfung in einem eigenen Hintergrund-Thread aus (Netzwerk-
     * Zugriff darf nicht den JavaFX-Anwendungs-Thread blockieren) und liefert
     * das Ergebnis über {@code onResult} zurück, bereits auf dem
     * JavaFX-Anwendungs-Thread (per {@link Platform#runLater}) - der Aufrufer
     * kann also direkt UI-Elemente (Dialoge etc.) anfassen.
     */
    public static void checkAsync(Consumer<UpdateResult> onResult) {
        Thread thread = new Thread(() -> {
            UpdateResult result = checkNow();
            Platform.runLater(() -> onResult.accept(result));
        }, "update-check");
        thread.setDaemon(true);
        thread.start();
    }

    /** Blockierende Prüfung - nur aus einem Hintergrund-Thread aufrufen, siehe {@link #checkAsync}. */
    private static UpdateResult checkNow() {
        String currentVersion = AppInfo.getVersion();
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(TIMEOUT)
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(MANIFEST_URL))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return UpdateResult.error(currentVersion, "HTTP " + response.statusCode());
            }
            String body = response.body();
            String latestVersion = extractJsonString(body, "version");
            String downloadUrl = extractJsonString(body, "url");
            String notes = extractJsonString(body, "notes");
            if (latestVersion == null || downloadUrl == null) {
                return UpdateResult.error(currentVersion, "Ungültiges Antwortformat (version/url fehlen)");
            }
            return UpdateResult.ok(currentVersion, latestVersion, downloadUrl, notes);
        } catch (Exception ex) {
            return UpdateResult.error(currentVersion, ex.getMessage() != null ? ex.getMessage() : ex.toString());
        }
    }

    /**
     * Sehr einfache Extraktion eines Strings zu {@code "key": "..."} aus
     * flachem JSON. Bewusst kein vollwertiger JSON-Parser (dafür müsste eine
     * zusätzliche Abhängigkeit eingebunden werden) - reicht für das exakt
     * vorgegebene, selbst kontrollierte Manifest-Format mit genau drei
     * flachen String-Feldern. Gibt {@code null} zurück, wenn der Schlüssel
     * nicht gefunden wird (z.B. optionales "notes"-Feld fehlt).
     */
    private static String extractJsonString(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\t", "\t");
    }

    /**
     * Vergleicht zwei Versionsnummern nach Punkt-getrennten, numerischen
     * Segmenten (z.B. "1.12" &gt; "1.9" &gt; "1.2"), robust gegenüber einem
     * führenden "v" sowie unterschiedlicher Segment-Anzahl (fehlende
     * Segmente zählen als 0). Nicht-numerische Reste je Segment werden
     * ignoriert, damit z.B. auch "1.11-SNAPSHOT" nicht zu einem Fehler führt.
     */
    static int compareVersions(String a, String b) {
        String[] partsA = normalize(a).split("\\.");
        String[] partsB = normalize(b).split("\\.");
        int length = Math.max(partsA.length, partsB.length);
        for (int i = 0; i < length; i++) {
            int valueA = i < partsA.length ? parseLeadingInt(partsA[i]) : 0;
            int valueB = i < partsB.length ? parseLeadingInt(partsB[i]) : 0;
            if (valueA != valueB) {
                return Integer.compare(valueA, valueB);
            }
        }
        return 0;
    }

    private static String normalize(String version) {
        String trimmed = version == null ? "" : version.trim();
        if (trimmed.startsWith("v") || trimmed.startsWith("V")) {
            trimmed = trimmed.substring(1);
        }
        return trimmed;
    }

    private static int parseLeadingInt(String segment) {
        Matcher matcher = Pattern.compile("\\d+").matcher(segment);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }
}
