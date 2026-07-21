package com.example.itrain_import_export;

import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Line;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Baut die komplette Bearbeitungsoberfläche für eine control-items-Kategorie
 * (z.B. "blocks"). Da sich Aufbau und Unter-Kategorien je Eintrag stark
 * unterscheiden können (siehe accessories: turnout/signal/relay/... oder
 * blocks: verschachtelte prev/next/side-block/turnout-Bäume), arbeitet dieser
 * Editor rein generisch über {@link XmlNode} statt über feste Java-Modelle
 * je Kategorie.
 * <p>
 * Die Kategorie muss in der geladenen Datei nicht vorhanden sein - der Reiter
 * wird trotzdem angezeigt (leer). Erst wenn tatsächlich ein Eintrag angelegt
 * oder importiert wird, wird das zugehörige XML-Element unter control-items
 * angelegt ({@link #ensureCategoryNode()}); so bleiben unbenutzte Kategorien
 * beim Speichern unsichtbar, statt leere Elemente in die Datei zu schreiben.
 */
public class CategoryEditor {

    /** Für Kategorien ohne bestehende Einträge: sinnvoller Standard-Tag für neue Einträge. */
    private static final Map<String, String> DEFAULT_TAG_BY_CATEGORY = Map.ofEntries(
            Map.entry("functions", "function"),
            Map.entry("interfaces", "interface"),
            Map.entry("boosters", "booster"),
            Map.entry("feedbacks", "feedback"),
            Map.entry("measurements", "measurement"),
            Map.entry("accessories", "turnout"),
            Map.entry("memory", "track"),
            Map.entry("actions", "action"),
            Map.entry("locomotives", "locomotive"),
            Map.entry("wagons", "wagon"),
            Map.entry("trains", "train"),
            Map.entry("train-types", "train-type"),
            Map.entry("train-routes", "route"),
            Map.entry("blocks", "block"),
            Map.entry("stations", "station")
    );

    private final String categoryName;
    private final XmlNode controlItemsNode;
    private final I18n i18n = I18n.getInstance();
    /** Wird bei jeder tatsächlichen inhaltlichen Änderung aufgerufen (siehe {@link TcdDocument#markDirty()}). */
    private final Runnable onModified;
    /**
     * Wird aufgerufen, wenn sich durch diesen Editor die Struktur ANDERER
     * Kategorien geändert hat (z.B. durch einen Mehrfach-Kategorie-Import,
     * siehe {@link #onImport()}) - damit deren eigene, bereits aufgebaute
     * Reiter neu an den aktuellen XML-Baum gebunden werden.
     */
    private final Runnable onStructuralChange;
    /**
     * Wird VOR jeder tatsächlichen inhaltlichen Änderung aufgerufen, damit
     * der aktuelle Stand für "Rückgängig" gesichert werden kann (siehe
     * {@code HelloController.recordUndoSnapshot()}).
     */
    private final Runnable beforeChange;

    private final TableView<XmlNode> entryTable = new TableView<>();
    private final TreeView<XmlNode> detailTree = new TreeView<>();
    private final TextField tagField = new TextField();
    private final TextField textContentField = new TextField();
    private final GridPane tagGrid = new GridPane();
    private final SimpleIntegerProperty entryCount = new SimpleIntegerProperty(0);
    private final SimpleIntegerProperty selectedCount = new SimpleIntegerProperty(0);

    /**
     * Spezialisierte Tabelle für {@code <configuration>}-Knoten von
     * Lokomotiven (Spalten Aktiv/Nr/Wert/Typ/Beschreibung statt der
     * generischen Tag-/Textinhalt-Felder) - ersetzt {@link #tagGrid} im
     * "Daten-Einstellen"-Bereich, sobald im Daten-Explorer ein
     * {@code configuration}-Knoten ausgewählt ist. Andere Kategorien folgen
     * später mit eigenen spezialisierten Ansichten.
     */
    private final TableView<XmlNode> configTable = new TableView<>();
    private final VBox configPane = new VBox();
    private XmlNode currentConfigNode;

    /** Kann null sein, solange diese Kategorie in der Datei nicht existiert. */
    private XmlNode categoryNode;
    private XmlNode selectedTreeNode;
    private int lastClickedIndex = -1;

    public CategoryEditor(String categoryName, XmlNode controlItemsNode, Runnable onModified,
                           Runnable onStructuralChange, Runnable beforeChange) {
        this.categoryName = categoryName;
        this.controlItemsNode = controlItemsNode;
        this.onModified = onModified;
        this.onStructuralChange = onStructuralChange;
        this.beforeChange = beforeChange;
        this.categoryNode = controlItemsNode.findChild(categoryName);
    }

    public ReadOnlyIntegerProperty entryCountProperty() {
        return entryCount;
    }

    /** Anzahl der aktuell markierten Einträge in der Tabelle dieser Kategorie. */
    public ReadOnlyIntegerProperty selectedCountProperty() {
        return selectedCount;
    }

    public String getDisplayName() {
        return categoryDisplayName();
    }

    private String categoryDisplayName() {
        String key = "category." + categoryName;
        String translated = i18n.t(key);
        if (translated.equals(key)) {
            // Keine Übersetzung hinterlegt - kommt vor bei Kategorien, die
            // uns (noch) nicht bekannt sind. Dann wenigstens lesbar
            // formatieren statt den rohen Übersetzungsschlüssel anzuzeigen.
            return prettifyTagName(categoryName);
        }
        return translated;
    }

    private static String prettifyTagName(String tag) {
        String[] parts = tag.split("-");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }

    public Tab createTab() {
        Tab tab = new Tab(categoryDisplayName());
        tab.setClosable(false);
        tab.setContent(buildContent());
        return tab;
    }

    /** Legt das XML-Element für diese Kategorie an, falls es noch nicht existiert. */
    private XmlNode ensureCategoryNode() {
        if (categoryNode == null) {
            categoryNode = new XmlNode(categoryName);
            controlItemsNode.getChildren().add(categoryNode);
            bindEntryItems(categoryNode.getChildren());
        }
        return categoryNode;
    }

    /**
     * Wie {@link #ensureCategoryNode()}, aber für eine BELIEBIGE Kategorie
     * (nicht nur die dieses Editors) - wird beim Mehrfach-Kategorie-Import
     * gebraucht, wenn eine importierte Zeile zu einer anderen Kategorie
     * gehört (siehe {@link #onImport()}).
     */
    private XmlNode ensureCategoryNodeGeneric(String category) {
        XmlNode existing = controlItemsNode.findChild(category);
        if (existing != null) {
            return existing;
        }
        XmlNode newCategoryNode = new XmlNode(category);
        controlItemsNode.getChildren().add(newCategoryNode);
        return newCategoryNode;
    }

    /** Prüft, ob eine Kategorie bereits einen direkten Eintrag mit diesem Tag-Namen + "name"-Attribut hat. */
    private static boolean categoryHasEntry(XmlNode category, String tag, String name) {
        for (XmlNode entry : category.getChildren()) {
            if (entry.getTagName().equals(tag) && name.equals(entry.getAttribute("name"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Findet zu einem Eintrag rekursiv alle Einträge, die er (an beliebiger
     * Stelle in seiner Struktur, nicht nur innerhalb von {@code <condition>}/
     * {@code <items>}) über Tag-Name + {@code name}-Attribut referenziert -
     * z.B. {@code <feedback name="RM1" change="on"/>} in einer Aktion oder
     * {@code <train-type name="DB Dampf GZ"/>} direkt unter einem Zug. In
     * iTrains Dateiformat ist der {@code name} pro Kategorie eindeutig und
     * dient genau so als Fremdschlüssel - jedes Element mit demselben
     * Tag-Namen + {@code name}-Wert wie ein tatsächlicher Eintrag einer
     * Kategorie IST eine Referenz auf diesen Eintrag, unabhängig davon, in
     * welchem umschließenden Element es steht. Es wird bewusst NICHT über
     * eine feste Tag→Kategorie-Zuordnung oder eine feste Liste erlaubter
     * Wrapper-Tags gearbeitet (Kategorien wie "accessories" enthalten
     * mehrere unterschiedliche Tags, und neue Referenzmuster wie
     * "train"→"train-type" kommen ohne Vorwarnung dazu), sondern direkt im
     * Dokument gesucht - das bleibt auch bei uns unbekannten/künftigen
     * Referenzmustern korrekt.
     * <p>
     * Wird auch auf neu gefundene Einträge rekursiv angewendet (falls diese
     * selbst wieder Referenzen enthalten, z.B. ein referenzierter Block, der
     * seinerseits Feedbacks/Signale/Weichen referenziert). Liefert eine
     * Zuordnung gefundener Eintrag → seine tatsächliche Kategorie, ohne die
     * ursprünglich übergebenen Einträge.
     */
    private Map<XmlNode, String> resolveLinkedEntries(List<XmlNode> selectedEntries) {
        Map<XmlNode, String> result = new LinkedHashMap<>();
        Set<XmlNode> alreadyHandled = new HashSet<>(selectedEntries);
        Deque<XmlNode> toScan = new ArrayDeque<>(selectedEntries);

        while (!toScan.isEmpty()) {
            XmlNode current = toScan.poll();
            for (XmlNode reference : allDescendants(current)) {
                String refName = reference.getAttribute("name");
                if (refName == null || refName.isBlank()) {
                    continue;
                }
                XmlNode found = findEntryByReference(reference.getTagName(), refName);
                if (found != null && !alreadyHandled.contains(found)) {
                    alreadyHandled.add(found);
                    String category = findCategoryNameOf(found);
                    if (category != null) {
                        result.put(found, category);
                        toScan.add(found);
                    }
                }
            }
        }
        return result;
    }

    /** Alle Nachfahren (rekursiv, alle Ebenen) eines Knotens. */
    private static List<XmlNode> allDescendants(XmlNode node) {
        List<XmlNode> result = new ArrayList<>();
        for (XmlNode child : node.getChildren()) {
            result.add(child);
            result.addAll(allDescendants(child));
        }
        return result;
    }

    /**
     * Löst eine Referenz (Tag + name-Attribut) zum tatsächlichen Eintrag auf.
     * Zuerst wird ein exakter Tag+Name-Treffer versucht (der Normalfall,
     * z.B. {@code <feedback name="RM1"/>} → {@code <feedback name="RM1">}
     * unter feedbacks). Manche Referenzen verwenden aber je nach
     * struktureller Rolle einen ANDEREN Tag-Namen als die eigentliche
     * Definition - z.B. referenziert ein Block im Rangier-Kontext ein
     * Signal als {@code <shunt-signal name="B67_RS_N"/>}, obwohl die
     * Definition selbst unter accessories als
     * {@code <signal name="B67_RS_N" type="de_sh0_1f">} steht (belegt durch
     * eine reale iTrain-Referenzdatei). Da {@code name} in iTrains
     * Datenmodell der eindeutige Objekt-Identifikator ist, wird als
     * Fallback ohne Tag-Bedingung gesucht - aber NUR als Fallback, da
     * gleichlautende Namen über Kategorien hinweg vorkommen können (z.B.
     * teilen sich eine Lok und der zugehörige Zug oft denselben Namen); der
     * exakte Tag-Treffer bleibt deshalb immer die erste Wahl.
     */
    private XmlNode findEntryByReference(String tag, String name) {
        XmlNode exact = findEntryByTagAndName(tag, name);
        if (exact != null) {
            return exact;
        }
        return findEntryByName(name);
    }

    /** Sucht in ALLEN Kategorien nach einem direkten Eintrag mit passendem "name"-Attribut, unabhängig vom Tag. */
    private XmlNode findEntryByName(String name) {
        for (XmlNode category : controlItemsNode.getChildren()) {
            for (XmlNode entry : category.getChildren()) {
                if (name.equals(entry.getAttribute("name"))) {
                    return entry;
                }
            }
        }
        return null;
    }

    /** Sucht in ALLEN Kategorien nach einem direkten Eintrag mit passendem Tag-Namen und "name"-Attribut. */
    private XmlNode findEntryByTagAndName(String tag, String name) {
        for (XmlNode category : controlItemsNode.getChildren()) {
            for (XmlNode entry : category.getChildren()) {
                if (entry.getTagName().equals(tag) && name.equals(entry.getAttribute("name"))) {
                    return entry;
                }
            }
        }
        return null;
    }

    /** Ermittelt, unter welcher Kategorie (direktes Kind von control-items) ein Eintrag tatsächlich steht. */
    private String findCategoryNameOf(XmlNode entry) {
        for (XmlNode category : controlItemsNode.getChildren()) {
            if (category.getChildren().contains(entry)) {
                return category.getTagName();
            }
        }
        return null;
    }

    private void bindEntryItems(ObservableList<XmlNode> items) {
        entryTable.setItems(items);
        items.addListener((ListChangeListener<XmlNode>) change -> entryCount.set(items.size()));
        entryCount.set(items.size());
    }

    private javafx.scene.Node buildContent() {
        SplitPane split = new SplitPane();
        split.setOrientation(Orientation.HORIZONTAL);
        split.getItems().addAll(buildListSide(), buildDetailSide());
        split.setDividerPositions(0.45);
        return split;
    }

    // ---------------------------------------------------------------
    // Linke Seite: Tabelle aller Einträge dieser Kategorie
    // ---------------------------------------------------------------

    private javafx.scene.Node buildListSide() {
        entryTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        entryTable.getSelectionModel().getSelectedIndices().addListener((ListChangeListener<Integer>) change ->
                selectedCount.set(entryTable.getSelectionModel().getSelectedIndices().size()));

        TableColumn<XmlNode, Void> selectColumn = buildSelectionColumn();

        TableColumn<XmlNode, String> typeColumn = new TableColumn<>(i18n.t("editor.columnType"));
        typeColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getTagName()));
        typeColumn.setPrefWidth(110);
        // Standardmäßig ausgeblendet - wir haben ohnehin eine Übersicht über
        // alle Kategorien, der Tag-Name ist meist redundant. Über
        // Einstellungen -> Ansicht -> "Typ anzeigen" einschaltbar.
        typeColumn.setVisible(AppSettings.getInstance().getShowTypeColumn());

        TableColumn<XmlNode, String> nameColumn = new TableColumn<>(i18n.t("editor.columnName"));
        // Verknüpfte, mit-importierte Einträge tragen jetzt bereits ein "~"
        // direkt im "name"-Attribut (siehe CategoryEditor.onExport /
        // renameReferencedChildren) - hier also einfach der echte Name, ohne
        // Sonderbehandlung.
        nameColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getName()));
        nameColumn.setPrefWidth(220);

        TableColumn<XmlNode, String> descColumn = new TableColumn<>(i18n.t("editor.columnDescription"));
        descColumn.setCellValueFactory(data -> {
            XmlNode description = data.getValue().findChild("description");
            String text = description != null && description.getTextContent() != null
                    ? description.getTextContent() : "";
            return new ReadOnlyStringWrapper(text);
        });
        descColumn.setPrefWidth(260);

        entryTable.getColumns().add(selectColumn);
        entryTable.getColumns().add(typeColumn);
        entryTable.getColumns().add(nameColumn);
        entryTable.getColumns().add(descColumn);
        bindEntryItems(categoryNode != null ? categoryNode.getChildren() : FXCollections.observableArrayList());
        entryTable.setPlaceholder(new Label(i18n.t("editor.noEntries")));

        entryTable.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> showDetail(newV));

        ContextMenu rowContextMenu = new ContextMenu();
        MenuItem exportItem = new MenuItem(i18n.t("editor.export"));
        exportItem.setOnAction(e -> onExport());
        rowContextMenu.getItems().add(exportItem);

        entryTable.setRowFactory(tv -> {
            javafx.scene.control.TableRow<XmlNode> row = new javafx.scene.control.TableRow<>();
            row.setOnMousePressed(event -> {
                if (row.isEmpty()) {
                    return;
                }
                if (event.getButton() == MouseButton.SECONDARY
                        && !entryTable.getSelectionModel().isSelected(row.getIndex())) {
                    entryTable.getSelectionModel().clearSelection();
                    entryTable.getSelectionModel().select(row.getIndex());
                    lastClickedIndex = row.getIndex();
                }
            });
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !row.isEmpty()) {
                    deleteEntry(row.getItem());
                }
            });
            row.setContextMenu(rowContextMenu);
            return row;
        });

        Button exportSelectedButton = new Button(i18n.t("editor.exportSelected"));
        exportSelectedButton.setOnAction(e -> onExport());

        Button importButton = new Button(i18n.t("editor.import"));
        importButton.setOnAction(e -> onImport());

        // "Neuer Eintrag" und "Löschen" als reine Symbol-Buttons ("+"/rotes
        // "X"), rechtsbündig über der Tabelle - der Tooltip trägt weiterhin
        // den vollen (übersetzten) Text.
        Button addButton = new Button("+");
        addButton.setOnAction(e -> onAddEntry());
        addButton.setTooltip(new Tooltip(i18n.t("editor.newEntry")));
        addButton.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-min-width: 32px;");

        Button deleteButton = new Button("X");
        deleteButton.setOnAction(e -> {
            XmlNode selected = entryTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                deleteEntry(selected);
            }
        });
        deleteButton.setTooltip(new Tooltip(i18n.t("editor.delete")));
        deleteButton.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-min-width: 32px; -fx-text-fill: #c0392b;");

        Region toolbarSpacer = new Region();
        HBox.setHgrow(toolbarSpacer, Priority.ALWAYS);

        HBox toolbar = new HBox(10, exportSelectedButton, importButton, toolbarSpacer, addButton, deleteButton);
        toolbar.setPadding(new Insets(8));

        Label hint = new Label(i18n.t("editor.deleteHint"));
        hint.setStyle("-fx-font-size: 11px; -fx-text-fill: gray;");

        VBox box = new VBox(toolbar, entryTable, hint);
        VBox.setVgrow(entryTable, javafx.scene.layout.Priority.ALWAYS);
        return box;
    }

    /**
     * Checkbox-Spalte für die Mehrfachauswahl (z.B. für Export). Die
     * Checkbox spiegelt/steuert die normale TableView-Selektion, damit
     * Shift (Bereich) und Strg (einzelne Einträge dazu/weg) genau wie
     * gewünscht funktionieren; Pfeiltasten + Leertaste funktionieren
     * bereits standardmäßig über die TableView-Selektion.
     */
    private TableColumn<XmlNode, Void> buildSelectionColumn() {
        TableColumn<XmlNode, Void> column = new TableColumn<>();
        column.setSortable(false);
        column.setPrefWidth(34);
        column.setMaxWidth(34);
        column.setMinWidth(34);
        // Standardmäßig ausgeblendet. Mehrfachauswahl funktioniert auch ohne
        // sichtbare Checkbox-Spalte weiterhin ganz normal über die
        // TableView-Selektion (Maus-Klick, Shift für Bereich, Strg für
        // einzelne Einträge dazu/weg). Über Einstellungen -> Ansicht ->
        // "Auswahlbox anzeigen" einschaltbar.
        column.setVisible(AppSettings.getInstance().getShowSelectionCheckbox());

        CheckBox selectAllBox = new CheckBox();
        selectAllBox.setOnAction(e -> {
            if (selectAllBox.isSelected()) {
                entryTable.getSelectionModel().selectAll();
            } else {
                entryTable.getSelectionModel().clearSelection();
            }
        });
        column.setGraphic(selectAllBox);

        entryTable.getSelectionModel().getSelectedIndices().addListener((ListChangeListener<Integer>) change -> {
            int total = entryTable.getItems().size();
            int selectedCount = entryTable.getSelectionModel().getSelectedIndices().size();
            selectAllBox.setIndeterminate(selectedCount > 0 && selectedCount < total);
            selectAllBox.setSelected(total > 0 && selectedCount == total);
            entryTable.refresh();
        });

        column.setCellFactory(col -> new TableCell<XmlNode, Void>() {
            private final CheckBox checkBox = new CheckBox();
            {
                checkBox.setOnMouseClicked(event -> {
                    int index = getIndex();
                    if (index < 0 || index >= getTableView().getItems().size()) {
                        return;
                    }
                    var selectionModel = getTableView().getSelectionModel();
                    if (event.isShiftDown() && lastClickedIndex >= 0) {
                        int from = Math.min(lastClickedIndex, index);
                        int to = Math.max(lastClickedIndex, index);
                        selectionModel.clearSelection();
                        for (int i = from; i <= to; i++) {
                            selectionModel.select(i);
                        }
                    } else if (event.isControlDown() || event.isMetaDown()) {
                        if (selectionModel.isSelected(index)) {
                            selectionModel.clearSelection(index);
                        } else {
                            selectionModel.select(index);
                        }
                    } else {
                        if (selectionModel.isSelected(index) && selectionModel.getSelectedIndices().size() == 1) {
                            selectionModel.clearSelection(index);
                        } else {
                            selectionModel.clearSelection();
                            selectionModel.select(index);
                        }
                    }
                    lastClickedIndex = index;
                    event.consume();
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    checkBox.setSelected(getTableView().getSelectionModel().getSelectedIndices().contains(getIndex()));
                    setGraphic(checkBox);
                }
            }
        });

        return column;
    }

    private Window getWindow() {
        return entryTable.getScene().getWindow();
    }

    private void onExport() {
        List<XmlNode> selected = new ArrayList<>(entryTable.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION, i18n.t("editor.noSelectionForExport")).showAndWait();
            return;
        }

        // Verknüpfte Einträge anderer Kategorien (condition/items, z.B. bei
        // Aktionen) müssen mit exportiert werden, sonst lässt sich die
        // Zieldatei nach dem Import in iTrain nicht mehr öffnen.
        Map<XmlNode, String> linkedEntries = resolveLinkedEntries(selected);

        ButtonType cancelButton = new ButtonType(i18n.t("editor.exportConfirmCancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType continueButton = new ButtonType(i18n.t("editor.exportConfirmContinue"), ButtonBar.ButtonData.OK_DONE);
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                i18n.t("editor.exportConfirmMessage", selected.size(), linkedEntries.size()),
                cancelButton, continueButton);
        confirm.setTitle(i18n.t("editor.exportConfirmTitle"));
        confirm.setHeaderText(null);
        Optional<ButtonType> confirmResult = confirm.showAndWait();
        if (confirmResult.isEmpty() || confirmResult.get() != continueButton) {
            return;
        }

        String exportDir = AppSettings.getInstance().getExportDirectory();
        if (exportDir == null || exportDir.isBlank() || !new File(exportDir).isDirectory()) {
            DirectoryChooser dirChooser = new DirectoryChooser();
            dirChooser.setTitle(i18n.t("editor.chooseExportDirTitle"));
            File chosen = dirChooser.showDialog(getWindow());
            if (chosen == null) {
                return;
            }
            exportDir = chosen.getAbsolutePath();
            AppSettings.getInstance().setExportDirectory(exportDir);
        }

        TextInputDialog nameDialog = new TextInputDialog(categoryName + "-export");
        nameDialog.setTitle(i18n.t("editor.exportFileNameTitle"));
        nameDialog.setHeaderText(i18n.t("editor.exportFileNameHeader", exportDir));
        Optional<String> nameResult = nameDialog.showAndWait();
        if (nameResult.isEmpty() || nameResult.get().isBlank()) {
            return;
        }
        String fileName = nameResult.get().trim();
        if (!fileName.toLowerCase().endsWith(".csv")) {
            fileName = fileName + ".csv";
        }
        File target = new File(exportDir, fileName);

        try {
            List<String> header = List.of("Kategorie",
                    i18n.t("editor.columnType"), i18n.t("editor.columnName"),
                    i18n.t("editor.columnDescription"), "XML");

            // Verknüpfte Einträge werden beim Export UMBENANNT (Original-Name
            // -> "~"+Original-Name) - sowohl ihre eigene Definition als auch
            // JEDE Stelle, an der sie innerhalb der exportierten Menge
            // referenziert werden (z.B. <feedback name="RM1"/> in einer
            // Aktion). So bleibt die Zieldatei nach dem Import in iTrain
            // konsistent und öffnbar, auch wenn der Nutzer die importierten
            // Daten (erkennbar am "~") noch nicht manuell bereinigt hat.
            // Gearbeitet wird auf tiefen KOPIEN - das Original im gerade
            // geöffneten Dokument bleibt unverändert.
            Map<String, String> renameMap = new LinkedHashMap<>();
            for (XmlNode linked : linkedEntries.keySet()) {
                String originalName = linked.getName();
                if (!originalName.isBlank()) {
                    renameMap.put(originalName, "~" + originalName);
                }
            }

            List<List<String>> rows = new ArrayList<>();
            for (XmlNode node : selected) {
                XmlNode copy = node.deepCopy();
                // Der direkt ausgewählte Eintrag selbst behält seinen Namen -
                // nur Referenzen DARIN auf verknüpfte Einträge werden angepasst.
                renameReferencedChildren(copy, renameMap);
                rows.add(toExportRow(categoryName, copy));
            }
            // Verknüpfte Einträge mit ihrer JEWEILS EIGENEN Kategorie anhängen
            // (nicht der Kategorie dieses Reiters), inklusive umbenannter
            // eigener Definition und ggf. umbenannter eigener Referenzen.
            for (Map.Entry<XmlNode, String> linked : linkedEntries.entrySet()) {
                XmlNode copy = linked.getKey().deepCopy();
                String renamedName = renameMap.get(copy.getName());
                if (renamedName != null) {
                    copy.setAttribute("name", renamedName);
                }
                renameReferencedChildren(copy, renameMap);
                rows.add(toExportRow(linked.getValue(), copy));
            }
            CsvUtil.write(target, header, rows);
            new Alert(Alert.AlertType.INFORMATION,
                    i18n.t("editor.exportSuccess", rows.size(), target.getAbsolutePath()))
                    .showAndWait();
        } catch (Exception ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR, String.valueOf(ex.getMessage()));
            alert.setHeaderText(i18n.t("editor.exportErrorTitle"));
            alert.showAndWait();
        }
    }

    /** Baut eine CSV-Export-Zeile (Kategorie/Typ/Name/Beschreibung/XML) für einen Eintrag. */
    private static List<String> toExportRow(String category, XmlNode node) throws Exception {
        XmlNode description = node.findChild("description");
        String descText = description != null && description.getTextContent() != null
                ? description.getTextContent() : "";
        String xml = TcdDocument.nodeToXmlString(node);
        return List.of(category, node.getTagName(), node.getName(), descText, xml);
    }

    /**
     * Benennt in ALLEN Nachfahren (nicht im übergebenen Knoten selbst) jedes
     * "name"-Attribut um, das ein Schlüssel in renameMap ist - für
     * Referenzen auf verknüpfte Einträge innerhalb eines exportierten
     * Eintrags (z.B. {@code <feedback name="RM1"/>} in einer Aktion wird zu
     * {@code <feedback name="~RM1"/>}, wenn RM1 verknüpft und daher
     * umbenannt wird). Wird sowohl auf direkt ausgewählte als auch auf
     * verknüpfte Einträge angewendet, damit auch verknüpfte Einträge, die
     * ihrerseits weitere verknüpfte Einträge referenzieren, konsistent
     * bleiben.
     */
    private static void renameReferencedChildren(XmlNode node, Map<String, String> renameMap) {
        for (XmlNode child : node.getChildren()) {
            String currentName = child.getAttribute("name");
            if (currentName != null) {
                String renamed = renameMap.get(currentName);
                if (renamed != null) {
                    child.setAttribute("name", renamed);
                }
            }
            renameReferencedChildren(child, renameMap);
        }
    }

    /**
     * Öffentlicher Zugriff auf den CSV-Import von außerhalb dieses Editors -
     * genutzt vom Menüpunkt "Export Dateien" in {@link HelloController}, der
     * so denselben Import auslösen kann, ohne dass zuvor zu diesem
     * bestimmten Kategorie-Reiter gewechselt werden muss (die CSV-Zeilen
     * sortieren sich beim Import ohnehin anhand ihrer eigenen
     * Kategorie-Spalte selbst ein, unabhängig davon, welcher Editor den
     * Import ausgelöst hat).
     */
    public void triggerImport() {
        onImport();
    }

    private void onImport() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n.t("editor.import"));
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("CSV (*.csv)", "*.csv"),
                new FileChooser.ExtensionFilter("*.*", "*.*"));
        String exportDir = AppSettings.getInstance().getExportDirectory();
        if (exportDir != null && new File(exportDir).isDirectory()) {
            chooser.setInitialDirectory(new File(exportDir));
        }
        File source = chooser.showOpenDialog(getWindow());
        if (source == null) {
            return;
        }

        try {
            List<List<String>> rows = CsvUtil.read(source);
            List<List<String>> dataRows = rows.size() > 1 ? rows.subList(1, rows.size()) : List.of();

            // Format-Check: unsere Exporte haben immer 5 Spalten, die erste
            // ist die Kategorie. Ohne diese Spalte können wir nicht sicher
            // sagen, wohin die Daten gehören - also lieber abbrechen statt
            // zu raten (das war die Ursache für falsch einsortierte/
            // unlesbare Dateien).
            for (List<String> row : dataRows) {
                if (row.size() < 5) {
                    throw new IllegalStateException(i18n.t("editor.importInvalidFormat"));
                }
            }

            // Eine Export-Datei kann (seit der Verknüpfung verwandter
            // Einträge, siehe resolveLinkedEntries) Zeilen aus MEHREREN
            // Kategorien enthalten - jede Zeile wird daher anhand ihrer
            // eigenen Kategorie-Spalte einsortiert, statt (wie früher) eine
            // einheitliche Kategorie über die ganze Datei zu verlangen. Der
            // aktuell geöffnete Reiter spielt für das Ziel keine Rolle mehr.
            if (!dataRows.isEmpty()) {
                // Ein Import gilt als EIN Rückgängig-Schritt, nicht einer je
                // Zeile - der Nutzer will einen kompletten Import mit einem
                // "Rückgängig" wieder loswerden können.
                beforeChange.run();
            }
            boolean touchesOtherCategories = false;
            int imported = 0;
            int skippedDuplicates = 0;
            for (List<String> row : dataRows) {
                String rowCategory = row.get(0);
                XmlNode categoryTarget = rowCategory.equals(categoryName)
                        ? ensureCategoryNode() : ensureCategoryNodeGeneric(rowCategory);
                if (!rowCategory.equals(categoryName)) {
                    touchesOtherCategories = true;
                }
                XmlNode node = TcdDocument.xmlStringToNode(row.get(4));
                // iTrain verlangt pro Kategorie eindeutige Namen (siehe
                // "Duplicate ... elements"-Fehler) - ein per Verknüpfung
                // mit-exportierter Eintrag, der im Ziel bereits existiert
                // (z.B. weil er dort schon vorhanden war), wird daher nicht
                // ein zweites Mal eingefügt. Einträge ohne name-Attribut
                // werden davon nicht betroffen (kein eindeutiger Schlüssel).
                String nodeName = node.getName();
                if (!nodeName.isBlank() && categoryHasEntry(categoryTarget, node.getTagName(), nodeName)) {
                    skippedDuplicates++;
                    continue;
                }
                categoryTarget.getChildren().add(node);
                imported++;
            }
            if (imported > 0) {
                onModified.run();
            }
            if (touchesOtherCategories) {
                // Andere Reiter sind bereits (evtl. leer) an ihren alten
                // XML-Stand gebunden - komplett neu aufbauen, damit sie die
                // gerade importierten Einträge auch anzeigen.
                onStructuralChange.run();
            }
            String successMessage = skippedDuplicates > 0
                    ? i18n.t("editor.importSuccessWithSkipped", imported, skippedDuplicates)
                    : i18n.t("editor.importSuccess", imported);
            new Alert(Alert.AlertType.INFORMATION, successMessage).showAndWait();
        } catch (Exception ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR, String.valueOf(ex.getMessage()));
            alert.setHeaderText(i18n.t("editor.importErrorTitle"));
            alert.showAndWait();
        }
    }

    private void onAddEntry() {
        ObservableList<String> options = FXCollections.observableArrayList();
        options.add(i18n.t("editor.newEntryChoiceEmpty"));
        if (categoryNode != null) {
            for (XmlNode existing : categoryNode.getChildren()) {
                options.add(i18n.t("editor.newEntryChoiceTemplate", existing.getTagName(), existing.getName()));
            }
        }

        ChoiceDialog<String> choiceDialog = new ChoiceDialog<>(options.get(0), options);
        choiceDialog.setTitle(i18n.t("editor.newEntryTitle"));
        choiceDialog.setHeaderText(i18n.t("editor.newEntryHeader", categoryDisplayName()));
        choiceDialog.setContentText(i18n.t("editor.newEntryChoiceLabel"));
        Optional<String> choice = choiceDialog.showAndWait();
        if (choice.isEmpty()) {
            return;
        }

        XmlNode newNode;
        int index = options.indexOf(choice.get());
        if (index <= 0) {
            // Kein Tag mehr abfragen - immer automatisch den für diese
            // Kategorie typischen Tag verwenden (bestehender Eintrag als
            // Vorbild, sonst Standard-Zuordnung).
            String defaultTag = (categoryNode != null && !categoryNode.getChildren().isEmpty())
                    ? categoryNode.getChildren().get(0).getTagName()
                    : DEFAULT_TAG_BY_CATEGORY.getOrDefault(categoryName, categoryName);
            newNode = new XmlNode(defaultTag);
        } else {
            newNode = categoryNode.getChildren().get(index - 1).deepCopy();
        }

        String defaultName = newNode.getName().isEmpty()
                ? i18n.t("editor.newEntryDefaultName")
                : i18n.t("editor.newEntryCopyOf", newNode.getName());
        TextInputDialog nameDialog = new TextInputDialog(defaultName);
        nameDialog.setTitle(i18n.t("editor.newEntryTitle"));
        nameDialog.setHeaderText(i18n.t("editor.newEntryNameHeader"));
        Optional<String> name = nameDialog.showAndWait();
        if (name.isEmpty() || name.get().isBlank()) {
            return;
        }
        newNode.setAttribute("name", name.get().trim());

        beforeChange.run();
        ensureCategoryNode().getChildren().add(newNode);
        onModified.run();
        entryTable.getSelectionModel().select(newNode);
        entryTable.scrollTo(newNode);
    }

    private void deleteEntry(XmlNode node) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                i18n.t("editor.confirmDeleteEntry", node.getName(), node.getTagName()),
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle(i18n.t("editor.confirmDeleteEntryTitle"));
        confirm.setHeaderText(null);
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            beforeChange.run();
            categoryNode.getChildren().remove(node);
            onModified.run();
            if (node == selectedTreeNode) {
                showDetail(null);
            }
        }
    }

    // ---------------------------------------------------------------
    // Rechte Seite: Baumstruktur der Daten des ausgewählten Eintrags
    // ---------------------------------------------------------------

    private javafx.scene.Node buildDetailSide() {
        detailTree.setShowRoot(true);
        detailTree.setCellFactory(tv -> new GuideLineTreeCell());
        detailTree.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                selectNode(newV.getValue());
            }
        });

        VBox treeBox = new VBox(new Label(i18n.t("editor.structureLabel")), detailTree);
        VBox.setVgrow(detailTree, javafx.scene.layout.Priority.ALWAYS);

        textContentField.textProperty().addListener((obs, oldV, newV) -> {
            if (selectedTreeNode != null) {
                // Vergleich mit dem bisherigen Wert nötig, da setText() beim
                // Wechsel der Auswahl (selectNode()) denselben Listener auch
                // ohne echte Nutzer-Änderung auslöst - sonst würde jeder
                // Klick auf einen anderen Eintrag fälschlich als Änderung
                // gezählt (relevant für das Backup-Aufräumen beim Öffnen).
                String newContent = newV.isEmpty() ? null : newV;
                if (!java.util.Objects.equals(selectedTreeNode.getTextContent(), newContent)) {
                    beforeChange.run();
                    selectedTreeNode.setTextContent(newContent);
                    onModified.run();
                }
            }
        });

        tagGrid.setHgap(8);
        tagGrid.setVgap(8);
        tagGrid.setPadding(new Insets(8));
        tagGrid.addRow(0, new Label(i18n.t("editor.tag")), tagField);
        tagGrid.addRow(1, new Label(i18n.t("editor.textContent")), textContentField);
        tagField.setOnAction(e -> commitTagName());
        tagField.focusedProperty().addListener((obs, was, is) -> {
            if (!is) {
                commitTagName();
            }
        });

        buildConfigPane();

        StackPane detailContentStack = new StackPane(tagGrid, configPane);

        Label dataEditorLabel = new Label(i18n.t("editor.dataEditorLabel"));
        dataEditorLabel.setStyle("-fx-font-weight: bold;");
        VBox detailBox = new VBox(dataEditorLabel, detailContentStack);
        VBox.setVgrow(detailContentStack, javafx.scene.layout.Priority.ALWAYS);

        SplitPane detailSplit = new SplitPane();
        detailSplit.setOrientation(Orientation.HORIZONTAL);
        detailSplit.getItems().add(treeBox);
        // "Daten ändern"-Bereich standardmäßig ausgeblendet (siehe
        // Einstellungen -> Ansicht -> "Daten ändern") - die Ansicht ist dann
        // nur noch zweigeteilt (Liste + Daten-Explorer) statt dreigeteilt.
        // Da SplitPane unsichtbare Kinder nicht wie andere Layouts einfach
        // wegblendet, wird der Bereich hier nur bei Bedarf überhaupt erst
        // hinzugefügt statt nur ausgeblendet.
        if (AppSettings.getInstance().getShowDataEditor()) {
            detailSplit.getItems().add(detailBox);
            detailSplit.setDividerPositions(0.55);
        }

        BorderPane pane = new BorderPane(detailSplit);
        pane.setPadding(new Insets(0, 0, 0, 8));
        return pane;
    }

    private void showDetail(XmlNode entry) {
        if (entry == null) {
            detailTree.setRoot(null);
            selectNode(null);
            return;
        }
        TreeItem<XmlNode> rootItem = buildTreeItem(entry, true);
        detailTree.setRoot(rootItem);
        selectNode(entry);
        detailTree.getSelectionModel().select(rootItem);
    }

    /** Baut den Baum rekursiv auf; nur {@code expandThisLevel} (der Oberbegriff) wird aufgeklappt. */
    private TreeItem<XmlNode> buildTreeItem(XmlNode node, boolean expandThisLevel) {
        TreeItem<XmlNode> item = new TreeItem<>(node);
        for (XmlNode child : node.getChildren()) {
            item.getChildren().add(buildTreeItem(child, false));
        }
        item.setExpanded(expandThisLevel);
        return item;
    }

    private void selectNode(XmlNode node) {
        selectedTreeNode = node;
        tagField.setText(node != null ? node.getTagName() : "");
        textContentField.setText(node != null && node.getTextContent() != null ? node.getTextContent() : "");
        boolean enabled = node != null;
        tagField.setDisable(!enabled);
        textContentField.setDisable(!enabled);

        // Lokomotiven bekommen für ihren "configuration"-Knoten eine
        // spezialisierte Tabelle (Aktiv/Nr/Wert/Typ/Beschreibung) statt der
        // generischen Tag-/Textinhalt-Felder - andere Kategorien folgen
        // später mit eigenen spezialisierten Ansichten.
        boolean showConfig = node != null && "locomotives".equals(categoryName)
                && "configuration".equals(node.getTagName());
        if (showConfig) {
            currentConfigNode = node;
            configTable.setItems(node.getChildren());
        }
        tagGrid.setVisible(!showConfig);
        tagGrid.setManaged(!showConfig);
        configPane.setVisible(showConfig);
        configPane.setManaged(showConfig);
    }

    private void commitTagName() {
        if (selectedTreeNode != null && !tagField.getText().isBlank()) {
            String newTag = tagField.getText().trim();
            // Auch hier nur bei tatsächlicher Änderung als "modified" zählen
            // (siehe textContentField-Listener oben) - sonst würde z.B. das
            // reine Verlassen des Feldes per Tab schon als Änderung gelten.
            if (!newTag.equals(selectedTreeNode.getTagName())) {
                beforeChange.run();
                selectedTreeNode.setTagName(newTag);
                onModified.run();
            }
            detailTree.refresh();
            entryTable.refresh();
        }
    }

    // ---------------------------------------------------------------
    // Spezialisierte Konfigurations-Tabelle für Lokomotiven
    // ---------------------------------------------------------------

    /**
     * Baut die Konfigurations-Tabelle für {@code <configuration>}-Knoten
     * einer Lokomotive: eine Zeile je {@code <parameter>}-Kind-Element mit
     * den Spalten Aktiv (nur informativ - eine Zeile existiert nur, wenn das
     * Element tatsächlich da ist), Nr (Attribut {@code nr}), Wert (Attribut
     * {@code value}), Typ (Attribut {@code type}, unübersetzt wie im
     * Original) und Beschreibung (Kind-Element {@code <description>}).
     * Hinzufügen/Löschen einzelner Parameter über die Buttons darunter -
     * bewusst NICHT über das Ab-/Anhaken von "Aktiv", um konsistent mit dem
     * übrigen Hinzufügen/Löschen-Muster der Anwendung zu bleiben.
     */
    private void buildConfigPane() {
        configTable.setEditable(true);
        configTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        TableColumn<XmlNode, Void> activeCol = new TableColumn<>(i18n.t("editor.paramActive"));
        activeCol.setSortable(false);
        activeCol.setPrefWidth(50);
        activeCol.setCellFactory(col -> new TableCell<XmlNode, Void>() {
            private final CheckBox box = new CheckBox();
            {
                box.setSelected(true);
                box.setDisable(true);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        TableColumn<XmlNode, String> nrCol = new TableColumn<>(i18n.t("editor.paramNr"));
        nrCol.setPrefWidth(60);
        nrCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(emptyIfNull(data.getValue().getAttribute("nr"))));
        nrCol.setCellFactory(TextFieldTableCell.forTableColumn());
        nrCol.setOnEditCommit(event -> commitParamAttribute(event.getRowValue(), "nr", event.getNewValue()));

        TableColumn<XmlNode, String> valueCol = new TableColumn<>(i18n.t("editor.paramValue"));
        valueCol.setPrefWidth(90);
        valueCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(emptyIfNull(data.getValue().getAttribute("value"))));
        valueCol.setCellFactory(TextFieldTableCell.forTableColumn());
        valueCol.setOnEditCommit(event -> commitParamAttribute(event.getRowValue(), "value", event.getNewValue()));

        TableColumn<XmlNode, String> typeCol = new TableColumn<>(i18n.t("editor.paramType"));
        typeCol.setPrefWidth(130);
        typeCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(emptyIfNull(data.getValue().getAttribute("type"))));
        typeCol.setCellFactory(TextFieldTableCell.forTableColumn());
        typeCol.setOnEditCommit(event -> commitParamAttribute(event.getRowValue(), "type", event.getNewValue()));

        TableColumn<XmlNode, String> descCol = new TableColumn<>(i18n.t("editor.paramDescription"));
        descCol.setPrefWidth(200);
        descCol.setCellValueFactory(data -> {
            XmlNode desc = data.getValue().findChild("description");
            String text = desc != null && desc.getTextContent() != null ? desc.getTextContent() : "";
            return new ReadOnlyStringWrapper(text);
        });
        descCol.setCellFactory(TextFieldTableCell.forTableColumn());
        descCol.setOnEditCommit(event -> commitParamDescription(event.getRowValue(), event.getNewValue()));

        configTable.getColumns().add(activeCol);
        configTable.getColumns().add(nrCol);
        configTable.getColumns().add(valueCol);
        configTable.getColumns().add(typeCol);
        configTable.getColumns().add(descCol);
        configTable.setPlaceholder(new Label(i18n.t("editor.noEntries")));

        Button addParamButton = new Button(i18n.t("editor.paramAdd"));
        addParamButton.setOnAction(e -> onAddParameter());

        Button deleteParamButton = new Button(i18n.t("editor.paramDelete"));
        deleteParamButton.setOnAction(e -> {
            XmlNode selected = configTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                deleteParameter(selected);
            }
        });

        HBox configToolbar = new HBox(10, addParamButton, deleteParamButton);
        configToolbar.setPadding(new Insets(8, 0, 8, 0));

        configPane.getChildren().addAll(configToolbar, configTable);
        VBox.setVgrow(configTable, javafx.scene.layout.Priority.ALWAYS);
        configPane.setVisible(false);
        configPane.setManaged(false);
    }

    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    /** Setzt/entfernt ein Attribut eines {@code <parameter>}-Elements, nur bei tatsächlicher Änderung. */
    private void commitParamAttribute(XmlNode param, String attributeName, String newValue) {
        String oldValue = emptyIfNull(param.getAttribute(attributeName));
        String trimmed = newValue == null ? "" : newValue.trim();
        if (oldValue.equals(trimmed)) {
            return;
        }
        beforeChange.run();
        if (trimmed.isEmpty()) {
            param.removeAttribute(attributeName);
        } else {
            param.setAttribute(attributeName, trimmed);
        }
        onModified.run();
        configTable.refresh();
    }

    /** Setzt/entfernt das {@code <description>}-Kind-Element eines Parameters, nur bei tatsächlicher Änderung. */
    private void commitParamDescription(XmlNode param, String newValue) {
        XmlNode desc = param.findChild("description");
        String oldValue = desc != null && desc.getTextContent() != null ? desc.getTextContent() : "";
        String trimmed = newValue == null ? "" : newValue;
        if (oldValue.equals(trimmed)) {
            return;
        }
        beforeChange.run();
        if (trimmed.isBlank()) {
            if (desc != null) {
                param.getChildren().remove(desc);
            }
        } else {
            if (desc == null) {
                desc = new XmlNode("description");
                param.getChildren().add(0, desc);
            }
            desc.setTextContent(trimmed);
        }
        onModified.run();
        configTable.refresh();
    }

    private void onAddParameter() {
        if (currentConfigNode == null) {
            return;
        }
        TextInputDialog nrDialog = new TextInputDialog();
        nrDialog.setTitle(i18n.t("editor.paramAdd"));
        nrDialog.setHeaderText(i18n.t("editor.paramNewNrHeader"));
        nrDialog.setContentText(i18n.t("editor.paramNr"));
        Optional<String> nr = nrDialog.showAndWait();
        if (nr.isEmpty() || nr.get().isBlank()) {
            return;
        }
        XmlNode newParam = new XmlNode("parameter");
        newParam.setAttribute("nr", nr.get().trim());
        beforeChange.run();
        currentConfigNode.getChildren().add(newParam);
        onModified.run();
        configTable.getSelectionModel().select(newParam);
        configTable.scrollTo(newParam);
    }

    private void deleteParameter(XmlNode param) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                i18n.t("editor.paramConfirmDelete", emptyIfNull(param.getAttribute("nr"))),
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle(i18n.t("editor.paramConfirmDeleteTitle"));
        confirm.setHeaderText(null);
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES && currentConfigNode != null) {
            beforeChange.run();
            currentConfigNode.getChildren().remove(param);
            onModified.run();
        }
    }

    // ---------------------------------------------------------------
    // Verbindungslinien im Daten-Explorer-Baum
    // ---------------------------------------------------------------

    /** Breite je Verschachtelungsebene für die Führungslinien (in Pixeln). */
    private static final double GUIDE_LINE_INDENT = 14.0;
    /**
     * Angenommene Zeilenhöhe für die Linienberechnung. TreeCells haben in der
     * Standard-Anzeige eine feste Höhe (ca. 24px); da sich in dieser Sandbox
     * kein echtes JavaFX-Fenster anzeigen lässt, wurde dieser Wert nicht
     * visuell nachgeprüft - bei Bedarf hier anpassen, falls es bei dir zu
     * hoch/niedrig wirkt.
     */
    private static final double GUIDE_LINE_ROW_HEIGHT = 24.0;

    /**
     * Zeigt vor jedem Baum-Element Führungslinien an, die Untergruppen mit
     * einer Linie zur jeweils übergeordneten Gruppe verbinden (klassische
     * Baumansicht mit Linien statt reiner Einrückung). Die Standard-Einrückung
     * und der Auf-/Zuklapp-Pfeil von {@link TreeView} bleiben unverändert -
     * die Linien werden zusätzlich vor dem Text eingefügt.
     */
    private static final class GuideLineTreeCell extends TreeCell<XmlNode> {
        @Override
        protected void updateItem(XmlNode item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            setText(item.toString());
            javafx.scene.Node lines = buildGuideLines(getTreeItem());
            setGraphic(lines);
        }

        /** Baut die Führungslinien-Grafik für die Ebene des übergebenen Baum-Elements. */
        private static javafx.scene.Node buildGuideLines(TreeItem<XmlNode> item) {
            int level = item != null && item.getParent() != null ? countLevel(item) : 0;
            if (level <= 0) {
                return null;
            }
            Pane pane = new Pane();
            double width = level * GUIDE_LINE_INDENT;
            pane.setPrefSize(width, GUIDE_LINE_ROW_HEIGHT);
            pane.setMinSize(width, GUIDE_LINE_ROW_HEIGHT);
            pane.setMaxSize(width, GUIDE_LINE_ROW_HEIGHT);

            // Durchgehende vertikale Linien für alle Vorfahren-Ebenen, die
            // selbst noch weitere Geschwister-Elemente weiter unten haben -
            // nur dann muss die Linie über diese Zeile hinaus fortgesetzt
            // werden, damit sie das nächste Geschwister-Element erreicht.
            TreeItem<XmlNode> ancestor = item.getParent();
            for (int lvl = level - 1; lvl >= 1 && ancestor != null; lvl--) {
                if (!isLastChild(ancestor)) {
                    double x = (lvl - 1) * GUIDE_LINE_INDENT + GUIDE_LINE_INDENT / 2.0;
                    pane.getChildren().add(styledLine(x, 0, x, GUIDE_LINE_ROW_HEIGHT));
                }
                ancestor = ancestor.getParent();
            }

            // Eigener Verbindungswinkel dieser Zeile: von oben zur Mitte,
            // dann waagerecht zum Element; führt die Linie weiter Richtung
            // Ebene an einem nächsten Geschwister-Element weiter nach unten.
            double ownX = (level - 1) * GUIDE_LINE_INDENT + GUIDE_LINE_INDENT / 2.0;
            double midY = GUIDE_LINE_ROW_HEIGHT / 2.0;
            double bottomY = isLastChild(item) ? midY : GUIDE_LINE_ROW_HEIGHT;
            pane.getChildren().add(styledLine(ownX, 0, ownX, bottomY));
            pane.getChildren().add(styledLine(ownX, midY, level * GUIDE_LINE_INDENT, midY));

            return pane;
        }

        private static Line styledLine(double startX, double startY, double endX, double endY) {
            Line line = new Line(startX, startY, endX, endY);
            line.getStyleClass().add("tree-guide-line");
            line.setStrokeWidth(1);
            line.setStyle("-fx-stroke: derive(-fx-text-base-color, 40%);");
            return line;
        }

        private static boolean isLastChild(TreeItem<XmlNode> item) {
            TreeItem<XmlNode> parent = item.getParent();
            if (parent == null) {
                return true;
            }
            ObservableList<TreeItem<XmlNode>> siblings = parent.getChildren();
            return siblings.indexOf(item) == siblings.size() - 1;
        }

        /** Tiefe des Elements relativ zur Wurzel (Wurzel selbst = 0). */
        private static int countLevel(TreeItem<XmlNode> item) {
            int level = 0;
            TreeItem<XmlNode> parent = item.getParent();
            while (parent != null) {
                level++;
                parent = parent.getParent();
            }
            return level;
        }
    }
}
