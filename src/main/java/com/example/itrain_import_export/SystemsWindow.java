package com.example.itrain_import_export;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Das Fenster "Systeme" (seit 2.5) - die Anbindung von Digitalsystemen, bisher
 * BiDiB (netBiDiB und seriell/USB). Der Arbeitsablauf:
 * <ol>
 * <li>"Verbindung" öffnet den Verbindungsdialog ({@link BidibConnectionDialog}),
 * "Auslesen" liest die Knoten der Verbindung mit Fortschrittsanzeige
 * ({@link BidibNodeReader}) und zeigt den Knotenbaum
 * ({@link BidibNodeTreeWindow}).</li>
 * <li>Im Knotenbaum "Speichern" legt eine System-Datei mit allem ab, was
 * das Interface geliefert hat ({@link BidibSystemFile}); "Auswahl
 * übernehmen" macht aus den angekreuzten Anschlüssen die iTrain-Objekte und
 * trägt sie hier ein ({@link #acceptObjects}).</li>
 * <li>Die Objekte stehen iTrain-konform in übereinander liegenden
 * Abschnitten - Schnittstelle, Booster, Rückmeldungen, Zubehör -, jeder nur,
 * wenn er Zeilen hat, mit eigenem Ankreuzkasten "Alles auswählen". Spalten:
 * Name im BiDiB-System | Ausgewählt | Name in iTrain | Typ | Länge |
 * Schnittstelle. Die Schnittstelle ist immer ausgewählt.</li>
 * <li>Bearbeitet wird nicht in der Tabelle, sondern im Bearbeitungsfenster
 * ({@link SystemsObjectDialog}): Zeile markieren, dann Eingabetaste,
 * Doppelklick oder der Knopf "Zeile bearbeiten" rechts über der Tabelle.</li>
 * <li>Das Menü "Datei" und die Ribbon-Knöpfe Öffnen/Speichern/Aktuelle
 * schließen arbeiten mit den System-Dateien.</li>
 * <li>"Markierte exportieren" schreibt die ausgewählten Zeilen (Haken in
 * "Ausgewählt", die Schnittstelle immer) je Kategorie als CSV, verpackt in
 * ein ZIP, in den Export-Ordner - genau das Format, das im Hauptfenster
 * "In Kategorie importieren" erwartet.</li>
 * </ol>
 * Höchstens ein Fenster; ein zweiter Klick holt das offene nach vorn.
 */
public final class SystemsWindow {

    /** Pastellfarben der Funktionsknöpfe. */
    private static final String STYLE_CONNECT =
            "-fx-background-color: #cfe2f7; -fx-text-fill: #2b2b2b;";
    private static final String STYLE_READ =
            "-fx-background-color: #c8e6c9; -fx-text-fill: #2b2b2b;";
    private static final String STYLE_RAWLOG =
            "-fx-background-color: #fde2c4; -fx-text-fill: #2b2b2b;";
    private static final String STYLE_EXPORT =
            "-fx-background-color: #f8d3dd; -fx-text-fill: #2b2b2b;";
    private static final String STYLE_EXPORT_ALL =
            "-fx-background-color: #f3c9d6; -fx-text-fill: #2b2b2b;";
    private static final String STYLE_CLEAR =
            "-fx-background-color: #e4e4e4; -fx-text-fill: #2b2b2b;";
    private static final String STYLE_MC2 =
            "-fx-background-color: #e0d6f5; -fx-text-fill: #2b2b2b;";

    /** Zeilenhoehe der Tabellen - so hoch, dass die Auswahlbox "Zuordnung in iTrain" lesbar hineinpasst. */
    private static final double ROW_HEIGHT = 32;

    /**
     * Seit 13.10.2026 mehrere Fenster: das Hauptfenster "Systeme" nimmt die
     * erste Verbindung auf, jede weitere Verbindung bekommt ein eigenes
     * Fenster mit eigenen Tabellen und eigener Statusleiste (Andre). Bedient
     * werden kann alles aus jedem Fenster.
     */
    private static SystemsWindow open;
    private static final List<SystemsWindow> WINDOWS = new ArrayList<>();
    /** Fenster, das zuletzt einen Knotenbaum geoeffnet hat - dorthin gehen die uebernommenen Objekte. */
    private static SystemsWindow treeTarget;
    private static boolean globalListenersInstalled;

    /**
     * Eine Verbindung, die einem Fenster gehoert: BiDiB (netBiDiB/USB), ECoS
     * oder mc2 ueber CGI (nur der Host). Dieselbe mc2 ueber netBiDiB und
     * ueber CGI landet im selben Fenster (gleicher Host).
     */
    private record Link(BidibConnection bidib, EcosConnection ecos, String cgiHost) {
        String host() {
            return bidib != null ? bidib.getHost() : ecos != null ? ecos.getHost() : cgiHost;
        }

        String name() {
            if (bidib != null) {
                return bidib.getName();
            }
            if (ecos != null) {
                return ecos.getName();
            }
            return I18n.getInstance().t("mc2.cgiStatus", cgiHost);
        }
    }

    /** Diesem Fenster zugeordnete Verbindungen (leer = keine). */
    private final List<Link> links = new ArrayList<>();
    /** Letzte Uebertragung in eine Zentrale (fuer Bearbeiten -> Rueckgaengig), oder null. */
    private CentralUndo lastTransfer;
    private final boolean isMain;

    private final I18n i18n = I18n.getInstance();
    private final AppSettings settings = AppSettings.getInstance();
    private final Stage stage = new Stage();

    private final Button openToolButton = new Button();
    private final Button saveToolButton = new Button();
    private final Button undoToolButton = new Button();
    private final Button redoToolButton = new Button();
    private final Button connectButton = new Button();
    private final Button readButton = new Button();
    /**
     * Auswahl des Digitalsystems fuer "Verbindung" und "Auslesen"
     * (Klappliste mit Dreieck): erst BiDiB, ECoS oder Tams mc2 waehlen, dann den Knopf
     * druecken.
     */
    private final javafx.scene.control.ComboBox<String> systemBox = new javafx.scene.control.ComboBox<>();
    private static final String SYSTEM_BIDIB = "BiDiB";
    private static final String SYSTEM_ECOS = "ESU ECoS";
    /** Seit 13.10.2026: Verbindung ueber CGI (wie Menue Systeme / mc2), Auslesen = Lokomotiven der mc2. */
    private static final String SYSTEM_MC2 = "Tams mc2";
    private final Button rawLogButton = new Button();
    /** Nur sichtbar, solange mindestens eine verbundene mc2 per FTP lesbar ist (siehe BidibConnection#isMc2LocoAvailable). */
    private final Button mc2LocoButton = new Button();
    /**
     * "Lokomotiven zur mc2 uebertragen" (seit 13.10.2026): wie
     * {@link #mc2LocoButton} nur bei lesbarer mc2, zusaetzlich nur bei
     * geladener iTrain-Datei - siehe {@link #writeMc2Locos()}.
     */
    private final Button mc2WriteButton = new Button();
    /** "ECoS Lokomotiven auslesen" (seit 13.10.2026): nur sichtbar bei bestehender ECoS-Verbindung. */
    private final Button ecosLocoButton = new Button();
    /** "Lokomotiven zur ECoS uebertragen" (seit 13.10.2026): ECoS-Verbindung und geladene iTrain-Datei. */
    private final Button ecosWriteButton = new Button();
    private final Button exportButton = new Button();
    private final Button exportAllButton = new Button();
    private final Button clearTablesButton = new Button();
    /** "iTrain-Datei laden": .tcd/.tcdz zum Abgleichen (Spalte "Zuordnung in iTrain"). */
    private final Button loadItrainButton = new Button();
    private final MenuItem loadItrainMenuItem = new MenuItem();
    /** "In iTrain-Datei schreiben": markierte Zeilen direkt in die geladene .tcdz (ohne Exportdateien). */
    private final Button writeItrainButton = new Button();
    private final MenuItem writeItrainMenuItem = new MenuItem();
    private static final String STYLE_ITRAIN =
            "-fx-background-color: #fff3b0; -fx-text-fill: #2b2b2b;";

    /**
     * Die gemeinsame iTrain-Datei aller Programmteile (Dokument der Sitzung
     * des Hauptfensters), oder null - siehe onMainDocumentChanged().
     */
    private TcdDocument matchDocument = mainDocument();
    /** Beobachter an der Hauptsitzung (beim Schliessen wieder abgemeldet). */
    private final Runnable mainDocumentListener = this::onMainDocumentChanged;
    /** Statuszeile Mitte: welche iTrain-Datei zum Abgleich geladen ist. */
    private final Label itrainFileLabel = new Label();
    /**
     * Auswahl der Spalte "Zuordnung in iTrain" je Kategorie: "Neu" plus die
     * Namen der vorhandenen Eintraege dieser Kategorie in der geladenen Datei.
     */
    private final Map<String, ObservableList<String>> matchChoices = new LinkedHashMap<>();

    /** Alle vorbereiteten iTrain-Objekte; die Abschnitte filtern daraus je Kategorie. */
    private final ObservableList<SystemsObject> objects = FXCollections.observableArrayList();

    /**
     * Ein Abschnitt je Kategorie: Kopfzeile (Ueberschrift, "Alles
     * auswaehlen", rechts "Zeile bearbeiten") und Tabelle - nur sichtbar
     * mit Zeilen.
     */
    private final class Section {
        final String category;
        final Label header = new Label();
        final CheckBox selectAllBox = new CheckBox();
        final Button editRowButton = new Button();
        final FilteredList<SystemsObject> rows;
        final TableView<SystemsObject> table = new TableView<>();
        final VBox box;

        Section(String category) {
            this.category = category;
            this.rows = new FilteredList<>(objects, o -> category.equals(o.getCategory()));
            header.setStyle("-fx-font-weight: bold;");
            table.setItems(rows);
            table.setEditable(true); // nur fuer den Ankreuzkasten "Ausgewaehlt"
            table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            table.setPlaceholder(new Label(""));
            table.setFixedCellSize(ROW_HEIGHT);
            // Frei veraenderbare Spalten (Anwenderwunsch): kein "constrained"
            // Verhalten mehr, das gezogene Breiten wieder umverteilt; jede
            // Breite wird je Abschnitt gemerkt (rememberColumnWidths).
            table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
            table.setStyle("-fx-border-color: derive(-fx-background, -25%); -fx-border-width: 1;");
            table.getColumns().setAll(buildColumns(category));
            rememberColumnWidths(category, table);
            // Hoehe passend zur Zeilenzahl - die Abschnitte liegen gemeinsam
            // in einer Bildlaufflaeche, jede Tabelle zeigt alle Zeilen.
            table.prefHeightProperty().bind(Bindings.size(rows).multiply(ROW_HEIGHT).add(ROW_HEIGHT + 6));
            table.minHeightProperty().bind(table.prefHeightProperty());
            table.maxHeightProperty().bind(table.prefHeightProperty());
            // Markierung nur in einem Abschnitt zugleich; der Knopf "Zeile
            // bearbeiten" erscheint nur mit markierter Zeile.
            table.getSelectionModel().getSelectedItems().addListener(
                    (javafx.collections.ListChangeListener<SystemsObject>) change -> {
                        if (!table.getSelectionModel().getSelectedItems().isEmpty()) {
                            for (Section other : sections) {
                                if (other != this) {
                                    other.table.getSelectionModel().clearSelection();
                                }
                            }
                        }
                        editRowButton.setVisible(table.getSelectionModel().getSelectedItem() != null);
                        updateState();
                    });
            // Eingabetaste und Doppelklick oeffnen das Bearbeitungsfenster,
            // Entf loescht die markierten Zeilen.
            table.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ENTER && table.getSelectionModel().getSelectedItem() != null) {
                    editSelected(this);
                    e.consume();
                } else if (e.getCode() == KeyCode.DELETE && !table.getSelectionModel().getSelectedItems().isEmpty()) {
                    deleteSelected();
                    e.consume();
                }
            });
            // Rechtsklick: Bearbeiten | Markierte exportieren | Loeschen.
            ContextMenu rowMenu = new ContextMenu();
            MenuItem editItem = new MenuItem();
            editItem.setOnAction(e -> editSelected(this));
            MenuItem exportItem = new MenuItem();
            exportItem.setOnAction(e -> onExportSelected());
            MenuItem deleteItem = new MenuItem();
            deleteItem.setOnAction(e -> deleteSelected());
            rowMenu.getItems().addAll(editItem, exportItem, new SeparatorMenuItem(), deleteItem);
            contextMenus.add(rowMenu); // Beschriftung in applyLanguage
            table.setRowFactory(tv -> {
                TableRow<SystemsObject> row = new TableRow<>();
                row.setOnMouseClicked(e -> {
                    if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && !row.isEmpty()) {
                        table.getSelectionModel().clearAndSelect(row.getIndex());
                        editSelected(this);
                    }
                });
                row.contextMenuProperty().bind(Bindings.when(row.emptyProperty())
                        .then((ContextMenu) null).otherwise(rowMenu));
                return row;
            });
            editRowButton.setVisible(false);
            editRowButton.setOnAction(e -> editSelected(this));
            // "Alles auswaehlen" wirkt nur auf diesen Abschnitt.
            selectAllBox.setOnAction(e -> {
                for (SystemsObject object : rows) {
                    object.setSelected(selectAllBox.isSelected());
                }
                table.refresh();
            });
            if (SystemsObject.CATEGORY_INTERFACES.equals(category)) {
                selectAllBox.setSelected(true);
                selectAllBox.setDisable(true);
            }
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox headerRow = new HBox(16, header, selectAllBox, spacer, editRowButton);
            headerRow.setAlignment(Pos.CENTER_LEFT);
            box = new VBox(4, headerRow, table);
            box.visibleProperty().bind(Bindings.isNotEmpty(rows));
            box.managedProperty().bind(box.visibleProperty());
        }
    }

    private final List<Section> sections = new ArrayList<>();
    /** Die Rechtsklick-Menues der Abschnitte (Beschriftung bei Sprachwechsel). */
    private final List<ContextMenu> contextMenus = new ArrayList<>();
    private final VBox sectionsBox = new VBox(14);

    /** Die geöffnete System-Datei, oder null. */
    private BidibSystemFile currentFile;

    private final Menu fileMenu = new Menu();
    private final MenuItem openMenuItem = new MenuItem();
    private final MenuItem saveMenuItem = new MenuItem();
    private final MenuItem closeFileMenuItem = new MenuItem();
    private final Menu editMenu = new Menu();
    private final MenuItem editUndoMenuItem = new MenuItem();
    private final MenuItem editRedoMenuItem = new MenuItem();
    private final MenuItem editRowMenuItem = new MenuItem();
    private final MenuItem editExportMenuItem = new MenuItem();
    private final MenuItem editExportAllMenuItem = new MenuItem();
    private final MenuItem editDeleteRowMenuItem = new MenuItem();
    private final MenuItem editClearTablesMenuItem = new MenuItem();
    private final Menu systemsMenu = new Menu();
    private final Menu bidibMenu = new Menu();
    private final MenuItem bidibConnectMenuItem = new MenuItem();
    private final MenuItem bidibReadMenuItem = new MenuItem();
    private final MenuItem bidibLoadFileMenuItem = new MenuItem();
    private final Menu bidibRawLogMenu = new Menu();
    /** Seit 13.10.2026: mc2 ueber cgi (unabhaengig von netBiDiB), siehe {@link Mc2CgiConnection}. */
    private final Menu mc2Menu = new Menu();
    private final MenuItem mc2CgiConnectMenuItem = new MenuItem();
    private final MenuItem mc2CgiDisconnectMenuItem = new MenuItem();
    private final Menu esuMenu = new Menu();
    private final MenuItem esuConnectMenuItem = new MenuItem();
    private final MenuItem esuReadMenuItem = new MenuItem();
    private final Menu maerklinMenu = new Menu();
    private final Menu z21Menu = new Menu();
    private final Menu lokstoreMenu = new Menu();
    private final Menu zimoMenu = new Menu();
    private final Menu[] placeholderSystemMenus = {maerklinMenu, z21Menu, lokstoreMenu, zimoMenu};
    private final String[] placeholderSystemKeys =
            {"systems.maerklin", "systems.z21", "systems.lokstore", "systems.zimo"};
    private final HBox bidibStatusBox = new HBox(12);
    private final Label fileLabel = new Label();
    private final Label emptyLabel = new Label();
    private final Menu functionsMenu = new Menu();
    private final MenuItem functionsImportExportMenuItem = new MenuItem();
    private final MenuItem functionsDecoderMenuItem = new MenuItem();
    private final Menu helpMenu = new Menu();
    private final MenuItem helpMenuItem = new MenuItem();

    /** Oeffnet das Hauptfenster "Systeme" oder holt es nach vorn. */
    public static void show(Stage mainStage) {
        installGlobalListeners();
        if (open != null && open.stage.isShowing()) {
            open.stage.toFront();
            open.stage.requestFocus();
            return;
        }
        open = new SystemsWindow(true);
        WINDOWS.add(open);
        open.stage.show();
        assignConnections();
    }

    /** Beim Beenden des Programms: alle Systeme-Fenster ohne Rückfrage schließen (siehe HelloApplication). */
    public static void closeIfOpen() {
        for (SystemsWindow window : new ArrayList<>(WINDOWS)) {
            if (window.stage.isShowing()) {
                window.stage.setOnCloseRequest(null);
                window.stage.close();
            }
        }
    }

    /** Einmalig: auf neue/geschlossene Verbindungen aller Systeme hoeren. */
    private static void installGlobalListeners() {
        if (globalListenersInstalled) {
            return;
        }
        globalListenersInstalled = true;
        BidibConnectionManager.getInstance().getConnections().addListener(
                (javafx.collections.ListChangeListener<BidibConnection>) change -> assignConnections());
        EcosConnection.getConnections().addListener(
                (javafx.collections.ListChangeListener<EcosConnection>) change -> assignConnections());
        Mc2CgiConnection.getInstance().hostProperty().addListener((obs, old, value) -> assignConnections());
    }

    /** Alle derzeit offenen Verbindungen als Links. */
    private static List<Link> currentLinks() {
        List<Link> result = new ArrayList<>();
        for (BidibConnection connection : BidibConnectionManager.getInstance().getConnections()) {
            result.add(new Link(connection, null, null));
        }
        for (EcosConnection connection : EcosConnection.getConnections()) {
            result.add(new Link(null, connection, null));
        }
        Mc2CgiConnection cgi = Mc2CgiConnection.getInstance();
        if (cgi.isConnected()) {
            result.add(new Link(null, null, cgi.getHost()));
        }
        return result;
    }

    /**
     * Verbindungen auf die Fenster verteilen (Andre, 13.10.2026): die erste
     * ins Hauptfenster, jede weitere in ein eigenes Fenster - ausser sie
     * gehoert zu einer Zentrale, die schon ein Fenster hat (mc2 ueber
     * netBiDiB und CGI). Geschlossene Verbindungen verschwinden aus ihrem
     * Fenster; das Fenster bleibt mit seinen Tabellen offen.
     */
    private static void assignConnections() {
        WINDOWS.removeIf(window -> !window.stage.isShowing());
        if (WINDOWS.isEmpty()) {
            return;
        }
        List<Link> current = currentLinks();
        for (SystemsWindow window : WINDOWS) {
            window.links.removeIf(link -> !current.contains(link));
        }
        for (Link link : current) {
            if (WINDOWS.stream().anyMatch(w -> w.links.contains(link))) {
                continue;
            }
            SystemsWindow target = null;
            for (SystemsWindow window : WINDOWS) {
                if (link.host() != null && window.links.stream().anyMatch(l -> link.host().equals(l.host()))) {
                    target = window;
                    break;
                }
            }
            if (target == null && open != null && open.stage.isShowing() && open.links.isEmpty()) {
                target = open;
            }
            if (target == null) {
                target = new SystemsWindow(false);
                WINDOWS.add(target);
                target.links.add(link);
                target.stage.show();
                continue;
            }
            target.links.add(link);
        }
        for (SystemsWindow window : WINDOWS) {
            window.rebuildBidibUi();
        }
    }

    /**
     * Vom Knotenbaum: übernimmt die erzeugten Objekte in die Abschnitte. Ist
     * noch keine System-Datei geöffnet, wird die Quelle des Baums zur
     * aktuellen; eine bereits vorhandene Schnittstelle gleichen Namens wird
     * ersetzt, alles andere angehängt (doppelte iTrain-Kennungen übersprungen).
     */
    static void acceptObjects(BidibSystemFile source, List<SystemsObject> newObjects) {
        if (treeTarget != null && treeTarget.stage.isShowing()) {
            treeTarget.acceptObjectsHere(source, newObjects);
            return;
        }
        if (open == null || !open.stage.isShowing()) {
            show(HelloController.getMainStage());
        }
        open.acceptObjectsHere(source, newObjects);
    }

    private void acceptObjectsHere(BidibSystemFile source, List<SystemsObject> newObjects) {
        SystemsWindow open = this;
        open.stage.toFront();
        if (open.currentFile == null) {
            open.currentFile = source;
        }
        int added = 0;
        List<SystemsObject> addedObjects = new ArrayList<>();
        for (SystemsObject object : newObjects) {
            if (object.isInterface()) {
                open.objects.removeIf(existing -> existing.isInterface()
                        && existing.getName().equals(object.getName()));
                open.objects.add(0, object);
                addedObjects.add(object);
                added++;
                continue;
            }
            if (open.findByItrainId(object) != null) {
                continue;
            }
            open.objects.add(object);
            addedObjects.add(object);
            added++;
        }
        // Ist schon eine iTrain-Datei zum Abgleich geladen (z.B. VOR dem
        // Auslesen), die neuen Zeilen gleich zuordnen - vorher standen sie
        // alle auf "Neu" und es sah aus, als waere die Datei wieder weg.
        int matched = open.matchDocument != null ? open.autoMatch(addedObjects) : 0;
        open.refreshTables();
        open.updateFileLabel();
        open.updateState();
        open.fileLabel.setText(open.i18n.t("systems.objectsAccepted", added)
                + (open.matchDocument != null ? "  " + open.i18n.t("systems.objectsMatched", matched) : ""));
    }

    /** Vom Knotenbaum nach dem Speichern: Öffnen-Knopf ggf. freigeben. */
    static void systemFilesChanged() {
        for (SystemsWindow window : WINDOWS) {
            window.updateState();
        }
    }

    private SystemsObject findByItrainId(SystemsObject candidate) {
        String id = itrainId(candidate);
        if (id == null) {
            return null;
        }
        for (SystemsObject existing : objects) {
            if (id.equals(itrainId(existing))) {
                return existing;
            }
        }
        return null;
    }

    private static String itrainId(SystemsObject object) {
        if (object.getXml() == null) {
            return null;
        }
        XmlNode id = object.getXml().findChild("id");
        return id != null ? id.getTextContent() : null;
    }

    private SystemsWindow(boolean isMain) {
        this.isMain = isMain;
        // --- Menüzeile -------------------------------------------------
        openMenuItem.setGraphic(HelloController.loadIcon("icons/open-icon.png", 16));
        saveMenuItem.setGraphic(HelloController.loadIcon("icons/save-icon.png", 16));
        openMenuItem.setOnAction(e -> onOpenSystemFile());
        saveMenuItem.setOnAction(e -> onSaveSystemFile());
        closeFileMenuItem.setOnAction(e -> onCloseSystemFile());
        loadItrainMenuItem.setOnAction(e -> onLoadItrainFile());
        writeItrainMenuItem.setOnAction(e -> onWriteToItrain());
        fileMenu.getItems().addAll(openMenuItem, saveMenuItem, closeFileMenuItem,
                new SeparatorMenuItem(), loadItrainMenuItem, writeItrainMenuItem);

        bidibConnectMenuItem.setOnAction(e -> BidibConnectionDialog.show(stage));
        bidibReadMenuItem.setOnAction(e -> openNodeTree());
        // "RX/TX anzeigen": Untermenue mit einem Eintrag je offener Verbindung
        // (wird in rebuildBidibUi gefuellt) - je Verbindung ein eigenes
        // Fenster mit dem gesamten Datenverkeh (siehe BidibRawLogWindow).
        // "System-Datei laden": Simulationsdatei des BiDiB-Wizard (.xml) oder
        // eine eigene System-Datei - in beiden Faellen der Knotenbaum.
        bidibLoadFileMenuItem.setOnAction(e -> loadSystemFileToTree(BidibSystemFile.SYSTEM_BIDIB));
        bidibMenu.getItems().addAll(bidibConnectMenuItem, bidibReadMenuItem, bidibLoadFileMenuItem,
                new SeparatorMenuItem(), bidibRawLogMenu);
        // ESU ECoS: Verbindung (nur IP) und Auslesen ueber das ECoS-Netzprotokoll.
        // Kein "System-Datei laden" hier (anders als bei BiDiB): ECoS hat keine
        // vom Wizard erzeugte Simulationsdatei, auf die das zuträfe.
        esuConnectMenuItem.setOnAction(e -> EcosConnectionDialog.show(stage));
        esuReadMenuItem.setOnAction(e -> readEcos());
        esuMenu.getItems().addAll(esuConnectMenuItem, esuReadMenuItem);
        for (Menu menu : placeholderSystemMenus) {
            MenuItem connect = new MenuItem();
            MenuItem read = new MenuItem();
            connect.setDisable(true);
            read.setDisable(true);
            menu.getItems().addAll(connect, read);
            menu.setDisable(true);
        }
        mc2CgiConnectMenuItem.setOnAction(e -> Mc2CgiConnection.showConnectDialog(stage));
        mc2CgiDisconnectMenuItem.setOnAction(e -> Mc2CgiConnection.getInstance().disconnect());
        mc2Menu.getItems().addAll(mc2CgiConnectMenuItem, mc2CgiDisconnectMenuItem);
        systemsMenu.getItems().addAll(bidibMenu, mc2Menu, esuMenu, maerklinMenu, z21Menu, lokstoreMenu, zimoMenu);
        helpMenu.getItems().add(helpMenuItem);
        helpMenuItem.setOnAction(e -> HelpDialog.showSystems(stage));

        // Rueckgaengig: die letzte Uebertragung in eine Zentrale (mc2/ECoS)
        // aus der Sicherung auf dem PC zuruecknehmen.
        editUndoMenuItem.setOnAction(e -> undoTransfer());
        editRedoMenuItem.setDisable(true);
        editRowMenuItem.setOnAction(e -> editSelected(null));
        editExportMenuItem.setOnAction(e -> onExportSelected());
        editExportAllMenuItem.setOnAction(e -> onExportAll());
        editDeleteRowMenuItem.setOnAction(e -> deleteSelected());
        editClearTablesMenuItem.setOnAction(e -> clearTables());
        editMenu.getItems().addAll(editUndoMenuItem, editRedoMenuItem, new SeparatorMenuItem(),
                editRowMenuItem, editExportMenuItem, editExportAllMenuItem, new SeparatorMenuItem(),
                editDeleteRowMenuItem, editClearTablesMenuItem);

        functionsImportExportMenuItem.setOnAction(e -> HelloController.focusMainWindow());
        functionsDecoderMenuItem.setOnAction(e ->
                DecoderWindow.show(HelloController.getMainStage(), HelloController.getMainSession()));
        functionsMenu.getItems().addAll(functionsImportExportMenuItem, functionsDecoderMenuItem);

        MenuBar menuBar = new MenuBar(fileMenu, editMenu, systemsMenu, functionsMenu, helpMenu);

        // --- Ribbon ----------------------------------------------------
        openToolButton.setGraphic(HelloController.loadIcon("icons/open-icon.png", 22));
        saveToolButton.setGraphic(HelloController.loadIcon("icons/save-icon.png", 22));
        undoToolButton.setGraphic(HelloController.loadIcon("icons/undo-icon.png", 22));
        redoToolButton.setGraphic(HelloController.loadIcon("icons/redo-icon.png", 22));
        openToolButton.setOnAction(e -> onOpenSystemFile());
        saveToolButton.setOnAction(e -> onSaveSystemFile());
        undoToolButton.setOnAction(e -> undoTransfer());
        redoToolButton.setDisable(true);

        connectButton.setStyle(STYLE_CONNECT);
        readButton.setStyle(STYLE_READ);
        rawLogButton.setStyle(STYLE_RAWLOG);
        rawLogButton.setOnAction(e -> chooseConnection(rawLogButton,
                connection -> BidibRawLogWindow.show(stage, connection)));
        mc2LocoButton.setStyle(STYLE_MC2);
        mc2LocoButton.setOnAction(e -> readMc2Locos());
        mc2LocoButton.setVisible(false);
        mc2LocoButton.setManaged(false);
        mc2WriteButton.setStyle(STYLE_MC2);
        mc2WriteButton.setOnAction(e -> writeMc2Locos());
        mc2WriteButton.setVisible(false);
        mc2WriteButton.setManaged(false);
        ecosLocoButton.setStyle(STYLE_MC2);
        ecosLocoButton.setOnAction(e -> readEcosLocos());
        ecosLocoButton.setVisible(false);
        ecosLocoButton.setManaged(false);
        ecosWriteButton.setStyle(STYLE_MC2);
        ecosWriteButton.setOnAction(e -> writeEcosLocos());
        ecosWriteButton.setVisible(false);
        ecosWriteButton.setManaged(false);
        exportButton.setStyle(STYLE_EXPORT);
        exportAllButton.setStyle(STYLE_EXPORT_ALL);
        clearTablesButton.setStyle(STYLE_CLEAR);
        systemBox.getItems().setAll(SYSTEM_BIDIB, SYSTEM_ECOS, SYSTEM_MC2);
        systemBox.setValue(SYSTEM_BIDIB);
        systemBox.valueProperty().addListener((obs, old, value) -> updateState());
        connectButton.setOnAction(e -> {
            if (SYSTEM_ECOS.equals(systemBox.getValue())) {
                EcosConnectionDialog.show(stage);
            } else if (SYSTEM_MC2.equals(systemBox.getValue())) {
                Mc2CgiConnection.showConnectDialog(stage);
            } else {
                BidibConnectionDialog.show(stage);
            }
        });
        // Auslesen (zweite Reihe): richtet sich nach der Verbindung DIESES
        // Fensters - BiDiB: Knotenbaum, ECoS: Bestand, mc2 nur ueber CGI:
        // Lokomotiven; ohne Verbindung der Knotenbaum der System-Datei.
        readButton.setOnAction(e -> {
            if (hasBidibLink()) {
                openNodeTree();
            } else if (!ecosLinks().isEmpty()) {
                readEcos();
            } else if (cgiHost() != null) {
                readMc2Locos(new Mc2Target(i18n.t("mc2.cgiStatus", cgiHost()), cgiHost(), "mc2"));
            } else {
                openNodeTree();
            }
        });
        loadItrainButton.setStyle(STYLE_ITRAIN);
        loadItrainButton.setOnAction(e -> onLoadItrainFile());
        writeItrainButton.setStyle(STYLE_ITRAIN);
        writeItrainButton.setOnAction(e -> onWriteToItrain());
        exportButton.setOnAction(e -> onExportSelected());
        exportAllButton.setOnAction(e -> onExportAll());
        clearTablesButton.setOnAction(e -> clearTables());

        // Zwei Reihen (Andre, 13.10.2026): oben nur Datei/Rueckgaengig,
        // Verbindung und iTrain-Datei; darunter Auslesen, die Knoepfe der
        // verbundenen Zentrale, Export und Tabellen leeren.
        Region gapLeft = new Region();
        gapLeft.setMinWidth(30);
        Region gapRight = new Region();
        gapRight.setMinWidth(10);
        Region gapItrain = new Region();
        gapItrain.setMinWidth(20);
        ToolBar ribbon = new ToolBar(
                openToolButton, saveToolButton, undoToolButton, redoToolButton,
                gapLeft, new Separator(Orientation.VERTICAL), gapRight,
                systemBox, connectButton,
                gapItrain, new Separator(Orientation.VERTICAL), loadItrainButton, writeItrainButton);
        Region gapExport = new Region();
        gapExport.setMinWidth(20);
        Region gapClear = new Region();
        gapClear.setMinWidth(20);
        ToolBar ribbon2 = new ToolBar(
                readButton, rawLogButton, mc2LocoButton, mc2WriteButton, ecosLocoButton, ecosWriteButton,
                gapExport, new Separator(Orientation.VERTICAL), exportButton, exportAllButton,
                gapClear, clearTablesButton);

        // --- Abschnitte ------------------------------------------------
        for (String category : SystemsObject.CATEGORY_ORDER) {
            Section section = new Section(category);
            sections.add(section);
            sectionsBox.getChildren().add(section.box);
        }
        emptyLabel.setStyle("-fx-text-fill: derive(-fx-text-background-color, 35%);");
        emptyLabel.visibleProperty().bind(Bindings.isEmpty(objects));
        emptyLabel.managedProperty().bind(emptyLabel.visibleProperty());
        sectionsBox.getChildren().add(0, emptyLabel);
        sectionsBox.setPadding(new Insets(10));
        ScrollPane scroll = new ScrollPane(sectionsBox);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        objects.addListener((javafx.collections.ListChangeListener<SystemsObject>) change -> updateState());

        // Statusleiste: links die geoeffnete Datei, rechts je Verbindung
        // Name und Kreis (gruen = verbunden, rot = getrennt).
        fileLabel.setPadding(new Insets(4, 10, 4, 10));
        bidibStatusBox.setAlignment(Pos.CENTER_RIGHT);
        bidibStatusBox.setPadding(new Insets(4, 10, 4, 10));

        BorderPane statusBar = new BorderPane();
        statusBar.setLeft(fileLabel);
        statusBar.setRight(bidibStatusBox);
        // Mitte: geladene iTrain-Datei zum Abgleich - bleibt sichtbar, auch
        // wenn links Meldungen wie "n Objekte uebernommen" erscheinen.
        itrainFileLabel.setStyle("-fx-font-weight: bold;");
        itrainFileLabel.setPadding(new Insets(4, 10, 4, 10));
        statusBar.setCenter(itrainFileLabel);
        updateItrainFileLabel();

        BorderPane root = new BorderPane();
        root.setTop(new VBox(menuBar, ribbon, ribbon2));
        // Startbild (Besetztmelder) wie die Dampflok im Hauptfenster: mittig,
        // mitwachsend, nur solange die Tabellen leer sind.
        javafx.scene.layout.StackPane center = new javafx.scene.layout.StackPane(scroll);
        javafx.scene.image.ImageView startImage = HelloController.createStartImage("occupancy-image.png", center);
        if (startImage != null) {
            startImage.visibleProperty().bind(Bindings.isEmpty(objects));
            center.getChildren().add(startImage);
        }
        root.setCenter(center);
        root.setBottom(statusBar);

        // --- Fenster ---------------------------------------------------
        stage.getIcons().addAll(loadAppIcons());
        Scene scene = new Scene(root);
        ThemeManager.apply(scene, settings.getTheme());
        stage.setScene(scene);
        stage.setMinWidth(760);
        stage.setMinHeight(300);

        Runnable languageListener = this::applyLanguage;
        i18n.addLanguageChangeListener(languageListener);
        // Die gemeinsame iTrain-Datei beobachten (laden/schliessen/rueckgaengig
        // in irgendeinem Programmteil) - siehe onMainDocumentChanged().
        DocumentSession mainSession = HelloController.getMainSession();
        if (mainSession != null) {
            mainSession.addDocumentListener(mainDocumentListener);
        }
        stage.setOnHidden(event -> {
            i18n.removeLanguageChangeListener(languageListener);
            if (mainSession != null) {
                mainSession.removeDocumentListener(mainDocumentListener);
            }
            WINDOWS.remove(this);
            if (open == this) {
                open = null;
            }
            if (treeTarget == this) {
                treeTarget = null;
            }
        });
        // Offene Verbindungen (netBiDiB und USB) halten Faeden der
        // Bibliothek am Leben - beim Schliessen nachfragen und die
        // Verbindungen DIESES Fensters trennen.
        stage.setOnCloseRequest(event -> {
            if (!confirmDisconnect()) {
                event.consume();
            }
        });
        if (isMain) {
            WindowState.apply(stage, "systems", 1000, 520);
        } else {
            WindowState.apply(stage, "systemsConnection", 1000, 520);
            // Leicht versetzt zum Hauptfenster, damit man sieht, dass es zwei sind.
            if (open != null && open.stage.isShowing()
                    && Math.abs(stage.getX() - open.stage.getX()) < 20
                    && Math.abs(stage.getY() - open.stage.getY()) < 20) {
                stage.setX(open.stage.getX() + 40);
                stage.setY(open.stage.getY() + 40);
            }
        }
        applyLanguage();
        rebuildBidibUi();
        updateState();
    }

    // ------------------------------------------------------------------
    // Spalten der Abschnitte
    // ------------------------------------------------------------------

    /**
     * BiDiB-Name | Ausgewählt | Name in iTrain | Typ | Länge | Schnittstelle -
     * alles nur Anzeige bis auf den Ankreuzkasten. Die Länge gibt es nur
     * bei Rückmeldern (Abschnittslänge in cm).
     */
    private List<TableColumn<SystemsObject, ?>> buildColumns(String category) {
        List<TableColumn<SystemsObject, ?>> columns = new ArrayList<>();

        TableColumn<SystemsObject, String> bidibColumn = new TableColumn<>();
        bidibColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getBidibName()));
        bidibColumn.setPrefWidth(220);
        bidibColumn.setEditable(false);
        bidibColumn.setReorderable(false);
        bidibColumn.setUserData("systems.colBidibName");
        columns.add(bidibColumn);

        TableColumn<SystemsObject, Boolean> selectedColumn = new TableColumn<>();
        selectedColumn.setCellValueFactory(cell -> cell.getValue().selectedProperty());
        boolean isInterface = SystemsObject.CATEGORY_INTERFACES.equals(category);
        selectedColumn.setCellFactory(col -> {
            CheckBoxTableCell<SystemsObject, Boolean> cell = new CheckBoxTableCell<>();
            // Die Schnittstelle ist immer dabei - Haken fest.
            cell.setDisable(isInterface);
            return cell;
        });
        selectedColumn.setPrefWidth(90);
        selectedColumn.setMaxWidth(110);
        selectedColumn.setReorderable(false);
        selectedColumn.setEditable(!isInterface);
        selectedColumn.setUserData("systems.colSelected");
        columns.add(selectedColumn);

        columns.add(textColumn("systems.colNameItrain", SystemsObject::nameProperty, 200));
        // Beschreibung: landet beim Import im gleichnamigen iTrain-Feld
        // (<description>, siehe SystemsObject.applyDescription). Bekommt den
        // freien Platz der Tabelle (siehe unten: Typ/Schnittstelle schmal).
        columns.add(textColumn("systems.colDescription", SystemsObject::descriptionProperty, 220));

        // Zuordnung zu einem vorhandenen Eintrag der geladenen iTrain-Datei
        // ("iTrain-Datei laden") - "Neu" oder ein vorhandener Name. Die
        // einzige direkt in der Tabelle bearbeitbare Spalte neben "Ausgewaehlt".
        TableColumn<SystemsObject, String> matchColumn = new TableColumn<>();
        matchColumn.setCellValueFactory(cell -> {
            String name = cell.getValue().matchNameProperty().get();
            return new ReadOnlyStringWrapper(name == null || name.isEmpty() ? i18n.t("systems.matchNew") : name);
        });
        matchColumn.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            private final javafx.scene.control.ComboBox<String> box = new javafx.scene.control.ComboBox<>();
            {
                box.setMaxWidth(Double.MAX_VALUE);
                // Kompakt, aber mit voller Schrifthoehe: vorher schnitt die
                // feste Zeilenhoehe den Text der Box unten ab.
                box.setPrefHeight(ROW_HEIGHT - 4);
                box.setMinHeight(ROW_HEIGHT - 4);
                box.setMaxHeight(ROW_HEIGHT - 4);
                box.setStyle("-fx-padding: 0 2 0 2;");
                setStyle("-fx-padding: 1 2 1 2;");
                box.setOnAction(e -> {
                    SystemsObject object = getTableRow() == null ? null : getTableRow().getItem();
                    if (object != null && box.getValue() != null) {
                        applyMatch(object, box.getValue());
                    }
                });
            }

            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                SystemsObject object = getTableRow() == null ? null : getTableRow().getItem();
                if (empty || object == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                if (matchDocument == null) {
                    // Ohne geladene iTrain-Datei gibt es nichts zuzuordnen.
                    setGraphic(null);
                    setText(i18n.t("systems.matchNew"));
                    return;
                }
                box.setItems(matchChoices.getOrDefault(object.getCategory(), FXCollections.observableArrayList()));
                box.setValue(value);
                setText(null);
                setGraphic(box);
            }
        });
        matchColumn.setPrefWidth(190);
        matchColumn.setReorderable(false);
        matchColumn.setSortable(false);
        matchColumn.setUserData("systems.colMatch");
        columns.add(matchColumn);

        TableColumn<SystemsObject, String> typeColumn = new TableColumn<>();
        typeColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().typeLabel(i18n)));
        // Typ und Schnittstelle so schmal wie sinnvoll (Wunsch: "auf den
        // niedrigsten Platz begrenzen") - per Maus weiterhin verbreiterbar.
        typeColumn.setPrefWidth(110);
        typeColumn.setMaxWidth(150);
        typeColumn.setEditable(false);
        typeColumn.setReorderable(false);
        typeColumn.setUserData("systems.colType");
        columns.add(typeColumn);

        if (SystemsObject.CATEGORY_LOCOMOTIVES.equals(category)) {
            // Seit 13.10.2026: Adresse, UID (nur Anzeige, mc2/DCC-A) und
            // Laenge in mm (wie in iTrain).
            TableColumn<SystemsObject, String> addressColumn =
                    textColumn("systems.fieldAddress", SystemsObject::addressProperty, 70);
            addressColumn.setMaxWidth(90);
            columns.add(addressColumn);
            TableColumn<SystemsObject, String> uidColumn =
                    textColumn("vehicle.fieldUid", SystemsObject::uidProperty, 90);
            uidColumn.setMaxWidth(110);
            columns.add(uidColumn);
            TableColumn<SystemsObject, String> locoLengthColumn =
                    textColumn("systems.colLength", SystemsObject::lengthProperty, 80);
            locoLengthColumn.setMaxWidth(100);
            columns.add(locoLengthColumn);
        }

        if (SystemsObject.CATEGORY_FEEDBACKS.equals(category)) {
            TableColumn<SystemsObject, String> lengthColumn =
                    textColumn("systems.colLength", SystemsObject::lengthProperty, 80);
            lengthColumn.setMaxWidth(100);
            columns.add(lengthColumn);
        }

        TableColumn<SystemsObject, String> interfaceColumn =
                textColumn("systems.colInterface", SystemsObject::interfaceNameProperty, 110);
        interfaceColumn.setMaxWidth(150);
        columns.add(interfaceColumn);
        return columns;
    }

    /**
     * Spalten frei in der Breite veraenderbar machen, gemerkte Breiten
     * wiederherstellen und jede Aenderung (kurz verzoegert, damit beim
     * Ziehen nicht staendig geschrieben wird) dauerhaft speichern - je
     * Abschnitt und Spalte, siehe {@link AppSettings#getColumnWidth}.
     */
    private void rememberColumnWidths(String category, TableView<SystemsObject> table) {
        for (TableColumn<SystemsObject, ?> column : table.getColumns()) {
            column.setResizable(true);
            column.setMaxWidth(5000);
            String key = "systems." + category + "." + column.getUserData();
            double saved = settings.getColumnWidth(key);
            if (saved > 20) {
                column.setPrefWidth(saved);
            }
            javafx.animation.PauseTransition delay = new javafx.animation.PauseTransition(javafx.util.Duration.millis(400));
            delay.setOnFinished(e -> settings.setColumnWidth(key, column.getWidth()));
            column.widthProperty().addListener((obs, old, width) -> delay.playFromStart());
        }
    }

    private TableColumn<SystemsObject, String> textColumn(String key,
            Function<SystemsObject, javafx.beans.property.StringProperty> property, int width) {
        TableColumn<SystemsObject, String> col = new TableColumn<>();
        col.setCellValueFactory(cell -> property.apply(cell.getValue()));
        col.setPrefWidth(width);
        col.setReorderable(false);
        col.setEditable(false);
        col.setUserData(key);
        return col;
    }

    private void applyLanguage() {
        updateTitle();
        fileMenu.setText(i18n.t("menu.file"));
        openMenuItem.setText(i18n.t("menu.open"));
        saveMenuItem.setText(i18n.t("systems.saveFile"));
        closeFileMenuItem.setText(i18n.t("menu.closeFile"));
        editMenu.setText(i18n.t("menu.edit"));
        editUndoMenuItem.setText(i18n.t("menu.undo"));
        editRedoMenuItem.setText(i18n.t("menu.redo"));
        editRowMenuItem.setText(i18n.t("systems.editRow"));
        editExportMenuItem.setText(i18n.t("editor.exportSelected"));
        editExportAllMenuItem.setText(i18n.t("systems.exportAll"));
        editDeleteRowMenuItem.setText(i18n.t("systems.deleteRow"));
        editClearTablesMenuItem.setText(i18n.t("systems.clearTables"));
        for (ContextMenu menu : contextMenus) {
            menu.getItems().get(0).setText(i18n.t("systems.editRow"));
            menu.getItems().get(1).setText(i18n.t("editor.exportSelected"));
            menu.getItems().get(3).setText(i18n.t("systems.deleteRow"));
        }
        systemsMenu.setText(i18n.t("window.systems"));
        bidibMenu.setText(i18n.t("menu.bidib"));
        mc2Menu.setText(i18n.t("menu.mc2"));
        mc2CgiConnectMenuItem.setText(i18n.t("mc2.cgiConnect"));
        mc2CgiDisconnectMenuItem.setText(i18n.t("mc2.cgiDisconnect"));
        bidibConnectMenuItem.setText(i18n.t("systems.connect"));
        bidibReadMenuItem.setText(i18n.t("systems.read"));
        esuMenu.setText(i18n.t("systems.esu"));
        esuConnectMenuItem.setText(i18n.t("systems.connect"));
        esuReadMenuItem.setText(i18n.t("systems.read"));
        bidibLoadFileMenuItem.setText(i18n.t("systems.loadSystemFile"));
        bidibRawLogMenu.setText(i18n.t("systems.showRawLog"));
        rawLogButton.setText(i18n.t("systems.rawLogButton"));
        rawLogButton.setTooltip(new Tooltip(i18n.t("systems.showRawLog")));
        mc2LocoButton.setText(i18n.t("mc2.readLocosButton"));
        mc2WriteButton.setText(i18n.t("mc2.writeLocosButton"));
        mc2WriteButton.setTooltip(new Tooltip(i18n.t("mc2.writeLocosTooltip")));
        ecosLocoButton.setText(i18n.t("ecos.readLocosButton"));
        ecosWriteButton.setText(i18n.t("central.writeTitle", "ECoS"));
        for (int i = 0; i < placeholderSystemMenus.length; i++) {
            Menu menu = placeholderSystemMenus[i];
            menu.setText(i18n.t(placeholderSystemKeys[i]));
            menu.getItems().get(0).setText(i18n.t("systems.connect"));
            menu.getItems().get(1).setText(i18n.t("systems.read"));
        }
        functionsMenu.setText(i18n.t("menu.functions"));
        functionsImportExportMenuItem.setText(i18n.t("menu.programPart", i18n.t("window.importExport")));
        functionsDecoderMenuItem.setText(i18n.t("menu.programPart", i18n.t("window.decoder")));
        helpMenu.setText(i18n.t("menu.help"));
        helpMenuItem.setText(i18n.t("menu.helpItem"));
        String later = i18n.t("systems.comingSoon");
        openToolButton.setTooltip(new Tooltip(i18n.t("systems.openFileTooltip")));
        saveToolButton.setTooltip(new Tooltip(i18n.t("systems.saveFile")));
        undoToolButton.setTooltip(new Tooltip(i18n.t("menu.undo") + " - " + later));
        redoToolButton.setTooltip(new Tooltip(i18n.t("menu.redo") + " - " + later));
        connectButton.setText(i18n.t("systems.connect"));
        readButton.setText(i18n.t("systems.read"));
        exportButton.setText(i18n.t("editor.exportSelected"));
        exportButton.setTooltip(new Tooltip(i18n.t("systems.exportTooltip")));
        exportAllButton.setText(i18n.t("systems.exportAll"));
        clearTablesButton.setText(i18n.t("systems.clearTables"));
        loadItrainButton.setText(i18n.t("systems.loadItrain"));
        loadItrainButton.setTooltip(new Tooltip(i18n.t("systems.loadItrainTooltip")));
        loadItrainMenuItem.setText(i18n.t("systems.loadItrain"));
        writeItrainButton.setText(i18n.t("systems.writeItrain"));
        writeItrainButton.setTooltip(new Tooltip(i18n.t("systems.writeItrainTooltip")));
        writeItrainMenuItem.setText(i18n.t("systems.writeItrain"));
        if (matchDocument != null) {
            rebuildMatchChoices(); // "Neu" in der neuen Sprache
        }
        emptyLabel.setText(i18n.t("systems.noObjects"));
        for (Section section : sections) {
            section.header.setText(i18n.t(sectionKey(section.category)));
            section.selectAllBox.setText(i18n.t("systems.selectAllRows"));
            section.editRowButton.setText(i18n.t("systems.editRow"));
            for (TableColumn<SystemsObject, ?> column : section.table.getColumns()) {
                column.setText(i18n.t(String.valueOf(column.getUserData())));
            }
            section.table.refresh();
        }
        updateFileLabel();
    }

    private static String sectionKey(String category) {
        switch (category) {
            case SystemsObject.CATEGORY_INTERFACES:
                return "systems.sectionInterface";
            case SystemsObject.CATEGORY_BOOSTERS:
                return "systems.sectionBoosters";
            case SystemsObject.CATEGORY_FEEDBACKS:
                return "systems.sectionFeedbacks";
            case SystemsObject.CATEGORY_LOCOMOTIVES:
                return "systems.sectionLocomotives";
            default:
                return "systems.sectionAccessories";
        }
    }

    /** Die markierte Zeile (Markierung gibt es immer nur in einem Abschnitt) samt Abschnitt. */
    private Section sectionWithSelection() {
        for (Section section : sections) {
            if (section.table.getSelectionModel().getSelectedItem() != null) {
                return section;
            }
        }
        return null;
    }

    /**
     * Sperrzustand aller Knoepfe und Menuepunkte aus dem aktuellen Stand
     * ableiten: Oeffnen nur, wenn es System-Dateien gibt; Speichern und
     * Exportieren nur, wenn Objekte da sind; Auslesen nur mit Verbindung;
     * Zeile bearbeiten nur mit markierter Zeile.
     */
    private void updateState() {
        // Nur die Verbindungen DIESES Fensters zaehlen (seit 13.10.2026).
        boolean anyBidib = hasBidibLink();
        boolean anyEcos = !ecosLinks().isEmpty();
        boolean hasFile = currentFile != null;
        boolean fileHasNodes = hasFile && !currentFile.getNodes().isEmpty();
        boolean anyObjects = !objects.isEmpty();
        boolean anySelected = sectionWithSelection() != null;

        // Oeffnen immer moeglich - der Dateidialog zeigt den Ordner aus den
        // Voreinstellungen (vorher war der Knopf gesperrt, solange dort
        // keine Datei mit der aktuellen Endung lag).
        openToolButton.setDisable(false);
        openMenuItem.setDisable(false);
        saveToolButton.setDisable(!(hasFile || anyObjects));
        saveMenuItem.setDisable(!(hasFile || anyObjects));
        closeFileMenuItem.setDisable(!(hasFile || anyObjects));
        // Auslesen: mit Verbindung vom Geraet, sonst aus der geoeffneten
        // System-Datei (Knotenbaum ohne angeschlossene Anlage).
        boolean cgiHere = cgiHost() != null;
        readButton.setDisable(!(anyBidib || anyEcos || cgiHere || fileHasNodes));
        bidibReadMenuItem.setDisable(!(anyBidib || fileHasNodes));
        // "mc2 Lokomotiven auslesen": nur sichtbar, solange die mc2 dieses
        // Fensters lesbar ist (CGI-Verbindung, oder netBiDiB mit gepruefter
        // mc2 - siehe BidibConnection.isMc2/isMc2LocoAvailable, kommt erst
        // nach dem Hintergrund-Check).
        mc2CgiDisconnectMenuItem.setDisable(!Mc2CgiConnection.getInstance().isConnected());
        boolean anyMc2Loco = cgiHere || mc2LocoConnections().findFirst().isPresent();
        mc2LocoButton.setVisible(anyMc2Loco);
        mc2LocoButton.setManaged(anyMc2Loco);
        boolean canWriteMc2 = anyMc2Loco && mainDocument() != null;
        mc2WriteButton.setVisible(canWriteMc2);
        mc2WriteButton.setManaged(canWriteMc2);
        boolean anyEcosConnected = ecosLinks().stream().anyMatch(EcosConnection::isConnected);
        ecosLocoButton.setVisible(anyEcosConnected);
        ecosLocoButton.setManaged(anyEcosConnected);
        boolean canWriteEcos = anyEcosConnected && mainDocument() != null;
        ecosWriteButton.setVisible(canWriteEcos);
        ecosWriteButton.setManaged(canWriteEcos);
        editUndoMenuItem.setDisable(lastTransfer == null);
        undoToolButton.setDisable(lastTransfer == null);
        esuReadMenuItem.setDisable(!anyEcos);
        bidibRawLogMenu.setDisable(!anyBidib);
        // RX/TX gibt es nur bei BiDiB - als systemspezifischer Knopf nur dann sichtbar.
        rawLogButton.setVisible(anyBidib);
        rawLogButton.setManaged(anyBidib);
        editRowMenuItem.setDisable(!anySelected);
        editDeleteRowMenuItem.setDisable(!anySelected);
        exportButton.setDisable(!anyObjects);
        editExportMenuItem.setDisable(!anyObjects);
        exportAllButton.setDisable(!anyObjects);
        editExportAllMenuItem.setDisable(!anyObjects);
        clearTablesButton.setDisable(!anyObjects);
        editClearTablesMenuItem.setDisable(!anyObjects);
        // Direkt schreiben nur mit geladener iTrain-Datei.
        writeItrainButton.setDisable(!(anyObjects && matchDocument != null));
        writeItrainMenuItem.setDisable(!(anyObjects && matchDocument != null));
    }

    private void updateFileLabel() {
        if (currentFile != null && currentFile.getFile() != null) {
            fileLabel.setText(currentFile.getFile().getName());
        } else if (currentFile != null) {
            fileLabel.setText(currentFile.getInterfaceName());
        } else {
            fileLabel.setText(i18n.t("systems.noFile"));
        }
    }

    // ------------------------------------------------------------------
    // Datei: System-Dateien
    // ------------------------------------------------------------------

    private File systemFilesDirectory() {
        String dir = settings.getSystemFilesDirectory();
        return dir == null || dir.isBlank() ? null : new File(dir);
    }

    private void onOpenSystemFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n.t("systems.openFileTooltip"));
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(i18n.t("systems.fileFilter"), "*" + BidibSystemFile.EXTENSION),
                new FileChooser.ExtensionFilter("ZIP (*.zip)", "*.zip"));
        File dir = systemFilesDirectory();
        if (dir != null && dir.isDirectory()) {
            chooser.setInitialDirectory(dir);
        }
        File chosen = chooser.showOpenDialog(stage);
        if (chosen == null) {
            return;
        }
        try {
            BidibSystemFile loaded = BidibSystemFile.load(chosen);
            currentFile = loaded;
            objects.clear();
            for (Section section : sections) {
                section.selectAllBox.setSelected(section.category.equals(SystemsObject.CATEGORY_INTERFACES));
            }
            for (BidibSystemFile.ObjectRecord record : loaded.getObjects()) {
                SystemsObject object = SystemsObject.fromRecord(record);
                String nodeAddress = record.address().contains(" / ")
                        ? record.address().substring(0, record.address().indexOf(" / ")) : record.address();
                for (BidibSystemFile.NodeRecord node : loaded.getNodes()) {
                    if (node.address().equals(nodeAddress)) {
                        object.setNodeUniqueId(node.uniqueId());
                        break;
                    }
                }
                objects.add(object);
            }
            updateFileLabel();
            updateState();
            // Eine Datei direkt aus dem Knotenbaum ("Speichern" vor "Auswahl
            // uebernehmen") hat Knoten, aber noch keine Objekte - dann gleich
            // den Baum aus der Datei zeigen, sonst bliebe das Fenster leer.
            if (objects.isEmpty() && !loaded.getNodes().isEmpty()) {
                BidibNodeTreeWindow.showFromFile(stage, loaded);
            }
        } catch (IOException | RuntimeException ex) {
            error(i18n.t("systems.fileError", String.valueOf(ex.getMessage())));
        }
    }

    private void onSaveSystemFile() {
        if (currentFile == null) {
            currentFile = new BidibSystemFile();
            for (SystemsObject object : objects) {
                if (object.isInterface()) {
                    currentFile.setInterfaceName(object.getName());
                    currentFile.setHostPort(object.getAddress());
                    currentFile.setSerial(!object.getAddress().contains(":"));
                    currentFile.setSystemType("ecos".equals(object.getType())
                            ? BidibSystemFile.SYSTEM_ECOS : BidibSystemFile.SYSTEM_BIDIB);
                    break;
                }
            }
        }
        currentFile.getObjects().clear();
        for (SystemsObject object : objects) {
            currentFile.getObjects().add(object.toRecord());
        }
        File target = currentFile.getFile();
        if (target == null) {
            File dir = systemFilesDirectory();
            if (dir == null) {
                error(i18n.t("bidib.systemFilesDirMissing"));
                return;
            }
            FileChooser chooser = new FileChooser();
            chooser.setTitle(i18n.t("systems.saveFile"));
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                    i18n.t("systems.fileFilter"), "*" + BidibSystemFile.EXTENSION));
            if (dir.isDirectory()) {
                chooser.setInitialDirectory(dir);
            }
            chooser.setInitialFileName(currentFile.suggestedFileName());
            target = chooser.showSaveDialog(stage);
            if (target == null) {
                return;
            }
            if (!target.getName().toLowerCase().endsWith(BidibSystemFile.EXTENSION)) {
                String name = target.getName();
                if (name.toLowerCase().endsWith(".zip")) {
                    name = name.substring(0, name.length() - 4);
                }
                target = new File(target.getParentFile(), name + BidibSystemFile.EXTENSION);
            }
        }
        try {
            currentFile.save(target);
            updateFileLabel();
            updateState();
            fileLabel.setText(i18n.t("systems.fileSaved", target.getName()));
        } catch (IOException | RuntimeException ex) {
            error(i18n.t("systems.fileError", String.valueOf(ex.getMessage())));
        }
    }

    private void onCloseSystemFile() {
        currentFile = null;
        objects.clear();
        updateFileLabel();
        updateState();
    }

    // ------------------------------------------------------------------
    // Objekte
    // ------------------------------------------------------------------

    /** Bearbeitungsfenster fuer die markierte Zeile (des Abschnitts, sonst des Abschnitts mit Markierung). */
    private void editSelected(Section section) {
        if (section == null) {
            section = sectionWithSelection();
        }
        if (section == null) {
            return;
        }
        SystemsObject selected = section.table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        if (SystemsObjectDialog.show(stage, selected, matchDocument == null ? null : matchDocument.getRoot())) {
            for (Section s : sections) {
                s.table.refresh();
            }
        }
    }

    /** "Zeile loeschen" / Entf / Rechtsklick: die markierten Zeilen entfernen. */
    private void deleteSelected() {
        Section section = sectionWithSelection();
        if (section == null) {
            return;
        }
        List<SystemsObject> selected = new ArrayList<>(section.table.getSelectionModel().getSelectedItems());
        section.table.getSelectionModel().clearSelection();
        objects.removeAll(selected);
        updateState();
    }

    /** "Tabellen loeschen": alle Zeilen weg; die geoeffnete Datei bleibt zugeordnet. */
    private void clearTables() {
        if (objects.isEmpty()) {
            return;
        }
        ButtonType clear = new ButtonType(i18n.t("systems.clearTables"), ButtonBar.ButtonData.OK_DONE);
        ButtonType abort = new ButtonType(i18n.t("bidib.abortButton"), ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, i18n.t("systems.clearTablesConfirm", objects.size()),
                clear, abort);
        alert.initOwner(stage);
        alert.setHeaderText(null);
        alert.setTitle(i18n.t("window.systemsTitle"));
        ThemeManager.apply(alert.getDialogPane().getScene(), settings.getTheme());
        if (alert.showAndWait().orElse(abort) != clear) {
            return;
        }
        for (Section section : sections) {
            section.table.getSelectionModel().clearSelection();
        }
        objects.clear();
        updateState();
    }

    /** "Alles exportieren": alle Zeilen, unabhaengig vom Haken. */
    private void onExportAll() {
        exportObjects(new ArrayList<>(objects));
    }

    /**
     * "Markierte exportieren": die ausgewaehlten Zeilen (Haken); die
     * Schnittstelle ist immer dabei.
     */
    private void onExportSelected() {
        List<SystemsObject> chosen = new ArrayList<>();
        for (SystemsObject object : objects) {
            if (object.isSelected() || object.isInterface()) {
                chosen.add(object);
            }
        }
        exportObjects(chosen);
    }

    /**
     * Schreibt die Objekte je Kategorie als CSV im Format des
     * Kategorie-Exports (Kategorie, Typ, Name, Beschreibung, XML), verpackt
     * in ein ZIP im Export-Ordner. Der Import im Hauptfenster liest ZIP wie
     * CSV und prueft die erste Spalte gegen den gewaehlten Reiter, deshalb
     * je Kategorie eine Datei.
     */
    private void exportObjects(List<SystemsObject> chosen) {
        Map<String, List<SystemsObject>> byCategory = new LinkedHashMap<>();
        for (SystemsObject object : chosen) {
            if (object.getXml() == null) {
                continue;
            }
            byCategory.computeIfAbsent(object.getCategory(), k -> new ArrayList<>()).add(object);
        }
        if (byCategory.isEmpty()) {
            return;
        }
        // Zusammengehoerigkeit sichtbar machen (Anwenderwunsch): alle Dateien
        // EINES Exports bekommen denselben "Exportsatz"-Namen (Schnittstelle +
        // Zeitstempel) und eine laufende Nummer "1von3", "2von3" ... - in
        // der Reihenfolge, in der sie importiert werden sollten (Schnittstelle
        // zuerst, weil die anderen auf sie verweisen). Ein einziger
        // Ordner-Dialog statt eines Datei-Dialogs je Kategorie.
        List<Map.Entry<String, List<SystemsObject>>> parts = new ArrayList<>();
        for (String category : SystemsObject.CATEGORY_ORDER) {
            if (byCategory.containsKey(category)) {
                parts.add(Map.entry(category, byCategory.get(category)));
            }
        }
        for (Map.Entry<String, List<SystemsObject>> entry : byCategory.entrySet()) {
            if (!SystemsObject.CATEGORY_ORDER.contains(entry.getKey())) {
                parts.add(entry);
            }
        }
        // Speichern-Dialog MIT vorgeschlagenem Namen (Anwenderwunsch - der
        // reine Ordner-Dialog schlug keinen vor). Der eingegebene Name ist der
        // gemeinsame Namensanfang des Exportsatzes; die Teile bekommen
        // "_1von3_<kategorie>.zip" usw. angehaengt.
        String interfaceName = currentFile != null ? currentFile.getInterfaceName() : "BiDiB";
        String base = (interfaceName == null || interfaceName.isBlank() ? "BiDiB" : interfaceName)
                .replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        String proposed = base + "_" + java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n.t("systems.exportChooseFolder"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZIP", "*.zip"));
        String exportDir = settings.getExportDirectory();
        if (exportDir != null && new File(exportDir).isDirectory()) {
            chooser.setInitialDirectory(new File(exportDir));
        }
        chooser.setInitialFileName(proposed + ".zip");
        File picked = chooser.showSaveDialog(stage);
        if (picked == null) {
            return;
        }
        File folder = picked.getParentFile();
        String setName = picked.getName().toLowerCase().endsWith(".zip")
                ? picked.getName().substring(0, picked.getName().length() - 4) : picked.getName();
        if (setName.isBlank()) {
            setName = proposed;
        }
        List<File> targets = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            String part = i18n.t("systems.exportPart", i + 1, parts.size());
            targets.add(new File(folder, setName + "_" + part + "_" + parts.get(i).getKey() + ".zip"));
        }
        if (targets.stream().anyMatch(File::exists)) {
            ButtonType overwrite = new ButtonType(i18n.t("systems.overwrite"), ButtonBar.ButtonData.OK_DONE);
            ButtonType abort = new ButtonType(i18n.t("bidib.abortButton"), ButtonBar.ButtonData.CANCEL_CLOSE);
            Alert ask = new Alert(Alert.AlertType.CONFIRMATION, i18n.t("systems.exportExists"), overwrite, abort);
            ask.initOwner(stage);
            ask.setHeaderText(null);
            if (ask.showAndWait().orElse(abort) != overwrite) {
                return;
            }
        }
        List<String> written = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            try {
                writeCategoryZip(targets.get(i), parts.get(i).getKey(), parts.get(i).getValue());
                written.add(targets.get(i).getName());
            } catch (Exception ex) {
                error(i18n.t("systems.fileError", String.valueOf(ex.getMessage())));
                return;
            }
        }
        if (!written.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION,
                    i18n.t("systems.exportSetText", setName, folder.getAbsolutePath()) + "\n\n"
                            + String.join("\n", written));
            alert.initOwner(stage);
            alert.setHeaderText(i18n.t("bidib.generateSaveSuccess", written.size()));
            alert.getDialogPane().setMinWidth(560);
            alert.showAndWait();
        }
    }

    private void writeCategoryZip(File target, String category, List<SystemsObject> items) throws Exception {
        List<String> header = List.of("Kategorie",
                i18n.t("editor.columnType"), i18n.t("editor.columnName"),
                i18n.t("editor.columnDescription"), "XML");
        List<List<String>> rows = new ArrayList<>();
        for (SystemsObject object : items) {
            // Bei einer Zuordnung zur geladenen iTrain-Datei: der vorhandene
            // Eintrag mit aktualisierter BiDiB-Verknuepfung (siehe exportXml).
            XmlNode item = object.exportXml();
            rows.add(List.of(category, item.getTagName(), item.getName(), object.getDescription(),
                    TcdDocument.nodeToXmlString(item)));
        }
        String entryName = target.getName().substring(0, target.getName().length() - 4) + ".csv";
        CsvUtil.writeZipped(target, entryName, header, rows);
    }

    // ------------------------------------------------------------------
    // iTrain-Datei laden und zuordnen
    // ------------------------------------------------------------------

    /**
     * "iTrain-Datei laden": laedt die .tcd/.tcdz als DIE geladene iTrain-Datei
     * des ganzen Programms (sie gehoert der Sitzung des Hauptfensters und gilt
     * damit gleichzeitig im Hauptfenster, im Decoder- und im Systeme-Fenster,
     * bis eine andere geladen oder das Programm beendet wird). Die
     * Tabellenzeilen werden danach automatisch zugeordnet - siehe
     * {@link #onMainDocumentChanged()}.
     */
    private void onLoadItrainFile() {
        DocumentSession main = HelloController.getMainSession();
        if (main != null) {
            // Eigenes Fenster als Besitzer und danach wieder nach vorn -
            // sonst springt Windows nach dem Laden auf das Hauptfenster.
            main.openFileDialog(stage);
            stage.toFront();
            stage.requestFocus();
            javafx.application.Platform.runLater(() -> {
                stage.toFront();
                stage.requestFocus();
            });
        }
    }

    /** Das in allen Programmteilen gemeinsame iTrain-Dokument (Hauptfenster), oder null. */
    private static TcdDocument mainDocument() {
        DocumentSession main = HelloController.getMainSession();
        return main == null ? null : main.getDocument();
    }

    /**
     * Die gemeinsame iTrain-Datei wurde geladen, geschlossen, per
     * Rueckgaengig veraendert oder von hier aus beschrieben: Zuordnungen neu
     * aufbauen (alte verweisen auf nicht mehr gueltige Eintraege).
     */
    private void onMainDocumentChanged() {
        matchDocument = mainDocument();
        for (SystemsObject object : objects) {
            object.setMatch(null);
        }
        rebuildMatchChoices();
        int matched = autoMatch();
        refreshTables();
        updateState();
        updateItrainFileLabel();
        if (matchDocument != null && matchDocument.getFile() != null && !objects.isEmpty()) {
            fileLabel.setText(i18n.t("systems.itrainLoaded", matchDocument.getFile().getName(), matched));
        }
    }

    /** Statuszeile: "iTrain-Datei: <Name>" solange eine iTrain-Datei geladen ist. */
    private void updateItrainFileLabel() {
        boolean loaded = matchDocument != null && matchDocument.getFile() != null;
        itrainFileLabel.setText(loaded ? i18n.t("systems.itrainFileLabel", matchDocument.getFile().getName()) : "");
        itrainFileLabel.setVisible(loaded);
    }

    /**
     * "In iTrain-Datei schreiben": die markierten Zeilen (Haken
     * "Ausgewaehlt", die Schnittstelle immer) OHNE Umweg ueber Exportdateien
     * in die geladene iTrain-Datei uebernehmen und diese speichern.
     * Zugeordnete Zeilen ersetzen ihren vorhandenen Eintrag an derselben
     * Stelle (Verweise bleiben gueltig), neue werden angehaengt - bei
     * Namensgleichheit mit freiem Namen "Name (2)". Die Aenderung laeuft
     * ueber die Sitzung des Hauptfensters (Rueckgaengig-Schritt, Reiter aller
     * Fenster aktualisiert); gespeichert wird mit vorheriger Sicherung
     * (DocumentSession.saveToCurrentFile).
     */
    private void onWriteToItrain() {
        DocumentSession main = HelloController.getMainSession();
        matchDocument = mainDocument();
        if (main == null || matchDocument == null || matchDocument.getFile() == null) {
            return;
        }
        List<SystemsObject> chosen = new ArrayList<>();
        for (String category : SystemsObject.CATEGORY_ORDER) {
            for (SystemsObject object : objects) {
                if (category.equals(object.getCategory()) && object.getXml() != null
                        && (object.isSelected() || object.isInterface())) {
                    chosen.add(object);
                }
            }
        }
        if (chosen.isEmpty()) {
            return;
        }
        String fileName = matchDocument.getFile().getName();
        ButtonType write = new ButtonType(i18n.t("systems.writeItrain"), ButtonBar.ButtonData.OK_DONE);
        ButtonType abort = new ButtonType(i18n.t("bidib.abortButton"), ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert ask = new Alert(Alert.AlertType.CONFIRMATION,
                i18n.t("systems.writeItrainConfirm", chosen.size(), fileName), write, abort);
        ask.initOwner(stage);
        ask.setHeaderText(null);
        ask.getDialogPane().setMinWidth(520);
        ThemeManager.apply(ask.getDialogPane().getScene(), settings.getTheme());
        if (ask.showAndWait().orElse(abort) != write) {
            return;
        }

        main.recordExternalUndo();
        XmlNode root = matchDocument.getRoot();
        XmlNode controlItems = root.findChild("control-items");
        if (controlItems == null) {
            controlItems = new XmlNode("control-items");
            root.getChildren().add(controlItems);
        }
        int updated = 0;
        int added = 0;
        List<String> renamed = new ArrayList<>();
        for (SystemsObject object : chosen) {
            XmlNode node = object.exportXml().deepCopy();
            XmlNode category = controlItems.findChild(object.getCategory());
            if (category == null) {
                category = new XmlNode(object.getCategory());
                controlItems.getChildren().add(category);
            }
            int index = object.isMatched() ? indexOfIdentity(category, object.getMatchedEntry()) : -1;
            if (index >= 0) {
                category.getChildren().set(index, node);
                updated++;
            } else {
                String base = node.getName();
                if (!base.isBlank() && hasEntry(category, node.getTagName(), base)) {
                    int n = 2;
                    while (hasEntry(category, node.getTagName(), base + " (" + n + ")")) {
                        n++;
                    }
                    node.setAttribute("name", base + " (" + n + ")");
                    renamed.add(base + " -> " + node.getName());
                }
                category.getChildren().add(node);
                added++;
            }
        }
        // Reiter aller Fenster nachziehen; die Zuordnungen hier baut der
        // Beobachter (onMainDocumentChanged) neu auf - die geschriebenen
        // Eintraege tragen jetzt die BiDiB-Kennung und werden wiedererkannt.
        main.applyExternalChange();
        if (!main.saveToCurrentFile()) {
            return;
        }
        StringBuilder text = new StringBuilder(i18n.t("systems.writeItrainDone", updated, added, fileName));
        if (!renamed.isEmpty()) {
            text.append("\n\n").append(i18n.t("systems.writeItrainRenamed")).append("\n").append(String.join("\n", renamed));
        }
        Alert done = new Alert(Alert.AlertType.INFORMATION, text.toString());
        done.initOwner(stage);
        done.setHeaderText(null);
        done.getDialogPane().setMinWidth(520);
        done.showAndWait();
    }

    private static int indexOfIdentity(XmlNode category, XmlNode entry) {
        List<XmlNode> children = category.getChildren();
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i) == entry) {
                return i;
            }
        }
        return -1;
    }

    private static boolean hasEntry(XmlNode category, String tag, String name) {
        for (XmlNode child : category.getChildren()) {
            if (child.getTagName().equals(tag) && name.equals(child.getAttribute("name"))) {
                return true;
            }
        }
        return false;
    }

    /** "Neu" plus die Namen der vorhandenen Eintraege je Kategorie. */
    private void rebuildMatchChoices() {
        matchChoices.clear();
        if (matchDocument == null) {
            return;
        }
        for (String category : SystemsObject.CATEGORY_ORDER) {
            ObservableList<String> choices = FXCollections.observableArrayList();
            choices.add(i18n.t("systems.matchNew"));
            for (XmlNode entry : entriesOf(category)) {
                if (!entry.getName().isBlank() && !choices.contains(entry.getName())) {
                    choices.add(entry.getName());
                }
            }
            matchChoices.put(category, choices);
        }
    }

    private List<XmlNode> entriesOf(String category) {
        if (matchDocument == null || matchDocument.getRoot() == null) {
            return List.of();
        }
        XmlNode controlItems = matchDocument.getRoot().findChild("control-items");
        XmlNode categoryNode = controlItems == null ? null : controlItems.findChild(category);
        return categoryNode == null ? List.of() : categoryNode.getChildren();
    }

    /** Eindeutige Zuordnungen setzen; Rueckgabe: Anzahl zugeordneter Zeilen. */
    private int autoMatch() {
        return autoMatch(objects);
    }

    /** Wie {@link #autoMatch()}, aber nur fuer die uebergebenen Zeilen (z.B. frisch ausgelesene). */
    private int autoMatch(List<SystemsObject> candidates) {
        if (matchDocument == null) {
            return 0;
        }
        int matched = 0;
        // Schnittstelle zuerst - ihr Name wird ggf. an die anderen Zeilen weitergegeben.
        List<SystemsObject> ordered = new ArrayList<>(candidates);
        ordered.sort((a, b) -> Boolean.compare(!a.isInterface(), !b.isInterface()));
        for (SystemsObject object : ordered) {
            XmlNode ours = object.getXml();
            if (ours == null) {
                continue;
            }
            XmlNode found = null;
            if (object.isInterface()) {
                String host = object.childAttribute("socket", "host");
                for (XmlNode entry : entriesOf(object.getCategory())) {
                    XmlNode socket = entry.findChild("socket");
                    boolean sameHost = !host.isBlank() && socket != null && host.equals(socket.getAttribute("host"));
                    if (entry.getName().equals(object.getName()) || sameHost) {
                        found = entry;
                        break;
                    }
                }
            } else {
                XmlNode idNode = ours.findChild("id");
                String id = idNode == null || idNode.getTextContent() == null ? "" : idNode.getTextContent().trim();
                if (!id.isEmpty()) {
                    for (XmlNode entry : entriesOf(object.getCategory())) {
                        XmlNode theirId = entry.findChild("id");
                        if (theirId != null && theirId.getTextContent() != null
                                && id.equalsIgnoreCase(theirId.getTextContent().trim())) {
                            found = entry;
                            break;
                        }
                    }
                }
            }
            if (found != null) {
                matchTo(object, found);
                matched++;
            }
        }
        return matched;
    }

    /** Auswahl in der Spalte "Zuordnung in iTrain": "Neu" oder ein vorhandener Name. */
    private void applyMatch(SystemsObject object, String choice) {
        if (choice.equals(i18n.t("systems.matchNew"))) {
            if (object.isMatched()) {
                object.setMatch(null);
                refreshTables();
            }
            return;
        }
        if (object.isMatched() && choice.equals(object.getMatchedEntry().getName())) {
            return;
        }
        XmlNode entry = null;
        for (XmlNode candidate : entriesOf(object.getCategory())) {
            if (choice.equals(candidate.getName())) {
                // Bei Zubehoer gleiche Elementart (turnout/signal ...) bevorzugen.
                if (entry == null || (object.getXml() != null
                        && candidate.getTagName().equals(object.getXml().getTagName()))) {
                    entry = candidate;
                }
            }
        }
        if (entry != null) {
            matchTo(object, entry);
            refreshTables();
        }
    }

    /**
     * Zuordnen; bei der Schnittstelle zusaetzlich deren iTrain-Namen in
     * alle Zeilen uebernehmen, die auf den bisherigen Namen verweisen.
     */
    private void matchTo(SystemsObject object, XmlNode entry) {
        if (object.isInterface()) {
            String oldName = object.getName();
            String newName = entry.getName();
            object.setMatch(entry);
            if (!newName.equals(oldName)) {
                object.setInterfaceName(newName);
                for (SystemsObject other : objects) {
                    if (other != object && oldName.equals(other.getInterfaceName())) {
                        other.setInterfaceName(newName);
                    }
                }
            }
            return;
        }
        object.setMatch(entry);
    }

    private void refreshTables() {
        for (Section section : sections) {
            section.table.refresh();
        }
    }

    // ------------------------------------------------------------------
    // Verbindungen
    // ------------------------------------------------------------------

    /** BiDiB-Verbindungen dieses Fensters. */
    private List<BidibConnection> bidibLinks() {
        List<BidibConnection> result = new ArrayList<>();
        for (Link link : links) {
            if (link.bidib() != null) {
                result.add(link.bidib());
            }
        }
        return result;
    }

    private boolean hasBidibLink() {
        return !bidibLinks().isEmpty();
    }

    /** ECoS-Verbindungen dieses Fensters. */
    private List<EcosConnection> ecosLinks() {
        List<EcosConnection> result = new ArrayList<>();
        for (Link link : links) {
            if (link.ecos() != null) {
                result.add(link.ecos());
            }
        }
        return result;
    }

    /** Host der mc2, wenn dieses Fenster die CGI-Verbindung hat, sonst null. */
    private String cgiHost() {
        for (Link link : links) {
            if (link.cgiHost() != null) {
                return link.cgiHost();
            }
        }
        return null;
    }

    /**
     * Beim Schliessen: die Verbindungen DIESES Fensters trennen (nach
     * Rueckfrage, sofern es welche gibt). Die anderen Fenster behalten ihre.
     */
    private boolean confirmDisconnect() {
        if (links.isEmpty()) {
            return true;
        }
        ButtonType disconnect = new ButtonType(i18n.t("bidib.disconnectButton"), ButtonBar.ButtonData.OK_DONE);
        ButtonType abort = new ButtonType(i18n.t("bidib.abortButton"), ButtonBar.ButtonData.CANCEL_CLOSE);
        StringBuilder names = new StringBuilder();
        for (Link link : links) {
            names.append("\n").append(link.name());
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, i18n.t("bidib.closeConfirm") + names,
                disconnect, abort);
        alert.initOwner(stage);
        alert.setHeaderText(null);
        alert.setTitle(stage.getTitle());
        ThemeManager.apply(alert.getDialogPane().getScene(), settings.getTheme());
        alert.getDialogPane().sceneProperty().addListener((obs, oldScene, newScene) ->
                ThemeManager.apply(newScene, settings.getTheme()));
        if (alert.showAndWait().orElse(abort) != disconnect) {
            return false;
        }
        for (Link link : new ArrayList<>(links)) {
            if (link.bidib() != null) {
                BidibConnection connection = link.bidib();
                BidibConnectionManager.getInstance().remove(connection);
                // close() kann haengen, wenn die Gegenseite nicht antwortet.
                Thread worker = new Thread(connection::close, "bidib-close");
                worker.setDaemon(true);
                worker.start();
            } else if (link.ecos() != null) {
                link.ecos().close();
            } else if (link.cgiHost() != null) {
                Mc2CgiConnection.getInstance().disconnect();
            }
        }
        return true;
    }

    /**
     * "Auslesen": liest die Knoten und oeffnet den Knotenbaum. Bei genau
     * einer BiDiB-Verbindung dieses Fensters sofort, bei mehreren erst nach
     * Auswahl ueber ein Klappmenue am Knopf.
     */
    private void openNodeTree() {
        treeTarget = this;
        if (!hasBidibLink()) {
            // Nur ECoS verbunden: deren Auslesen. Sonst der Knotenbaum aus
            // der geoeffneten System-Datei.
            if (!ecosLinks().isEmpty()) {
                readEcos();
            } else if (currentFile != null && !currentFile.getNodes().isEmpty()) {
                BidibNodeTreeWindow.showFromFile(stage, currentFile);
            }
            return;
        }
        chooseConnection(readButton, connection -> BidibNodeTreeWindow.show(stage, connection));
    }

    /**
     * ESU -> Auslesen: Bestand der ECoS lesen (Fortschrittsdialog) und die
     * fertigen Objekte direkt in die Tabellen uebernehmen - einen Knotenbaum
     * gibt es hier nicht, die Auswahl geschieht ueber die Haken.
     */
    private void readEcos() {
        List<EcosConnection> connections = ecosLinks();
        if (connections.isEmpty()) {
            return;
        }
        treeTarget = this;
        // Wie bei BiDiB: erst der Knotenbaum mit Ankreuzkaesten, dann
        // "Auswahl uebernehmen" in die Tabellen.
        java.util.function.Consumer<EcosConnection> read = connection ->
                EcosReader.readWithProgress(stage, connection, file -> BidibNodeTreeWindow.showFromFile(stage, file));
        if (connections.size() == 1) {
            read.accept(connections.get(0));
            return;
        }
        ContextMenu chooser = new ContextMenu();
        for (EcosConnection connection : connections) {
            MenuItem item = new MenuItem();
            item.textProperty().bind(connection.nameProperty());
            item.setOnAction(e -> read.accept(connection));
            chooser.getItems().add(item);
        }
        chooser.show(readButton, javafx.geometry.Side.BOTTOM, 0, 0);
    }

    /**
     * "ECoS Lokomotiven auslesen" (seit 13.10.2026): Lokliste der ECoS
     * (Objekte 1000-1999: Name, Adresse, Protokoll) direkt in die Tabelle
     * "Lokomotiven" - ohne Knotenbaum, wie bei der mc2. Schnittstelle ist die
     * schon vorhandene ECoS-Schnittstelle der Tabelle, sonst "ECoS".
     */
    private void readEcosLocos() {
        List<EcosConnection> connections = ecosLinks().stream()
                .filter(EcosConnection::isConnected).collect(java.util.stream.Collectors.toList());
        if (connections.isEmpty()) {
            return;
        }
        java.util.function.Consumer<EcosConnection> read = connection -> {
            String interfaceName = "ECoS";
            for (SystemsObject object : objects) {
                if (object.isInterface() && "ecos".equals(object.getType())) {
                    interfaceName = object.getInterfaceName();
                    break;
                }
            }
            String finalInterfaceName = interfaceName;
            runWithProgress(i18n.t("ecos.readLocosButton"), i18n.t("ecos.readingLocos"), () -> {
                EcosClient.Reply reply = connection.getClient().send("queryObjects(10, addr, name, protocol)");
                if (!reply.isOk()) {
                    throw new IOException("queryObjects(10): " + reply.message());
                }
                return reply.lines();
            }, lines -> {
                if (currentFile == null) {
                    BidibSystemFile placeholder = new BidibSystemFile();
                    placeholder.setSystemType(BidibSystemFile.SYSTEM_ECOS);
                    placeholder.setInterfaceName(finalInterfaceName);
                    currentFile = placeholder;
                }
                int added = 0;
                int total = 0;
                for (EcosClient.Line line : lines) {
                    if (line.objectId() < 1000 || line.objectId() >= 2000) {
                        continue;
                    }
                    total++;
                    String name = line.get("name") == null || line.get("name").isBlank()
                            ? "Lok " + line.objectId() : line.get("name");
                    int address = line.getInt("addr", 0);
                    LocomotiveXmlFactory.LocoInfo info = LocomotiveXmlFactory.parseLocoType(line.get("protocol"));
                    XmlNode node = LocomotiveXmlFactory.createLocomotive(name, address, info, finalInterfaceName);
                    SystemsObject object = new SystemsObject(SystemsObject.CATEGORY_LOCOMOTIVES,
                            info.protocolCode(), String.valueOf(address), name + " (" + address + ")", name,
                            true, "", "", finalInterfaceName, node);
                    object.refreshLocomotiveLength();
                    if (findByItrainId(object) != null) {
                        continue;
                    }
                    objects.add(object);
                    added++;
                }
                updateFileLabel();
                updateState();
                if (added == 0 && total > 0) {
                    error(i18n.t("ecos.noNewLocos"));
                }
            });
        };
        if (connections.size() == 1) {
            read.accept(connections.get(0));
            return;
        }
        ContextMenu chooser = new ContextMenu();
        for (EcosConnection connection : connections) {
            MenuItem item = new MenuItem();
            item.textProperty().bind(connection.nameProperty());
            item.setOnAction(e -> read.accept(connection));
            chooser.getItems().add(item);
        }
        chooser.show(ecosLocoButton, javafx.geometry.Side.BOTTOM, 0, 0);
    }

    /**
     * "System-Datei laden" (Menue Systeme -> BiDiB bzw. ESU): eine Datei mit
     * dem Aufbau eines Systems in den Knotenbaum laden - ohne Verbindung.
     * BiDiB: Simulationsdatei des BiDiB-Wizard (.xml) oder eine eigene
     * System-Datei; ECoS: eigene System-Datei (die Sicherung der ECoS,
     * .eco, ist verschluesselt und laesst sich nicht lesen).
     */
    private void loadSystemFileToTree(String systemType) {
        boolean bidib = BidibSystemFile.SYSTEM_BIDIB.equals(systemType);
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n.t("systems.loadSystemFile"));
        if (bidib) {
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter(i18n.t("systems.loadFilterAll"),
                            "*.xml", "*" + BidibSystemFile.EXTENSION, "*.zip"),
                    new FileChooser.ExtensionFilter(i18n.t("systems.loadFilterWizard"), "*.xml"),
                    new FileChooser.ExtensionFilter(i18n.t("systems.fileFilter"), "*" + BidibSystemFile.EXTENSION));
        } else {
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter(i18n.t("systems.fileFilter"),
                            "*" + BidibSystemFile.EXTENSION, "*.zip"));
        }
        File dir = systemFilesDirectory();
        if (dir != null && dir.isDirectory()) {
            chooser.setInitialDirectory(dir);
        }
        File chosen = chooser.showOpenDialog(stage);
        if (chosen == null) {
            return;
        }
        try {
            BidibSystemFile loaded;
            if (BidibSimulationLoader.looksLikeSimulation(chosen)) {
                loaded = BidibSimulationLoader.load(chosen);
            } else {
                loaded = BidibSystemFile.load(chosen);
            }
            if (!bidib && !loaded.isEcos()) {
                error(i18n.t("systems.loadWrongSystem", "BiDiB", "ECoS"));
                return;
            }
            if (bidib && loaded.isEcos()) {
                error(i18n.t("systems.loadWrongSystem", "ECoS", "BiDiB"));
                return;
            }
            if (currentFile == null) {
                currentFile = loaded;
                updateFileLabel();
                updateState();
            }
            BidibNodeTreeWindow.showFromFile(stage, loaded);
        } catch (IOException | RuntimeException ex) {
            error(i18n.t("systems.fileError", String.valueOf(ex.getMessage())));
        }
    }


    /**
     * Bei genau einer offenen Verbindung sofort ausfuehren, bei mehreren
     * erst nach Auswahl ueber ein Klappmenue unter dem Knopf.
     */
    private void chooseConnection(Button anchor, java.util.function.Consumer<BidibConnection> action) {
        List<BidibConnection> connections = bidibLinks();
        if (connections.isEmpty()) {
            return;
        }
        if (connections.size() == 1) {
            action.accept(connections.get(0));
            return;
        }
        ContextMenu chooser = new ContextMenu();
        for (BidibConnection connection : connections) {
            MenuItem item = new MenuItem();
            item.textProperty().bind(connection.nameProperty());
            item.setOnAction(e -> action.accept(connection));
            chooser.getItems().add(item);
        }
        chooser.show(anchor, javafx.geometry.Side.BOTTOM, 0, 0);
    }

    private java.util.stream.Stream<BidibConnection> mc2LocoConnections() {
        return bidibLinks().stream()
                .filter(c -> c.isMc2() && c.isMc2LocoAvailable());
    }

    /**
     * "mc2 Lokomotiven auslesen": liest loco.ini per FTP (Fortschrittsfenster)
     * und haengt die benannten Lokomotiven direkt an die Tabelle "Lokomotiven"
     * an (kein Knotenbaum noetig - die mc2 liefert schon Name und Adresse je
     * Lok). Bei mehreren mc2-Verbindungen erst Auswahl ueber ein Klappmenue.
     * Referenziert dieselbe iTrain-Schnittstelle wie die bereits vorhandene
     * BiDiB-Schnittstelle dieser Verbindung (siehe {@link #interfaceNameFor}),
     * bei der cgi-Verbindung ohne solche Schnittstelle ersatzweise "mc2".
     */
    private void readMc2Locos() {
        withMc2Target(mc2LocoButton, this::readMc2Locos);
    }

    /** Eine erreichbare mc2: ueber cgi verbunden oder als netBiDiB-Verbindung. */
    private record Mc2Target(String label, String host, String interfaceFallback) {
    }

    /** Alle mc2-Ziele, cgi-Verbindung zuerst; derselbe Host nur einmal. */
    private List<Mc2Target> mc2Targets() {
        java.util.LinkedHashMap<String, Mc2Target> byHost = new java.util.LinkedHashMap<>();
        String cgi = cgiHost();
        if (cgi != null) {
            byHost.put(cgi, new Mc2Target(i18n.t("mc2.cgiStatus", cgi), cgi, "mc2"));
        }
        mc2LocoConnections().forEach(c -> byHost.putIfAbsent(c.getHost(),
                new Mc2Target(c.getName(), c.getHost(), c.getName())));
        return new ArrayList<>(byHost.values());
    }

    /** Bei einem Ziel direkt ausfuehren, bei mehreren erst Auswahl per Klappmenue am Knopf. */
    private void withMc2Target(javafx.scene.Node anchor, java.util.function.Consumer<Mc2Target> action) {
        List<Mc2Target> targets = mc2Targets();
        if (targets.isEmpty()) {
            return;
        }
        if (targets.size() == 1) {
            action.accept(targets.get(0));
            return;
        }
        ContextMenu chooser = new ContextMenu();
        for (Mc2Target target : targets) {
            MenuItem item = new MenuItem(target.label());
            item.setOnAction(e -> action.accept(target));
            chooser.getItems().add(item);
        }
        chooser.show(anchor, javafx.geometry.Side.BOTTOM, 0, 0);
    }

    private void readMc2Locos(Mc2Target target) {
        {
            String interfaceName = interfaceNameFor(target.interfaceFallback());
            // Vorgabe fuer die Spurweite: die globale Spurweite der geladenen
            // iTrain-Datei (Einstellungen), sonst Normalspur.
            String gauge = LocomotiveOptionsFields.globalGauge(
                    matchDocument == null ? null : matchDocument.getRoot());
            Mc2LocoReader.readWithProgress(stage, target.host(), interfaceName, gauge, result -> {
                if (currentFile == null) {
                    BidibSystemFile placeholder = new BidibSystemFile();
                    placeholder.setSystemType(BidibSystemFile.SYSTEM_BIDIB);
                    placeholder.setInterfaceName(interfaceName);
                    currentFile = placeholder;
                }
                int added = 0;
                for (SystemsObject object : result) {
                    if (findByItrainId(object) != null) {
                        continue;
                    }
                    objects.add(object);
                    added++;
                }
                updateFileLabel();
                updateState();
                if (added == 0 && !result.isEmpty()) {
                    error(i18n.t("mc2.noNewLocos"));
                }
            });
        }
    }

    /**
     * Der iTrain-Schnittstellenname, der zu dieser Verbindung gehoert: der
     * Name der schon vorhandenen BiDiB-Schnittstelle in den Tabellen (Andres
     * Ablauf: erst "Auslesen", dann "mc2 Lokomotiven auslesen" - die
     * Schnittstelle existiert also normalerweise schon), sonst ersatzweise
     * der Anzeigename der Verbindung.
     */
    private String interfaceNameFor(String fallback) {
        for (SystemsObject object : objects) {
            if (object.isInterface() && !"ecos".equals(object.getType())) {
                return object.getInterfaceName();
            }
        }
        return fallback;
    }

    /** Verbindungen, an deren Eigenschaften dieses Fenster schon horcht (nicht doppelt anmelden). */
    private final java.util.Set<Object> watched = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    /**
     * Statusleiste (nur die Verbindungen DIESES Fensters), RX/TX-Menue und
     * Fenstertitel neu aufbauen.
     */
    private void rebuildBidibUi() {
        bidibStatusBox.getChildren().clear();
        bidibRawLogMenu.getItems().clear();
        updateTitle();
        if (links.isEmpty()) {
            Label none = new Label(i18n.t("systems.statusNoConnection"));
            none.setStyle("-fx-opacity: 0.7;");
            bidibStatusBox.getChildren().add(none);
        }
        for (BidibConnection connection : bidibLinks()) {
            MenuItem rawItem = new MenuItem();
            rawItem.textProperty().bind(connection.nameProperty());
            rawItem.setOnAction(e -> BidibRawLogWindow.show(stage, connection));
            bidibRawLogMenu.getItems().add(rawItem);
        }
        for (BidibConnection connection : bidibLinks()) {
            Label nameLabel = new Label();
            nameLabel.textProperty().bind(connection.nameProperty());
            Circle indicator = new Circle(5);
            indicator.fillProperty().bind(Bindings.createObjectBinding(
                    () -> connection.isConnected() ? Color.web("#2e9e4f") : Color.web("#cc3333"),
                    connection.connectedProperty()));
            HBox entry = new HBox(5, nameLabel, indicator);
            entry.setAlignment(Pos.CENTER_LEFT);
            bidibStatusBox.getChildren().add(entry);
            // Der Knopf "mc2 Lokomotiven auslesen" erscheint erst, sobald der
            // FTP-Zugriff im Hintergrund erfolgreich geprueft wurde (siehe
            // BidibConnection.resolveNameInBackground) - das kann nach diesem
            // Aufbau noch nachkommen, deshalb hier auf die Eigenschaft hoeren.
            if (watched.add(connection)) {
                connection.mc2LocoAvailableProperty().addListener((obs, old, value) -> updateState());
                connection.nameProperty().addListener((obs, old, value) -> updateTitle());
            }
        }
        String cgi = cgiHost();
        if (cgi != null) {
            Circle indicator = new Circle(5, Color.web("#2e9e4f"));
            HBox entry = new HBox(5, new Label(i18n.t("mc2.cgiStatus", cgi)), indicator);
            entry.setAlignment(Pos.CENTER_LEFT);
            bidibStatusBox.getChildren().add(entry);
        }
        for (EcosConnection connection : ecosLinks()) {
            Label nameLabel = new Label();
            nameLabel.textProperty().bind(connection.nameProperty());
            Circle indicator = new Circle(5);
            indicator.fillProperty().bind(Bindings.createObjectBinding(
                    () -> connection.isConnected() ? Color.web("#2e9e4f") : Color.web("#cc3333"),
                    connection.connectedProperty()));
            HBox entry = new HBox(5, nameLabel, indicator);
            entry.setAlignment(Pos.CENTER_LEFT);
            bidibStatusBox.getChildren().add(entry);
            // Knopf "ECoS Lokomotiven auslesen" folgt dem Verbindungsstatus.
            if (watched.add(connection)) {
                connection.connectedProperty().addListener((obs, old, value) -> updateState());
                connection.nameProperty().addListener((obs, old, value) -> updateTitle());
            }
        }
        updateState();
    }

    /** Titel: Hauptfenster "Systeme", weitere Fenster mit dem Namen ihrer Verbindung(en). */
    private void updateTitle() {
        if (isMain && links.isEmpty()) {
            stage.setTitle(i18n.t("window.systemsTitle"));
            return;
        }
        List<String> names = new ArrayList<>();
        for (Link link : links) {
            names.add(link.name());
        }
        stage.setTitle(names.isEmpty() ? i18n.t("window.systemsTitle")
                : i18n.t("window.systemsTitle") + " - " + String.join(", ", names));
    }

    /**
     * "Lokomotiven zur mc2 uebertragen" (seit 13.10.2026): loco.ini und die
     * Symbolliste der mc2 lesen, Gegenueberstellung zeigen
     * ({@link Mc2TransferDialog}), dann schreiben ({@link Mc2LocoWriter} -
     * mit Sicherung der bisherigen loco.ini auf der mc2, hoechstens 10).
     */
    private void writeMc2Locos() {
        TcdDocument document = mainDocument();
        if (document == null) {
            return;
        }
        withMc2Target(mc2WriteButton, target -> {
            String host = target.host();
            // Lesen per HTTP (cgi, wie die Weboberflaeche): Lokliste, Daten je
            // Lok, Symbolliste. Geschrieben wird ebenfalls per cgi - siehe
            // Mc2LocoWriter.
            record Mc2State(java.util.Map<Integer, Mc2Http.LocoInfo> locos, String texteJs) {
            }
            runWithProgress(i18n.t("mc2.reading"), () -> {
                java.util.Map<Integer, Mc2Http.LocoInfo> locos = new java.util.TreeMap<>();
                for (int address : Mc2Http.locoAddresses(host)) {
                    Mc2Http.LocoInfo info = Mc2Http.locoInfo(host, address);
                    if (info != null) {
                        locos.put(address, info);
                    }
                }
                String texteJs;
                try {
                    texteJs = Mc2Http.get(host, "/texte.js");
                } catch (IOException ex) {
                    texteJs = "";
                }
                return new Mc2State(locos, texteJs);
            }, state -> {
                java.util.Optional<List<Mc2LocoWriter.Profile>> profiles =
                        Mc2TransferDialog.show(stage, document.getRoot(), state.locos(), state.texteJs());
                if (profiles.isEmpty()) {
                    return;
                }
                runWithProgress(i18n.t("mc2.writing"),
                        () -> Mc2LocoWriter.transfer(host, profiles.get(), state.locos()),
                        result -> {
                            lastTransfer = result;
                            updateState();
                            // Wirksam sofort (cgi); der Neustart-Knopf bleibt
                            // als Moeglichkeit (Befehl wie "reboot mc²").
                            ButtonType rebootButton = new ButtonType(i18n.t("mc2.rebootNow"),
                                    ButtonBar.ButtonData.OTHER);
                            ButtonType laterButton = new ButtonType(i18n.t("mc2.writeClose"),
                                    ButtonBar.ButtonData.CANCEL_CLOSE);
                            Alert done = new Alert(result.failed.isEmpty()
                                    ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING,
                                    transferMessage(result) + "\n\n" + i18n.t("mc2.rebootHint"),
                                    rebootButton, laterButton);
                            done.initOwner(stage);
                            done.setTitle(i18n.t("mc2.writeTitle"));
                            done.setHeaderText(i18n.t("mc2.writeLive"));
                            if (done.showAndWait().orElse(laterButton) == rebootButton) {
                                runWithProgress(i18n.t("mc2.rebooting"), () -> {
                                    Mc2Http.reboot(host);
                                    return Boolean.TRUE;
                                }, ok -> {
                                    Alert info = new Alert(Alert.AlertType.INFORMATION, i18n.t("mc2.rebootSent"));
                                    info.initOwner(stage);
                                    info.setHeaderText(null);
                                    info.setTitle(i18n.t("mc2.writeTitle"));
                                    info.showAndWait();
                                });
                            }
                        });
            });
        });
    }

    /** Meldung nach dem Schreiben in eine Zentrale (Anzahl, Sicherung, ggf. Fehler). */
    private String transferMessage(CentralUndo result) {
        String text = i18n.t("central.writeDone", result.transferredCount(),
                result.backupFile.getAbsolutePath());
        if (!result.failed.isEmpty()) {
            text += "\n\n" + i18n.t("central.writePartlyFailed", String.join(", ", result.failed));
        }
        return text;
    }

    /**
     * "Lokomotiven zur ECoS uebertragen" (seit 13.10.2026): Lokliste der
     * ECoS lesen, Gegenueberstellung zeigen ({@link Mc2TransferDialog} ohne
     * Funktionssymbole), dann schreiben ({@link EcosLocoWriter} - vorher
     * Sicherung der Lokliste auf dem PC).
     */
    private void writeEcosLocos() {
        TcdDocument document = mainDocument();
        List<EcosConnection> connections = ecosLinks().stream()
                .filter(EcosConnection::isConnected).collect(java.util.stream.Collectors.toList());
        if (document == null || connections.isEmpty()) {
            return;
        }
        EcosConnection connection = connections.get(0);
        String title = i18n.t("central.writeTitle", "ECoS");
        runWithProgress(title, i18n.t("ecos.readingLocos"),
                () -> EcosLocoWriter.readLocos(connection.getClient()),
                locos -> {
                    Map<Integer, String> names = new java.util.HashMap<>();
                    for (EcosLocoWriter.EcosLoco loco : locos.values()) {
                        names.putIfAbsent(loco.address(), loco.name());
                    }
                    java.util.Optional<List<Mc2LocoWriter.Profile>> profiles = Mc2TransferDialog.show(stage,
                            document.getRoot(), names, null, "ECoS", EcosLocoWriter::protocolFor);
                    if (profiles.isEmpty()) {
                        return;
                    }
                    runWithProgress(title, i18n.t("central.writing", "ECoS"),
                            () -> EcosLocoWriter.transfer(connection, profiles.get()),
                            result -> {
                                lastTransfer = result;
                                updateState();
                                Alert done = new Alert(result.failed.isEmpty()
                                        ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING,
                                        transferMessage(result));
                                done.initOwner(stage);
                                done.setTitle(title);
                                done.setHeaderText(null);
                                done.showAndWait();
                            });
                });
    }

    /**
     * Bearbeiten -&gt; Rueckgaengig (seit 13.10.2026): die letzte Uebertragung
     * in eine Zentrale aus der Sicherung auf dem PC zuruecknehmen - erst die
     * Tabelle "Diesen Inhalt in die Zentralen uebertragen?" mit "Abbruch" und
     * "Austauschen".
     */
    private void undoTransfer() {
        CentralUndo undo = lastTransfer;
        if (undo == null) {
            return;
        }
        try {
            if (!undo.confirm(stage)) {
                return;
            }
        } catch (IOException ex) {
            error(i18n.t("central.undoFailed", String.valueOf(ex.getMessage())));
            return;
        }
        EcosConnection ecos = null;
        if (undo.kind == CentralUndo.Kind.ECOS) {
            for (EcosConnection connection : ecosLinks()) {
                if (undo.host.equals(connection.getHost()) && connection.isConnected()) {
                    ecos = connection;
                }
            }
            if (ecos == null) {
                error(i18n.t("central.undoNotConnected", undo.host));
                return;
            }
        }
        EcosConnection ecosConnection = ecos;
        String title = i18n.t("central.undoTitle", undo.kind.label + " (" + undo.host + ")");
        runWithProgress(title, i18n.t("central.undoing"),
                () -> undo.kind == CentralUndo.Kind.MC2 ? Mc2LocoWriter.undo(undo)
                        : EcosLocoWriter.undo(ecosConnection, undo),
                failed -> {
                    if (failed.isEmpty()) {
                        lastTransfer = null;
                    }
                    updateState();
                    String text = i18n.t("central.undoDone", undo.overwritten.size(), undo.created.size());
                    if (!failed.isEmpty()) {
                        text += "\n\n" + i18n.t("central.writePartlyFailed", String.join(", ", failed));
                    }
                    Alert done = new Alert(failed.isEmpty() ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING,
                            text);
                    done.initOwner(stage);
                    done.setTitle(title);
                    done.setHeaderText(null);
                    done.showAndWait();
                });
    }

    /** Blockierende Arbeit im Hintergrund mit kleinem Fortschrittsfenster; Fehler als Meldung. */
    private <T> void runWithProgress(String text, java.util.concurrent.Callable<T> work,
            java.util.function.Consumer<T> onDone) {
        runWithProgress(i18n.t("mc2.writeTitle"), text, work, onDone);
    }

    private <T> void runWithProgress(String title, String text, java.util.concurrent.Callable<T> work,
            java.util.function.Consumer<T> onDone) {
        Stage dialog = new Stage();
        dialog.initOwner(stage);
        dialog.initModality(javafx.stage.Modality.WINDOW_MODAL);
        dialog.setTitle(title);
        dialog.setResizable(false);
        javafx.scene.control.ProgressIndicator indicator = new javafx.scene.control.ProgressIndicator();
        indicator.setPrefSize(28, 28);
        VBox box = new VBox(14, new Label(text), indicator);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new javafx.geometry.Insets(20));
        Scene scene = new Scene(new BorderPane(box));
        ThemeManager.apply(scene, settings.getTheme());
        dialog.setScene(scene);
        dialog.show();
        Thread worker = new Thread(() -> {
            try {
                T result = work.call();
                javafx.application.Platform.runLater(() -> {
                    dialog.close();
                    onDone.accept(result);
                });
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() -> {
                    dialog.close();
                    error(title + ":\n" + ex.getMessage());
                });
            }
        }, "mc2-write");
        worker.setDaemon(true);
        worker.start();
    }

    private void error(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.initOwner(stage);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private static Image[] loadAppIcons() {
        try (InputStream in = SystemsWindow.class.getResourceAsStream("app-icon.png")) {
            return in == null ? new Image[0] : new Image[]{new Image(in)};
        } catch (IOException ex) {
            return new Image[0];
        }
    }
}
