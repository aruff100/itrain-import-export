package com.example.itrain_import_export;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Fenster mit dem rohen RX/TX-Protokoll EINER BiDiB-Verbindung - jedes
 * gesendete und empfangene Paket als Hex-Dump mit Zeitstempel, vom ersten
 * Byte des Verbindungsaufbaus an. Die Mitschrift selbst führt
 * {@link BidibConnection} (Listener wird vor dem Öffnen der Verbindung
 * angemeldet); dieses Fenster zeigt sie nur an und hängt sich für neue
 * Zeilen an {@link BidibConnection#addRawLogListener}.
 * <p>
 * Bewusst ein einfaches {@link TextArea} statt einer Tabelle: aus einer
 * {@code TableView} lässt sich in JavaFX nichts markieren/kopieren, ein
 * Textfeld unterstützt Markieren mit der Maus und Strg+C.
 * <p>
 * Ein Fenster JE VERBINDUNG; ein erneuter Aufruf für dieselbe Verbindung
 * holt das offene Fenster nach vorn. "Anhalten" friert die Anzeige ein
 * (die Mitschrift in der Verbindung läuft weiter; "Fortsetzen" holt das
 * Versäumte nach), "Exportieren" schreibt alles als .txt, "Leeren"
 * verwirft die Mitschrift.
 */
public final class BidibRawLogWindow {

    private static final Map<BidibConnection, Stage> OPEN_WINDOWS = new IdentityHashMap<>();

    private BidibRawLogWindow() {
    }

    public static void show(Stage owner, BidibConnection connection) {
        Stage existing = OPEN_WINDOWS.get(connection);
        if (existing != null && existing.isShowing()) {
            existing.toFront();
            existing.requestFocus();
            return;
        }

        I18n i18n = I18n.getInstance();
        AppSettings settings = AppSettings.getInstance();

        TextArea textArea = new TextArea();
        textArea.setEditable(false);
        textArea.setWrapText(false);
        textArea.setStyle("-fx-font-family: 'Consolas', 'Monospaced';");
        textArea.setPromptText(i18n.t("bidib.rawLogEmpty"));

        Label countLabel = new Label();
        // "Hex": rohe Bytes statt der lesbaren Form (Nachrichtenname,
        // Adresse, aufgeloeste Nutzdaten - siehe BidibMessageDecoder).
        ToggleButton hexButton = new ToggleButton(i18n.t("bidib.rawLogHex"));
        Runnable refill = () -> {
            List<BidibConnection.RawEntry> entries = connection.snapshotRawLog();
            StringBuilder sb = new StringBuilder();
            for (BidibConnection.RawEntry entry : entries) {
                sb.append(hexButton.isSelected() ? entry.hexLine() : entry.textLine()).append('\n');
            }
            textArea.setText(sb.toString());
            textArea.positionCaret(textArea.getLength());
            countLabel.setText(i18n.t("bidib.rawLogCount", entries.size()));
        };
        // Beschriftung zeigt die AKTUELLE Ansicht ("Ansicht: Lesbar" /
        // "Ansicht: Hex") - ein Klick schaltet um. Vorher stand dort immer
        // nur "Hex", man sah nicht, in welcher Ansicht man ist.
        Runnable relabel = () -> hexButton.setText(
                i18n.t(hexButton.isSelected() ? "bidib.rawLogViewHex" : "bidib.rawLogViewReadable"));
        relabel.run();
        hexButton.setTooltip(new javafx.scene.control.Tooltip(i18n.t("bidib.rawLogViewTooltip")));
        hexButton.setOnAction(e -> {
            relabel.run();
            refill.run();
        });

        ToggleButton pauseButton = new ToggleButton(i18n.t("bidib.rawLogPause"));
        pauseButton.selectedProperty().addListener((obs, old, paused) -> {
            pauseButton.setText(i18n.t(paused ? "bidib.rawLogResume" : "bidib.rawLogPause"));
            if (!paused) {
                refill.run();
            }
        });
        Button exportButton = new Button(i18n.t("bidib.rawLogExport"));
        // "Kopieren": die ganze Mitschrift in die Zwischenablage.
        Button copyButton = new Button(i18n.t("bidib.rawLogCopy"));
        copyButton.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(logText(connection, hexButton.isSelected()));
            Clipboard.getSystemClipboard().setContent(content);
        });
        Button clearButton = new Button(i18n.t("bidib.rawLogClear"));
        clearButton.setOnAction(e -> {
            connection.clearRawLog();
            refill.run();
        });

        HBox toolbar = new HBox(12, pauseButton, hexButton, copyButton, exportButton, clearButton, countLabel);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Label hintLabel = new Label(i18n.t("bidib.rawLogHint"));
        hintLabel.setWrapText(true);

        VBox content = new VBox(8, hintLabel, toolbar, textArea);
        content.setPadding(new Insets(12));
        VBox.setVgrow(textArea, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setCenter(content);

        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.titleProperty().bind(javafx.beans.binding.Bindings.concat(
                i18n.t("bidib.rawLogWindowTitle") + " - ", connection.nameProperty()));
        stage.getIcons().addAll(loadAppIcons());

        exportButton.setOnAction(e -> exportLog(stage, connection, i18n, settings, hexButton.isSelected()));

        Consumer<BidibConnection.RawEntry> listener = entry -> Platform.runLater(() -> {
            if (pauseButton.isSelected()) {
                return;
            }
            textArea.appendText((hexButton.isSelected() ? entry.hexLine() : entry.textLine()) + "\n");
            countLabel.setText(i18n.t("bidib.rawLogCount", connection.snapshotRawLog().size()));
        });
        refill.run();
        connection.addRawLogListener(listener);

        OPEN_WINDOWS.put(connection, stage);
        stage.setOnHidden(e -> {
            OPEN_WINDOWS.remove(connection);
            connection.removeRawLogListener(listener);
        });

        Scene scene = new Scene(root);
        ThemeManager.apply(scene, settings.getTheme());
        stage.setScene(scene);
        stage.setMinWidth(520);
        stage.setMinHeight(360);
        WindowState.apply(stage, "bidibRawLog", 780, 480);
        stage.show();
    }

    private static void exportLog(Stage owner, BidibConnection connection, I18n i18n, AppSettings settings,
            boolean hex) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n.t("bidib.rawLogExport"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("TXT", "*.txt"));
        String exportDir = settings.getExportDirectory();
        if (exportDir != null && new File(exportDir).isDirectory()) {
            chooser.setInitialDirectory(new File(exportDir));
        }
        String base = connection.getName().replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        chooser.setInitialFileName("RXTX_" + base + "_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt");
        File target = chooser.showSaveDialog(owner);
        if (target == null) {
            return;
        }
        if (!target.getName().toLowerCase().endsWith(".txt")) {
            target = new File(target.getParentFile(), target.getName() + ".txt");
        }
        try {
            Files.writeString(target.toPath(), logText(connection, hex), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR, String.valueOf(ex.getMessage()));
            alert.initOwner(owner);
            alert.setHeaderText(null);
            alert.showAndWait();
        }
    }

    /**
     * Kopfzeile plus alle Zeilen - fuer Datei und Zwischenablage. In der
     * lesbaren Form steht der Hex-Dump zusaetzlich hinter jeder Zeile
     * (durch " | " getrennt), damit nichts verloren geht, was der Decoder
     * nicht aufloest.
     */
    private static String logText(BidibConnection connection, boolean hex) {
        StringBuilder text = new StringBuilder();
        text.append("RX/TX ").append(connection.getName()).append(" (").append(connection.getHostPort())
                .append(") ").append(LocalDateTime.now()).append(System.lineSeparator());
        for (BidibConnection.RawEntry entry : connection.snapshotRawLog()) {
            if (hex) {
                text.append(entry.hexLine());
            } else {
                text.append(entry.textLine()).append("   | ").append(BidibMessageDecoder.toHex(entry.data()));
            }
            text.append(System.lineSeparator());
        }
        return text.toString();
    }

    private static Image[] loadAppIcons() {
        try (InputStream in = BidibRawLogWindow.class.getResourceAsStream("app-icon.png")) {
            return in == null ? new Image[0] : new Image[]{new Image(in)};
        } catch (Exception ex) {
            return new Image[0];
        }
    }
}
