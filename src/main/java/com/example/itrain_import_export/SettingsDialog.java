package com.example.itrain_import_export;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.InputStream;

/**
 * Einstellungen, aufgeteilt in drei eigenständige Dialoge - passend zu den
 * drei Einträgen im "Einstellungen"-Menü: {@link #showPaths}, "Pfade" (die
 * beiden Standard-Ordner für iTrain-Dateien und Exports); {@link
 * #showLanguage}, "Sprache" (mit Flagge je Sprache); {@link #showView},
 * "Ansicht" (Hell/Dunkel-Farbschema). Alle Änderungen wirken sofort und
 * werden dauerhaft über {@link AppSettings} gespeichert.
 */
public final class SettingsDialog {

    private SettingsDialog() {
    }

    /** Dialog "Pfade": Standard-Ordner für iTrain-Dateien und für Exports. */
    public static void showPaths(Stage owner) {
        I18n i18n = I18n.getInstance();
        AppSettings settings = AppSettings.getInstance();

        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(i18n.t("menu.settingsPaths"));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        Label tcdPathLabel = new Label(pathOrPlaceholder(settings.getTcdDirectory(), i18n));
        Button tcdBrowseButton = new Button(i18n.t("settings.browse"));
        tcdBrowseButton.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle(i18n.t("settings.tcdPath"));
            File initial = settings.getTcdDirectory() != null ? new File(settings.getTcdDirectory()) : null;
            if (initial != null && initial.isDirectory()) {
                chooser.setInitialDirectory(initial);
            }
            File chosen = chooser.showDialog(owner);
            if (chosen != null) {
                settings.setTcdDirectory(chosen.getAbsolutePath());
                tcdPathLabel.setText(chosen.getAbsolutePath());
            }
        });
        HBox tcdRow = new HBox(10, tcdPathLabel, tcdBrowseButton);

        Label exportPathLabel = new Label(pathOrPlaceholder(settings.getExportDirectory(), i18n));
        Button exportBrowseButton = new Button(i18n.t("settings.browse"));
        exportBrowseButton.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle(i18n.t("settings.exportPath"));
            File initial = settings.getExportDirectory() != null ? new File(settings.getExportDirectory()) : null;
            if (initial != null && initial.isDirectory()) {
                chooser.setInitialDirectory(initial);
            }
            File chosen = chooser.showDialog(owner);
            if (chosen != null) {
                settings.setExportDirectory(chosen.getAbsolutePath());
                exportPathLabel.setText(chosen.getAbsolutePath());
            }
        });
        HBox exportRow = new HBox(10, exportPathLabel, exportBrowseButton);

        Label backupPathLabel = new Label(pathOrPlaceholder(settings.getBackupDirectory(), i18n));
        Button backupBrowseButton = new Button(i18n.t("settings.browse"));
        backupBrowseButton.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle(i18n.t("settings.backupPath"));
            File initial = settings.getBackupDirectory() != null ? new File(settings.getBackupDirectory()) : null;
            if (initial != null && initial.isDirectory()) {
                chooser.setInitialDirectory(initial);
            }
            File chosen = chooser.showDialog(owner);
            if (chosen != null) {
                settings.setBackupDirectory(chosen.getAbsolutePath());
                backupPathLabel.setText(chosen.getAbsolutePath());
            }
        });
        HBox backupRow = new HBox(10, backupPathLabel, backupBrowseButton);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(15));
        grid.addRow(0, new Label(i18n.t("settings.tcdPath")), tcdRow);
        grid.addRow(1, new Label(i18n.t("settings.exportPath")), exportRow);
        grid.addRow(2, new Label(i18n.t("settings.backupPath")), backupRow);

        dialog.getDialogPane().setContent(grid);
        applyThemeOnceShown(dialog, settings);
        dialog.showAndWait();
    }

    /** Dialog "Sprache": Sprachauswahl mit Flagge je Sprache. */
    public static void showLanguage(Stage owner) {
        I18n i18n = I18n.getInstance();
        AppSettings settings = AppSettings.getInstance();

        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(i18n.t("menu.settingsLanguage"));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        ComboBox<String> languageCombo = new ComboBox<>(FXCollections.observableArrayList(I18n.LANGUAGE_CODES));
        languageCombo.setValue(i18n.getCurrentLanguage());
        languageCombo.setCellFactory(list -> new LanguageCell());
        languageCombo.setButtonCell(new LanguageCell());
        languageCombo.valueProperty().addListener((obs, oldCode, newCode) -> {
            if (newCode != null) {
                i18n.setLanguage(newCode);
            }
        });

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(15));
        grid.addRow(0, new Label(i18n.t("settings.language")), languageCombo);

        dialog.getDialogPane().setContent(grid);
        applyThemeOnceShown(dialog, settings);
        dialog.showAndWait();
    }

    /** Dialog "Ansicht": Farbschema Hell/Dunkel. */
    public static void showView(Stage owner) {
        I18n i18n = I18n.getInstance();
        AppSettings settings = AppSettings.getInstance();

        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(i18n.t("menu.settingsView"));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        ComboBox<String> themeCombo = new ComboBox<>(FXCollections.observableArrayList(
                AppSettings.THEME_LIGHT, AppSettings.THEME_DARK));
        themeCombo.setValue(settings.getTheme());
        themeCombo.setCellFactory(list -> new ThemeCell());
        themeCombo.setButtonCell(new ThemeCell());
        themeCombo.valueProperty().addListener((obs, oldTheme, newTheme) -> {
            if (newTheme != null) {
                settings.setTheme(newTheme);
                ThemeManager.apply(owner.getScene(), newTheme);
                ThemeManager.apply(dialog.getDialogPane().getScene(), newTheme);
            }
        });

        CheckBox showTypeBox = new CheckBox();
        showTypeBox.setSelected(settings.getShowTypeColumn());
        showTypeBox.selectedProperty().addListener((obs, oldV, newV) -> settings.setShowTypeColumn(newV));

        CheckBox showSelectionBox = new CheckBox();
        showSelectionBox.setSelected(settings.getShowSelectionCheckbox());
        showSelectionBox.selectedProperty().addListener((obs, oldV, newV) -> settings.setShowSelectionCheckbox(newV));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(15));
        grid.addRow(0, new Label(i18n.t("settings.theme")), themeCombo);
        grid.addRow(1, new Label(i18n.t("settings.showType")), showTypeBox);
        grid.addRow(2, new Label(i18n.t("settings.showSelectionCheckbox")), showSelectionBox);

        dialog.getDialogPane().setContent(grid);
        applyThemeOnceShown(dialog, settings);
        dialog.showAndWait();
    }

    /**
     * Der Dialog bekommt seine eigene Scene erst beim Anzeigen - deshalb
     * das aktuelle Farbschema erst anwenden, sobald sie tatsächlich existiert,
     * damit auch die Einstellungen-Dialoge selbst im Dunkel-Modus dunkel sind.
     */
    private static void applyThemeOnceShown(Dialog<?> dialog, AppSettings settings) {
        dialog.getDialogPane().sceneProperty().addListener((obs, oldScene, newScene) ->
                ThemeManager.apply(newScene, settings.getTheme()));
    }

    /** Zeigt "Hell"/"Dunkel" (übersetzt) statt der internen Theme-Codes "light"/"dark". */
    private static class ThemeCell extends ListCell<String> {
        @Override
        protected void updateItem(String theme, boolean empty) {
            super.updateItem(theme, empty);
            if (empty || theme == null) {
                setText(null);
                return;
            }
            I18n i18n = I18n.getInstance();
            setText(AppSettings.THEME_DARK.equals(theme)
                    ? i18n.t("settings.themeDark")
                    : i18n.t("settings.themeLight"));
        }
    }

    private static String pathOrPlaceholder(String path, I18n i18n) {
        return (path == null || path.isBlank()) ? i18n.t("settings.notSet") : path;
    }

    /** Zeigt Flagge + nativen Sprachnamen in der ComboBox/Dropdown-Liste. */
    private static class LanguageCell extends ListCell<String> {
        @Override
        protected void updateItem(String code, boolean empty) {
            super.updateItem(code, empty);
            if (empty || code == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            I18n i18n = I18n.getInstance();
            setText(i18n.getNativeName(code));

            String flagPath = i18n.getFlagResourcePath(code);
            try (InputStream flagStream = I18n.class.getResourceAsStream(flagPath)) {
                if (flagStream != null) {
                    ImageView flagView = new ImageView(new Image(flagStream));
                    flagView.setFitWidth(24);
                    flagView.setFitHeight(16);
                    flagView.setPreserveRatio(false);
                    setGraphic(flagView);
                } else {
                    setGraphic(null);
                }
            } catch (Exception ex) {
                setGraphic(null);
            }
        }
    }
}
