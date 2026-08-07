<!--
  Ergaenzung fuer den GIST zu iTrain Import/Export, Stand Version 2.0.
  Die drei Abschnitte unten sind zum Anhaengen bzw. Ersetzen gedacht:
  "Decoder-Vorlagen", "Fenster und Bildschirme", "Handbuch".
-->

## Decoder-Vorlagen (neu in 2.0)

Eine Decoder-Vorlage ist die CV-Tabelle eines Decoder-Typs, gespeichert als
`.csv`. Statt jede CV von Hand einzutippen, übernimmst du eine Vorlage in eine
Lokomotive oder einen Wagen und passt nur noch an, was bei deinem Fahrzeug
abweicht.

**Die drei Wege** (Werkzeugleiste, nur bei Lokomotiven und Wagen):

- **Decoder importieren** – eine gespeicherte Vorlage in das markierte Fahrzeug
  übernehmen.
- **Decoder exportieren** – die CV-Werte des markierten Fahrzeugs als neue
  Vorlage sichern. Die Datei lässt sich weitergeben (E-Mail) und anderswo
  wieder einlesen.
- **In eigenem Fenster bearbeiten** – die CV-Tabelle des Fahrzeugs direkt
  ändern, ergänzen und aufräumen.

Dazu **Decoder erfassen**: eine neue Vorlage neben der geöffneten
Hersteller-Anleitung anlegen. Die Werte kommen per Zwischenablage aus deinem
PDF-Programm; *Ab hier einfügen* verteilt eine größere Markierung auf mehrere
Zeilen.

**31 fertige Vorlagen** liegen bei – ESU, ZIMO, Uhlenbrock, Tams, Märklin,
Lenz, D&H, PIKO, mXion und der NEM-Standard.

**In der CV-Tabelle** ist die Spalte *Nr.* jetzt die Zeile: Zeile 8 ist CV 8,
fest und nicht eintippbar. Eingelesene Vorlagen landen in der Zeile ihrer
CV-Nummer. Was in die Vorlage wandert, entscheidet der Haken *Aktiv* – er
setzt sich selbst, sobald in Wert, Typ oder Beschreibung etwas steht. Ein
Doppelklick auf die Beschreibung öffnet den Text in einem eigenen Fenster mit
Zeilenumbruch.

**Nicht jeder Decoder nimmt jede Vorlage.** Massgeblich ist das Protokoll beim
Fahrzeug: dcc, fmz, ctc und sx2 übernehmen alles; sx1 und Selectrix nur die
ersten fünf Werte; mot, multi und analog kennen keine CVs – dort wird der
Import abgewiesen.

**Wagen ohne Decoder:** Viele Wagen haben in iTrain gar keinen Decoder
eingetragen. Nur die Konfiguration einzufügen genügt dort nicht – ohne
zugehörigen Decoder zeigt iTrain die CV-Werte überhaupt nicht an. Das Programm
fragt nach und legt den Decoder auf Wunsch mit an; das Protokoll wählst du aus.

**Wichtig:** Das Programm schreibt nichts auf den Decoder und liest nichts aus
ihm. Geändert wird ausschließlich die iTrain-Datei – das Aufspielen bleibt
Sache von iTrain oder deiner Zentrale. Die mitgelieferten Vorlagen sind per
Skript aus Hersteller-PDFs abgeleitet; ihr Inhalt muss nicht stimmen. Und:
Die Digital-Adresse (CV 1, bei langer Adresse CV 17/18) nach dem Einlesen
prüfen, sonst fahren mehrere Loks auf derselben Adresse.

**Hinweistext zum Selbstschreiben:** Vor der ersten Decoder-Bearbeitung
erscheint ein Hinweisfenster. Sein Text steht in
`decoder-hints_<sprache>.txt` neben dem Programm und lässt sich frei
bearbeiten – Änderungen wirken sofort, ohne Neuinstallation. Abschalten im
Fenster selbst, wieder einschalten unter *Voreinstellungen → Ansicht*.

## Fenster und Bildschirme (neu in 2.0)

- Größe und Position werden **je Fenster** gemerkt – Hauptfenster,
  Erfassungsfenster, Konfigurationsfenster und Vorlagen-Übersicht getrennt.
- *Voreinstellungen → Ansicht → Bildschirm*: neue Fenster erscheinen wie
  zuletzt oder immer auf einem festen Monitor. Bei nur einem Bildschirm ist
  die Auswahl abgeschaltet.
- **Dialoge erscheinen über dem Hauptfenster** statt irgendwo auf dem
  Hauptbildschirm des Systems. Verschiebst du einen Dialog, merkt sich das
  Programm den Abstand zur Mitte des Hauptfensters – nicht die feste
  Bildschirmposition, denn das Hauptfenster kann später woanders stehen.
- **Bearbeiten → Alle Fenster zentrieren** holt alles auf den Bildschirm des
  Hauptfensters zurück und vergisst dabei die gemerkte Verschiebung. Das ist
  der Ausweg, wenn ein Fenster nach dem Abziehen eines Monitors oder einer
  Auflösungsänderung unauffindbar ist.
- Das **dunkle Farbschema** gilt jetzt für alle Dialoge und Zusatzfenster und
  wird beim Umschalten auf bereits offene Fenster mitgezogen.

## Handbuch

Das Handbuch gibt es auf **Deutsch und Englisch**, jeweils 15 Abschnitte –
neu darin: *Fensterverwaltung und mehrere Bildschirme* sowie *Hinweise zur
Decoder-Funktion*. Erreichbar über **Hilfe → Handbuch**.

Dieselben Texte stecken als eingebaute Hilfe im Programm (**Hilfe → Hilfe**),
in **zehn Sprachen**: Deutsch, Englisch, Niederländisch, Französisch,
Spanisch, Italienisch, Portugiesisch, Polnisch, Dänisch, Schwedisch.
