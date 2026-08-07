#!/usr/bin/env python3
"""
Erzeugt die Handbuecher (.odt) aus den Hilfetexten.

    python3 tools/manual_build.py de
    python3 tools/manual_build.py en
    python3 tools/manual_build.py de en

WARUM AUS DEN HILFETEXTEN
-------------------------
Handbuch und eingebaute Hilfe beschreiben dasselbe Programm. Zwei getrennt
gepflegte Fassungen laufen unweigerlich auseinander - genau das war schon
einmal der Fall (siehe STATUS.md, "Hilfetext hinkt dem Programm hinterher").
Quelle ist deshalb translations.properties; das Handbuch ist daraus erzeugt.

Der bearbeitbare Umweg fuer den deutschen Text ist Hilfetext-bearbeiten.txt
(siehe tools/help_text_sync.py). Reihenfolge:

    Hilfetext-bearbeiten.txt  --import-->  translations.properties (de)
    translations.properties   --uebersetzen (Handarbeit)-->  9 Sprachen
    translations.properties   --manual_build-->  .odt

WIE DAS DOKUMENT ENTSTEHT
-------------------------
Nicht von Grund auf: Ein .odt ist ein ZIP mit mehreren XML-Dateien, und
Formatvorlagen, Seitenaufbau und Schriften von Hand zu erzeugen waere viel
Arbeit fuer ein schlechteres Ergebnis. Stattdessen wird das **vorhandene**
Dokument als Geruest genommen; ersetzt wird nur der Fliesstext in
content.xml. Alles andere - styles.xml, Titelseite, Kopf- und Fusszeilen,
Schriftarten - bleibt unangetastet.

Verwendete Vorlagen (aus dem bestehenden Dokument abgelesen):
    P19            Ueberschrift Ebene 1
    P13            Fliesstext
    WWNum2/P14     Aufzaehlung
    T4             Fettdruck innerhalb eines Absatzes

INHALTSVERZEICHNIS
------------------
Das Verzeichnis bleibt als Feld erhalten und wird beim Neuaufbau geleert.
LibreOffice fuellt es beim Oeffnen ueber "Extras -> Aktualisieren -> Alles
aktualisieren" bzw. beim Ausdruck selbst. Es hier zu berechnen hiesse,
Seitenzahlen zu raten, die erst beim Umbruch feststehen.
"""

import io
import os
import re
import shutil
import sys
import zipfile
from xml.sax.saxutils import escape

PROPERTIES = "translations.properties"

MANUALS = {
    "de": "iTrain-Import-Export-Handbuch.odt",
    "en": "iTrain-Import-Export-Manual-EN.odt",
}

# Reihenfolge wie im Hilfefenster, siehe HelpDialog.show().
ORDER = ["intro", "fileMenu", "editMenu", "settingsMenu", "helpMenu", "update",
         "selection", "categoryView", "explorer", "decoder", "decoderHints",
         "capture", "windows", "referenceRename", "statusBar"]

STYLE_H1 = "P19"
STYLE_BODY = "P13"
STYLE_LIST_ITEM = "P14"
STYLE_LIST = "WWNum2"
STYLE_BOLD = "T4"


def read_help(language):
    """Alle help.*-Texte einer Sprache: {name: (titel, text)}."""
    werte = {}
    for zeile in io.open(PROPERTIES, encoding="utf-8"):
        zeile = zeile.rstrip("\n")
        if not zeile.startswith("help.") or "=" not in zeile:
            continue
        k, v = zeile.split("=", 1)
        if k.endswith("." + language):
            werte[k[:-(len(language) + 1)]] = v.replace("\\n", "\n")
    abschnitte = {}
    for name in ORDER:
        text = werte.get(f"help.{name}Text")
        if text is None:
            continue
        abschnitte[name] = (werte.get(f"help.{name}Title", ""), text)
    return abschnitte


def inline(zeile):
    """**fett** in <text:span>-Laeufe uebersetzen, Rest maskieren."""
    teile = []
    rest = zeile
    while True:
        start = rest.find("**")
        if start < 0:
            teile.append(escape(rest))
            break
        ende = rest.find("**", start + 2)
        if ende < 0:
            teile.append(escape(rest))
            break
        teile.append(escape(rest[:start]))
        fett = rest[start + 2:ende]
        if fett:
            teile.append(f'<text:span text:style-name="{STYLE_BOLD}">{escape(fett)}</text:span>')
        else:
            teile.append("****")
        rest = rest[ende + 2:]
    return "".join(teile)


def body_xml(abschnitte):
    """Baut den Fliesstext-Teil: je Abschnitt eine H1 und die Absaetze."""
    aus = []
    for index, name in enumerate(ORDER):
        if name not in abschnitte:
            continue
        titel, text = abschnitte[name]
        marke = f"__RefHeading___Manual{index}_1"
        if titel:
            aus.append(
                f'<text:h text:style-name="{STYLE_H1}" text:outline-level="1">'
                f'<text:bookmark-start text:name="{marke}"/>{escape(titel)}'
                f'<text:bookmark-end text:name="{marke}"/></text:h>')

        absatz = []          # gesammelte Zeilen eines Fliesstext-Absatzes
        punkte = []          # gesammelte Aufzaehlungspunkte

        def absatz_schliessen():
            if absatz:
                inhalt = "<text:line-break/>".join(inline(z) for z in absatz)
                aus.append(f'<text:p text:style-name="{STYLE_BODY}">{inhalt}</text:p>')
                absatz.clear()

        def liste_schliessen():
            if punkte:
                eintraege = "".join(
                    f'<text:list-item><text:p text:style-name="{STYLE_LIST_ITEM}">'
                    f'{inline(p)}</text:p></text:list-item>' for p in punkte)
                aus.append(f'<text:list text:style-name="{STYLE_LIST}">{eintraege}</text:list>')
                punkte.clear()

        for roh in text.replace("\r\n", "\n").split("\n"):
            zeile = roh.strip()
            if not zeile:
                absatz_schliessen()
                liste_schliessen()
                continue
            if zeile.startswith("- "):
                absatz_schliessen()
                punkte.append(zeile[2:].strip())
                continue
            liste_schliessen()
            absatz.append(zeile)
        absatz_schliessen()
        liste_schliessen()
    return "".join(aus)


def rebuild(language):
    ziel = MANUALS[language]
    if not os.path.isfile(ziel):
        sys.exit(f"{ziel} nicht gefunden - das vorhandene Dokument dient als Geruest.")

    abschnitte = read_help(language)
    fehlend = [n for n in ORDER if n not in abschnitte]

    with zipfile.ZipFile(ziel) as z:
        eintraege = {n: z.read(n) for n in z.namelist()}
    content = eintraege["content.xml"].decode("utf-8")

    kopf = re.search(r"(<office:text[^>]*>)(.*)(</office:text>)", content, re.S)
    if not kopf:
        sys.exit("content.xml: <office:text> nicht gefunden")
    alt = kopf.group(2)

    # Alles bis einschliesslich Inhaltsverzeichnis behalten: Titelseite,
    # Sequenzdeklarationen und der Verzeichnis-Abschnitt stehen dort. Der
    # Fliesstext beginnt bei der ersten Ueberschrift der Ebene 1 NACH dem
    # Verzeichnis.
    ende_verzeichnis = alt.find("</text:table-of-content>")
    if ende_verzeichnis >= 0:
        schnitt = ende_verzeichnis + len("</text:table-of-content>")
    else:
        erste = re.search(r'<text:h [^>]*text:outline-level="1"', alt)
        schnitt = erste.start() if erste else 0
    vorspann = alt[:schnitt]

    # Verzeichnis leeren - die Seitenzahlen stimmen nach dem Neuaufbau nicht
    # mehr. LibreOffice fuellt es beim Aktualisieren neu.
    vorspann = re.sub(r"(<text:index-body>).*?(</text:index-body>)",
                      r"\1\2", vorspann, flags=re.S)

    neu = kopf.group(1) + vorspann + body_xml(abschnitte) + kopf.group(3)
    eintraege["content.xml"] = (content[:kopf.start()] + neu + content[kopf.end():]).encode("utf-8")

    shutil.copy2(ziel, ziel + ".bak")
    with zipfile.ZipFile(ziel, "w", zipfile.ZIP_DEFLATED) as z:
        # mimetype muss als erster Eintrag und unkomprimiert stehen, sonst
        # erkennen manche Programme das Format nicht.
        if "mimetype" in eintraege:
            z.writestr("mimetype", eintraege.pop("mimetype"), zipfile.ZIP_STORED)
        for name, daten in eintraege.items():
            z.writestr(name, daten)

    print(f"{ziel}: {len(abschnitte)} Abschnitte geschrieben "
          f"(Sicherung: {os.path.basename(ziel)}.bak)")
    if fehlend:
        print("  ohne Text in dieser Sprache:", ", ".join(fehlend))
    print("  Inhaltsverzeichnis in LibreOffice einmal aktualisieren "
          "(Extras -> Aktualisieren -> Alles aktualisieren).")


if __name__ == "__main__":
    sprachen = sys.argv[1:] or ["de", "en"]
    for s in sprachen:
        if s not in MANUALS:
            sys.exit(f"Keine Handbuch-Datei fuer '{s}' bekannt: {sorted(MANUALS)}")
        rebuild(s)
