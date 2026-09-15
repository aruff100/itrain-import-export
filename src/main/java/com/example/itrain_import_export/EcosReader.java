package com.example.itrain_import_export;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Liest den Bestand einer ESU ECoS aus (Rückmeldemodule, Zubehör,
 * Lokomotiven) - mit Fortschrittsdialog wie {@link BidibNodeReader} - und
 * macht daraus die Objekte für das Systeme-Fenster: die Schnittstelle, je
 * Anschluss eines Rückmeldemoduls einen Rückmelder, je ECoS-Zubehör eine
 * Weiche, je ECoS-Lokomotive eine Lokomotive (Anschlussart LOCOMOTIVE, siehe
 * {@link EcosItemFactory#createLocomotive}). Ein Knotenbaum wie bei BiDiB ist
 * hier nicht nötig: Die ECoS liefert je Objekt schon Name und Adresse, die
 * Auswahl geschieht über die Haken in den Tabellen.
 * <p>
 * Eine ECoS kennt - anders als BiDiB - keine "Merkmale" (Features); der
 * Ordner entfällt im Baum ({@link BidibNodeTreeWindow#createNodeItem}).
 */
public final class EcosReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(EcosReader.class);

    private EcosReader() {
    }

    public static void readWithProgress(Window owner, EcosConnection connection, Consumer<BidibSystemFile> onDone) {
        I18n i18n = I18n.getInstance();
        AppSettings settings = AppSettings.getInstance();

        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle(i18n.t("bidib.readTitle", connection.getName()));
        dialog.setResizable(false);

        Label stepLabel = new Label(i18n.t("bidib.readPreparing"));
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

        Thread worker = new Thread(() -> {
            try {
                BidibSystemFile result = read(connection, i18n, (fraction, text) -> Platform.runLater(() -> {
                    bar.setProgress(fraction);
                    percentLabel.setText(Math.round(fraction * 100) + " %");
                    stepLabel.setText(text);
                }), cancelled);
                Platform.runLater(() -> {
                    dialog.setOnCloseRequest(null);
                    dialog.close();
                    if (result != null) {
                        onDone.accept(result);
                    }
                });
            } catch (Exception ex) {
                LOGGER.warn("ECoS-Auslesen fehlgeschlagen: {}", ex.getMessage(), ex);
                Platform.runLater(() -> {
                    dialog.setOnCloseRequest(null);
                    dialog.close();
                    Alert alert = new Alert(Alert.AlertType.ERROR, String.valueOf(ex.getMessage()));
                    alert.initOwner(owner);
                    alert.setHeaderText(i18n.t("bidib.readFailedTitle"));
                    alert.showAndWait();
                });
            }
        }, "ecos-read");
        worker.setDaemon(true);
        worker.start();
    }

    private interface Progress {
        void report(double fraction, String text);
    }

    /** Objektnummern der Sammelknoten im Baum (die ECoS selbst hat 1). */
    public static final int NODE_ACCESSORIES = 11;
    public static final int NODE_LOCOMOTIVES = 10;

    /**
     * Liest den Bestand in eine System-Datei mit Systemart "ecos": Knoten
     * "0" = die ECoS, je Rueckmeldemodul ein Knoten mit FEEDBACK-Anschluessen
     * (iTrain-Adressen in portAddresses), ein Sammelknoten "Zubehoer" mit
     * ACCESSORY-Anschluessen (Name, DCC-Adresse, Bauform je Anschluss) und
     * ein Knoten "Lokomotiven", dessen Merkmale die Loks auflisten
     * (Name = Adresse). Liefert null bei Abbruch.
     */
    private static BidibSystemFile read(EcosConnection connection, I18n i18n, Progress progress,
            AtomicBoolean cancelled) throws IOException {
        EcosClient client = connection.getClient();
        String host = connection.getHost();

        BidibSystemFile file = new BidibSystemFile();
        file.setSystemType(BidibSystemFile.SYSTEM_ECOS);
        file.setInterfaceName("ECoS");
        file.setHostPort(host + ":" + EcosClient.PORT);
        file.setSerial(false);
        file.setInterfaceUniqueId(1L);

        // Schritt 1: Schnittstelle.
        progress.report(0.05, i18n.t("ecos.readStepInterface"));
        file.getNodes().add(new BidibSystemFile.NodeRecord(1L, "0", connection.getName(),
                "ECoS " + connection.getHardware() + " / " + connection.getVersion(), null, null, List.of()));
        if (cancelled.get()) {
            return null;
        }

        // Schritt 2: Rueckmeldemodule.
        progress.report(0.15, i18n.t("ecos.readStepFeedback"));
        EcosClient.Reply modules = client.send("queryObjects(26, ports)");
        if (!modules.isOk()) {
            throw new IOException("queryObjects(26): " + modules.message());
        }
        int moduleIndex = 0;
        for (EcosClient.Line line : modules.lines()) {
            int id = line.objectId();
            int ports = line.getInt("ports", 0);
            if (id < 100 || id >= 300 || ports <= 0) {
                continue;
            }
            moduleIndex++;
            String moduleName = (id >= 200 ? "ECoSDetector " : "S88 ") + id;
            List<String> names = new ArrayList<>();
            List<String> addresses = new ArrayList<>();
            for (int port = 1; port <= ports; port++) {
                names.add(i18n.t("bidib.namePartFeedback") + " " + port);
                addresses.add(String.valueOf(EcosItemFactory.feedbackAddress(id, port)));
            }
            file.getNodes().add(new BidibSystemFile.NodeRecord(id, String.valueOf(id), moduleName,
                    id >= 200 ? "ECoSDetector" : "S88", "FEEDBACK", ports, List.of(), names, addresses, null));
            progress.report(0.15 + 0.3 * moduleIndex / Math.max(1, modules.lines().size()),
                    i18n.t("ecos.readStepFeedback") + " " + moduleName);
            if (cancelled.get()) {
                return null;
            }
        }

        // Schritt 3: Zubehoer (20000-29999; 30000+ sind Fahrstrassen der ECoS).
        progress.report(0.5, i18n.t("ecos.readStepAccessories"));
        EcosClient.Reply accessories = client.send(
                "queryObjects(11, name1, name2, name3, addr, addrext, protocol, symbol)");
        if (!accessories.isOk()) {
            throw new IOException("queryObjects(11): " + accessories.message());
        }
        List<String> accNames = new ArrayList<>();
        List<String> accAddresses = new ArrayList<>();
        List<String> accTypes = new ArrayList<>();
        for (EcosClient.Line line : accessories.lines()) {
            int id = line.objectId();
            if (id < 20000 || id >= 30000) {
                continue;
            }
            String name = joinNames(line.get("name1"), line.get("name2"), line.get("name3"));
            if (name.isBlank()) {
                name = i18n.t("bidib.namePartAccessory") + " " + id;
            }
            int address = line.getInt("addr", 0);
            if (address == 0) {
                // Zubehoer mit erweiterter Adresse liefert nur addrext, z.B.
                // "605g,605r" (gruen/rot) - die erste Zahl ist die Grundadresse.
                String ext = line.get("addrext");
                if (ext != null) {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(ext);
                    if (m.find()) {
                        address = Integer.parseInt(m.group());
                    }
                }
            }
            int symbol = line.getInt("symbol", 0);
            accNames.add(name);
            accAddresses.add(String.valueOf(address));
            accTypes.add(EcosItemFactory.turnoutTypeForSymbol(symbol));
            if (cancelled.get()) {
                return null;
            }
        }
        file.getNodes().add(new BidibSystemFile.NodeRecord(NODE_ACCESSORIES, String.valueOf(NODE_ACCESSORIES),
                i18n.t("ecos.nodeAccessories"), "ECoS", "ACCESSORY", accNames.size(), List.of(),
                accNames, accAddresses, accTypes));

        // Schritt 4: Lokomotiven - als Anschluesse eines Knotens, genau wie
        // Rueckmelder/Zubehoer: Name, DCC-Adresse und Protokoll je Lok
        // (portType); daraus baut EcosItemFactory.createLocomotive den
        // vollstaendigen iTrain-Eintrag.
        progress.report(0.9, i18n.t("ecos.readStepLocomotives"));
        List<String> locoNames = new ArrayList<>();
        List<String> locoAddresses = new ArrayList<>();
        List<String> locoProtocols = new ArrayList<>();
        EcosClient.Reply locos = client.send("queryObjects(10, addr, name, protocol)");
        if (locos.isOk()) {
            for (EcosClient.Line line : locos.lines()) {
                if (line.objectId() >= 1000 && line.objectId() < 2000) {
                    locoNames.add(nz(line.get("name")));
                    locoAddresses.add(String.valueOf(line.getInt("addr", 0)));
                    locoProtocols.add(nz(line.get("protocol")));
                }
            }
        }
        file.getNodes().add(new BidibSystemFile.NodeRecord(NODE_LOCOMOTIVES, String.valueOf(NODE_LOCOMOTIVES),
                i18n.t("ecos.nodeLocomotives"), "ECoS", "LOCOMOTIVE", locoNames.size(), List.of(),
                locoNames, locoAddresses, locoProtocols));
        progress.report(1.0, i18n.t("ecos.readDone", moduleIndex, accNames.size(), locoNames.size()));
        return file;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String joinNames(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(part.trim());
            }
        }
        return sb.toString();
    }
}
