package com.example.itrain_import_export;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Das Fenster "Decoder" (seit 2.5): alles rund um Decoder-Konfigurationen
 * und -Vorlagen, aus dem Hauptfenster herausgelöst.
 * <p>
 * Es arbeitet auf <b>derselben</b> iTrain-Datei wie das Hauptfenster
 * (Nebensitzung, siehe {@link DocumentSession#attachTo}): Ist dort schon
 * eine Datei offen, zeigt dieses Fenster genau die; öffnet man hier eine,
 * erscheint sie auch im Hauptfenster. Gezeigt werden nur die beiden
 * Kategorien mit Decoder-Konfiguration: Lokomotiven und Wagen.
 * <p>
 * Aufbau wie das Hauptfenster: Menüzeile (Datei, Bearbeiten, Decoder, Hilfe),
 * Ribbon (Öffnen, Speichern, Rückgängig, Wiederholen | Decoder exportieren,
 * Decoder importieren, Decoder erfassen), Dateiname-Zeile, Reiter,
 * Statuszeile. Die Hilfe enthält nur die Decoder-Abschnitte
 * ({@link HelpDialog#showDecoder}); Voreinstellungen gelten programmweit
 * und bleiben im Hauptfenster.
 * <p>
 * Es gibt höchstens EIN Decoder-Fenster; ein zweiter Klick auf den Knopf
 * holt das offene nach vorn ({@link #show(Stage)}).
 */
public final class DecoderWindow implements DocumentSession.Host {

    /** Die beiden Kategorien mit {@code <configuration>}-Knoten, in dieser Reihenfolge. */
    private static final List<String> CATEGORIES = List.of("locomotives", "wagons");

    private static DecoderWindow open;

    private final I18n i18n = I18n.getInstance();
    private final Stage stage = new Stage();
    private final TabPane tabPane = new TabPane();
    /** Startbild ohne geladene Datei (siehe documentChanged), oder null. */
    private javafx.scene.image.ImageView startImage;
    private final Label fileNameLabel = new Label();
    private final Label statusLabel = new Label();
    private final DocumentSession session;

    private final Menu fileMenu = new Menu();
    private final MenuItem openMenuItem = new MenuItem();
    private final MenuItem saveMenuItem = new MenuItem();
    private final MenuItem closeFileMenuItem = new MenuItem();
    private final Menu recentFilesMenu = new Menu();
    private final MenuItem loadBackupMenuItem = new MenuItem();
    private final Menu editMenu = new Menu();
    private final MenuItem undoMenuItem = new MenuItem();
    private final MenuItem redoMenuItem = new MenuItem();
    /** Seit 2.5: dieselben Aktionen wie die pastellfarbenen Ribbon-Knöpfe, siehe {@link #updateRibbonState()}. */
    private final MenuItem editExportMenuItem = new MenuItem();
    private final MenuItem editImportMenuItem = new MenuItem();
    private final MenuItem editCaptureMenuItem = new MenuItem();
    /**
     * "Decoder": seit der Bereinigung der doppelten Einträge (Export/Import/
     * Erfassen stehen jetzt nur noch unter "Bearbeiten", siehe
     * {@link #editExportMenuItem}) bleiben hier nur die beiden
     * Vorlagen-Punkte.
     */
    private final Menu decoderMenu = new Menu();
    private final MenuItem templatesMenuItem = new MenuItem();
    private final MenuItem installMenuItem = new MenuItem();
    /** Seit 2.5: führt zu den beiden anderen Programmteilen, siehe {@link HelloController#focusMainWindow()}. */
    private final Menu functionsMenu = new Menu();
    private final MenuItem functionsImportExportMenuItem = new MenuItem();
    private final MenuItem functionsSystemsMenuItem = new MenuItem();
    private final Menu helpMenu = new Menu();
    private final MenuItem helpMenuItem = new MenuItem();

    private final Button openToolButton = new Button();
    private final Button saveToolButton = new Button();
    private final Button undoToolButton = new Button();
    private final Button redoToolButton = new Button();
    private final Button decoderExportToolButton = new Button();
    private final Button decoderImportToolButton = new Button();
    private final Button decoderCaptureToolButton = new Button();
    private final Button loadItrainButton = new Button();
    private final Button writeItrainButton = new Button();
    private final MenuItem loadItrainMenuItem = new MenuItem();
    private final MenuItem writeItrainMenuItem = new MenuItem();

    /** Öffnet das Fenster oder holt das bereits offene nach vorn. */
    public static void show(Stage mainStage, DocumentSession mainSession) {
        if (open != null && open.stage.isShowing()) {
            open.stage.toFront();
            open.stage.requestFocus();
            return;
        }
        open = new DecoderWindow(mainSession);
        open.stage.show();
    }

    /** Beim Beenden des Programms mit schließen (siehe {@link HelloApplication}). */
    public static void closeIfOpen() {
        if (open != null && open.stage.isShowing()) {
            open.stage.close();
        }
    }

    private DecoderWindow(DocumentSession mainSession) {
        session = new DocumentSession(tabPane, CATEGORIES, this);

        // --- Menüzeile -------------------------------------------------
        openMenuItem.setGraphic(HelloController.loadIcon("icons/open-icon.png", 16));
        openMenuItem.setOnAction(e -> session.openFileDialog());
        saveMenuItem.setGraphic(HelloController.loadIcon("icons/save-icon.png", 16));
        saveMenuItem.setOnAction(e -> session.saveAsDialog());
        recentFilesMenu.setGraphic(HelloController.loadIcon("icons/open-icon.png", 16));
        loadBackupMenuItem.setOnAction(e -> session.loadBackupDialog());
        closeFileMenuItem.setOnAction(e -> session.closeDocument());
        fileMenu.getItems().addAll(openMenuItem, saveMenuItem, closeFileMenuItem, new SeparatorMenuItem(),
                recentFilesMenu, loadBackupMenuItem);

        undoMenuItem.setOnAction(e -> session.undo());
        redoMenuItem.setOnAction(e -> session.redo());
        // Seit 2.5: dieselben Aktionen wie die drei pastellfarbenen
        // Ribbon-Knöpfe, hier zusätzlich als Menüpunkt - per Trennstrich von
        // Rückgängig/Wiederholen abgesetzt.
        editExportMenuItem.setOnAction(e -> onDecoderExport());
        editImportMenuItem.setOnAction(e -> onDecoderImport());
        editCaptureMenuItem.setOnAction(e -> onDecoderCapture());
        editMenu.getItems().addAll(undoMenuItem, redoMenuItem, new SeparatorMenuItem(),
                editExportMenuItem, editImportMenuItem, editCaptureMenuItem);

        // "Decoder": nur noch die beiden Vorlagen-Punkte - exportieren/
        // importieren/erfassen stehen (ohne Dopplung) unter "Bearbeiten",
        // siehe editExportMenuItem/editImportMenuItem/editCaptureMenuItem
        // weiter oben. Bis 2.0.1 standen die beiden Vorlagen-Punkte im
        // Hauptfenster unter "Einstellungen".
        templatesMenuItem.setOnAction(e -> DecoderTemplateBrowser.show(stage));
        installMenuItem.setOnAction(e -> DecoderTemplateInstaller.install(stage));
        decoderMenu.getItems().addAll(templatesMenuItem, installMenuItem);

        helpMenuItem.setOnAction(e -> HelpDialog.showDecoder(stage));
        helpMenu.getItems().add(helpMenuItem);

        // "Funktionen": führt zu den beiden anderen Programmteilen - mit
        // derselben Öffnen-oder-nach-vorn-Wirkung wie die Ribbon-Knöpfe des
        // Hauptfensters (siehe HelloController.focusMainWindow/
        // SystemsWindow.show).
        functionsImportExportMenuItem.setOnAction(e -> HelloController.focusMainWindow());
        functionsSystemsMenuItem.setOnAction(e -> SystemsWindow.show(HelloController.getMainStage()));
        functionsMenu.getItems().addAll(functionsImportExportMenuItem, functionsSystemsMenuItem);

        MenuBar menuBar = new MenuBar(fileMenu, editMenu, decoderMenu, functionsMenu, helpMenu);

        // --- Ribbon ----------------------------------------------------
        openToolButton.setGraphic(HelloController.loadIcon("icons/open-icon.png", 22));
        openToolButton.setOnAction(e -> session.openFileDialog());
        saveToolButton.setGraphic(HelloController.loadIcon("icons/save-icon.png", 22));
        saveToolButton.setOnAction(e -> session.saveAsDialog());
        undoToolButton.setGraphic(HelloController.loadIcon("icons/undo-icon.png", 22));
        undoToolButton.setOnAction(e -> session.undo());
        redoToolButton.setGraphic(HelloController.loadIcon("icons/redo-icon.png", 22));
        redoToolButton.setOnAction(e -> session.redo());

        decoderExportToolButton.setStyle(HelloController.STYLE_DECODER_EXPORT);
        decoderExportToolButton.setOnAction(e -> onDecoderExport());
        decoderImportToolButton.setStyle(HelloController.STYLE_DECODER_IMPORT);
        decoderImportToolButton.setOnAction(e -> onDecoderImport());
        decoderCaptureToolButton.setStyle(HelloController.STYLE_DECODER_CAPTURE);
        decoderCaptureToolButton.setOnAction(e -> onDecoderCapture());

        Region gapLeft = new Region();
        gapLeft.setMinWidth(10);
        Region gapRight = new Region();
        gapRight.setMinWidth(10);
        // "iTrain-Datei laden" / "In iTrain-Datei schreiben" wie im
        // Systeme-Fenster: es gibt EINE geladene iTrain-Datei fuer alle
        // Programmteile (die des Hauptfensters) - laden hier laedt sie
        // ueberall, schreiben speichert sie (mit Sicherung) in ihre Datei.
        loadItrainButton.setStyle("-fx-background-color: #fff3b0; -fx-text-fill: #2b2b2b;");
        loadItrainButton.setOnAction(e -> session.openFileDialog());
        writeItrainButton.setStyle("-fx-background-color: #fff3b0; -fx-text-fill: #2b2b2b;");
        writeItrainButton.setOnAction(e -> onWriteItrain());
        writeItrainButton.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                () -> !session.hasDocument(), fileNameLabel.textProperty()));
        loadItrainMenuItem.setOnAction(e -> session.openFileDialog());
        writeItrainMenuItem.setOnAction(e -> onWriteItrain());
        fileMenu.getItems().addAll(new SeparatorMenuItem(), loadItrainMenuItem, writeItrainMenuItem);
        Region gapItrain = new Region();
        gapItrain.setMinWidth(10);
        ToolBar ribbon = new ToolBar(
                openToolButton, saveToolButton, undoToolButton, redoToolButton,
                gapLeft, new Separator(javafx.geometry.Orientation.VERTICAL), gapRight,
                decoderExportToolButton, decoderImportToolButton, decoderCaptureToolButton,
                gapItrain, loadItrainButton, writeItrainButton);

        VBox.setMargin(fileNameLabel, new Insets(6, 10, 6, 10));
        VBox top = new VBox(menuBar, ribbon, fileNameLabel);

        statusLabel.setPadding(new Insets(4, 10, 4, 10));

        BorderPane root = new BorderPane();
        root.setTop(top);
        // Startbild (Decoder-Platine) wie die Dampflok im Hauptfenster:
        // mittig, mitwachsend, nur solange keine Datei geladen ist.
        javafx.scene.layout.StackPane center = new javafx.scene.layout.StackPane(tabPane);
        startImage = HelloController.createStartImage("decoder-image.png", center);
        if (startImage != null) {
            center.getChildren().add(startImage);
            startImage.setVisible(!session.hasDocument());
        }
        root.setCenter(center);
        root.setBottom(statusLabel);

        // --- Fenster ---------------------------------------------------
        // Bewusst OHNE initOwner(mainStage): Ein Fenster mit Besitzer läge
        // dauerhaft über dem Hauptfenster - für ein zweites Arbeitsfenster,
        // das man auch hinter das erste legen will, ist das falsch. Beim
        // Beenden des Programms wird es über closeIfOpen() mitgeschlossen.
        stage.getIcons().addAll(loadAppIcons());
        Scene scene = new Scene(root);
        ThemeManager.apply(scene, AppSettings.getInstance().getTheme());
        stage.setScene(scene);
        stage.setMinWidth(640);
        stage.setMinHeight(400);
        stage.setOnCloseRequest(event -> session.onClosing());

        // Sprachwechsel-Listener beim Schließen wieder abmelden - sonst
        // bliebe jedes je geöffnete Decoder-Fenster daran hängen. Der
        // Listener muss dafür als Referenz festgehalten werden;
        // this::applyLanguage ergäbe bei jedem Aufruf ein neues Objekt.
        // Muss VOR WindowState.apply stehen: das hängt seinen eigenen
        // OnHidden-Handler an den hier gesetzten an (joinHandler) - in
        // umgekehrter Reihenfolge würde er ersetzt und die Fensterlage
        // nicht mehr gespeichert.
        Runnable languageListener = this::applyLanguage;
        i18n.addLanguageChangeListener(languageListener);
        stage.setOnHidden(event -> {
            i18n.removeLanguageChangeListener(languageListener);
            if (open == this) {
                open = null;
            }
        });
        WindowState.apply(stage, "decoder", 1400, 800);
        applyLanguage();
        undoRedoStateChanged(false, false);
        // Zuletzt, wenn Menü/Ribbon stehen: an die Datei des Hauptfensters
        // hängen - baut die Reiter auf und setzt Dateiname und
        // Rückgängig-Zustand. Abgehängt wird in session.onClosing().
        session.attachTo(mainSession);
        updateRibbonState();
    }

    private void applyLanguage() {
        stage.setTitle(i18n.t("window.decoderTitle"));
        fileMenu.setText(i18n.t("menu.file"));
        openMenuItem.setText(i18n.t("menu.open"));
        saveMenuItem.setText(i18n.t("menu.saveAs"));
        closeFileMenuItem.setText(i18n.t("menu.closeFile"));
        recentFilesMenu.setText(i18n.t("menu.recentFiles"));
        loadBackupMenuItem.setText(i18n.t("menu.loadBackup"));
        refreshRecentFilesMenu();
        editMenu.setText(i18n.t("menu.edit"));
        undoMenuItem.setText(i18n.t("menu.undo"));
        redoMenuItem.setText(i18n.t("menu.redo"));
        editExportMenuItem.setText(i18n.t("editor.decoderExport"));
        editImportMenuItem.setText(i18n.t("editor.decoderImport"));
        editCaptureMenuItem.setText(i18n.t("menu.decoderCapture"));
        decoderMenu.setText(i18n.t("window.decoder"));
        templatesMenuItem.setText(i18n.t("menu.decoderTemplates"));
        installMenuItem.setText(i18n.t("menu.decoderInstall"));
        functionsMenu.setText(i18n.t("menu.functions"));
        functionsImportExportMenuItem.setText(i18n.t("menu.programPart", i18n.t("window.importExport")));
        functionsSystemsMenuItem.setText(i18n.t("menu.programPart", i18n.t("window.systems")));
        helpMenu.setText(i18n.t("menu.help"));
        helpMenuItem.setText(i18n.t("menu.helpItem"));

        openToolButton.setTooltip(new Tooltip(i18n.t("menu.open")));
        saveToolButton.setTooltip(new Tooltip(i18n.t("menu.saveAs")));
        undoToolButton.setTooltip(new Tooltip(i18n.t("menu.undo")));
        redoToolButton.setTooltip(new Tooltip(i18n.t("menu.redo")));
        decoderExportToolButton.setText(i18n.t("editor.decoderExport"));
        decoderImportToolButton.setText(i18n.t("editor.decoderImport"));
        decoderCaptureToolButton.setText(i18n.t("menu.decoderCapture"));
        loadItrainButton.setText(i18n.t("systems.loadItrain"));
        loadItrainButton.setTooltip(new Tooltip(i18n.t("decoder.loadItrainTooltip")));
        writeItrainButton.setText(i18n.t("systems.writeItrain"));
        writeItrainButton.setTooltip(new Tooltip(i18n.t("decoder.writeItrainTooltip")));
        loadItrainMenuItem.setText(i18n.t("systems.loadItrain"));
        writeItrainMenuItem.setText(i18n.t("systems.writeItrain"));

        if (!session.hasDocument()) {
            fileNameLabel.setText(i18n.t("status.noFileLoaded"));
            statusLabel.setText("");
        }
        session.rebuildTabs();
    }

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

    /**
     * Die beiden Decoder-Schaltflächen brauchen einen sichtbaren Reiter mit
     * Fahrzeugen; "Decoder erfassen" ist immer möglich - dort entsteht eine
     * Vorlagen-Datei, keine Änderung an der iTrain-Datei.
     */
    private void updateRibbonState() {
        CategoryEditor editor = session.currentEditor();
        boolean decoder = editor != null && editor.supportsDecoderConfiguration();
        decoderExportToolButton.setDisable(!decoder);
        decoderImportToolButton.setDisable(!decoder);
        editExportMenuItem.setDisable(!decoder);
        editImportMenuItem.setDisable(!decoder);
    }

    private void onDecoderExport() {
        CategoryEditor editor = session.currentEditor();
        if (editor != null && editor.supportsDecoderConfiguration()) {
            editor.triggerDecoderExport();
        }
    }

    private void onDecoderImport() {
        CategoryEditor editor = session.currentEditor();
        if (editor != null && editor.supportsDecoderConfiguration()) {
            editor.triggerDecoderImport();
        }
    }

    /**
     * "Decoder erfassen": öffnet das eigenständige Fenster zum Anlegen einer
     * neuen Decoder-Vorlage neben der Hersteller-Anleitung (siehe
     * {@link DecoderCaptureWindow}). Bewusst UNABHÄNGIG vom geöffneten
     * Dokument nutzbar - hier wird eine Vorlagen-Datei erstellt, keine
     * iTrain-Datei verändert.
     */
    /**
     * "In iTrain-Datei schreiben": die (in allen Programmteilen gemeinsame)
     * geladene iTrain-Datei nach Rueckfrage in ihre Datei speichern - vorher
     * wird eine Sicherung angelegt (siehe DocumentSession.saveToCurrentFile).
     */
    private void onWriteItrain() {
        TcdDocument doc = session.getDocument();
        if (doc == null || doc.getFile() == null) {
            return;
        }
        javafx.scene.control.ButtonType write = new javafx.scene.control.ButtonType(
                i18n.t("systems.writeItrain"), javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        javafx.scene.control.ButtonType abort = new javafx.scene.control.ButtonType(
                i18n.t("bidib.abortButton"), javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
        javafx.scene.control.Alert ask = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION,
                i18n.t("decoder.writeItrainConfirm", doc.getFile().getName()), write, abort);
        ask.initOwner(stage);
        ask.setHeaderText(null);
        ThemeManager.apply(ask.getDialogPane().getScene(), AppSettings.getInstance().getTheme());
        if (ask.showAndWait().orElse(abort) == write) {
            session.saveToCurrentFile();
        }
    }

    private void onDecoderCapture() {
        // Auch hier der Hinweistext, solange er nicht abgeschaltet wurde -
        // "Decoder erfassen" ist für viele der erste Kontakt mit der Funktion.
        if (!DecoderHintsDialog.confirm(stage)) {
            return;
        }
        DecoderCaptureWindow.show(stage);
    }

    // ------------------------------------------------------------------
    // DocumentSession.Host
    // ------------------------------------------------------------------

    @Override
    public Stage stage() {
        return stage;
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
        if (startImage != null) {
            startImage.setVisible(!loaded);
        }
    }

    private static Image[] loadAppIcons() {
        try (InputStream in = DecoderWindow.class.getResourceAsStream("app-icon.png")) {
            return in == null ? new Image[0] : new Image[]{new Image(in)};
        } catch (IOException ex) {
            return new Image[0];
        }
    }
}
