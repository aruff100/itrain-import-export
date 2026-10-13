package com.example.itrain_import_export;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Schreibt Lokomotiven aus der iTrain-Datei in die ESU ECoS (seit
 * 13.10.2026, "Lokomotiven zur ECoS uebertragen") ueber das Netzprotokoll
 * (Port 15471). An der ECoS 192.168.0.99 (ECoS2, Firmware 4.3.3) geprueft:
 * {@code create(10, addr[..], name["..."], protocol[DCC128], append)} legt
 * eine Lok an und meldet {@code 10 id[1022]}; {@code set(id, name[..])}
 * und {@code delete(id)} gehen ohne Kontrolle der Lok; Namen in UTF-8.
 * <p>
 * Funktionssymbole werden nicht gesetzt - welche Symbolnummer der ECoS zu
 * welchem Namen gehoert, ist (noch) nicht bekannt.
 */
final class EcosLocoWriter {

    private EcosLocoWriter() {
    }

    /** Eine Lok der ECoS (Objekt 1000-1999). */
    record EcosLoco(int id, int address, String name, String protocol) {
        String backupLine() {
            return id + " name[\"" + quote(name) + "\"] addr[" + address + "] protocol[" + protocol + "]";
        }
    }

    /** Alle Loks der ECoS, nach Objektnummer. Blockierend (Netzwerk). */
    static Map<Integer, EcosLoco> readLocos(EcosClient client) throws IOException {
        EcosClient.Reply reply = client.send("queryObjects(10, addr, name, protocol)");
        if (!reply.isOk()) {
            throw new IOException("queryObjects(10): " + reply.message());
        }
        Map<Integer, EcosLoco> result = new LinkedHashMap<>();
        for (EcosClient.Line line : reply.lines()) {
            if (line.objectId() >= 1000 && line.objectId() < 2000) {
                result.put(line.objectId(), new EcosLoco(line.objectId(), line.getInt("addr", 0),
                        nz(line.get("name")), nz(line.get("protocol"))));
            }
        }
        return result;
    }

    /**
     * ECoS-Protokoll aus dem iTrain-Decoder, oder null, wenn nicht
     * uebertragbar: DCC mit 14/28/128 Fahrstufen, Selectrix (SX32).
     */
    static String protocolFor(XmlNode decoder) {
        if (decoder == null) {
            return null;
        }
        String protocol = nz(decoder.getAttribute("protocol")).toLowerCase(java.util.Locale.ROOT);
        if ("selectrix".equals(protocol)) {
            return "SX32";
        }
        if (!"dcc".equals(protocol)) {
            return null;
        }
        switch (nz(decoder.getAttribute("steps")).trim()) {
            case "14":
                return "DCC14";
            case "28":
                return "DCC28";
            case "126":
            case "128":
                return "DCC128";
            default:
                return null;
        }
    }

    /**
     * Sichert die Lokliste auf dem PC und schreibt die Profile. Eine Adresse,
     * die schon eine Lok der ECoS traegt, ueberschreibt die erste solche Lok
     * (Name, Adresse, Protokoll); sonst wird eine neue Lok angelegt.
     */
    static CentralUndo transfer(EcosConnection connection, List<Mc2LocoWriter.Profile> profiles)
            throws IOException {
        EcosClient client = connection.getClient();
        Map<Integer, EcosLoco> locos = readLocos(client);
        StringBuilder backup = new StringBuilder();
        for (EcosLoco loco : locos.values()) {
            backup.append(loco.backupLine()).append('\n');
        }
        CentralUndo undo = new CentralUndo(CentralUndo.Kind.ECOS, connection.getHost(),
                CentralUndo.writeBackup(CentralUndo.Kind.ECOS, connection.getHost(), backup.toString()));
        Map<Integer, EcosLoco> byAddress = new LinkedHashMap<>();
        for (EcosLoco loco : locos.values()) {
            byAddress.putIfAbsent(loco.address(), loco);
        }
        for (Mc2LocoWriter.Profile profile : profiles) {
            String fields = "name[\"" + quote(profile.name()) + "\"], addr[" + profile.address()
                    + "], protocol[" + profile.fmt() + "]";
            try {
                EcosLoco existing = byAddress.get(profile.address());
                if (existing != null) {
                    setLoco(client, existing.id(), profile.name(), profile.address(), profile.fmt());
                    undo.overwritten.add(existing.id());
                } else {
                    EcosClient.Reply reply = client.send("create(10, " + fields + ", append)");
                    int id = -1;
                    for (EcosClient.Line line : reply.lines()) {
                        id = line.getInt("id", id);
                    }
                    if (!reply.isOk() || id < 0) {
                        throw new IOException(reply.message());
                    }
                    undo.created.add(new CentralUndo.Entry(id, profile.address(), profile.name(), profile.fmt()));
                }
            } catch (IOException ex) {
                undo.failed.add(profile.address() + " (" + ex.getMessage() + ")");
            }
        }
        return undo;
    }

    /** Uebertragung zuruecknehmen; liefert die Eintraege, bei denen es nicht geklappt hat. */
    static List<String> undo(EcosConnection connection, CentralUndo undo) throws IOException {
        EcosClient client = connection.getClient();
        Map<Integer, CentralUndo.Entry> backup = undo.backupEntries();
        List<String> failed = new ArrayList<>();
        for (Integer id : undo.overwritten) {
            CentralUndo.Entry entry = backup.get(id);
            if (entry == null) {
                continue;
            }
            try {
                setLoco(client, id, entry.name(), entry.address(), entry.format());
            } catch (IOException ex) {
                failed.add(entry.address() + " (" + ex.getMessage() + ")");
            }
        }
        for (CentralUndo.Entry entry : undo.created) {
            EcosClient.Reply reply = client.send("delete(" + entry.key() + ")");
            if (!reply.isOk()) {
                client.send("request(" + entry.key() + ", control, force)");
                reply = client.send("delete(" + entry.key() + ")");
            }
            if (!reply.isOk()) {
                failed.add(entry.address() + " (" + reply.message() + ")");
            }
        }
        return failed;
    }

    /**
     * Name, Adresse, Protokoll einer vorhandenen Lok setzen. Die ECoS
     * verlangt protocol/addr in einem eigenen set ohne weitere Attribute
     * ("protocol/addr with other attributes", Handtest 13.10.2026).
     */
    private static void setLoco(EcosClient client, int id, String name, int address, String protocol)
            throws IOException {
        set(client, id, "name[\"" + quote(name) + "\"]");
        set(client, id, "protocol[" + protocol + "], addr[" + address + "]");
    }

    /** {@code set(id, ...)}; geht das ohne Kontrolle nicht, mit {@code request(..., control, force)} erneut. */
    private static void set(EcosClient client, int id, String fields) throws IOException {
        EcosClient.Reply reply = client.send("set(" + id + ", " + fields + ")");
        if (reply.isOk()) {
            return;
        }
        client.send("request(" + id + ", control, force)");
        try {
            reply = client.send("set(" + id + ", " + fields + ")");
            if (!reply.isOk()) {
                throw new IOException(reply.message());
            }
        } finally {
            client.send("release(" + id + ", control)");
        }
    }

    /** Anfuehrungszeichen und Zeilenumbrueche sind im ECoS-Text nicht erlaubt. */
    static String quote(String text) {
        return nz(text).replace('"', '\'').replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
