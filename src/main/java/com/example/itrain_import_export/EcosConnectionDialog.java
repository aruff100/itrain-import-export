package com.example.itrain_import_export;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.control.TableView;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Verbindungsfenster für ESU ECoS: eine automatisch beim Öffnen startende
 * Subnetz-Abtastung (siehe {@link EcosDiscovery} - AUSDRÜCKLICH keine echte
 * Gerätesuche über ein Discovery-Protokoll, die ECoS kennt keins) mit
 * Fundliste, darunter die IP-Adresse zum manuellen Eintragen (Port ist fest
 * {@value EcosClient#PORT}), "Verbinden", und schließlich die bestehenden
 * ECoS-Verbindungen mit "Trennen". Die zuletzt benutzte Adresse wird
 * gemerkt. Höchstens ein Fenster.
 */
public final class EcosConnectionDialog {

    private static Stage open;

    private EcosConnectionDialog() {
    }

    public static void show(Stage owner) {
        if (open != null && open.isShowing()) {
            open.toFront();
            open.requestFocus();
            return;
        }
        I18n i18n = I18n.getInstance();
        AppSettings settings = AppSettings.getInstance();

        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.NONE);
        stage.setTitle(i18n.t("ecos.dialogTitle"));

        Label hint = new Label(i18n.t("ecos.hint"));
        hint.setWrapText(true);

        // ==== Netz: per Subnetz-Abtastung gefundene ECoS =====================
        // (siehe EcosDiscovery: KEIN echtes Discovery-Protokoll, sondern eine
        // Portabtastung des lokalen /24-Netzes mit Protokoll-Bestaetigung.)
        Label discoveredLabel = new Label(i18n.t("bidib.discoveredLabel"));
        TableView<EcosDiscoveredDevice> deviceList = new TableView<>();
        deviceList.setPlaceholder(new Label(i18n.t("bidib.noDevicesFound")));
        deviceList.setPrefHeight(110);
        deviceList.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TableColumn<EcosDiscoveredDevice, String> deviceColumn = new TableColumn<>(i18n.t("bidib.deviceColumn"));
        deviceColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getDisplayText()));
        deviceColumn.setReorderable(false);
        deviceList.getColumns().setAll(deviceColumn);
        Button rescanButton = new Button(i18n.t("bidib.rescanButton"));
        HBox discoveryRow = new HBox(8, discoveredLabel, rescanButton);
        discoveryRow.setAlignment(Pos.CENTER_LEFT);

        Label ipLabel = new Label(i18n.t("bidib.ipLabel"));
        TextField ipField = new TextField(settings.getEcosHost());
        ipField.setPrefColumnCount(16);
        Button connectButton = new Button(i18n.t("bidib.connectButton"));
        connectButton.setDefaultButton(true);
        HBox ipRow = new HBox(8, ipLabel, ipField, connectButton);
        ipRow.setAlignment(Pos.CENTER_LEFT);

        Label statusLabel = new Label();
        statusLabel.setWrapText(true);
        // Wie im BiDiB-Fenster: Statuszeile im vertieften Rahmen, Erfolg gruen.
        BidibConnectionDialog.installStatusStyling(statusLabel);

        // Auswahl in der Fundliste fuellt das IP-Feld (wie im BiDiB-Dialog);
        // wer die Adresse von Hand aendert, hebt die Auswahl wieder auf,
        // damit "Verbinden" garantiert die eingetippte Adresse verwendet.
        boolean[] fillingField = {false};
        deviceList.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null) {
                fillingField[0] = true;
                ipField.setText(selected.getHost());
                fillingField[0] = false;
            }
        });
        ipField.textProperty().addListener((obs, old, value) -> {
            if (!fillingField[0]) {
                deviceList.getSelectionModel().clearSelection();
            }
        });
        // Doppelklick auf einen Fund = "Verbinden".
        deviceList.setRowFactory(tv -> {
            TableRow<EcosDiscoveredDevice> row = new TableRow<>();
            row.setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2 && !row.isEmpty()) {
                    deviceList.getSelectionModel().select(row.getItem());
                    connectButton.fire();
                }
            });
            return row;
        });

        // Abtastung startet sofort beim Oeffnen und laeuft im Hintergrund
        // weiter; "Erneut suchen" bricht einen laufenden Lauf ab und beginnt
        // neu. AtomicInteger zaehlt die Funde fuer die Abschlussmeldung mit.
        AtomicInteger foundCount = new AtomicInteger(0);
        EcosDiscovery[] discoveryHolder = new EcosDiscovery[1];
        Runnable startScan = () -> {
            foundCount.set(0);
            statusLabel.setText(i18n.t("ecos.scanning"));
            EcosDiscovery discovery = new EcosDiscovery(
                    device -> Platform.runLater(() -> {
                        foundCount.incrementAndGet();
                        deviceList.getItems().add(device);
                    }),
                    () -> Platform.runLater(() -> statusLabel.setText(i18n.t("ecos.scanDone", foundCount.get()))));
            discoveryHolder[0] = discovery;
            discovery.start();
        };
        rescanButton.setOnAction(e -> {
            if (discoveryHolder[0] != null) {
                discoveryHolder[0].stop();
            }
            deviceList.getItems().clear();
            startScan.run();
        });

        Label existingLabel = new Label(i18n.t("bidib.openConnectionsLabel"));
        ListView<EcosConnection> list = new ListView<>(EcosConnection.getConnections());
        list.setPrefHeight(110);
        Button disconnectButton = new Button(i18n.t("bidib.disconnectButton"));
        disconnectButton.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        disconnectButton.setOnAction(e -> {
            EcosConnection selected = list.getSelectionModel().getSelectedItem();
            if (selected != null) {
                selected.close();
                statusLabel.setText(i18n.t("bidib.statusClosed"));
            }
        });
        Button closeButton = new Button(i18n.t("bidib.continueButton"));
        closeButton.setCancelButton(true);
        // Nach erfolgreicher Verbindung blinkt "Weiter zur Bearbeitung" rot.
        Runnable stopBlink = BidibConnectionDialog.installContinueBlink(statusLabel, closeButton);
        closeButton.setOnAction(e -> {
            stopBlink.run();
            stage.close();
        });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bottom = new HBox(8, disconnectButton, spacer, closeButton);
        bottom.setAlignment(Pos.CENTER_LEFT);

        connectButton.setOnAction(e -> {
            String host = ipField.getText() == null ? "" : ipField.getText().trim();
            if (host.isEmpty()) {
                return;
            }
            if (EcosConnection.findByHost(host) != null) {
                statusLabel.setText(i18n.t("bidib.alreadyConnected"));
                return;
            }
            settings.setEcosHost(host);
            connectButton.setDisable(true);
            statusLabel.setText(i18n.t("bidib.statusConnecting"));
            Thread worker = new Thread(() -> {
                try {
                    EcosConnection connection = EcosConnection.open(host);
                    Platform.runLater(() -> {
                        BidibConnectionDialog.markSuccess(statusLabel, i18n.t("ecos.statusConnected",
                                connection.getVersion(), connection.getHardware()));
                        connectButton.setDisable(false);
                    });
                } catch (IOException ex) {
                    String detail = String.valueOf(ex.getMessage());
                    boolean refused = ex instanceof java.net.ConnectException
                            || detail.toLowerCase(java.util.Locale.ROOT).contains("refused");
                    Platform.runLater(() -> {
                        statusLabel.setText(i18n.t("bidib.statusError", detail));
                        connectButton.setDisable(false);
                        // Wie bei BiDiB: deutlicher Fehlerdialog statt nur einer Zeile.
                        BidibConnectionDialog.showConnectFailed(stage, i18n, "ECoS",
                                host + ":" + EcosClient.PORT, refused, detail);
                    });
                }
            }, "ecos-connect");
            worker.setDaemon(true);
            worker.start();
        });

        VBox content = new VBox(10, hint, discoveryRow, deviceList, ipRow,
                BidibConnectionDialog.sunkenBox(statusLabel), existingLabel, list, bottom);
        content.setPadding(new Insets(14));
        VBox.setVgrow(list, Priority.ALWAYS);
        Scene scene = new Scene(new BorderPane(content));
        ThemeManager.apply(scene, settings.getTheme());
        stage.setScene(scene);
        stage.setMinWidth(520);
        stage.titleProperty().bind(Bindings.concat(i18n.t("ecos.dialogTitle")));
        // Abtastung beenden, sobald das Fenster schliesst - offene
        // ECoS-VERBINDUNGEN bleiben davon unberuehrt (siehe EcosConnection).
        stage.setOnHidden(e -> {
            stopBlink.run();
            if (discoveryHolder[0] != null) {
                discoveryHolder[0].stop();
            }
            if (open == stage) {
                open = null;
            }
        });
        open = stage;
        WindowState.apply(stage, "ecosConnection", 560, 560);
        stage.show();
        startScan.run();
    }
}
