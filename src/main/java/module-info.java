module com.example.itrain_import_export {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.xml;
    requires java.prefs;
    requires java.net.http;
    // java.desktop: Desktop.browse (Download-Links im Browser oeffnen) und
    // Desktop.mail (Speichern & Senden), siehe UpdateDialog und MailSender.
    requires java.desktop;

    opens com.example.itrain_import_export to javafx.fxml;
    exports com.example.itrain_import_export;
}