package com.example.itrain_import_export;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

import org.bidib.jbidibc.core.BidibInterface;
import org.bidib.jbidibc.core.node.RootNode;
import org.bidib.jbidibc.jserialcomm.JSerialCommSerialBidib;
import org.bidib.jbidibc.messages.ConnectionListener;
import org.bidib.jbidibc.messages.StringData;
import org.bidib.jbidibc.messages.enums.PairingResult;
import org.bidib.jbidibc.messages.helpers.Context;
import org.bidib.jbidibc.messages.helpers.DefaultContext;
import org.bidib.jbidibc.messages.utils.ByteUtils;

import java.util.Collections;

/**
 * Serielles BiDiB (USB / virtueller COM-Port, siehe
 * bidib.org/transport/bidib_seriell_e.html): Pruefen, ob an einem Port ein
 * BiDiB-Geraet antwortet, und Verbindung aufbauen. Beides ueber
 * jbidibc-jserialcomm; die Baudrate (19200/115200/1M) probiert die
 * Bibliothek beim Oeffnen selbst durch, ein Pairing gibt es nicht.
 */
public final class BidibSerialSupport {

    private BidibSerialSupport() {
    }

    /** Ergebnis der Pruefung eines Ports. */
    public record ProbeResult(boolean bidib, String uniqueId, String productName, String error) {
    }

    /**
     * Pruefroutine: oeffnet den Port kurz mit dem BiDiB-Protokoll, fragt
     * Kennung und Produktnamen des Interface ab und schliesst wieder.
     * Blockierend (Baudraten-Versuche, bis zu einigen Sekunden je Port) -
     * nur im Hintergrund aufrufen. Ein Port, an dem kein BiDiB-Geraet
     * haengt, antwortet nicht: dann kommt {@code bidib=false} mit dem
     * Fehlertext der Bibliothek.
     */
    public static ProbeResult probe(String portName) {
        Context context = new DefaultContext();
        BidibInterface bidib = JSerialCommSerialBidib.createInstance(context);
        try {
            bidib.setResponseTimeout(1600);
            bidib.open(portName, silentListener(), Collections.emptySet(), null, null, context);
            RootNode root = bidib.getRootNode();
            String uid = null;
            String product = null;
            if (root != null) {
                try {
                    Long id = root.getUniqueId();
                    uid = id != null ? ByteUtils.formatHexUniqueId(id) : null;
                } catch (Exception ignored) {
                    // Kennung nicht abrufbar - trotzdem ein BiDiB-Geraet.
                }
                try {
                    StringData data = root.getString(StringData.NAMESPACE_NODE, StringData.INDEX_PRODUCTNAME);
                    product = data != null ? data.getValue() : null;
                } catch (Exception ignored) {
                    // Produktname optional.
                }
            }
            return new ProbeResult(true, uid, product, null);
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            return new ProbeResult(false, null, null, message);
        } finally {
            try {
                bidib.close();
            } catch (RuntimeException ignored) {
                // Port war ohnehin nicht offen.
            }
        }
    }

    /**
     * Verbindung ueber einen COM-Port im Hintergrund aufbauen und im
     * {@link BidibConnectionManager} eintragen. {@code open(...)} kehrt erst
     * zurueck, wenn das Interface geantwortet hat (MSG_SYS_GET_MAGIC) - danach
     * kann die Knotentabelle sofort gelesen werden.
     *
     * @param button Knopf, der waehrend des Aufbaus gesperrt wird (darf null sein)
     */
    public static void connect(String portName, I18n i18n, Label statusLabel, Button button,
            BidibConnectionManager manager) {
        connectWith(portName, false, i18n, statusLabel, button, manager);
    }

    /**
     * Wie {@link #connect}, aber "BiDiB seriell ueber TCP" (siehe
     * {@link TcpSerialBidib}) - z.B. die BiDiB-Weiterleitung von iTrain.
     *
     * @param hostPort "Adresse:Port", z.B. "192.168.0.171:62800"
     */
    public static void connectTcp(String hostPort, I18n i18n, Label statusLabel, Button button,
            BidibConnectionManager manager) {
        connectWith(hostPort, true, i18n, statusLabel, button, manager);
    }

    private static void connectWith(String portName, boolean tcp, I18n i18n, Label statusLabel, Button button,
            BidibConnectionManager manager) {
        if (button != null) {
            button.setDisable(true);
        }
        statusLabel.setText(i18n.t("bidib.statusConnecting"));
        String deviceLabel = tcp
                ? (BidibConnectionDialog.knownName(portName) != null
                        ? BidibConnectionDialog.knownName(portName) : i18n.t("bidib.tcpSerialDevice"))
                : "BiDiB-USB";

        Thread worker = new Thread(() -> {
            Context context = new DefaultContext();
            BidibInterface bidib = tcp
                    ? TcpSerialBidib.createInstance(context)
                    : JSerialCommSerialBidib.createInstance(context);
            BidibNodeModel nodeModel = new BidibNodeModel();
            BidibConnection connection = new BidibConnection(portName, bidib, nodeModel);

            ConnectionListener listener = new ConnectionListener() {
                @Override
                public void opened(String port) {
                    // Der eigentliche Abschluss ist die Rueckkehr aus open().
                }

                @Override
                public void closed(String port) {
                    Platform.runLater(() -> {
                        connection.setConnected(false);
                        statusLabel.setText(i18n.t("bidib.statusClosed"));
                    });
                }

                @Override
                public void status(String messageKey, Context ctx) {
                    // Diagnosemeldungen der Bibliothek (Baudraten-Versuche).
                }

                @Override
                public void actionRequired(String messageKey, Context ctx) {
                    // Kein Pairing bei serieller Verbindung.
                }

                @Override
                public void pairingFinished(PairingResult result, long remoteUid) {
                    // Kein Pairing bei serieller Verbindung.
                }

                @Override
                public void handleError(RuntimeException ex) {
                    Platform.runLater(() -> statusLabel.setText(i18n.t("bidib.statusError", ex.getMessage())));
                }
            };

            try {
                bidib.setResponseTimeout(1600);
                bidib.open(portName, listener, Collections.singleton(nodeModel.createListener()), null, null, context);
                nodeModel.readInitialNodeTable(bidib);
                connection.resolveNameInBackground();
                Platform.runLater(() -> {
                    manager.add(connection);
                    // Gruene Erfolgsmeldung (im Verbindungsfenster blinkt
                    // danach "Weiter zur Bearbeitung").
                    BidibConnectionDialog.markSuccess(statusLabel,
                            i18n.t("bidib.statusConnectedOk", deviceLabel, portName));
                    if (button != null) {
                        button.setDisable(false);
                    }
                });
            } catch (Exception ex) {
                try {
                    bidib.close();
                } catch (RuntimeException ignored) {
                    // Port war ohnehin nicht offen.
                }
                String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                // COM-Port: ein Fehler heisst fast immer "belegt". TCP: nur bei
                // abgelehnter Verbindung; sonst antwortet dort kein BiDiB.
                boolean busy = !tcp || hasRefusedCause(ex);
                Platform.runLater(() -> {
                    statusLabel.setText(i18n.t("bidib.statusSerialFailed", portName, message));
                    if (button != null) {
                        button.setDisable(false);
                    }
                    // Wie bei netBiDiB: deutlicher Fehlerdialog. Ein belegter
                    // COM-Port heisst praktisch immer: ein anderes Programm
                    // (iTrain, BiDiB-Wizard) hat ihn offen.
                    BidibConnectionDialog.showConnectFailed(
                            statusLabel.getScene() != null ? statusLabel.getScene().getWindow() : null,
                            i18n, deviceLabel, portName, busy, message);
                });
            }
        }, tcp ? "bidib-tcp-serial-connect" : "bidib-serial-connect");
        worker.setDaemon(true);
        worker.start();
    }

    private static boolean hasRefusedCause(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            String m = t.getMessage() == null ? "" : t.getMessage().toLowerCase(java.util.Locale.ROOT);
            if (t instanceof java.net.ConnectException || m.contains("refused")) {
                return true;
            }
        }
        return false;
    }

    private static ConnectionListener silentListener() {
        return new ConnectionListener() {
            @Override
            public void opened(String port) {
            }

            @Override
            public void closed(String port) {
            }

            @Override
            public void status(String messageKey, Context ctx) {
            }

            @Override
            public void actionRequired(String messageKey, Context ctx) {
            }

            @Override
            public void pairingFinished(PairingResult result, long remoteUid) {
            }

            @Override
            public void handleError(RuntimeException ex) {
            }
        };
    }
}
