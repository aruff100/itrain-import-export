# iTrain Import/Export 2.0

Der Schwerpunkt dieser Fassung sind die **Decoder-Vorlagen**: CV-Tabellen eines
Decoder-Typs lassen sich speichern, weitergeben und in Lokomotiven und Wagen
übernehmen, ohne jede CV von Hand einzutippen.

## Decoder-Vorlagen

- **Decoder importieren / exportieren** bei Lokomotiven und Wagen. Der
  `configuration`-Bereich eines Fahrzeugs wird als Vorlage gespeichert
  (`.csv`) und lässt sich in andere Fahrzeuge einlesen - auch in anderen
  Anlagendateien, per E-Mail weitergegeben oder aus dem Vorlagenpaket.
- **Konfiguration bearbeiten**: die CV-Tabelle eines Fahrzeugs in einem
  eigenen Fenster ändern, ergänzen und aufräumen.
- **Decoder erfassen**: neue Vorlagen neben der geöffneten Hersteller-Anleitung
  anlegen. Werte werden aus dem PDF-Programm über die Zwischenablage
  übernommen; **Ab hier einfügen** verteilt eine größere Markierung auf
  mehrere Zeilen.
- **31 fertige Vorlagen** liegen bei (ESU, ZIMO, Uhlenbrock, Tams, Märklin,
  Lenz, D&H, PIKO, mXion, NEM-Standard).

### Was sich in der CV-Tabelle geändert hat

- Die Spalte **Nr. ist jetzt die Zeile**: Zeile 8 ist CV 8, dauerhaft und
  nicht mehr eintippbar. Eingelesene Vorlagen landen in der Zeile ihrer
  CV-Nummer, Lücken bleiben leer.
- Welche Zeilen in die Vorlage wandern, entscheidet allein **Aktiv** - der
  Haken setzt sich selbst, sobald in Wert, Typ oder Beschreibung etwas steht.
- **Doppelklick auf die Beschreibung** öffnet den Text in einem eigenen
  Fenster mit Zeilenumbruch. Beschreibungen aus Anleitungen sind oft länger,
  als die Spalte zeigen kann.
- Ein einfacher Klick öffnet die Eingabe mit Rahmen, weißem Grund und
  Schreibcursor.

### Protokollregeln

Massgeblich ist das Protokoll, das beim Fahrzeug unter `decoder` steht:

| Protokoll | Import |
|---|---|
| dcc, fmz, ctc, sx2 | alle Parameter |
| sx1, Selectrix | nur die ersten fünf |
| mot, multi, analog | kein Import |

### Wagen ohne Decoder

Viele Wagen haben in iTrain gar keinen Decoder eingetragen. Dort genügt es
**nicht**, nur die Konfiguration einzufügen - ohne zugehörigen Decoder zeigt
iTrain die CV-Werte überhaupt nicht an. Das Programm fragt jetzt nach und legt
auf Wunsch den Decoder gleich mit an; das Protokoll wählst du dabei aus.

### Hinweise, die du selbst schreibst

Vor der ersten Decoder-Bearbeitung erscheint ein Hinweisfenster. Sein Text
steht in `decoder-hints_<sprache>.txt` neben dem Programm und lässt sich frei
bearbeiten - Änderungen wirken sofort. Abschalten im Fenster selbst, wieder
einschalten unter *Voreinstellungen → Ansicht*.

## Fenster und mehrere Bildschirme

- Größe und Position werden **je Fenster** gemerkt.
- *Voreinstellungen → Ansicht → Bildschirm*: neue Fenster erscheinen wie
  zuletzt oder immer auf einem festen Monitor.
- **Dialoge erscheinen über dem Hauptfenster** statt irgendwo auf dem
  Hauptbildschirm des Systems. Verschiebst du einen Dialog, merkt sich das
  Programm den Abstand zur Fenstermitte.
- **Bearbeiten → Alle Fenster zentrieren** holt alles zurück - der Ausweg,
  wenn ein Fenster nach dem Abziehen eines Monitors unauffindbar ist.
- Das dunkle Farbschema gilt jetzt für **alle** Dialoge und Zusatzfenster und
  wird beim Umschalten auf offene Fenster mitgezogen.

## Handbuch und Hilfe

Zwei neue Abschnitte in Hilfe und Handbuch: **Fensterverwaltung und mehrere
Bildschirme** sowie **Hinweise zur Decoder-Funktion**. Beide Handbücher
(deutsch und englisch) sind neu erzeugt und umfassen 15 Abschnitte.

Die Hilfe gibt es in zehn Sprachen: de, en, nl, fr, es, it, pt, pl, da, sv.

## Hinweis

Das Programm schreibt **nichts** auf den Decoder und liest **nichts** aus ihm.
Geändert wird ausschließlich die iTrain-Datei; das Aufspielen auf das Fahrzeug
übernimmt weiterhin iTrain oder deine Zentrale. Die mitgelieferten Vorlagen
sind per Skript aus Hersteller-PDFs abgeleitet - ihr Inhalt muss nicht stimmen.
