package com.example.itrain_import_export;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.bidib.jbidibc.core.BidibInterface;
import org.bidib.jbidibc.core.node.BidibNode;
import org.bidib.jbidibc.core.node.RootNode;
import org.bidib.jbidibc.messages.BidibLibrary;
import org.bidib.jbidibc.messages.Feature;
import org.bidib.jbidibc.messages.FeatureData;
import org.bidib.jbidibc.messages.Node;
import org.bidib.jbidibc.messages.StringData;
import org.bidib.jbidibc.messages.utils.ByteUtils;
import org.bidib.jbidibc.messages.utils.NodeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import org.bidib.jbidibc.messages.exception.ProtocolNoAnswerException;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * "Auslesen": holt Interface und alle Knoten einer Verbindung - Kennung,
 * Namen, Merkmale, daraus die Anschlüsse - und zeigt dabei einen
 * Fortschrittsdialog von 0 bis 100 %. Nicht zeitkritisch: Jeder einzelne
 * Schritt wird bei einem Zeitüberlauf bis zu {@value #MAX_ATTEMPTS}-mal
 * wiederholt; erst dann gilt das Auslesen als fehlgeschlagen und es
 * erscheint "Auslesen fehlerhaft! Gerät bitte neu starten" mit einem
 * großen Ausrufezeichen. Antwortet ein Knoten dagegen mit einem
 * Protokollfehler (z.B. "kennt keine Zeichenketten"), ist das keine
 * Störung - der Wert bleibt einfach leer.
 * <p>
 * Das Ergebnis ist eine {@link BidibSystemFile} im Speicher (noch nicht
 * gespeichert); daraus baut {@link BidibNodeTreeWindow} den Baum. So gibt
 * es nur EINEN Weg, den Baum zu zeigen - frisch ausgelesen oder aus der
 * Datei geladen.
 */
public final class BidibNodeReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(BidibNodeReader.class);

    /** Wiederholungen je Schritt, bevor das Auslesen als fehlgeschlagen gilt. */
    static final int MAX_ATTEMPTS = 10;

    /** Pause zwischen zwei Versuchen - das Geraet soll Luft bekommen. */
    private static final long RETRY_PAUSE_MS = 400;

    private BidibNodeReader() {
    }

    /** Ein Schritt ist auch nach allen Wiederholungen nicht durchgekommen. */
    private static final class ReadFailedException extends Exception {
        ReadFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Vom Nutzer abgebrochen. */
    private static final class CancelledException extends Exception {
    }

    /**
     * Startet das Auslesen mit Fortschrittsdialog. {@code onDone} wird auf
     * dem JavaFX-Thread mit dem Ergebnis aufgerufen - bei Abbruch oder
     * Fehlschlag gar nicht.
     */
    public static void readWithProgress(Window owner, BidibConnection connection, Consumer<BidibSystemFile> onDone) {
        I18n i18n = I18n.getInstance();
        AppSettings settings = AppSettings.getInstance();

        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle(i18n.t("bidib.readTitle", connection.getName()));
        dialog.setResizable(false);

        Label stepLabel = new Label(i18n.t("bidib.readPreparing"));
        stepLabel.setWrapText(true);
        stepLabel.setMaxWidth(420);
        ProgressBar bar = new ProgressBar(0);
        bar.setPrefWidth(420);
        Label percentLabel = new Label("0 %");
        Button cancelButton = new Button(i18n.t("bidib.pairingCancelButton"));
        AtomicBoolean cancelled = new AtomicBoolean(false);
        cancelButton.setOnAction(e -> {
            cancelled.set(true);
            cancelButton.setDisable(true);
        });
        HBox buttons = new HBox(cancelButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        VBox content = new VBox(10, stepLabel, bar, percentLabel, buttons);
        content.setPadding(new Insets(16));
        Scene scene = new Scene(new BorderPane(content));
        ThemeManager.apply(scene, settings.getTheme());
        dialog.setScene(scene);
        dialog.setOnCloseRequest(e -> {
            cancelled.set(true);
            e.consume();
        });
        dialog.show();

        // Die Knotenliste auf dem JavaFX-Thread kopieren - sie gehoert der
        // Oberflaeche und kann sich waehrend des Lesens noch aendern.
        List<Node> nodes = new ArrayList<>(connection.getNodeModel().getNodes());
        BidibInterface bidib = connection.getBidib();

        Thread worker = new Thread(() -> {
            BidibSystemFile result = new BidibSystemFile();
            result.setInterfaceName(connection.getName());
            result.setHostPort(connection.getHostPort());
            result.setSerial(!connection.getHostPort().contains(":"));

            int total = (1 + nodes.size()) * 2;
            int[] done = {0};
            Runnable step = () -> {
                done[0]++;
                double fraction = Math.min(1.0, (double) done[0] / total);
                Platform.runLater(() -> {
                    bar.setProgress(fraction);
                    percentLabel.setText(Math.round(fraction * 100) + " %");
                });
            };

            try {
                RootNode root = bidib.getRootNode();
                if (root != null) {
                    Platform.runLater(() -> stepLabel.setText(i18n.t("bidib.readStep",
                            i18n.t("bidib.nodeTreeInterfaceLabel"), 1, 1 + nodes.size())));
                    BidibSystemFile.NodeRecord record = readNode(root, "0", true, cancelled, step);
                    result.setInterfaceUniqueId(record.uniqueId());
                    result.getNodes().add(record);
                }
                int index = 1;
                for (Node node : nodes) {
                    index++;
                    int shownIndex = index;
                    String label = ByteUtils.formatHexUniqueId(node.getUniqueId());
                    Platform.runLater(() -> stepLabel.setText(i18n.t("bidib.readStep", label, shownIndex,
                            1 + nodes.size())));
                    BidibNode bidibNode = bidib.getNode(node);
                    if (bidibNode == null) {
                        step.run();
                        step.run();
                        continue;
                    }
                    result.getNodes().add(readNode(bidibNode, formatAddress(node.getAddr()), false, cancelled, step));
                }
                Platform.runLater(() -> {
                    dialog.setOnCloseRequest(null);
                    dialog.close();
                    onDone.accept(result);
                });
            } catch (CancelledException ex) {
                Platform.runLater(() -> {
                    dialog.setOnCloseRequest(null);
                    dialog.close();
                });
            } catch (ReadFailedException ex) {
                LOGGER.warn("Auslesen fehlgeschlagen: {}", ex.getMessage(), ex.getCause());
                Platform.runLater(() -> {
                    dialog.setOnCloseRequest(null);
                    dialog.close();
                    showFailure(owner, i18n, settings, ex.getMessage());
                });
            }
        }, "bidib-read");
        worker.setDaemon(true);
        worker.start();
    }

    // ------------------------------------------------------------------
    // Lesen eines Knotens
    // ------------------------------------------------------------------

    private static BidibSystemFile.NodeRecord readNode(BidibNode bidibNode, String address, boolean isRoot,
            AtomicBoolean cancelled, Runnable step) throws ReadFailedException, CancelledException {

        // Schritt 1: Kennung und Namen.
        Long uid = retry(cancelled, "uniqueId", () -> bidibNode.getUniqueId());
        String product = retryOptional(cancelled, "productName",
                () -> stringValue(bidibNode.getString(StringData.NAMESPACE_NODE, StringData.INDEX_PRODUCTNAME)));
        String user = retryOptional(cancelled, "userName",
                () -> stringValue(bidibNode.getString(StringData.NAMESPACE_NODE, StringData.INDEX_USERNAME)));
        step.run();

        // Schritt 2: Merkmale. Vorher Protokollversion und Magic holen, ohne
        // die jbidibc bei Unterknoten aussteigt (NullPointerException bzw.
        // Bootloader-Fehlannahme); Knoten ohne Streaming liefern die
        // Merkmale nur einzeln.
        FeatureData data = retry(cancelled, "features", () -> {
            bidibNode.getProtocolVersion();
            bidibNode.getMagic(null);
            return bidibNode.getFeaturesAll();
        });
        // Doppelte (nach einer Wiederholung moeglich) ueber den Typ ausfiltern.
        java.util.Map<Integer, Feature> byType = new java.util.TreeMap<>();
        if (data != null && data.getFeatures() != null) {
            for (Feature feature : data.getFeatures()) {
                byType.putIfAbsent(feature.getType(), feature);
            }
        }
        if (data != null && !data.isStreamingSupport() && data.getFeatureCount() > 0 && byType.isEmpty()) {
            // Einzeln abholen (MSG_FEATURE_GETNEXT). Hinter einem Verteiler
            // geht schon mal eine ANTWORT verloren (der Hub meldet
            // BIDIB_ERR_SUBTIME) - der Knoten hat die Anfrage aber gesehen und
            // seinen Zeiger weitergesetzt, das Merkmal fehlt dann in dieser
            // Runde und am Ende kommt MSG_FEATURE_NA (91). So gesehen am
            // IFnet .93 / B6_2 am 14.09.: 19 gemeldet, 18 geliefert, dann NA -
            // und die NA-Antwort liess das Auslesen abbrechen. Deshalb:
            // FEATURE_NA beendet nur die Runde (retryOptional liefert dafuer
            // null), und fehlen danach Merkmale, faengt eine zweite Runde mit
            // MSG_FEATURE_GETALL (setzt den Zeiger zurueck) von vorn an; die
            // Ergebnisse werden ueber den Typ zusammengefuehrt.
            int expected = data.getFeatureCount();
            for (int round = 1; round <= 3 && byType.size() < expected; round++) {
                if (round > 1) {
                    LOGGER.info("Merkmale von {}: {} von {} nach Runde {} - neue Runde.", address, byType.size(),
                            expected, round - 1);
                    FeatureData again = retry(cancelled, "features", () -> bidibNode.getFeaturesAll());
                    if (again != null && again.getFeatures() != null) {
                        for (Feature feature : again.getFeatures()) {
                            byType.putIfAbsent(feature.getType(), feature);
                        }
                    }
                }
                for (int i = 0; i < expected; i++) {
                    Feature feature = retryOptional(cancelled, "feature " + (i + 1) + "/" + expected,
                            () -> bidibNode.getNextFeature());
                    if (feature == null) {
                        break;
                    }
                    byType.putIfAbsent(feature.getType(), feature);
                }
            }
        }
        step.run();

        List<Feature> features = new ArrayList<>(byType.values());
        List<BidibSystemFile.FeatureRecord> records = new ArrayList<>();
        Integer feedbackPorts = null;
        Integer accessoryPorts = null;
        for (Feature feature : features) {
            records.add(new BidibSystemFile.FeatureRecord(feature.getType(), feature.getFeatureName(),
                    feature.getValue()));
            if (feature.getType() == BidibLibrary.FEATURE_BM_SIZE) {
                feedbackPorts = feature.getValue();
            } else if (feature.getType() == BidibLibrary.FEATURE_ACCESSORY_COUNT) {
                accessoryPorts = feature.getValue();
            }
        }

        long id = uid == null ? 0L : uid;
        String portKind = null;
        Integer portCount = null;
        if (id != 0L && NodeUtils.hasFeedbackFunctions(id) && feedbackPorts != null && feedbackPorts > 0) {
            portKind = "FEEDBACK";
            portCount = feedbackPorts;
        } else if (id != 0L && NodeUtils.hasAccessoryFunctions(id) && accessoryPorts != null && accessoryPorts > 0) {
            portKind = "ACCESSORY";
            portCount = accessoryPorts;
        }
        return new BidibSystemFile.NodeRecord(id, address, user, product, portKind, portCount, records);
    }

    /**
     * Fuehrt einen Schritt aus und wiederholt ihn bei Zeitueberlauf bis zu
     * {@value #MAX_ATTEMPTS}-mal. Ein Protokollfehler (das Geraet hat
     * geantwortet, aber mit Fehler) wird NICHT wiederholt - er wird
     * weitergereicht, damit der Aufrufer entscheidet.
     */
    private static <T> T retry(AtomicBoolean cancelled, String what, Callable<T> action)
            throws ReadFailedException, CancelledException {
        Throwable last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (cancelled.get()) {
                throw new CancelledException();
            }
            try {
                return action.call();
            } catch (Exception ex) {
                last = ex;
                if (!isTimeout(ex)) {
                    throw new ReadFailedException(what + ": " + ex.getMessage(), ex);
                }
                LOGGER.info("Zeitueberlauf bei {} (Versuch {} von {})", what, attempt, MAX_ATTEMPTS);
                try {
                    Thread.sleep(RETRY_PAUSE_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new CancelledException();
                }
            }
        }
        throw new ReadFailedException(what + ": " + MAX_ATTEMPTS + " Versuche", last);
    }

    /**
     * Wie {@link #retry}, aber ein Protokollfehler ergibt {@code null} statt
     * eines Fehlschlags - fuer Werte, die ein Knoten schlicht nicht kennt
     * (Zeichenketten bei einfachen Knoten).
     */
    private static <T> T retryOptional(AtomicBoolean cancelled, String what, Callable<T> action)
            throws ReadFailedException, CancelledException {
        Throwable last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (cancelled.get()) {
                throw new CancelledException();
            }
            try {
                return action.call();
            } catch (Exception ex) {
                last = ex;
                if (!isTimeout(ex)) {
                    LOGGER.debug("{} nicht abrufbar: {}", what, ex.getMessage());
                    return null;
                }
                LOGGER.info("Zeitueberlauf bei {} (Versuch {} von {})", what, attempt, MAX_ATTEMPTS);
                try {
                    Thread.sleep(RETRY_PAUSE_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new CancelledException();
                }
            }
        }
        throw new ReadFailedException(what + ": " + MAX_ATTEMPTS + " Versuche", last);
    }

    /**
     * "Keine Antwort" irgendwo in der Ursachenkette - jbidibc verpackt das
     * unterschiedlich: als TimeoutException, als
     * ProtocolNoAnswerException ("No response received from ... message!")
     * oder nur im Text. Genau diese Faelle lohnen eine Wiederholung; eine
     * echte Fehlerantwort des Geraets nicht.
     * <p>
     * Die ProtocolNoAnswerException fehlte hier bis zum 14.09. - deshalb
     * wurde in Wirklichkeit NIE wiederholt: Beim IFnet .93 brach das
     * Auslesen hinter dem ReadyHub beim ersten verlorenen Merkmal ab (der
     * Hub meldete MSG_SYS_ERROR BIDIB_ERR_SUBTIME, der Unterknoten hatte
     * ihm nicht rechtzeitig geantwortet), und beim mc2 lief die allererste
     * Abfrage ohne einen einzigen zweiten Versuch in den Fehlerdialog.
     */
    private static boolean isTimeout(Throwable ex) {
        Throwable t = ex;
        int guard = 0;
        while (t != null && guard++ < 10) {
            if (t instanceof TimeoutException || t instanceof ProtocolNoAnswerException) {
                return true;
            }
            String message = t.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(java.util.Locale.ROOT);
                if (lower.contains("timeout") || lower.contains("no response")) {
                    return true;
                }
            }
            t = t.getCause();
        }
        return false;
    }

    private static String stringValue(StringData data) {
        String value = data != null ? data.getValue() : null;
        return value != null && !value.isBlank() ? value : null;
    }

    private static String formatAddress(byte[] addr) {
        if (addr == null || addr.length == 0) {
            return "0";
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : addr) {
            int v = b & 0xFF;
            if (v == 0) {
                break;
            }
            if (sb.length() > 0) {
                sb.append('.');
            }
            sb.append(v);
        }
        return sb.length() == 0 ? "0" : sb.toString();
    }

    // ------------------------------------------------------------------
    // Fehlerdialog
    // ------------------------------------------------------------------

    /** "Auslesen fehlerhaft! Geraet bitte neu starten" mit grossem gelbem Ausrufezeichen darunter. */
    private static void showFailure(Window owner, I18n i18n, AppSettings settings, String detail) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(i18n.t("bidib.readFailedTitle"));
        stage.setResizable(false);

        Label text = new Label(i18n.t("bidib.readFailedText"));
        text.setWrapText(true);
        text.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        text.setAlignment(Pos.CENTER);

        Circle circle = new Circle(36, Color.web("#f2c400"));
        circle.setStroke(Color.web("#8a6d00"));
        circle.setStrokeWidth(2);
        Text mark = new Text("!");
        mark.setFont(Font.font("System", FontWeight.BOLD, 48));
        mark.setFill(Color.web("#2b2b2b"));
        StackPane badge = new StackPane(circle, mark);

        Button ok = new Button("OK");
        ok.setDefaultButton(true);
        ok.setOnAction(e -> stage.close());
        HBox buttons = new HBox(ok);
        buttons.setAlignment(Pos.CENTER);

        // Der technische Grund (welcher Schritt, welche Ausnahme) - klein
        // und kopierbar, damit er sich weitergeben laesst.
        javafx.scene.control.TextField detailField = new javafx.scene.control.TextField(detail == null ? "" : detail);
        detailField.setEditable(false);
        detailField.setPrefColumnCount(48);
        detailField.setStyle("-fx-font-size: 11px;");

        VBox content = new VBox(14, text, badge, detailField, buttons);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(20));
        Scene scene = new Scene(new BorderPane(content));
        ThemeManager.apply(scene, settings.getTheme());
        stage.setScene(scene);
        stage.showAndWait();
    }
}
