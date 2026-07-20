module com.example.itrain_import_export {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.xml;
    requires java.prefs;

    opens com.example.itrain_import_export to javafx.fxml;
    exports com.example.itrain_import_export;
}