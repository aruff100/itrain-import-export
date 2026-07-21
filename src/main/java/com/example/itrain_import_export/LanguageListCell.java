package com.example.itrain_import_export;

import javafx.scene.control.ListCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.InputStream;

/**
 * Zeigt Flagge + nativen Sprachnamen (z.B. "Deutsch") in einer
 * Sprachauswahl-ComboBox/Dropdown-Liste - gemeinsam genutzt von
 * {@link SettingsDialog} (Reiter "Sprache") und {@link FirstRunDialog}
 * (Sprachauswahl bei der Ersteinrichtung), damit beide exakt gleich
 * aussehen und es nur eine Stelle zum Pflegen gibt.
 */
final class LanguageListCell extends ListCell<String> {
    @Override
    protected void updateItem(String code, boolean empty) {
        super.updateItem(code, empty);
        if (empty || code == null) {
            setText(null);
            setGraphic(null);
            return;
        }
        I18n i18n = I18n.getInstance();
        setText(i18n.getNativeName(code));

        String flagPath = i18n.getFlagResourcePath(code);
        try (InputStream flagStream = I18n.class.getResourceAsStream(flagPath)) {
            if (flagStream != null) {
                ImageView flagView = new ImageView(new Image(flagStream));
                flagView.setFitWidth(24);
                flagView.setFitHeight(16);
                flagView.setPreserveRatio(false);
                setGraphic(flagView);
            } else {
                setGraphic(null);
            }
        } catch (Exception ex) {
            setGraphic(null);
        }
    }
}
