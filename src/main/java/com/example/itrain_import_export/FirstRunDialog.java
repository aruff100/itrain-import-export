package com.example.itrain_import_export;

import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Ersteinrichtung: erscheint nur, solange noch KEINER der drei Pfade
 * (iTrain-/Export-/Backup-Ordner) in {@link AppSettings} gesetzt ist -
 * typischerweise also nur beim allerersten Programmstart. Schlägt je nach
 * Betriebssystem einen Standardpfad unterhalb des Benutzerverzeichnisses vor
 * ({@code <Benutzerverzeichnis>/iTrain/layouts} bzw. {@code .../export} bzw.
 * {@code .../backup} - unter Windows z.B. {@code C:\Users\<Name>\iTrain\...},
 * unter Linux {@code /home/<Name>/iTrain/...}, unter macOS
 * {@code /Users/<Name>/iTrain/...}; ermittelt plattformunabhängig über
 * {@code System.getProperty("user.home")}, statt das Betriebssystem selbst
 * zu erkennen). Über "Durchsuchen" kann statt des Vorschlags jederzeit ein
 * anderer, bereits vorhandener Ordner gewählt werden. Existiert der beim
 * Abschließen eingetragene Ordner (Vorschlag oder eigene Wahl) noch nicht,
 * wird gefragt, ob er angelegt werden soll. Die getroffene Wahl wird wie
 * gewohnt über {@link AppSettings} dauerhaft gespeichert (Java-Preferences) -
 * bei allen weiteren Starts bleibt dieser Dialog deshalb aus. Über
 * Voreinstellungen → Pfade lässt sich jederzeit nachträglich alles ändern.
 */
public final class FirstRunDialog {

    private FirstRunDialog() {
    }

    /** Zeigt den Dialog nur, falls noch kein einziger der drei Pfade gesetzt ist. */
    public static void showIfNeeded(Stage owner) {
        AppSettings settings = AppSettings.getInstance();
        boolean alreadyConfigured = settings.getTcdDirectory() != null
                || settings.getExportDirectory() != null
                || settings.getBackupDirectory() != null;
        if (alreadyConfigured) {
            return;
        }

        I18n i18n = I18n.getInstance();
        String home = System.getProperty("user.home");
        String suggestedTcd = Paths.get(home, "iTrain", "layouts").toString();
        String suggestedExport = Paths.get(home, "iTrain", "export").toString();
        String suggestedBackup = Paths.get(home, "iTrain", "backup").toString();

        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(i18n.t("firstRun.title"));

        ButtonType finishButtonType = new ButtonType(i18n.t("firstRun.finish"), ButtonBar.ButtonData.OK_DONE);
        ButtonType skipButtonType = new ButtonType(i18n.t("firstRun.skip"), ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(skipButtonType, finishButtonType);

        Label introLabel = new Label(i18n.t("firstRun.intro"));
        introLabel.setWrapText(true);
        introLabel.setMaxWidth(500);

        PathRow tcdRow = new PathRow(owner, i18n, suggestedTcd);
        PathRow exportRow = new PathRow(owner, i18n, suggestedExport);
        PathRow backupRow = new PathRow(owner, i18n, suggestedBackup);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.addRow(0, new Label(i18n.t("settings.tcdPath")), tcdRow.row);
        grid.addRow(1, new Label(i18n.t("settings.exportPath")), exportRow.row);
        grid.addRow(2, new Label(i18n.t("settings.backupPath")), backupRow.row);

        VBox content = new VBox(12, introLabel, grid);
        content.setPadding(new Insets(15));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(600);

        // Die Scene existiert erst, sobald der Dialog tatsächlich angezeigt
        // wird - deshalb das Farbschema erst dann anwenden (gleiches Muster
        // wie in SettingsDialog/HelpDialog/AboutDialog).
        dialog.getDialogPane().sceneProperty().addListener((obs, oldScene, newScene) ->
                ThemeManager.apply(newScene, settings.getTheme()));

        dialog.setResultConverter(buttonType -> {
            if (buttonType == finishButtonType) {
                applyPath(owner, i18n, settings, tcdRow.getPath(), settings::setTcdDirectory);
                applyPath(owner, i18n, settings, exportRow.getPath(), settings::setExportDirectory);
                applyPath(owner, i18n, settings, backupRow.getPath(), settings::setBackupDirectory);
            }
            return null;
        });

        dialog.showAndWait();
    }

    /**
     * Übernimmt den Pfad in die Einstellungen. Existiert der Ordner noch
     * nicht, wird gefragt, ob er angelegt werden soll - bei "No" bleibt der
     * Pfad ungesetzt (kann später jederzeit über Voreinstellungen → Pfade
     * nachgeholt werden, siehe {@link SettingsDialog}).
     */
    private static void applyPath(Stage owner, I18n i18n, AppSettings settings, String path, Consumer<String> setter) {
        if (path == null || path.isBlank()) {
            return;
        }
        File dir = new File(path);
        if (dir.isDirectory()) {
            setter.accept(path);
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                i18n.t("firstRun.createFolderMessage", path),
                ButtonType.YES, ButtonType.NO);
        confirm.initOwner(owner);
        confirm.setTitle(i18n.t("firstRun.createFolderTitle"));
        confirm.setHeaderText(null);
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            try {
                Files.createDirectories(dir.toPath());
                setter.accept(path);
            } catch (IOException ex) {
                new Alert(Alert.AlertType.ERROR, i18n.t("firstRun.createFolderFailed", ex.getMessage())).showAndWait();
            }
        }
    }

    /** Eine Zeile: Pfad-Anzeige (vorbelegt mit dem OS-Vorschlag) + "Durchsuchen"-Button. */
    private static final class PathRow {
        private final Label label;
        final HBox row;

        PathRow(Stage owner, I18n i18n, String suggestedPath) {
            label = new Label(suggestedPath);
            Button browseButton = new Button(i18n.t("settings.browse"));
            browseButton.setOnAction(e -> {
                DirectoryChooser chooser = new DirectoryChooser();
                File initial = new File(label.getText());
                if (initial.isDirectory()) {
                    chooser.setInitialDirectory(initial);
                }
                File chosen = chooser.showDialog(owner);
                if (chosen != null) {
                    label.setText(chosen.getAbsolutePath());
                }
            });
            row = new HBox(10, label, browseButton);
        }

        String getPath() {
            return label.getText();
        }
    }
}
