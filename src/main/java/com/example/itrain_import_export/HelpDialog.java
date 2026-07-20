package com.example.itrain_import_export;

import javafx.geometry.Insets;
import javafx.scene.control.Dialog;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Zeigt die eingebaute Hilfe: kurze Beschreibung des Programmzwecks, gefolgt
 * von Überschriften/Absätzen zu den einzelnen Menüpunkten sowie zur
 * Kategorie-Ansicht (Import/Export inkl. Referenzen, Anlegen/Löschen). Der
 * gesamte Text kommt aus {@link I18n} (alle 6 Sprachen), damit er automatisch
 * mit der Oberflächensprache wechselt.
 */
public final class HelpDialog {

    private HelpDialog() {
    }

    public static void show(Stage owner) {
        I18n i18n = I18n.getInstance();

        VBox content = new VBox(4);
        content.setPadding(new Insets(15));

        addParagraph(content, null, i18n.t("help.introText"));
        addParagraph(content, i18n.t("help.fileMenuTitle"), i18n.t("help.fileMenuText"));
        addParagraph(content, i18n.t("help.editMenuTitle"), i18n.t("help.editMenuText"));
        addParagraph(content, i18n.t("help.settingsMenuTitle"), i18n.t("help.settingsMenuText"));
        addParagraph(content, i18n.t("help.helpMenuTitle"), i18n.t("help.helpMenuText"));
        addParagraph(content, i18n.t("help.categoryViewTitle"), i18n.t("help.categoryViewText"));
        addParagraph(content, i18n.t("help.referenceRenameTitle"), i18n.t("help.referenceRenameText"));

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefSize(560, 480);

        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(i18n.t("menu.helpItem"));
        dialog.getDialogPane().setContent(scrollPane);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        // Die Scene existiert erst, sobald der Dialog tatsächlich angezeigt
        // wird - deshalb das Farbschema erst dann anwenden (siehe
        // SettingsDialog.applyThemeOnceShown für dasselbe Muster).
        dialog.getDialogPane().sceneProperty().addListener((obs, oldScene, newScene) ->
                ThemeManager.apply(newScene, AppSettings.getInstance().getTheme()));
        dialog.showAndWait();
    }

    /** Fügt einen Abschnitt an - mit fett gedruckter Überschrift (falls angegeben) und Fließtext. */
    private static void addParagraph(VBox container, String title, String text) {
        if (title != null) {
            Label titleLabel = new Label(title);
            titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 10 0 2 0;");
            container.getChildren().add(titleLabel);
        }
        Label textLabel = new Label(text);
        textLabel.setWrapText(true);
        container.getChildren().add(textLabel);
    }
}
