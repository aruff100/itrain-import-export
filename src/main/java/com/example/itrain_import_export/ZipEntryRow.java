package com.example.itrain_import_export;

import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;

/**
 * Repräsentiert eine Zeile in der Tabelle: ein einzelner Eintrag aus einer ZIP-Datei.
 */
public class ZipEntryRow {

    private final SimpleStringProperty name;
    private final SimpleLongProperty size;
    private final SimpleLongProperty compressedSize;
    private final SimpleStringProperty lastModified;

    public ZipEntryRow(String name, long size, long compressedSize, String lastModified) {
        this.name = new SimpleStringProperty(name);
        this.size = new SimpleLongProperty(size);
        this.compressedSize = new SimpleLongProperty(compressedSize);
        this.lastModified = new SimpleStringProperty(lastModified);
    }

    public String getName() {
        return name.get();
    }

    public SimpleStringProperty nameProperty() {
        return name;
    }

    public long getSize() {
        return size.get();
    }

    public SimpleLongProperty sizeProperty() {
        return size;
    }

    public long getCompressedSize() {
        return compressedSize.get();
    }

    public SimpleLongProperty compressedSizeProperty() {
        return compressedSize;
    }

    public String getLastModified() {
        return lastModified.get();
    }

    public SimpleStringProperty lastModifiedProperty() {
        return lastModified;
    }
}
