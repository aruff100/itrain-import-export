package com.example.itrain_import_export;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import org.bidib.jbidibc.core.BidibInterface;
import org.bidib.jbidibc.messages.ConnectionListener;
import org.bidib.jbidibc.messages.ProtocolVersion;
import org.bidib.jbidibc.messages.enums.NetBidibRole;
import org.bidib.jbidibc.messages.enums.PairingResult;
import org.bidib.jbidibc.messages.helpers.Context;
import org.bidib.jbidibc.messages.helpers.DefaultContext;
import org.bidib.jbidibc.messages.message.netbidib.NetBidibLinkData;
import org.bidib.jbidibc.messages.message.netbidib.NetBidibLinkData.PairingStatus;
import org.bidib.jbidibc.messages.message.netbidib.NetBidibLinkData.PartnerType;
import org.bidib.jbidibc.messages.utils.ByteUtils;
import org.bidib.jbidibc.netbidib.NetBidibContextKeys;
import org.bidib.jbidibc.netbidib.client.NetBidibClient;
import org.bidib.jbidibc.netbidib.client.pairingstates.PairingStateEnum;
import org.bidib.jbidibc.netbidib.pairingstore.LocalPairingStore;
import org.bidib.jbidibc.netbidib.pairingstore.PairingStoreEntry;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Menü "BiDiB" → "Verbinden...": Verbindungsaufbau zu netBiDiB-Servern
 * im lokalen Netz (siehe bidib.org/transport/bidib_net_e.html).
 * <p>
 * Ablauf: Geräte werden per DNS Service Discovery (mDNS, Dienst-Typ
 * "_bidib._tcp.local.") automatisch gesucht und in einer Liste angezeigt;
 * alternativ lässt sich eine Adresse von Hand eingeben. Beim ersten
 * Verbindungsversuch zu einem noch unbekannten Gerät verlangt netBiDiB ein
 * einmaliges "Pairing" - dafür erscheint {@link BidibPairingDialog} mit einem
 * herunterzählenden Zeitgeber; bestätigt werden muss zusätzlich am Gerät
 * selbst. Das Ergebnis wird dauerhaft gespeichert (siehe
 * {@link #pairingStoreFile()}), sodass spätere Verbindungen zum selben Gerät
 * ohne erneutes Pairing funktionieren.
 * <p>
 * <b>Mehrere Verbindungen gleichzeitig:</b> Findet die Suche mehrere
 * Interfaces, lassen sich alle nacheinander verbinden - jedes "Verbinden"
 * legt eine WEITERE Verbindung an, statt die bestehende zu ersetzen. Die
 * offenen Verbindungen werden zentral in {@link BidibConnectionManager}
 * gehalten (nicht in diesem Dialog), damit sie bestehen bleiben, wenn dieses
 * Fenster geschlossen wird. Angezeigt und ausgewählt werden sie danach über
 * die Statusleiste des Hauptfensters und die Untermenüs unter "BiDiB".
 * <p>
 * Die eigentliche Nachrichten-Kodierung und der Pairing-Zustandsautomat
 * stammen aus der jbidibc-Bibliothek (siehe build.gradle.kts) - hier wird nur
 * die Bedienoberfläche und die Verdrahtung mit den restlichen Einstellungen
 * der Anwendung ergänzt.
 */
public final class BidibConnectionDialog {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(BidibConnectionDialog.class);

    /** Standard-TCP-Port für netBiDiB-Server (siehe bidib.org/transport/bidib_net_e.html). */
    private static final String DEFAULT_PORT = "62875";

    /** Vom Protokoll angefragtes Zeitlimit für das Pairing, in Sekunden. */
    private static final int PAIRING_TIMEOUT_SECONDS = 30;

    /** USB-Herstellerkennung von FTDI - die USB-Bruecke in IF2, GBMboost & Co. */
    private static final int FTDI_VENDOR_ID = 0x0403;

    /**
     * Datei, in der zugelassene (gepairte) Geräte dauerhaft gemerkt werden.
     * Paketsichtbar (nicht privat), weil {@link BidibPairingStoreDialog}
     * dieselbe Datei zum Anzeigen/Löschen von Einträgen braucht.
     */
    static File pairingStoreFile() {
        String home = System.getProperty("user.home");
        return new File(home, ".itrain_import_export_bidib" + File.separator + "netbidib-pairing.json");
    }

    /**
     * Das eine offene Verbindungsfenster - ein zweiter Aufruf von
     * {@link #show(Stage)} holt es nach vorn, statt ein weiteres zu öffnen
     * (zwei Fenster hätten zwei parallele Gerätesuchen laufen lassen).
     */
    private static Stage open;

    private BidibConnectionDialog() {
    }

    public static void show(Stage owner) {
        if (open != null && open.isShowing()) {
            open.toFront();
            open.requestFocus();
            return;
        }
        I18n i18n = I18n.getInstance();
        AppSettings settings = AppSettings.getInstance();
        BidibConnectionManager manager = BidibConnectionManager.getInstance();

        Label statusLabel = new Label(i18n.t("bidib.statusIdle"));
        statusLabel.setWrapText(true);
        statusLabel.setStyle("-fx-font-weight: bold;");

        // ==== 1. Netz: gefundene netBiDiB-Geraete ============================
        Label hintLabel = new Label(i18n.t("bidib.hint"));
        hintLabel.setWrapText(true);

        Label discoveredLabel = new Label(i18n.t("bidib.discoveredLabel"));
        // Drei Spalten: Ankreuzkasten "Verbunden" (nur Anzeige), das gefundene
        // Geraet, und ob dafuer schon ein Pairing im Speicher liegt. Steht
        // dort "Pairing verfuegbar", laeuft das Verbinden ohne erneute
        // Pairing-Abfrage durch (siehe isKnownPaired und actionRequired).
        TableView<BidibDiscoveredDevice> deviceList = new TableView<>();
        deviceList.setPlaceholder(new Label(i18n.t("bidib.noDevicesFound")));
        deviceList.setPrefHeight(140);
        deviceList.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<BidibDiscoveredDevice, String> deviceColumn =
                new TableColumn<>(i18n.t("bidib.deviceColumn"));
        deviceColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getDisplayText()));
        deviceColumn.setPrefWidth(360);
        deviceColumn.setReorderable(false);

        TableColumn<BidibDiscoveredDevice, String> pairingColumn =
                new TableColumn<>(i18n.t("bidib.pairingColumn"));
        pairingColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                isKnownPaired(cell.getValue().getUid())
                        ? i18n.t("bidib.pairingAvailable")
                        : i18n.t("bidib.pairingRequired")));
        pairingColumn.setPrefWidth(160);
        pairingColumn.setReorderable(false);

        deviceList.getColumns().setAll(java.util.List.of(
                connectedColumn(i18n, manager, BidibConnectionDialog::hostPortOf), deviceColumn, pairingColumn));

        Button rescanButton = new Button(i18n.t("bidib.rescanButton"));
        HBox discoveryRow = new HBox(8, discoveredLabel, rescanButton);
        discoveryRow.setAlignment(Pos.CENTER_LEFT);

        // ==== 2. Manuell: Adresse eintippen ==================================
        Label manualLabel = new Label(i18n.t("bidib.manualLabel"));
        manualLabel.setStyle("-fx-font-weight: bold;");
        Label manualHint = new Label(i18n.t("bidib.manualHint"));
        manualHint.setWrapText(true);
        Label ipLabel = new Label(i18n.t("bidib.ipLabel"));
        TextField ipField = new TextField(settings.getBidibLastIp() != null ? settings.getBidibLastIp() : "");
        ipField.setPromptText("192.168.1.23");
        HBox.setHgrow(ipField, Priority.ALWAYS);
        Label portLabel = new Label(i18n.t("bidib.portLabel"));
        TextField portField = new TextField(
                settings.getBidibLastPort() != null ? settings.getBidibLastPort() : DEFAULT_PORT);
        portField.setPrefColumnCount(6);
        HBox addressRow = new HBox(8, ipLabel, ipField, portLabel, portField);
        addressRow.setAlignment(Pos.CENTER_LEFT);
        // Eigener "Verbinden"-Knopf direkt unter der manuellen Adresse - wirkt
        // wie der Haupt-Knopf unten (siehe manualConnectButton.setOnAction
        // weiter unten, nachdem dieser feststeht), zusaetzlich zum
        // Doppelklick auf das IP-Feld (siehe ipField.setOnMouseClicked).
        Button manualConnectButton = new Button(i18n.t("bidib.connectButton"));
        HBox manualConnectRow = new HBox(manualConnectButton);
        manualConnectRow.setAlignment(Pos.CENTER_LEFT);

        // ==== 2b. mc2 per FTP gefunden (Zusatzerkennung, siehe Mc2Discovery) =
        // Die mc2 meldet sich derzeit nicht zuverlaessig per mDNS (Andre:
        // "wird das mDNS Protokoll demnaechst wieder unterstuetzen") - bis
        // dahin findet dieser zweite, von der mDNS-Suche oben UNABHAENGIGE
        // Scan sie ueber ihren FTP-Zugang (siehe Mc2Discovery). Ein Fund
        // traegt nur die Adresse in die Manuell-Felder ein; verbunden wird
        // trotzdem ganz normal per netBiDiB (Standard-Port), nicht per FTP -
        // die FTP-Pruefung dient ausschliesslich dem Auffinden der Adresse.
        // Wird dieselbe mc2 gleichzeitig auch per mDNS gefunden, erscheint
        // sie bewusst ZWEIMAL: oben in der Netz-Liste als BiDiB-Geraet, hier
        // als "mc2 (FTP)".
        Label mc2Label = new Label(i18n.t("mc2.ftpFoundLabel"));
        TableView<Mc2DiscoveredDevice> mc2List = new TableView<>();
        mc2List.setPlaceholder(new Label(i18n.t("mc2.noFtpFound")));
        mc2List.setFixedCellSize(24);
        mc2List.setPrefHeight(24 * 2 + 30);
        mc2List.setMinHeight(24 * 2 + 30);
        mc2List.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TableColumn<Mc2DiscoveredDevice, String> mc2Column = new TableColumn<>(i18n.t("bidib.deviceColumn"));
        mc2Column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getDisplayText()));
        mc2Column.setReorderable(false);
        mc2List.getColumns().setAll(mc2Column);
        Button mc2RescanButton = new Button(i18n.t("bidib.rescanButton"));
        HBox mc2Row = new HBox(8, mc2Label, mc2RescanButton);
        mc2Row.setAlignment(Pos.CENTER_LEFT);

        // ==== 3. USB: COM-Ports mit Pruefroutine =============================
        // Interfaces am virtuellen COM-Port (FTDI-USB, z.B. IF2/GBMboost -
        // bidib.org/transport/bidib_seriell_e.html). Kein Pairing. Die
        // Pruefroutine (BidibSerialSupport.probe) oeffnet jeden Port kurz mit
        // dem BiDiB-Protokoll; FTDI-Ports (USB-Kennung 0403) automatisch beim
        // Oeffnen, alle anderen ueber "Pruefen". Gefundene Geraete werden in
        // der Liste hervorgehoben.
        Label usbLabel = new Label(i18n.t("bidib.serialLabel"));
        usbLabel.setStyle("-fx-font-weight: bold;");
        Label usbHint = new Label(i18n.t("bidib.usbHint"));
        usbHint.setWrapText(true);
        Button usbRescanButton = new Button(i18n.t("bidib.rescanButton"));
        Button usbProbeButton = new Button(i18n.t("bidib.usbProbeButton"));
        HBox usbRow = new HBox(8, usbLabel, usbRescanButton, usbProbeButton);
        usbRow.setAlignment(Pos.CENTER_LEFT);

        TableView<PortRow> usbList = new TableView<>();
        usbList.setPlaceholder(new Label(i18n.t("bidib.noSerialPorts")));
        // Drei Zeilen sichtbar, der Rest scrollt.
        usbList.setFixedCellSize(24);
        usbList.setPrefHeight(24 * 3 + 30);
        usbList.setMinHeight(24 * 3 + 30);
        usbList.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<PortRow, String> portColumn = new TableColumn<>(i18n.t("bidib.serialPortColumn"));
        portColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().systemName));
        portColumn.setPrefWidth(80);
        portColumn.setReorderable(false);
        TableColumn<PortRow, String> descriptionColumn = new TableColumn<>(i18n.t("editor.columnDescription"));
        descriptionColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().description));
        descriptionColumn.setPrefWidth(200);
        descriptionColumn.setReorderable(false);
        TableColumn<PortRow, String> vendorColumn = new TableColumn<>(i18n.t("bidib.usbVendorColumn"));
        vendorColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().vendor));
        vendorColumn.setPrefWidth(170);
        vendorColumn.setReorderable(false);
        TableColumn<PortRow, String> bidibColumn = new TableColumn<>("BiDiB");
        bidibColumn.setCellValueFactory(cell -> cell.getValue().bidib);
        bidibColumn.setPrefWidth(220);
        bidibColumn.setReorderable(false);
        usbList.getColumns().setAll(java.util.List.of(
                connectedColumn(i18n, manager, row -> row.systemName),
                portColumn, descriptionColumn, vendorColumn, bidibColumn));
        // Gefundene BiDiB-Geraete hervorheben: fett und mit gruenlichem Grund,
        // halbtransparent, damit es in heller UND dunkler Ansicht sichtbar ist.
        usbList.setRowFactory(tv -> {
            javafx.scene.control.TableRow<PortRow> row = new javafx.scene.control.TableRow<>();
            javafx.beans.value.ChangeListener<String> restyle = (obs, old, value) -> styleUsbRow(row);
            row.itemProperty().addListener((obs, oldItem, newItem) -> {
                if (oldItem != null) {
                    oldItem.bidib.removeListener(restyle);
                }
                if (newItem != null) {
                    newItem.bidib.addListener(restyle);
                }
                styleUsbRow(row);
            });
            return row;
        });

        Runnable usbRescan = () -> rescanUsbPorts(usbList, usbRescanButton, usbProbeButton, statusLabel, i18n, manager);
        usbRescanButton.setOnAction(e -> usbRescan.run());
        usbProbeButton.setOnAction(e -> probeUsbPorts(new java.util.ArrayList<>(usbList.getItems()), usbList,
                statusLabel, usbProbeButton, i18n, manager));

        // ==== 4. Bestehende Verbindungen =====================================
        Label openLabel = new Label(i18n.t("bidib.openConnectionsLabel"));
        openLabel.setStyle("-fx-font-weight: bold;");
        // Tabelle statt Liste: links ein ANKLICKBARER Ankreuzkasten - Haken
        // entfernen wirkt wie "Trennen", die Verbindung verschwindet aus der
        // Tabelle. (Das Trennen laeuft im Hintergrund, siehe disconnect().)
        TableView<BidibConnection> connectionList = new TableView<>(manager.getConnections());
        connectionList.setPlaceholder(new Label(i18n.t("bidib.noOpenConnections")));
        connectionList.setPrefHeight(90);
        connectionList.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TableColumn<BidibConnection, Boolean> openCheckColumn = new TableColumn<>(i18n.t("bidib.connectedColumn"));
        openCheckColumn.setCellValueFactory(cell -> new ReadOnlyBooleanWrapper(true));
        openCheckColumn.setCellFactory(col -> {
            TableCell<BidibConnection, Boolean> cell = new TableCell<>() {
                private final CheckBox box = new CheckBox();
                {
                    box.setFocusTraversable(false);
                    box.setOnAction(e -> {
                        BidibConnection connection = getTableRow() != null ? getTableRow().getItem() : null;
                        if (connection != null && !box.isSelected()) {
                            disconnect(connection, statusLabel, i18n, manager);
                        }
                    });
                }

                @Override
                protected void updateItem(Boolean value, boolean empty) {
                    super.updateItem(value, empty);
                    if (empty) {
                        setGraphic(null);
                        return;
                    }
                    box.setSelected(true);
                    setGraphic(box);
                }
            };
            cell.setAlignment(Pos.CENTER);
            return cell;
        });
        openCheckColumn.setPrefWidth(95);
        openCheckColumn.setMinWidth(95);
        openCheckColumn.setMaxWidth(95);
        openCheckColumn.setResizable(false);
        openCheckColumn.setReorderable(false);
        openCheckColumn.setSortable(false);
        TableColumn<BidibConnection, String> openNameColumn = new TableColumn<>(i18n.t("bidib.deviceColumn"));
        openNameColumn.setCellValueFactory(cell -> cell.getValue().nameProperty());
        openNameColumn.setPrefWidth(300);
        openNameColumn.setReorderable(false);
        TableColumn<BidibConnection, String> openAddressColumn = new TableColumn<>(i18n.t("bidib.ipLabel"));
        openAddressColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getHostPort()));
        openAddressColumn.setReorderable(false);
        connectionList.getColumns().setAll(java.util.List.of(openCheckColumn, openNameColumn, openAddressColumn));

        // Haken-Spalten beider Fundlisten bei jeder Aenderung der
        // Verbindungsliste neu berechnen; beim Schliessen wieder abmelden.
        javafx.collections.ListChangeListener<BidibConnection> refreshOnChange = change -> {
            deviceList.refresh();
            usbList.refresh();
        };
        manager.getConnections().addListener(refreshOnChange);

        // ==== Auswahl: Netz, USB und Adressfelder schliessen sich aus ========
        // Ein Geraet in der Netzliste fuellt die Adressfelder; eine Auswahl
        // in der USB-Liste hebt die Netzauswahl auf und umgekehrt; wer die
        // Adresse von Hand aendert, hebt beide Auswahlen auf. "Verbinden"
        // wirkt dann auf das, was uebrig bleibt.
        boolean[] fillingFields = {false};
        deviceList.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null) {
                usbList.getSelectionModel().clearSelection();
                mc2List.getSelectionModel().clearSelection();
                fillingFields[0] = true;
                ipField.setText(selected.getAddress() != null ? selected.getAddress().getHostAddress() : "");
                portField.setText(String.valueOf(selected.getPort()));
                fillingFields[0] = false;
            }
        });
        usbList.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null) {
                deviceList.getSelectionModel().clearSelection();
                mc2List.getSelectionModel().clearSelection();
            }
        });
        // mc2-per-FTP-Fund: nur die Adresse ins Manuell-Feld uebernehmen (fester
        // Standard-Port, siehe DEFAULT_PORT) - verbunden wird trotzdem ganz
        // normal per netBiDiB, nicht per FTP (siehe Mc2Discovery).
        mc2List.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null) {
                deviceList.getSelectionModel().clearSelection();
                usbList.getSelectionModel().clearSelection();
                fillingFields[0] = true;
                ipField.setText(selected.getHost());
                portField.setText(DEFAULT_PORT);
                fillingFields[0] = false;
            }
        });
        javafx.beans.value.ChangeListener<String> manualEdit = (obs, old, value) -> {
            if (!fillingFields[0]) {
                deviceList.getSelectionModel().clearSelection();
                usbList.getSelectionModel().clearSelection();
                mc2List.getSelectionModel().clearSelection();
            }
        };
        ipField.textProperty().addListener(manualEdit);
        portField.textProperty().addListener(manualEdit);

        // Der Name, unter dem sich das Programm beim Geraet meldet: ein frueher
        // gespeicherter Wert, sonst der Rechnername.
        String userName = settings.getBidibUserName() != null && !settings.getBidibUserName().isBlank()
                ? settings.getBidibUserName() : defaultUserName();

        Button connectButton = new Button(i18n.t("bidib.connectButton"));
        Button disconnectButton = new Button(i18n.t("bidib.disconnectButton"));
        disconnectButton.disableProperty().bind(
                connectionList.getSelectionModel().selectedItemProperty().isNull());
        Button disconnectAllButton = new Button(i18n.t("bidib.disconnectAllButton"));
        disconnectAllButton.disableProperty().bind(Bindings.isEmpty(manager.getConnections()));
        Button pairingStoreButton = new Button(i18n.t("bidib.pairingStoreButton"));
        Button localUidButton = new Button(i18n.t("bidib.localUidButton"));

        BidibDiscovery discovery = new BidibDiscovery(devices -> Platform.runLater(() -> {
            BidibDiscoveredDevice selected = deviceList.getSelectionModel().getSelectedItem();
            deviceList.getItems().setAll(devices.values());
            // Der Pairing-Speicher kann sich zwischenzeitlich geaendert haben
            // (erstes Pairing, Eintrag geloescht) - Spalte neu berechnen lassen.
            deviceList.refresh();
            if (selected != null && deviceList.getItems().contains(selected)) {
                deviceList.getSelectionModel().select(selected);
            }
        }));
        startDiscovery(discovery, i18n, statusLabel);
        rescanButton.setOnAction(e -> {
            discovery.stop();
            deviceList.getItems().clear();
            startDiscovery(discovery, i18n, statusLabel);
        });

        Stage stage = new Stage();

        // "Verbinden": USB-Port gewaehlt -> seriell; Netzgeraet gewaehlt ->
        // dessen Adresse (Pairing-Stand aus dem Speicher); sonst die
        // eingetippte Adresse (entspricht sie einem gefundenen Geraet, gilt
        // dessen Pairing-Stand).
        connectButton.setOnAction(e -> {
            PortRow usb = usbList.getSelectionModel().getSelectedItem();
            if (usb != null) {
                if (manager.findByHostPort(usb.systemName) != null) {
                    statusLabel.setText(i18n.t("bidib.alreadyConnected", usb.systemName));
                    return;
                }
                BidibSerialSupport.connect(usb.systemName, i18n, statusLabel, connectButton, manager);
                return;
            }
            BidibDiscoveredDevice chosen = deviceList.getSelectionModel().getSelectedItem();
            if (chosen != null) {
                String ip = chosen.getAddress() != null ? chosen.getAddress().getHostAddress() : "";
                connectTo(stage, ip, String.valueOf(chosen.getPort()), isKnownPaired(chosen.getUid()),
                        userName, i18n, statusLabel, connectButton, settings, manager);
                return;
            }
            String ip = ipField.getText() == null ? "" : ipField.getText().trim();
            if (ip.isEmpty()) {
                statusLabel.setText(i18n.t("bidib.enterAddress"));
                return;
            }
            String port = portField.getText() == null || portField.getText().isBlank()
                    ? DEFAULT_PORT : portField.getText().trim();
            fillingFields[0] = true;
            portField.setText(port);
            fillingFields[0] = false;
            boolean knownPaired = false;
            for (BidibDiscoveredDevice device : deviceList.getItems()) {
                if (hostPortOf(device).equals(ip + ":" + port)) {
                    knownPaired = isKnownPaired(device.getUid());
                    break;
                }
            }
            connectTo(stage, ip, port, knownPaired, userName, i18n, statusLabel, connectButton, settings, manager);
        });

        // Der Knopf unter der manuellen Adresse und der Doppelklick auf das
        // IP-Feld loesen denselben Ablauf aus wie der Haupt-Knopf - dafuer
        // werden vorher die Netz-/USB-Auswahl aufgehoben, damit garantiert
        // die eingetippte Adresse verwendet wird (sonst haette eine noch
        // bestehende Auswahl in einer der beiden Listen Vorrang, siehe
        // connectButton.setOnAction oben).
        Runnable connectManual = () -> {
            deviceList.getSelectionModel().clearSelection();
            usbList.getSelectionModel().clearSelection();
            connectButton.fire();
        };
        manualConnectButton.setOnAction(e -> connectManual.run());
        manualConnectButton.disableProperty().bind(connectButton.disableProperty());
        ipField.setOnMouseClicked(ev -> {
            if (ev.getClickCount() == 2 && ipField.getText() != null && !ipField.getText().isBlank()) {
                connectManual.run();
            }
        });

        localUidButton.setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION, i18n.t("bidib.localUidLabel",
                    ByteUtils.formatHexUniqueId(ensureLocalUid(settings))));
            alert.initOwner(stage);
            alert.setHeaderText(null);
            alert.setTitle(i18n.t("bidib.dialogTitle"));
            alert.showAndWait();
        });

        disconnectButton.setOnAction(e -> {
            BidibConnection connection = connectionList.getSelectionModel().getSelectedItem();
            if (connection != null) {
                disconnect(connection, statusLabel, i18n, manager);
            }
        });

        // Doppelklick auf ein Geraet (Netz oder USB) = "Verbinden".
        deviceList.setRowFactory(tv -> {
            javafx.scene.control.TableRow<BidibDiscoveredDevice> row = new javafx.scene.control.TableRow<>();
            row.setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2 && !row.isEmpty()) {
                    deviceList.getSelectionModel().select(row.getItem());
                    connectButton.fire();
                }
            });
            return row;
        });
        usbList.setOnMouseClicked(ev -> {
            if (ev.getClickCount() == 2 && usbList.getSelectionModel().getSelectedItem() != null) {
                connectButton.fire();
            }
        });
        // Doppelklick auf einen mc2-FTP-Fund = "Verbinden" (wie bei den
        // Netz-Funden oben) - siehe Kommentar bei mc2List weiter oben.
        mc2List.setRowFactory(tv -> {
            javafx.scene.control.TableRow<Mc2DiscoveredDevice> row = new javafx.scene.control.TableRow<>();
            row.setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2 && !row.isEmpty()) {
                    mc2List.getSelectionModel().select(row.getItem());
                    connectButton.fire();
                }
            });
            return row;
        });

        // mc2-per-FTP-Suche: startet zusammen mit der mDNS-Suche, unabhaengig
        // von ihr (siehe Mc2Discovery-Klassenkommentar, warum das noetig
        // ist). "Erneut suchen" bricht einen laufenden Lauf ab und beginnt
        // neu - derselbe Ablauf wie beim mDNS-"rescanButton" oben.
        Mc2Discovery mc2Discovery = new Mc2Discovery(
                device -> Platform.runLater(() -> mc2List.getItems().add(device)),
                () -> { });
        mc2Discovery.start();
        mc2RescanButton.setOnAction(e -> {
            mc2Discovery.stop();
            mc2List.getItems().clear();
            mc2Discovery.start();
        });

        // "Alles trennen": saemtliche Verbindungen (netBiDiB und USB) beenden,
        // das Fenster bleibt offen. closeAll() leert die beobachtbare Liste
        // und gehoert deshalb auf den JavaFX-Thread.
        disconnectAllButton.setOnAction(e -> {
            manager.closeAll();
            statusLabel.setText(i18n.t("bidib.statusClosed"));
        });

        pairingStoreButton.setOnAction(e -> BidibPairingStoreDialog.show(stage));

        // "Weiter zur Bearbeitung" rechts, von den Arbeitsknoepfen links
        // durch einen wachsenden Zwischenraum getrennt - schliesst das
        // Fenster; die Verbindungen bleiben bestehen. Die Knopfzeile ist das
        // Ende des Fensters; die Statuszeile (Fehler, Pairing-Hinweise)
        // steht darueber, zwischen Verbindungstabelle und Knoepfen.
        javafx.scene.layout.Region buttonSpacer = new javafx.scene.layout.Region();
        HBox.setHgrow(buttonSpacer, Priority.ALWAYS);
        Button continueButton = new Button(i18n.t("bidib.continueButton"));
        continueButton.setCancelButton(true);
        HBox buttonRow = new HBox(8, connectButton, disconnectButton, disconnectAllButton,
                pairingStoreButton, localUidButton, buttonSpacer, continueButton);
        buttonRow.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(10,
                hintLabel, discoveryRow, deviceList,
                new Separator(),
                manualLabel, manualHint, addressRow, manualConnectRow,
                mc2Row, mc2List,
                new Separator(),
                usbRow, usbHint, usbList,
                new Separator(),
                openLabel, connectionList,
                statusLabel, buttonRow);
        content.setPadding(new Insets(12));

        BorderPane root = new BorderPane();
        root.setCenter(content);

        stage.initOwner(owner);
        // Bewusst kein modales Fenster: die Suche soll im Hintergrund weiterlaufen
        // koennen, waehrend im Hauptfenster weitergearbeitet wird.
        stage.initModality(Modality.NONE);
        stage.setTitle(i18n.t("bidib.dialogTitle"));
        stage.getIcons().addAll(loadAppIcons());
        continueButton.setOnAction(e -> stage.close());
        // VOR WindowState.apply (das haengt seinen eigenen OnHidden-Handler
        // an diesen an). Nur die Geraetesuche beenden - die offenen
        // VERBINDUNGEN bleiben bestehen; den Listener am Manager abmelden.
        stage.setOnHidden(e -> {
            discovery.stop();
            mc2Discovery.stop();
            manager.getConnections().removeListener(refreshOnChange);
            if (open == stage) {
                open = null;
            }
        });

        Scene scene = new Scene(root);
        ThemeManager.apply(scene, settings.getTheme());
        stage.setScene(scene);
        // Breit genug, dass die komplette Knopfzeile immer sichtbar ist.
        stage.setMinWidth(900);
        stage.setMinHeight(760);
        WindowState.apply(stage, "bidibConnection", 940, 860);

        open = stage;
        stage.show();
        usbRescan.run();
    }

    // ------------------------------------------------------------------
    // Spalte "Verbunden" und USB-Liste
    // ------------------------------------------------------------------

    /**
     * Spalte "Verbunden" als Ankreuzkasten (3D, mit oder ohne Haken - in
     * heller wie dunkler Ansicht gut sichtbar), nur so breit wie ihre
     * Ueberschrift, reine Anzeige: der Kasten reagiert nicht auf Klicks.
     */
    private static <S> TableColumn<S, Boolean> connectedColumn(I18n i18n, BidibConnectionManager manager,
            java.util.function.Function<S, String> hostPortOf) {
        TableColumn<S, Boolean> column = new TableColumn<>(i18n.t("bidib.connectedColumn"));
        column.setCellValueFactory(cell -> new ReadOnlyBooleanWrapper(
                manager.findByHostPort(hostPortOf.apply(cell.getValue())) != null));
        column.setCellFactory(col -> {
            TableCell<S, Boolean> cell = new TableCell<>() {
                private final CheckBox box = new CheckBox();
                {
                    box.setMouseTransparent(true);
                    box.setFocusTraversable(false);
                }

                @Override
                protected void updateItem(Boolean connected, boolean empty) {
                    super.updateItem(connected, empty);
                    if (empty) {
                        setGraphic(null);
                        return;
                    }
                    box.setSelected(Boolean.TRUE.equals(connected));
                    setGraphic(box);
                }
            };
            cell.setAlignment(Pos.CENTER);
            return cell;
        });
        column.setPrefWidth(95);
        column.setMinWidth(95);
        column.setMaxWidth(95);
        column.setResizable(false);
        column.setReorderable(false);
        column.setSortable(false);
        return column;
    }

    /** Eine Zeile der USB-Liste: ein COM-Port und das Ergebnis seiner Pruefung. */
    private static final class PortRow {
        private final String systemName;
        private final String description;
        private final String vendor;
        private final int vendorId;
        private final javafx.beans.property.StringProperty bidib =
                new javafx.beans.property.SimpleStringProperty("");

        PortRow(String systemName, String description, String vendor, int vendorId) {
            this.systemName = systemName;
            this.description = description;
            this.vendor = vendor;
            this.vendorId = vendorId;
        }

        boolean isFtdi() {
            return vendorId == FTDI_VENDOR_ID;
        }

        boolean isBidib() {
            return bidib.get() != null && bidib.get().startsWith("✔");
        }
    }

    private static void styleUsbRow(javafx.scene.control.TableRow<PortRow> row) {
        PortRow item = row.getItem();
        if (item != null && item.isBidib()) {
            row.setStyle("-fx-font-weight: bold; -fx-background-color: rgba(46,158,79,0.28);");
        } else {
            row.setStyle("");
        }
    }

    /**
     * COM-Ports auflisten (im Hintergrund - die Treiber-Abfrage kann dauern)
     * und danach FTDI-Ports automatisch pruefen.
     */
    private static void rescanUsbPorts(TableView<PortRow> usbList, Button rescanButton, Button probeButton,
            Label statusLabel, I18n i18n, BidibConnectionManager manager) {
        rescanButton.setDisable(true);
        Thread worker = new Thread(() -> {
            java.util.List<PortRow> rows = new java.util.ArrayList<>();
            try {
                for (com.fazecast.jSerialComm.SerialPort port : com.fazecast.jSerialComm.SerialPort.getCommPorts()) {
                    rows.add(new PortRow(port.getSystemPortName(), port.getDescriptivePortName(),
                            describeVendor(port), port.getVendorID()));
                }
            } catch (Throwable ex) {
                // Keine Ports ermittelbar - leere Liste.
            }
            Platform.runLater(() -> {
                usbList.getItems().setAll(rows);
                rescanButton.setDisable(false);
                java.util.List<PortRow> ftdi = new java.util.ArrayList<>();
                for (PortRow row : rows) {
                    if (row.isFtdi()) {
                        ftdi.add(row);
                    }
                }
                if (!ftdi.isEmpty()) {
                    probeUsbPorts(ftdi, usbList, statusLabel, probeButton, i18n, manager);
                }
            });
        }, "bidib-usb-ports");
        worker.setDaemon(true);
        worker.start();
    }

    private static String describeVendor(com.fazecast.jSerialComm.SerialPort port) {
        String manufacturer = null;
        try {
            manufacturer = port.getManufacturer();
        } catch (Throwable ignored) {
            // Aeltere Treiber liefern keinen Hersteller.
        }
        int vid = port.getVendorID();
        int pid = port.getProductID();
        String ids = vid > 0 ? String.format("%04X:%04X", vid, pid) : "";
        if (manufacturer != null && !manufacturer.isBlank()) {
            return ids.isEmpty() ? manufacturer : manufacturer + " (" + ids + ")";
        }
        return ids;
    }

    /**
     * Pruefroutine: die Ports nacheinander im Hintergrund oeffnen (siehe
     * {@link BidibSerialSupport#probe}); bereits verbundene ueberspringen.
     * Das erste gefundene Geraet wird in die Sicht gerollt.
     */
    private static void probeUsbPorts(java.util.List<PortRow> rows, TableView<PortRow> usbList, Label statusLabel,
            Button probeButton, I18n i18n, BidibConnectionManager manager) {
        probeButton.setDisable(true);
        Thread worker = new Thread(() -> {
            for (PortRow row : rows) {
                if (manager.findByHostPort(row.systemName) != null) {
                    Platform.runLater(() -> row.bidib.set("✔ " + i18n.t("bidib.usbConnectedAlready")));
                    continue;
                }
                Platform.runLater(() -> {
                    row.bidib.set(i18n.t("bidib.usbProbing"));
                    statusLabel.setText(i18n.t("bidib.usbProbingPort", row.systemName));
                });
                BidibSerialSupport.ProbeResult result = BidibSerialSupport.probe(row.systemName);
                Platform.runLater(() -> {
                    if (result.bidib()) {
                        String text = result.productName() != null && !result.productName().isBlank()
                                ? result.productName() : "BiDiB";
                        if (result.uniqueId() != null) {
                            text += " (" + result.uniqueId() + ")";
                        }
                        row.bidib.set("✔ " + text);
                    } else {
                        row.bidib.set("✘ " + i18n.t("bidib.usbNoBidib"));
                    }
                });
            }
            Platform.runLater(() -> {
                statusLabel.setText(i18n.t("bidib.usbProbeDone"));
                probeButton.setDisable(false);
                for (int i = 0; i < usbList.getItems().size(); i++) {
                    if (usbList.getItems().get(i).isBidib()) {
                        usbList.scrollTo(i);
                        break;
                    }
                }
            });
        }, "bidib-usb-probe");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Eine Verbindung trennen - "Trennen"-Knopf und Ankreuzkasten in der
     * Tabelle der bestehenden Verbindungen. Blockierend (die Bibliothek
     * meldet sich noch ab), deshalb im Hintergrund; entfernt wird der
     * Eintrag danach auf dem JavaFX-Thread.
     */
    private static void disconnect(BidibConnection connection, Label statusLabel, I18n i18n,
            BidibConnectionManager manager) {
        statusLabel.setText(i18n.t("bidib.statusDisconnecting", connection.getName()));
        Thread worker = new Thread(() -> {
            connection.close();
            Platform.runLater(() -> {
                manager.remove(connection);
                statusLabel.setText("");
            });
        }, "bidib-disconnect");
        worker.setDaemon(true);
        worker.start();
    }

    /** "Adresse:Port" eines gefundenen Geraets - dasselbe Format wie {@link BidibConnection#getHostPort()}. */
    private static String hostPortOf(BidibDiscoveredDevice device) {
        String host = device.getAddress() != null ? device.getAddress().getHostAddress() : "";
        return host + ":" + device.getPort();
    }

    /**
     * Gemeinsamer Weg fuer "Verbinden" (gewaehltes Geraet) und "Manuell
     * verbinden" (eingetippte Adresse): Doppelverbindung abweisen, Adresse
     * merken, Verbindungsaufbau starten. {@code button} ist der gedrueckte
     * Knopf - er wird waehrend des Aufbaus gesperrt.
     */
    private static void connectTo(Stage stage, String ip, String port, boolean knownPaired, String userName,
            I18n i18n, Label statusLabel, Button button, AppSettings settings, BidibConnectionManager manager) {
        String target = ip + ":" + port;
        if (manager.findByHostPort(target) != null) {
            statusLabel.setText(i18n.t("bidib.alreadyConnected", target));
            return;
        }
        settings.setBidibLastIp(ip);
        settings.setBidibLastPort(port);
        startConnect(stage, target, userName, i18n, statusLabel, button, settings, knownPaired);
    }

    private static void startDiscovery(BidibDiscovery discovery, I18n i18n, Label statusLabel) {
        try {
            discovery.start();
        } catch (IOException ex) {
            statusLabel.setText(i18n.t("bidib.discoveryFailed", ex.getMessage()));
        }
    }

    /**
     * Steht fuer diese Unique-ID bereits ein bestaetigtes Pairing im
     * Speicher? Grundlage fuer die Spalte "Pairing" in der Fundliste und
     * dafuer, ob beim Verbinden noch ein Pairing-Fenster erscheint.
     * <p>
     * Verglichen wird nachsichtig (nur Hex-Zeichen, Kleinschreibung, notfalls
     * ueber das Ende der Kennung): Geraet und Speicher schreiben dieselbe ID
     * nicht immer in derselben Form - mit oder ohne Trennzeichen, mit oder
     * ohne fuehrende Nullen.
     */
    static boolean isKnownPaired(String uid) {
        String wanted = normalizeUid(uid);
        if (wanted.isEmpty()) {
            return false;
        }
        try {
            LocalPairingStore store = new LocalPairingStore(pairingStoreFile());
            store.load();
            for (PairingStoreEntry entry : store.getPairingStoreEntries()) {
                if (!entry.isPaired()) {
                    continue;
                }
                String stored = normalizeUid(entry.getUid());
                if (stored.isEmpty()) {
                    continue;
                }
                if (stored.equals(wanted) || stored.endsWith(wanted) || wanted.endsWith(stored)) {
                    return true;
                }
            }
        } catch (Exception ex) {
            // Kein lesbarer Speicher: dann gilt das Geraet als noch nicht
            // gepaart - der gewohnte Pairing-Ablauf greift.
            return false;
        }
        return false;
    }

    private static String normalizeUid(String uid) {
        if (uid == null) {
            return "";
        }
        return uid.toLowerCase(java.util.Locale.ROOT).replaceAll("[^0-9a-f]", "");
    }

    /**
     * Startet einen Verbindungsversuch im Hintergrund. Ausgelagert (statt
     * direkt im Knopf-Handler), weil der Pairing-Dialog bei "Erneut versuchen"
     * genau hierher zurückspringt - mit derselben Adresse.
     */
    private static void startConnect(Stage owner, String target, String userName, I18n i18n,
            Label statusLabel, Button connectButton, AppSettings settings, boolean knownPaired) {
        connectButton.setDisable(true);
        statusLabel.setText(i18n.t("bidib.statusConnecting"));

        Thread worker = new Thread(
                () -> connectInBackground(owner, target, userName, i18n, statusLabel, connectButton,
                        settings, knownPaired),
                "bidib-connect");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Baut Kontext, eigene Verbindungsdaten und Pairing-Speicher auf und
     * öffnet die netBiDiB-Verbindung. Läuft in einem eigenen Thread, weil
     * {@code BidibInterface.open(...)} bis zum Abschluss des ersten
     * Handshakes blockiert (bis zu einigen Sekunden).
     */
    private static void connectInBackground(Stage owner, String hostPort, String userName, I18n i18n,
            Label statusLabel, Button connectButton, AppSettings settings, boolean knownPaired) {

        BidibConnectionManager manager = BidibConnectionManager.getInstance();

        long localUid = ensureLocalUid(settings);
        NetBidibLinkData clientLinkData = buildClientLinkData(localUid, userName);

        Context context = new DefaultContext();
        LocalPairingStore pairingStore;
        try {
            pairingStore = new LocalPairingStore(pairingStoreFile());
            pairingStore.load();
        } catch (Exception ex) {
            Platform.runLater(() -> {
                statusLabel.setText(i18n.t("bidib.statusError", ex.getMessage()));
                connectButton.setDisable(false);
            });
            return;
        }
        context.register(Context.PAIRING_STORE, pairingStore);
        context.register(Context.NET_BIDIB_CLIENT_LINK_DATA, clientLinkData);

        BidibInterface bidib = NetBidibClient.createInstance(context);

        // Muss schon jetzt (vor bidib.open(...)) erzeugt werden, siehe
        // Klassenkommentar in BidibNodeModel - eine nachträgliche Anmeldung
        // wie beim RawMessageListener gibt es dafür nicht.
        BidibNodeModel nodeModel = new BidibNodeModel();
        BidibConnection connection = new BidibConnection(hostPort, bidib, nodeModel);

        // jbidibc ruft ConnectionListener.opened(...) ZWEIMAL auf: einmal direkt
        // nach dem anfänglichen Handshake (NetBidibClient.open(), noch bevor
        // überhaupt die Unique-ID des Gegenparts bekannt ist) und ein zweites
        // Mal, nachdem die eigentliche BiDiB-Verbindung inklusive Logon und
        // "Magic"-Antwort steht (AbstractBidib, "Signal the connection was
        // opened"). Ohne dieses Merkmal überschreibt der zweite, spätere Aufruf
        // die bereits gesetzte "gepaart"-Meldung wieder mit "wartet auf
        // Pairing-Status" - obwohl das Pairing laut Protokoll-Log längst
        // erfolgreich war. Deshalb merken, ob pairingFinished() schon lief.
        AtomicBoolean pairingAlreadyFinished = new AtomicBoolean(false);

        // Ob die anfängliche Knotentabelle schon abgefragt wurde (siehe unten,
        // zweiter opened()-Aufruf) - verhindert einen doppelten Abruf, falls
        // opened() aus irgendeinem Grund mehr als zweimal kommen sollte.
        AtomicBoolean nodeTableRequested = new AtomicBoolean(false);

        // Der Pairing-Dialog entsteht erst, wenn die Gegenseite tatsächlich ein
        // Pairing verlangt - bei bereits bekannten Geräten passiert das nie.
        AtomicReference<BidibPairingDialog> pairingDialog = new AtomicReference<>();

        ConnectionListener listener = new ConnectionListener() {

            @Override
            public void opened(String port) {
                if (pairingAlreadyFinished.get()) {
                    // Das ist der ZWEITE Aufruf - hier, und nicht direkt nach
                    // bidib.open() (siehe unten), ist die Verbindung wirklich
                    // fertig aufgebaut: sysDisable + Magic-Austausch
                    // ("Contact the interface") sind an dieser Stelle laut
                    // jbidibc-Quelltext bereits abgeschlossen. Ein Test hat
                    // gezeigt: fragt man die Knotentabelle (MSG_NODETAB_GETALL)
                    // schon direkt nach bidib.open() ab, kommt vom Gerät ein
                    // MSG_SYS_ERROR (Fehlercode 4) zurück und die Abfrage läuft
                    // in einen Timeout - schlicht zu früh gefragt.
                    if (nodeTableRequested.compareAndSet(false, true)) {
                        Thread nodeTableWorker =
                                new Thread(() -> nodeModel.readInitialNodeTable(bidib), "bidib-node-table");
                        nodeTableWorker.setDaemon(true);
                        nodeTableWorker.start();
                        // Erst jetzt darf der Name abgefragt werden - vorher
                        // weist die Gegenseite Anfragen zurück.
                        connection.resolveNameInBackground();
                    }
                    return;
                }
                Platform.runLater(() -> statusLabel.setText(i18n.t("bidib.statusOpened")));
            }

            @Override
            public void closed(String port) {
                Platform.runLater(() -> {
                    // Nicht sofort aus der Liste nehmen: Der rote Kreis in der
                    // Statusleiste soll den Abbruch sichtbar machen. Entfernt
                    // wird der Eintrag erst durch "Trennen" im Dialog.
                    connection.setConnected(false);
                    statusLabel.setText(i18n.t("bidib.statusClosed"));
                    connectButton.setDisable(false);
                });
            }

            @Override
            public void status(String messageKey, Context ctx) {
                // Reine Diagnosemeldungen der Bibliothek - keine eigene Anzeige.
            }

            @Override
            public void actionRequired(String messageKey, Context ctx) {
                if (NetBidibContextKeys.KEY_ACTION_PAIRING_STATE.equals(messageKey)
                        && ctx.get(NetBidibContextKeys.KEY_PAIRING_STATE) == PairingStateEnum.Unpaired) {
                    // Eigene Pairing-Anfrage senden. Die Bestätigung muss zusätzlich
                    // am Gegenstück erfolgen (siehe bidib.org, Abschnitt "Pairing") -
                    // ohne das bleibt die Verbindung nach Ablauf des Zeitlimits ungepaart.
                    bidib.signalUserAction(NetBidibContextKeys.KEY_PAIRING_REQUEST, ctx);
                    Platform.runLater(() -> {
                        if (pairingDialog.get() != null && pairingDialog.get().isShowing()) {
                            return;
                        }
                        if (knownPaired) {
                            // Der eigene Speicher kennt das Geraet als gepaart,
                            // das Geraet meldet sich aber trotzdem als ungepaart
                            // (BIDIB_LINK_STATUS_UNPAIRED). Massgeblich ist das
                            // Geraet: Das Fenster muss erscheinen, sonst wartet
                            // es vergeblich auf die Bestaetigung. Der Hinweis
                            // sagt, warum trotz Speichereintrag gefragt wird.
                            statusLabel.setText(i18n.t("bidib.statusPairingFromStore"));
                        }
                        BidibPairingDialog dialog = new BidibPairingDialog(owner, hostPort,
                                PAIRING_TIMEOUT_SECONDS,
                                // Abbruch: Verbindung wieder abbauen.
                                () -> {
                                    Thread t = new Thread(connection::close, "bidib-pairing-cancel");
                                    t.setDaemon(true);
                                    t.start();
                                    Platform.runLater(() -> {
                                        manager.remove(connection);
                                        statusLabel.setText(i18n.t("bidib.statusPairingCancelled"));
                                        connectButton.setDisable(false);
                                    });
                                },
                                // Erneut versuchen: alte Verbindung abbauen und
                                // denselben Verbindungsaufbau noch einmal starten.
                                () -> {
                                    Thread t = new Thread(() -> {
                                        connection.close();
                                        Platform.runLater(() -> {
                                            manager.remove(connection);
                                            startConnect(owner, hostPort, userName, i18n, statusLabel,
                                                    connectButton, settings, knownPaired);
                                        });
                                    }, "bidib-pairing-retry");
                                    t.setDaemon(true);
                                    t.start();
                                });
                        pairingDialog.set(dialog);
                        if (knownPaired) {
                            // Der Hinweis steht sonst nur in der Statuszeile
                            // HINTER dem modalen Pairing-Fenster - hier sieht
                            // ihn der Nutzer, waehrend er zum Geraet geht.
                            dialog.setExtraHint(i18n.t("bidib.statusPairingFromStore"));
                        }
                        dialog.show();
                    });
                } else if (NetBidibContextKeys.KEY_ACTION_PAIRING_REQUESTED.equals(messageKey)) {
                    // Der Gegenpart hat (zusätzlich zu unserer eigenen, bereits
                    // gesendeten Anfrage) selbst eine Pairing-Anfrage gestellt -
                    // passiert laut jbidibc-Quelltext (MyRequestPairingState/
                    // TheirRequestPairingState), wenn beide Seiten das Pairing
                    // gleichzeitig anstoßen. Ohne eine Antwort auf GENAU DIESE
                    // Anfrage (über den eigenen Aktions-Schlüssel KEY_PAIRING_STATUS,
                    // nicht KEY_PAIRING_REQUEST) bleibt die Verbindung auf "wartet auf
                    // Pairing-Status" stehen, obwohl am Gerät selbst schon bestätigt
                    // wurde. Automatisch annehmen (siehe Klassenkommentar oben).
                    Long remoteUid = ctx.get(NetBidibContextKeys.KEY_DESCRIPTOR_UID, Long.class, null);
                    Context response = new DefaultContext();
                    response.register(Context.PAIRING_STATUS, PairingStatus.PAIRED);
                    if (remoteUid != null) {
                        response.register(Context.UNIQUE_ID, remoteUid);
                    }
                    bidib.signalUserAction(NetBidibContextKeys.KEY_PAIRING_STATUS, response);
                }
            }

            @Override
            public void pairingFinished(PairingResult result, long remoteUid) {
                pairingAlreadyFinished.set(true);
                BidibPairingDialog dialog = pairingDialog.get();
                if (result == PairingResult.PAIRED) {
                    if (dialog != null) {
                        dialog.succeeded();
                    }
                    Platform.runLater(() -> statusLabel.setText(i18n.t("bidib.statusPaired")));
                    // Nach dem Pairing muss das Geraet von sich aus MSG_LOCAL_LOGON
                    // schicken; erst dann (zweiter opened()-Aufruf) antwortet es
                    // auf Knoten-Abfragen. Bleibt das aus - so gesehen am mc2
                    // (192.168.0.90) am 14.09.: PAIRED um 14:27:52, danach
                    // nichts mehr, "Auslesen" lief in einen Timeout -, ist das
                    // Geraet in aller Regel schon bei einem anderen Programm
                    // angemeldet (iTrain, Wizard): ein netBiDiB-Knoten meldet
                    // sich nur bei EINEM Host an. Nach 6 s deutlich sagen,
                    // statt den Nutzer in den Timeout laufen zu lassen.
                    Thread logonWatch = new Thread(() -> {
                        try {
                            Thread.sleep(6000);
                        } catch (InterruptedException ex) {
                            return;
                        }
                        if (!nodeTableRequested.get() && connection.isConnected()) {
                            LOGGER.warn("Kein MSG_LOCAL_LOGON von {} innerhalb von 6 s nach dem Pairing.", hostPort);
                            Platform.runLater(() -> statusLabel.setText(i18n.t("bidib.statusNoLogon")));
                        }
                    }, "bidib-logon-watch");
                    logonWatch.setDaemon(true);
                    logonWatch.start();
                } else {
                    // Der Dialog bleibt bewusst stehen, bis der Nutzer sich
                    // entscheidet (Abbruch oder erneut versuchen).
                    if (dialog != null) {
                        dialog.failed(i18n.t("bidib.pairingIncomplete"));
                    }
                    Platform.runLater(() -> statusLabel.setText(i18n.t("bidib.statusUnpaired")));
                }
            }

            @Override
            public void handleError(RuntimeException ex) {
                Platform.runLater(() -> statusLabel.setText(i18n.t("bidib.statusError", ex.getMessage())));
            }
        };

        try {
            bidib.setResponseTimeout(1600);
            // Wichtig: bidib.open(...) kehrt schon nach dem ERSTEN, anfänglichen
            // Handshake zurück (siehe Klassenkommentar zum doppelten
            // opened()-Aufruf) - NICHT erst nach Logon/Magic-Austausch, wie
            // frühere Kommentare hier fälschlich angenommen hatten. Die
            // Knotentabelle darf deshalb NICHT direkt hier abgefragt werden
            // (siehe oben, zweiter opened()-Aufruf für den richtigen Zeitpunkt).
            bidib.open(hostPort, listener, Collections.singleton(nodeModel.createListener()), null, null, context);
            Platform.runLater(() -> {
                manager.add(connection);
                connectButton.setDisable(false);
            });
        } catch (Exception ex) {
            Platform.runLater(() -> {
                statusLabel.setText(describeConnectError(ex, i18n));
                connectButton.setDisable(false);
            });
        }
    }

    /**
     * Fehlertext beim Verbindungsaufbau. "Connection refused" bekommt einen
     * eigenen Hinweis: Der Rechner ist erreichbar und lehnt ab - bei netBiDiB
     * fast immer, weil bereits ein anderes Programm mit dem Geraet verbunden
     * ist (es laesst nur eine Verbindung zu) oder der Server dort aus ist.
     */
    private static String describeConnectError(Exception ex, I18n i18n) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        if (ex instanceof java.net.ConnectException
                || message.toLowerCase(java.util.Locale.ROOT).contains("connection refused")) {
            return i18n.t("bidib.statusRefused");
        }
        return i18n.t("bidib.statusError", message);
    }

    /**
     * Eigene, dauerhaft gemerkte netBiDiB-Unique-ID (siehe
     * {@link AppSettings#getBidibLocalUid()}). Ohne eine bei bidib.org
     * registrierte Produkt-ID (siehe bidib.org/support/product_id_e.html)
     * wird nach deren eigener Empfehlung im Bereich für Open-Source-
     * Komponenten (Vendor-ID 13) eine zufällige Kennung erzeugt und dann
     * unverändert weiterverwendet - ein Wechsel bei jedem Programmstart würde
     * bei jeder Verbindung ein erneutes Pairing verlangen.
     */
    private static long ensureLocalUid(AppSettings settings) {
        String existing = settings.getBidibLocalUid();
        if (existing != null) {
            Long parsed = ByteUtils.parseHexUniqueId(existing);
            if (parsed != null) {
                return parsed;
            }
        }
        byte[] uid = new byte[7];
        uid[0] = 0x00; // Klassenbits: keine gesetzt (Vorgabe fuer Host-Programme)
        uid[1] = 0x00; // ClassID-Erweiterung, reserviert
        uid[2] = 0x0D; // Vendor-ID 13: Bereich fuer Open-Source-/Selbstbau-Komponenten
        byte[] random = new byte[4];
        new SecureRandom().nextBytes(random);
        System.arraycopy(random, 0, uid, 3, 4);

        long value = ByteUtils.convertUniqueIdToLong(uid);
        settings.setBidibLocalUid(ByteUtils.formatHexUniqueId(value));
        return value;
    }

    private static NetBidibLinkData buildClientLinkData(long uid, String userName) {
        NetBidibLinkData data = new NetBidibLinkData(PartnerType.LOCAL);
        // Muss mit "BiDiB" beginnen (siehe MSG_LOCAL_PROTOCOL_SIGNATURE, bidib.org).
        data.setRequestorName("BiDiB-iTrain-ImportExport");
        data.setUniqueId(uid);
        data.setProdString("iTrain Import/Export");
        data.setUserString(userName);
        data.setProtocolVersion(ProtocolVersion.VERSION_0_8);
        data.setNetBidibRole(NetBidibRole.INTERFACE);
        data.setRequestedPairingTimeout(PAIRING_TIMEOUT_SECONDS);
        return data;
    }

    private static String defaultUserName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            return "iTrain-Import-Export";
        }
    }

    private static Image[] loadAppIcons() {
        try (InputStream in = BidibConnectionDialog.class.getResourceAsStream("app-icon.png")) {
            return in == null ? new Image[0] : new Image[]{new Image(in)};
        } catch (Exception ex) {
            return new Image[0];
        }
    }
}
