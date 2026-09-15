package com.example.itrain_import_export;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * EINE offene Verbindung zu einer ESU ECoS (siehe {@link EcosClient}) samt
 * Anzeigename, Version und Verbindungszustand - das Gegenstück zu
 * {@link BidibConnection} für ESU. Alle offenen ECoS-Verbindungen hält die
 * statische Liste {@link #getConnections()}; die Statuszeile des
 * Systeme-Fensters bindet sich daran.
 * <p>
 * Der Datenverkehr (Befehle und Antworten, reiner Text) geht in die
 * Protokolldatei des Programms (Logger "ecos"), damit er sich bei Fragen
 * nachlesen lässt.
 */
public final class EcosConnection {

    private static final Logger LOGGER = LoggerFactory.getLogger("ecos");

    private static final ObservableList<EcosConnection> CONNECTIONS = FXCollections.observableArrayList();

    private final String host;
    private final EcosClient client;
    private final StringProperty name = new SimpleStringProperty();
    private final BooleanProperty connected = new SimpleBooleanProperty(true);
    private String version = "";
    private String hardware = "";

    private EcosConnection(String host, EcosClient client) {
        this.host = host;
        this.client = client;
        this.name.set(host);
        client.addTrafficListener((direction, line) -> LOGGER.info("{} {} {}", host, direction, line));
    }

    /** Alle offenen ECoS-Verbindungen - nur im JavaFX-Thread lesen/aendern. */
    public static ObservableList<EcosConnection> getConnections() {
        return CONNECTIONS;
    }

    public static EcosConnection findByHost(String host) {
        for (EcosConnection connection : CONNECTIONS) {
            if (connection.host.equalsIgnoreCase(host)) {
                return connection;
            }
        }
        return null;
    }

    /**
     * Verbindet und fragt Name und Version ab ({@code get(1, info)}).
     * Blockierend - im Hintergrundfaden aufrufen; das Eintragen in die
     * Liste geschieht dann auf dem JavaFX-Thread.
     */
    public static EcosConnection open(String host) throws IOException {
        EcosClient client = new EcosClient(host);
        EcosConnection connection = new EcosConnection(host, client);
        try {
            EcosClient.Reply info = client.send("get(1, info)");
            if (!info.isOk()) {
                throw new IOException("get(1, info): " + info.message());
            }
            String deviceName = null;
            for (EcosClient.Line line : info.lines()) {
                // Erste Zeile "1 ECoS2" (Geraetetyp ohne Feld), manche
                // Firmwares liefern zusaetzlich Name["..."].
                String n = line.get("Name");
                if (n == null || n.isBlank()) {
                    n = line.get("_text");
                }
                if (n != null && !n.isBlank() && deviceName == null) {
                    deviceName = n;
                }
                String v = line.get("ApplicationVersion");
                if (v != null) {
                    connection.version = v;
                }
                String h = line.get("HardwareVersion");
                if (h != null) {
                    connection.hardware = h;
                }
            }
            String finalName = deviceName != null ? deviceName : "ECoS";
            Platform.runLater(() -> {
                connection.name.set(finalName + " (" + host + ")");
                if (!CONNECTIONS.contains(connection)) {
                    CONNECTIONS.add(connection);
                }
            });
            return connection;
        } catch (IOException ex) {
            client.close();
            throw ex;
        }
    }

    public String getHost() {
        return host;
    }

    public EcosClient getClient() {
        return client;
    }

    public StringProperty nameProperty() {
        return name;
    }

    public String getName() {
        return name.get();
    }

    public String getVersion() {
        return version;
    }

    public String getHardware() {
        return hardware;
    }

    public BooleanProperty connectedProperty() {
        return connected;
    }

    public boolean isConnected() {
        return connected.get();
    }

    /** Trennen und aus der Liste nehmen (JavaFX-Thread). */
    public void close() {
        client.close();
        connected.set(false);
        CONNECTIONS.remove(this);
    }

    public static void closeAll() {
        for (EcosConnection connection : new java.util.ArrayList<>(CONNECTIONS)) {
            connection.close();
        }
    }

    @Override
    public String toString() {
        return getName();
    }
}
