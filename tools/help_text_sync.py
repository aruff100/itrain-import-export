#!/usr/bin/env python3
"""
Haelt Hilfetext-bearbeiten.txt und translations.properties zusammen.

Die eingebaute Hilfe steht in translations.properties - eine Zeile je
Sprache und Schluessel, mit \\n statt echter Zeilenumbrueche. Zum Schreiben
laengerer Absaetze ist das unbrauchbar. Deshalb gibt es
Hilfetext-bearbeiten.txt: dieselben Texte, deutsch, als lesbare Datei.

Zwei Richtungen:

  export  translations.properties  ->  Hilfetext-bearbeiten.txt
          Baut die bearbeitbare Datei aus dem aktuellen Stand neu auf.
          Nach einer Aenderung an der Hilfe im Programm hier aufrufen,
          damit die Datei nicht veraltet.

  import  Hilfetext-bearbeiten.txt  ->  translations.properties
          Schreibt die deutschen Texte zurueck. Andere Sprachen bleiben
          unberuehrt - die Uebersetzung ist Handarbeit und passiert
          getrennt davon.

  diff    zeigt nur an, welche Abschnitte sich unterscheiden. Fuer die
          Frage "was hat sich seit dem letzten Mal geaendert, was muss
          also uebersetzt und ins Handbuch uebernommen werden".

Aufruf:
    python3 tools/help_text_sync.py export
    python3 tools/help_text_sync.py diff
    python3 tools/help_text_sync.py import
"""

import io
import re
import sys

PROPERTIES = "translations.properties"
RESOURCE_COPY = "src/main/resources/com/example/itrain_import_export/translations.properties"
EDIT_FILE = "Hilfetext-bearbeiten.txt"

# Reihenfolge wie im Hilfefenster, siehe HelpDialog.show(). Wird beim Export
# verwendet; beim Import zaehlt allein, was in der Datei steht.
ORDER = ["intro", "fileMenu", "editMenu", "settingsMenu", "helpMenu", "update",
         "selection", "categoryView", "explorer", "decoder", "decoderHints",
         "capture", "windows", "referenceRename", "statusBar"]

KOPF = """# ===========================================================================
#  Hilfetext des Programms - zum Bearbeiten
# ===========================================================================
#
#  Das hier ist die deutsche Fassung der eingebauten Hilfe (Menue Hilfe ->
#  Hilfe). Bearbeite den Text nach Belieben; die Uebersetzung in die neun
#  anderen Sprachen und die Anpassung des Handbuchs wird daraus erstellt.
#
#  AUFBAU
#  ------
#  Ein Abschnitt beginnt mit einer Zeile
#        === schluessel ===
#  Danach folgen zwei Bloecke:
#        TITEL: <die fette Ueberschrift im Hilfefenster>
#        TEXT:
#        <der Fliesstext, beliebig viele Zeilen>
#
#  Den Schluessel bitte NICHT aendern - daran wird der Abschnitt beim
#  Zurueckschreiben wiedererkannt. Reihenfolge und Anzahl der Abschnitte
#  duerfen sich aendern; neue Abschnitte einfach mit einem neuen Schluessel
#  in Kleinbuchstaben anlegen.
#
#  AUSZEICHNUNG IM TEXT
#  --------------------
#     **fett**          hebt eine Stelle hervor
#     "- " am Anfang    macht aus der Zeile einen Aufzaehlungspunkt
#     Leerzeile         beginnt einen neuen Absatz
#     einfacher Umbruch bleibt ein Umbruch im selben Absatz
#
#  Zeilen, die mit # beginnen, sind Kommentare und erscheinen nicht in der
#  Hilfe. Innerhalb einer Zeile ist # ein gewoehnliches Zeichen.
#
#  Der Abschnitt "intro" hat keine Ueberschrift - dort bleibt TITEL leer.
#
# ===========================================================================
"""


def lies_properties(pfad=PROPERTIES):
    """Alle help.*-Eintraege je Sprache: {schluessel: {sprache: text}}."""
    tabelle = {}
    for zeile in io.open(pfad, encoding="utf-8"):
        zeile = zeile.rstrip("\n")
        if not zeile.startswith("help.") or "=" not in zeile:
            continue
        k, v = zeile.split("=", 1)
        schluessel, sprache = k.rsplit(".", 1)
        tabelle.setdefault(schluessel, {})[sprache] = v
    return tabelle


def lies_editdatei(pfad=EDIT_FILE):
    """
    Abschnitte aus der bearbeitbaren Datei: {name: (titel, text)}.

    Kommentarzeilen fallen weg. Das Rautezeichen muss dafuer am
    Zeilenanfang stehen - mitten im Text ist es ein gewoehnliches Zeichen.
    """
    abschnitte = {}
    name = None
    titel = ""
    text_zeilen = []
    im_text = False

    def abschliessen():
        if name is not None:
            abschnitte[name] = (titel, "\n".join(text_zeilen).strip())

    for roh in io.open(pfad, encoding="utf-8"):
        zeile = roh.rstrip("\n")
        if zeile.startswith("#"):
            continue
        kopf = re.match(r"^===\s*(\S+)\s*===\s*$", zeile)
        if kopf:
            abschliessen()
            name = kopf.group(1)
            titel = ""
            text_zeilen = []
            im_text = False
            continue
        if name is None:
            continue
        if not im_text and zeile.startswith("TITEL:"):
            titel = zeile[len("TITEL:"):].strip()
            continue
        if not im_text and zeile.strip() == "TEXT:":
            im_text = True
            continue
        if im_text:
            text_zeilen.append(zeile)
    abschliessen()
    return abschnitte


def export():
    tabelle = lies_properties()
    teile = [KOPF]
    fehlend = []
    for name in ORDER:
        titel = tabelle.get(f"help.{name}Title", {}).get("de", "")
        text = tabelle.get(f"help.{name}Text", {}).get("de")
        if text is None:
            fehlend.append(name)
            continue
        teile.append(f"=== {name} ===")
        teile.append(f"TITEL: {titel}")
        teile.append("TEXT:")
        teile.append(text.replace("\\n", "\n"))
        teile.append("")
    io.open(EDIT_FILE, "w", encoding="utf-8").write("\n".join(teile).rstrip() + "\n")
    print(f"{EDIT_FILE} neu geschrieben, {len(ORDER) - len(fehlend)} Abschnitte")
    if fehlend:
        print("  ohne Text in translations.properties:", ", ".join(fehlend))


def unterschiede():
    """Abschnitte, deren deutscher Text von der Properties-Datei abweicht."""
    tabelle = lies_properties()
    abschnitte = lies_editdatei()
    geaendert = []
    for name, (titel, text) in abschnitte.items():
        alt_titel = tabelle.get(f"help.{name}Title", {}).get("de", "")
        alt_text = (tabelle.get(f"help.{name}Text", {}).get("de") or "").replace("\\n", "\n")
        if titel != alt_titel or text != alt_text:
            geaendert.append((name, titel != alt_titel, text != alt_text))
    return geaendert


def diff():
    geaendert = unterschiede()
    if not geaendert:
        print("Keine Unterschiede - Hilfetext-bearbeiten.txt und translations.properties (de) sind gleich.")
        return
    print("Geaendert gegenueber translations.properties (deutsch):")
    for name, titel_neu, text_neu in geaendert:
        was = " + ".join(x for x, ja in (("Titel", titel_neu), ("Text", text_neu)) if ja)
        print(f"  {name}: {was}")
    print()
    print("Diese Abschnitte muessen in die uebrigen neun Sprachen uebersetzt")
    print("und im Handbuch nachgezogen werden.")


def importieren():
    abschnitte = lies_editdatei()
    zeilen = io.open(PROPERTIES, encoding="utf-8").read().split("\n")

    ersetzt, neu = [], []
    for name, (titel, text) in abschnitte.items():
        for art, wert in (("Title", titel), ("Text", text)):
            schluessel = f"help.{name}{art}.de"
            # Zeilenumbrueche zurueck in die Properties-Schreibweise
            wert_flach = wert.replace("\n", "\\n")
            neue_zeile = f"{schluessel}={wert_flach}"
            for i, zeile in enumerate(zeilen):
                if zeile.startswith(schluessel + "="):
                    if zeile != neue_zeile:
                        zeilen[i] = neue_zeile
                        ersetzt.append(schluessel)
                    break
            else:
                zeilen.append(neue_zeile)
                neu.append(schluessel)

    io.open(PROPERTIES, "w", encoding="utf-8").write("\n".join(zeilen))
    io.open(RESOURCE_COPY, "w", encoding="utf-8").write("\n".join(zeilen))
    print(f"{len(ersetzt)} Eintraege geaendert, {len(neu)} neu angelegt.")
    for s in ersetzt + neu:
        print("  ", s)
    print()
    print("Beide Kopien der translations.properties wurden geschrieben.")
    print("ACHTUNG: Nur Deutsch. Die neun anderen Sprachen und das Handbuch")
    print("muessen noch nachgezogen werden.")


if __name__ == "__main__":
    befehl = sys.argv[1] if len(sys.argv) > 1 else "diff"
    if befehl == "export":
        export()
    elif befehl == "import":
        importieren()
    elif befehl == "diff":
        diff()
    else:
        sys.exit(__doc__)
