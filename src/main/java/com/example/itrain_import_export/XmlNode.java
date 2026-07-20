package com.example.itrain_import_export;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generisches, reihenfolge-erhaltendes XML-Element.
 * <p>
 * Anstatt für jede der 15 control-items-Kategorien (functions, interfaces,
 * feedbacks, accessories, memory, boosters, blocks, train-types, stations,
 * train-routes, locomotives, wagons, trains, actions, measurements) eine
 * eigene Java-Klasse mit fest verdrahteten Feldern zu pflegen, wird die
 * komplette TCD-Datei in
 * diesen generischen Baum aus XmlNode-Objekten geladen. Das garantiert, dass
 * beim Speichern nichts verloren geht – auch unbekannte oder künftig neue
 * Unter-Elemente/Attribute bleiben automatisch erhalten, weil sie einfach
 * mitkopiert werden, ohne dass der Code sie kennen muss.
 */
public class XmlNode {

    private String tagName;
    private final LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
    private final ObservableList<XmlNode> children = FXCollections.observableArrayList();
    private String textContent;

    /**
     * Rein UI-seitiges Auswahl-Flag für die Checkbox-Spalte in der
     * Einträge-Tabelle (z.B. für Export). Wird nicht in die XML-Datei
     * geschrieben.
     */
    private final BooleanProperty selected = new SimpleBooleanProperty(false);

    public XmlNode(String tagName) {
        this.tagName = tagName;
    }

    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }

    public LinkedHashMap<String, String> getAttributes() {
        return attributes;
    }

    public String getAttribute(String name) {
        return attributes.get(name);
    }

    public void setAttribute(String name, String value) {
        attributes.put(name, value);
    }

    public void removeAttribute(String name) {
        attributes.remove(name);
    }

    /** Ersetzt die komplette Attribut-Liste, unter Beibehaltung der übergebenen Reihenfolge. */
    public void replaceAttributes(Map<String, String> newAttributes) {
        attributes.clear();
        attributes.putAll(newAttributes);
    }

    public ObservableList<XmlNode> getChildren() {
        return children;
    }

    public String getTextContent() {
        return textContent;
    }

    public void setTextContent(String textContent) {
        this.textContent = textContent;
    }

    /** Erstes direktes Kind-Element mit dem angegebenen Tag-Namen, oder null. */
    public XmlNode findChild(String tag) {
        for (XmlNode child : children) {
            if (child.tagName.equals(tag)) {
                return child;
            }
        }
        return null;
    }

    public BooleanProperty selectedProperty() {
        return selected;
    }

    public boolean isSelected() {
        return selected.get();
    }

    public void setSelected(boolean value) {
        selected.set(value);
    }

    public String getName() {
        String name = attributes.get("name");
        return name != null ? name : "";
    }

    /** Tiefe Kopie – wird beim "Eintrag aus Vorlage duplizieren" verwendet. */
    public XmlNode deepCopy() {
        XmlNode copy = new XmlNode(tagName);
        copy.attributes.putAll(this.attributes);
        copy.textContent = this.textContent;
        for (XmlNode child : children) {
            copy.children.add(child.deepCopy());
        }
        return copy;
    }

    /** Kurze, menschenlesbare Zusammenfassung für Baum-/Tabellenanzeige. */
    public String toSummaryString() {
        StringBuilder sb = new StringBuilder(tagName);
        String name = attributes.get("name");
        if (name != null) {
            sb.append(" \"").append(name).append('"');
        }
        for (Map.Entry<String, String> e : attributes.entrySet()) {
            if (e.getKey().equals("name")) {
                continue;
            }
            sb.append(' ').append(e.getKey()).append('=').append(e.getValue());
        }
        if (textContent != null && !textContent.isBlank()) {
            sb.append(" : ").append(textContent);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return toSummaryString();
    }
}
