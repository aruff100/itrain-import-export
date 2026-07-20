package com.example.itrain_import_export;

import javafx.scene.Scene;

/**
 * Wendet das gewählte Farbschema (hell/dunkel) auf eine Scene an. Hell ist
 * einfach das JavaFX-Standardaussehen (kein zusätzliches Stylesheet); dunkel
 * lädt {@code dark-theme.css} aus den Ressourcen oben drauf.
 */
public final class ThemeManager {

    private static final String DARK_STYLESHEET = "dark-theme.css";

    private ThemeManager() {
    }

    public static void apply(Scene scene, String theme) {
        if (scene == null) {
            return;
        }
        scene.getStylesheets().clear();
        if (AppSettings.THEME_DARK.equals(theme)) {
            String url = HelloApplication.class.getResource(DARK_STYLESHEET) != null
                    ? HelloApplication.class.getResource(DARK_STYLESHEET).toExternalForm()
                    : null;
            if (url != null) {
                scene.getStylesheets().add(url);
            }
        }
    }
}
