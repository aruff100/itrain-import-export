package com.example.itrain_import_export;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.DirectoryChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Ein einziges "Voreinstellungen"-Fenster mit drei Reitern - Pfade, Sprache,
 * Ansicht - statt vormals drei getrennter Dialoge. Erreichbar über einen
 * einzelnen Menüpunkt "Voreinstellungen" im "Einstellungen"-Menü. Alle
 * Änderungen wirken sofort und werden dauerhaft über {@link AppSettings}
 * gespeichert.
 */
public final class SettingsDialog {

    private SettingsDialog() {
    }

    /** Öffnet das Voreinstellungen-Fenster mit allen drei Reitern. */
    public static void showPreferences(Stage owner) {
        I18n i18n = I18n.getInstance();
        AppSettings settings = AppSettings.getInstance();

        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(i18n.t("menu.preferences"));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Reihenfolge: erst "Ansicht" (Sprache, Farbschema, Bildschirm - was
        // man am ehesten sucht), dann "Pfade". Die Sprache hat keinen eigenen
        // Reiter mehr; sie gehört sachlich zur Darstellung und stand allein
        // auf einem fast leeren Reiter.
        Tab viewTab = new Tab(i18n.t("menu.settingsView"), buildViewContent(owner, dialog, settings, i18n));
        Tab pathsTab = new Tab(i18n.t("menu.settingsPaths"), buildPathsContent(owner, settings, i18n));
        tabPane.getTabs().addAll(viewTab, pathsTab);

        dialog.getDialogPane().setContent(tabPane);
        // Breit genug, dass auch lange Ordnerpfade lesbar bleiben.
        dialog.getDialogPane().setPrefSize(760, 380);
        applyThemeOnceShown(dialog, settings);
        dialog.showAndWait();
    }

    /** Inhalt des Reiters "Pfade": Standard-Ordner für iTrain-Dateien, Exports und Backups. */
    private static javafx.scene.Node buildPathsContent(Stage owner, AppSettings settings, I18n i18n) {
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
        HBox tcdRow = pathRow(tcdPathLabel, tcdBrowseButton);

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
        HBox exportRow = pathRow(exportPathLabel, exportBrowseButton);

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
        HBox backupRow = pathRow(backupPathLabel, backupBrowseButton);

        Label decoderPathLabel = new Label(pathOrPlaceholder(settings.getDecoderDirectory(), i18n));
        Button decoderBrowseButton = new Button(i18n.t("settings.browse"));
        decoderBrowseButton.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle(i18n.t("settings.decoderPath"));
            File initial = settings.getDecoderDirectory() != null ? new File(settings.getDecoderDirectory()) : null;
            if (initial != null && initial.isDirectory()) {
                chooser.setInitialDirectory(initial);
            }
            File chosen = chooser.showDialog(owner);
            if (chosen != null) {
                settings.setDecoderDirectory(chosen.getAbsolutePath());
                decoderPathLabel.setText(chosen.getAbsolutePath());
            }
        });
        HBox decoderRow = pathRow(decoderPathLabel, decoderBrowseButton);

        Label systemFilesPathLabel = new Label(pathOrPlaceholder(settings.getSystemFilesDirectory(), i18n));
        Button systemFilesBrowseButton = new Button(i18n.t("settings.browse"));
        systemFilesBrowseButton.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle(i18n.t("settings.systemFilesPath"));
            File initial = settings.getSystemFilesDirectory() != null
                    ? new File(settings.getSystemFilesDirectory()) : null;
            if (initial != null && initial.isDirectory()) {
                chooser.setInitialDirectory(initial);
            }
            File chosen = chooser.showDialog(owner);
            if (chosen != null) {
                settings.setSystemFilesDirectory(chosen.getAbsolutePath());
                systemFilesPathLabel.setText(chosen.getAbsolutePath());
            }
        });
        HBox systemFilesRow = pathRow(systemFilesPathLabel, systemFilesBrowseButton);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(15));
        grid.addRow(0, new Label(i18n.t("settings.tcdPath")), tcdRow);
        grid.addRow(1, new Label(i18n.t("settings.exportPath")), exportRow);
        grid.addRow(2, new Label(i18n.t("settings.backupPath")), backupRow);
        grid.addRow(3, new Label(i18n.t("settings.decoderPath")), decoderRow);
        grid.addRow(4, new Label(i18n.t("settings.systemFilesPath")), systemFilesRow);

        // Die erste Spalte bekommt nur so viel Platz, wie die Beschriftungen
        // brauchen; alles Weitere geht an die Pfadzeile. Ohne das teilte sich
        // das Gitter den Platz gleichmäßig auf, und lange Ordnerpfade waren
        // abgeschnitten.
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setHgrow(Priority.NEVER);
        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setHgrow(Priority.ALWAYS);
        valueColumn.setFillWidth(true);
        grid.getColumnConstraints().addAll(labelColumn, valueColumn);
        return grid;
    }

    /**
     * Eine Pfadzeile: der Pfad selbst nimmt den verfügbaren Platz ein, der
     * Knopf "Durchsuchen..." steht rechtsbündig am Rand - so stehen alle
     * vier Knöpfe untereinander auf einer Linie, statt hinter unterschiedlich
     * langen Pfaden zu verspringen.
     */
    private static HBox pathRow(Label pathLabel, Button browseButton) {
        pathLabel.setWrapText(true);
        pathLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(pathLabel, Priority.ALWAYS);
        HBox row = new HBox(10, pathLabel, browseButton);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /**
     * Inhalt des Reiters "Ansicht": Sprache, Farbschema, Bildschirm sowie
     * Spalten-/Bereichs-Sichtbarkeit - also alles, was die Darstellung
     * betrifft.
     */
    private static javafx.scene.Node buildViewContent(Stage owner, Dialog<Void> dialog, AppSettings settings, I18n i18n) {
        ComboBox<String> languageCombo = new ComboBox<>(FXCollections.observableArrayList(I18n.LANGUAGE_CODES));
        languageCombo.setValue(i18n.getCurrentLanguage());
        languageCombo.setCellFactory(list -> new LanguageListCell());
        languageCombo.setButtonCell(new LanguageListCell());
        languageCombo.valueProperty().addListener((obs, oldCode, newCode) -> {
            if (newCode != null) {
                i18n.setLanguage(newCode);
            }
        });

        // Farbschema: keine Hell/Dunkel-Auswahl mehr, sondern ein Knopf, der
        // das Fenster "Farbkombination" oeffnet (Hintergrund- und Textfarbe
        // frei waehlbar, Hell/Dunkel dort als Vorbelegung). Siehe
        // CustomColorDialog/ThemeManager.
        Button customColorButton = new Button(i18n.t("settings.customColor"));
        customColorButton.setOnAction(e -> CustomColorDialog.show(owner));

        CheckBox showTypeBox = new CheckBox();
        showTypeBox.setSelected(settings.getShowTypeColumn());
        showTypeBox.selectedProperty().addListener((obs, oldV, newV) -> settings.setShowTypeColumn(newV));

        CheckBox showSelectionBox = new CheckBox();
        showSelectionBox.setSelected(settings.getShowSelectionCheckbox());
        showSelectionBox.selectedProperty().addListener((obs, oldV, newV) -> settings.setShowSelectionCheckbox(newV));

        CheckBox showDataEditorBox = new CheckBox();
        showDataEditorBox.setSelected(settings.getShowDataEditor());
        showDataEditorBox.selectedProperty().addListener((obs, oldV, newV) -> settings.setShowDataEditor(newV));

        CheckBox autoUpdateCheckBox = new CheckBox();
        autoUpdateCheckBox.setSelected(settings.getAutoUpdateCheckEnabled());
        autoUpdateCheckBox.selectedProperty().addListener((obs, oldV, newV) -> settings.setAutoUpdateCheckEnabled(newV));

        // Gegenstück zum Ankreuzfeld "Hinweise nicht mehr anzeigen" im
        // Hinweis-Dialog selbst: dieselbe Einstellung, hier nur mit
        // umgekehrter Aussage - dort wird abgeschaltet, hier eingeschaltet.
        CheckBox decoderHintsBox = new CheckBox();
        decoderHintsBox.setSelected(settings.getShowDecoderHints());
        decoderHintsBox.selectedProperty().addListener((obs, oldV, newV) -> settings.setShowDecoderHints(newV));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(15));
        grid.addRow(0, new Label(i18n.t("settings.language")), languageCombo);
        grid.addRow(1, new Label(i18n.t("settings.theme")), customColorButton);
        grid.addRow(2, new Label(i18n.t("settings.screen")), buildScreenChooser(settings, i18n));
        grid.addRow(3, new Label(i18n.t("settings.showType")), showTypeBox);
        grid.addRow(4, new Label(i18n.t("settings.showSelectionCheckbox")), showSelectionBox);
        grid.addRow(5, new Label(i18n.t("settings.showDataEditor")), showDataEditorBox);
        grid.addRow(6, new Label(i18n.t("settings.autoUpdateCheck")), autoUpdateCheckBox);
        grid.addRow(7, new Label(i18n.t("settings.showDecoderHints")), decoderHintsBox);
        return grid;
    }

    /**
     * Auswahl des Bildschirms für neu geöffnete Fenster. Voreinstellung ist
     * "wie zuletzt": Jedes Fenster merkt sich seine Lage selbst (siehe
     * {@link WindowState}), und Zusatzfenster erscheinen dort, wo das Fenster
     * steht, aus dem sie aufgerufen wurden. Ein fester Bildschirm ist für
     * den Fall gedacht, dass die Anwendung immer auf demselben Monitor
     * starten soll.
     * <p>
     * Bei nur einem angeschlossenen Bildschirm bleibt die Auswahl deaktiviert -
     * sie hätte dort nichts zu entscheiden.
     */
    private static javafx.scene.Node buildScreenChooser(AppSettings settings, I18n i18n) {
        List<Screen> screens = Screen.getScreens();
        List<String> options = new ArrayList<>();
        options.add(AppSettings.SCREEN_REMEMBER);
        for (int i = 0; i < screens.size(); i++) {
            options.add(String.valueOf(i));
        }

        ComboBox<String> combo = new ComboBox<>(FXCollections.observableArrayList(options));
        combo.setCellFactory(list -> screenCell(screens, i18n));
        combo.setButtonCell(screenCell(screens, i18n));
        String current = settings.getPreferredScreen();
        combo.setValue(options.contains(current) ? current : AppSettings.SCREEN_REMEMBER);
        combo.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                settings.setPreferredScreen(newV);
            }
        });
        if (screens.size() < 2) {
            combo.setDisable(true);
            Label hint = new Label(i18n.t("settings.screenSingle"));
            hint.setStyle("-fx-opacity: 0.8;");
            HBox row = new HBox(10, combo, hint);
            row.setAlignment(Pos.CENTER_LEFT);
            return row;
        }
        return combo;
    }

    /** Zeigt "wie zuletzt" bzw. "Bildschirm 1 (1920x1080)" statt der rohen Kennung. */
    private static ListCell<String> screenCell(List<Screen> screens, I18n i18n) {
        return new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                if (AppSettings.SCREEN_REMEMBER.equals(item)) {
                    setText(i18n.t("settings.screenRemember"));
                    return;
                }
                int index = Integer.parseInt(item);
                Rectangle2D bounds = screens.get(index).getBounds();
                setText(i18n.t("settings.screenNumber", index + 1,
                        (int) bounds.getWidth(), (int) bounds.getHeight()));
            }
        };
    }

    /**
     * Der Dialog bekommt seine eigene Scene erst beim Anzeigen - deshalb
     * das aktuelle Farbschema erst anwenden, sobald sie tatsächlich existiert,
     * damit auch das Voreinstellungen-Fenster selbst im Dunkel-Modus dunkel ist.
     */
    private static void applyThemeOnceShown(Dialog<?> dialog, AppSettings settings) {
        dialog.getDialogPane().sceneProperty().addListener((obs, oldScene, newScene) ->
                ThemeManager.apply(newScene, settings.getTheme()));
    }

    private static String pathOrPlaceholder(String path, I18n i18n) {
        return (path == null || path.isBlank()) ? i18n.t("settings.notSet") : path;
    }
}
