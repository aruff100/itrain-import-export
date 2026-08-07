package com.example.itrain_import_export;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.util.Optional;

/**
 * Auswahldialog für "Decoder-Informationen importieren": zeigt die
 * installierten Vorlagen (siehe {@link DecoderTemplate#loadAll()}) und lässt
 * alternativ über "Datei wählen..." eine beliebige Vorlagen-CSV von der
 * Festplatte laden - z.B. eine, die man selbst exportiert oder von jemand
 * anderem bekommen hat, ohne sie erst in den Vorlagen-Ordner zu legen.
 * <p>
 * Bedienung: Das Suchfeld filtert die Liste laufend. Zusätzlich springt ein
 * Tastendruck in der Tabelle zur ersten Vorlage mit diesem Anfangsbuchstaben
 * (z.B. "z" → erste Vorlage, die mit Z beginnt) - praktisch bei langen
 * Listen. Die Vorlagen sind alphabetisch sortiert; über die Spaltenköpfe
 * lässt sich beliebig umsortieren.
 */
public final class DecoderTemplateChooser {

    private DecoderTemplateChooser() {
    }

    /**
     * Zeigt den Dialog und gibt die gewählte Vorlage zurück, oder
     * {@link Optional#empty()} bei Abbruch.
     */
    public static Optional<DecoderTemplate> choose(Window owner) {
        I18n i18n = I18n.getInstance();

        ObservableList<DecoderTemplate> all = FXCollections.observableArrayList(DecoderTemplate.loadAll());
        FilteredList<DecoderTemplate> filtered = new FilteredList<>(all, t -> true);

        TextField searchField = new TextField();
        searchField.setPromptText(i18n.t("decoder.searchPrompt"));
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchField.textProperty().addListener((obs, oldText, newText) -> {
            String needle = newText == null ? "" : newText.trim().toLowerCase();
            filtered.setPredicate(t -> needle.isEmpty()
                    || t.getName().toLowerCase().contains(needle)
                    || (t.getDescription() != null && t.getDescription().toLowerCase().contains(needle)));
        });

        TableView<DecoderTemplate> table = new TableView<>(filtered);
        table.setPlaceholder(new Label(i18n.t("decoder.noTemplates")));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.SINGLE);

        TableColumn<DecoderTemplate, String> nameCol = new TableColumn<>(i18n.t("editor.columnName"));
        nameCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getName()));
        nameCol.setPrefWidth(230);
        TableColumn<DecoderTemplate, Number> countCol = new TableColumn<>(i18n.t("decoder.columnParameters"));
        countCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getParameterCount()));
        countCol.setPrefWidth(90);
        TableColumn<DecoderTemplate, String> descCol = new TableColumn<>(i18n.t("editor.columnDescription"));
        descCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getDescription()));
        table.getColumns().add(nameCol);
        table.getColumns().add(countCol);
        table.getColumns().add(descCol);

        // Buchstabensprung: springt zur ersten Vorlage mit diesem
        // Anfangsbuchstaben. Bewusst nur bei einem einzelnen, druckbaren
        // Zeichen ohne Steuertaste, damit Pfeiltasten/Tab/Strg-C weiterhin
        // ihre normale Funktion behalten.
        table.addEventHandler(KeyEvent.KEY_TYPED, event -> {
            String typed = event.getCharacter();
            if (typed == null || typed.isEmpty() || event.isControlDown() || event.isAltDown()
                    || event.isMetaDown() || Character.isISOControl(typed.charAt(0))) {
                return;
            }
            String prefix = typed.toLowerCase();
            for (int i = 0; i < filtered.size(); i++) {
                if (filtered.get(i).getName().toLowerCase().startsWith(prefix)) {
                    table.getSelectionModel().clearAndSelect(i);
                    table.scrollTo(i);
                    event.consume();
                    return;
                }
            }
        });

        Button fileButton = new Button(i18n.t("decoder.chooseFileButton"));
        HBox searchRow = new HBox(8, new Label(i18n.t("decoder.searchLabel")), searchField, fileButton);
        searchRow.setPadding(new Insets(0, 0, 8, 0));

        BorderPane content = new BorderPane();
        content.setTop(searchRow);
        content.setCenter(table);
        content.setPadding(new Insets(12));
        content.setPrefSize(640, 420);

        Dialog<DecoderTemplate> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(i18n.t("editor.decoderImport"));
        dialog.setResizable(true);
        ButtonType okButton = new ButtonType(i18n.t("decoder.useTemplate"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType(i18n.t("editor.exportConfirmCancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(okButton, cancelButton);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().sceneProperty().addListener((obs, oldScene, newScene) ->
                ThemeManager.apply(newScene, AppSettings.getInstance().getTheme()));

        Button okNode = (Button) dialog.getDialogPane().lookupButton(okButton);
        okNode.setDisable(true);
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) ->
                okNode.setDisable(newSel == null));
        // Doppelklick auf eine Zeile übernimmt sie direkt.
        table.setRowFactory(tv -> {
            javafx.scene.control.TableRow<DecoderTemplate> row = new javafx.scene.control.TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    dialog.setResult(row.getItem());
                    dialog.close();
                }
            });
            return row;
        });

        // "Datei wählen...": lädt eine beliebige Vorlagen-CSV und schließt den
        // Dialog sofort mit dieser Vorlage als Ergebnis.
        fileButton.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(i18n.t("decoder.chooseFileTitle"));
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("CSV (*.csv)", "*.csv"),
                    new FileChooser.ExtensionFilter("*.*", "*.*"));
            String decoderDir = AppSettings.getInstance().getDecoderDirectory();
            if (decoderDir != null && new File(decoderDir).isDirectory()) {
                chooser.setInitialDirectory(new File(decoderDir));
            }
            File chosen = chooser.showOpenDialog(dialog.getDialogPane().getScene().getWindow());
            if (chosen == null) {
                return;
            }
            DecoderTemplate template = DecoderTemplate.read(chosen);
            if (template == null) {
                Alert invalid = new Alert(Alert.AlertType.ERROR, i18n.t("decoder.notADecoderFile"));
                invalid.initOwner(dialog.getDialogPane().getScene().getWindow());
                invalid.setHeaderText(null);
                invalid.showAndWait();
                return;
            }
            dialog.setResult(template);
            dialog.close();
        });

        dialog.setResultConverter(button -> button == okButton
                ? table.getSelectionModel().getSelectedItem()
                : null);

        return Optional.ofNullable(dialog.showAndWait().orElse(null));
    }
}
