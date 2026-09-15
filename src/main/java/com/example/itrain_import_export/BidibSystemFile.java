package com.example.itrain_import_export;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Eine "System-Datei" (seit 2.5): das, was ein BiDiB-Interface beim Auslesen
 * geliefert hat - das Interface selbst und alle erkannten Knoten mit ihren
 * Merkmalen und Anschlüssen -, dazu die daraus vorbereiteten iTrain-Objekte
 * (Schnittstelle, Booster, Rückmelder, Zubehör) samt ihrer Bearbeitung.
 * <p>
 * Abgelegt als ZIP mit drei CSV-Dateien im Ordner "Pfad für System-Dateien"
 * (siehe {@link AppSettings#getSystemFilesDirectory()}): {@code interface.csv}
 * (eine Zeile), {@code nodes.csv} (eine Zeile je Knoten, die Merkmale als
 * Liste in einer Zelle) und {@code objects.csv} (eine Zeile je vorbereitetem
 * Objekt). CSV statt eines eigenen Formats, damit die Datei sich
 * verschicken und in anderen Programmen (Tabellenkalkulation) lesen lässt;
 * ZIP, weil .csv in vielen Foren nicht als Anhang durchgeht.
 * <p>
 * Zweck: Die Anlage muss nicht angeschlossen sein, um die iTrain-Objekte
 * später zu bearbeiten oder zu exportieren - der Knotenbaum lässt sich aus
 * der Datei genauso öffnen wie nach dem Auslesen ({@link BidibNodeTreeWindow}).
 */
public final class BidibSystemFile {

    /** Dateiendung der System-Dateien. */
    public static final String EXTENSION = ".bidib.zip";

    private static final String ENTRY_INTERFACE = "interface.csv";
    private static final String ENTRY_NODES = "nodes.csv";
    private static final String ENTRY_OBJECTS = "objects.csv";
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** Ein Merkmal eines Knotens, wie BiDiB es meldet. */
    public record FeatureRecord(int type, String name, int value) {
    }

    /**
     * Ein Knoten, wie er beim Auslesen gemeldet wurde.
     *
     * @param uniqueId      Kennung (7 Byte), 0 wenn unbekannt; bei ECoS die Objektnummer
     * @param address       BiDiB-Adresse als Text ("0" ist das Interface selbst)
     * @param userName      am Gerät vergebener Name, darf null sein
     * @param productName   Produktname, darf null sein
     * @param portKind      "FEEDBACK", "ACCESSORY" oder null ohne Anschlüsse
     * @param portCount     Anzahl der Anschlüsse, oder null
     * @param features      Merkmale in gemeldeter Reihenfolge
     * @param portNames     Namen je Anschluss (ECoS: Name des Zubehörs), oder null
     * @param portAddresses iTrain-Adresse je Anschluss (ECoS), oder null
     * @param portTypes     Bauform je Anschluss (ECoS: aus dem Symbol), oder null
     */
    public record NodeRecord(long uniqueId, String address, String userName, String productName,
            String portKind, Integer portCount, List<FeatureRecord> features,
            List<String> portNames, List<String> portAddresses, List<String> portTypes) {

        /** Knoten ohne Anschlussdaten (BiDiB). */
        public NodeRecord(long uniqueId, String address, String userName, String productName,
                String portKind, Integer portCount, List<FeatureRecord> features) {
            this(uniqueId, address, userName, productName, portKind, portCount, features, null, null, null);
        }

        public String portName(int index) {
            return portNames != null && index < portNames.size() ? portNames.get(index) : null;
        }

        public String portAddress(int index) {
            return portAddresses != null && index < portAddresses.size() ? portAddresses.get(index) : null;
        }

        public String portType(int index) {
            return portTypes != null && index < portTypes.size() ? portTypes.get(index) : null;
        }
    }

    /** Systemart der Datei: "bidib" (Vorgabe) oder "ecos". */
    public static final String SYSTEM_BIDIB = "bidib";
    public static final String SYSTEM_ECOS = "ecos";

    /**
     * Ein vorbereitetes iTrain-Objekt - eine Zeile der Tabellen im
     * Systeme-Fenster. {@code xml} ist der fertige iTrain-Eintrag, so wie ihn
     * der Kategorie-Import erwartet; die bearbeitbaren Felder stehen
     * zusätzlich als eigene Spalten.
     *
     * @param bidibName Name, wie ihn das BiDiB-System liefert (Knotenname bzw.
     *                  Knotenname + Anschluss) - unveränderlich, nur Anzeige
     */
    public record ObjectRecord(String category, String type, String address, String bidibName, String name,
            boolean selected, String description, String length, String interfaceName, String xml,
            boolean typeDefault) {
    }

    private String systemType = SYSTEM_BIDIB;
    private String interfaceName = "";
    private long interfaceUniqueId;
    /** Adresse der Verbindung: "192.168.0.91:62875" oder "COM15". */
    private String hostPort = "";
    private boolean serial;
    private String savedAt = "";
    private final List<NodeRecord> nodes = new ArrayList<>();
    private final List<ObjectRecord> objects = new ArrayList<>();
    private File file;

    public String getSystemType() {
        return systemType;
    }

    public void setSystemType(String systemType) {
        this.systemType = SYSTEM_ECOS.equalsIgnoreCase(systemType) ? SYSTEM_ECOS : SYSTEM_BIDIB;
    }

    public boolean isEcos() {
        return SYSTEM_ECOS.equals(systemType);
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName == null ? "" : interfaceName;
    }

    public long getInterfaceUniqueId() {
        return interfaceUniqueId;
    }

    public void setInterfaceUniqueId(long interfaceUniqueId) {
        this.interfaceUniqueId = interfaceUniqueId;
    }

    public String getHostPort() {
        return hostPort;
    }

    public void setHostPort(String hostPort) {
        this.hostPort = hostPort == null ? "" : hostPort;
    }

    public boolean isSerial() {
        return serial;
    }

    public void setSerial(boolean serial) {
        this.serial = serial;
    }

    public String getSavedAt() {
        return savedAt;
    }

    public List<NodeRecord> getNodes() {
        return nodes;
    }

    public List<ObjectRecord> getObjects() {
        return objects;
    }

    /** Datei, aus der gelesen bzw. in die zuletzt geschrieben wurde - oder null. */
    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    /**
     * Vorschlag für den Dateinamen: Interface-Name (dateitauglich gemacht)
     * plus Zeitstempel - zwei Auslesungen desselben Interface überschreiben
     * sich so nicht.
     */
    public String suggestedFileName() {
        String base = interfaceName == null || interfaceName.isBlank() ? "BiDiB" : interfaceName;
        base = base.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return base + "_" + LocalDateTime.now().format(FILE_STAMP) + EXTENSION;
    }

    // ------------------------------------------------------------------
    // Schreiben
    // ------------------------------------------------------------------

    public void save(File target) throws IOException {
        savedAt = LocalDateTime.now().toString();
        Map<String, CsvUtil.CsvTable> entries = new LinkedHashMap<>();

        entries.put(ENTRY_INTERFACE, new CsvUtil.CsvTable(
                List.of("Name", "UID", "Adresse", "Seriell", "Gespeichert", "System"),
                List.of(List.of(interfaceName, uidText(interfaceUniqueId), hostPort,
                        serial ? "ja" : "nein", savedAt, systemType))));

        List<List<String>> nodeRows = new ArrayList<>();
        for (NodeRecord node : nodes) {
            StringBuilder features = new StringBuilder();
            for (FeatureRecord feature : node.features()) {
                if (features.length() > 0) {
                    features.append('|');
                }
                features.append(feature.type()).append(':').append(nz(feature.name()).replace('|', '/'))
                        .append('=').append(feature.value());
            }
            nodeRows.add(List.of(nz(node.address()), uidText(node.uniqueId()), nz(node.userName()),
                    nz(node.productName()), nz(node.portKind()),
                    node.portCount() == null ? "" : String.valueOf(node.portCount()), features.toString(),
                    joinList(node.portNames()), joinList(node.portAddresses()), joinList(node.portTypes())));
        }
        entries.put(ENTRY_NODES, new CsvUtil.CsvTable(
                List.of("Adresse", "UID", "Benutzername", "Produktname", "Anschlussart", "Anschluesse", "Merkmale",
                        "Anschlussnamen", "Anschlussadressen", "Anschlusstypen"),
                nodeRows));

        List<List<String>> objectRows = new ArrayList<>();
        for (ObjectRecord object : objects) {
            objectRows.add(List.of(nz(object.category()), nz(object.type()), nz(object.address()),
                    nz(object.bidibName()), nz(object.name()), object.selected() ? "ja" : "nein",
                    nz(object.description()), nz(object.length()), nz(object.interfaceName()), nz(object.xml()),
                    object.typeDefault() ? "ja" : "nein"));
        }
        entries.put(ENTRY_OBJECTS, new CsvUtil.CsvTable(
                List.of("Kategorie", "Typ", "Adresse", "BiDiB-Name", "Name", "Ausgewaehlt", "Beschreibung",
                        "Laenge", "Schnittstelle", "XML", "Typ-Voreinstellung"),
                objectRows));

        File parent = target.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Ordner kann nicht angelegt werden: " + parent);
        }
        CsvUtil.writeZipped(target, entries);
        file = target;
    }

    // ------------------------------------------------------------------
    // Lesen
    // ------------------------------------------------------------------

    public static BidibSystemFile load(File source) throws IOException {
        Map<String, List<List<String>>> entries = CsvUtil.readZipEntries(source);
        List<List<String>> iface = entries.get(ENTRY_INTERFACE);
        List<List<String>> nodeRows = entries.get(ENTRY_NODES);
        if (iface == null || nodeRows == null) {
            throw new IOException("Keine System-Datei: " + source.getName());
        }
        BidibSystemFile result = new BidibSystemFile();
        result.file = source;
        if (iface.size() > 1) {
            List<String> row = iface.get(1);
            result.interfaceName = at(row, 0);
            result.interfaceUniqueId = parseUid(at(row, 1));
            result.hostPort = at(row, 2);
            result.serial = "ja".equalsIgnoreCase(at(row, 3));
            result.savedAt = at(row, 4);
            result.setSystemType(at(row, 5));
        }
        for (int i = 1; i < nodeRows.size(); i++) {
            List<String> row = nodeRows.get(i);
            List<FeatureRecord> features = new ArrayList<>();
            String featureText = at(row, 6);
            if (!featureText.isEmpty()) {
                for (String part : featureText.split("\\|")) {
                    int colon = part.indexOf(':');
                    int equals = part.lastIndexOf('=');
                    if (colon < 0 || equals < colon) {
                        continue;
                    }
                    features.add(new FeatureRecord(parseInt(part.substring(0, colon)),
                            part.substring(colon + 1, equals), parseInt(part.substring(equals + 1))));
                }
            }
            String portCountText = at(row, 5);
            result.nodes.add(new NodeRecord(parseUid(at(row, 1)), at(row, 0), nullIfEmpty(at(row, 2)),
                    nullIfEmpty(at(row, 3)), nullIfEmpty(at(row, 4)),
                    portCountText.isEmpty() ? null : parseInt(portCountText), features,
                    splitList(at(row, 7)), splitList(at(row, 8)), splitList(at(row, 9))));
        }
        List<List<String>> objectRows = entries.get(ENTRY_OBJECTS);
        if (objectRows != null) {
            for (int i = 1; i < objectRows.size(); i++) {
                List<String> row = objectRows.get(i);
                result.objects.add(new ObjectRecord(at(row, 0), at(row, 1), at(row, 2), at(row, 3), at(row, 4),
                        "ja".equalsIgnoreCase(at(row, 5)), at(row, 6), at(row, 7), at(row, 8), at(row, 9),
                        "ja".equalsIgnoreCase(at(row, 10))));
            }
        }
        return result;
    }

    /** Alle System-Dateien im konfigurierten Ordner, neueste zuerst - oder eine leere Liste. */
    public static List<File> listSystemFiles() {
        List<File> result = new ArrayList<>();
        String dir = AppSettings.getInstance().getSystemFilesDirectory();
        if (dir == null || dir.isBlank()) {
            return result;
        }
        File[] files = new File(dir).listFiles((d, name) -> name.toLowerCase().endsWith(EXTENSION));
        if (files != null) {
            for (File f : files) {
                result.add(f);
            }
            result.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        }
        return result;
    }

    /** Liste mit "|" verbinden (leer, wenn null); "|" im Text wird zu "/". */
    private static String joinList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String v : values) {
            if (sb.length() > 0) {
                sb.append('|');
            }
            sb.append(nz(v).replace('|', '/'));
        }
        return sb.toString();
    }

    private static List<String> splitList(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        return new ArrayList<>(List.of(text.split("\\|", -1)));
    }

    private static String uidText(long uid) {
        return uid == 0L ? "" : String.format("0x%014X", uid);
    }

    private static long parseUid(String text) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseUnsignedLong(t.startsWith("0x") || t.startsWith("0X") ? t.substring(2) : t, 16);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private static int parseInt(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static String at(List<String> row, int index) {
        return index < row.size() && row.get(index) != null ? row.get(index) : "";
    }

    private static String nullIfEmpty(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
