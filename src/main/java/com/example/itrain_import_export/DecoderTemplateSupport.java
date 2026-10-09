package com.example.itrain_import_export;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gemeinsame Bausteine rund um Decoder-Vorlagen fuer das Bearbeitungsfenster
 * eines Fahrzeugs ({@link VehicleFieldsDialog}) und die Tabellenspalten
 * "Decoder"/"Decoder-Vorlage" ({@link CategoryEditor}):
 * <ul>
 * <li>{@link #recognise}: welche Vorlage steckt (vermutlich) in einem
 * Fahrzeug? iTrain speichert den Vorlagennamen NICHT - erkannt wird ueber die
 * CV-Nummern der {@code <configuration>}: gleiche Menge an CV-Nummern wie eine
 * Vorlage; bei mehreren Kandidaten gewinnt die mit den meisten gleichen
 * Werten.</li>
 * <li>{@link #apply}: eine Vorlage einem Fahrzeug zuweisen - Decoder anlegen,
 * falls keiner da ist, Konfiguration an der von iTrain erwarteten Stelle
 * einsetzen bzw. ersetzen, auf das Protokoll kuerzen (gleiche Regeln wie
 * "Decoder-Informationen importieren", siehe {@link DecoderProtocol}).</li>
 * </ul>
 */
final class DecoderTemplateSupport {

    private DecoderTemplateSupport() {
    }

    /** Eine Vorlage, vorbereitet fuer die Erkennung. */
    record Known(DecoderTemplate template, Set<String> numbers, Map<String, String> values) {
    }

    private static List<Known> cache;
    private static String cacheDir;

    /** Alle Vorlagen des eingestellten Ordners (zwischengespeichert, solange der Ordner gleich bleibt). */
    static synchronized List<Known> known() {
        String dir = AppSettings.getInstance().getDecoderDirectory();
        if (cache != null && java.util.Objects.equals(dir, cacheDir)) {
            return cache;
        }
        List<Known> list = new ArrayList<>();
        for (DecoderTemplate template : DecoderTemplate.loadAll()) {
            try {
                XmlNode configuration = template.toConfigurationNode();
                list.add(new Known(template, numbers(configuration), values(configuration)));
            } catch (Exception ignored) {
                // unlesbare Vorlage - fuer die Erkennung uebergehen
            }
        }
        cache = list;
        cacheDir = dir;
        return list;
    }

    /** Zwischenspeicher verwerfen (z.B. nach dem Installieren neuer Vorlagen). */
    static synchronized void invalidate() {
        cache = null;
    }

    private static Set<String> numbers(XmlNode configuration) {
        Set<String> set = new HashSet<>();
        for (XmlNode parameter : configuration.getChildren()) {
            String nr = parameter.getAttribute("nr");
            if (nr != null) {
                set.add(nr.trim());
            }
        }
        return set;
    }

    private static Map<String, String> values(XmlNode configuration) {
        Map<String, String> map = new HashMap<>();
        for (XmlNode parameter : configuration.getChildren()) {
            String nr = parameter.getAttribute("nr");
            if (nr != null) {
                map.put(nr.trim(), String.valueOf(parameter.getAttribute("value")));
            }
        }
        return map;
    }

    /** Vermutlich zugewiesene Vorlage, oder null. */
    static DecoderTemplate recognise(XmlNode vehicle) {
        XmlNode configuration = vehicle == null ? null : vehicle.findChild(DecoderTemplate.CONFIGURATION_TAG);
        if (configuration == null || configuration.getChildren().isEmpty()) {
            return null;
        }
        Set<String> nums = numbers(configuration);
        Map<String, String> vals = values(configuration);
        Known best = null;
        int bestEqual = -1;
        for (Known known : known()) {
            if (!known.numbers().equals(nums)) {
                continue;
            }
            int equal = 0;
            for (Map.Entry<String, String> entry : known.values().entrySet()) {
                if (entry.getValue().equals(vals.get(entry.getKey()))) {
                    equal++;
                }
            }
            if (equal > bestEqual) {
                best = known;
                bestEqual = equal;
            }
        }
        return best == null ? null : best.template();
    }

    /** Text fuer die Spalte "Decoder-Vorlage": erkannte Vorlage, sonst Anzahl der CVs, sonst leer. */
    static String templateColumnText(XmlNode vehicle, I18n i18n) {
        DecoderTemplate template = recognise(vehicle);
        if (template != null) {
            return template.getName();
        }
        XmlNode configuration = vehicle.findChild(DecoderTemplate.CONFIGURATION_TAG);
        if (configuration != null && !configuration.getChildren().isEmpty()) {
            return i18n.t("vehicle.ownConfiguration", configuration.getChildren().size());
        }
        return "";
    }

    /** Text fuer die Spalte "Decoder": Protokoll, oder leer ohne Decoder. */
    static String decoderColumnText(XmlNode vehicle) {
        XmlNode decoder = vehicle.findChild(DecoderProtocol.DECODER_TAG);
        if (decoder == null) {
            return "";
        }
        // Bevorzugt derselbe Name wie in iTrain (z.B. "DCC 126 Extended")
        String label = VehicleFieldsDialog.decoderTypeLabel(decoder);
        return label != null ? label : DecoderProtocol.displayName(DecoderProtocol.read(vehicle));
    }

    // ------------------------------------------------------------------
    // Zuweisen
    // ------------------------------------------------------------------

    /** Ergebnis von {@link #apply}. */
    enum Result { APPLIED, PROTOCOL_BLOCKED, FAILED }

    /**
     * Weist {@code template} dem Fahrzeug zu. Fehlt ein {@code <decoder>},
     * wird einer mit {@code protocolIfMissing} angelegt. Die Konfiguration
     * wird ersetzt (an derselben Stelle) bzw. hinter {@code <decoder>}
     * eingefuegt und auf das Protokoll gekuerzt.
     */
    static Result apply(XmlNode vehicle, DecoderTemplate template, String protocolIfMissing) {
        if (vehicle.findChild(DecoderProtocol.DECODER_TAG) == null) {
            insertDecoder(vehicle, protocolIfMissing == null || protocolIfMissing.isBlank() ? "dcc" : protocolIfMissing);
        }
        String protocol = DecoderProtocol.read(vehicle);
        if (DecoderProtocol.ruleFor(protocol) == DecoderProtocol.Rule.NONE) {
            return Result.PROTOCOL_BLOCKED;
        }
        XmlNode configuration;
        try {
            configuration = template.toConfigurationNode();
        } catch (Exception ex) {
            return Result.FAILED;
        }
        int max = DecoderProtocol.maxParameters(protocol);
        if (configuration.getChildren().size() > max) {
            configuration.getChildren().subList(max, configuration.getChildren().size()).clear();
        }
        if (configuration.getAttribute("count") != null) {
            configuration.setAttribute("count", String.valueOf(configuration.getChildren().size()));
        }
        XmlNode existing = vehicle.findChild(DecoderTemplate.CONFIGURATION_TAG);
        if (existing != null) {
            vehicle.getChildren().set(vehicle.getChildren().indexOf(existing), configuration);
        } else {
            insertConfiguration(vehicle, configuration);
        }
        return Result.APPLIED;
    }

    /** Wie in {@link CategoryEditor}: Kinder, die in iTrain VOR {@code <decoder>} stehen. */
    private static final List<String> TAGS_BEFORE_DECODER = List.of("description", "interface", "id");

    /** Wie in {@link CategoryEditor}: Kinder, die NACH {@code <configuration>} stehen. */
    private static final List<String> TAGS_AFTER_CONFIGURATION = List.of(
            "length", "options", "feedback", "delay", "acceleration", "deceleration",
            "speed-limit", "speed-control", "image", "functions", "fuel", "maintenance",
            "total", "comment");

    static void insertDecoder(XmlNode vehicle, String protocol) {
        XmlNode decoder = new XmlNode(DecoderProtocol.DECODER_TAG);
        decoder.setAttribute(DecoderProtocol.PROTOCOL_ATTRIBUTE, protocol);
        List<XmlNode> children = vehicle.getChildren();
        for (int i = children.size() - 1; i >= 0; i--) {
            if (TAGS_BEFORE_DECODER.contains(children.get(i).getTagName())) {
                children.add(i + 1, decoder);
                return;
            }
        }
        children.add(0, decoder);
    }

    static void insertConfiguration(XmlNode vehicle, XmlNode configuration) {
        List<XmlNode> children = vehicle.getChildren();
        for (int i = children.size() - 1; i >= 0; i--) {
            if (DecoderProtocol.DECODER_TAG.equals(children.get(i).getTagName())) {
                children.add(i + 1, configuration);
                return;
            }
        }
        for (int i = 0; i < children.size(); i++) {
            if (TAGS_AFTER_CONFIGURATION.contains(children.get(i).getTagName())) {
                children.add(i, configuration);
                return;
            }
        }
        children.add(configuration);
    }
}
