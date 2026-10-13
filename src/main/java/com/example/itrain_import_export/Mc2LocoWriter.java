package com.example.itrain_import_export;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Schreibt Lokomotiven aus der iTrain-Datei in die Tams mc2 (seit
 * 13.10.2026, "Lokomotiven zur mc2 uebertragen") - das Gegenstueck zu
 * {@link Mc2LocoReader}.
 * <p>
 * Geschrieben wird LIVE ueber die cgi-Befehle der Weboberflaeche
 * ({@link Mc2Http#writeLoco}) - wirksam sofort, ohne Neustart; die mc2
 * schreibt ihre loco.ini dabei selbst (Handtest 13.10.2026).
 * <p>
 * Ablauf ({@link #transfer}):
 * <ol>
 * <li>Sicherung: loco.ini per HTTP lesen und auf dem PC ablegen
 * ({@link CentralUndo#writeBackup}, hoechstens 10 je mc2);</li>
 * <li>je Lok Format, Name und Funktionssymbole per cgi setzen;</li>
 * <li>jede Lok per {@code cmd=info} zuruecklesen und Name/Format vergleichen.</li>
 * </ol>
 * Zuruecknehmen: {@link #undo} (Bearbeiten -&gt; Rueckgaengig im Systeme-Fenster).
 */
public final class Mc2LocoWriter {

    static final String LOCO_PATH = "/config/loco.ini";

    private Mc2LocoWriter() {
    }

    /** Was fuer eine Lok in die Zentrale geschrieben wird (mc2: fmt wie "DCC/126"; ECoS: Protokoll wie "DCC128"). */
    public record Profile(int address, String name, String fmt, Map<Integer, Integer> icons) {
    }

    /**
     * mc2-Format aus dem iTrain-Decoder, oder null, wenn es (noch) nicht
     * uebertragen wird. Belegt: DCC/14, DCC/28, DCC/126 (Formatnummern 6, 7,
     * 8 aus scripts/loks.js der mc2); Motorola, mfx, Selectrix nicht.
     */
    static String fmtFor(XmlNode decoder) {
        if (decoder == null || !"dcc".equalsIgnoreCase(nz(decoder.getAttribute("protocol")))) {
            return null;
        }
        switch (nz(decoder.getAttribute("steps")).trim()) {
            case "14":
                return "DCC/14";
            case "28":
                return "DCC/28";
            case "126":
            case "128":
                return "DCC/126";
            default:
                return null;
        }
    }

    /** Zeilenumbrueche und fuehrende/abschliessende Leerzeichen raus - der Rest bleibt, wie er ist. */
    static String sanitize(String name) {
        return name == null ? "" : name.replace('\r', ' ').replace('\n', ' ').trim();
    }

    /**
     * Sichert loco.ini auf dem PC und schreibt die Profile (blockierend,
     * Netzwerk - nur aus einem Hintergrundfaden). {@code existing} sind die
     * vorhandenen Loks der mc2 (Adresse -&gt; Daten), gelesen beim Oeffnen des
     * Fensters. Einzelne Fehler stehen in {@link CentralUndo#failed}.
     */
    static CentralUndo transfer(String host, List<Profile> profiles, Map<Integer, Mc2Http.LocoInfo> existing)
            throws IOException {
        String current = Mc2Http.get(host, LOCO_PATH);
        CentralUndo undo = new CentralUndo(CentralUndo.Kind.MC2, host,
                CentralUndo.writeBackup(CentralUndo.Kind.MC2, host, current));
        Map<Integer, Mc2LocoReader.LocoEntry> before = new java.util.HashMap<>();
        for (Mc2LocoReader.LocoEntry entry : Mc2LocoReader.parse(current)) {
            before.put(entry.address(), entry);
        }
        for (Profile profile : profiles) {
            try {
                Mc2Http.writeLoco(host, profile, existing.get(profile.address()));
                if (before.containsKey(profile.address()) || existing.containsKey(profile.address())) {
                    undo.overwritten.add(profile.address());
                } else {
                    undo.created.add(new CentralUndo.Entry(profile.address(), profile.address(),
                            profile.name(), profile.fmt()));
                }
                Mc2Http.LocoInfo check = Mc2Http.locoInfo(host, profile.address());
                if (check == null || !profile.fmt().equals(check.fmt()) || !profile.name().equals(check.name())) {
                    undo.failed.add(String.valueOf(profile.address()));
                }
            } catch (IOException ex) {
                undo.failed.add(profile.address() + " (" + ex.getMessage() + ")");
            }
        }
        return undo;
    }

    /**
     * Nimmt eine Uebertragung zurueck: neu angelegte Loks loeschen,
     * ueberschriebene aus der Sicherung wiederherstellen (eine Adresse, die
     * in der Sicherung fehlt, war vorher frei und wird geloescht).
     * Liefert die Adressen, bei denen es nicht geklappt hat.
     */
    static List<String> undo(CentralUndo undo) throws IOException {
        List<String> failed = new java.util.ArrayList<>();
        Map<Integer, Mc2LocoReader.LocoEntry> backup = new java.util.HashMap<>();
        for (Mc2LocoReader.LocoEntry entry : Mc2LocoReader.parse(undo.readBackup())) {
            backup.put(entry.address(), entry);
        }
        for (Integer address : undo.overwritten) {
            try {
                Mc2LocoReader.LocoEntry entry = backup.get(address);
                if (entry == null) {
                    Mc2Http.removeLoco(undo.host, address);
                    continue;
                }
                Profile profile = new Profile(address, entry.name() == null ? "" : entry.name(),
                        entry.fmt(), entry.icons());
                Mc2Http.writeLoco(undo.host, profile, Mc2Http.locoInfo(undo.host, address));
            } catch (IOException ex) {
                failed.add(address + " (" + ex.getMessage() + ")");
            }
        }
        for (CentralUndo.Entry entry : undo.created) {
            try {
                Mc2Http.removeLoco(undo.host, entry.address());
            } catch (IOException ex) {
                failed.add(entry.address() + " (" + ex.getMessage() + ")");
            }
        }
        return failed;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
