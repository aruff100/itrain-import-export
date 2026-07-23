package com.example.itrain_import_export;

import javafx.scene.control.ListCell;

/**
 * Zeigt "Hell"/"Dunkel" (übersetzt) statt der internen Theme-Codes
 * "light"/"dark" in einer Farbschema-Auswahl-ComboBox - gemeinsam genutzt
 * von {@link SettingsDialog} (Reiter "Ansicht") und {@link FirstRunDialog}
 * (Farbschema-Auswahl bei der Ersteinrichtung), nach demselben Muster wie
 * {@link LanguageListCell}.
 */
final class ThemeListCell extends ListCell<String> {
    @Override
    protected void updateItem(String theme, boolean empty) {
        super.updateItem(theme, empty);
        if (empty || theme == null) {
            setText(null);
            return;
        }
        I18n i18n = I18n.getInstance();
        setText(AppSettings.THEME_DARK.equals(theme)
                ? i18n.t("settings.themeDark")
                : i18n.t("settings.themeLight"));
    }
}
