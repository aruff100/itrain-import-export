package com.example.itrain_import_export;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Hält alle GLEICHZEITIG offenen netBiDiB-Verbindungen (siehe
 * {@link BidibConnection}).
 * <p>
 * Bewusst ein anwendungsweiter Einzelbestand und nicht an ein Fenster
 * gebunden: Der Verbindungsdialog darf geschlossen werden, ohne die
 * Verbindungen zu beenden, und sowohl die Statusleiste des Hauptfensters als
 * auch die Untermenüs unter "BiDiB" müssen unabhängig davon wissen, was gerade
 * offen ist.
 * <p>
 * Die Liste ist beobachtbar - Statusleiste und Menü hängen sich als
 * Listener ein und bauen sich neu auf, sobald eine Verbindung dazukommt oder
 * wegfällt. Ausschließlich im JavaFX-Thread verändern.
 */
public final class BidibConnectionManager {

    private static final BidibConnectionManager INSTANCE = new BidibConnectionManager();

    private final ObservableList<BidibConnection> connections = FXCollections.observableArrayList();

    private BidibConnectionManager() {
    }

    public static BidibConnectionManager getInstance() {
        return INSTANCE;
    }

    /** Alle offenen Verbindungen - nur im JavaFX-Thread lesen/verändern. */
    public ObservableList<BidibConnection> getConnections() {
        return connections;
    }

    public void add(BidibConnection connection) {
        if (connection != null && !connections.contains(connection)) {
            connections.add(connection);
        }
    }

    public void remove(BidibConnection connection) {
        connections.remove(connection);
    }

    /**
     * Bereits offene Verbindung zu dieser Adresse, oder {@code null}. Verhindert,
     * dass dasselbe Interface versehentlich zweimal verbunden wird - das Gerät
     * würde die zweite Sitzung ohnehin ablehnen oder die erste verdrängen.
     */
    /**
     * Trennt alle offenen Verbindungen und leert die Liste. Das Trennen läuft
     * in eigenen Fäden mit knapper Wartezeit: {@code bidib.close()} kann
     * hängen bleiben, wenn die Gegenseite nicht mehr antwortet - das darf das
     * Schließen des Fensters nicht aufhalten.
     */
    public void closeAll() {
        java.util.List<BidibConnection> open = new java.util.ArrayList<>(connections);
        connections.clear();
        java.util.List<Thread> workers = new java.util.ArrayList<>();
        for (BidibConnection connection : open) {
            Thread worker = new Thread(connection::close, "bidib-close");
            worker.setDaemon(true);
            worker.start();
            workers.add(worker);
        }
        for (Thread worker : workers) {
            try {
                worker.join(1500);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** Ob gerade mindestens eine Verbindung offen ist. */
    public boolean hasConnections() {
        return !connections.isEmpty();
    }

    public BidibConnection findByHostPort(String hostPort) {
        if (hostPort == null) {
            return null;
        }
        for (BidibConnection c : connections) {
            if (hostPort.equals(c.getHostPort())) {
                return c;
            }
        }
        return null;
    }
}
