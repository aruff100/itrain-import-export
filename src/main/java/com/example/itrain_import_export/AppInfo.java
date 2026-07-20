package com.example.itrain_import_export;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Liest die von Gradle beim Build erzeugte {@code build-info.properties}
 * (siehe {@code build.gradle.kts}, Task {@code processResources}) und
 * stellt Versions-/Build-Informationen für den Über-Dialog bereit. Die Datei
 * wird aus einer Vorlage mit {@code ${version}}/{@code ${buildTimestamp}}-
 * Platzhaltern erzeugt, die bei jedem Build frisch ersetzt werden - so zeigt
 * "Version" im Über-Dialog immer die aktuelle Build-Nummer.
 */
public final class AppInfo {

    private static final String RESOURCE_NAME = "build-info.properties";
    private static final String FALLBACK_VERSION = "1.0-SNAPSHOT";

    private static String cachedBuildNumber;

    private AppInfo() {
    }

    /**
     * "Version, Build-Zeitstempel", z.B. "1.0-SNAPSHOT (Build 20260720-1432)".
     * Falls die Datei nicht gefunden wird (z.B. bei einem sehr ungewöhnlichen
     * Klassenpfad-Setup), wird ersatzweise nur die im Code hinterlegte
     * Fallback-Version ohne Zeitstempel angezeigt.
     */
    public static synchronized String getBuildNumber() {
        if (cachedBuildNumber != null) {
            return cachedBuildNumber;
        }
        Properties props = new Properties();
        try (InputStream in = AppInfo.class.getResourceAsStream(RESOURCE_NAME)) {
            if (in != null) {
                props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
            // Fällt unten auf den Fallback-Wert zurück.
        }
        String version = props.getProperty("version", FALLBACK_VERSION);
        String buildTimestamp = props.getProperty("buildTimestamp");
        cachedBuildNumber = (buildTimestamp == null || buildTimestamp.isBlank() || buildTimestamp.startsWith("$"))
                ? version
                : version + " (Build " + buildTimestamp + ")";
        return cachedBuildNumber;
    }
}
