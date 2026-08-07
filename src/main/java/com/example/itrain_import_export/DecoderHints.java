package com.example.itrain_import_export;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Lädt den Hinweistext zur Decoder-Funktion aus einer <b>von Hand
 * editierbaren</b> Datei.
 *
 * <h2>Wo die Datei gesucht wird</h2>
 * Je Sprache eine Datei {@code decoder-hints_<code>.txt} (z.B.
 * {@code decoder-hints_de.txt}). Gesucht wird in dieser Reihenfolge:
 * <ol>
 *   <li>im aktuellen Arbeitsverzeichnis - dort landet die Datei beim
 *       Entpacken der Programm-Fassung, und Änderungen wirken sofort, ohne
 *       Neu-Build;</li>
 *   <li>ersatzweise die mitgelieferte Kopie aus den Ressourcen;</li>
 *   <li>gibt es für die eingestellte Sprache nichts, wird auf Deutsch
 *       zurückgefallen - besser ein Text in der falschen Sprache als ein
 *       leerer Dialog.</li>
 * </ol>
 * Dasselbe Muster wie bei {@code translations.properties} (siehe {@link I18n}).
 * Bewusst eine eigene Datei statt eines Schlüssels dort: Der Text ist mehrere
 * Absätze lang, und in einer Properties-Datei müsste er als eine einzige Zeile
 * mit {@code \n} geschrieben werden - unbequem genau für den Zweck, zu dem er
 * da ist, nämlich vom Anwender bearbeitet zu werden.
 *
 * <h2>Format</h2>
 * Reiner Text in UTF-8, mit derselben einfachen Auszeichnung wie die
 * Hilfetexte (siehe {@link HelpDialog}):
 * <ul>
 *   <li>{@code **fett**} für hervorgehobene Stellen,</li>
 *   <li>eine Zeile mit {@code "- "} am Anfang wird zum Aufzählungspunkt,</li>
 *   <li>eine Leerzeile beginnt einen neuen Absatz.</li>
 * </ul>
 * Zeilen, die mit {@code #} beginnen, sind Kommentare für den Bearbeiter und
 * werden nicht angezeigt.
 */
public final class DecoderHints {

    private static final String FILE_PREFIX = "decoder-hints_";
    private static final String FILE_SUFFIX = ".txt";

    /** Sprache, auf die zurückgefallen wird, wenn es die eingestellte nicht gibt. */
    private static final String FALLBACK_LANGUAGE = "de";

    private DecoderHints() {
    }

    /**
     * Liefert den Hinweistext für die aktuell eingestellte Sprache. Nie
     * {@code null}: Fehlt jede Datei, kommt ein kurzer Hinweis darauf zurück,
     * wie die Datei heißen müsste - das ist aussagekräftiger als ein leerer
     * Dialog, bei dem man rätselt, ob das Programm kaputt ist.
     */
    public static String text() {
        String language = I18n.getInstance().getCurrentLanguage();
        String text = read(language);
        if (text == null && !FALLBACK_LANGUAGE.equals(language)) {
            text = read(FALLBACK_LANGUAGE);
        }
        if (text == null) {
            return I18n.getInstance().t("decoderHints.missing", fileName(language));
        }
        return text;
    }

    /** Dateiname für eine Sprache - auch für die Meldung, wenn nichts gefunden wurde. */
    public static String fileName(String language) {
        return FILE_PREFIX + language + FILE_SUFFIX;
    }

    /**
     * Liest die Datei einer Sprache, oder {@code null}, wenn es sie weder im
     * Arbeitsverzeichnis noch in den Ressourcen gibt (oder sie leer ist).
     */
    private static String read(String language) {
        String name = fileName(language);
        File external = new File(name);
        try (InputStream in = external.isFile()
                ? new FileInputStream(external)
                : DecoderHints.class.getResourceAsStream(name)) {
            if (in == null) {
                return null;
            }
            String content = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .lines()
                    // Kommentarzeilen des Bearbeiters entfernen. Das Rautezeichen
                    // muss am Zeilenanfang stehen; mitten im Text ist es ein
                    // gewöhnliches Zeichen (CV-Nummern werden gern "#8" geschrieben).
                    .filter(line -> !line.startsWith("#"))
                    .collect(Collectors.joining("\n"));
            return content.isBlank() ? null : content.strip();
        } catch (IOException ex) {
            System.err.println("Decoder-Hinweise " + name + " konnten nicht gelesen werden: "
                    + ex.getMessage());
            return null;
        }
    }
}
