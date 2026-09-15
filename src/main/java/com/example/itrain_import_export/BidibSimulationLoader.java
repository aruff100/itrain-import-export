package com.example.itrain_import_export;

import org.bidib.jbidibc.messages.BidibLibrary;
import org.bidib.jbidibc.messages.utils.NodeUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Liest eine BiDiB-Simulationsdatei des BiDiB-Wizard ("Simulation
 * exportieren", z.B. bidib/BiDiB_Simulation.xml) in eine
 * {@link BidibSystemFile} - dieselbe Struktur wie beim Auslesen am Gerät:
 * <pre>
 * &lt;simulation&gt;
 *   &lt;master uniqueId="9000FBF601FD00" address="0" productName="BiDiB-IFnet" userName="IFNet Spur1"&gt;
 *     &lt;Features&gt;&lt;feature type="FEATURE_GEN_WATCHDOG" value="20"/&gt;...&lt;/Features&gt;
 *     &lt;subNodes&gt;
 *       &lt;node uniqueId="40003E93000080" address="1" productName="Hermes" userName="Belegtmelder 1/8"&gt;
 *         &lt;Features&gt;...&lt;/Features&gt;
 *       &lt;/node&gt;
 *       &lt;node xsi:type="HubType" address="10" ...&gt;&lt;subNodes&gt;...&lt;/subNodes&gt;&lt;/node&gt;
 * </pre>
 * Geprüft gegen das Auslesen desselben IFnet (14./15.09.): gleiche
 * Kennungen, Namen, Merkmale und Adressen; Knoten hinter einem Verteiler
 * bekommen die Punktadresse ("10.1"). Die Merkmale stehen in der Datei mit
 * Namen (FEATURE_BM_SIZE), am Gerät als Nummer - die Nummer kommt aus den
 * Konstanten der BiDiB-Bibliothek. Anschlussart und -zahl werden wie beim
 * Auslesen aus FEATURE_BM_SIZE / FEATURE_ACCESSORY_COUNT und den
 * Klassenbits der Kennung abgeleitet.
 */
public final class BidibSimulationLoader {

    private static final Map<String, Integer> FEATURE_NUMBERS = featureNumbers();

    private BidibSimulationLoader() {
    }

    public static boolean looksLikeSimulation(File file) {
        return file != null && file.getName().toLowerCase().endsWith(".xml");
    }

    public static BidibSystemFile load(File file) throws IOException {
        Document document;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            document = factory.newDocumentBuilder().parse(file);
        } catch (Exception ex) {
            throw new IOException("Keine BiDiB-Simulationsdatei: " + ex.getMessage(), ex);
        }
        Element root = document.getDocumentElement();
        Element master = firstChild(root, "master");
        if (master == null) {
            throw new IOException("Keine BiDiB-Simulationsdatei: <master> fehlt (" + file.getName() + ")");
        }
        BidibSystemFile result = new BidibSystemFile();
        result.setSystemType(BidibSystemFile.SYSTEM_BIDIB);
        result.setSerial(false);
        result.setHostPort("");
        BidibSystemFile.NodeRecord interfaceRecord = toRecord(master, "0");
        result.setInterfaceUniqueId(interfaceRecord.uniqueId());
        String name = interfaceRecord.userName() != null ? interfaceRecord.userName()
                : interfaceRecord.productName() != null ? interfaceRecord.productName() : "BiDiB";
        result.setInterfaceName(name);
        result.getNodes().add(interfaceRecord);
        addSubNodes(master, "", result.getNodes());
        // Die Datei ist die Quelle - Speichern legt dann eine eigene
        // System-Datei im Ordner an (getFile() bleibt null).
        return result;
    }

    private static void addSubNodes(Element parent, String addressPrefix, List<BidibSystemFile.NodeRecord> target) {
        Element subNodes = firstChild(parent, "subNodes");
        if (subNodes == null) {
            return;
        }
        for (Element node : children(subNodes, "node")) {
            String local = attr(node, "address");
            String address = addressPrefix.isEmpty() ? local : addressPrefix + "." + local;
            target.add(toRecord(node, address));
            addSubNodes(node, address, target);
        }
    }

    private static BidibSystemFile.NodeRecord toRecord(Element element, String address) {
        long uid = parseUid(attr(element, "uniqueId"));
        String user = emptyToNull(attr(element, "userName"));
        String product = emptyToNull(attr(element, "productName"));

        List<BidibSystemFile.FeatureRecord> features = new ArrayList<>();
        Integer feedbackPorts = null;
        Integer accessoryPorts = null;
        Element featuresElement = firstChild(element, "Features");
        if (featuresElement != null) {
            for (Element feature : children(featuresElement, "feature")) {
                String typeName = attr(feature, "type");
                int value = parseInt(attr(feature, "value"));
                int type = FEATURE_NUMBERS.getOrDefault(typeName, -1);
                features.add(new BidibSystemFile.FeatureRecord(type, typeName, value));
                if ("FEATURE_BM_SIZE".equals(typeName)) {
                    feedbackPorts = value;
                } else if ("FEATURE_ACCESSORY_COUNT".equals(typeName)) {
                    accessoryPorts = value;
                }
            }
        }
        features.sort(java.util.Comparator.comparingInt(BidibSystemFile.FeatureRecord::type));

        String portKind = null;
        Integer portCount = null;
        if (uid != 0L && NodeUtils.hasFeedbackFunctions(uid) && feedbackPorts != null && feedbackPorts > 0) {
            portKind = "FEEDBACK";
            portCount = feedbackPorts;
        } else if (uid != 0L && NodeUtils.hasAccessoryFunctions(uid) && accessoryPorts != null && accessoryPorts > 0) {
            portKind = "ACCESSORY";
            portCount = accessoryPorts;
        }
        return new BidibSystemFile.NodeRecord(uid, address, user, product, portKind, portCount, features);
    }

    private static long parseUid(String hex) {
        String t = hex == null ? "" : hex.trim();
        if (t.startsWith("0x") || t.startsWith("0X")) {
            t = t.substring(2);
        }
        if (t.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseUnsignedLong(t, 16);
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

    private static String attr(Element element, String name) {
        String value = element.getAttribute(name);
        return value == null ? "" : value;
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static Element firstChild(Element parent, String tag) {
        for (Element child : children(parent, tag)) {
            return child;
        }
        return null;
    }

    private static List<Element> children(Element parent, String tag) {
        List<Element> result = new ArrayList<>();
        NodeList list = parent.getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            Node node = list.item(i);
            if (node instanceof Element element) {
                String localName = element.getTagName();
                int colon = localName.indexOf(':');
                if (colon >= 0) {
                    localName = localName.substring(colon + 1);
                }
                if (localName.equals(tag)) {
                    result.add(element);
                }
            }
        }
        return result;
    }

    /** FEATURE_*-Konstanten der Bibliothek: Name -> Nummer. */
    private static Map<String, Integer> featureNumbers() {
        Map<String, Integer> map = new HashMap<>();
        for (Field field : BidibLibrary.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !field.getName().startsWith("FEATURE_")) {
                continue;
            }
            try {
                Object value = field.get(null);
                if (value instanceof Integer intValue) {
                    map.put(field.getName(), intValue);
                } else if (value instanceof Byte byteValue) {
                    map.put(field.getName(), byteValue & 0xFF);
                }
            } catch (IllegalAccessException ignored) {
                // ohne Nummer
            }
        }
        return map;
    }
}
