package com.example.itrain_import_export;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Lädt und speichert iTrain-.tcd-Dateien.
 * <p>
 * Der komplette Dateiinhalt wird in einen generischen {@link XmlNode}-Baum
 * geladen. Bearbeitet werden in der Oberfläche ausschließlich die 13
 * Kategorien unterhalb von control-items. Alle anderen Bereiche
 * (settings, clock, control-state, switchboards, sowie alles, was in
 * künftigen iTrain-Versionen zusätzlich dazukommt) werden unangetastet
 * mitgeführt und beim Speichern 1:1 in ursprünglicher Reihenfolge wieder
 * ausgegeben – so gehen keine Daten verloren.
 */
public class TcdDocument {

    /**
     * Feste, von iTrain erwartete Reihenfolge der control-items-Kategorien.
     * Verifiziert anhand einer Referenzdatei, die alle 15 Kategorien mit je
     * einem Beispiel-Eintrag enthält: functions, interfaces, feedbacks,
     * accessories, memory, boosters, blocks, train-types, stations,
     * train-routes, locomotives, wagons, trains, actions, measurements.
     * "memory" (Speicher-/Rangiergleise, Kind-Element &lt;track&gt;) und
     * "boosters" kommen nicht in jeder Anlage vor (z.B. "boosters" nur bei
     * BiDiB/z21-Interfaces), gehören aber an diese feste Position.
     */
    public static final List<String> CONTROL_ITEM_ORDER = List.of(
            "functions", "interfaces", "feedbacks", "accessories", "memory",
            "boosters", "blocks", "train-types", "stations", "train-routes",
            "locomotives", "wagons", "trains", "actions", "measurements"
    );

    /**
     * Reihenfolge, in der die Reiter in der Oberfläche angezeigt werden
     * (folgt der Kategorie-Übersicht in iTrain selbst) - unabhängig von
     * {@link #CONTROL_ITEM_ORDER}, die nur für das Speichern auf der
     * Festplatte gilt.
     */
    public static final List<String> TAB_DISPLAY_ORDER = List.of(
            "functions", "interfaces", "boosters", "feedbacks", "measurements",
            "accessories", "memory", "actions", "locomotives", "wagons",
            "trains", "train-types", "train-routes", "blocks", "stations"
    );

    /** Feste, von iTrain erwartete Reihenfolge der Kinder von train-control. */
    public static final List<String> ROOT_ORDER = List.of(
            "settings", "clock", "control-items", "control-state", "switchboards"
    );

    private XmlNode root;
    private File file;

    /** true, sobald über die Oberfläche tatsächlich ein Wert geändert wurde. */
    private boolean dirty = false;
    /** true, sobald dieses Dokument mindestens einmal gespeichert wurde. */
    private boolean savedSinceOpen = false;
    /**
     * Pfad der beim Öffnen angelegten Sicherheitskopie (siehe
     * {@link HelloController}), oder null falls kein Backup-Ordner
     * eingestellt war. Wird gelöscht, falls beim Schließen/Wechseln der
     * Datei weder {@link #dirty} noch {@link #savedSinceOpen} zutrifft.
     */
    private File backupFile;

    public XmlNode getRoot() {
        return root;
    }

    public File getFile() {
        return file;
    }

    /** Markiert das Dokument als über die Oberfläche geändert (siehe {@link CategoryEditor}). */
    public void markDirty() {
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    /** true, sobald {@link #save(File)} mindestens einmal erfolgreich lief. */
    public boolean wasSavedSinceOpen() {
        return savedSinceOpen;
    }

    public void setBackupFile(File backupFile) {
        this.backupFile = backupFile;
    }

    public File getBackupFile() {
        return backupFile;
    }

    public static TcdDocument load(File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // Namespace-unaware: das einzige xmlns-Attribut am Wurzelelement wird
        // dadurch wie ein ganz normales Attribut behandelt und 1:1
        // mitgeführt/wieder ausgegeben, ohne dass wir uns um Namespace-APIs
        // kümmern müssen.
        factory.setNamespaceAware(false);
        DocumentBuilder builder = factory.newDocumentBuilder();

        TcdDocument tcd = new TcdDocument();
        tcd.file = file;

        Document document;
        if (isZipFile(file)) {
            try (ZipFile zipFile = new ZipFile(file)) {
                List<ZipEntry> entries = new ArrayList<>();
                Enumeration<? extends ZipEntry> e = zipFile.entries();
                ZipEntry tcdEntry = null;
                while (e.hasMoreElements()) {
                    ZipEntry entry = e.nextElement();
                    entries.add(entry);
                    if (entry.getName().toLowerCase().endsWith(".tcd")) {
                        tcdEntry = entry;
                    }
                }
                if (tcdEntry == null && entries.size() == 1) {
                    tcdEntry = entries.get(0);
                }
                if (tcdEntry == null) {
                    throw new IOException("Im ZIP-Archiv \"" + file.getName() + "\" wurde keine .tcd-Datei gefunden.");
                }
                try (InputStream in = zipFile.getInputStream(tcdEntry)) {
                    document = builder.parse(in);
                }
            }
        } else {
            document = builder.parse(file);
        }
        document.getDocumentElement().normalize();
        tcd.root = toXmlNode(document.getDocumentElement());
        return tcd;
    }

    /** .tcdz-Dateien sind normale ZIP-Archive mit einer .tcd-Datei darin. */
    private static boolean isZipFile(File file) {
        return file.getName().toLowerCase().endsWith(".tcdz");
    }

    private static XmlNode toXmlNode(Element element) {
        XmlNode node = new XmlNode(element.getTagName());

        NamedNodeMap attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Attr attr = (Attr) attrs.item(i);
            node.setAttribute(attr.getName(), attr.getValue());
        }

        StringBuilder text = null;
        NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                node.getChildren().add(toXmlNode((Element) child));
            } else if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                String value = ((Text) child).getData();
                if (!value.isBlank()) {
                    if (text == null) {
                        text = new StringBuilder();
                    }
                    text.append(value);
                }
            }
        }
        if (text != null) {
            node.setTextContent(text.toString().trim());
        }
        return node;
    }

    /**
     * Speichert das Dokument nach {@code target}. Reihenfolge von
     * train-control-Kindern und control-items-Kategorien wird erzwungen;
     * alles andere bleibt exakt wie geladen (bzw. wie über die Oberfläche
     * bearbeitet) erhalten.
     * <p>
     * Endet {@code target} auf ".tcdz", wird die XML-Datei wieder in ein
     * ZIP-Archiv verpackt (wie beim Laden erwartet); bei ".tcd" (oder jeder
     * anderen Endung) wird reines XML geschrieben. Das erlaubt sowohl
     * "tcdz bleibt tcdz" als auch bei Bedarf eine Umwandlung.
     * <p>
     * Ablauf zur Absicherung (u.a. gegen unlesbare Dateien nach einem
     * Import): das XML wird zunächst nur im Arbeitsspeicher erzeugt und
     * probeweise wieder eingelesen ({@link #verifyWellFormed}). Erst wenn
     * das klappt, wird über eine temporäre Datei im selben Ordner und ein
     * atomares Umbenennen geschrieben – die bisherige Zieldatei wird also
     * nie durch einen halb geschriebenen oder fehlerhaften Stand ersetzt.
     */
    public void save(File target) throws Exception {
        reorderChildren(root, ROOT_ORDER);
        XmlNode controlItems = root.findChild("control-items");
        if (controlItems != null) {
            reorderChildren(controlItems, CONTROL_ITEM_ORDER);
            recomputeCategoryCounts(controlItems);
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        Document document = factory.newDocumentBuilder().newDocument();
        Element rootElement = toElement(root, document);
        document.appendChild(rootElement);

        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        ByteArrayOutputStream xmlBuffer = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(document), new StreamResult(xmlBuffer));
        byte[] xmlBytes = xmlBuffer.toByteArray();

        verifyWellFormed(xmlBytes, controlItems);

        boolean zipped = isZipFile(target);
        // Der interne .tcd-Eintragsname richtet sich immer nach dem Namen der
        // Zieldatei (so wie es auch iTrain selbst handhabt) - NICHT nach dem
        // Namen, unter dem die Datei ursprünglich geladen wurde. Sonst würde
        // z.B. nach "Speichern als 1A.tcdz" beim Entpacken weiterhin eine
        // "1.tcd" (Name der geladenen Datei) entstehen statt der erwarteten
        // "1A.tcd" - das war ein Bug.
        String entryName = zipped ? withoutExtension(target.getName()) + ".tcd" : null;

        File parentDir = target.getAbsoluteFile().getParentFile();
        File tempFile = File.createTempFile("tcd-save-", ".tmp", parentDir);
        try {
            if (zipped) {
                try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempFile.toPath()))) {
                    zos.putNextEntry(new ZipEntry(entryName));
                    zos.write(xmlBytes);
                    zos.closeEntry();
                }
            } else {
                Files.write(tempFile.toPath(), xmlBytes);
            }

            try {
                Files.move(tempFile.toPath(), target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tempFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tempFile.toPath());
        }

        this.file = target;
        this.savedSinceOpen = true;
    }

    /**
     * Prüft, dass das gerade erzeugte XML tatsächlich wieder einlesbar ist
     * und die erwarteten Kategorien/Anzahlen enthält, bevor irgendetwas auf
     * der Festplatte verändert wird.
     */
    private static void verifyWellFormed(byte[] xmlBytes, XmlNode expectedControlItems) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document check = builder.parse(new ByteArrayInputStream(xmlBytes));
        Element checkRoot = check.getDocumentElement();
        if (checkRoot == null || !"train-control".equals(checkRoot.getTagName())) {
            throw new IOException("Interner Fehler: die erzeugte Datei hat kein gültiges train-control-Wurzelelement.");
        }
        if (expectedControlItems != null) {
            // Wichtig: nur DIREKTE Kinder betrachten (findDirectChild), nicht
            // getElementsByTagName - einige Tag-Namen wie "actions" oder
            // "feedbacks" kommen sowohl als Top-Level-Kategorie als auch
            // verschachtelt (z.B. <train><actions>...) vor.
            Element checkControlItems = findDirectChild(checkRoot, "control-items");
            if (checkControlItems == null) {
                throw new IOException("Interner Fehler: control-items fehlt in der erzeugten Datei.");
            }
            for (String tag : CONTROL_ITEM_ORDER) {
                XmlNode category = expectedControlItems.findChild(tag);
                if (category == null) {
                    continue;
                }
                int expectedCount = category.getChildren().size();
                Element checkCategory = findDirectChild(checkControlItems, tag);
                if (checkCategory == null) {
                    throw new IOException("Interner Fehler: Kategorie \"" + tag + "\" fehlt in der erzeugten Datei.");
                }
                int actualCount = countChildElements(checkCategory);
                if (actualCount != expectedCount) {
                    throw new IOException("Interner Fehler: Kategorie \"" + tag + "\" hat " + actualCount
                            + " statt " + expectedCount + " Einträge in der erzeugten Datei.");
                }
            }
        }
    }

    private static Element findDirectChild(Element parent, String tag) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && ((Element) n).getTagName().equals(tag)) {
                return (Element) n;
            }
        }
        return null;
    }

    private static int countChildElements(Element element) {
        int count = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
                count++;
            }
        }
        return count;
    }

    private static String withoutExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    /**
     * Bringt NUR die Kinder mit bekanntem Tag-Namen relativ zueinander in
     * die vorgegebene Reihenfolge (stabiler Teil-Sort). Kinder mit
     * unbekanntem Tag-Namen bleiben exakt an ihrer ursprünglichen Position
     * stehen und werden NICHT ans Ende verschoben.
     * <p>
     * Das ist wichtig, weil manche iTrain-Konfigurationen zusätzliche,
     * uns nicht bekannte Kategorien enthalten (z.B. "boosters" bei
     * BiDiB/z21-Anlagen, das bei DR5000/YD7001-Anlagen fehlt). Frühere
     * Versionen haben solche unbekannten Kategorien ans Ende von
     * control-items verschoben - dadurch landete z.B. die Booster-Definition
     * hinter allen Blöcken, die den Booster per Name referenzieren, und
     * iTrain konnte die Datei danach nicht mehr öffnen ("Wrong attribute
     * value ... in tag 'booster'"). Jetzt bleibt jede unbekannte Kategorie
     * exakt dort, wo sie war.
     */
    private static void reorderChildren(XmlNode parent, List<String> order) {
        List<XmlNode> current = new ArrayList<>(parent.getChildren());
        LinkedHashSet<String> known = new LinkedHashSet<>(order);

        List<XmlNode> sortedKnown = new ArrayList<>();
        for (String tag : order) {
            for (XmlNode child : current) {
                if (child.getTagName().equals(tag)) {
                    sortedKnown.add(child);
                }
            }
        }

        List<XmlNode> result = new ArrayList<>(current.size());
        int knownIndex = 0;
        for (XmlNode child : current) {
            if (known.contains(child.getTagName())) {
                result.add(sortedKnown.get(knownIndex));
                knownIndex++;
            } else {
                result.add(child);
            }
        }
        parent.getChildren().setAll(result);
    }

    /**
     * Aktualisiert das count-Attribut der 13 bekannten Kategorien auf die
     * tatsächliche Anzahl ihrer Einträge (nur wenn das Attribut im
     * geladenen Original bereits vorhanden war). Tiefer verschachtelte
     * count-Attribute (z.B. innerhalb eines Blocks) werden bewusst NICHT
     * angefasst, da deren Bedeutung je nach Kontext variiert.
     */
    private static void recomputeCategoryCounts(XmlNode controlItems) {
        for (String tag : CONTROL_ITEM_ORDER) {
            XmlNode category = controlItems.findChild(tag);
            if (category != null && category.getAttribute("count") != null) {
                category.setAttribute("count", String.valueOf(category.getChildren().size()));
            }
        }
    }

    /**
     * Serialisiert einen einzelnen Knoten (z.B. einen Block) als
     * eigenständiges XML-Fragment (ohne Deklaration). Wird für den
     * Export einzelner Einträge in eine CSV-Zelle verwendet.
     */
    public static String nodeToXmlString(XmlNode node) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        Document document = factory.newDocumentBuilder().newDocument();
        Element element = toElement(node, document);
        document.appendChild(element);

        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString();
    }

    /**
     * Kehrt {@link #nodeToXmlString(XmlNode)} um – liest ein einzelnes
     * XML-Fragment (wie es beim Export in einer CSV-Zelle abgelegt wurde)
     * wieder in einen {@link XmlNode}-Baum ein. Wird beim Import
     * verwendet.
     */
    public static XmlNode xmlStringToNode(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(new InputSource(new StringReader(xml)));
        document.getDocumentElement().normalize();
        return toXmlNode(document.getDocumentElement());
    }

    private static Element toElement(XmlNode node, Document document) {
        Element element = document.createElement(node.getTagName());
        for (var entry : node.getAttributes().entrySet()) {
            element.setAttribute(entry.getKey(), entry.getValue());
        }
        if (node.getChildren().isEmpty() && node.getTextContent() != null && !node.getTextContent().isEmpty()) {
            element.appendChild(document.createTextNode(node.getTextContent()));
        } else {
            for (XmlNode child : node.getChildren()) {
                element.appendChild(toElement(child, document));
            }
        }
        return element;
    }
}
