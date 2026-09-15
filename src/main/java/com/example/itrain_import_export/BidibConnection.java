package com.example.itrain_import_export;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import org.bidib.jbidibc.core.BidibInterface;
import org.bidib.jbidibc.core.node.BidibNode;
import org.bidib.jbidibc.core.node.RootNode;
import org.bidib.jbidibc.messages.StringData;
import org.bidib.jbidibc.messages.base.RawMessageListener;
import org.bidib.jbidibc.messages.exception.ProtocolException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * EINE offene netBiDiB-Verbindung zu einem Interface, mit allem, was zu ihr
 * gehört: das jbidibc-Verbindungsobjekt, das Knotenmodell, der Anzeigename und
 * der Verbindungszustand.
 * <p>
 * Es können mehrere Verbindungen GLEICHZEITIG offen sein - findet die Suche im
 * Netz drei Interfaces, lassen sich alle drei nacheinander verbinden. Deshalb
 * hängen Verbindungsobjekt und Knotenmodell nicht mehr am Verbindungsdialog
 * (der ja auch geschlossen werden kann, ohne die Verbindung zu beenden),
 * sondern an dieser Klasse; verwaltet werden alle offenen Verbindungen von
 * {@link BidibConnectionManager}.
 * <p>
 * Name und Verbindungszustand sind JavaFX-Properties, weil die Statusleiste
 * des Hauptfensters und die Untermenüs unter "BiDiB" sich daran binden -
 * dadurch aktualisieren sie sich von selbst, sobald der Name der Gegenseite
 * abgefragt wurde oder die Verbindung abbricht.
 */
public final class BidibConnection {

    private static final Logger LOGGER = LoggerFactory.getLogger(BidibConnection.class);

    private final String hostPort;
    private final BidibInterface bidib;
    private final BidibNodeModel nodeModel;

    /**
     * Anzeigename. Anfangs die Adresse, sobald bekannt der Name, den das
     * Interface selbst meldet (siehe {@link #resolveNameInBackground()}) -
     * beim Testgerät z.B. "IFNet Spur1".
     */
    private final StringProperty name = new SimpleStringProperty();

    /**
     * Produktname der Gegenseite (z.B. "MC2", "BiDiB-IFnet") - unabhaengig
     * vom Anzeigenamen (der bevorzugt den vom Nutzer vergebenen Namen zeigt,
     * siehe {@link #name}). Wird zusammen mit ihm abgefragt
     * ({@link #resolveNameInBackground()}); dient u.a. {@link #isMc2()}, um
     * den Extra-Knopf "mc2 Lokomotiven auslesen" nur bei einer echten
     * Tams-mc2 anzuzeigen.
     */
    private final StringProperty productName = new SimpleStringProperty();

    /** {@code true} solange die Verbindung steht - steuert die Farbe des Kreises in der Statusleiste. */
    private final BooleanProperty connected = new SimpleBooleanProperty(true);

    /**
     * {@code true}, sobald bei einer mc2 ({@link #isMc2()}) per FTP auf
     * "config/loco.ini" zugegriffen werden konnte (siehe
     * {@link Mc2LocoReader#isAvailable}) - steuert den Extra-Knopf "mc2
     * Lokomotiven auslesen" im Systeme-Fenster. Wird einmalig nach
     * {@link #resolveNameInBackground()} geprueft, wenn sich die Gegenseite
     * als mc2 herausstellt.
     */
    private final BooleanProperty mc2LocoAvailable = new SimpleBooleanProperty(false);

    /** So viele RX/TX-Zeilen werden je Verbindung behalten; aeltere fallen weg. */
    public static final int RAW_LOG_MAX = 20000;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    /**
     * Mitschrift des gesamten Datenverkehrs (RX/TX) ab dem ersten Byte des
     * Verbindungsaufbaus - der Listener wird schon im Konstruktor, also VOR
     * {@code bidib.open(...)}, angemeldet. Das RX/TX-Fenster
     * ({@link BidibRawLogWindow}) zeigt diese Mitschrift und haengt sich fuer
     * neue Zeilen an {@link #addRawLogListener}; frueher meldete das Fenster
     * seinen eigenen Listener erst beim Oeffnen an und sah deshalb vom
     * Verbindungsaufbau nichts.
     */
    /** Ein Paket der Mitschrift: Zeit, Richtung, rohe Bytes - lesbar oder als Hex formatierbar. */
    public record RawEntry(String time, String direction, byte[] data) {
        public String hexLine() {
            return time + "  " + direction + "  " + BidibMessageDecoder.toHex(data);
        }

        public String textLine() {
            return time + "  " + direction + "  " + BidibMessageDecoder.decode(data);
        }
    }

    private final Deque<RawEntry> rawLog = new ArrayDeque<>();
    private final List<Consumer<RawEntry>> rawLogListeners = new CopyOnWriteArrayList<>();

    public BidibConnection(String hostPort, BidibInterface bidib, BidibNodeModel nodeModel) {
        this.hostPort = hostPort;
        this.bidib = bidib;
        this.nodeModel = nodeModel;
        this.name.set(hostPort);
        try {
            bidib.addRawMessageListener(new RawMessageListener() {
                @Override
                public void notifyReceived(byte[] data) {
                    recordRaw("RX", data);
                }

                @Override
                public void notifySend(byte[] data) {
                    recordRaw("TX", data);
                }
            });
        } catch (RuntimeException ex) {
            LOGGER.warn("RX/TX-Mitschrift fuer {} nicht moeglich: {}", hostPort, ex.getMessage());
        }
    }

    private void recordRaw(String direction, byte[] data) {
        if (data == null) {
            return;
        }
        RawEntry entry = new RawEntry(LocalTime.now().format(TIME_FORMAT), direction, data.clone());
        synchronized (rawLog) {
            rawLog.addLast(entry);
            while (rawLog.size() > RAW_LOG_MAX) {
                rawLog.removeFirst();
            }
        }
        for (Consumer<RawEntry> listener : rawLogListeners) {
            listener.accept(entry);
        }
    }

    /** Kopie der bisherigen Mitschrift (aelteste zuerst). */
    public List<RawEntry> snapshotRawLog() {
        synchronized (rawLog) {
            return new ArrayList<>(rawLog);
        }
    }

    public void clearRawLog() {
        synchronized (rawLog) {
            rawLog.clear();
        }
    }

    /** Wird fuer jedes neue Paket aufgerufen - aus dem Faden der Bibliothek, nicht dem JavaFX-Thread. */
    public void addRawLogListener(Consumer<RawEntry> listener) {
        rawLogListeners.add(listener);
    }

    public void removeRawLogListener(Consumer<RawEntry> listener) {
        rawLogListeners.remove(listener);
    }

    public String getHostPort() {
        return hostPort;
    }

    public BidibInterface getBidib() {
        return bidib;
    }

    public BidibNodeModel getNodeModel() {
        return nodeModel;
    }

    public StringProperty nameProperty() {
        return name;
    }

    public String getName() {
        return name.get();
    }

    public StringProperty productNameProperty() {
        return productName;
    }

    public String getProductName() {
        return productName.get();
    }

    /** true, wenn die Gegenseite sich als "MC2" meldet (Tams mc2) - siehe {@link #productNameProperty()}. */
    public boolean isMc2() {
        String product = productName.get();
        return product != null && product.trim().equalsIgnoreCase("MC2");
    }

    public BooleanProperty mc2LocoAvailableProperty() {
        return mc2LocoAvailable;
    }

    public boolean isMc2LocoAvailable() {
        return mc2LocoAvailable.get();
    }

    /**
     * Nur der Host-Anteil aus {@link #getHostPort()} ("192.168.0.90:62875"
     * -&gt; "192.168.0.90") - fuer den FTP-Zugriff auf die mc2 (eigener Port
     * 21, nicht der netBiDiB-Port). Bei einer seriellen (USB-)Verbindung
     * steht hier kein Host - dann liefert diese Methode null.
     */
    public String getHost() {
        if (hostPort == null || hostPort.indexOf(':') < 0) {
            return null;
        }
        return hostPort.substring(0, hostPort.lastIndexOf(':'));
    }

    public BooleanProperty connectedProperty() {
        return connected;
    }

    public boolean isConnected() {
        return connected.get();
    }

    /** Nur aus dem JavaFX-Thread aufrufen - die Statusleiste hängt daran. */
    public void setConnected(boolean value) {
        connected.set(value);
    }

    /**
     * Fragt den Namen des Interface im Hintergrund ab und trägt ihn als
     * Anzeigenamen ein. Bevorzugt der vom Nutzer am Gerät vergebene Name
     * ("IFNet Spur1"), ersatzweise der Produktname ("BiDiB-IFnet"); scheitert
     * beides, bleibt die Adresse stehen.
     * <p>
     * Erst NACH abgeschlossenem Verbindungsaufbau aufrufen (zweiter
     * {@code opened()}-Aufruf, siehe {@code BidibConnectionDialog}) - vorher
     * weist die Gegenseite Abfragen zurück.
     */
    public void resolveNameInBackground() {
        Thread worker = new Thread(() -> {
            String product = queryProductName();
            if (product != null && !product.isBlank()) {
                Platform.runLater(() -> productName.set(product));
            }
            String resolved = queryName(product);
            if (resolved != null && !resolved.isBlank()) {
                // Auch die Protokolldatei traegt ab jetzt diesen Namen.
                BidibLog.setInterfaceName(resolved);
                Platform.runLater(() -> name.set(resolved));
            }
            if (product != null && product.trim().equalsIgnoreCase("MC2") && Mc2LocoReader.isAvailable(getHost())) {
                Platform.runLater(() -> mc2LocoAvailable.set(true));
            }
        }, "bidib-connection-name");
        worker.setDaemon(true);
        worker.start();
    }

    private String queryName(String product) {
        try {
            RootNode rootNode = bidib.getRootNode();
            if (rootNode == null) {
                return null;
            }
            String user = readString(rootNode, StringData.INDEX_USERNAME);
            if (user != null && !user.isBlank()) {
                return user;
            }
            return product;
        } catch (RuntimeException ex) {
            LOGGER.debug("Name der Verbindung {} nicht abrufbar: {}", hostPort, ex.getMessage());
            return null;
        }
    }

    private String queryProductName() {
        try {
            RootNode rootNode = bidib.getRootNode();
            return rootNode == null ? null : readString(rootNode, StringData.INDEX_PRODUCTNAME);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String readString(BidibNode node, int index) {
        try {
            StringData data = node.getString(StringData.NAMESPACE_NODE, index);
            return data != null ? data.getValue() : null;
        } catch (ProtocolException | RuntimeException ex) {
            return null;
        }
    }

    /**
     * Trennt die Verbindung. Blockierend (die Bibliothek schickt noch eine
     * Abmeldung und wartet auf das Leeren der Sendewarteschlange), deshalb
     * nicht auf dem JavaFX-Thread aufrufen.
     */
    public void close() {
        try {
            bidib.close();
        } catch (RuntimeException ex) {
            LOGGER.warn("Trennen der Verbindung {} fehlgeschlagen.", hostPort, ex);
        }
    }

    @Override
    public String toString() {
        return getName();
    }
}
