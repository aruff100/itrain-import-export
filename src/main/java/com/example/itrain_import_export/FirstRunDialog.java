package com.example.itrain_import_export;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.Scene;
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
 * typischerweise also nur beim allerersten Programmstart. Bietet drei Dinge:
 * <p>
 * 1) Eine Sprachauswahl (identisch zum Reiter "Sprache" in den
 * Voreinstellungen, mit Flagge - siehe {@link LanguageListCell}). Wählt der
 * Nutzer hier eine andere Sprache, wird sie sofort über
 * {@link I18n#setLanguage} aktiv - das lässt auch diesen Dialog selbst live
 * in der neuen Sprache erscheinen (Titel, Beschriftungen, Buttons), über
 * einen eigenen, beim Schließen wieder abgemeldeten Sprachwechsel-Listener.
 * <p>
 * 2) Eine Farbschema-Auswahl (hell/dunkel, identisch zum Reiter "Ansicht" in
 * den Voreinstellungen, siehe {@link ThemeListCell}) - wirkt sofort auf das
 * bereits sichtbare Hauptfenster UND auf diesen Dialog selbst, damit der
 * Nutzer das gewählte Farbschema direkt sieht, statt es erst nachträglich in
 * den Voreinstellungen zu entdecken.
 * <p>
 * 3) Je einen Pfadvorschlag für iTrain-/Export-/Backup-Ordner, abhängig vom
 * Betriebssystem unterhalb des Benutzerverzeichnisses
 * ({@code <Benutzerverzeichnis>/iTrain/layouts} bzw. {@code .../export} bzw.
 * {@code .../backup} - unter Windows z.B. {@code C:\Users\<Name>\iTrain\...},
 * unter Linux {@code /home/<Name>/iTrain/...}, unter macOS
 * {@code /Users/<Name>/iTrain/...}; ermittelt plattformunabhängig über
 * {@code System.getProperty("user.home")}, statt das Betriebssystem selbst
 * zu erkennen). Über "Durchsuchen" kann statt des Vorschlags jederzeit ein
 * anderer, bereits vorhandener Ordner gewählt werden. Existiert der beim
 * Abschließen eingetragene Ordner (Vorschlag oder eigene Wahl) noch nicht,
 * wird gefragt, ob er angelegt werden soll.
 * <p>
 * Die getroffene Wahl wird wie gewohnt über {@link AppSettings} dauerhaft
 * gespeichert (Java-Preferences) - bei allen weiteren Starts bleibt dieser
 * Dialog deshalb aus. Über Voreinstellungen → Pfade/Sprache lässt sich
 * jederzeit nachträglich alles ändern.
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

        // Sprachauswahl - dieselbe Zellen-Darstellung (Flagge + nativer Name)
        // wie im Voreinstellungen-Reiter "Sprache".
        Label languageCaption = new Label(i18n.t("settings.language"));
        ComboBox<String> languageCombo = new ComboBox<>(FXCollections.observableArrayList(I18n.LANGUAGE_CODES));
        languageCombo.setValue(i18n.getCurrentLanguage());
        languageCombo.setCellFactory(list -> new LanguageListCell());
        languageCombo.setButtonCell(new LanguageListCell());
        languageCombo.valueProperty().addListener((obs, oldCode, newCode) -> {
            if (newCode != null) {
                i18n.setLanguage(newCode);
            }
        });

        // Farbschema-Auswahl - dieselbe Zellen-Darstellung ("Hell"/"Dunkel")
        // wie im Voreinstellungen-Reiter "Ansicht", wirkt aber sofort auf das
        // bereits sichtbare Hauptfenster UND diesen Dialog selbst (siehe
        // Listener unten), damit der Nutzer die Wahl direkt sieht.
        Label themeCaption = new Label(i18n.t("settings.theme"));
        ComboBox<String> themeCombo = new ComboBox<>(FXCollections.observableArrayList(
                AppSettings.THEME_LIGHT, AppSettings.THEME_DARK));
        themeCombo.setValue(settings.getTheme());
        themeCombo.setCellFactory(list -> new ThemeListCell());
        themeCombo.setButtonCell(new ThemeListCell());
        themeCombo.valueProperty().addListener((obs, oldTheme, newTheme) -> {
            if (newTheme != null) {
                settings.setTheme(newTheme);
                ThemeManager.apply(owner.getScene(), newTheme);
                Scene dialogScene = dialog.getDialogPane().getScene();
                if (dialogScene != null) {
                    ThemeManager.apply(dialogScene, newTheme);
                }
            }
        });

        Label tcdCaption = new Label(i18n.t("settings.tcdPath"));
        Label exportCaption = new Label(i18n.t("settings.exportPath"));
        Label backupCaption = new Label(i18n.t("settings.backupPath"));

        PathRow tcdRow = new PathRow(owner, i18n, suggestedTcd);
        PathRow exportRow = new PathRow(owner, i18n, suggestedExport);
        PathRow backupRow = new PathRow(owner, i18n, suggestedBackup);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.addRow(0, languageCaption, languageCombo);
        grid.addRow(1, themeCaption, themeCombo);
        grid.addRow(2, tcdCaption, tcdRow.row);
        grid.addRow(3, exportCaption, exportRow.row);
        grid.addRow(4, backupCaption, backupRow.row);

        VBox content = new VBox(12, introLabel, grid);
        content.setPadding(new Insets(15));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(600);

        // Die Scene existiert erst, sobald der Dialog tatsächlich angezeigt
        // wird - deshalb das Farbschema erst dann anwenden (gleiches Muster
        // wie in SettingsDialog/HelpDialog/AboutDialog).
        dialog.getDialogPane().sceneProperty().addListener((obs, oldScene, newScene) ->
                ThemeManager.apply(newScene, settings.getTheme()));

        // Bei Sprachwechsel (siehe languageCombo oben) feuert I18n einen
        // globalen Listener, der u.a. das Hauptfenster neu aufbaut - dieser
        // Dialog selbst hängt aber nicht an diesem Mechanismus (er ist kein
        // Teil von rebuildTabs) und muss seine eigenen Texte deshalb separat
        // aktualisieren. Der Listener wird beim Schließen des Dialogs wieder
        // abgemeldet, damit er nicht dauerhaft (über die gesamte weitere
        // Programmlaufzeit) an I18n hängen bleibt.
        Runnable refreshTexts = () -> {
            dialog.setTitle(i18n.t("firstRun.title"));
            introLabel.setText(i18n.t("firstRun.intro"));
            languageCaption.setText(i18n.t("settings.language"));
            themeCaption.setText(i18n.t("settings.theme"));
            // ThemeListCell übersetzt "Hell"/"Dunkel" erst beim (Neu-)Rendern
            // der Zelle - ein neuer Button-Cell erzwingt das sofort, auch
            // wenn sich die Auswahl selbst nicht geändert hat.
            themeCombo.setButtonCell(new ThemeListCell());
            tcdCaption.setText(i18n.t("settings.tcdPath"));
            exportCaption.setText(i18n.t("settings.exportPath"));
            backupCaption.setText(i18n.t("settings.backupPath"));
            tcdRow.refreshText(i18n);
            exportRow.refreshText(i18n);
            backupRow.refreshText(i18n);
            Button finishButton = (Button) dialog.getDialogPane().lookupButton(finishButtonType);
            if (finishButton != null) {
                finishButton.setText(i18n.t("firstRun.finish"));
            }
            Button skipButton = (Button) dialog.getDialogPane().lookupButton(skipButtonType);
            if (skipButton != null) {
                skipButton.setText(i18n.t("firstRun.skip"));
            }
        };
        i18n.addLanguageChangeListener(refreshTexts);
        dialog.setOnHidden(e -> i18n.removeLanguageChangeListener(refreshTexts));

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
        private final Button browseButton;
        final HBox row;

        PathRow(Stage owner, I18n i18n, String suggestedPath) {
            label = new Label(suggestedPath);
            browseButton = new Button(i18n.t("settings.browse"));
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

        /** Übersetzt nur den Button-Text neu - der Pfad selbst (Vorschlag oder eigene Wahl) bleibt unverändert. */
        void refreshText(I18n i18n) {
            browseButton.setText(i18n.t("settings.browse"));
        }
    }
}
