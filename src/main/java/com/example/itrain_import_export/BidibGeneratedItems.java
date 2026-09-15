package com.example.itrain_import_export;

import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sammelt die im Knoten-Fenster per Rechtsklick erzeugten iTrain-Einträge,
 * getrennt nach Kategorie, bis sie beim Speichern in CSV-Dateien geschrieben
 * werden (eine Datei je Kategorie, siehe {@link BidibNodeTreeWindow}).
 * <p>
 * Die Kategorienamen sind bewusst genau die Tag-Namen aus der iTrain-Datei
 * ({@code feedbacks}, {@code accessories}, {@code boosters},
 * {@code interfaces}) - die CSV trägt sie in ihrer ersten Spalte, und der
 * Import in {@link CategoryEditor} prüft anhand dieser Spalte, ob eine Datei
 * zum gewählten Reiter passt.
 * <p>
 * Doppelte Einträge werden abgewiesen: Ein Anschluss lässt sich nicht zweimal
 * erzeugen, sonst stünden nach dem Import zwei iTrain-Einträge mit derselben
 * Kennung in der Datei.
 */
public final class BidibGeneratedItems {

    public static final String CATEGORY_FEEDBACKS = "feedbacks";
    public static final String CATEGORY_ACCESSORIES = "accessories";
    public static final String CATEGORY_BOOSTERS = "boosters";
    public static final String CATEGORY_INTERFACES = "interfaces";

    /**
     * Reihenfolge, in der die Kategorien in der Statuszeile erscheinen -
     * dieselbe wie in der iTrain-Datei, damit es vertraut wirkt.
     */
    public static final List<String> CATEGORY_ORDER =
            List.of(CATEGORY_INTERFACES, CATEGORY_FEEDBACKS, CATEGORY_ACCESSORIES, CATEGORY_BOOSTERS);

    private final Map<String, List<XmlNode>> itemsByCategory = new LinkedHashMap<>();

    /**
     * Bereits vergebene Kennungen - Schutz gegen doppeltes Erzeugen desselben
     * Anschlusses.
     */
    private final Map<String, String> usedIds = new LinkedHashMap<>();

    /**
     * Gesamtzahl, damit sich Knöpfe (z.B. "Speichern") einfach daran binden
     * lassen und ausgegraut bleiben, solange nichts erzeugt wurde.
     */
    private final ReadOnlyIntegerWrapper totalCount = new ReadOnlyIntegerWrapper(0);

    public ReadOnlyIntegerProperty totalCountProperty() {
        return totalCount.getReadOnlyProperty();
    }

    /**
     * Nimmt einen erzeugten Eintrag auf.
     *
     * @param id eindeutige Kennung des Eintrags (die iTrain-ID), oder
     *           {@code null} wenn es keine gibt
     * @return {@code true}, wenn der Eintrag neu war; {@code false}, wenn
     *         dieselbe Kennung schon vorhanden ist
     */
    public boolean add(String category, XmlNode item, String id) {
        if (id != null && usedIds.containsKey(id)) {
            return false;
        }
        itemsByCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(item);
        if (id != null) {
            usedIds.put(id, category);
        }
        totalCount.set(totalCount.get() + 1);
        return true;
    }

    /** Ersetzt einen ggf. schon vorhandenen Eintrag dieser Kategorie - für die Schnittstelle,
     *  von der es sinnvollerweise nur eine je Verbindung gibt. */
    public void replaceSingle(String category, XmlNode item) {
        List<XmlNode> existing = itemsByCategory.get(category);
        int previous = existing != null ? existing.size() : 0;
        List<XmlNode> single = new ArrayList<>();
        single.add(item);
        itemsByCategory.put(category, single);
        totalCount.set(totalCount.get() - previous + 1);
    }

    public List<XmlNode> get(String category) {
        return itemsByCategory.getOrDefault(category, List.of());
    }

    /** Alle erzeugten Einträge über alle Kategorien - für das nachträgliche Umbenennen der Schnittstelle. */
    public List<XmlNode> allItems() {
        List<XmlNode> all = new ArrayList<>();
        for (List<XmlNode> list : itemsByCategory.values()) {
            all.addAll(list);
        }
        return all;
    }

    public int count(String category) {
        return get(category).size();
    }

    /** Kategorien, in denen tatsächlich etwas erzeugt wurde - in fester Reihenfolge. */
    public List<String> nonEmptyCategories() {
        List<String> result = new ArrayList<>();
        for (String category : CATEGORY_ORDER) {
            if (count(category) > 0) {
                result.add(category);
            }
        }
        return result;
    }

    public boolean isEmpty() {
        return totalCount.get() == 0;
    }

    /** Ist dieser Anschluss schon erzeugt worden? */
    public boolean containsId(String id) {
        return id != null && usedIds.containsKey(id);
    }

    public void clear() {
        itemsByCategory.clear();
        usedIds.clear();
        totalCount.set(0);
    }
}
