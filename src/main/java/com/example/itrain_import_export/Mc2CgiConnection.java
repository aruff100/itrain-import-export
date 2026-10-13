package com.example.itrain_import_export;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.stage.Window;

import java.util.List;
import java.util.prefs.Preferences;

/**
 * Verbindung zur Tams mc2 ueber die cgi-Schnittstelle der Weboberflaeche
 * (seit 13.10.2026, Menue Systeme / mc2 / "Verbindung ueber CGI").
 * Unabhaengig von netBiDiB: es gibt keine dauerhafte Verbindung, gemerkt
 * wird nur der Host, nachdem eine Probeabfrage ({@link Mc2Http#locoAddresses})
 * geantwortet hat. Solange ein Host gesetzt ist, zeigt das Systeme-Fenster
 * die beiden mc2-Knoepfe.
 */
public final class Mc2CgiConnection {

    private static final Mc2CgiConnection INSTANCE = new Mc2CgiConnection();
    private static final String PREF_LAST_HOST = "mc2CgiLastHost";
    private static final String DEFAULT_HOST = "192.168.0.90";

    private final ReadOnlyStringWrapper host = new ReadOnlyStringWrapper();
    private final Preferences prefs = Preferences.userNodeForPackage(Mc2CgiConnection.class);

    private Mc2CgiConnection() {
    }

    public static Mc2CgiConnection getInstance() {
        return INSTANCE;
    }

    /** Host der verbundenen mc2, {@code null} = nicht verbunden. */
    public ReadOnlyStringProperty hostProperty() {
        return host.getReadOnlyProperty();
    }

    public String getHost() {
        return host.get();
    }

    public boolean isConnected() {
        return host.get() != null;
    }

    public void disconnect() {
        host.set(null);
    }

    String lastHost() {
        return prefs.get(PREF_LAST_HOST, DEFAULT_HOST);
    }

    void connected(String newHost) {
        prefs.put(PREF_LAST_HOST, newHost);
        host.set(newHost);
    }

    /**
     * Verbindungsdialog: IP-Feld (Vorgabe letzter Host), "Suchen" (Abtastung
     * des Subnetzes, siehe {@link Mc2Discovery}), "Verbinden" prueft im
     * Hintergrund per cgi und schliesst erst bei Erfolg.
     */
    public static void showConnectDialog(Window owner) {
        I18n i18n = I18n.getInstance();
        Mc2CgiConnection connection = getInstance();

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(i18n.t("mc2.cgiTitle"));
        dialog.setHeaderText(null);
        ButtonType connectType = new ButtonType(i18n.t("mc2.cgiConnectButton"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType(i18n.t("mc2.cgiCancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(connectType, cancelType);

        TextField hostField = new TextField(connection.isConnected() ? connection.getHost() : connection.lastHost());
        hostField.setPrefColumnCount(16);
        Button searchButton = new Button(i18n.t("mc2.cgiSearch"));
        ListView<String> found = new ListView<>();
        found.setPrefHeight(90);
        found.getSelectionModel().selectedItemProperty().addListener((obs, old, value) -> {
            if (value != null) {
                hostField.setText(value);
            }
        });
        Label status = new Label();
        status.setWrapText(true);
        status.setMaxWidth(360);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(12));
        Label info = new Label(i18n.t("mc2.cgiInfo"));
        info.setWrapText(true);
        info.setMaxWidth(360);
        grid.add(info, 0, 0, 3, 1);
        grid.add(new Label(i18n.t("mc2.cgiHost")), 0, 1);
        grid.add(hostField, 1, 1);
        grid.add(searchButton, 2, 1);
        grid.add(found, 0, 2, 3, 1);
        grid.add(status, 0, 3, 3, 1);
        dialog.getDialogPane().setContent(grid);

        Mc2Discovery[] discovery = new Mc2Discovery[1];
        searchButton.setOnAction(e -> {
            if (discovery[0] != null) {
                discovery[0].stop();
            }
            found.getItems().clear();
            status.setTextFill(Color.BLACK);
            status.setText(i18n.t("mc2.cgiSearching"));
            searchButton.setDisable(true);
            discovery[0] = new Mc2Discovery(
                    device -> Platform.runLater(() -> {
                        if (!found.getItems().contains(device.getHost())) {
                            found.getItems().add(device.getHost());
                        }
                    }),
                    () -> Platform.runLater(() -> {
                        searchButton.setDisable(false);
                        status.setText(found.getItems().isEmpty() ? i18n.t("mc2.cgiNothingFound") : "");
                        if (found.getItems().size() == 1) {
                            found.getSelectionModel().select(0);
                        }
                    }));
            discovery[0].start();
        });

        Button connectButton = (Button) dialog.getDialogPane().lookupButton(connectType);
        connectButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume(); // erst nach erfolgreicher Pruefung schliessen
            String candidate = hostField.getText() == null ? "" : hostField.getText().trim();
            if (candidate.isEmpty()) {
                return;
            }
            connectButton.setDisable(true);
            status.setTextFill(Color.BLACK);
            status.setText(i18n.t("mc2.cgiChecking"));
            Thread worker = new Thread(() -> {
                try {
                    List<Integer> addresses = Mc2Http.locoAddresses(candidate);
                    Platform.runLater(() -> {
                        connection.connected(candidate);
                        dialog.setResult(connectType);
                        dialog.close();
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        connectButton.setDisable(false);
                        status.setTextFill(Color.web("#cc3333"));
                        status.setText(i18n.t("mc2.cgiFailed", candidate, String.valueOf(ex.getMessage())));
                    });
                }
            }, "mc2-cgi-connect");
            worker.setDaemon(true);
            worker.start();
        });
        dialog.setOnHidden(e -> {
            if (discovery[0] != null) {
                discovery[0].stop();
            }
        });
        dialog.showAndWait();
    }
}
