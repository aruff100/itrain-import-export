package com.example.itrain_import_export;

import org.bidib.jbidibc.messages.BidibLibrary;
import org.bidib.jbidibc.messages.utils.ByteUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Macht aus einem rohen BiDiB-Paket lesbaren Text für das RX/TX-Fenster
 * ({@link BidibRawLogWindow}): Nachrichtenname, Knotenadresse, laufende
 * Nummer und die Nutzdaten so weit aufgelöst, wie es das Protokoll hergibt
 * (Zeichenketten, Kennungen, Merkmale mit Namen, Fehlercodes, Knotentabelle).
 * Was nicht bekannt ist, bleibt als Hex stehen.
 * <p>
 * Aufbau eines Pakets: {@code LEN, ADDR..., 0x00, NUM, TYPE, DATA...} - ein
 * Paket kann mehrere Nachrichten hintereinander enthalten. Die Namen der
 * Nachrichten, Merkmale, Fehlercodes und Link-Untertypen kommen per
 * Reflexion aus {@code BidibLibrary} (Konstanten MSG_*, FEATURE_*,
 * BIDIB_ERR_*, BIDIB_LINK_*), damit hier keine eigene Tabelle gepflegt
 * werden muss.
 */
public final class BidibMessageDecoder {

    private static final Map<Integer, String> MESSAGE_NAMES = constants("MSG_");
    private static final Map<Integer, String> FEATURE_NAMES = constants("FEATURE_");
    private static final Map<Integer, String> ERROR_NAMES = constants("BIDIB_ERR_");
    private static final Map<Integer, String> LINK_NAMES = constants("BIDIB_LINK_");

    private BidibMessageDecoder() {
    }

    /** Lesbare Form eines Pakets; bei unverstaendlichem Aufbau der Hex-Dump. */
    public static String decode(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        int offset = 0;
        while (offset < data.length) {
            int len = data[offset] & 0xFF;
            if (len < 3 || offset + 1 + len > data.length) {
                return toHex(data);
            }
            String part = decodeMessage(data, offset + 1, len);
            if (part == null) {
                return toHex(data);
            }
            parts.add(part);
            offset += 1 + len;
        }
        return String.join("  |  ", parts);
    }

    public static String toHex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 3);
        for (byte b : data) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    private static String decodeMessage(byte[] data, int start, int len) {
        int end = start + len;
        // Adresse bis zum 0-Byte.
        StringBuilder address = new StringBuilder();
        int i = start;
        while (i < end && data[i] != 0) {
            if (address.length() > 0) {
                address.append('.');
            }
            address.append(data[i] & 0xFF);
            i++;
        }
        if (i >= end - 1) {
            return null;
        }
        i++; // 0-Byte
        int num = data[i++] & 0xFF;
        int type = data[i++] & 0xFF;
        byte[] payload = java.util.Arrays.copyOfRange(data, i, end);

        String name = MESSAGE_NAMES.getOrDefault(type, String.format("MSG_%02X", type));
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(address.length() == 0 ? "0" : address).append("] #").append(num).append("  ")
                .append(name);
        String detail = describe(type, payload);
        if (detail != null && !detail.isEmpty()) {
            sb.append("  ").append(detail);
        }
        return sb.toString();
    }

    /** Nutzdaten je Nachrichtentyp - null heisst: nur Hex anhaengen. */
    private static String describe(int type, byte[] d) {
        switch (type) {
            case BidibLibrary.MSG_LOCAL_PROTOCOL_SIGNATURE:
                return quoted(text(d, 0, d.length));
            case BidibLibrary.MSG_LOCAL_LINK:
                return describeLink(d);
            case BidibLibrary.MSG_LOCAL_LOGON:
                return "UID " + uid(d, 0);
            case BidibLibrary.MSG_LOCAL_LOGON_ACK:
                return d.length >= 8 ? "Adresse " + (d[0] & 0xFF) + ", UID " + uid(d, 1) : hexOrEmpty(d);
            case BidibLibrary.MSG_LOCAL_LOGON_REJECTED:
                return "UID " + uid(d, 0);
            case BidibLibrary.MSG_SYS_MAGIC:
                return d.length >= 2 ? String.format("%02X%02X", d[1], d[0]) : hexOrEmpty(d);
            case BidibLibrary.MSG_SYS_P_VERSION:
                return d.length >= 2 ? "Protokoll " + (d[1] & 0xFF) + "." + (d[0] & 0xFF) : hexOrEmpty(d);
            case BidibLibrary.MSG_SYS_UNIQUE_ID:
                return "UID " + uid(d, 0);
            case BidibLibrary.MSG_SYS_ERROR:
                if (d.length == 0) {
                    return "";
                }
                return ERROR_NAMES.getOrDefault(d[0] & 0xFF, String.format("Fehler 0x%02X", d[0]))
                        + (d.length > 1 ? " " + toHex(java.util.Arrays.copyOfRange(d, 1, d.length)) : "");
            case BidibLibrary.MSG_NODETAB_COUNT:
                return d.length >= 1 ? (d[0] & 0xFF) + " Knoten" : "";
            case BidibLibrary.MSG_NODETAB:
            case BidibLibrary.MSG_NODE_NEW:
            case BidibLibrary.MSG_NODE_LOST:
                return d.length >= 9 ? "Version " + (d[0] & 0xFF) + ", lokale Adresse " + (d[1] & 0xFF) + ", UID "
                        + uid(d, 2) : hexOrEmpty(d);
            case BidibLibrary.MSG_STRING_GET:
                return d.length >= 2 ? stringSlot(d[0] & 0xFF, d[1] & 0xFF) : hexOrEmpty(d);
            case BidibLibrary.MSG_STRING:
            case BidibLibrary.MSG_STRING_SET:
                if (d.length >= 3) {
                    int size = d[2] & 0xFF;
                    return stringSlot(d[0] & 0xFF, d[1] & 0xFF) + " = " + quoted(text(d, 3, Math.min(size, d.length - 3)));
                }
                return hexOrEmpty(d);
            case BidibLibrary.MSG_FEATURE_COUNT:
                return d.length >= 1 ? (d[0] & 0xFF) + " Merkmale" + (d.length > 1 && d[1] != 0 ? " (Streaming)" : "")
                        : "";
            case BidibLibrary.MSG_FEATURE:
            case BidibLibrary.MSG_FEATURE_SET:
                return d.length >= 2 ? featureName(d[0] & 0xFF) + " = " + (d[1] & 0xFF) : hexOrEmpty(d);
            case BidibLibrary.MSG_FEATURE_NA:
            case BidibLibrary.MSG_FEATURE_GET:
                return d.length >= 1 ? featureName(d[0] & 0xFF) : "";
            case BidibLibrary.MSG_SYS_GET_MAGIC:
            case BidibLibrary.MSG_SYS_GET_P_VERSION:
            case BidibLibrary.MSG_SYS_ENABLE:
            case BidibLibrary.MSG_SYS_DISABLE:
            case BidibLibrary.MSG_SYS_GET_UNIQUE_ID:
            case BidibLibrary.MSG_NODETAB_GETALL:
            case BidibLibrary.MSG_NODETAB_GETNEXT:
            case BidibLibrary.MSG_FEATURE_GETALL:
            case BidibLibrary.MSG_FEATURE_GETNEXT:
                return hexOrEmpty(d);
            default:
                return hexOrEmpty(d);
        }
    }

    private static String describeLink(byte[] d) {
        if (d.length == 0) {
            return "";
        }
        int sub = d[0] & 0xFF;
        String subName;
        switch (sub) {
            case 0x00:
                subName = "DESCRIPTOR_PROD_STRING";
                break;
            case 0x01:
                subName = "DESCRIPTOR_USER_STRING";
                break;
            default:
                subName = LINK_NAMES.getOrDefault(sub, String.format("0x%02X", sub));
                break;
        }
        String rest;
        switch (sub) {
            case 0x00:
            case 0x01:
                rest = d.length >= 2 ? quoted(text(d, 2, Math.min(d[1] & 0xFF, d.length - 2))) : "";
                break;
            case 0xFF: // DESCRIPTOR_UID
                rest = "UID " + uid(d, 1);
                break;
            case 0xFE: // STATUS_PAIRED
            case 0xFD: // STATUS_UNPAIRED
            case 0xFC: // PAIRING_REQUEST
                rest = d.length >= 15 ? uid(d, 1) + " <-> " + uid(d, 8) : (d.length >= 8 ? uid(d, 1) : "");
                break;
            case 0x80: // DESCRIPTOR_P_VERSION
                rest = d.length >= 3 ? (d[2] & 0xFF) + "." + (d[1] & 0xFF) : "";
                break;
            default:
                rest = d.length > 1 ? toHex(java.util.Arrays.copyOfRange(d, 1, d.length)) : "";
                break;
        }
        return subName + (rest.isEmpty() ? "" : " " + rest);
    }

    private static String stringSlot(int namespace, int index) {
        String ns = namespace == 0 ? "Knoten" : namespace == 1 ? "Makro" : "Namensraum " + namespace;
        String ix = namespace == 0 && index == 0 ? "Produktname"
                : namespace == 0 && index == 1 ? "Benutzername" : "Index " + index;
        return ns + "/" + ix;
    }

    private static String featureName(int number) {
        return FEATURE_NAMES.getOrDefault(number, "Merkmal " + number);
    }

    private static String uid(byte[] d, int offset) {
        if (d.length < offset + 7) {
            return toHex(java.util.Arrays.copyOfRange(d, Math.min(offset, d.length), d.length));
        }
        byte[] raw = java.util.Arrays.copyOfRange(d, offset, offset + 7);
        try {
            return ByteUtils.formatHexUniqueId(ByteUtils.convertUniqueIdToLong(raw));
        } catch (RuntimeException ex) {
            return toHex(raw);
        }
    }

    private static String text(byte[] d, int offset, int length) {
        if (offset >= d.length || length <= 0) {
            return "";
        }
        int safe = Math.min(length, d.length - offset);
        return new String(d, offset, safe, StandardCharsets.ISO_8859_1);
    }

    private static String quoted(String s) {
        return "\"" + s + "\"";
    }

    private static String hexOrEmpty(byte[] d) {
        return d.length == 0 ? "" : toHex(d);
    }

    /** Alle int-Konstanten von BidibLibrary mit diesem Praefix: Wert -> Name (ohne Praefix bei MSG_ nicht). */
    private static Map<Integer, String> constants(String prefix) {
        Map<Integer, String> map = new HashMap<>();
        for (Field field : BidibLibrary.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !field.getName().startsWith(prefix)) {
                continue;
            }
            try {
                Object value = field.get(null);
                int key;
                if (value instanceof Integer intValue) {
                    key = intValue;
                } else if (value instanceof Byte byteValue) {
                    key = byteValue & 0xFF;
                } else {
                    continue;
                }
                // Mehrere Namen je Wert kommen vor (Bereichsmarken wie
                // MSG_DLOCAL = 0x70 neben MSG_LOCAL_LOGON_ACK, Kurzformen wie
                // MSG_LOGON neben MSG_LOCAL_LOGON) - der laengere Name ist
                // der sprechende.
                String existing = map.get(key);
                if (existing == null || field.getName().length() > existing.length()) {
                    map.put(key, field.getName());
                }
            } catch (IllegalAccessException ignored) {
                // nicht zugreifbar - dann eben ohne Namen
            }
        }
        return map;
    }
}
