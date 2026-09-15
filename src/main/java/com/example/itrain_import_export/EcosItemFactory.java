package com.example.itrain_import_export;

import java.util.List;

/**
 * Baut iTrain-Einträge für eine ESU ECoS - das Gegenstück zu
 * {@link BidibItemFactory}.
 * <p>
 * Was belegt ist (an echten iTrain-Dateien abgelesen, Kellerbahn_20241221):
 * Rückmelder und Weichen an einer Schnittstelle mit Adresse -
 * {@code <feedback name type="occupancy" selected="false"><interface
 * address="28" name="YD7001"/><options/></feedback>} und
 * {@code <turnout name type="right" state="straight"><interface address="27"
 * name="YD7001"/><options/></turnout>}. Genau so werden die ECoS-Objekte
 * geschrieben.
 * <p>
 * NICHT belegt (kein Beispiel vorhanden, siehe STATUS.md): der
 * Schnittstellen-Eintrag selbst. Typname {@code ecos} und
 * {@code <socket host port="15471"/>} folgen dem Muster von netbidib
 * ({@code <socket host port timeout/>}) und muessen beim ersten Import in
 * iTrain geprueft werden. Ebenfalls Annahme: die Rueckmelder-Adresse in
 * iTrain ist {@code (Objekt - 100) * 16 + Port} (S88-Modul 100 -> 1..16,
 * 101 -> 17..32, ECoSDetector 200 -> 1601..1616).
 */
public final class EcosItemFactory {

    private EcosItemFactory() {
    }

    /** iTrain-Adresse eines Rueckmelde-Anschlusses (Port ab 1). */
    public static int feedbackAddress(int objectId, int port) {
        return (objectId - 100) * 16 + port;
    }

    public static XmlNode createInterface(String name, String host) {
        XmlNode iface = new XmlNode("interface");
        iface.setAttribute("name", name);
        iface.setAttribute("type", "ecos");

        XmlNode control = new XmlNode("control");
        for (String type : List.of("vehicles", "accessories", "feedbacks")) {
            XmlNode t = new XmlNode("type");
            t.setTextContent(type);
            control.getChildren().add(t);
        }
        iface.getChildren().add(control);

        XmlNode options = new XmlNode("options");
        for (String option : List.of("stop_on_disconnect", "init_vehicles", "init_accessories")) {
            XmlNode o = new XmlNode("option");
            o.setTextContent(option);
            options.getChildren().add(o);
        }
        iface.getChildren().add(options);

        XmlNode socket = new XmlNode("socket");
        socket.setAttribute("host", host);
        socket.setAttribute("port", String.valueOf(EcosClient.PORT));
        socket.setAttribute("timeout", "2000");
        iface.getChildren().add(socket);

        XmlNode accessory = new XmlNode("accessory");
        accessory.setAttribute("protocol", "dcc");
        accessory.setAttribute("switch-pause", "25");
        accessory.setAttribute("switching-time", "250");
        iface.getChildren().add(accessory);
        return iface;
    }

    public static XmlNode createFeedback(String name, int address, String interfaceName) {
        XmlNode feedback = new XmlNode("feedback");
        feedback.setAttribute("name", name);
        feedback.setAttribute("type", "occupancy");
        feedback.setAttribute("selected", "false");

        XmlNode iface = new XmlNode("interface");
        iface.setAttribute("address", String.valueOf(address));
        iface.setAttribute("name", interfaceName);
        feedback.getChildren().add(iface);

        feedback.getChildren().add(new XmlNode("options"));
        return feedback;
    }

    public static XmlNode createTurnout(String type, String name, int address, String interfaceName) {
        XmlNode turnout = new XmlNode("turnout");
        turnout.setAttribute("name", name);
        turnout.setAttribute("type", type);
        turnout.setAttribute("state", SystemsObject.turnoutStates(type).get(0));

        XmlNode iface = new XmlNode("interface");
        iface.setAttribute("address", String.valueOf(address));
        iface.setAttribute("name", interfaceName);
        turnout.getChildren().add(iface);

        turnout.getChildren().add(new XmlNode("options"));
        return turnout;
    }

    /**
     * iTrain-Bauform aus dem ECoS-Symbol. Belegt: 0 = Weiche links und
     * 1 = Weiche rechts (Andres ECoS am 15.09.: "Linksweiche" symbol[0],
     * "Rechtsweiche" symbol[1]), 19 / 20 = Bogenweiche links / rechts
     * (ebenda), 2 = Dreiwegweiche und 4 = Doppelkreuzungsweiche (JMRI).
     * 3 = Y-Weiche und 5 = einfache Kreuzungsweiche sind Annahmen - jedes
     * Zubehoer bekommt ohnehin die "*"-Markierung (Voreinstellung, im
     * Bearbeitungsfenster pruefen).
     */
    public static String turnoutTypeForSymbol(int symbol) {
        switch (symbol) {
            case 1:
                return "right";
            case 2:
                return "three-way";
            case 3:
                return "y-type";
            case 4:
                return "cross-double-slip";
            case 5:
                return "cross-single-slip";
            case 19:
                return "curved-left";
            case 20:
                return "curved-right";
            default:
                return "left";
        }
    }
}
