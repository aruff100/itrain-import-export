package com.example.itrain_import_export;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.converter.DefaultStringConverter;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Eigenes Fenster mit einer großen CV-Tabelle im iTrain-Format (Aktiv, Nr.,
 * Wert, Typ, Bezeichnung). Es dient drei Zwecken:
 * <ul>
 * <li><b>Decoder erfassen</b> ({@link #show}) - eine neue Decoder-Vorlage
 *     anlegen, ohne dass eine iTrain-Datei geöffnet sein muss. Gespeichert
 *     wird als Vorlagen-Datei im Vorlagen-Ordner ({@link DecoderTemplate}).</li>
 * <li><b>Vorlage bearbeiten</b> ({@link #showForTemplate}) - eine vorhandene
 *     Vorlage aus dem Ordner ansehen und ändern; bei unverändertem Namen
 *     wird in dieselbe Datei zurückgeschrieben.</li>
 * <li><b>Konfiguration bearbeiten</b> ({@link #showForConfiguration}) - den
 *     {@code <configuration>}-Knoten einer Lokomotive oder eines Wagens im
 *     geöffneten Dokument ändern. Übernommen wird direkt in den Knoten, mit
 *     Rückgängig-Unterstützung.</li>
 * </ul>
 * Lage und Größe merkt sich das Fenster je Betriebsart eigens (siehe
 * {@link WindowState}).
 * Der Grund für ein eigenes Fenster: Im Hauptfenster teilt sich die
 * Konfigurations-Tabelle den Platz mit Einträgeliste und Daten-Explorer. Für
 * eine Decoder-Liste mit oft dutzenden CVs ist das zu eng - hier steht die
 * ganze Fensterfläche zur Verfügung.
 * <p>
 * Die Tabelle arbeitet auf denselben {@link XmlNode}-Objekten wie das übrige
 * Programm, deshalb passt das Ergebnis unverändert sowohl in eine
 * Vorlagen-Datei als auch zurück ins Dokument.
 * <p>
 * <b>Vorrat an Leerzeilen</b>: Das Fenster startet mit {@link #INITIAL_ROWS}
 * leeren Zeilen und hängt selbsttätig weitere an, sobald man sich dem Ende
 * nähert - die Zahl ist eine Vorgabe, keine Obergrenze. Welche Zeilen
 * übernommen werden, entscheidet allein der Haken in der Spalte "Aktiv": Nur
 * angehakte, nicht leere Zeilen zählen, der ungenutzte Rest wird verworfen.
 * Der Haken setzt sich beim Ausfüllen einer Zeile von selbst und verschwindet
 * wieder, sobald die Zeile leer ist - abwählen lässt er sich jederzeit von
 * Hand.
 * <p>
 * <b>Bedienung</b>: Die Auswahl arbeitet zellweise. Tippen beginnt sofort die
 * Eingabe, Tabulator und Pfeiltasten übernehmen den Wert und springen zur
 * Nachbarzelle. Werte aus einer Hersteller-Anleitung kommen über die
 * Zwischenablage herein: im PDF-Programm markieren und kopieren, hier mit
 * Strg+V in eine Zelle oder über "Ab hier einfügen" auf mehrere Zeilen
 * verteilt einsetzen.
 */
public final class DecoderCaptureWindow {

    /** Zeilenvorrat beim Öffnen des Fensters - Vorgabe, keine Obergrenze. */
    private static final int INITIAL_ROWS = 1000;

    /** So viele Zeilen kommen jeweils nach. */
    private static final int ROW_BLOCK = 100;

    /** Ab diesem Abstand zum Tabellenende werden neue Zeilen angehängt. */
    private static final int SPARE_ROWS = 50;

    /** Erste Spalte mit Text - Spalte 0 ist die Aktiv-Auswahl. */
    /**
     * Erste Spalte, in die getippt werden kann. Spalte 0 ist "Aktiv" (ein
     * Kästchen), Spalte 1 die CV-Nummer - und die ergibt sich seit der
     * festen Nummerierung aus der Zeile selbst, ist also nicht mehr
     * beschreibbar. Tabulator und Pfeiltasten überspringen beide.
     */
    private static final int FIRST_EDITABLE_COLUMN = 2;

    /** Spaltenindizes der Tabelle - an mehreren Stellen gebraucht. */
    private static final int COLUMN_VALUE = 2;
    private static final int COLUMN_TYPE = 3;
    private static final int COLUMN_DESCRIPTION = 4;

    /** Breite für "Nr." und "Wert" - beides höchstens vierstellige Zahlen. */
    private static final double NUMBER_COLUMN_WIDTH = 64;

    /**
     * Zugabe auf die reine Textbreite einer Spaltenüberschrift: Innenabstand
     * der Kopfzelle links und rechts plus Rahmen. Siehe {@link #headerWidth}.
     */
    private static final double HEADER_PADDING = 22;

    /** Eine CV-Nummer am Zeilenanfang, mit oder ohne führendes "#". */
    private static final Pattern NUMBER_TOKEN = Pattern.compile("^#?(\\d{1,4})$");

    /**
     * Größte CV-Nummer, für die eine Zeile angelegt wird. Der NMRA-Bereich
     * endet bei 1024; die Reserve darüber deckt Hersteller ab, die eigene
     * Nummern darüber vergeben (ZIMO geht bis 1019, Uhlenbrock-Bänke höher).
     * Die Grenze verhindert vor allem, dass eine verrutschte Zahl aus einer
     * Anleitung Millionen Zeilen anlegt.
     */
    private static final int MAX_CV = 2048;

    /** Ein Wert: höchstens dreistellig (ein CV-Wert ist ein Byte). */
    private static final Pattern VALUE_TOKEN = Pattern.compile("^\\d{1,3}$");

    private final I18n i18n = I18n.getInstance();
    private final ObservableList<CvRow> rows = FXCollections.observableArrayList();

    /**
     * Meldungen, die beim Laden anfielen, als es noch kein anzeigbares
     * Fenster gab - siehe {@link #info} und {@link #flushPendingMessages}.
     */
    private final List<String> pendingMessages = new ArrayList<>();
    private final TableView<CvRow> cvTable = new TableView<>(rows);
    private final TextField nameField = new TextField();
    private final TextField descriptionField = new TextField();
    private Stage stage;

    /**
     * Der zu bearbeitende {@code <configuration>}-Knoten im Dokument, oder
     * {@code null}, wenn eine neue Vorlage erfasst wird. Unterscheidet die
     * beiden Betriebsarten des Fensters.
     */
    private XmlNode targetConfiguration;

    /**
     * Die bearbeitete Vorlage aus dem Vorlagen-Ordner, oder {@code null}.
     * Solange die Bezeichnung unverändert bleibt, schreibt "Speichern"
     * wieder in genau diese Datei zurück - sonst entstünde beim Bearbeiten
     * still eine zweite Datei daneben.
     */
    private DecoderTemplate sourceTemplate;

    /** Name des Fahrzeugs - nur zur Anzeige im Bearbeiten-Modus. */
    private String vehicleName;

    /**
     * Digital-Protokoll des bearbeiteten Fahrzeugs ({@code dcc}, {@code sx1},
     * ...), oder {@code null} außerhalb des Bearbeiten-Modus. Begrenzt, was
     * "Vorlage einlesen" übernehmen darf - siehe {@link DecoderProtocol}.
     * Das <em>manuelle</em> Ausfüllen der Tabelle bleibt davon unberührt: Die
     * Regel schützt vor dem versehentlichen Hineinkippen einer fremden
     * Vorlage, nicht vor bewussten Einzeleingaben.
     */
    private String vehicleProtocol;

    /** Schnappschuss für "Rückgängig", vor der Änderung am Dokument aufzurufen. */
    private Runnable beforeChange;

    /** Nach dem Übernehmen aufzurufen: Dokument als geändert markieren, Ansicht auffrischen. */
    private Runnable onApplied;

    /**
     * Text, mit dem eine gerade beginnende Zellbearbeitung vorbelegt wird -
     * so führt einfaches Tippen direkt zur Eingabe, statt erst einen
     * Doppelklick zu verlangen.
     */
    private String pendingInitialText;

    private DecoderCaptureWindow() {
    }

    /** Öffnet das Fenster zum Erfassen einer neuen Decoder-Vorlage. */
    public static void show(Stage owner) {
        new DecoderCaptureWindow().open(owner);
    }

    /**
     * Öffnet eine vorhandene Vorlage aus dem Vorlagen-Ordner zum Ansehen und
     * Ändern. Gespeichert wird wieder als Vorlagen-Datei; bei unverändertem
     * Namen in dieselbe Datei, über "Speichern als..." als zusätzliche.
     *
     * @param onSaved wird nach dem Speichern aufgerufen, damit die
     *                Vorlagen-Übersicht sich auffrischen kann
     */
    public static void showForTemplate(Stage owner, DecoderTemplate template, Runnable onSaved) {
        DecoderCaptureWindow window = new DecoderCaptureWindow();
        window.sourceTemplate = template;
        window.onApplied = onSaved;
        window.open(owner);
    }

    /**
     * Öffnet dasselbe Fenster, um den {@code <configuration>}-Knoten eines
     * Fahrzeugs im geöffneten Dokument zu bearbeiten.
     *
     * @param vehicleName  Name des Fahrzeugs, nur zur Anzeige
     * @param protocol     Digital-Protokoll des Fahrzeugs aus
     *                     {@code <decoder protocol="..."/>}; begrenzt, was
     *                     "Vorlage einlesen" übernehmen darf
     * @param configuration der Knoten, dessen Kinder bearbeitet werden
     * @param beforeChange  Schnappschuss für "Rückgängig" (wird genau einmal
     *                      aufgerufen, unmittelbar vor dem Übernehmen)
     * @param onApplied     nach dem Übernehmen: Dokument als geändert
     *                      markieren und die Ansicht auffrischen
     */
    public static void showForConfiguration(Stage owner, String vehicleName, String protocol,
                                            XmlNode configuration,
                                            Runnable beforeChange, Runnable onApplied) {
        DecoderCaptureWindow window = new DecoderCaptureWindow();
        window.targetConfiguration = configuration;
        window.vehicleName = vehicleName;
        window.vehicleProtocol = protocol;
        window.beforeChange = beforeChange;
        window.onApplied = onApplied;
        window.open(owner);
    }

    private void open(Stage owner) {
        buildTable();

        BorderPane root = new BorderPane();
        root.setTop(buildHeader());
        root.setCenter(cvTable);
        root.setBottom(buildFooter());

        stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.NONE);
        stage.getIcons().addAll(loadAppIcons());

        if (targetConfiguration != null) {
            loadConfiguration();
            stage.setTitle(i18n.t("capture.editConfigTitle", vehicleName));
        } else if (sourceTemplate != null) {
            loadTemplate();
            stage.setTitle(i18n.t("capture.editTemplateTitle", sourceTemplate.getName()));
        } else {
            appendRows(INITIAL_ROWS);
            stage.setTitle(i18n.t("menu.decoderCapture"));
        }

        Scene scene = new Scene(root);
        ThemeManager.apply(scene, AppSettings.getInstance().getTheme());
        stage.setScene(scene);
        stage.setMinWidth(640);
        stage.setMinHeight(400);
        // Eigener Schlüssel je Betriebsart: Wer eine Vorlage erfasst, will das
        // Fenster oft anders stehen haben als beim Nachbessern der
        // Konfiguration eines Fahrzeugs.
        WindowState.apply(stage, targetConfiguration != null ? "decoderConfig" : "decoderCapture", 900, 800);
        stage.show();

        // Erst jetzt melden, was beim Laden aufgefallen ist: Vorher gab es
        // keine Scene, an der ein Dialog hängen könnte (siehe info()).
        flushPendingMessages();
    }

    /** Übernimmt Bezeichnung, Beschreibung und CV-Werte einer vorhandenen Vorlage. */
    private void loadTemplate() {
        nameField.setText(sourceTemplate.getName());
        descriptionField.setText(sourceTemplate.getDescription() == null ? "" : sourceTemplate.getDescription());
        try {
            placeAll(sourceTemplate.toConfigurationNode().getChildren());
        } catch (Exception ex) {
            // Auf die Ausgabe verlassen wir uns hier nicht mehr blind: Genau
            // dieser Auffangblock hat den ursprünglichen Fehler verdoppelt,
            // als info() selbst noch scheitern konnte. Die Ursache steht
            // zusätzlich auf der Fehlerausgabe, falls der Dialog ausbleibt.
            ex.printStackTrace();
            info(i18n.t("capture.templateLoadFailed", String.valueOf(ex.getMessage())));
        }
    }

    /** Übernimmt die vorhandenen CV-Werte des Fahrzeugs in die Tabelle. */
    private void loadConfiguration() {
        placeAll(targetConfiguration.getChildren());
    }

    /**
     * Trägt Parameter in die Zeile ihrer CV-Nummer ein - CV 8 in Zeile 8,
     * CV 900 in Zeile 900.
     * <p>
     * Früher wurden sie einfach der Reihe nach angehängt. Mit der festen
     * Nummerierung ginge das nicht mehr auf: Eine Vorlage listet die CVs
     * lückenhaft (1 bis 9, dann 17, 29, 50 ...), angehängt stünde CV 17 in
     * Zeile 10 und die Nummernspalte wäre falsch.
     * <p>
     * <h2>Einträge ohne Nummer</h2>
     * Es gibt Vorlagen, in denen einzelne Parameter <b>keine</b> CV-Nummer
     * tragen - vor der festen Nummerierung musste sie von Hand eingetippt
     * werden, und dabei blieb sie manchmal leer. In "NEM Standard.csv" etwa
     * steht zwischen CV 6 und CV 8 ein Eintrag "Version ID" ohne Nummer.
     * <p>
     * Solche Einträge werden <b>nicht</b> verworfen: Sie kämen sonst beim
     * nächsten Speichern still abhanden. Sie bekommen die nächste freie Zeile
     * hinter dem zuletzt belegten Platz - weil Vorlagen aufsteigend
     * geschrieben werden, trifft das meist die richtige Nummer (im Beispiel
     * CV 7). "Meist" ist aber nicht "immer", deshalb wird jeder solche Fall
     * gemeldet, mitsamt der Nummer, auf der er gelandet ist.
     */
    private void placeAll(List<XmlNode> parameters) {
        // Erst der Vorrat, dann füllen: rowForCv legt zwar bei Bedarf nach,
        // aber der Startvorrat soll auch dann stehen, wenn die Vorlage nur
        // niedrige CV-Nummern enthält.
        if (rows.isEmpty()) {
            appendRows(INITIAL_ROWS);
        }
        List<String> skipped = new ArrayList<>();
        List<String> guessed = new ArrayList<>();
        int lastCv = 0;
        for (XmlNode parameter : parameters) {
            int cv = parseCv(parameter.getAttribute("nr"));
            boolean withoutNumber = cv < 1;
            if (withoutNumber) {
                cv = nextFreeCv(lastCv + 1);
            }
            CvRow row = rowForCv(cv);
            if (row == null) {
                skipped.add(describeParameter(parameter));
                continue;
            }
            if (withoutNumber) {
                guessed.add(i18n.t("capture.guessedNumberEntry", describeParameter(parameter), cv));
            }
            lastCv = cv;
            // Inhalt übernehmen, Nummer nicht: die gehört der Zeile.
            copyAttribute(parameter, row.getParameter(), "value");
            copyAttribute(parameter, row.getParameter(), "type");
            XmlNode description = parameter.findChild("description");
            String text = description != null ? description.getTextContent() : null;
            if (text != null && !text.isBlank()) {
                setDescription(row.getParameter(), text);
            }
            row.syncActive();
        }
        if (!guessed.isEmpty()) {
            info(i18n.t("capture.guessedNumber", guessed.size(), String.join("\n", guessed)));
        }
        if (!skipped.isEmpty()) {
            info(i18n.t("capture.skippedWithoutNumber", skipped.size(), String.join(", ", skipped)));
        }
    }

    /**
     * Erste freie CV-Nummer ab {@code from}. Frei heißt: die Zeile ist noch
     * leer. Gibt es keine mehr, kommt {@link #MAX_CV}+1 zurück - dann greift
     * die Grenzprüfung in {@link #rowForCv} und der Eintrag wird gemeldet.
     */
    private int nextFreeCv(int from) {
        for (int cv = Math.max(1, from); cv <= MAX_CV; cv++) {
            if (cv > rows.size() || rows.get(cv - 1).isEmpty()) {
                return cv;
            }
        }
        return MAX_CV + 1;
    }

    /** Kurzbeschreibung eines Parameters für Meldungen: Typ, sonst Beschreibung. */
    private static String describeParameter(XmlNode parameter) {
        String type = parameter.getAttribute("type");
        if (type != null && !type.isBlank()) {
            return type.trim();
        }
        XmlNode description = parameter.findChild("description");
        String text = description != null ? description.getTextContent() : null;
        if (text != null && !text.isBlank()) {
            String flat = text.trim().replace("\n", " ");
            return flat.length() > 40 ? flat.substring(0, 40) + "..." : flat;
        }
        return "?";
    }

    private VBox buildHeader() {
        Label hint = new Label(i18n.t("capture.tableHint"));
        hint.setWrapText(true);
        hint.setStyle("-fx-opacity: 0.8;");

        if (targetConfiguration != null) {
            // Im Bearbeiten-Modus gibt es keine Vorlagen-Bezeichnung; statt
            // dessen steht dort, um wessen Konfiguration es geht.
            Label vehicle = new Label(i18n.t("capture.editConfigFor", vehicleName));
            vehicle.setStyle("-fx-font-weight: bold;");
            VBox box = new VBox(6, vehicle, hint);
            box.setPadding(new Insets(10, 10, 8, 10));
            return box;
        }

        nameField.setPromptText(i18n.t("capture.namePrompt"));
        descriptionField.setPromptText(i18n.t("capture.descriptionPrompt"));
        HBox.setHgrow(nameField, Priority.ALWAYS);
        HBox.setHgrow(descriptionField, Priority.ALWAYS);

        HBox nameRow = new HBox(8, new Label(i18n.t("editor.columnName")), nameField);
        nameRow.setAlignment(Pos.CENTER_LEFT);
        HBox descRow = new HBox(8, new Label(i18n.t("editor.columnDescription")), descriptionField);
        descRow.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(6, nameRow, descRow, hint);
        box.setPadding(new Insets(10, 10, 8, 10));
        return box;
    }

    /**
     * Dieselben Spalten wie die Konfigurations-Tabelle im Hauptfenster
     * (siehe {@code CategoryEditor.buildConfigPane}), damit das Ergebnis
     * genau dem entspricht, was iTrain erwartet.
     * <p>
     * Anders als dort ist "Aktiv" hier aber kein reines Anzeigefeld, sondern
     * die eigentliche Auswahl (siehe Klassenkommentar).
     */
    private void buildTable() {
        cvTable.setEditable(true);
        // Eigenes Stylesheet an der Tabelle selbst - nicht an der Scene:
        // ThemeManager.apply() leert beim Umschalten des Farbschemas die
        // Stylesheet-Liste der Scene, die eines Knotens fasst er nicht an.
        // Siehe den Kopf von capture-table.css.
        cvTable.getStyleClass().add("cv-table");
        var tableStyle = DecoderCaptureWindow.class.getResource("capture-table.css");
        if (tableStyle != null) {
            cvTable.getStylesheets().add(tableStyle.toExternalForm());
        }
        cvTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        // Zellweise Auswahl: Grundlage dafür, dass Tabulator und Pfeiltasten
        // gezielt von Feld zu Feld springen können.
        cvTable.getSelectionModel().setCellSelectionEnabled(true);
        cvTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        cvTable.setPlaceholder(new Label(i18n.t("capture.emptyTable")));

        TableColumn<CvRow, Boolean> activeCol = new TableColumn<>(i18n.t("editor.paramActive"));
        activeCol.setSortable(false);
        // So schmal wie die Überschrift, nicht breiter: Die Spalte enthält nur
        // ein Kästchen, und jeder Punkt, den sie belegt, fehlt der Beschreibung.
        // Die Breite wird gemessen statt festgelegt - "Aktiv" ist in anderen
        // Sprachen länger ("Attivo", "Aktywny"), eine feste Zahl würde die
        // Überschrift dort abschneiden.
        double activeWidth = headerWidth(activeCol.getText());
        activeCol.setPrefWidth(activeWidth);
        activeCol.setMinWidth(activeWidth);
        activeCol.setMaxWidth(activeWidth);
        activeCol.setEditable(true);
        activeCol.setCellValueFactory(data -> data.getValue().activeProperty());
        activeCol.setCellFactory(CheckBoxTableCell.forTableColumn(activeCol));
        cvTable.getColumns().add(activeCol);

        // Nr. und Wert sind Zahlen von höchstens vier Stellen (CV-Nummern
        // gehen bis 1024, ein CV-Wert ist ein Byte) - breiter müssen die
        // Spalten nicht sein. Der gewonnene Platz geht an die Bezeichnung,
        // wo er tatsächlich gebraucht wird.
        cvTable.getColumns().add(cvNumberColumn());
        cvTable.getColumns().add(numberColumn(i18n.t("editor.paramValue"), "value"));
        cvTable.getColumns().add(attributeColumn(i18n.t("editor.paramType"), "type", 130));

        TableColumn<CvRow, String> descCol = new TableColumn<>(i18n.t("editor.paramDescription"));
        descCol.setPrefWidth(360);
        descCol.setCellValueFactory(data -> {
            XmlNode desc = data.getValue().getParameter().findChild("description");
            return new ReadOnlyStringWrapper(desc != null && desc.getTextContent() != null
                    ? desc.getTextContent() : "");
        });
        descCol.setCellFactory(column -> new DescriptionCell());
        descCol.setOnEditCommit(event -> {
            setDescription(event.getRowValue().getParameter(), event.getNewValue());
            afterCellEdit(event.getRowValue());
        });
        cvTable.getColumns().add(descCol);

        installKeyboardHandling();
    }

    /**
     * Die CV-Nummer - fest an die Zeile gebunden: Zeile 1 ist CV 1, Zeile 8
     * ist CV 8, und das ändert sich nie.
     *
     * <h2>Warum nicht mehr eintippbar</h2>
     * Vorher musste die Nummer von Hand gesetzt werden. Das hatte zwei
     * Nachteile: Man tippte sie bei jeder Zeile mit, obwohl sie sich aus der
     * Reihenfolge ohnehin ergibt, und zwei Zeilen konnten dieselbe Nummer
     * tragen, ohne dass es auffiel. Jetzt ist die Spalte eine reine Anzeige;
     * welche Zeilen tatsächlich in die Vorlage wandern, entscheidet allein
     * der Haken in "Aktiv".
     * <p>
     * Der Wert wird aus dem {@code nr}-Attribut gelesen und nicht aus dem
     * Zeilenindex berechnet: {@code rows.indexOf(...)} je Zelle wäre bei
     * tausend Zeilen spürbar langsam. Das Attribut wird beim Anlegen der
     * Zeilen gesetzt und stimmt immer mit der Position überein - siehe
     * {@link #appendRows}.
     */
    private TableColumn<CvRow, String> cvNumberColumn() {
        TableColumn<CvRow, String> column = new TableColumn<>(i18n.t("editor.paramNr"));
        column.setPrefWidth(NUMBER_COLUMN_WIDTH);
        column.setMinWidth(NUMBER_COLUMN_WIDTH);
        column.setMaxWidth(NUMBER_COLUMN_WIDTH);
        column.setSortable(false);
        column.setEditable(false);
        column.setCellValueFactory(data -> {
            String nr = data.getValue().getParameter().getAttribute("nr");
            return new ReadOnlyStringWrapper(nr == null ? "" : nr);
        });
        column.setCellFactory(col -> {
            TableCell<CvRow, String> cell = new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item);
                }
            };
            cell.getStyleClass().add("cv-number-cell");
            return cell;
        });
        return column;
    }

    /**
     * Breite, die eine Spalte braucht, damit ihre Überschrift vollständig
     * hineinpasst - gemessen, nicht geraten.
     * <p>
     * Die Zugabe deckt ab, was neben dem reinen Text noch Platz beansprucht:
     * der Innenabstand der Kopfzelle links und rechts und der Rahmen. Für die
     * Aktiv-Spalte reicht das zugleich für das Kästchen, das mit rund 18
     * Punkten schmaler ist als jede der zehn Übersetzungen von "Aktiv".
     */
    private static double headerWidth(String title) {
        javafx.scene.text.Text probe = new javafx.scene.text.Text(title);
        return Math.ceil(probe.getLayoutBounds().getWidth()) + HEADER_PADDING;
    }

    /**
     * Schmale Spalte für eine vierstellige Zahl (Nr. bzw. Wert). Die Breite
     * ist fest: Beide Spalten nehmen nie mehr Platz ein, als vier Ziffern
     * brauchen - und wachsen auch nicht mit, wenn das Fenster breiter wird
     * (dafür ist die Bezeichnung da).
     */
    private TableColumn<CvRow, String> numberColumn(String title, String attribute) {
        TableColumn<CvRow, String> column = attributeColumn(title, attribute, NUMBER_COLUMN_WIDTH);
        column.setMinWidth(NUMBER_COLUMN_WIDTH);
        column.setMaxWidth(NUMBER_COLUMN_WIDTH);
        return column;
    }

    private TableColumn<CvRow, String> attributeColumn(String title, String attribute, double width) {
        TableColumn<CvRow, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(data -> {
            String value = data.getValue().getParameter().getAttribute(attribute);
            return new ReadOnlyStringWrapper(value == null ? "" : value);
        });
        column.setCellFactory(col -> new EditCell());
        column.setOnEditCommit(event -> {
            CvRow row = event.getRowValue();
            String value = event.getNewValue();
            if (value == null || value.isBlank()) {
                row.getParameter().removeAttribute(attribute);
            } else {
                row.getParameter().setAttribute(attribute, value.trim());
            }
            afterCellEdit(row);
        });
        return column;
    }

    /**
     * Nach jeder Eingabe: Der Haken folgt dem Inhalt, damit niemand 1000
     * Kästchen von Hand anklicken muss. Wird die Zeile wieder leer geräumt,
     * verschwindet er von selbst.
     */
    private void afterCellEdit(CvRow row) {
        row.syncActive();
        ensureSpareRows(rows.indexOf(row));
        // Bewusst kein cvTable.refresh(): Es baute alle sichtbaren Zellen neu
        // auf, wodurch das gerade offene Textfeld verschwand und der Fokus
        // aus der Tabelle fiel. Nötig ist es hier auch nicht - die bearbeitete
        // Zelle zeigt den neuen Wert von sich aus, und der Haken in "Aktiv"
        // hängt an einer Property, die ihre Anzeige selbst nachführt.
    }

    /**
     * Öffnet die Beschreibung einer Zeile in einem eigenen Fenster - mit
     * mehrzeiligem, umbrechendem Textbereich.
     * <p>
     * Der Anlass: In der Tabellenzelle ist von einer langen Beschreibung nur
     * der Anfang zu sehen, und das einzeilige Textfeld beim Bearbeiten hilft
     * kaum weiter. Hier steht der Text vollständig da, lässt sich lesen und
     * ändern.
     * <p>
     * "Speichern" schreibt zurück, "Abbruch" verwirft - dazwischen wird nichts
     * angetastet, das Fenster arbeitet also auf einer Kopie des Textes. Damit
     * bleibt ein versehentlich geöffnetes Fenster folgenlos.
     *
     * @param rowIndex Zeile in {@link #rows}; außerhalb des gültigen Bereichs
     *                 passiert nichts (die Tabelle hat stets Leerzeilen im
     *                 Vorrat, und der Doppelklick kann daneben gehen)
     */
    private void openDescriptionEditor(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= rows.size()) {
            return;
        }
        // Eine noch offene Zellbearbeitung beenden. Der erste Klick eines
        // Doppelklicks hat das Textfeld in der Zelle bereits geöffnet (siehe
        // EditCell); ohne diese Zeile stünden Textfeld und Fenster
        // gleichzeitig offen und die zuletzt geschlossene Eingabe gewänne.
        cvTable.edit(-1, null);

        CvRow row = rows.get(rowIndex);
        XmlNode desc = row.getParameter().findChild("description");
        String current = desc != null && desc.getTextContent() != null ? desc.getTextContent() : "";

        TextArea area = new TextArea(current);
        area.setWrapText(true);
        area.setPrefRowCount(12);
        area.setPrefColumnCount(60);

        // Welches CV bearbeitet wird, gehört sichtbar dazu: Bei 1000 Zeilen
        // ist nach dem Öffnen des Fensters sonst nicht mehr sicher, ob man
        // die gemeinte Zeile erwischt hat.
        String nr = row.getParameter().getAttribute("nr");
        Label caption = new Label(i18n.t("capture.descriptionEditFor",
                nr == null || nr.isBlank() ? "?" : nr));
        caption.setStyle("-fx-font-weight: bold;");

        VBox content = new VBox(8, caption, area);
        content.setPadding(new Insets(14));
        VBox.setVgrow(area, Priority.ALWAYS);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle(i18n.t("capture.descriptionEditTitle"));
        dialog.setResizable(true);
        dialog.getDialogPane().setContent(content);
        ButtonType save = new ButtonType(i18n.t("capture.descriptionEditSave"),
                ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(i18n.t("capture.descriptionEditCancel"),
                ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(cancel, save);

        if (dialog.showAndWait().orElse(cancel) != save) {
            return;
        }
        setDescription(row.getParameter(), area.getText());
        afterCellEdit(row);
    }

    /** Setzt/entfernt das {@code <description>}-Kind eines Parameters. */
    private void setDescription(XmlNode parameter, String text) {
        XmlNode desc = parameter.findChild("description");
        if (text == null || text.isBlank()) {
            if (desc != null) {
                parameter.getChildren().remove(desc);
            }
        } else {
            if (desc == null) {
                desc = new XmlNode("description");
                parameter.getChildren().add(desc);
            }
            desc.setTextContent(text.trim());
        }
    }

    // ------------------------------------------------------------------
    // Tastatur
    // ------------------------------------------------------------------

    /**
     * Tastaturbedienung der Tabelle. Ohne das hier müsste jede Zelle per
     * Doppelklick geöffnet und jede Eingabe mit der Eingabetaste bestätigt
     * werden - bei einer CV-Liste mit dutzenden Zeilen unzumutbar.
     * <ul>
     * <li>Tippen beginnt die Eingabe in der ausgewählten Zelle.</li>
     * <li>Eingabetaste öffnet die Zelle bzw. übernimmt und geht nach unten.</li>
     * <li>Tabulator übernimmt und geht nach rechts (mit Umschalt nach links).</li>
     * <li>Strg+V setzt den Text der Zwischenablage in die ausgewählte Zelle.</li>
     * </ul>
     */
    private void installKeyboardHandling() {
        cvTable.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (cvTable.getEditingCell() != null) {
                // Während des Editierens gelten die Tasten im Textfeld
                // (siehe EditCell) - hier nicht dazwischenfunken.
                return;
            }
            if (event.isControlDown() || event.isMetaDown()) {
                if (event.getCode() == KeyCode.V) {
                    pasteIntoFocusedCell();
                    event.consume();
                }
                return;
            }
            TablePosition<CvRow, ?> pos = focusedCell();
            if (pos == null) {
                return;
            }
            if (event.getCode() == KeyCode.ENTER) {
                editCell(pos.getRow(), Math.max(FIRST_EDITABLE_COLUMN, pos.getColumn()));
                event.consume();
            } else if (event.getCode() == KeyCode.TAB) {
                moveTo(pos.getRow(), pos.getColumn() + (event.isShiftDown() ? -1 : 1), false);
                event.consume();
            }
        });

        // Tippen startet die Eingabe. Bewusst KEY_TYPED: nur dort steht das
        // tatsächlich erzeugte Zeichen zur Verfügung (Tastaturbelegung,
        // Umlaute, Ziffernblock).
        cvTable.addEventFilter(KeyEvent.KEY_TYPED, event -> {
            if (cvTable.getEditingCell() != null
                    || event.isControlDown() || event.isMetaDown() || event.isAltDown()) {
                return;
            }
            String character = event.getCharacter();
            if (character == null || character.isEmpty() || character.charAt(0) < ' ') {
                return;
            }
            TablePosition<CvRow, ?> pos = focusedCell();
            if (pos == null || pos.getColumn() < FIRST_EDITABLE_COLUMN) {
                return;
            }
            pendingInitialText = character;
            editCell(pos.getRow(), pos.getColumn());
            event.consume();
        });
    }

    @SuppressWarnings("unchecked")
    private TablePosition<CvRow, ?> focusedCell() {
        List<TablePosition> selected = cvTable.getSelectionModel().getSelectedCells();
        if (selected.isEmpty()) {
            return null;
        }
        return (TablePosition<CvRow, ?>) selected.get(0);
    }

    /**
     * Bewegt die Auswahl auf die angegebene Zelle. Über den Zeilenrand hinaus
     * wird umgebrochen (letzte Spalte -> nächste Zeile), die Aktiv-Spalte
     * dabei übersprungen. {@code startEdit} öffnet die Zelle gleich zur
     * Eingabe - so kann man eine Liste ohne Unterbrechung durchtippen.
     */
    private void moveTo(int row, int column, boolean startEdit) {
        int lastColumn = cvTable.getColumns().size() - 1;
        int targetRow = row;
        int targetColumn = column;
        if (targetColumn > lastColumn) {
            targetColumn = FIRST_EDITABLE_COLUMN;
            targetRow++;
        } else if (targetColumn < FIRST_EDITABLE_COLUMN) {
            targetColumn = lastColumn;
            targetRow--;
        }
        if (targetRow < 0) {
            return;
        }
        ensureSpareRows(targetRow);
        if (targetRow >= rows.size()) {
            return;
        }
        final int r = targetRow;
        final int c = targetColumn;
        // Kein scrollTo mehr: Es rückte die Zeile an den oberen Rand, sodass
        // nach jeder Eingabe die halbe Tabelle wegsprang. Wo die Ansicht
        // steht, bestimmt allein der Schieberegler.
        cvTable.getSelectionModel().clearAndSelect(r, cvTable.getColumns().get(c));
        // Der Fokus muss ausdrücklich zurück auf die Tabelle: Beim Beenden
        // der Zellbearbeitung verschwindet das Textfeld, und ohne diese Zeile
        // wandert der Fokus über die normale Reihenfolge weiter - beim
        // Tabulator landete er dadurch in den Kopffeldern "Bezeichnung" und
        // "Beschreibung" statt in der nächsten Spalte.
        cvTable.requestFocus();
        if (startEdit) {
            editCell(r, c);
        }
    }

    /**
     * Öffnet eine Zelle zur Eingabe. Über {@code runLater}, weil die Tabelle
     * eine gerade beendete Bearbeitung erst abschließen muss - ein direkter
     * Aufruf würde von ihr wieder verworfen.
     */
    private void editCell(int row, int column) {
        if (row < 0 || row >= rows.size() || column < FIRST_EDITABLE_COLUMN) {
            return;
        }
        Platform.runLater(() -> {
            cvTable.getSelectionModel().clearAndSelect(row, cvTable.getColumns().get(column));
            cvTable.edit(row, cvTable.getColumns().get(column));
        });
    }

    /** Strg+V außerhalb der Bearbeitung: setzt die erste Zeile der Zwischenablage in die Zelle. */
    private void pasteIntoFocusedCell() {
        TablePosition<CvRow, ?> pos = focusedCell();
        if (pos == null || pos.getColumn() < FIRST_EDITABLE_COLUMN) {
            return;
        }
        String text = Clipboard.getSystemClipboard().getString();
        if (text == null || text.isBlank()) {
            return;
        }
        String value = text.split("\\R", 2)[0].trim();
        CvRow row = rows.get(pos.getRow());
        applyToColumn(row, pos.getColumn(), value);
        afterCellEdit(row);
    }

    /** Die CV-Nummer am Anfang einer eingefügten Zeile, oder -1. */
    private static int leadingCv(String line) {
        String[] tokens = line.split("\\s+");
        var matcher = NUMBER_TOKEN.matcher(tokens[0]);
        return matcher.matches() ? parseCv(matcher.group(1)) : -1;
    }

    /**
     * Schreibt einen Wert in die durch den Spaltenindex bezeichnete
     * Eigenschaft. Die Nummernspalte fehlt hier bewusst - sie ist nicht
     * beschreibbar, und ein Einfügen dorthin wird von den Aufrufern schon
     * über {@link #FIRST_EDITABLE_COLUMN} abgefangen.
     */
    private void applyToColumn(CvRow row, int columnIndex, String value) {
        String attribute = switch (columnIndex) {
            case COLUMN_VALUE -> "value";
            case COLUMN_TYPE -> "type";
            default -> null;
        };
        if (attribute == null) {
            setDescription(row.getParameter(), value);
        } else if (value == null || value.isBlank()) {
            row.getParameter().removeAttribute(attribute);
        } else {
            row.getParameter().setAttribute(attribute, value.trim());
        }
    }

    // ------------------------------------------------------------------
    // Bedienleiste
    // ------------------------------------------------------------------

    private HBox buildFooter() {
        Button addButton = new Button("+");
        addButton.setTooltip(new Tooltip(i18n.t("capture.addRows", ROW_BLOCK)));
        addButton.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-min-width: 32px;");
        addButton.setOnAction(e -> onAddRows());

        Button clearButton = new Button("X");
        clearButton.setTooltip(new Tooltip(i18n.t("capture.clearRow")));
        clearButton.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-min-width: 32px; -fx-text-fill: #c0392b;");
        clearButton.setOnAction(e -> onClearRow());

        Button pasteButton = new Button(i18n.t("capture.pasteHere"));
        pasteButton.setTooltip(new Tooltip(i18n.t("capture.pasteHereHint")));
        pasteButton.setOnAction(e -> pasteRowsHere());

        Button hintsButton = new Button(i18n.t("capture.hints"));
        hintsButton.setStyle(CategoryEditor.STYLE_CONFIG_EDIT);
        hintsButton.setOnAction(e -> showHints());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox box = new HBox(8, addButton, clearButton, pasteButton);

        // "Vorlage einlesen" nur dort, wo es etwas zu holen gibt: beim
        // Neuerfassen (leeres Fenster) und beim Bearbeiten der Konfiguration
        // eines Fahrzeugs. Beim Bearbeiten einer VORLAGE ist der Knopf
        // sinnlos - man hat die Vorlage ja schon offen, und eine zweite
        // darüberzuladen würde die gerade bearbeitete kommentarlos ersetzen.
        if (sourceTemplate == null) {
            Button importButton = new Button(i18n.t("capture.importTemplate"));
            importButton.setTooltip(new Tooltip(i18n.t("capture.importTemplateHint")));
            importButton.setStyle("-fx-background-color: #ded3f0; -fx-text-fill: #2b2b2b;");
            importButton.setOnAction(e -> importFromTemplate());
            box.getChildren().add(importButton);
        }

        box.getChildren().addAll(hintsButton, spacer);
        if (targetConfiguration != null) {
            Button applyButton = new Button(i18n.t("capture.apply"));
            applyButton.setStyle("-fx-background-color: #c8e6c9; -fx-text-fill: #2b2b2b;");
            applyButton.setOnAction(e -> applyToDocument());

            Button cancelButton = new Button(i18n.t("capture.cancel"));
            cancelButton.setOnAction(e -> stage.close());

            box.getChildren().addAll(applyButton, cancelButton);
        } else {
            Button saveButton = new Button(i18n.t("capture.save"));
            saveButton.setStyle("-fx-background-color: #c8e6c9; -fx-text-fill: #2b2b2b;");
            saveButton.setOnAction(e -> save(false, false));

            Button saveAsButton = new Button(i18n.t("capture.saveAs"));
            saveAsButton.setTooltip(new Tooltip(i18n.t("capture.saveAsHint")));
            saveAsButton.setStyle("-fx-background-color: #ded3f0; -fx-text-fill: #2b2b2b;");
            saveAsButton.setOnAction(e -> save(false, true));

            Button saveSendButton = new Button(i18n.t("capture.saveAndSend"));
            saveSendButton.setStyle("-fx-background-color: #cfe2f7; -fx-text-fill: #2b2b2b;");
            saveSendButton.setOnAction(e -> save(true, false));

            box.getChildren().addAll(saveButton, saveAsButton, saveSendButton);
        }
        box.setPadding(new Insets(8, 10, 10, 10));
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    // ------------------------------------------------------------------
    // Zeilen
    // ------------------------------------------------------------------

    /** Hängt weitere leere Zeilen an, falls der Vorrat einmal nicht reicht. */
    private void onAddRows() {
        int firstNew = rows.size();
        appendRows(ROW_BLOCK);
        cvTable.scrollTo(firstNew);
    }

    /**
     * Hängt leere Zeilen an und gibt jeder gleich ihre CV-Nummer mit: Die
     * erste Zeile ist CV 1, die tausendste CV 1000.
     * <p>
     * Die Nummer steht damit im Knoten und muss nicht je Zelle aus der
     * Position berechnet werden. Weil ausschließlich <b>angehängt</b> wird -
     * Zeilen werden nie eingefügt, entfernt oder umsortiert -, stimmt sie
     * dauerhaft mit dem Index überein.
     */
    private void appendRows(int count) {
        List<CvRow> fresh = new ArrayList<>(count);
        int firstCv = rows.size() + 1;
        for (int i = 0; i < count; i++) {
            XmlNode parameter = new XmlNode("parameter");
            parameter.setAttribute("nr", String.valueOf(firstCv + i));
            fresh.add(new CvRow(parameter));
        }
        rows.addAll(fresh);
    }

    /**
     * Liefert die Zeile einer CV-Nummer und legt bei Bedarf so viele Zeilen
     * an, dass es sie gibt. CV 900 landet also in Zeile 900, auch wenn die
     * Tabelle mit 1000 Zeilen startet und die Nummer darüber liegt.
     *
     * @return die Zeile, oder {@code null}, wenn die Nummer unbrauchbar ist
     *         (kleiner 1 oder jenseits von {@link #MAX_CV})
     */
    private CvRow rowForCv(int cv) {
        if (cv < 1 || cv > MAX_CV) {
            return null;
        }
        while (rows.size() < cv) {
            appendRows(Math.max(ROW_BLOCK, cv - rows.size()));
        }
        return rows.get(cv - 1);
    }

    /** CV-Nummer aus einem Text, oder -1, wenn keine brauchbare drinsteht. */
    private static int parseCv(String text) {
        if (text == null) {
            return -1;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    /**
     * Sorgt dafür, dass hinter der angegebenen Zeile immer noch Platz ist.
     * Damit ist die Startzahl von 1000 Zeilen nur eine Vorgabe: Wer mehr
     * braucht, bekommt sie, ohne etwas anklicken zu müssen.
     */
    private void ensureSpareRows(int rowIndex) {
        if (rowIndex >= 0 && rows.size() - rowIndex <= SPARE_ROWS) {
            appendRows(ROW_BLOCK);
        }
    }

    /**
     * Leert die markierte Zeile, statt sie zu entfernen: Bei einem festen
     * Zeilenvorrat ist das Aufräumen einer verrutschten Eingabe der
     * eigentliche Bedarf - und die Zeile steht danach sofort wieder zum
     * Ausfüllen bereit. Übernommen wird sie ohnehin nicht mehr, weil mit
     * dem Inhalt auch der Haken verschwindet.
     */
    private void onClearRow() {
        TablePosition<CvRow, ?> pos = focusedCell();
        if (pos == null || pos.getRow() < 0 || pos.getRow() >= rows.size()) {
            return;
        }
        rows.get(pos.getRow()).clear();
        cvTable.refresh();
    }

    /**
     * Verteilt den Inhalt der Zwischenablage ab der ausgewählten Zeile auf
     * mehrere Zeilen - gedacht für Werte, die in einem PDF-Programm aus der
     * Hersteller-Anleitung markiert und kopiert wurden.
     */
    private void pasteRowsHere() {
        String text = Clipboard.getSystemClipboard().getString();
        if (text == null || text.isBlank()) {
            info(i18n.t("capture.clipboardEmpty"));
            return;
        }
        TablePosition<CvRow, ?> pos = focusedCell();
        if (pos == null) {
            info(i18n.t("capture.pasteNoTarget"));
            return;
        }
        // Zeile für Zeile: Steht vorn eine CV-Nummer, gehört der Rest in die
        // Zeile eben dieser Nummer - eine Anleitung listet die CVs
        // lückenhaft, und genau dafür ist die feste Nummerierung da. Nur
        // wenn keine Nummer erkennbar ist, wird ab der markierten Zeile der
        // Reihe nach gefüllt.
        int fallback = pos.getRow();
        int filled = 0;
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int cv = leadingCv(trimmed);
            CvRow row = cv >= 1 ? rowForCv(cv) : null;
            if (row == null) {
                ensureSpareRows(fallback);
                if (fallback >= rows.size()) {
                    break;
                }
                row = rows.get(fallback);
                fallback++;
            }
            applyLine(row, trimmed);
            row.syncActive();
            filled++;
        }
        cvTable.refresh();
        info(i18n.t("capture.pasteResult", filled));
    }

    /**
     * Zerlegt eine eingefügte Textzeile in Nummer, Wert und Bezeichnung.
     * Bewusst schlicht und vorhersehbar: Beginnt die Zeile mit einer Zahl,
     * gilt diese als CV-Nummer; folgt darauf eine weitere kurze Zahl, ist das
     * der Wert; der Rest wird zur Bezeichnung. Passt nichts davon, landet die
     * ganze Zeile in der Bezeichnung - dort richtet sie keinen Schaden an und
     * lässt sich von Hand aufteilen.
     */
    private void applyLine(CvRow row, String line) {
        String[] tokens = line.split("\\s+");
        var numberMatcher = NUMBER_TOKEN.matcher(tokens[0]);
        if (!numberMatcher.matches()) {
            setDescription(row.getParameter(), line);
            return;
        }
        // Die führende Zahl wird NICHT eingetragen: Sie hat die Zeile
        // ausgesucht (siehe pasteRowsHere), und die Nummer der Zeile steht
        // ohnehin fest. Sie hier zu setzen, könnte sie nur verfälschen.
        int rest = 1;
        if (tokens.length > 1 && VALUE_TOKEN.matcher(tokens[1]).matches()) {
            row.getParameter().setAttribute("value", tokens[1]);
            rest = 2;
        }
        if (tokens.length > rest) {
            setDescription(row.getParameter(), String.join(" ", List.of(tokens).subList(rest, tokens.length)));
        }
    }

    // ------------------------------------------------------------------
    // Vorlage einlesen
    // ------------------------------------------------------------------

    /**
     * Übernimmt die CV-Liste einer Vorlage in die Tabelle. Der typische Fall:
     * Ein Fahrzeug hat nur eine Handvoll gepflegter CVs, die Vorlage des
     * Herstellers dagegen die vollständige Liste mit Bezeichnungen. Nach dem
     * Einlesen steht alles da - ohne dass die eigenen Werte verloren gehen.
     * <p>
     * Für CV-Nummern, die es hier schon gibt, wird deshalb gefragt:
     * <ul>
     * <li><b>Werte behalten</b> - der eigene Wert bleibt stehen, Typ und
     *     Bezeichnung kommen aus der Vorlage. Das ist der Regelfall: Die
     *     Adresse der eigenen Lok soll nicht durch die des Vorlagen-Fahrzeugs
     *     ersetzt werden.</li>
     * <li><b>Werte überschreiben</b> - auch die Werte stammen danach aus der
     *     Vorlage, etwa um einen Decoder auf den Auslieferungszustand
     *     zurückzusetzen.</li>
     * </ul>
     * Neue CV-Nummern werden in beiden Fällen ergänzt. Gefragt wird nur, wenn
     * es überhaupt Überschneidungen gibt.
     */
    private void importFromTemplate() {
        // Dieselbe Protokoll-Regel wie beim Import direkt aus der Hauptansicht
        // (siehe CategoryEditor.onDecoderImport): Beide Wege führen in
        // denselben <configuration>-Knoten, also darf nur einer von beiden
        // geprüft werden - sonst wäre die Sperre über den Umweg
        // "Konfiguration bearbeiten" schlicht zu umgehen. Greift nur im
        // Bearbeiten-Modus; beim Erfassen einer neuen Vorlage gibt es kein
        // Fahrzeug und damit kein Protokoll.
        DecoderProtocol.Rule rule = DecoderProtocol.ruleFor(vehicleProtocol);
        if (targetConfiguration != null && rule == DecoderProtocol.Rule.NONE) {
            info(i18n.t("capture.protocolBlocked", vehicleName,
                    DecoderProtocol.displayName(vehicleProtocol)));
            return;
        }

        Optional<DecoderTemplate> choice = DecoderTemplateChooser.choose(stage);
        if (choice.isEmpty()) {
            return;
        }
        DecoderTemplate template = choice.get();
        List<XmlNode> parameters;
        try {
            parameters = template.toConfigurationNode().getChildren();
        } catch (Exception ex) {
            info(i18n.t("capture.templateLoadFailed", String.valueOf(ex.getMessage())));
            return;
        }
        if (parameters.isEmpty()) {
            info(i18n.t("capture.importTemplateEmpty", template.getName()));
            return;
        }

        // Protokolle mit wenigen Konfigurationswerten (SX1): auf die ersten
        // Einträge kürzen, aber nur nach Rückfrage - siehe die
        // gleichlautende Begründung in CategoryEditor.onDecoderImport.
        if (targetConfiguration != null && rule == DecoderProtocol.Rule.LIMITED
                && parameters.size() > DecoderProtocol.LIMITED_MAX) {
            Alert limit = new Alert(Alert.AlertType.CONFIRMATION,
                    i18n.t("capture.protocolLimited",
                            DecoderProtocol.displayName(vehicleProtocol),
                            template.getName(),
                            parameters.size(),
                            DecoderProtocol.LIMITED_MAX),
                    ButtonType.YES, ButtonType.NO);
            limit.initOwner(stage);
            limit.setTitle(i18n.t("capture.importTemplate"));
            limit.setHeaderText(null);
            Optional<ButtonType> proceed = limit.showAndWait();
            if (proceed.isEmpty() || proceed.get() != ButtonType.YES) {
                return;
            }
            // Eigene Liste: getChildren() gehört dem frisch erzeugten
            // Vorlagen-Knoten, der sonst nirgends weiterverwendet wird - eine
            // Kopie macht die Absicht aber unmissverständlich.
            parameters = new ArrayList<>(parameters.subList(0, DecoderProtocol.LIMITED_MAX));
        }

        // Überschneidungen zählen: Die Zeile einer CV-Nummer steht seit der
        // festen Nummerierung fest, es genügt also nachzusehen, ob dort schon
        // etwas eingetragen ist.
        int overlapping = 0;
        for (XmlNode parameter : parameters) {
            int cv = parseCv(parameter.getAttribute("nr"));
            if (cv >= 1 && cv <= rows.size() && !rows.get(cv - 1).isEmpty()) {
                overlapping++;
            }
        }

        boolean keepValues = true;
        if (overlapping > 0) {
            ButtonType keep = new ButtonType(i18n.t("capture.importKeepValues"), ButtonBar.ButtonData.OK_DONE);
            ButtonType replace = new ButtonType(i18n.t("capture.importReplaceValues"), ButtonBar.ButtonData.OTHER);
            ButtonType cancel = new ButtonType(i18n.t("capture.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
            Alert ask = new Alert(Alert.AlertType.CONFIRMATION,
                    i18n.t("capture.importQuestion", template.getName(), parameters.size(), overlapping),
                    keep, replace, cancel);
            ask.initOwner(stage);
            ask.setTitle(i18n.t("capture.importTemplate"));
            ask.setHeaderText(null);
            Optional<ButtonType> answer = ask.showAndWait();
            if (answer.isEmpty() || answer.get() == cancel) {
                return;
            }
            keepValues = answer.get() == keep;
        }

        int updated = 0;
        int added = 0;
        List<String> skipped = new ArrayList<>();
        for (XmlNode parameter : parameters) {
            int cv = parseCv(parameter.getAttribute("nr"));
            CvRow row = rowForCv(cv);
            if (row == null) {
                skipped.add(String.valueOf(parameter.getAttribute("nr")));
                continue;
            }
            // War die Zeile schon belegt, gilt die Antwort auf die Rückfrage
            // oben; eine bis dahin leere Zeile bekommt den Wert der Vorlage,
            // da ist nichts zu schützen.
            boolean occupied = !row.isEmpty();
            mergeInto(row, parameter, occupied && keepValues);
            if (occupied) {
                updated++;
            } else {
                added++;
            }
        }
        if (!skipped.isEmpty()) {
            info(i18n.t("capture.skippedWithoutNumber", skipped.size(), String.join(", ", skipped)));
        }
        cvTable.refresh();
        info(i18n.t("capture.importResult", added, updated,
                i18n.t(keepValues ? "capture.importKeptShort" : "capture.importReplacedShort")));
    }

    /**
     * Überträgt einen Parameter der Vorlage in eine Zeile. Typ und
     * Bezeichnung kommen immer aus der Vorlage - sie sind es, weswegen man
     * eine Vorlage einliest. Der Wert nur, wenn er nicht geschützt ist.
     */
    private void mergeInto(CvRow row, XmlNode parameter, boolean keepExistingValue) {
        XmlNode target = row.getParameter();
        // "nr" wird bewusst NICHT übernommen: Die Nummer gehört seit der
        // festen Nummerierung zur Zeile, und die Zeile wurde gerade anhand
        // eben dieser Nummer ausgesucht.
        copyAttribute(parameter, target, "type");
        if (!keepExistingValue) {
            copyAttribute(parameter, target, "value");
        }
        XmlNode description = parameter.findChild("description");
        String text = description != null ? description.getTextContent() : null;
        if (text != null && !text.isBlank()) {
            setDescription(target, text);
        }
        row.syncActive();
    }

    private static void copyAttribute(XmlNode from, XmlNode to, String name) {
        String value = from.getAttribute(name);
        if (value == null || value.isBlank()) {
            to.removeAttribute(name);
        } else {
            to.setAttribute(name, value);
        }
    }

    /** Alle Zeilen, die angehakt <em>und</em> ausgefüllt sind. */
    private List<CvRow> selectedRows() {
        List<CvRow> selected = new ArrayList<>();
        for (CvRow row : rows) {
            if (row.isActive() && !row.isEmpty()) {
                selected.add(row);
            }
        }
        return selected;
    }

    // ------------------------------------------------------------------
    // Übernehmen bzw. Speichern
    // ------------------------------------------------------------------

    /**
     * Schreibt die erfassten CV-Werte in den {@code <configuration>}-Knoten
     * des Fahrzeugs zurück. Der Knoten selbst bleibt dabei erhalten und wird
     * nur neu befüllt - so bleibt seine Position innerhalb des Fahrzeugs
     * garantiert unverändert, worauf iTrain empfindlich reagiert.
     */
    private void applyToDocument() {
        List<CvRow> selected = selectedRows();
        if (selected.isEmpty()) {
            // Alles abgewählt hieße: Konfiguration leeren. Das ist so
            // ungewöhnlich, dass eine Rückfrage angebracht ist.
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    i18n.t("capture.applyEmptyConfirm"), ButtonType.YES, ButtonType.NO);
            confirm.initOwner(stage);
            confirm.setHeaderText(null);
            Optional<ButtonType> answer = confirm.showAndWait();
            if (answer.isEmpty() || answer.get() != ButtonType.YES) {
                return;
            }
        }
        if (beforeChange != null) {
            beforeChange.run();
        }
        targetConfiguration.getChildren().clear();
        for (CvRow row : selected) {
            targetConfiguration.getChildren().add(row.getParameter().deepCopy());
        }
        // Das count-Attribut nur nachführen, wenn es vorher da war: In echten
        // iTrain-Dateien trägt configuration ein count (z.B. count="86"),
        // TcdDocument berechnet es aber nur für die 15 bekannten Kategorien
        // neu - hier also selbst.
        if (targetConfiguration.getAttribute("count") != null) {
            targetConfiguration.setAttribute("count", String.valueOf(selected.size()));
        }
        if (onApplied != null) {
            onApplied.run();
        }
        info(i18n.t("capture.applied", selected.size(), vehicleName));
        stage.close();
    }

    /**
     * Speichert die erfassten CVs als Decoder-Vorlage im Vorlagen-Ordner.
     * Bei {@code send} wird anschließend zusätzlich eine E-Mail vorbereitet
     * (siehe {@link MailSender}), bei {@code asCopy} wird vorher nach einer
     * neuen Bezeichnung gefragt.
     * <p>
     * Übernommen wird nur, was angehakt <em>und</em> ausgefüllt ist - der
     * leere Rest des Zeilenvorrats fällt hier weg und landet nie in der
     * Vorlage.
     */
    private void save(boolean send, boolean asCopy) {
        List<CvRow> selected = selectedRows();
        if (selected.isEmpty()) {
            info(i18n.t("capture.nothingToSave"));
            return;
        }

        if (asCopy && !askForCopyName()) {
            return;
        }

        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (name.isEmpty()) {
            info(i18n.t("capture.nameRequired"));
            nameField.requestFocus();
            return;
        }

        String dir = AppSettings.getInstance().getDecoderDirectory();
        if (dir == null || dir.isBlank() || !new File(dir).isDirectory()) {
            info(i18n.t("editor.decoderNoDirectory"));
            return;
        }

        File target = new File(dir, DecoderTemplate.toFileName(name) + ".csv");
        // Beim Bearbeiten einer vorhandenen Vorlage unter unverändertem Namen
        // wird ohne Rückfrage genau deren Datei zurückgeschrieben - alles
        // andere wäre für "Speichern" überraschend.
        boolean writingBackSource = sourceTemplate != null && target.equals(sourceTemplate.getFile());
        if (!writingBackSource && target.exists()) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    i18n.t("editor.decoderOverwrite", target.getName()),
                    ButtonType.YES, ButtonType.NO);
            confirm.initOwner(stage);
            confirm.setHeaderText(null);
            Optional<ButtonType> answer = confirm.showAndWait();
            if (answer.isEmpty() || answer.get() != ButtonType.YES) {
                return;
            }
        }

        try {
            XmlNode configuration = new XmlNode(DecoderTemplate.CONFIGURATION_TAG);
            configuration.setAttribute("count", String.valueOf(selected.size()));
            for (CvRow row : selected) {
                configuration.getChildren().add(row.getParameter().deepCopy());
            }
            String description = descriptionField.getText() == null ? "" : descriptionField.getText().trim();
            DecoderTemplate.write(target, name, description, configuration);
            // Ab jetzt ist DIESE Datei die Herkunft - ein weiteres "Speichern"
            // schreibt wieder dorthin, statt erneut nachzufragen.
            sourceTemplate = DecoderTemplate.read(target);
            if (onApplied != null) {
                onApplied.run();
            }

            if (send) {
                MailSender.sendDecoderTemplate(stage, target, name);
            } else {
                info(i18n.t("capture.saved", selected.size(), target.getAbsolutePath()));
            }
        } catch (Exception ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR, String.valueOf(ex.getMessage()));
            alert.initOwner(stage);
            alert.setHeaderText(i18n.t("editor.exportErrorTitle"));
            alert.showAndWait();
        }
    }

    /**
     * Fragt die Bezeichnung für "Speichern als" ab und übernimmt sie ins
     * Namensfeld. Gibt {@code false} zurück, wenn abgebrochen wurde.
     */
    private boolean askForCopyName() {
        String current = nameField.getText() == null ? "" : nameField.getText().trim();
        TextInputDialog dialog = new TextInputDialog(
                current.isEmpty() ? "" : i18n.t("capture.copySuffix", current));
        dialog.initOwner(stage);
        dialog.setTitle(i18n.t("capture.saveAs"));
        dialog.setHeaderText(i18n.t("capture.saveAsHeader"));
        Optional<String> answer = dialog.showAndWait();
        if (answer.isEmpty() || answer.get().isBlank()) {
            return false;
        }
        nameField.setText(answer.get().trim());
        // Ab jetzt eine eigenständige Vorlage - die geladene bleibt unberührt.
        sourceTemplate = null;
        return true;
    }

    // ------------------------------------------------------------------
    // Hinweise
    // ------------------------------------------------------------------

    /**
     * Zeigt den Hinweis-Dialog: die empfohlene Arbeitsweise, die Anleitung
     * des Herstellers in einem PDF-Programm daneben zu öffnen und die Werte
     * über die Zwischenablage zu übernehmen.
     * <p>
     * Der Text kommt wie alle Hilfetexte aus {@link I18n} und wird über
     * {@link HelpDialog#renderBlocks} dargestellt - damit gilt hier dieselbe
     * einfache Auszeichnung ({@code **fett**}, Aufzählungen mit "- ").
     * Darunter eine kleine Skizze der Fensteranordnung, die bewusst mit
     * JavaFX-Bordmitteln gezeichnet wird: kein Bild als Ressource, keine
     * zusätzliche Bibliothek, keine Lizenzfrage - und sie passt sich der
     * Schriftgröße an.
     */
    private void showHints() {
        VBox content = new VBox(4);
        content.getChildren().addAll(HelpDialog.renderBlocks(i18n.t("capture.hintsText")));
        content.getChildren().add(buildIllustration());
        content.setPadding(new Insets(14));

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);

        Button close = new Button(i18n.t("capture.hintsClose"));
        close.setDefaultButton(true);
        HBox buttonRow = new HBox(close);
        buttonRow.setAlignment(Pos.CENTER_RIGHT);
        buttonRow.setPadding(new Insets(0, 14, 12, 14));

        BorderPane root = new BorderPane(scroll);
        root.setBottom(buttonRow);

        Stage dialog = new Stage();
        dialog.initOwner(stage);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(i18n.t("capture.hints"));
        dialog.getIcons().addAll(loadAppIcons());
        Scene scene = new Scene(root, 680, 620);
        ThemeManager.apply(scene, AppSettings.getInstance().getTheme());
        dialog.setScene(scene);
        close.setOnAction(e -> dialog.close());
        dialog.showAndWait();
    }

    /**
     * Kleine Skizze: links das Fenster mit der Decoder-Konfiguration, rechts
     * die Anleitung im PDF-Programm, dazwischen der Weg über die
     * Zwischenablage. Gezeichnet aus einfachen Formen - siehe
     * {@link #showHints()}.
     */
    private Pane buildIllustration() {
        Pane canvas = new Pane();
        canvas.setPrefSize(560, 210);
        canvas.setMinSize(560, 210);
        canvas.setMaxSize(560, 210);

        // Die Skizze besteht aus Rechtecken und Linien, nicht aus Steuer-
        // elementen - CSS greift dort nicht (Formen kennen weder
        // -fx-text-fill noch die Farbvariablen des Schemas). Deshalb werden
        // die Farben hier einmal passend zum eingestellten Schema gewählt.
        // Mit fest hellen Farben verschwanden Rahmen und Beschriftungen im
        // dunklen Schema nahezu vollständig.
        boolean dark = AppSettings.THEME_DARK.equals(AppSettings.getInstance().getTheme());
        Color frame = Color.web(dark ? "#9aa0a6" : "#8a8a8a");
        Color line = Color.web(dark ? "#6e7479" : "#b4b4b4");
        Color accent = Color.web(dark ? "#6ba4e8" : "#2f6fb5");
        Color leftTitle = Color.web(dark ? "#31506e" : "#cfe2f7");
        Color rightTitle = Color.web(dark ? "#6b5a2e" : "#ffe3a3");
        Color textColor = Color.web(dark ? "#e0e0e0" : "#000000");

        // Linkes Fenster: unsere Konfigurations-Tabelle.
        canvas.getChildren().add(caption(10, 4, i18n.t("capture.hintsPictureLeft"), textColor));
        canvas.getChildren().addAll(windowFrame(10, 26, 240, 160, frame, leftTitle));
        for (int i = 0; i < 6; i++) {
            // Zeilen der Tabelle, die erste etwas dunkler (Kopfzeile).
            canvas.getChildren().add(bar(20, 62 + i * 19, 220, i == 0 ? frame : line));
        }

        // Rechtes Fenster: die Anleitung des Herstellers.
        canvas.getChildren().add(caption(310, 4, i18n.t("capture.hintsPictureRight"), textColor));
        canvas.getChildren().addAll(windowFrame(310, 26, 240, 160, frame, rightTitle));
        for (int i = 0; i < 6; i++) {
            canvas.getChildren().add(bar(320, 62 + i * 19, 220, line));
        }
        // Zwei hervorgehobene Zeilen = die markierte CV-Tabelle.
        Rectangle selection = new Rectangle(316, 96, 228, 42);
        selection.setFill(accent.deriveColor(0, 1, 1, 0.30));
        selection.setStroke(accent);
        selection.setStrokeWidth(1);
        canvas.getChildren().add(selection);

        // Pfeil von rechts nach links: der Weg über die Zwischenablage.
        Line shaft = new Line(304, 117, 262, 117);
        shaft.setStroke(accent);
        shaft.setStrokeWidth(2.5);
        Polygon head = new Polygon(252, 117, 264, 111, 264, 123);
        head.setFill(accent);
        canvas.getChildren().addAll(shaft, head);

        Text arrowLabel = new Text(i18n.t("capture.hintsPictureArrow"));
        arrowLabel.setFill(accent);
        arrowLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: bold;");
        arrowLabel.setLayoutX(252);
        arrowLabel.setLayoutY(107);
        canvas.getChildren().add(arrowLabel);

        Text footer = new Text(i18n.t("capture.hintsPictureFooter"));
        footer.setStyle("-fx-font-size: 11px;");
        footer.setFill(textColor);
        // Kein setOpacity mehr: Im dunklen Schema war die ohnehin gedämpfte
        // Fußzeile damit kaum noch vom Hintergrund zu unterscheiden. Der
        // zurückhaltende Eindruck entsteht jetzt über die kleinere Schrift.
        footer.setLayoutX(10);
        footer.setLayoutY(204);
        canvas.getChildren().add(footer);

        VBox.setMargin(canvas, new Insets(12, 0, 0, 0));
        return canvas;
    }

    /** Fensterrahmen mit farbiger Titelleiste. */
    private static List<javafx.scene.Node> windowFrame(double x, double y, double w, double h,
                                                       Color border, Color titleBar) {
        Rectangle body = new Rectangle(x, y, w, h);
        body.setFill(Color.TRANSPARENT);
        body.setStroke(border);
        body.setStrokeWidth(1.5);
        body.setArcWidth(6);
        body.setArcHeight(6);

        Rectangle title = new Rectangle(x, y, w, 18);
        title.setFill(titleBar);
        title.setStroke(border);
        title.setStrokeWidth(1);
        return List.of(body, title);
    }

    /** Eine angedeutete Textzeile. */
    private static Rectangle bar(double x, double y, double w, Color color) {
        Rectangle rectangle = new Rectangle(x, y, w, 6);
        rectangle.setFill(color);
        rectangle.setArcWidth(3);
        rectangle.setArcHeight(3);
        return rectangle;
    }

    /** Beschriftung über einem der beiden Fenster. */
    private static Text caption(double x, double y, String text, Color color) {
        Text label = new Text(text);
        label.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
        // Farbe ausdrücklich setzen: Text ist eine Form und bekommt sonst die
        // Vorgabe Schwarz - im dunklen Schema unlesbar.
        label.setFill(color);
        label.setLayoutX(x);
        label.setLayoutY(y + 12);
        return label;
    }

    /**
     * Zeigt eine Meldung - und merkt sie sich, solange das Fenster dafür noch
     * nicht bereit ist.
     *
     * <h2>Warum die Verzögerung nötig ist</h2>
     * {@code Dialog.initOwner(...)} greift auf die <b>Scene</b> des Besitzers
     * zu, um dessen Stylesheets zu übernehmen. Beim Laden einer Vorlage steht
     * die Scene aber noch nicht: {@link #open} legt erst die Stage an, füllt
     * dann die Tabelle und setzt die Scene erst danach. Eine Meldung aus dem
     * Laden heraus lief deshalb in eine {@code NullPointerException} - und
     * weil der Auffangblock in {@link #loadTemplate} daraufhin <em>noch
     * einmal</em> meldete, kam sie ein zweites Mal und riss das Fenster mit.
     * <p>
     * Gemeldet wird jetzt nach dem Anzeigen des Fensters, gesammelt in einer
     * einzigen Meldung. Das ist auch inhaltlich besser: Man sieht die Tabelle
     * bereits und kann nachsehen, wovon die Meldung spricht.
     */
    private void info(String message) {
        if (stage == null || stage.getScene() == null || !stage.isShowing()) {
            pendingMessages.add(message);
            return;
        }
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.initOwner(stage);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    /** Gesammelte Meldungen aus der Ladephase ausgeben - siehe {@link #info}. */
    private void flushPendingMessages() {
        if (pendingMessages.isEmpty()) {
            return;
        }
        String text = String.join("\n\n", pendingMessages);
        pendingMessages.clear();
        info(text);
    }

    private static Image[] loadAppIcons() {
        try (InputStream in = DecoderCaptureWindow.class.getResourceAsStream("app-icon.png")) {
            return in == null ? new Image[0] : new Image[]{new Image(in)};
        } catch (Exception ex) {
            return new Image[0];
        }
    }

    // ------------------------------------------------------------------
    // Zelle und Zeile
    // ------------------------------------------------------------------

    /**
     * Textzelle mit erweiterter Tastaturbedienung. {@link TextFieldTableCell}
     * bestätigt von sich aus nur mit der Eingabetaste und verwirft die
     * Eingabe sogar, wenn man einfach woanders hinklickt. Hier wird deshalb
     * ergänzt:
     * <ul>
     * <li>Tabulator, Pfeil hoch/runter und Eingabetaste übernehmen den Wert
     *     und springen weiter,</li>
     * <li>Fokusverlust übernimmt ebenfalls, statt die Eingabe zu verlieren,</li>
     * <li>ein vorgemerktes Zeichen ({@link #pendingInitialText}) belegt das
     *     Feld vor, damit einfaches Tippen die Eingabe beginnt.</li>
     * </ul>
     */
    /**
     * Zelle der Beschreibungsspalte. Verhält sich wie {@link EditCell}, öffnet
     * beim <b>Doppelklick</b> aber statt des einzeiligen Textfelds ein eigenes
     * Fenster mit umbrechendem Textbereich - siehe
     * {@link #openDescriptionEditor(int)}.
     * <p>
     * Beschreibungen aus Hersteller-Anleitungen sind oft mehrere Zeilen lang
     * ("0 = deaktiv / 1 = PZB Infraroterkennung / ..."). In der Tabellenzelle
     * ist davon nur der Anfang zu sehen, und im einzeiligen Textfeld muss man
     * sich mit den Pfeiltasten durchschieben.
     * <p>
     * Die übrigen Wege zum Bearbeiten bleiben unverändert: Tippen und
     * Eingabetaste öffnen weiterhin das Textfeld in der Zelle. Wer nur schnell
     * etwas Kurzes eintragen will, wird also nicht mit einem Fenster behelligt.
     */
    private final class DescriptionCell extends EditCell {

        DescriptionCell() {
            // Als Filter und nicht als Handler: Die Tabelle startet das
            // Bearbeiten in der Zelle selbst beim Doppelklick. Nur ein Filter
            // kommt vorher dran und kann das mit consume() verhindern - sonst
            // stünden Textfeld und Fenster gleichzeitig offen.
            addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                if (event.getClickCount() == 2 && !isEmpty() && event.isPrimaryButtonDown()) {
                    event.consume();
                    openDescriptionEditor(getIndex());
                }
            });
        }
    }

    private class EditCell extends TextFieldTableCell<CvRow, String> {

        EditCell() {
            super(new DefaultStringConverter());
            // Ein Klick genügt. Vorher brauchte es einen Doppelklick, und bis
            // dahin war die Zelle nur blau hinterlegt - man sah eine Auswahl,
            // konnte aber nicht tippen. Jetzt steht der Schreibcursor sofort
            // in der angeklickten Zelle.
            addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                if (event.getClickCount() != 1 || !event.isPrimaryButtonDown() || isEmpty()) {
                    return;
                }
                int column = getTableView().getColumns().indexOf(getTableColumn());
                if (column < FIRST_EDITABLE_COLUMN) {
                    return;
                }
                editCell(getIndex(), column);
            });
        }

        @Override
        public void startEdit() {
            super.startEdit();
            if (!(getGraphic() instanceof TextField field)) {
                return;
            }
            if (pendingInitialText != null) {
                field.setText(pendingInitialText);
                field.positionCaret(field.getText().length());
                pendingInitialText = null;
            }
            // TextFieldTableCell legt bei jedem startEdit ein neues Textfeld
            // an - die Handler können deshalb ohne Doppelregistrierung
            // einfach hier angehängt werden.
            field.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                int row = getIndex();
                int column = getTableView().getColumns().indexOf(getTableColumn());
                switch (event.getCode()) {
                    case TAB -> {
                        commitEdit(field.getText());
                        moveTo(row, column + (event.isShiftDown() ? -1 : 1), true);
                        event.consume();
                    }
                    case ENTER, DOWN -> {
                        commitEdit(field.getText());
                        moveTo(row + 1, column, event.getCode() == KeyCode.DOWN);
                        event.consume();
                    }
                    case UP -> {
                        commitEdit(field.getText());
                        moveTo(row - 1, column, true);
                        event.consume();
                    }
                    default -> {
                        // andere Tasten wie gewohnt ans Textfeld
                    }
                }
            });
            field.focusedProperty().addListener((obs, hadFocus, hasFocus) -> {
                if (!hasFocus && isEditing()) {
                    commitEdit(field.getText());
                }
            });
        }
    }

    /**
     * Eine Tabellenzeile: der eigentliche {@code <parameter>}-Knoten plus der
     * Haken "Aktiv".
     * <p>
     * Der Haken bewusst <em>nicht</em> als Attribut im {@link XmlNode}: Er ist
     * eine reine Bedienhilfe dieses Fensters und hat in der gespeicherten
     * Datei nichts zu suchen - iTrain kennt an einem Parameter kein solches
     * Feld. Die Hülle hält beides zusammen, ohne den XML-Knoten zu verändern;
     * übernommen wird am Ende nur {@link #getParameter()}.
     */
    public static final class CvRow {

        private final BooleanProperty active = new SimpleBooleanProperty(false);
        private final XmlNode parameter;

        CvRow(XmlNode parameter) {
            this.parameter = parameter;
        }

        public BooleanProperty activeProperty() {
            return active;
        }

        public boolean isActive() {
            return active.get();
        }

        public XmlNode getParameter() {
            return parameter;
        }

        /** Zeile ohne jeden Inhalt - also eine bloße Vorratszeile. */
        /**
         * Leer heißt: nichts eingetragen, was die Zeile lohnenswert machte.
         * <p>
         * Die CV-Nummer zählt bewusst <b>nicht</b> mit. Sie steht seit der
         * festen Nummerierung in jeder Zeile, auch in den tausend leeren -
         * würde sie mitzählen, wäre jede Zeile "gefüllt" und der Haken in
         * "Aktiv" überall gesetzt.
         */
        public boolean isEmpty() {
            return blank(parameter.getAttribute("value"))
                    && blank(parameter.getAttribute("type"))
                    && parameter.findChild("description") == null;
        }

        /** Haken dem Inhalt nachführen (siehe Klassenkommentar des Fensters). */
        void syncActive() {
            active.set(!isEmpty());
        }

        /** Setzt die Zeile auf den Zustand einer frischen Vorratszeile zurück. */
        void clear() {
            // Die CV-Nummer bleibt: Sie gehört zur Zeile, nicht zum Inhalt.
            parameter.removeAttribute("value");
            parameter.removeAttribute("type");
            XmlNode desc = parameter.findChild("description");
            if (desc != null) {
                parameter.getChildren().remove(desc);
            }
            active.set(false);
        }

        private static boolean blank(String value) {
            return value == null || value.isBlank();
        }
    }
}
