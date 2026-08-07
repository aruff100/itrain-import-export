#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Erzeugt aus der CV-Tabelle einer Hersteller-Betriebsanleitung (PDF) eine
Decoder-Vorlage im CSV-Format von iTrain Import/Export.

WARUM ES DIESES SKRIPT GIBT
---------------------------
Die ersten dreizehn Vorlagen (ESU, Lenz, D&H, Tams, PIKO, ZIMO, Maerklin)
wurden jeweils mit einem eigenen Wegwerf-Skript erzeugt. Die sind verloren,
und das Verfahren musste bei jedem neuen Decoder neu hergeleitet werden.
Hier steht es nun einmal.

WAS DIESES SKRIPT NICHT IST
---------------------------
Kein Automat. Jede Anleitung setzt ihre Tabelle anders: mal mit Linien, mal
ohne, mal zweispaltig, mal mit einer Spalte "Bereich", die wegfaellt. Die
Spaltenzuordnung wird deshalb immer von Hand uebergeben, und das Ergebnis
gehoert angesehen, bevor es in den Vorlagen-Ordner wandert. Das Skript nimmt
die stumpfe Arbeit ab - die Beurteilung nicht.

VORGEHEN (so wurde jede der bisherigen Vorlagen gebaut)
-------------------------------------------------------
1. Seitenbereich der CV-Tabelle bestimmen. Nicht auf die gedruckte
   Seitenzahl verlassen - sie kann vom PDF-Blatt abweichen. Sicherer:
   --show-pages benutzen und die Seiten am Tabellenkopf erkennen.
2. Spaltenaufbau ansehen (--inspect). Welche Spalte ist CV, welche der Wert,
   was faellt weg.
3. Erzeugen, Ergebnis pruefen: Anzahl, erste und letzte CV, Luecken.
4. Datei neben die anderen Vorlagen legen.

BEISPIEL (Uhlenbrock 77 300 / 77 310)
--------------------------------------
    python3 tools/pdf_cv_extract.py --pdf Bes77300_1.pdf --show-pages
    python3 tools/pdf_cv_extract.py --pdf Bes77300_1.pdf --pages 54-73 --inspect
    python3 tools/pdf_cv_extract.py --pdf Bes77300_1.pdf --pages 54-73 \
        --col-nr 0 --col-description 1 --col-value 4 --expand-ranges \
        --name "Uhlenbrock digital 77 300, 77 310" \
        --template-description "..." --out Uhlenbrock_77300_77310.csv

Benoetigt: pip install pdfplumber
"""

import argparse
import csv
import itertools
import io
import re
import sys
from xml.sax.saxutils import escape

try:
    import pdfplumber
except ImportError:
    sys.exit("pdfplumber fehlt:  pip install pdfplumber --break-system-packages")


# --------------------------------------------------------------------------
# CSV-Format des Programms - muss exakt zu DecoderTemplate.read/write passen
# --------------------------------------------------------------------------

HEADER = ["Kategorie", "Typ", "Name", "Beschreibung", "XML"]
CATEGORY = "decoder-configuration"
TYPE = "configuration"


def write_template(path, name, description, parameters):
    """
    Schreibt die Vorlagendatei: 5 Spalten, Semikolon, immer gequotet,
    UTF-8 MIT BOM und CRLF (so erwartet es das Programm, und so oeffnet
    Excel/LibreOffice die Datei ohne Nachfrage zur Kodierung).

    Die gesamte CV-Liste steht als <configuration>-XML in der fuenften
    Spalte - eine Vorlagendatei hat deshalb genau eine Kopf- und eine
    Datenzeile. Das ist kein Fehler, sondern das Transportformat.
    """
    zeilen = [f'<configuration count="{len(parameters)}">']
    for p in parameters:
        attrs = f' nr="{escape(p["nr"], {chr(34): "&quot;"})}"'
        if p.get("value"):
            attrs += f' value="{escape(p["value"], {chr(34): "&quot;"})}"'
        if p.get("type"):
            attrs += f' type="{escape(p["type"], {chr(34): "&quot;"})}"'
        zeilen.append(f"  <parameter{attrs}>")
        zeilen.append(f'    <description>{escape(p.get("description", ""))}</description>')
        zeilen.append("  </parameter>")
    zeilen.append("</configuration>")
    xml = "\n".join(zeilen)

    puffer = io.StringIO()
    schreiber = csv.writer(puffer, delimiter=";", quotechar='"',
                           quoting=csv.QUOTE_ALL, lineterminator="\r\n")
    schreiber.writerow(HEADER)
    schreiber.writerow([CATEGORY, TYPE, name, description, xml])
    with open(path, "wb") as f:
        f.write(b"\xef\xbb\xbf" + puffer.getvalue().encode("utf-8"))


# --------------------------------------------------------------------------
# Auslesen
# --------------------------------------------------------------------------

# Zeichen aus dem Unicode-Privatbereich (U+E000-U+F8FF). Sie entstehen, wenn
# eine Anleitung Symbolschriften wie Wingdings einsetzt - im PDF sieht man
# einen Pfeil, im ausgelesenen Text steht ein Zeichen ohne allgemeingueltige
# Bedeutung, das in der Vorlage als leeres Kaestchen erscheint. ZIMO nutzt
# U+F0E0 76-mal als Pfeil ("#127 -> FA1").
SYMBOLZEICHEN = {
    "": "→",
    "": "→",
    "": "←",
    "": "↑",
    "": "↓",
    "": "-",
    "": "-",
}


def zellen_text(zelle):
    """Zellinhalt saeubern. Mehrzeilige Zellen behalten ihre Zeilen vorerst."""
    if zelle is None:
        return ""
    text = str(zelle)
    for zeichen, ersatz in SYMBOLZEICHEN.items():
        text = text.replace(zeichen, ersatz)
    return re.sub(r"[ \t]+", " ", text).strip()


def unbekannte_symbole(text):
    """Uebrig gebliebene Privatbereich-Zeichen - die gehoeren gemeldet."""
    return sorted({c for c in text if 0xE000 <= ord(c) <= 0xF8FF})


def erste_zeile(text):
    """Erste nicht-leere Zeile - fuer Zellen, in denen mehrere Werte stehen."""
    for z in text.split("\n"):
        if z.strip():
            return z.strip()
    return ""


def parse_nr_cell(nr_text, streng=False):
    """
    Deutet die CV-Spalte. Drei Faelle, die in echten Anleitungen vorkommen:

    - **Einzelne Zahl** "57"            -> ([57], "einzeln")
    - **Spanne** "116 - 123"            -> ([116..123], "bereich")
    - **Mehrere Zahlen untereinander**  -> ([17, 18], "gestapelt")
      Eine Zeile der gedruckten Tabelle deckt zwei CVs ab, etwa die lange
      Adresse ("17 = Hoeherwertiges Byte" / "18 = Niederwertiges Byte").
      Beschreibung und Wert stehen dann ebenfalls zeilenweise darunter und
      werden weiter unten paarweise zugeordnet.

    Spannen muessen aufgeloest werden: iTrain speichert je CV ein eigenes
    <parameter nr="...">, eine Spanne laesst sich dort nicht ablegen.
    """
    # Erst plaetten und vereinheitlichen. Die Zelle kann alles Moegliche
    # enthalten - Rauten, Zeilenumbrueche mitten in einer Spanne, ein "+"
    # oder Auslassungspunkte als Trenner:
    #   "#15 + #16"                 zwei CVs
    #   "#840 #841"                 zwei CVs, nur durch Leerzeichen getrennt
    #   "#127 \n - \n #132"         eine Spanne, umgebrochen
    #   "#35 … #46"                 eine Spanne mit Auslassungspunkten
    #   "#166 - #169 #170 - #173"   mehrere Spannen in einer Zelle
    t = nr_text.replace("#", " ")
    t = t.replace("…", " - ").replace("...", " - ").replace("–", "-").replace("—", "-")
    # Ausgeschriebene Trenner: Tams schreibt "15 und 16", englische
    # Anleitungen "15 and 16".
    t = re.sub(r"\b(und|and)\b", "+", t, flags=re.I)
    t = re.sub(r"\s+", " ", t).strip()
    # Auslassungspunkte als Spannen-Zeichen, auch ueber Zeilen verteilt:
    # Tams schreibt "68 . . 95" (jeder Punkt in einer eigenen Zeile).
    t = re.sub(r"(?<=\d)\s*(?:\.\s*){2,}(?=\d)", " - ", t)

    # Nur Zahlen und Trennzeichen? Dann laesst sich die Zelle vollstaendig
    # deuten. Steht noch anderer Text darin ("364 ab SW- 6.00"), waere das
    # Herausklauben einzelner Zahlen gefaehrlich - daraus wuerde sonst neben
    # CV 364 auch noch ein "CV 6" und ein "CV 0".
    if re.fullmatch(r"[\d\s\-+,]+", t):
        nummern = []
        art = "einzeln"
        for m in re.finditer(r"(\d+)\s*-\s*(\d+)|(\d+)", t):
            if m.group(1):
                von, bis = int(m.group(1)), int(m.group(2))
                if von > bis or bis - von > 512:
                    return [], "unklar"
                nummern.extend(range(von, bis + 1))
                art = "bereich"
            else:
                nummern.append(int(m.group(3)))
        if not nummern:
            return [], "unklar"
        if art != "bereich" and len(nummern) > 1:
            art = "gestapelt"
        return nummern, art

    # Sonst: fuehrende Zahl nehmen, Rest ist Beiwerk ("364 ab SW-Version 6.00").
    #
    # Mit --strict-nr unterbleibt das. Noetig bei bankabhaengigen CVs:
    # Uhlenbrock schreibt "900 A", "900 B", "900 C" - dieselbe Nummer in
    # fuenf ueber CV 1021 umgeschalteten Baenken, jedesmal mit anderer
    # Bedeutung. Die fuehrende Zahl zu nehmen ergaebe fuenfmal "CV 900" mit
    # widerspruechlichem Inhalt.
    if streng:
        return [], "unklar"
    m = re.match(r"(\d+)", t)
    if m:
        return [int(m.group(1))], "einzeln"
    return [], "unklar"


def lies_vorlage(pfad):
    """
    Liest die Parameter einer vorhandenen Vorlagendatei - fuer --append-to.

    Gebraucht, wenn eine Anleitung ihre CVs auf zwei Tabellen mit
    unterschiedlichem Aufbau verteilt: Die Uhlenbrock-Sounddecoder haben die
    Lokdecoder-CVs in der Mitte des Hefts und die SUSI-Sound-CVs ganz hinten,
    mit anderem Tabellenkopf. Beide brauchen einen eigenen Lauf, sollen aber
    in einer Vorlage landen.
    """
    csv.field_size_limit(10_000_000)
    with open(pfad, encoding="utf-8-sig") as f:
        zeilen = list(csv.reader(f, delimiter=";"))
    if len(zeilen) < 2 or len(zeilen[1]) < 5:
        raise SystemExit(f"{pfad} sieht nicht wie eine Vorlage aus")
    xml = zeilen[1][4]
    parameter = []
    muster = re.compile(
        r'<parameter nr="([^"]*)"(?: value="([^"]*)")?(?: type="([^"]*)")?>\s*'
        r'<description>(.*?)</description>', re.S)
    entwerte = {"&lt;": "<", "&gt;": ">", "&quot;": '"', "&apos;": "'", "&amp;": "&"}
    def roh(text):
        for a, b in entwerte.items():
            text = text.replace(a, b)
        return text
    for nr, wert, typ, besch in muster.findall(xml):
        parameter.append({"nr": nr, "value": wert, "type": roh(typ),
                          "description": roh(besch), "aus_bereich": False})
    return parameter, zeilen[1][2], zeilen[1][3]


def zeilen_aus_woertern(seite, grenzen, toleranz, nr_index=0, fussbereich=0):
    """
    Baut die Tabelle aus den **Wortpositionen** statt aus der
    Tabellenerkennung von pdfplumber.

    Noetig, wenn eine Anleitung ihre Tabelle rein ueber das Layout setzt.
    Bei der mXion-Anleitung schwankt die von pdfplumber gefundene
    Spaltenzahl von Seite zu Seite zwischen 12 und 28, die CV-Spalte sitzt
    mal an Index 0, mal an 1, und mit fest vorgegebenen Trennlinien faellt
    sie ganz aus der erkannten Tabelle heraus. Die x-Positionen der Spalten
    sind dagegen ueber alle Seiten stabil.

    @param grenzen  aufsteigende x-Werte; zwischen zwei Werten liegt eine
                    Spalte. Sieben Werte ergeben sechs Spalten.
    @param toleranz wie weit zwei Woerter senkrecht auseinander liegen
                    duerfen und noch als dieselbe Zeile gelten
    @param nr_index Spaltenindex der CV-Nummer, fuer das Zusammenfuehren
                    umgebrochener Spannen (siehe unten)
    @param fussbereich Bildpunkte vom unteren Seitenrand, die ausgeblendet
                    werden (--footer-margin). mXion druckt auf jeder Seite
                    eine Fusszeile "<Seitenzahl> DRIVE-M" - "DRIVE-M" faellt
                    zufaellig in die Bemerkung-Spalte und haengte sich sonst
                    als sinnlose Fortsetzung an das letzte CV der Seite.
    """
    grenze_unten = seite.height - fussbereich if fussbereich else seite.height + 1
    woerter = [w for w in seite.extract_words()
               if grenzen[0] <= w["x0"] < grenzen[-1] and w["top"] < grenze_unten]
    woerter.sort(key=lambda w: (round(w["top"] / toleranz), w["x0"]))
    zeilen = []
    for _, gruppe in itertools.groupby(woerter, key=lambda w: round(w["top"] / toleranz)):
        spalten = [""] * (len(grenzen) - 1)
        for w in gruppe:
            for i in range(len(grenzen) - 1):
                if grenzen[i] <= w["x0"] < grenzen[i + 1]:
                    spalten[i] = (spalten[i] + " " + w["text"]).strip()
                    break
        if any(spalten):
            zeilen.append(spalten)

    # Ende der eigentlichen CV-Tabelle erkennen. mXion haengt nach der
    # letzten CV auf derselben Seite die Anhaenge an ("ANHANG 1 -
    # Schaltbefehlszuordnung" usw., worauf die CV-Bemerkungen mit "siehe
    # Anhang 1" verweisen) - gleiche Spaltenbreiten, aber eine eigene,
    # andere Tabelle. Ohne Abschneiden landet sie als Fortsetzungstext am
    # letzten CV der Seite.
    # Grossschreibung ist hier das Unterscheidungsmerkmal: Die Ueberschrift
    # heisst "ANHANG", der laufende Verweis in den Bemerkungen aber "Anhang"
    # ("siehe Anhang 6") - ein Vergleich ohne Gross-/Kleinschreibung wuerde
    # schon beim ersten solchen Verweis mitten in der echten Tabelle
    # abschneiden.
    for index, zeile in enumerate(zeilen):
        if any(re.search(r"\bANHANG\b", zelle or "") for zelle in zeile):
            zeilen = zeilen[:index]
            break

    # Umgebrochene Spanne in der CV-Spalte zusammenfuehren. mXion setzt die
    # frei programmierbare Fahrkurve als
    #     "67-"  | "Frei programmierbare" | ...
    #     "94"   | "Fahrkurve"            |
    # also die Spanne ueber zwei Textzeilen. Ohne Zusammenfuehrung entstehen
    # daraus CV 67 und CV 94, und die 26 CVs dazwischen fehlen stillschweigend.
    verbunden = []
    i = 0
    while i < len(zeilen):
        zeile = list(zeilen[i])
        nr = zeile[nr_index].rstrip() if nr_index < len(zeile) else ""
        if nr.endswith(("-", "–", "…")) and i + 1 < len(zeilen):
            folge = zeilen[i + 1]
            if re.fullmatch(r"\d+", (folge[nr_index] or "").strip()):
                zeile[nr_index] = nr + folge[nr_index].strip()
                for j in range(len(zeile)):
                    if j != nr_index and folge[j]:
                        zeile[j] = (zeile[j] + " " + folge[j]).strip()
                verbunden.append(zeile)
                i += 2
                continue
        verbunden.append(zeile)
        i += 1
    return verbunden


def spalten_aus_kopf(tabelle, header_map, ersatz):
    """
    Sucht die Kopfzeile der Tabelle und ordnet die Spalten ueber ihre
    Ueberschrift zu, statt ueber feste Nummern.

    Erkannt wird die Kopfzeile daran, dass eine Zelle genau die
    CV-Ueberschrift traegt (z.B. "CV"). Die uebrigen Ueberschriften werden
    als Teilzeichenkette gesucht, damit "Werte bereich" und "Wertebereich"
    gleichermassen passen.

    **Fehlt eine der angeforderten Ueberschriften, gilt die Tabelle als
    fremd und wird uebersprungen.** Das ist der eigentliche Nutzen: Die
    Uhlenbrock-Anleitungen haben am Ende eine "CV Tabelle zur Programmierung
    der Banken 1 - 4" - auch sie hat eine Spalte "CV", aber keine Spalte
    "Wert ab Werk". Ohne diese Pruefung landeten deren Zeilen (CV 257-512,
    Werte je nach Bank voellig anders belegt) in der Vorlage.

    @return Zuordnung fuer diese Tabelle, oder None, wenn die Kopfzeile fehlt
            oder unvollstaendig ist - dann ist es keine CV-Tabelle.
    """
    def norm(text):
        return re.sub(r"\s+", " ", text or "").strip().lower()

    nr_titel = norm(header_map["nr"])
    for zeile in tabelle:
        if not zeile:
            continue
        titel = [norm(zellen_text(c)) for c in zeile]
        if nr_titel not in titel:
            continue
        zuordnung = {"nr": titel.index(nr_titel), "description": ersatz["description"],
                     "value": ersatz["value"], "type": ersatz["type"]}
        for schluessel in ("description", "value", "type"):
            gesucht = norm(header_map.get(schluessel) or "")
            if not gesucht:
                continue
            # Erst genaue Uebereinstimmung, dann erst Teilzeichenkette.
            # Sonst verschluckt ein einbuchstabiger Spaltenname alles:
            # mXion nennt die Wert-Spalte "S" - und "s" steckt auch in
            # "Beschreibung", die dann faelschlich als Wert gelesen wuerde.
            gefunden = None
            for idx, t in enumerate(titel):
                if t == gesucht:
                    gefunden = idx
                    break
            if gefunden is None:
                for idx, t in enumerate(titel):
                    if t and gesucht in t:
                        gefunden = idx
                        break
            if gefunden is None:
                return None      # fremde Tabelle - siehe Erlaeuterung oben
            zuordnung[schluessel] = gefunden
        return zuordnung
    return None


def extract(pdf_path, seiten, spalten, optionen):
    """
    Liest die Tabellenzeilen der angegebenen Seiten und macht daraus
    Parameter-Eintraege.

    Zwei Faelle, die jede Anleitung hat und die man leicht uebersieht:
    - **Kopfzeilen** wiederholen sich auf jeder Seite und muessen raus.
    - **Fortsetzungszeilen** (CV-Spalte leer) gehoeren zur Beschreibung des
      vorherigen CV - meist dort, wo eine lange Bit-Aufzaehlung ueber einen
      Seitenumbruch laeuft. Wer sie verwirft, verliert Text; wer sie als
      eigene Zeile behandelt, bekommt Eintraege ohne CV-Nummer.
    """
    ergebnis = []
    gesehen = {}
    hinweise = []

    with pdfplumber.open(pdf_path) as pdf:
        for seite in seiten:
            if seite < 1 or seite > len(pdf.pages):
                hinweise.append(f"Seite {seite} gibt es nicht")
                continue
            if optionen["columns"]:
                # Wortbasiert: eine "Tabelle" je Seite, Spalten ueber feste
                # x-Positionen. Kopf-Zuordnung und Tabellenfilter entfallen,
                # die Spalten stehen ja fest.
                tabellen = [zeilen_aus_woertern(pdf.pages[seite - 1],
                                                optionen["columns"], optionen["row_tolerance"],
                                                spalten["nr"], optionen["footer_margin"])]
            else:
                # Reihenfolge der Tabellen: erst nach linkem Rand, dann nach
                # Hoehe. Zweispaltig gesetzte Anleitungen (ZIMO) haben zwei
                # Tabellen je Seite, und pdfplumber liefert sie NICHT in
                # Lesereihenfolge - auf Seite 75 kommt die rechte Spalte
                # zuerst. Ohne Sortierung haengen Fortsetzungszeilen am
                # falschen CV.
                gefunden = sorted(pdf.pages[seite - 1].find_tables(),
                                  key=lambda t: (round(t.bbox[0] / 20), t.bbox[1]))
                tabellen = [t.extract() for t in gefunden]
            if not tabellen:
                hinweise.append(f"Seite {seite}: keine Tabelle gefunden")
                continue
            for tabelle in tabellen:
                # Spaltenzuordnung je Tabelle. Zwei Wege:
                #
                # a) ueber die Ueberschriften (--nr-header usw.). Noetig, wenn
                #    die Spaltenzahl innerhalb eines Dokuments wechselt: Die
                #    Uhlenbrock-Lokdecoder haben auf manchen Seiten eine
                #    zusaetzliche leere Spalte, "Wert ab Werk" steht mal an
                #    Position 4, mal an Position 3. Mit festen Indizes stuende
                #    ab der Haelfte der Wertebereich im Wert-Feld.
                # b) ueber feste Indizes (--col-nr usw.), wenn die Tabelle
                #    keinen brauchbaren Kopf hat.
                aktuell = spalten
                if optionen["columns"]:
                    pass  # feste Spalten aus --columns, nichts zuzuordnen
                elif optionen["header_map"]:
                    aktuell = spalten_aus_kopf(tabelle, optionen["header_map"], spalten)
                    if aktuell is None:
                        continue  # keine Kopfzeile -> keine CV-Tabelle
                elif optionen["require_header"]:
                    # Nur Tabellen mit dem erwarteten Kopf verarbeiten. Auf
                    # Seiten mit Fussnoten und Randkaesten findet pdfplumber
                    # sonst allerlei, was keine CV-Tabelle ist.
                    kopf_da = any(
                        zeile and spalten["nr"] < len(zeile)
                        and zellen_text(zeile[spalten["nr"]]).lower() == optionen["require_header"].lower()
                        for zeile in tabelle)
                    if not kopf_da:
                        continue
                spalten_hier = aktuell
                for zeile in tabelle:
                    if not zeile:
                        continue
                    hole = lambda idx: zellen_text(zeile[idx]) if idx is not None and idx < len(zeile) else ""

                    nr_roh = hole(spalten_hier["nr"])
                    beschreibung = hole(spalten_hier["description"])
                    wert_roh = hole(spalten_hier["value"]) if spalten_hier["value"] is not None else ""
                    typ_roh = hole(spalten_hier["type"]) if spalten_hier["type"] is not None else ""
                    # Wert: normalerweise die erste Zeile der Zelle. Steht der
                    # Vorgabewert aber eingebettet im Wertebereich - Tams
                    # schreibt "1 ... 255 (3)", der Defaultwert in Klammern -
                    # holt ihn ein Suchmuster heraus (--value-regex).
                    if optionen["value_regex"]:
                        flach_wert = re.sub(r"\s+", " ", wert_roh.replace("\n", " ")).strip()
                        treffer = optionen["value_regex"].search(flach_wert)
                        if treffer is None:
                            wert = ""
                        elif treffer.lastindex:
                            # Erste Gruppe, die etwas getroffen hat. Erlaubt
                            # Muster mit Alternativen: Maerklin schreibt den
                            # Vorgabewert mal in Klammern hinter dem Bereich
                            # ("01 - 80 (52)"), mal steht in der Spalte nur
                            # der feste Wert ("77"), mal nur ein Bereich
                            # ("0 - 255") - der ist kein Vorgabewert.
                            wert = next((g for g in treffer.groups() if g), "").strip()
                        else:
                            wert = treffer.group(0).strip()
                    else:
                        wert = erste_zeile(wert_roh)
                    typ = erste_zeile(typ_roh)

                    # Kopfzeile der Tabelle. Punkte und Umbrueche wegnehmen,
                    # damit auch "Nr." und "CV-\nNr." erkannt werden - sonst
                    # laeuft je Seite eine Kopfzeile als Datenzeile mit.
                    kurz = re.sub(r"[.\s-]+", "", nr_roh).lower()
                    if kurz in ("cv", "cvnr", "nr", "cvnummer", "nummer"):
                        continue
                    # voellig leere Zeile
                    if not nr_roh and not beschreibung:
                        continue

                    # Fortsetzung der vorherigen Beschreibung
                    if not nr_roh:
                        if optionen["last_group"] == 0 and beschreibung:
                            # Die vorherige Zeile hatte eine CV-Nummer, aber
                            # keiner ihrer Eintraege wurde uebernommen (z.B.
                            # eine Randnotiz zu bereits vergebenen CVs) - dann
                            # gehoert diese Fortsetzung zu niemandem Bekannten.
                            # Lieber verwerfen und melden, als sie dem
                            # naechstbesten CV unterzuschieben.
                            hinweise.append(f"Seite {seite}: Fortsetzungstext ohne zugehoeriges CV "
                                            f"verworfen: {flach(beschreibung, optionen)!r}")
                        elif ergebnis and beschreibung:
                            for eintrag in ergebnis[-optionen["last_group"]:]:
                                eintrag["description"] = (
                                    eintrag["description"] + optionen["join"] + flach(beschreibung, optionen)
                                ).strip(optionen["join"])
                        continue

                    if optionen["strip_prefix"]:
                        nr_roh = nr_roh.lstrip(optionen["strip_prefix"]).strip()
                    if optionen["strip_leading_zeros"]:
                        nr_roh = re.sub(r"\b0+(\d)", r"\1", nr_roh)

                    nummern, art = parse_nr_cell(nr_roh, optionen["strict_nr"])
                    if not nummern:
                        hinweise.append(f"Seite {seite}: CV-Angabe nicht deutbar: {nr_roh!r}")
                        continue
                    if art == "bereich" and not optionen["expand_ranges"]:
                        nummern = nummern[:1]
                        hinweise.append(f"Seite {seite}: Bereich {nr_roh!r} auf die erste Nummer verkuerzt "
                                        f"(--expand-ranges nicht gesetzt)")

                    # Gestapelte Zeile: Werte zeilenweise zuordnen, sofern die
                    # Zeilenzahl passt - "192\n128" bei CV 17+18 sind wirklich
                    # zwei Werte.
                    #
                    # Bei Bezeichnung und Beschreibung ist dieselbe Annahme
                    # gefaehrlich: Dort ist ein Zeilenumbruch meist nur der
                    # Umbruch einer schmalen Spalte. ZIMO schreibt zu CV 15+16
                    # "Decoder-Sperre / (decoder lock)" - paarweise zugeordnet
                    # hiesse CV 15 "Decoder-Sperre" und CV 16 "(decoder lock)",
                    # was Unsinn ist. Deshalb nur auf ausdruecklichen Wunsch
                    # (--pair-stacked), wenn die Zeilen tatsaechlich je einem
                    # CV gehoeren (Uhlenbrock: "17 = Hoeherwertiges Byte").
                    b_zeilen = [z.strip() for z in beschreibung.split("\n") if z.strip()]
                    w_zeilen = [z.strip() for z in wert_roh.split("\n") if z.strip()]
                    t_zeilen = [z.strip() for z in typ_roh.split("\n") if z.strip()]
                    paaren = optionen["pair_stacked"] and art == "gestapelt"
                    angehaengt = 0

                    for pos, n in enumerate(nummern):
                        if paaren and len(b_zeilen) == len(nummern):
                            text = b_zeilen[pos]
                        else:
                            text = flach(beschreibung, optionen)
                        if art == "gestapelt" and len(w_zeilen) == len(nummern):
                            w = w_zeilen[pos]
                        else:
                            w = wert
                        if paaren and len(t_zeilen) == len(nummern):
                            t = t_zeilen[pos]
                        else:
                            t = flach(typ_roh, optionen)
                        # "-" und aehnliches heisst: kein Vorgabewert angegeben
                        if w and not re.fullmatch(r"-?\d+", w):
                            w = ""
                        # Fuehrende Nullen weg: Maerklin schreibt "08" fuer die
                        # 6021-Eingabe. In der Vorlage soll der Wert so
                        # stehen, wie iTrain ihn fuehrt.
                        if w and re.fullmatch(r"0\d+", w):
                            w = str(int(w))

                        eintrag = {"nr": str(n), "value": w, "type": t,
                                   "description": text, "aus_bereich": art == "bereich"}

                        alt = gesehen.get(n)
                        if alt is None:
                            gesehen[n] = eintrag
                            ergebnis.append(eintrag)
                            angehaengt += 1
                        elif alt["aus_bereich"] and not eintrag["aus_bereich"]:
                            # Ein einzeln aufgefuehrtes CV schlaegt eine Spanne.
                            # Anleitungen fassen Spannen gern zu weit ("67 - 97"),
                            # obwohl 95/96/97 weiter unten eigene Bedeutungen
                            # haben. Die genauere Angabe gewinnt.
                            ergebnis[ergebnis.index(alt)] = eintrag
                            gesehen[n] = eintrag
                            angehaengt += 1
                            hinweise.append(f"Seite {seite}: CV {n} war von einer Spanne abgedeckt und wurde "
                                            f"durch den eigenen Eintrag ersetzt")
                        elif not alt["aus_bereich"] and eintrag["aus_bereich"]:
                            hinweise.append(f"Seite {seite}: CV {n} bleibt beim eigenen Eintrag, "
                                            f"die Spanne wird dafuer verworfen")
                        else:
                            hinweise.append(f"Seite {seite}: CV {n} kommt mehrfach vor - erster Treffer bleibt")

                    # Nur tatsaechlich neu angehaengte/ersetzte Eintraege zaehlen -
                    # nicht len(nummern). mXion hat eine Randnotiz "7+8", deren
                    # beide Nummern schon vergeben sind (echte CV 7 und CV 8
                    # existieren bereits) und deshalb komplett verworfen werden.
                    # Ohne diese Korrektur stand last_group trotzdem auf 2, und
                    # die folgenden Fortsetzungszeilen der Randnotiz wurden CV 8
                    # UND CV 9 zugeschlagen statt gar keinem der beiden.
                    optionen["last_group"] = angehaengt

    return ergebnis, hinweise


def flach(text, optionen):
    """
    Mehrzeilige Zelle zu einer Zeile - <description> traegt einen Text.

    Mit --dehyphenate werden dabei Trennstriche am Zeilenende aufgeloest.
    Schmale Spalten (ZIMO setzt die Bezeichnung in eine Spalte von zwei
    Zentimetern) trennen fast jedes Wort: "Motoransteue-\\nrungsperiode".
    Ohne Behandlung stuende das so in der Vorlage.

    Die Unterscheidung: Folgt auf den Trennstrich ein **Kleinbuchstabe**, war
    es eine Silbentrennung - Strich weg, Woerter zusammen. Folgt ein
    **Grossbuchstabe**, gehoert der Strich zum Wort ("Dreipunkt-Kennlinie")
    und bleibt stehen.
    """
    teile = [z.strip() for z in text.split("\n") if z.strip()]
    if not optionen.get("dehyphenate"):
        return optionen["join"].join(teile)

    ergebnis = ""
    for teil in teile:
        if not ergebnis:
            ergebnis = teil
        elif ergebnis.endswith("-"):
            if teil[:1].islower():
                ergebnis = ergebnis[:-1] + teil      # Silbentrennung
            else:
                ergebnis = ergebnis + teil           # Bindestrich gehoert dazu
        else:
            ergebnis = ergebnis + " " + teil
    return ergebnis


# --------------------------------------------------------------------------
# Hilfsausgaben zum Erkunden einer neuen Anleitung
# --------------------------------------------------------------------------

def show_pages(pdf_path, muster):
    with pdfplumber.open(pdf_path) as pdf:
        print(f"{len(pdf.pages)} Seiten")
        for i, seite in enumerate(pdf.pages, start=1):
            txt = seite.extract_text() or ""
            zeilen = [z.strip() for z in txt.split("\n") if z.strip()]
            treffer = "  <== TREFFER" if muster and re.search(muster, txt, re.I) else ""
            tab = len(seite.extract_tables())
            print(f"  S.{i:<4} Tabellen={tab}  {' / '.join(zeilen[:2])[:88]}{treffer}")


def inspect(pdf_path, seiten):
    with pdfplumber.open(pdf_path) as pdf:
        for seite in seiten:
            print(f"--- Seite {seite} ---")
            for tabelle in pdf.pages[seite - 1].extract_tables():
                for zeile in tabelle[:4]:
                    for idx, zelle in enumerate(zeile):
                        kurz = (zellen_text(zelle) or "").replace("\n", "\\n")[:60]
                        print(f"    [{idx}] {kurz}")
                    print("    -")


def parse_pages(text):
    seiten = []
    for teil in text.split(","):
        teil = teil.strip()
        if "-" in teil:
            a, b = teil.split("-", 1)
            seiten.extend(range(int(a), int(b) + 1))
        elif teil:
            seiten.append(int(teil))
    return seiten


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--pdf", required=True)
    ap.add_argument("--pages", help="z.B. 54-73 oder 10,12,14-16")
    ap.add_argument("--show-pages", action="store_true", help="Seitenuebersicht, um den Bereich zu finden")
    ap.add_argument("--find", help="Suchmuster fuer --show-pages, z.B. 'CV Beschreibung'")
    ap.add_argument("--inspect", action="store_true", help="Spaltenaufbau der ersten Zeilen zeigen")
    ap.add_argument("--col-nr", type=int, default=0)
    ap.add_argument("--col-description", type=int, default=1)
    ap.add_argument("--col-value", type=int, default=None)
    ap.add_argument("--col-type", type=int, default=None)
    ap.add_argument("--expand-ranges", action="store_true",
                    help='"116 - 123" in acht einzelne CVs aufloesen')
    ap.add_argument("--strip-prefix", default="", help='Zeichen vor der CV-Nummer entfernen, z.B. "#" bei ZIMO')
    ap.add_argument("--strip-leading-zeros", action="store_true", help="fuehrende Nullen entfernen (D&H)")
    ap.add_argument("--join", default=" / ", help="Trenner beim Zusammenziehen mehrzeiliger Zellen")
    ap.add_argument("--dehyphenate", action="store_true",
                    help="Silbentrennung am Zeilenende aufloesen (schmale Spalten, z.B. ZIMO)")
    ap.add_argument("--pair-stacked", action="store_true",
                    help="bei gestapelten CVs auch Bezeichnung/Beschreibung zeilenweise zuordnen")
    ap.add_argument("--require-header", default="",
                    help='nur Tabellen verarbeiten, die diesen Kopf in der CV-Spalte haben, z.B. "CV"')
    ap.add_argument("--columns", default="",
                    help="x-Positionen der Spaltengrenzen, z.B. \"25,54,224,253,285,340,575\". "
                         "Schaltet auf wortbasiertes Lesen um - noetig, wenn die Tabellenerkennung "
                         "von pdfplumber je Seite andere Spalten liefert.")
    ap.add_argument("--row-tolerance", type=float, default=3,
                    help="senkrechter Abstand, bis zu dem Woerter als dieselbe Zeile gelten")
    ap.add_argument("--footer-margin", type=float, default=0,
                    help="Bildpunkte vom unteren Seitenrand, die beim wortbasierten Lesen "
                         "ausgeblendet werden (nur mit --columns), z.B. gegen eine "
                         "Fusszeile mit Produktname/Seitenzahl")
    ap.add_argument("--value-regex", default="",
                    help="Suchmuster, das den Vorgabewert aus der Wert-Zelle holt. Erste Gruppe zaehlt. "
                         "Tams schreibt den Defaultwert in Klammern: --value-regex '\\(([^)]*)\\)'")
    ap.add_argument("--strict-nr", action="store_true",
                    help="CV-Zellen mit Beiwerk verwerfen statt die fuehrende Zahl zu nehmen "
                         "(noetig bei bankabhaengigen CVs wie \"900 A\")")
    ap.add_argument("--append-to", default="",
                    help="Ergebnis in eine vorhandene Vorlage einmischen statt neu anzulegen")
    ap.add_argument("--nr-header", default="",
                    help='Spalten ueber die Ueberschrift zuordnen statt ueber Nummern, z.B. "CV". '
                         'Noetig, wenn die Spaltenzahl je Seite wechselt.')
    ap.add_argument("--description-header", default="")
    ap.add_argument("--value-header", default="")
    ap.add_argument("--type-header", default="")
    ap.add_argument("--name", help="Bezeichnung der Vorlage")
    ap.add_argument("--template-description", default="")
    ap.add_argument("--out", help="Zieldatei (.csv)")
    args = ap.parse_args()

    if args.show_pages:
        show_pages(args.pdf, args.find)
        return
    if not args.pages:
        sys.exit("--pages fehlt (oder --show-pages benutzen)")
    seiten = parse_pages(args.pages)
    if args.inspect:
        inspect(args.pdf, seiten)
        return
    if not args.name or not args.out:
        sys.exit("--name und --out werden zum Schreiben gebraucht")

    spalten = {"nr": args.col_nr, "description": args.col_description,
               "value": args.col_value, "type": args.col_type}
    optionen = {"expand_ranges": args.expand_ranges, "strip_prefix": args.strip_prefix,
                "strip_leading_zeros": args.strip_leading_zeros, "join": args.join,
                "dehyphenate": args.dehyphenate, "pair_stacked": args.pair_stacked,
                "require_header": args.require_header, "last_group": 1,
                "strict_nr": args.strict_nr,
                "columns": [float(x) for x in args.columns.split(",")] if args.columns else None,
                "row_tolerance": args.row_tolerance,
                "footer_margin": args.footer_margin,
                "value_regex": re.compile(args.value_regex) if args.value_regex else None,
                "header_map": ({"nr": args.nr_header, "description": args.description_header,
                                "value": args.value_header, "type": args.type_header}
                               if args.nr_header else None)}

    parameter, hinweise = extract(args.pdf, seiten, spalten, optionen)

    if args.append_to:
        vorhanden, alter_name, alte_beschreibung = lies_vorlage(args.append_to)
        bekannt = {p["nr"] for p in vorhanden}
        neu = [p for p in parameter if p["nr"] not in bekannt]
        doppelt = len(parameter) - len(neu)
        if doppelt:
            hinweise.append(f"{doppelt} CVs stehen schon in {args.append_to} - der vorhandene Eintrag bleibt")
        hinweise.append(f"{len(vorhanden)} CVs aus {args.append_to} uebernommen, {len(neu)} hinzugefuegt")
        parameter = vorhanden + neu

    parameter.sort(key=lambda p: int(p["nr"]))

    # Nicht zugeordnete Symbolzeichen melden, statt sie stillschweigend in die
    # Vorlage zu schreiben - dort erschienen sie als leere Kaestchen.
    offen = set()
    for p in parameter:
        offen.update(unbekannte_symbole(p.get("description", "") + p.get("type", "")))
    if offen:
        hinweise.append("unbekannte Symbolzeichen (bitte in SYMBOLZEICHEN ergaenzen): "
                        + ", ".join(f"U+{ord(c):04X}" for c in offen))

    write_template(args.out, args.name, args.template_description, parameter)

    print(f"{len(parameter)} CVs geschrieben nach {args.out}")
    if parameter:
        nummern = [int(p["nr"]) for p in parameter]
        print(f"  CV {nummern[0]} bis {nummern[-1]}")
        mit_wert = sum(1 for p in parameter if p["value"])
        print(f"  mit Vorgabewert: {mit_wert}, ohne: {len(parameter) - mit_wert}")
    for h in hinweise:
        print("  Hinweis:", h)


if __name__ == "__main__":
    main()
