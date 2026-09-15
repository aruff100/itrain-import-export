package com.example.itrain_import_export;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import org.bidib.jbidibc.core.BidibInterface;
import org.bidib.jbidibc.core.NodeListener;
import org.bidib.jbidibc.core.node.BidibNode;
import org.bidib.jbidibc.core.node.RootNode;
import org.bidib.jbidibc.messages.Node;
import org.bidib.jbidibc.messages.exception.ProtocolException;
import org.bidib.jbidibc.messages.logger.EmptyLogger;
import org.bidib.jbidibc.messages.utils.NodeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Hält die Liste der aktuell bekannten BiDiB-Knoten einer netBiDiB-Verbindung
 * (siehe {@link BidibConnectionDialog}) - befüllt über einen
 * {@link NodeListener}, den jbidibc automatisch aufruft, sobald sich die
 * Knotentabelle der Verbindung ändert (z.B. weitere am BiDiBus angeschlossene
 * Module). Das passiert NUR, wenn dieser Listener beim Verbindungsaufbau
 * ({@code BidibInterface.open(...)}) mit übergeben wurde - eine nachträgliche
 * Anmeldung wie bei {@code RawMessageListener} sieht jbidibc dafür nicht vor.
 * <p>
 * Dieser Listener meldet aber NUR spontane Änderungen (MSG_NODE_NEW/-LOST) -
 * die bereits beim Verbinden vorhandenen Unterknoten am BiDiB-Bus werden nicht
 * automatisch übertragen. Dafür muss der Host aktiv die Knotentabelle der
 * Gegenseite abfragen (MSG_NODETAB_GETALL, dann MSG_NODETAB_GETNEXT je
 * gemeldetem Knoten - Standard-BiDiB-Ablauf, siehe {@link #readInitialNodeTable}).
 * Ohne diesen einmaligen Abruf bliebe der Knoten-Baum bis auf den Root-Knoten
 * leer, auch wenn am Bus weitere Module (Rückmelder, Zubehördecoder, ...)
 * angeschlossen sind.
 * <p>
 * Ein {@code Node}-Objekt selbst ist veränderlich: Produktname, Benutzername
 * und Feature-Liste kommen erst nach und nach herein, nachdem der Knoten
 * bereits über {@link #getNodes()} sichtbar wurde (siehe {@link BidibNodeTreeWindow}).
 */
public final class BidibNodeModel {

    private static final Logger LOGGER = LoggerFactory.getLogger(BidibNodeModel.class);

    private final ObservableList<Node> nodes = FXCollections.observableArrayList();

    // Ergebnis von readInitialNodeTable() - da diese Methode schon beim
    // Verbinden läuft (lange bevor der Nutzer das Knoten-Baum-Fenster
    // überhaupt öffnet), muss das Ergebnis hier zwischengespeichert werden,
    // damit BidibNodeTreeWindow es später anzeigen kann. volatile, weil
    // readInitialNodeTable() in einem Hintergrundthread schreibt und das
    // Fenster später auf dem JavaFX-Thread liest.
    private volatile Integer initialNodeTableCount;
    private volatile String initialNodeTableError;

    /** Aktuell bekannte Knoten - nur im JavaFX-Thread lesen/verändern. */
    public ObservableList<Node> getNodes() {
        return nodes;
    }

    /**
     * Von der Gegenseite gemeldete Gesamtzahl an Unterknoten (laut
     * MSG_NODETAB_GETALL), oder {@code null}, wenn {@link #readInitialNodeTable}
     * noch nicht gelaufen ist oder fehlgeschlagen ist (siehe
     * {@link #getInitialNodeTableError()}).
     */
    public Integer getInitialNodeTableCount() {
        return initialNodeTableCount;
    }

    /** Fehlermeldung von {@link #readInitialNodeTable}, oder {@code null} bei Erfolg/noch nicht gelaufen. */
    public String getInitialNodeTableError() {
        return initialNodeTableError;
    }

    /** Neuer Listener für {@code BidibInterface.open(...)} - pro Verbindung nur einmal verwenden. */
    public NodeListener createListener() {
        return new NodeListener() {
            @Override
            public void nodeNew(Node node) {
                Platform.runLater(() -> {
                    if (!nodes.contains(node)) {
                        nodes.add(node);
                    }
                });
            }

            @Override
            public void nodeLost(Node node) {
                Platform.runLater(() -> nodes.remove(node));
            }
        };
    }

    /**
     * Fragt einmalig die komplette Knotentabelle der Gegenseite ab und trägt
     * die gefundenen Unterknoten ein - der Standard-BiDiB-Ablauf, um bereits
     * vorhandene (nicht erst nachträglich angemeldete) Knoten zu bekommen.
     * Blockierend (mehrere Netzwerk-Roundtrips), deshalb ausschließlich im
     * selben Hintergrundthread aufrufen, in dem auch schon
     * {@code BidibInterface.open(...)} läuft - siehe
     * {@code BidibConnectionDialog.connectInBackground}.
     */
    public void readInitialNodeTable(BidibInterface bidib) {
        try {
            RootNode rootNode = bidib.getRootNode();
            if (rootNode == null) {
                initialNodeTableError = "getRootNode() == null";
                return;
            }

            int count = rootNode.getNodeCount();
            initialNodeTableCount = count;
            LOGGER.info("Knotentabelle der Gegenseite: {} Unterknoten gemeldet.", count);

            List<Node> hubs = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Node child = rootNode.getNextNode(new EmptyLogger());
                // Die Knotentabelle enthält laut BiDiB-Norm immer auch den
                // meldenden Knoten SELBST (Adresse 0, also die Zentrale bzw.
                // das Interface). Den überspringen: er wird im Knoten-Baum
                // bereits gesondert über getRootNode() angezeigt (siehe
                // BidibNodeTreeWindow) und stand sonst doppelt in der Liste.
                if (child != null && NodeUtils.convertAddress(child.getAddr()) != 0) {
                    Platform.runLater(() -> {
                        if (!nodes.contains(child)) {
                            nodes.add(child);
                        }
                    });
                    if (NodeUtils.hasSubNodesFunctions(child.getUniqueId())) {
                        hubs.add(child);
                    }
                }
            }

            rootNode.setReadNodesPassed(true);

            // Verteiler (Hub, z.B. ReadyHub) haben eine EIGENE Knotentabelle
            // mit den dahinter angeschlossenen Geraeten (Adresse 1.1, 1.2,
            // ...). Die Tabelle des Interface nennt nur den Verteiler
            // selbst - ohne diesen zweiten Abruf fehlten alle Knoten hinter
            // dem Hub (so gesehen am 14.09.: Wizard zeigt B6_2 und
            // MD_Servo_2 unter dem ReadyHub, wir nicht). Verschachtelte
            // Verteiler werden gleich mit abgearbeitet.
            while (!hubs.isEmpty()) {
                Node hub = hubs.remove(0);
                readSubNodeTable(bidib, hub, hubs);
            }
        } catch (ProtocolException | RuntimeException ex) {
            initialNodeTableError = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            LOGGER.warn("Auslesen der anfänglichen Knotentabelle fehlgeschlagen.", ex);
        }
    }

    /**
     * Knotentabelle eines Verteilers abrufen. Sie enthaelt den Verteiler
     * selbst noch einmal (gleiche Adresse) - den ueberspringen. Ein Fehler
     * hier bricht nicht den ganzen Abruf ab: Die direkt am Interface
     * haengenden Knoten sind dann trotzdem da.
     */
    private void readSubNodeTable(BidibInterface bidib, Node hub, List<Node> moreHubs) {
        String hubAddress = java.util.Arrays.toString(hub.getAddr());
        try {
            BidibNode hubNode = bidib.getNode(hub);
            if (hubNode == null) {
                return;
            }
            int count = hubNode.getNodeCount();
            LOGGER.info("Knotentabelle des Verteilers {}: {} Eintraege.", hubAddress, count);
            for (int i = 0; i < count; i++) {
                Node child = hubNode.getNextNode(new EmptyLogger());
                if (child == null || java.util.Arrays.equals(child.getAddr(), hub.getAddr())) {
                    continue;
                }
                Platform.runLater(() -> {
                    if (!nodes.contains(child)) {
                        nodes.add(child);
                    }
                });
                if (NodeUtils.hasSubNodesFunctions(child.getUniqueId())) {
                    moreHubs.add(child);
                }
            }
        } catch (ProtocolException | RuntimeException ex) {
            LOGGER.warn("Knotentabelle des Verteilers {} nicht lesbar: {}", hubAddress, ex.getMessage());
        }
    }
}
