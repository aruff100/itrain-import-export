package com.example.itrain_import_export;

import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.InputStream;
import java.util.List;

/**
 * Verdrahtet das Hauptfenster "Import/Export": Menüzeile, Ribbon-Werkzeugleiste,
 * Dateiname-Zeile und die Reiter je control-items-Kategorie. Die eigentliche
 * Dokumentlogik (Öffnen, Speichern, Backup, Rückgängig/Wiederholen,
 * Reiteraufbau, Statuszeile) steckt seit dem Umbau auf drei Fenster in
 * {@link DocumentSession} und wird vom Decoder-Fenster mitbenutzt - hier
 * bleibt nur, was das Hauptfenster von den anderen unterscheidet.
 * <p>
 * Alle sichtbaren Texte kommen aus {@link I18n}; bei Sprachwechsel wird die
 * komplette Oberfläche neu aufgebaut ({@link #applyLanguage()}).
 */
public class HelloController implements DocumentSession.Host {

    @FXML
    private TabPane tabPane;

    @FXML
    private Label statusLabel;

    @FXML
    private Label fileNameLabel;

    @FXML
    private Menu fileMenu;

    @FXML
    private MenuItem openMenuItem;

    @FXML
    private MenuItem saveMenuItem;

    @FXML
    private MenuItem closeFileMenuItem;

    @FXML
    private MenuItem exitMenuItem;

    /**
     * Eigenständiges Untermenü "Zuletzt verwendet..." - Inhalt (bis zu 5
     * zuletzt geöffnete Dateien, oder ein deaktivierter Platzhalter) wird
     * zur Laufzeit aufgebaut, siehe {@link #refreshRecentFilesMenu()}.
     * "Backup laden..." und "Export Dateien" sind eigene, gleichrangige
     * Menüpunkte direkt danach (nicht mehr darin verschachtelt).
     */
    @FXML
    private Menu recentFilesMenu;

    @FXML
    private MenuItem loadBackupMenuItem;

    @FXML
    private MenuItem exportFilesMenuItem;

    @FXML
    private Menu editMenu;

    @FXML
    private MenuItem undoMenuItem;

    @FXML
    private MenuItem redoMenuItem;

    @FXML
    private MenuItem centerWindowsMenuItem;

    /** Seit 2.5: dieselben Aktionen wie die pastellfarbenen Ribbon-Knöpfe, siehe {@link #updateRibbonState()}. */
    @FXML
    private MenuItem editExportSelectedMenuItem;

    @FXML
    private MenuItem editImportCategoryMenuItem;

    @FXML
    private Menu settingsMenu;

    @FXML
    private MenuItem preferencesMenuItem;

    /**
     * Seit 2.5: führt zu den beiden anderen Programmteilen, mit derselben
     * Öffnen-oder-nach-vorn-Wirkung wie die gleichnamigen Ribbon-Knöpfe.
     */
    @FXML
    private Menu functionsMenu;

    @FXML
    private MenuItem functionsDecoderMenuItem;

    @FXML
    private MenuItem functionsSystemsMenuItem;

    @FXML
    private Menu helpMenu;

    @FXML
    private MenuItem helpMenuItem;

    @FXML
    private MenuItem manualMenuItem;

    @FXML
    private MenuItem updateMenuItem;

    @FXML
    private MenuItem aboutMenuItem;

    @FXML
    private ToolBar ribbonToolBar;

    @FXML
    private Button openToolButton;

    @FXML
    private Button saveToolButton;

    @FXML
    private Button undoToolButton;

    @FXML
    private Button redoToolButton;

    @FXML
    private Button preferencesToolButton;

    /**
     * Die Import-/Export-Schaltflächen im Ribbon. Sie standen früher in der
     * Werkzeugleiste jedes Kategorie-Reiters; seit 01.08.2026 liegen sie
     * zentral im Ribbon (durch einen Trennstrich von den übrigen Symbolen
     * abgesetzt) und wirken auf den gerade sichtbaren Reiter. Ihr Zustand
     * wird bei jedem Reiterwechsel nachgeführt, siehe
     * {@link #updateRibbonState()}.
     */
    @FXML
    private Button exportSelectedToolButton;

    @FXML
    private Button importCategoryToolButton;

    /**
     * Die beiden Knöpfe rechts im Ribbon, abgesetzt durch einen breiten
     * Trennstrich: Sie öffnen die Zusatzfenster "Decoder" und "Systeme"
     * (seit 2.5). Bis 2.0.1 standen an dieser Stelle die drei Decoder-Knöpfe;
     * die liegen jetzt im Ribbon des Decoder-Fensters.
     */
    @FXML
    private Button decoderWindowButton;

    @FXML
    private Button systemsWindowButton;

    /** Mitte des Fensters: Reiter und darüber das Startbild (siehe {@link #initStartImage()}). */
    @FXML
    private StackPane centerPane;

    @FXML
    private ImageView startImage;

    // Die Adresse des Handbuchs stand hier früher fest im Quelltext. Sie
    // kommt jetzt zur Laufzeit aus dem Update-Manifest (siehe UpdateChecker)
    // und wird nach dem ersten Abruf gemerkt, sodass das Handbuch auch ohne
    // Netz erreichbar bleibt. Grund: Eine einkompilierte Freigabe-Adresse
    // der Form ".../urls/TOKEN#SCHLUESSEL" liess Windows Defender die
    // fertige .msi als "Trojan:Win32/MalUri.A!cl" blockieren - ausführlich
    // in STATUS.md. An der Verteilung ändert sich dadurch nichts.

    /**
     * Pastellfarben der Schaltflächen (auf Wunsch des Nutzers). Die
     * Schriftfarbe wird bewusst mitgesetzt: sonst wäre der Text im dunklen
     * Farbschema hell auf hellem Grund und damit unlesbar.
     */
    static final String STYLE_EXPORT_SELECTED =
            "-fx-background-color: #c8e6c9; -fx-text-fill: #2b2b2b;";
    static final String STYLE_IMPORT_CATEGORY =
            "-fx-background-color: #f8d3dd; -fx-text-fill: #2b2b2b;";
    static final String STYLE_DECODER_EXPORT =
            "-fx-background-color: #cfe2f7; -fx-text-fill: #2b2b2b;";
    static final String STYLE_DECODER_IMPORT =
            "-fx-background-color: #ded3f0; -fx-text-fill: #2b2b2b;";
    /** Blassgelb für "Decoder erfassen" - hebt sich von den vier übrigen ab. */
    static final String STYLE_DECODER_CAPTURE =
            "-fx-background-color: #faeec2; -fx-text-fill: #2b2b2b;";
    // Die beiden Fensterknöpfe "Decoder" und "Systeme" bekommen bewusst KEINE
    // Pastellfarbe: Sie führen keine Funktion aus, sondern öffnen ein
    // Zusatzfenster, und sollen deshalb wie gewöhnliche Knöpfe aussehen.

    /**
     * Anteil der kleineren Fensterkante, den das Startbild einnimmt. Das
     * Bild wächst und schrumpft mit dem Fenster; das Seitenverhältnis bleibt
     * erhalten (preserveRatio im FXML).
     */
    private static final double START_IMAGE_SHARE = 0.6;

    private final I18n i18n = I18n.getInstance();

    /** Dokument, Reiter, Rückgängig-Verlauf - siehe {@link DocumentSession}. */
    private DocumentSession session;

    /**
     * Fenster und Sitzung des Hauptfensters "Import/Export" - gesetzt von
     * {@link HelloApplication#start} über {@link #registerMainWindow(Stage)},
     * sobald die Bühne steht (bei {@code initialize()} gibt es noch keine).
     * Das Decoder- und das Systeme-Fenster kennen ihr eigenes Fenster nur als
     * Singleton (siehe deren {@code open}-Feld); für ihr Menü "Funktionen" →
     * "Import/Export" brauchen sie dagegen einen Weg zurück zum Hauptfenster,
     * das selbst kein solches Singleton-Muster hat - dafür sind diese beiden
     * statischen Felder da.
     */
    private static Stage mainStage;
    private static DocumentSession mainSession;

    /** Von {@link HelloApplication#start} aufgerufen, sobald die Bühne feststeht. */
    void registerMainWindow(Stage stage) {
        mainStage = stage;
        mainSession = session;
    }

    /** Für das Menü "Funktionen" der Decoder- und Systeme-Fenster. */
    static Stage getMainStage() {
        return mainStage;
    }

    /** Für das Menü "Funktionen" → "Decoder" des Systeme-Fensters (Nebensitzung, siehe {@link DocumentSession#attachTo}). */
    static DocumentSession getMainSession() {
        return mainSession;
    }

    /**
     * Holt das Hauptfenster in den Vordergrund (auch aus dem Symbol-Zustand)
     * - das Gegenstück zu {@code DecoderWindow.show}/{@code SystemsWindow.show}
     * für das Hauptfenster, das selbst kein Singleton-Öffnen kennt, weil es
     * immer existiert, solange das Programm läuft.
     */
    static void focusMainWindow() {
        if (mainStage == null) {
            return;
        }
        if (mainStage.isIconified()) {
            mainStage.setIconified(false);
        }
        if (!mainStage.isShowing()) {
            mainStage.show();
        }
        mainStage.toFront();
        mainStage.requestFocus();
    }

    @FXML
    private void initialize() {
        // Hauptfenster: alle Kategorien (null = TAB_DISPLAY_ORDER plus
        // unbekannte Kategorien aus der Datei).
        session = new DocumentSession(tabPane, null, this);

        openMenuItem.setGraphic(loadIcon("icons/open-icon.png", 16));
        saveMenuItem.setGraphic(loadIcon("icons/save-icon.png", 16));
        // Gleiches Öffnen-Symbol wie bei "Öffnen..." - das Untermenü
        // "Zuletzt verwendete öffnen" ist ja ebenfalls eine Öffnen-Aktion.
        recentFilesMenu.setGraphic(loadIcon("icons/open-icon.png", 16));

        // Ribbon: dieselben Aktionen wie im Datei-/Einstellungen-Menü, nur
        // als Symbol-Buttons für schnellen Zugriff. Aussagekräftig durch
        // Icon + Tooltip statt zusätzlichem Text, damit die Leiste kompakt
        // bleibt.
        openToolButton.setGraphic(loadIcon("icons/open-icon.png", 22));
        saveToolButton.setGraphic(loadIcon("icons/save-icon.png", 22));
        undoToolButton.setGraphic(loadIcon("icons/undo-icon.png", 22));
        redoToolButton.setGraphic(loadIcon("icons/redo-icon.png", 22));
        preferencesToolButton.setGraphic(loadIcon("icons/settings-icon.png", 22));

        exportSelectedToolButton.setStyle(STYLE_EXPORT_SELECTED);
        importCategoryToolButton.setStyle(STYLE_IMPORT_CATEGORY);

        initStartImage();

        i18n.addLanguageChangeListener(this::applyLanguage);
        applyLanguage();
        undoRedoStateChanged(false, false);
        updateRibbonState();
    }

    /**
     * Startbild: das Programmsymbol (die Dampflok) mittig in der leeren
     * Fenstermitte, solange keine Datei geladen ist. Die Größe ist an das
     * Fenster gebunden ({@value #START_IMAGE_SHARE} der kleineren Kante), das
     * Bild skaliert also beim Ziehen des Fensters mit. Sobald eine Datei
     * geladen wird, verschwindet es ({@link #documentChanged(boolean)}).
     * <p>
     * Quelle ist dieselbe app-icon.png wie für Fenster- und Taskleisten-
     * symbol - bewusst keine zweite Grafik, die getrennt gepflegt werden
     * müsste.
     */
    private void initStartImage() {
        try (InputStream in = HelloController.class.getResourceAsStream("app-icon.png")) {
            if (in == null) {
                startImage.setVisible(false);
                return;
            }
            startImage.setImage(new Image(in));
        } catch (Exception ex) {
            startImage.setVisible(false);
            return;
        }
        startImage.fitWidthProperty().bind(
                Bindings.min(centerPane.widthProperty(), centerPane.heightProperty())
                        .multiply(START_IMAGE_SHARE));
        startImage.fitHeightProperty().bind(startImage.fitWidthProperty());
        startImage.setVisible(!session.hasDocument());
    }

    /**
     * Startbild fuer die Zusatzfenster (Decoder: decoder-image.png, Systeme:
     * occupancy-image.png) - gleicher Stil und gleiches Groessenverhalten wie
     * die Dampflok im Hauptfenster ({@value #START_IMAGE_SHARE} der kleineren
     * Kante von {@code pane}). Mausdurchlaessig, damit es nichts verdeckt.
     * Rueckgabe null, wenn die Grafik fehlt.
     */
    static ImageView createStartImage(String resourcePath, javafx.scene.layout.Region pane) {
        try (InputStream in = HelloController.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return null;
            }
            ImageView view = new ImageView(new Image(in));
            view.setPreserveRatio(true);
            view.setSmooth(true);
            view.setMouseTransparent(true);
            view.fitWidthProperty().bind(
                    Bindings.min(pane.widthProperty(), pane.heightProperty()).multiply(START_IMAGE_SHARE));
            view.fitHeightProperty().bind(view.fitWidthProperty());
            return view;
        } catch (Exception ex) {
            return null;
        }
    }

    static ImageView loadIcon(String resourcePath, int size) {
        try (InputStream in = HelloController.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return null;
            }
            ImageView view = new ImageView(new Image(in));
            view.setFitWidth(size);
            view.setFitHeight(size);
            view.setPreserveRatio(true);
            return view;
        } catch (Exception ex) {
            return null;
        }
    }

    private void applyLanguage() {
        fileMenu.setText(i18n.t("menu.file"));
        openMenuItem.setText(i18n.t("menu.open"));
        saveMenuItem.setText(i18n.t("menu.saveAs"));
        closeFileMenuItem.setText(i18n.t("menu.closeFile"));
        exitMenuItem.setText(i18n.t("menu.exit"));
        recentFilesMenu.setText(i18n.t("menu.recentFiles"));
        loadBackupMenuItem.setText(i18n.t("menu.loadBackup"));
        exportFilesMenuItem.setText(i18n.t("menu.exportFiles"));
        refreshRecentFilesMenu();
        editMenu.setText(i18n.t("menu.edit"));
        undoMenuItem.setText(i18n.t("menu.undo"));
        redoMenuItem.setText(i18n.t("menu.redo"));
        centerWindowsMenuItem.setText(i18n.t("menu.centerWindows"));
        editExportSelectedMenuItem.setText(i18n.t("editor.exportSelected"));
        editImportCategoryMenuItem.setText(i18n.t("editor.import"));
        settingsMenu.setText(i18n.t("menu.settingsMenu"));
        preferencesMenuItem.setText(i18n.t("menu.preferences"));
        functionsMenu.setText(i18n.t("menu.functions"));
        functionsDecoderMenuItem.setText(i18n.t("menu.programPart", i18n.t("window.decoder")));
        functionsSystemsMenuItem.setText(i18n.t("menu.programPart", i18n.t("window.systems")));
        helpMenu.setText(i18n.t("menu.help"));
        helpMenuItem.setText(i18n.t("menu.helpItem"));
        manualMenuItem.setText(i18n.t("menu.manual"));
        updateMenuItem.setText(i18n.t("menu.updateItem"));
        aboutMenuItem.setText(i18n.t("menu.aboutItem"));

        openToolButton.setTooltip(new Tooltip(i18n.t("menu.open")));
        saveToolButton.setTooltip(new Tooltip(i18n.t("menu.saveAs")));
        undoToolButton.setTooltip(new Tooltip(i18n.t("menu.undo")));
        redoToolButton.setTooltip(new Tooltip(i18n.t("menu.redo")));
        preferencesToolButton.setTooltip(new Tooltip(i18n.t("menu.preferences")));

        // Die Import-/Export-Schaltflächen tragen ihren vollen Text (kein
        // Symbol), damit ohne Erklärung klar ist, was sie tun.
        exportSelectedToolButton.setText(i18n.t("editor.exportSelected"));
        importCategoryToolButton.setText(i18n.t("editor.import"));
        decoderWindowButton.setText(i18n.t("window.decoder"));
        decoderWindowButton.setTooltip(new Tooltip(i18n.t("window.decoderTooltip")));
        systemsWindowButton.setText(i18n.t("window.systems"));
        systemsWindowButton.setTooltip(new Tooltip(i18n.t("window.systemsTooltip")));

        if (!session.hasDocument()) {
            fileNameLabel.setText(i18n.t("status.noFileLoaded"));
            statusLabel.setText("");
        }
        session.rebuildTabs();
    }

    private Stage stageOf() {
        return (Stage) tabPane.getScene().getWindow();
    }

    // ------------------------------------------------------------------
    // DocumentSession.Host
    // ------------------------------------------------------------------

    @Override
    public Stage stage() {
        return stageOf();
    }

    @Override
    public void showFileName(String text) {
        fileNameLabel.setText(text);
    }

    @Override
    public void showStatus(String text) {
        statusLabel.setText(text);
    }

    @Override
    public void undoRedoStateChanged(boolean canUndo, boolean canRedo) {
        undoMenuItem.setDisable(!canUndo);
        redoMenuItem.setDisable(!canRedo);
        undoToolButton.setDisable(!canUndo);
        redoToolButton.setDisable(!canRedo);
    }

    @Override
    public void selectionChanged() {
        updateRibbonState();
    }

    @Override
    public void recentFilesChanged() {
        refreshRecentFilesMenu();
    }

    @Override
    public void documentChanged(boolean loaded) {
        startImage.setVisible(!loaded);
    }

    // ------------------------------------------------------------------
    // Menü Datei
    // ------------------------------------------------------------------

    @FXML
    private void onOpenFile() {
        session.openFileDialog();
    }

    @FXML
    private void onLoadBackup() {
        session.loadBackupDialog();
    }

    @FXML
    private void onOpenExportFiles() {
        session.importIntoAnyEditor();
    }

    @FXML
    private void onSaveAs() {
        session.saveAsDialog();
    }

    /** "Aktuelle schließen": Datei verwerfen, Programm bleibt offen. */
    @FXML
    private void onCloseFile() {
        session.closeDocument();
    }

    /**
     * "Programm beenden": löst dieselbe Schließanfrage aus wie das X des
     * Hauptfensters, damit HelloApplication seinen OnCloseRequest-Handler
     * durchläuft (Backup aufräumen, Zusatzfenster mitschließen) - und
     * schließt dann das Hauptfenster; mit dem letzten Fenster endet JavaFX.
     */
    @FXML
    private void onExit() {
        Stage stage = stageOf();
        stage.fireEvent(new javafx.stage.WindowEvent(stage, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST));
        stage.close();
    }

    /**
     * Baut den Inhalt des eigenständigen Untermenüs "Zuletzt verwendet..."
     * komplett neu auf: bis zu 5 zuletzt geöffnete Dateien, oder ein
     * deaktivierter Platzhalter-Eintrag, falls die Liste leer ist. Wird bei
     * Sprachwechsel (für die übersetzten Texte) sowie nach jeder Änderung
     * der Liste (neue/entfernte Datei) neu aufgerufen.
     */
    private void refreshRecentFilesMenu() {
        recentFilesMenu.getItems().clear();

        List<String> recentFiles = AppSettings.getInstance().getRecentFiles();
        if (recentFiles.isEmpty()) {
            MenuItem placeholder = new MenuItem(i18n.t("menu.recentFilesEmpty"));
            placeholder.setDisable(true);
            recentFilesMenu.getItems().add(placeholder);
        } else {
            for (String path : recentFiles) {
                MenuItem item = new MenuItem(path);
                item.setOnAction(e -> session.openRecentFile(path));
                recentFilesMenu.getItems().add(item);
            }
        }
    }

    /** Wird beim Schließen des Programmfensters aufgerufen (siehe {@link HelloApplication}). */
    public void onAppClosing() {
        session.onClosing();
    }

    // ------------------------------------------------------------------
    // Menü Bearbeiten / Einstellungen
    // ------------------------------------------------------------------

    @FXML
    private void onUndo() {
        session.undo();
    }

    @FXML
    private void onRedo() {
        session.redo();
    }

    /**
     * Bearbeiten → "Alle Fenster zentrieren": holt sämtliche offenen Fenster
     * und Dialoge auf den Bildschirm des Hauptfensters zurück. Gedacht als
     * Rettungsanker, wenn ein Fenster außer Sicht geraten ist - etwa weil es
     * auf einem inzwischen abgezogenen zweiten Bildschirm lag oder versehentlich
     * über den Rand geschoben wurde. Die eigentliche Arbeit macht
     * {@link WindowState#centerAll(Stage)}.
     */
    @FXML
    private void onCenterWindows() {
        int moved = WindowState.centerAll(stageOf());
        // Auch die gemerkte Verschiebung der Dialoge vergessen: Sonst käme
        // der nächste Dialog sofort wieder abseits heraus, obwohl der
        // Anwender gerade um das Gegenteil gebeten hat.
        DialogPlacement.resetOffset();
        statusLabel.setText(i18n.t("status.windowsCentered", moved));
    }

    @FXML
    private void onPreferences() {
        SettingsDialog.showPreferences(stageOf());
        // Spaltensichtbarkeit, "Daten ändern"-Bereich usw. werden erst beim
        // Aufbau eines CategoryEditor gelesen - nach Schließen des Dialogs
        // alle Reiter neu aufbauen, damit Änderungen sofort sichtbar werden
        // (wie beim Sprachwechsel).
        session.rebuildTabs();
        // Falls der Decoder-Ordner geändert wurde: mitgelieferte Vorlagen
        // dorthin übertragen (die Kennung enthält den Zielordner, bei
        // unverändertem Ordner passiert nichts).
        DecoderTemplateBundle.syncIfNeeded();
    }

    // ------------------------------------------------------------------
    // Ribbon: Import / Export / Decoder
    // ------------------------------------------------------------------

    /**
     * Schaltet die Import-/Export-Schaltflächen im Ribbon passend zum
     * sichtbaren Reiter: ohne geöffnete Datei sind beide gesperrt. Die
     * Fensterknöpfe "Decoder" und "Systeme" sind immer benutzbar - die
     * Fenster arbeiten unabhängig von der hier geladenen Datei.
     */
    private void updateRibbonState() {
        CategoryEditor editor = session.currentEditor();
        boolean hasCategory = editor != null;
        exportSelectedToolButton.setDisable(!hasCategory);
        importCategoryToolButton.setDisable(!hasCategory);
        editExportSelectedMenuItem.setDisable(!hasCategory);
        editImportCategoryMenuItem.setDisable(!hasCategory);
    }

    @FXML
    private void onExportSelected() {
        CategoryEditor editor = session.currentEditor();
        if (editor != null) {
            editor.triggerExport();
        }
    }

    @FXML
    private void onImportCategory() {
        CategoryEditor editor = session.currentEditor();
        if (editor != null) {
            editor.triggerImport();
        }
    }

    // ------------------------------------------------------------------
    // Ribbon: Zusatzfenster
    // ------------------------------------------------------------------

    /**
     * Knopf "Decoder": öffnet das Decoder-Fenster (oder holt es nach vorn,
     * falls es schon offen ist). Es arbeitet auf einer eigenen Datei - siehe
     * {@link DecoderWindow}.
     */
    @FXML
    private void onOpenDecoderWindow() {
        DecoderWindow.show(stageOf(), session);
    }

    /** Knopf "Systeme": öffnet das Systeme-Fenster - siehe {@link SystemsWindow}. */
    @FXML
    private void onOpenSystemsWindow() {
        SystemsWindow.show(stageOf());
    }

    // ------------------------------------------------------------------
    // Menü Hilfe
    // ------------------------------------------------------------------

    @FXML
    private void onHelp() {
        HelpDialog.show(stageOf());
    }

    /**
     * Menü Hilfe → "Handbuch": öffnet die Proton-Drive-Freigabe im Browser,
     * in der das Handbuch als PDF liegt.
     * <p>
     * Bewusst dieselbe Freigabe wie für das Programm selbst und die
     * Decoder-Vorlagen: Es gibt nur einen Ort, an dem der Autor Dateien
     * bereitstellt, und das Handbuch ändert sich mit jeder Version. Ein im
     * Programm mitgeliefertes PDF wäre dagegen ab der ersten Textänderung
     * veraltet - und würde das Laufzeitabbild unnötig vergrößern.
     * <p>
     * Wie beim Update lädt das Programm selbst nichts herunter und braucht
     * keinerlei Zugangsdaten; den Rest erledigt der Browser.
     */
    @FXML
    private void onOpenManual() {
        Stage stage = stageOf();
        UpdateChecker.resolveLinkAsync(UpdateChecker.Link.MANUAL, url -> {
            if (url == null) {
                // Beim allerersten Aufruf ohne Netz gibt es noch keine
                // gemerkte Adresse - dann wenigstens sagen, woran es liegt,
                // statt kommentarlos nichts zu tun.
                Alert alert = new Alert(Alert.AlertType.INFORMATION, i18n.t("update.linkUnavailable"));
                alert.initOwner(stage);
                alert.setHeaderText(null);
                alert.showAndWait();
                return;
            }
            UpdateDialog.openDownloadPage(stage, url);
        });
    }

    @FXML
    private void onAbout() {
        AboutDialog.show(stageOf());
    }

    /**
     * Menüpunkt Hilfe → Update: manuelle Prüfung, unabhängig von der
     * Einstellung "automatisch beim Start prüfen" (siehe
     * {@link HelloApplication#start}). Anders als der stille Start-Check
     * zeigt diese Variante IMMER ein Ergebnis an - auch "bereits aktuell"
     * oder eine Fehlermeldung (z.B. offline) - da der Nutzer die Prüfung hier
     * bewusst selbst ausgelöst hat. Der Menüpunkt wird während der laufenden
     * (kurzen) Netzwerk-Prüfung deaktiviert, damit kein Doppelklick zwei
     * Anfragen gleichzeitig auslöst.
     */
    @FXML
    private void onCheckForUpdate() {
        Stage stage = stageOf();
        updateMenuItem.setDisable(true);
        UpdateChecker.checkAsync(result -> {
            updateMenuItem.setDisable(false);
            if (!result.success) {
                UpdateDialog.showError(stage, result.errorMessage);
            } else if (result.updateAvailable) {
                UpdateDialog.showUpdateAvailable(stage, result);
            } else {
                UpdateDialog.showUpToDate(stage, result.currentVersion);
            }
        });
    }
}
