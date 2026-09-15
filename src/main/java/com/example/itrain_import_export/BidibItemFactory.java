package com.example.itrain_import_export;

import org.bidib.jbidibc.messages.Node;
import org.bidib.jbidibc.messages.utils.NodeUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Baut aus einem erkannten BiDiB-Knoten die passenden iTrain-Einträge
 * (Rückmelder, Zubehör, Booster, Schnittstelle) als {@link XmlNode}-Fragmente.
 * Diese Fragmente landen anschließend in einer CSV-Datei im Format des
 * normalen Kategorie-Exports (siehe {@link BidibNodeTreeWindow}) und lassen
 * sich damit über den gewohnten Import in eine iTrain-Datei übernehmen.
 *
 * <h2>Die ID-Umrechnung - der Kern des Ganzen</h2>
 *
 * jbidibc führt die Unique-ID eines Knotens als 7 Bytes, iTrain schreibt sie
 * als 16 Hex-Stellen (8 Bytes). Die Umrechnung wurde aus einer echten,
 * von iTrain selbst geschriebenen Datei abgelesen:
 * <pre>
 *   jbidibc: 0x40000D8600FAF3        (Bytes 40 00 0D 86 00 FA F3)
 *   iTrain:  0xF3FA00860D004000      (Bytes F3 FA 00 86 0D 00 40 00)
 * </pre>
 * Also: <b>Byte-Reihenfolge umdrehen, dann ein weiteres Byte anhängen.</b>
 * Dieses achte Byte gehört NICHT zur Kennung, sondern sagt, um welche Art
 * Eintrag es sich handelt:
 * <ul>
 * <li>{@code 00} - der Knoten selbst (in {@code <interfaces><nodes>}) sowie Booster</li>
 * <li>{@code 94} - Rückmelder, dahinter {@code .Port}</li>
 * <li>{@code 9E} - Zubehör, dahinter {@code .Port}</li>
 * </ul>
 * Die Portnummer hinter dem Punkt ist zero-basiert ({@code .0} bis
 * {@code .15} bei einem 16-fach-Rückmelder).
 *
 * <h2>Woran die Art eines Knotens hängt</h2>
 *
 * An den Klassenbits im ersten Byte der Unique-ID, ausgewertet über die
 * Hilfsmethoden von jbidibc ({@code NodeUtils.hasFeedbackFunctions} usw.).
 * Ein Knoten kann MEHRERE Rollen gleichzeitig haben - das Interface im
 * Testaufbau ist zugleich Gleisausgang und Verteiler und steht in der echten
 * iTrain-Datei folgerichtig zweimal in der Knotenliste, einmal als
 * {@code type="track"} und einmal als {@code type="hub"}.
 */
public final class BidibItemFactory {

    /** Typ-Byte für Rückmelder-Einträge (siehe Klassenkommentar). */
    private static final int TYPE_BYTE_FEEDBACK = 0x94;

    /** Typ-Byte für Zubehör-Einträge (siehe Klassenkommentar). */
    private static final int TYPE_BYTE_ACCESSORY = 0x9E;

    /** Typ-Byte für den Knoten selbst und für Booster. */
    private static final int TYPE_BYTE_NODE = 0x00;

    /**
     * Reihenfolge, in der iTrain die Rollen in der Knotenliste aufführt -
     * abgelesen an einer echten, von iTrain geschriebenen Datei. Der
     * Verteiler ("hub") steht dort ganz am Ende, auch wenn es derselbe Knoten
     * ist wie der Gleisausgang am Anfang.
     */
    private static final List<String> ROLE_ORDER = List.of("track", "booster", "feedback", "accessory", "hub");

    private BidibItemFactory() {
    }

    // ------------------------------------------------------------------
    // ID-Umrechnung
    // ------------------------------------------------------------------

    /**
     * Rechnet eine jbidibc-Unique-ID in die iTrain-Schreibweise um.
     *
     * @param uniqueId 7-Byte-Kennung, wie jbidibc sie in {@code Node.getUniqueId()} führt
     * @param typeByte abschließendes Kennzeichnungsbyte (siehe Klassenkommentar)
     * @return z.B. {@code 0xF3FA00860D004094}
     */
    public static String formatItrainId(long uniqueId, int typeByte) {
        byte[] bytes = new byte[7];
        for (int i = 0; i < 7; i++) {
            // Byte 0 der jbidibc-Kennung ist das höchstwertige der 7 Bytes.
            bytes[i] = (byte) ((uniqueId >> (8 * (6 - i))) & 0xFF);
        }
        StringBuilder sb = new StringBuilder("0x");
        // Rückwärts durchlaufen - genau das ist die Umkehrung der Reihenfolge.
        for (int i = 6; i >= 0; i--) {
            sb.append(String.format("%02X", bytes[i]));
        }
        sb.append(String.format("%02X", typeByte & 0xFF));
        return sb.toString();
    }

    /** Kennung eines Rückmelder-Anschlusses, z.B. {@code 0xF3FA00860D004094.0}. */
    public static String feedbackId(long uniqueId, int port) {
        return formatItrainId(uniqueId, TYPE_BYTE_FEEDBACK) + "." + port;
    }

    /** Kennung eines Zubehör-Anschlusses, z.B. {@code 0xE60500983E00049E.1}. */
    public static String accessoryId(long uniqueId, int port) {
        return formatItrainId(uniqueId, TYPE_BYTE_ACCESSORY) + "." + port;
    }

    /** Kennung des Knotens selbst - so steht sie in der Knotenliste und beim Booster. */
    public static String nodeId(long uniqueId) {
        return formatItrainId(uniqueId, TYPE_BYTE_NODE);
    }

    // ------------------------------------------------------------------
    // Knotenarten
    // ------------------------------------------------------------------

    /**
     * Alle Rollen eines Knotens, in der Reihenfolge, in der iTrain sie in der
     * Knotenliste führt. Mehrere Rollen je Knoten sind normal (siehe
     * Klassenkommentar).
     */
    public static List<String> nodeTypes(long uniqueId) {
        List<String> types = new ArrayList<>();
        if (NodeUtils.hasCommandStationFunctions(uniqueId)) {
            types.add("track");
        }
        if (NodeUtils.hasBoosterFunctions(uniqueId)) {
            types.add("booster");
        }
        if (NodeUtils.hasFeedbackFunctions(uniqueId)) {
            types.add("feedback");
        }
        if (NodeUtils.hasAccessoryFunctions(uniqueId)) {
            types.add("accessory");
        }
        if (NodeUtils.hasSubNodesFunctions(uniqueId)) {
            types.add("hub");
        }
        return types;
    }

    /**
     * Hersteller- und Produktkennung, wie iTrain sie als Kommentar in die
     * Knotenliste schreibt ({@code VID=FB, PID=F6011E00}). Rein informativ.
     */
    public static String vendorProductComment(long uniqueId) {
        int vendor = (int) ((uniqueId >> 32) & 0xFF);
        long product = uniqueId & 0xFFFFFFFFL;
        return String.format("VID=%02X, PID=%08X", vendor, product);
    }

    // ------------------------------------------------------------------
    // Einträge bauen
    // ------------------------------------------------------------------

    /**
     * Rückmelder-Eintrag für einen Anschluss.
     *
     * @param interfaceName Name der Schnittstelle, wie sie in der iTrain-Datei
     *                      heißt - iTrain verknüpft den Rückmelder darüber
     *                      mit der Verbindung, ein falscher Name macht den
     *                      Eintrag unbrauchbar
     */
    public static XmlNode createFeedback(String name, long uniqueId, int port, String interfaceName) {
        XmlNode feedback = new XmlNode("feedback");
        feedback.setAttribute("name", name);
        feedback.setAttribute("selected", "false");
        feedback.setAttribute("type", "occupancy");

        XmlNode id = new XmlNode("id");
        id.setTextContent(feedbackId(uniqueId, port));
        feedback.getChildren().add(id);

        XmlNode iface = new XmlNode("interface");
        iface.setAttribute("device", "bidib_occupancy");
        iface.setAttribute("name", interfaceName);
        feedback.getChildren().add(iface);

        feedback.getChildren().add(new XmlNode("options"));
        return feedback;
    }

    /**
     * Zubehör-Eintrag für einen Anschluss.
     *
     * @param tagName      {@code turnout} oder {@code signal}
     * @param accessoryType Bauform, z.B. {@code left} oder {@code de_hp0_1}
     */
    public static XmlNode createAccessory(String tagName, String accessoryType, String name,
            long uniqueId, int port, String interfaceName) {
        XmlNode accessory = new XmlNode(tagName);
        accessory.setAttribute("name", name);
        accessory.setAttribute("type", accessoryType);

        XmlNode id = new XmlNode("id");
        id.setTextContent(accessoryId(uniqueId, port));
        accessory.getChildren().add(id);

        XmlNode iface = new XmlNode("interface");
        iface.setAttribute("device", "bidib_accessory");
        iface.setAttribute("name", interfaceName);
        accessory.getChildren().add(iface);

        accessory.getChildren().add(new XmlNode("options"));
        return accessory;
    }

    /**
     * Booster-Eintrag. Grenzwerte (Spannung, Strom, Temperatur) legt iTrain
     * beim ersten Verbinden selbst an - hier bewusst weggelassen, statt
     * Zahlen zu erfinden, die zum konkreten Gerät nicht passen.
     */
    public static XmlNode createBooster(String name, long uniqueId, String interfaceName) {
        XmlNode booster = new XmlNode("booster");
        booster.setAttribute("name", name);
        booster.setAttribute("state", "off");
        booster.setAttribute("type", "bidib");

        XmlNode description = new XmlNode("description");
        description.setTextContent(name);
        booster.getChildren().add(description);

        XmlNode id = new XmlNode("id");
        id.setTextContent(nodeId(uniqueId));
        booster.getChildren().add(id);

        XmlNode iface = new XmlNode("interface");
        iface.setAttribute("name", interfaceName);
        booster.getChildren().add(iface);

        booster.getChildren().add(new XmlNode("options"));
        return booster;
    }

    /**
     * Ein Knoten, so wie er für den Schnittstellen-Eintrag gebraucht wird.
     * Bewusst eine eigene, kleine Hülle statt {@link Node}: Den Knoten der
     * Gegenseite selbst (Adresse 0) liefert jbidibc nur als
     * {@code RootNode}, dessen zugrunde liegendes {@code Node}-Objekt nicht
     * öffentlich zugänglich ist - über diese Hülle lassen sich beide Fälle
     * gleich behandeln.
     *
     * @param uniqueId    Kennung, wie jbidibc sie führt (7 Byte)
     * @param productName Produktname des Knotens, darf leer sein
     * @param userName    am Gerät vergebener Name, darf leer sein
     * @param ports       Anzahl der Anschlüsse, oder {@code null} wenn der
     *                    Knoten keine hat (Gleisausgang, Booster, Verteiler)
     */
    public record NodeInfo(long uniqueId, String productName, String userName, Integer ports) {
    }

    /** Baut die Hülle aus einem von jbidibc gemeldeten Knoten. */
    public static NodeInfo toNodeInfo(Node node, Integer ports) {
        Long uid = node.getUniqueId();
        return new NodeInfo(uid == null ? 0L : uid,
                node.getStoredString(org.bidib.jbidibc.messages.StringData.INDEX_PRODUCTNAME),
                node.getStoredString(org.bidib.jbidibc.messages.StringData.INDEX_USERNAME),
                ports);
    }

    /**
     * Vollständiger Schnittstellen-Eintrag mit der Liste ALLER erkannten
     * Knoten - so, wie iTrain ihn selbst schreibt. Nach dem Import ist die
     * netBiDiB-Verbindung damit fertig eingerichtet.
     *
     * @param nodes alle bekannten Knoten der Verbindung, einschließlich des
     *              Interface-Knotens selbst
     */
    /**
     * Schnittstellen-Eintrag fuer ein SERIELLES BiDiB-Interface (USB, z.B.
     * IF2 an COM15). ACHTUNG: Das Element {@code <serial>} ist gegen keine
     * echte, von iTrain geschriebene Datei abgeglichen (dafuer fehlte ein
     * Beispiel) - Typ "bidib" und die Attribute port/baudrate sind eine
     * begruendete Annahme nach dem Muster des netbidib-Eintrags und muessen
     * beim ersten Import in iTrain geprueft werden.
     */
    public static XmlNode createSerialInterface(String name, String portName, List<NodeInfo> nodes) {
        XmlNode iface = createInterface(name, "", "", nodes);
        iface.setAttribute("type", "bidib");
        int index = 0;
        for (int i = 0; i < iface.getChildren().size(); i++) {
            if ("socket".equals(iface.getChildren().get(i).getTagName())) {
                index = i;
                iface.getChildren().remove(i);
                break;
            }
        }
        XmlNode serial = new XmlNode("serial");
        serial.setAttribute("port", portName);
        serial.setAttribute("baudrate", "115200");
        iface.getChildren().add(index, serial);
        return iface;
    }

    public static XmlNode createInterface(String name, String host, String port, List<NodeInfo> nodes) {
        XmlNode iface = new XmlNode("interface");
        iface.setAttribute("name", name);
        iface.setAttribute("type", "netbidib");

        XmlNode control = new XmlNode("control");
        for (String type : List.of("vehicles", "accessories", "feedbacks", "boosters")) {
            XmlNode t = new XmlNode("type");
            t.setTextContent(type);
            control.getChildren().add(t);
        }
        iface.getChildren().add(control);

        XmlNode options = new XmlNode("options");
        for (String option : List.of("init_vehicles", "init_accessories")) {
            XmlNode o = new XmlNode("option");
            o.setTextContent(option);
            options.getChildren().add(o);
        }
        iface.getChildren().add(options);

        XmlNode socket = new XmlNode("socket");
        socket.setAttribute("host", host);
        socket.setAttribute("port", port);
        socket.setAttribute("timeout", "2000");
        iface.getChildren().add(socket);

        XmlNode accessory = new XmlNode("accessory");
        accessory.setAttribute("protocol", "dcc");
        accessory.setAttribute("switch-pause", "25");
        accessory.setAttribute("switching-time", "25");
        iface.getChildren().add(accessory);

        XmlNode feedbackDelay = new XmlNode("feedback-delay");
        feedbackDelay.setAttribute("off", "500");
        feedbackDelay.setAttribute("on", "50");
        feedbackDelay.setAttribute("unit", "ms");
        iface.getChildren().add(feedbackDelay);

        XmlNode nodesElement = new XmlNode("nodes");
        int count = 0;
        // Nach ROLLE gruppieren, nicht nach Knoten: Genau so schreibt iTrain
        // die Liste selbst - erst alle Gleisausgänge, dann Booster, dann
        // Rückmelder, dann Zubehör, zuletzt die Verteiler. Ein Knoten mit
        // mehreren Rollen (das Interface ist zugleich Gleisausgang und
        // Verteiler) taucht dadurch an zwei weit auseinanderliegenden Stellen
        // auf. Abgelesen an einer echten, von iTrain geschriebenen Datei.
        for (String type : ROLE_ORDER) {
            for (NodeInfo node : nodes) {
                long uid = node.uniqueId();
                if (uid == 0L || !nodeTypes(uid).contains(type)) {
                    continue;
                }
                XmlNode nodeElement = new XmlNode("node");
                nodeElement.setAttribute("type", type);
                // Die Anschlusszahl vermerkt iTrain nur dort, wo es
                // Anschlüsse gibt - bei Gleisausgang, Booster und Verteiler
                // stünde sonst eine sinnlose Null.
                Integer portCount = node.ports();
                if (portCount != null && portCount > 0
                        && ("feedback".equals(type) || "accessory".equals(type))) {
                    nodeElement.setAttribute("ports", String.valueOf(portCount));
                }

                XmlNode id = new XmlNode("id");
                id.setTextContent(nodeId(uid));
                nodeElement.getChildren().add(id);

                XmlNode description = new XmlNode("description");
                description.setTextContent(describeNode(node));
                nodeElement.getChildren().add(description);

                XmlNode comment = new XmlNode("comment");
                comment.setTextContent(vendorProductComment(uid));
                nodeElement.getChildren().add(comment);

                nodesElement.getChildren().add(nodeElement);
                count++;
            }
        }
        nodesElement.setAttribute("count", String.valueOf(count));
        iface.getChildren().add(nodesElement);

        return iface;
    }

    /**
     * Beschreibungstext eines Knotens im Stil von iTrain:
     * {@code "Produktname → Benutzername"}, ersatzweise nur das, was bekannt ist.
     */
    private static String describeNode(NodeInfo node) {
        String product = node.productName();
        String user = node.userName();
        boolean hasProduct = product != null && !product.isBlank();
        boolean hasUser = user != null && !user.isBlank();
        if (hasProduct && hasUser) {
            return product + " → " + user;
        } else if (hasProduct) {
            return product;
        } else if (hasUser) {
            return user;
        }
        return "";
    }
}
