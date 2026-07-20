package com.example.itrain_import_export;

import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** Zeigt den "Über"-Dialog: Programmname, Autor und aktuelle Build-Nummer. */
public final class AboutDialog {

    private static final String APP_NAME = "iTrain Import/Export";
    private static final String AUTHOR = "Andre Ruff";

    private AboutDialog() {
    }

    public static void show(Stage owner) {
        I18n i18n = I18n.getInstance();

        Label nameLabel = new Label(APP_NAME);
        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        Label authorLabel = new Label(AUTHOR);
        Label versionLabel = new Label(i18n.t("about.versionLabel", AppInfo.getBuildNumber()));

        VBox content = new VBox(6, nameLabel, authorLabel, versionLabel);
        content.setPadding(new Insets(15));

        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
        dialog.initOwner(owner);
        dialog.setTitle(i18n.t("menu.aboutItem"));
        dialog.setHeaderText(null);
        dialog.getDialogPane().setContent(content);
        dialog.getButtonTypes().setAll(ButtonType.CLOSE);
        // Die Scene existiert erst, sobald der Dialog tatsächlich angezeigt
        // wird - deshalb das Farbschema erst dann anwenden (siehe
        // SettingsDialog.applyThemeOnceShown für dasselbe Muster).
        dialog.getDialogPane().sceneProperty().addListener((obs, oldScene, newScene) ->
                ThemeManager.apply(newScene, AppSettings.getInstance().getTheme()));
        dialog.showAndWait();
    }
}
