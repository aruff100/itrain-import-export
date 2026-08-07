package com.example.itrain_import_export;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Eine Decoder-Vorlage: der {@code <configuration>}-Block einer Lokomotive
 * bzw. eines Wagens (die CV-Werte des eingebauten Decoders samt ihrer
 * Beschreibungen), gespeichert als einzelne CSV-Datei.
 * <p>
 * <b>Dateiformat</b> - bewusst dasselbe 5-Spalten-CSV wie beim normalen
 * Export ({@code CategoryEditor.onExport}, siehe {@link CsvUtil}: Semikolon,
 * immer gequotet, UTF-8 mit BOM), damit es nur ein Format im Programm gibt:
 * <ol>
 * <li>Kategorie - hier immer {@link #CATEGORY_MARKER}. Der eigene Marker
 *     (statt "locomotives") ist wichtig: Eine Decoder-Vorlage enthält KEINEN
 *     vollständigen Katalog-Eintrag, sondern nur einen Teilbaum. Würde sie
 *     versehentlich über den normalen "Importieren"-Knopf eingelesen, käme
 *     ein {@code <configuration>}-Element als eigenständiger Eintrag in die
 *     Kategorie - die Datei ließe sich danach in iTrain nicht mehr öffnen.
 *     Beide Import-Wege prüfen deshalb diese Spalte (siehe
 *     {@code CategoryEditor.onImport}).</li>
 * <li>Typ - der Tag-Name, immer {@code configuration}.</li>
 * <li>Name - die <b>Bezeichnung der Vorlage</b>, z.B. "ESU LokSound 5".
 *     Genau dieser Text erscheint in der Vorlagen-Auswahl.</li>
 * <li>Beschreibung - freier Zusatztext (z.B. Hersteller, Baureihe, Datum).</li>
 * <li>XML - das komplette {@code <configuration>}-Fragment.</li>
 * </ol>
 * Eine Vorlagen-Datei enthält genau eine solche Datenzeile (plus Kopfzeile).
 */
public final class DecoderTemplate {

    /** Kategorie-Spalte einer Decoder-Vorlage - siehe Klassenkommentar. */
    public static final String CATEGORY_MARKER = "decoder-configuration";

    /** Tag-Name des Knotens, den eine Vorlage transportiert. */
    public static final String CONFIGURATION_TAG = "configuration";

    private final String name;
    private final String description;
    private final String xml;
    private final File file;

    private DecoderTemplate(String name, String description, String xml, File file) {
        this.name = name;
        this.description = description;
        this.xml = xml;
        this.file = file;
    }

    /** Bezeichnung der Vorlage (Spalte "Name"), z.B. "ESU LokSound 5". */
    public String getName() {
        return name;
    }

    /** Freier Zusatztext (Spalte "Beschreibung"), kann leer sein. */
    public String getDescription() {
        return description;
    }

    /** Datei, aus der die Vorlage stammt. */
    public File getFile() {
        return file;
    }

    /** Anzahl der {@code <parameter>}-Einträge - nur zur Anzeige. */
    public int getParameterCount() {
        try {
            return toConfigurationNode().getChildren().size();
        } catch (Exception ex) {
            return 0;
        }
    }

    /**
     * Baut aus dem gespeicherten XML einen frischen Knoten. Bewusst bei jedem
     * Aufruf neu, damit derselbe Vorlagen-Eintrag mehrfach (in verschiedene
     * Fahrzeuge) importiert werden kann, ohne dass sich die eingefügten
     * Knoten denselben Teilbaum teilen.
     */
    public XmlNode toConfigurationNode() throws Exception {
        return TcdDocument.xmlStringToNode(xml);
    }

    /** Kopfzeile einer Vorlagen-Datei (die Spaltentitel sind reine Lesehilfe). */
    private static List<String> header(I18n i18n) {
        return List.of("Kategorie", i18n.t("editor.columnType"), i18n.t("editor.columnName"),
                i18n.t("editor.columnDescription"), "XML");
    }

    /**
     * Schreibt einen {@code <configuration>}-Knoten als Vorlagen-Datei.
     * Der Knoten wird vorher tief kopiert, damit spätere Änderungen am
     * geöffneten Dokument die geschriebene Datei nicht mehr berühren.
     */
    public static void write(File target, String name, String description, XmlNode configurationNode)
            throws Exception {
        String xml = TcdDocument.nodeToXmlString(configurationNode.deepCopy());
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of(CATEGORY_MARKER, configurationNode.getTagName(),
                name == null ? "" : name,
                description == null ? "" : description,
                xml));
        CsvUtil.write(target, header(I18n.getInstance()), rows);
    }

    /**
     * Liest eine Vorlagen-Datei. Gibt {@code null} zurück, wenn die Datei
     * keine Decoder-Vorlage ist (falsche Spaltenzahl oder fehlender
     * {@link #CATEGORY_MARKER}) - so lassen sich im Vorlagen-Ordner
     * versehentlich abgelegte, normale Export-CSVs still überspringen,
     * statt mit einem Fehler abzubrechen.
     */
    public static DecoderTemplate read(File file) {
        try {
            List<List<String>> rows = CsvUtil.read(file);
            for (int i = 0; i < rows.size(); i++) {
                List<String> row = rows.get(i);
                if (row.size() < 5) {
                    continue;
                }
                if (!CATEGORY_MARKER.equals(row.get(0))) {
                    continue;
                }
                String name = row.get(2);
                if (name == null || name.isBlank()) {
                    // Ohne Bezeichnung wäre die Vorlage in der Auswahl nicht
                    // unterscheidbar - dann ersatzweise der Dateiname.
                    name = stripExtension(file.getName());
                }
                return new DecoderTemplate(name, row.get(3), row.get(4), file);
            }
        } catch (Exception ignored) {
            // Unlesbare Datei wird wie "keine Vorlage" behandelt.
        }
        return null;
    }

    /**
     * Prüft, ob eine bereits eingelesene CSV eine Decoder-Vorlage ist -
     * genutzt vom normalen Import, um solche Dateien abzuweisen (siehe
     * Klassenkommentar).
     */
    public static boolean looksLikeDecoderCsv(List<List<String>> rows) {
        for (List<String> row : rows) {
            if (!row.isEmpty() && CATEGORY_MARKER.equals(row.get(0))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Alle Vorlagen aus dem eingestellten Ordner, alphabetisch nach
     * Bezeichnung sortiert (Groß-/Kleinschreibung wird dabei ignoriert, damit
     * "ESU" und "esu" beieinander stehen). Ist kein Ordner eingestellt oder
     * existiert er nicht, kommt eine leere Liste zurück.
     */
    public static List<DecoderTemplate> loadAll() {
        List<DecoderTemplate> templates = new ArrayList<>();
        String dir = AppSettings.getInstance().getDecoderDirectory();
        if (dir == null || dir.isBlank()) {
            return templates;
        }
        File folder = new File(dir);
        if (!folder.isDirectory()) {
            return templates;
        }
        File[] files = folder.listFiles((d, fileName) -> fileName.toLowerCase().endsWith(".csv"));
        if (files == null) {
            return templates;
        }
        for (File file : files) {
            DecoderTemplate template = read(file);
            if (template != null) {
                templates.add(template);
            }
        }
        templates.sort(Comparator.comparing(t -> t.getName().toLowerCase()));
        return templates;
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    /**
     * Macht aus einer Bezeichnung einen für alle Betriebssysteme gültigen
     * Dateinamen (ohne Endung): unzulässige Zeichen werden zu "_".
     */
    public static String toFileName(String templateName) {
        String cleaned = templateName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return cleaned.isEmpty() ? "decoder" : cleaned;
    }
}
