package com.example.itrain_import_export;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.InputStream;

/**
 * Menüpunkt Einstellungen → "Decoder-Vorlagen": eigenes, in der Größe
 * veränderbares Fenster mit einer Übersicht aller installierten Vorlagen.
 * <p>
 * Bewusst eine reine ÜBERSICHT: gezeigt werden nur Bezeichnung, Beschreibung,
 * Anzahl der enthaltenen CV-Einträge und der Dateiname - nicht die CV-Werte
 * selbst. Die tatsächlichen Daten bekommt man zu sehen, indem man eine
 * Vorlage über "Decoder importieren" in eine Lokomotive bzw. einen Wagen
 * einliest; dort lassen sie sich dann auch bearbeiten (Daten-Explorer →
 * configuration → "In eigenem Fenster bearbeiten").
 * <p>
 * Oben steht ein Suchfeld (filtert über Bezeichnung, Beschreibung und
 * Dateiname), unten die Herkunftsangabe: Versionskennung aus der
 * {@code version.txt} des Archivs und der Zeitpunkt der Installation (siehe
 * {@link DecoderTemplateInstaller}).
 */
public final class DecoderTemplateBrowser {

    private DecoderTemplateBrowser() {
    }

    public static void show(Stage owner) {
        I18n i18n = I18n.getInstance();
        AppSettings settings = AppSettings.getInstance();

        ObservableList<DecoderTemplate> all = FXCollections.observableArrayList(DecoderTemplate.loadAll());
        FilteredList<DecoderTemplate> filtered = new FilteredList<>(all, t -> true);

        TextField searchField = new TextField();
        searchField.setPromptText(i18n.t("decoder.searchPrompt"));
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchField.textProperty().addListener((obs, oldText, newText) -> {
            String needle = newText == null ? "" : newText.trim().toLowerCase();
            filtered.setPredicate(template -> matches(template, needle));
        });
        HBox searchRow = new HBox(8, new Label(i18n.t("decoder.searchLabel")), searchField);
        searchRow.setPadding(new Insets(10, 10, 6, 10));

        TableView<DecoderTemplate> table = new TableView<>(filtered);
        table.setPlaceholder(new Label(i18n.t("decoder.noTemplates")));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<DecoderTemplate, String> nameCol = new TableColumn<>(i18n.t("editor.columnName"));
        nameCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getName()));
        nameCol.setPrefWidth(220);

        // Anzahl der CV-Einträge: eine Zahl von höchstens fünf Stellen -
        // breiter muss die Spalte nicht werden, der Platz gehört der
        // Beschreibung.
        TableColumn<DecoderTemplate, Number> countCol = new TableColumn<>(i18n.t("decoder.columnParameters"));
        countCol.setCellValueFactory(data ->
                new ReadOnlyObjectWrapper<>(data.getValue().getParameterCount()));
        countCol.setPrefWidth(70);
        countCol.setMinWidth(70);
        countCol.setMaxWidth(70);

        TableColumn<DecoderTemplate, String> fileCol = new TableColumn<>(i18n.t("decoder.columnFile"));
        fileCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getFile().getName()));
        fileCol.setPrefWidth(200);

        TableColumn<DecoderTemplate, String> descCol = new TableColumn<>(i18n.t("editor.columnDescription"));
        descCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getDescription()));

        table.getColumns().add(nameCol);
        table.getColumns().add(countCol);
        table.getColumns().add(fileCol);
        table.getColumns().add(descCol);

        Label countLabel = new Label();
        countLabel.textProperty().bind(Bindings.createStringBinding(
                () -> i18n.t("decoder.templateCount", filtered.size(), all.size()),
                filtered));

        Label originLabel = new Label(originText(i18n, settings));
        originLabel.setWrapText(true);
        originLabel.setStyle("-fx-opacity: 0.8;");

        Stage stage = new Stage();

        // Nach dem Speichern im Bearbeiten-Fenster die Liste neu einlesen -
        // sonst zeigt die Übersicht noch den alten Stand (oder eine gerade
        // über "Speichern als..." angelegte Vorlage gar nicht).
        Runnable refresh = () -> all.setAll(DecoderTemplate.loadAll());

        Button editButton = new Button(i18n.t("editor.configEditWindow"));
        editButton.setTooltip(new Tooltip(i18n.t("decoder.editHint")));
        editButton.setStyle(CategoryEditor.STYLE_CONFIG_EDIT);
        editButton.disableProperty().bind(
                table.getSelectionModel().selectedItemProperty().isNull());
        editButton.setOnAction(e ->
                openForEditing(stage, table.getSelectionModel().getSelectedItem(), refresh));

        // Doppelklick auf eine Zeile macht dasselbe - der gewohnte Weg,
        // "in etwas hineinzugehen".
        table.setRowFactory(view -> {
            TableRow<DecoderTemplate> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    openForEditing(stage, row.getItem(), refresh);
                }
            });
            return row;
        });

        HBox buttonRow = new HBox(8, editButton);
        buttonRow.setAlignment(Pos.CENTER_LEFT);

        VBox footer = new VBox(6, buttonRow, countLabel, originLabel);
        footer.setPadding(new Insets(6, 10, 10, 10));

        BorderPane root = new BorderPane();
        root.setTop(searchRow);
        root.setCenter(table);
        root.setBottom(footer);

        stage.initOwner(owner);
        // Bewusst KEIN modales Fenster: so kann die Übersicht offen bleiben,
        // während im Hauptfenster weitergearbeitet wird.
        stage.initModality(Modality.NONE);
        stage.setTitle(i18n.t("menu.decoderTemplates"));
        stage.getIcons().addAll(loadAppIcons());
        Scene scene = new Scene(root);
        ThemeManager.apply(scene, settings.getTheme());
        stage.setScene(scene);
        stage.setMinWidth(480);
        stage.setMinHeight(320);
        WindowState.apply(stage, "decoderTemplates", 720, 480);
        stage.show();
        searchField.requestFocus();
    }

    /** Öffnet eine Vorlage im Bearbeiten-Fenster (siehe {@link DecoderCaptureWindow}). */
    private static void openForEditing(Stage owner, DecoderTemplate template, Runnable refresh) {
        if (template != null) {
            DecoderCaptureWindow.showForTemplate(owner, template, refresh);
        }
    }

    /** Sucht in Bezeichnung, Beschreibung und Dateiname (Groß-/Kleinschreibung egal). */
    private static boolean matches(DecoderTemplate template, String needle) {
        if (needle.isEmpty()) {
            return true;
        }
        return template.getName().toLowerCase().contains(needle)
                || (template.getDescription() != null && template.getDescription().toLowerCase().contains(needle))
                || template.getFile().getName().toLowerCase().contains(needle);
    }

    /** "Vorlagen-Paket: Version X, installiert am Y" bzw. Hinweis, dass noch nichts installiert ist. */
    private static String originText(I18n i18n, AppSettings settings) {
        String installed = settings.getDecoderPackInstalled();
        if (installed == null || installed.isBlank()) {
            return i18n.t("decoder.packNotInstalled");
        }
        String version = settings.getDecoderPackVersion();
        if (version == null || version.isBlank()) {
            return i18n.t("decoder.packInstalledNoVersion", installed);
        }
        return i18n.t("decoder.packInstalled", version, installed);
    }

    private static Image[] loadAppIcons() {
        try (InputStream in = DecoderTemplateBrowser.class.getResourceAsStream("app-icon.png")) {
            return in == null ? new Image[0] : new Image[]{new Image(in)};
        } catch (Exception ex) {
            return new Image[0];
        }
    }
}
