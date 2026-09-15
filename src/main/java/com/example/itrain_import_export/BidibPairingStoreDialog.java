package com.example.itrain_import_export;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import org.bidib.jbidibc.netbidib.pairingstore.LocalPairingStore;
import org.bidib.jbidibc.netbidib.pairingstore.PairingStoreEntry;

import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Zeigt den Inhalt der dauerhaften netBiDiB-Pairing-Datei (siehe
 * {@link BidibConnectionDialog#pairingStoreFile()}) an und erlaubt, einzelne
 * oder alle Einträge zu löschen - z.B. um ein Pairing mit einem Gerät
 * gezielt zurückzusetzen (danach fragt das Gerät beim nächsten Verbinden
 * wieder neu nach der Bestätigung), ohne die ganze Datei von Hand im
 * Dateisystem suchen zu müssen.
 * <p>
 * Arbeitet unabhängig von einer laufenden Verbindung direkt auf der Datei -
 * die {@code LocalPairingStore}-Instanz hier ist nur ein Werkzeug zum
 * Lesen/Schreiben derselben JSON-Datei, keine Referenz auf eine offene
 * Verbindung.
 */
public final class BidibPairingStoreDialog {

    private static final DateTimeFormatter LAST_SEEN_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private BidibPairingStoreDialog() {
    }

    public static void show(Stage owner) {
        I18n i18n = I18n.getInstance();
        AppSettings settings = AppSettings.getInstance();

        LocalPairingStore store = new LocalPairingStore(BidibConnectionDialog.pairingStoreFile());
        store.load();

        ObservableList<PairingStoreEntry> entries = FXCollections.observableArrayList(store.getPairingStoreEntries());

        TableView<PairingStoreEntry> table = new TableView<>(entries);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setPlaceholder(new Label(i18n.t("bidib.pairingStoreEmpty")));

        TableColumn<PairingStoreEntry, String> uidColumn = new TableColumn<>(i18n.t("bidib.pairingStoreUid"));
        uidColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getUid()));
        uidColumn.setPrefWidth(150);
        uidColumn.setReorderable(false);

        TableColumn<PairingStoreEntry, String> requestorColumn =
                new TableColumn<>(i18n.t("bidib.pairingStoreRequestor"));
        requestorColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getRequestorName()));
        requestorColumn.setPrefWidth(150);
        requestorColumn.setReorderable(false);

        TableColumn<PairingStoreEntry, String> productColumn =
                new TableColumn<>(i18n.t("bidib.pairingStoreProduct"));
        productColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getProductName()));
        productColumn.setPrefWidth(100);
        productColumn.setReorderable(false);

        TableColumn<PairingStoreEntry, String> userColumn = new TableColumn<>(i18n.t("bidib.pairingStoreUser"));
        userColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getUserName()));
        userColumn.setPrefWidth(100);
        userColumn.setReorderable(false);

        TableColumn<PairingStoreEntry, String> pairedColumn = new TableColumn<>(i18n.t("bidib.pairingStorePaired"));
        pairedColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().isPaired() ? i18n.t("bidib.pairingStoreYes") : i18n.t("bidib.pairingStoreNo")));
        pairedColumn.setPrefWidth(80);
        pairedColumn.setReorderable(false);

        TableColumn<PairingStoreEntry, String> lastSeenColumn =
                new TableColumn<>(i18n.t("bidib.pairingStoreLastSeen"));
        lastSeenColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().getLastSeen() != null ? cell.getValue().getLastSeen().format(LAST_SEEN_FORMAT) : ""));
        lastSeenColumn.setPrefWidth(140);
        lastSeenColumn.setReorderable(false);

        // Bewusst List.of(...) statt der Aufzählung als Einzelargumente: Die
        // varargs-Fassung von setAll(...) müsste dafür intern ein Feld des
        // Typs TableColumn<PairingStoreEntry,?>[] anlegen. Solche Felder mit
        // Typparametern verbietet Java, weshalb der Compiler die Warnung
        // "unchecked generic array creation" ausgibt. Die Collection-Fassung
        // braucht kein Feld und ist damit warnungsfrei.
        table.getColumns().setAll(List.of(
                uidColumn, requestorColumn, productColumn, userColumn, pairedColumn, lastSeenColumn));

        Label hintLabel = new Label(i18n.t("bidib.pairingStoreHint"));
        hintLabel.setWrapText(true);

        Button deleteSelectedButton = new Button(i18n.t("bidib.pairingStoreDeleteSelected"));
        deleteSelectedButton.setDisable(true);
        table.getSelectionModel().getSelectedItems()
                .addListener((javafx.collections.ListChangeListener<PairingStoreEntry>) change ->
                        deleteSelectedButton.setDisable(table.getSelectionModel().getSelectedItems().isEmpty()));

        Button deleteAllButton = new Button(i18n.t("bidib.pairingStoreDeleteAll"));
        Button closeButton = new Button(i18n.t("bidib.pairingStoreClose"));

        deleteSelectedButton.setOnAction(e -> {
            List<PairingStoreEntry> selected =
                    new ArrayList<>(table.getSelectionModel().getSelectedItems());
            if (selected.isEmpty()) {
                return;
            }
            if (!confirm(i18n, i18n.t("bidib.pairingStoreConfirmDeleteSelected", selected.size()))) {
                return;
            }
            entries.removeAll(selected);
            persist(store, entries);
        });

        deleteAllButton.setOnAction(e -> {
            if (entries.isEmpty()) {
                return;
            }
            if (!confirm(i18n, i18n.t("bidib.pairingStoreConfirmDeleteAll"))) {
                return;
            }
            entries.clear();
            persist(store, entries);
        });

        closeButton.setOnAction(e -> ((Stage) closeButton.getScene().getWindow()).close());

        HBox buttonRow = new HBox(8, deleteSelectedButton, deleteAllButton, closeButton);
        buttonRow.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(10, hintLabel, table, buttonRow);
        content.setPadding(new Insets(12));
        VBox.setVgrow(table, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setCenter(content);

        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.NONE);
        stage.setTitle(i18n.t("bidib.pairingStoreWindowTitle"));
        stage.getIcons().addAll(loadAppIcons());

        Scene scene = new Scene(root);
        ThemeManager.apply(scene, settings.getTheme());
        stage.setScene(scene);
        stage.setMinWidth(560);
        stage.setMinHeight(360);
        WindowState.apply(stage, "bidibPairingStore", 720, 440);
        stage.show();
    }

    private static void persist(LocalPairingStore store, List<PairingStoreEntry> remaining) {
        store.setPairings(remaining);
        store.store();
    }

    private static boolean confirm(I18n i18n, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setTitle(i18n.t("bidib.pairingStoreWindowTitle"));
        alert.setHeaderText(null);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.YES;
    }

    private static Image[] loadAppIcons() {
        try (InputStream in = BidibPairingStoreDialog.class.getResourceAsStream("app-icon.png")) {
            return in == null ? new Image[0] : new Image[]{new Image(in)};
        } catch (Exception ex) {
            return new Image[0];
        }
    }
}
