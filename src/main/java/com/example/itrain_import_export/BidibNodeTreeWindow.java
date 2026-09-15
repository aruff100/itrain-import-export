package com.example.itrain_import_export;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import org.bidib.jbidibc.messages.utils.ByteUtils;
import org.bidib.jbidibc.messages.utils.NodeUtils;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Baumfenster der BiDiB-Knoten eines Interface. Grundlage ist immer eine
 * {@link BidibSystemFile} - frisch ausgelesen ({@link #show(Stage,
 * BidibConnection)} laesst erst {@link BidibNodeReader} mit Fortschrittsdialog
 * lesen) oder aus einer gespeicherten System-Datei
 * ({@link #showFromFile(Stage, BidibSystemFile)}).
 *
 * <h2>Aufbau des Baums</h2>
 *
 * Je Knoten ein Eintrag mit sprechendem Namen, darunter die Ordner
 * "Merkmale" und - wo vorhanden - "Anschlüsse" mit einem Eintrag je
 * Ein-/Ausgang.
 *
 * <h2>Ankreuzen und Übernehmen</h2>
 *
 * Vor dem Aufklappdreieck steht eine Spalte mit Ankreuzkästen - bei Knoten,
 * Anschluss-Ordnern und einzelnen Anschlüssen, nie bei Merkmalen. Ein Haken
 * am Knoten oder Ordner wählt alle seine Anschlüsse; der Kasten "Alle
 * auswählen" über dem Baum nimmt alles bzw. hebt alles auf. "Auswahl
 * übernehmen" macht daraus die iTrain-Objekte und trägt sie in die Tabellen
 * des Systeme-Fensters ein ({@link SystemsWindow#acceptObjects}): die
 * Schnittstelle immer, je Rückmelder-Anschluss einen Rückmelder, je
 * Zubehör-Anschluss ein Zubehör (Bauform wird einmal gefragt), je
 * angekreuztem Booster-Knoten einen Booster. Die Statuszeile zählt mit.
 *
 * <h2>Speichern</h2>
 *
 * "Speichern" legt die System-Datei (CSV in ZIP, siehe
 * {@link BidibSystemFile}) im Ordner "Pfad für System-Dateien" ab.
 */
public final class BidibNodeTreeWindow {

    private BidibNodeTreeWindow() {
    }

    // ------------------------------------------------------------------
    // Hüllen für die Baumeinträge
    // ------------------------------------------------------------------

    private record FeatureFolder(String label) {
    }

    private record PortFolder(String label) {
    }

    private enum PortKind {
        FEEDBACK, ACCESSORY, LOCOMOTIVE;

        static PortKind of(String portKind) {
            if ("ACCESSORY".equals(portKind)) {
                return ACCESSORY;
            } else if ("LOCOMOTIVE".equals(portKind)) {
                return LOCOMOTIVE;
            }
            return FEEDBACK;
        }
    }

    /** Ein einzelner Anschluss eines Knotens (Nummer zero-basiert, wie iTrain sie führt). */
    private record PortEntry(PortKind kind, NodeHolder holder, int port) {
    }

    /** Ein Knoten des Baums - die Hülle um den gespeicherten Datensatz. */
    private static final class NodeHolder {
        private final BidibSystemFile.NodeRecord record;
        private final boolean isInterface;
        private final String fallbackLabel;
        private PortKind portKind;
        private Integer portCount;

        NodeHolder(BidibSystemFile.NodeRecord record, boolean isInterface, String fallbackLabel) {
            this.record = record;
            this.isInterface = isInterface;
            this.fallbackLabel = fallbackLabel;
        }

        Long uniqueId() {
            return record.uniqueId() == 0L ? null : record.uniqueId();
        }

        String displayName() {
            String uidText = uniqueId() != null ? ByteUtils.formatHexUniqueId(record.uniqueId()) : null;
            String fallback = isInterface && uidText != null ? fallbackLabel + " - " + uidText
                    : uidText != null ? uidText : fallbackLabel;
            return formatFriendlyLabel(record.userName(), record.productName(), fallback);
        }
    }

    // ------------------------------------------------------------------
    // Fenster
    // ------------------------------------------------------------------

    /** Frisch auslesen (mit Fortschrittsdialog) und dann anzeigen. */
    public static void show(Stage owner, BidibConnection connection) {
        BidibNodeReader.readWithProgress(owner, connection, snapshot -> open(owner, snapshot));
    }

    /** Aus einer gespeicherten System-Datei anzeigen. */
    public static void showFromFile(Stage owner, BidibSystemFile file) {
        open(owner, file);
    }

    private static void open(Stage owner, BidibSystemFile source) {
        I18n i18n = I18n.getInstance();
        AppSettings settings = AppSettings.getInstance();

        Label interfaceNameLabel = new Label(i18n.t("bidib.itrainInterfaceLabel"));
        TextField interfaceNameField = new TextField(source.getInterfaceName());
        HBox.setHgrow(interfaceNameField, Priority.ALWAYS);
        Button saveButton = new Button(i18n.t("bidib.generateSaveButton"));
        HBox topRow = new HBox(8, interfaceNameLabel, interfaceNameField, saveButton);
        topRow.setAlignment(Pos.CENTER_LEFT);

        TreeItem<Object> rootItem = new TreeItem<>("");
        rootItem.setExpanded(true);
        List<NodeHolder> nodeHolders = new ArrayList<>();
        Set<TreeItem<Object>> checked = Collections.newSetFromMap(new IdentityHashMap<>());

        TreeTableView<Object> tree = new TreeTableView<>(rootItem);
        tree.setShowRoot(false);
        tree.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        Label selectionLabel = new Label();
        Runnable updateSelectionLabel = () -> selectionLabel.setText(selectionSummary(checked, i18n));

        // Spalte 1: Ankreuzkasten - VOR dem Aufklappdreieck, das in der
        // zweiten (Baum-)Spalte sitzt. Nur fuer Knoten, Anschluss-Ordner und
        // Anschluesse; Merkmale bekommen keinen.
        TreeTableColumn<Object, TreeItem<Object>> checkColumn = new TreeTableColumn<>("");
        checkColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        checkColumn.setCellFactory(col -> new TreeTableCell<>() {
            private final CheckBox box = new CheckBox();
            {
                box.setOnAction(e -> {
                    TreeItem<Object> item = getItem();
                    if (item != null) {
                        setChecked(item, box.isSelected(), checked);
                        updateSelectionLabel.run();
                        tree.refresh();
                    }
                });
            }

            @Override
            protected void updateItem(TreeItem<Object> item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || !isCheckable(item.getValue())) {
                    setGraphic(null);
                    return;
                }
                box.setSelected(checked.contains(item));
                setGraphic(box);
            }
        });
        checkColumn.setPrefWidth(40);
        checkColumn.setMaxWidth(40);
        checkColumn.setResizable(false);
        checkColumn.setSortable(false);
        checkColumn.setReorderable(false);

        TreeTableColumn<Object, Object> nameColumn = new TreeTableColumn<>(i18n.t("bidib.nodeTreeColumn"));
        nameColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().getValue()));
        nameColumn.setCellFactory(col -> new TreeTableCell<>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : labelFor(item, i18n));
            }
        });
        nameColumn.setSortable(false);
        nameColumn.setReorderable(false);
        tree.getColumns().setAll(List.of(checkColumn, nameColumn));
        tree.setTreeColumn(nameColumn);

        // Knoten hinter einem Verteiler (Adresse "1.2") unter dem Verteiler
        // ("1") einhaengen - so wie der Wizard es zeigt.
        java.util.Map<String, TreeItem<Object>> itemsByAddress = new java.util.HashMap<>();
        for (BidibSystemFile.NodeRecord record : source.getNodes()) {
            TreeItem<Object> item = createNodeItem(record, source.isEcos(), i18n, nodeHolders);
            String address = record.address() == null ? "" : record.address();
            int dot = address.lastIndexOf('.');
            TreeItem<Object> parent = dot > 0 ? itemsByAddress.get(address.substring(0, dot)) : null;
            if (parent != null) {
                parent.getChildren().add(item);
                parent.setExpanded(true);
            } else {
                rootItem.getChildren().add(item);
            }
            itemsByAddress.put(address, item);
        }

        // "Alle auswaehlen": ein Haken nimmt alles dieser Verbindung, das
        // Entfernen setzt alle Auswahlen zurueck.
        CheckBox selectAllBox = new CheckBox(i18n.t("bidib.selectAll"));
        selectAllBox.setOnAction(e -> {
            for (TreeItem<Object> item : rootItem.getChildren()) {
                setChecked(item, selectAllBox.isSelected(), checked);
            }
            updateSelectionLabel.run();
            tree.refresh();
        });

        // Einzeilige Statuszeile: woher der Baum stammt und wie viele Knoten.
        Label statusLine = new Label(source.getFile() != null
                ? i18n.t("bidib.nodeTreeFromFile", source.getFile().getName())
                : i18n.t("bidib.nodeTreeInitialReadCount", Math.max(0, source.getNodes().size() - 1)));

        Stage stage = new Stage();

        // Unten: links der Zaehler, rechts "Schliessen" und "Auswahl uebernehmen".
        Button acceptButton = new Button(i18n.t("bidib.acceptSelectionButton"));
        Button closeButton = new Button(i18n.t("bidib.pairingStoreClose"));
        closeButton.setCancelButton(true);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bottomRow = new HBox(8, selectionLabel, spacer, closeButton, acceptButton);
        bottomRow.setAlignment(Pos.CENTER_LEFT);
        updateSelectionLabel.run();

        saveButton.setOnAction(e -> saveSystemFile(stage, source, interfaceNameField, i18n, settings));
        acceptButton.setOnAction(e -> {
            List<SystemsObject> objects = buildObjects(checked, nodeHolders, source, interfaceNameField, i18n);
            if (objects == null) {
                return;
            }
            source.setInterfaceName(interfaceName(interfaceNameField));
            SystemsWindow.acceptObjects(source, objects);
            stage.close();
        });
        closeButton.setOnAction(e -> stage.close());

        VBox content = new VBox(8, topRow, statusLine, selectAllBox, tree, bottomRow);
        content.setPadding(new Insets(12));
        VBox.setVgrow(tree, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setCenter(content);

        stage.initOwner(owner);
        stage.initModality(Modality.NONE);
        String title = source.getFile() != null ? source.getFile().getName() : source.getInterfaceName();
        stage.setTitle(i18n.t("bidib.nodeTreeWindowTitle") + " - " + title);
        stage.getIcons().addAll(loadAppIcons());

        Scene scene = new Scene(root);
        ThemeManager.apply(scene, settings.getTheme());
        stage.setScene(scene);
        stage.setMinWidth(560);
        stage.setMinHeight(420);
        WindowState.apply(stage, "bidibNodeTree", 720, 560);
        stage.show();
    }

    // ------------------------------------------------------------------
    // Ankreuzen
    // ------------------------------------------------------------------

    private static boolean isCheckable(Object value) {
        return value instanceof NodeHolder || value instanceof PortFolder || value instanceof PortEntry;
    }

    /** Haken setzen/entfernen - bei Knoten und Ordnern fuer alle Anschluesse darunter mit. */
    private static void setChecked(TreeItem<Object> item, boolean value, Set<TreeItem<Object>> checked) {
        if (!isCheckable(item.getValue())) {
            return;
        }
        if (value) {
            checked.add(item);
        } else {
            checked.remove(item);
        }
        for (TreeItem<Object> child : item.getChildren()) {
            setChecked(child, value, checked);
        }
    }

    private static String selectionSummary(Set<TreeItem<Object>> checked, I18n i18n) {
        int ports = 0;
        int nodes = 0;
        for (TreeItem<Object> item : checked) {
            if (item.getValue() instanceof PortEntry) {
                ports++;
            } else if (item.getValue() instanceof NodeHolder) {
                nodes++;
            }
        }
        return i18n.t("bidib.selectionSummary", ports, nodes);
    }

    // ------------------------------------------------------------------
    // Beschriftungen
    // ------------------------------------------------------------------

    private static String labelFor(Object item, I18n i18n) {
        if (item instanceof NodeHolder holder) {
            return holder.displayName();
        } else if (item instanceof BidibSystemFile.FeatureRecord feature) {
            return feature.name() + " = " + feature.value();
        } else if (item instanceof FeatureFolder folder) {
            return folder.label();
        } else if (item instanceof PortFolder folder) {
            return folder.label();
        } else if (item instanceof PortEntry entry) {
            // ECoS: Anschluesse haben eigene Namen und Adressen.
            String portName = entry.holder().record.portName(entry.port());
            if (portName != null) {
                String address = entry.holder().record.portAddress(entry.port());
                String type = entry.holder().record.portType(entry.port());
                // Lokomotiven: Name (Adresse, Protokoll Fx) - Zubehoer/Rueckmelder: Name (Adresse)
                String detail = address != null ? address : "";
                if (entry.kind() == PortKind.LOCOMOTIVE && type != null && !type.isBlank()) {
                    detail += (detail.isEmpty() ? "" : ", ") + type;
                }
                return portName + (detail.isEmpty() ? "" : " (" + detail + ")");
            }
            String key = entry.kind() == PortKind.FEEDBACK ? "bidib.portFeedbackLabel" : "bidib.portAccessoryLabel";
            return i18n.t(key, entry.port() + 1);
        }
        return String.valueOf(item);
    }

    private static String formatFriendlyLabel(String userName, String productName, String fallback) {
        boolean hasUser = userName != null && !userName.isBlank();
        boolean hasProduct = productName != null && !productName.isBlank();
        if (hasUser && hasProduct) {
            return userName + " (" + productName + ")";
        } else if (hasUser) {
            return userName;
        } else if (hasProduct) {
            return productName;
        }
        return fallback;
    }

    // ------------------------------------------------------------------
    // Baumaufbau
    // ------------------------------------------------------------------

    private static TreeItem<Object> createNodeItem(BidibSystemFile.NodeRecord record, boolean ecos, I18n i18n,
            List<NodeHolder> nodeHolders) {
        boolean isInterface = "0".equals(record.address());
        NodeHolder holder = new NodeHolder(record, isInterface,
                isInterface ? i18n.t("bidib.nodeTreeInterfaceLabel") : ByteUtils.formatHexUniqueId(record.uniqueId()));
        nodeHolders.add(holder);

        TreeItem<Object> item = new TreeItem<>(holder);
        // Merkmale (Features) gibt es nur bei BiDiB-Knoten - eine ECoS
        // kennt keine, der Ordner entfaellt dort (Andre, 15.09.).
        if (!ecos) {
            TreeItem<Object> featureFolder = new TreeItem<>(new FeatureFolder(
                    record.features().isEmpty() ? i18n.t("bidib.featureFolderEmpty")
                            : i18n.t("bidib.featureFolder", record.features().size())));
            for (BidibSystemFile.FeatureRecord feature : record.features()) {
                featureFolder.getChildren().add(new TreeItem<>(feature));
            }
            item.getChildren().add(featureFolder);
        }

        if (record.portKind() != null && record.portCount() != null && record.portCount() > 0) {
            PortKind kind = PortKind.of(record.portKind());
            holder.portKind = kind;
            holder.portCount = record.portCount();
            String folderLabel = kind == PortKind.FEEDBACK
                    ? i18n.t("bidib.portFolderFeedback", record.portCount())
                    : kind == PortKind.LOCOMOTIVE ? i18n.t("bidib.portFolderLocomotive", record.portCount())
                    : i18n.t("bidib.portFolderAccessory", record.portCount());
            TreeItem<Object> portFolder = new TreeItem<>(new PortFolder(folderLabel));
            for (int port = 0; port < record.portCount(); port++) {
                portFolder.getChildren().add(new TreeItem<>(new PortEntry(kind, holder, port)));
            }
            item.getChildren().add(portFolder);
        }
        if (isInterface) {
            item.setExpanded(true);
        }
        return item;
    }

    // ------------------------------------------------------------------
    // Speichern (System-Datei) und Uebernehmen
    // ------------------------------------------------------------------

    private static void saveSystemFile(Stage owner, BidibSystemFile source, TextField interfaceNameField,
            I18n i18n, AppSettings settings) {
        String dir = settings.getSystemFilesDirectory();
        if (dir == null || dir.isBlank()) {
            Alert alert = new Alert(Alert.AlertType.WARNING, i18n.t("bidib.systemFilesDirMissing"));
            alert.initOwner(owner);
            alert.setHeaderText(null);
            alert.showAndWait();
            return;
        }
        source.setInterfaceName(interfaceName(interfaceNameField));
        File target = source.getFile() != null ? source.getFile() : new File(dir, source.suggestedFileName());
        try {
            source.save(target);
        } catch (Exception ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR, String.valueOf(ex.getMessage()));
            alert.initOwner(owner);
            alert.setHeaderText(i18n.t("bidib.generateSaveError"));
            alert.showAndWait();
            return;
        }
        SystemsWindow.systemFilesChanged();
        Alert alert = new Alert(Alert.AlertType.INFORMATION, target.getAbsolutePath());
        alert.initOwner(owner);
        alert.setHeaderText(i18n.t("bidib.systemFileSaved"));
        alert.showAndWait();
    }

    /**
     * Baut aus der Auswahl die iTrain-Objekte. Die Schnittstelle immer (mit
     * ALLEN Knoten, so wie iTrain sie selbst schreibt), dazu je angekreuztem
     * Anschluss ein Rueckmelder bzw. Zubehoer und je angekreuztem
     * Booster-Knoten ein Booster. Liefert null, wenn der Nutzer die
     * Bauform-Abfrage abgebrochen hat.
     */
    private static List<SystemsObject> buildObjects(Set<TreeItem<Object>> checked, List<NodeHolder> nodeHolders,
            BidibSystemFile source, TextField interfaceNameField, I18n i18n) {

        String interfaceName = interfaceName(interfaceNameField);
        List<SystemsObject> result = new ArrayList<>();

        if (source.isEcos()) {
            return buildEcosObjects(checked, nodeHolders, source, interfaceName, i18n);
        }

        List<BidibItemFactory.NodeInfo> infos = new ArrayList<>();
        for (NodeHolder holder : nodeHolders) {
            if (holder.uniqueId() == null) {
                continue;
            }
            infos.add(new BidibItemFactory.NodeInfo(holder.record.uniqueId(), holder.record.productName(),
                    holder.record.userName(), holder.portCount));
        }
        XmlNode iface;
        String ifaceType;
        if (source.isSerial()) {
            iface = BidibItemFactory.createSerialInterface(interfaceName, source.getHostPort(), infos);
            ifaceType = "bidib";
        } else {
            String hostPort = source.getHostPort();
            int colon = hostPort.lastIndexOf(':');
            String host = colon > 0 ? hostPort.substring(0, colon) : hostPort;
            String port = colon > 0 ? hostPort.substring(colon + 1) : "62875";
            iface = BidibItemFactory.createInterface(interfaceName, host, port, infos);
            ifaceType = "netbidib";
        }
        String bidibInterfaceName = source.getInterfaceName();
        SystemsObject ifaceObject = new SystemsObject(SystemsObject.CATEGORY_INTERFACES, ifaceType,
                source.getHostPort(), bidibInterfaceName, interfaceName, false, "", String.valueOf(infos.size()),
                interfaceName, iface);
        ifaceObject.setNodeUniqueId(source.getInterfaceUniqueId());
        result.add(ifaceObject);

        List<TreeItem<Object>> ordered = new ArrayList<>(checked);
        ordered.sort(Comparator.comparingInt(item -> orderIndex(item, nodeHolders)));

        for (TreeItem<Object> item : ordered) {
            Object value = item.getValue();
            if (value instanceof PortEntry entry) {
                NodeHolder holder = entry.holder();
                if (holder.uniqueId() == null) {
                    continue;
                }
                long uid = holder.record.uniqueId();
                String nodeName = holder.displayName();
                String bidibName = nodeName + " / " + (entry.port() + 1);
                if (entry.kind() == PortKind.FEEDBACK) {
                    String name = nodeName + "_" + i18n.t("bidib.namePartFeedback") + " " + (entry.port() + 1);
                    XmlNode node = BidibItemFactory.createFeedback(name, uid, entry.port(), interfaceName);
                    SystemsObject object = new SystemsObject(SystemsObject.CATEGORY_FEEDBACKS, "occupancy",
                            holder.record.address() + " / " + (entry.port() + 1), bidibName, name, false, "", "",
                            interfaceName, node);
                    object.setNodeUniqueId(uid);
                    result.add(object);
                } else {
                    // Bauform wird erst im Systeme-Fenster festgelegt
                    // (Bearbeitungsfenster) - hier immer "Weiche links".
                    String name = nodeName + "_" + i18n.t("bidib.namePartAccessory") + " " + (entry.port() + 1);
                    XmlNode node = BidibItemFactory.createAccessory("turnout", "left", name, uid,
                            entry.port(), interfaceName);
                    SystemsObject object = new SystemsObject(SystemsObject.CATEGORY_ACCESSORIES, "left",
                            holder.record.address() + " / " + (entry.port() + 1), bidibName, name, false, "", "",
                            interfaceName, node);
                    object.setNodeUniqueId(uid);
                    object.setTypeDefault(true);
                    result.add(object);
                }
            } else if (value instanceof NodeHolder holder) {
                if (holder.uniqueId() != null && NodeUtils.hasBoosterFunctions(holder.record.uniqueId())) {
                    String name = holder.displayName() + "_" + i18n.t("bidib.namePartBooster") + " 1";
                    XmlNode node = BidibItemFactory.createBooster(name, holder.record.uniqueId(), interfaceName);
                    SystemsObject object = new SystemsObject(SystemsObject.CATEGORY_BOOSTERS, "bidib",
                            holder.record.address(), holder.displayName(), name, false, name, "", interfaceName, node);
                    object.setNodeUniqueId(holder.record.uniqueId());
                    result.add(object);
                }
            }
        }
        return result;
    }

    /**
     * ECoS: die Schnittstelle immer, je angekreuztem Rueckmelde-Anschluss ein
     * Rueckmelder (Adresse aus portAddresses), je angekreuztem Zubehoer-
     * Anschluss eine Weiche (Name, DCC-Adresse und Bauform aus dem Symbol,
     * mit "*"-Markierung). Booster gibt es bei der ECoS nicht als Knoten.
     */
    private static List<SystemsObject> buildEcosObjects(Set<TreeItem<Object>> checked, List<NodeHolder> nodeHolders,
            BidibSystemFile source, String interfaceName, I18n i18n) {
        List<SystemsObject> result = new ArrayList<>();
        String hostPort = source.getHostPort();
        int colon = hostPort.lastIndexOf(':');
        String host = colon > 0 ? hostPort.substring(0, colon) : hostPort;
        String bidibInterfaceName = source.getNodes().isEmpty() || source.getNodes().get(0).userName() == null
                ? source.getInterfaceName() : source.getNodes().get(0).userName();
        SystemsObject iface = new SystemsObject(SystemsObject.CATEGORY_INTERFACES, "ecos", hostPort,
                bidibInterfaceName, interfaceName, true, "", "", interfaceName,
                EcosItemFactory.createInterface(interfaceName, host));
        iface.setNodeUniqueId(source.getInterfaceUniqueId());
        result.add(iface);

        List<TreeItem<Object>> ordered = new ArrayList<>(checked);
        ordered.sort(Comparator.comparingInt(item -> orderIndex(item, nodeHolders)));
        for (TreeItem<Object> item : ordered) {
            if (!(item.getValue() instanceof PortEntry entry)) {
                continue;
            }
            BidibSystemFile.NodeRecord record = entry.holder().record;
            String portName = record.portName(entry.port());
            String addressText = record.portAddress(entry.port());
            int address = 0;
            try {
                address = addressText == null ? 0 : Integer.parseInt(addressText.trim());
            } catch (NumberFormatException ex) {
                address = 0;
            }
            String nodeName = entry.holder().displayName();
            if (entry.kind() == PortKind.FEEDBACK) {
                String name = nodeName + "_" + (portName != null ? portName
                        : i18n.t("bidib.namePartFeedback") + " " + (entry.port() + 1));
                XmlNode node = EcosItemFactory.createFeedback(name, address, interfaceName);
                SystemsObject object = new SystemsObject(SystemsObject.CATEGORY_FEEDBACKS, "occupancy",
                        record.address() + " / " + (entry.port() + 1), nodeName + " / " + (entry.port() + 1),
                        name, false, "", "", interfaceName, node);
                object.setNodeUniqueId(record.uniqueId());
                result.add(object);
            } else if (entry.kind() == PortKind.LOCOMOTIVE) {
                // Lokomotive: Name, Adresse und "Protokoll Fn" (Protokoll der
                // ECoS plus Anzahl der Funktionen) stehen im Anschluss.
                String name = portName != null ? portName : "Lok " + (entry.port() + 1);
                LocomotiveXmlFactory.LocoInfo info = LocomotiveXmlFactory.parseLocoType(record.portType(entry.port()));
                XmlNode node = LocomotiveXmlFactory.createLocomotive(name, address, info, interfaceName);
                SystemsObject object = new SystemsObject(SystemsObject.CATEGORY_LOCOMOTIVES, info.protocolCode(),
                        String.valueOf(address), name + " (" + address + ")", name, false, "", "",
                        interfaceName, node);
                object.setNodeUniqueId(record.uniqueId());
                result.add(object);
            } else {
                String name = portName != null ? portName
                        : i18n.t("bidib.namePartAccessory") + " " + (entry.port() + 1);
                String type = record.portType(entry.port());
                if (type == null || type.isBlank()) {
                    type = "left";
                }
                XmlNode node = EcosItemFactory.createTurnout(type, name, address, interfaceName);
                SystemsObject object = new SystemsObject(SystemsObject.CATEGORY_ACCESSORIES, type,
                        record.address() + " / " + (entry.port() + 1), name + " (" + address + ")",
                        name, false, "", "", interfaceName, node);
                object.setNodeUniqueId(record.uniqueId());
                object.setTypeDefault(true);
                result.add(object);
            }
        }
        return result;
    }

    private static int orderIndex(TreeItem<Object> item, List<NodeHolder> nodeHolders) {
        Object value = item.getValue();
        if (value instanceof PortEntry entry) {
            return nodeHolders.indexOf(entry.holder()) * 10000 + 1 + entry.port();
        } else if (value instanceof NodeHolder holder) {
            return nodeHolders.indexOf(holder) * 10000;
        }
        return Integer.MAX_VALUE;
    }

    private static String interfaceName(TextField field) {
        String value = field.getText();
        return value == null || value.isBlank() ? "BiDiB" : value.trim();
    }

    private static Image[] loadAppIcons() {
        try (InputStream in = BidibNodeTreeWindow.class.getResourceAsStream("app-icon.png")) {
            return in == null ? new Image[0] : new Image[]{new Image(in)};
        } catch (Exception ex) {
            return new Image[0];
        }
    }
}
