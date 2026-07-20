# iTrain Import/Export – Projektstatus

Stand: 18.07.2026

## Zweck

JavaFX-Desktop-Anwendung (BellSoft JDK 21, Gradle Kotlin DSL) zum Lesen,
Bearbeiten und Schreiben von iTrain-Modellbahn-Steuerungsdateien
(`.tcd` / gezippt `.tcdz`). Kernregel während der gesamten Entwicklung:
alles außerhalb von `control-items` (settings, clock, control-state,
switchboards) sowie alles innerhalb von `control-items`, das die App nicht
kennt, bleibt beim Laden/Speichern exakt erhalten – es darf nie Daten
verlieren.

## Architektur (Kernklassen)

- **XmlNode** – generischer, reihenfolge-erhaltender XML-Baum (Tag,
  Attribute als `LinkedHashMap`, Kinder als `ObservableList`, Textinhalt,
  UI-Auswahl-Flag). Ersetzt feste Java-Modelle pro Kategorie.
- **TcdDocument** – Laden/Speichern. Kennt die verifizierte Kategorie-
  Reihenfolge (`CONTROL_ITEM_ORDER`, 15 Einträge) sowie die UI-Reiter-
  Reihenfolge (`TAB_DISPLAY_ORDER`, folgt iTrains eigener Kategorie-Ansicht).
  Speichert nur über einen "verify-then-atomic-write"-Ablauf: XML im
  Speicher erzeugen → zur Probe erneut einlesen und Kategorie-Anzahlen
  gegenprüfen (`verifyWellFormed`) → temporäre Datei im Zielordner →
  atomares Umbenennen. So kann nie eine halb geschriebene/fehlerhafte Datei
  entstehen. `.tcdz` wird transparent ent-/wieder gezippt; der interne
  `.tcd`-Eintragsname wird bei JEDEM Speichern frisch aus dem Namen der
  Zieldatei abgeleitet (nicht mehr aus dem ursprünglich geladenen Namen -
  siehe Bugfix vom 18.07.2026).
- **CategoryEditor** – ein Editor pro Kategorie-Reiter. Tabelle aller
  Einträge links (Typ-Spalte und Auswahlbox-Spalte standardmäßig
  ausgeblendet, siehe Einstellungen → Ansicht), rechts der "Daten-Explorer"-
  Baum der Rohdaten (nur Oberbegriff aufgeklappt, mit Verbindungslinien
  zwischen Ober-/Untergruppen) plus darunter "Daten-Einstellen" mit den
  generischen Tag-/Textinhalt-Feldern - außer bei Lokomotiven im
  `configuration`-Knoten, dort erscheint stattdessen eine spezialisierte,
  editierbare Tabelle (Aktiv/Nr/Wert/Typ/Beschreibung, siehe unten). Legt
  das zugehörige XML-Element erst an, wenn tatsächlich ein Eintrag
  hinzugefügt oder importiert wird (`ensureCategoryNode()`) – leere, nie
  benutzte Kategorien werden nie in die Datei geschrieben. Änderungen
  melden sich über einen `onModified`-Callback an `TcdDocument.markDirty()`
  (Grundlage für das automatische Backup-Aufräumen, siehe unten).
- **CsvUtil** – eigener CSV-Reader/-Writer (Semikolon, immer gequotet,
  UTF-8 mit BOM), da Exportzellen komplette mehrzeilige XML-Fragmente
  enthalten können.
- **I18n** – lädt `translations.properties` (Format
  `schluessel.sprachcode=Text`) aus dem Arbeitsverzeichnis (bevorzugt,
  von Hand editierbar) oder ersatzweise aus den Ressourcen. 6 Sprachen:
  de, en, nl, fr, es, it.
- **AppSettings** – persistierte Einstellungen über `java.util.prefs`
  (iTrain-Ordner, Export-Ordner, Farbschema hell/dunkel).
- **SettingsDialog** – drei eigenständige Dialoge: Pfade, Sprache, Ansicht
  (Farbschema).
- **ThemeManager** – wendet `dark-theme.css` auf eine Scene an oder
  entfernt es wieder (heller Modus = Standard-JavaFX-Look).
- **HelloController / hello-view.fxml** – Hauptfenster: Menü "Datei"
  (Öffnen/Speichern, mit Ordner-Icons) und "Einstellungen" (Pfade/Sprache/
  Ansicht als getrennte Menüpunkte), Dateiname links unter dem Menü,
  Statuszeile unten (Anzahl gelesener Einträge der aktiven Kategorie),
  TabPane mit immer allen 15 Kategorie-Reitern.

## Umgesetzte Features

- Laden/Speichern von `.tcd` und `.tcdz`, Speichern-unter über
  Standard-Dateidialog.
- Alle 15 `control-items`-Kategorien immer als Reiter sichtbar (leer,
  falls in der Datei nicht vorhanden); Kategorie wird erst beim ersten
  Eintrag/Import tatsächlich angelegt.
- Neue Einträge bekommen automatisch den für die Kategorie typischen
  Tag-Namen (kein manueller Tag-Dialog mehr).
- Mehrfachauswahl per Checkbox (Maus, Shift, Strg, Leertaste, "Alle").
- Export ausgewählter Einträge als CSV (Rechtsklick oder Button
  "Markierte exportieren"), Import einer CSV in die passende Kategorie
  – mit Kategorie-Abgleich, damit nichts an der falschen Stelle landet.
- Statuszeile mit Anzahl gelesener Einträge je Kategorie.
- Vollständige Übersetzung der Oberfläche in 6 Sprachen inkl.
  Sprachauswahl mit Flaggen-Icons.
- Helles/dunkles Farbschema, umschaltbar unter Einstellungen → Ansicht.
- Programm-Icon plattformübergreifend eingebunden.

## Wichtige Bugfixes (verifiziert anhand echter Dateien)

- **Reihenfolge-Bug ("Wrong attribute value 'ReadyBoost' ... 'booster'")**:
  `reorderChildren()` verschiebt unbekannte Kategorien nicht mehr pauschal
  ans Ende, sondern lässt sie an ihrer ursprünglichen Position (stabiler
  Teil-Sort). Seit der Verifizierung der vollständigen 15er-Reihenfolge
  (siehe unten) ist "boosters" jetzt sogar eine bekannte Kategorie an
  fester Position – ein einfaches Öffnen+Speichern der ursprünglich
  fehlerhaften Datei würde die Position jetzt automatisch korrigieren.
- **Import an falscher Stelle / Datei danach unlesbar**: behoben durch
  Kategorie-Validierung beim Import (Abbruch vor jeder Änderung, falls
  CSV-Kategorie nicht zum aktuellen Reiter passt) sowie durch den
  verify-then-atomic-write-Ablauf beim Speichern.
- **Verifikation nutzte `getElementsByTagName` statt direkter Kinder**:
  konnte bei gleichnamigen, aber verschachtelten Elementen (z.B.
  `<train><actions>`) falsch zählen. Jetzt nur noch direkte Kinder
  (`findDirectChild`).

## Kategorien – verifizierter Stand

Anhand einer vom Nutzer bereitgestellten Referenzdatei mit allen 15
Kategorien wurde die tatsächliche Reihenfolge unter `control-items`
bestätigt:

`functions, interfaces, feedbacks, accessories, memory, boosters, blocks,
train-types, stations, train-routes, locomotives, wagons, trains, actions,
measurements`

Wichtige Korrektur: die zuvor vermutete 15. Kategorie "routes" (Fahrwege)
existiert nicht – tatsächlich heißt sie **memory** (Kind-Element
`<track>`), zwischen accessories und boosters. "Memory" wird von iTrain in
keiner Sprache übersetzt (wie "Booster") und ist in `translations.properties`
entsprechend unübersetzt hinterlegt.

## Neu am 18.07.2026

- **Typ-Spalte** in der Kategorie-Tabelle ist jetzt standardmäßig ausgeblendet
  (redundant, da es ja bereits eine Reiter-Übersicht aller Kategorien gibt).
  Über Einstellungen → Ansicht → "Typ anzeigen" wieder einschaltbar.
- **Auswahlbox-Spalte** (Checkbox vor jedem Eintrag) ebenfalls standardmäßig
  ausgeblendet, über Einstellungen → Ansicht → "Auswahlbox anzeigen"
  einschaltbar. Ist sie ausgeblendet, funktioniert Mehrfachauswahl weiterhin
  ganz normal über die Standard-TableView-Selektion: Maus-Klick, zusätzlich
  Shift (Bereich), zusätzlich Strg (einzelne Einträge dazu/weg) - dafür war
  keine Zusatzlogik nötig, das übernimmt JavaFX selbst.
- Beide neuen Ansicht-Einstellungen wirken sofort: `HelloController` baut nach
  Schließen des Ansicht-Dialogs alle Reiter neu auf (`rebuildTabs()`), genau
  wie beim Sprachwechsel.
- **Neuer Einstellungspunkt "Pfad für Backups"** (Einstellungen → Pfade,
  dritte Zeile, analog zu iTrain-Ordner/Export-Ordner). Ist er gesetzt, wird
  beim Öffnen einer Datei automatisch eine unveränderte 1:1-Kopie des
  Originals dort abgelegt - bevor irgendetwas bearbeitet wird. Ist kein
  Backup-Ordner gesetzt, passiert nichts (kein Zwang zur Nutzung).
  - **Dateiname**: `<originalDateiname>.<N>.bak`, z.B. beim Öffnen von
    `1.tcdz` entsteht `1.tcdz.1.bak`. Wird dieselbe Datei erneut geöffnet,
    zählt N automatisch hoch (2, 3, ...). Nach Erreichen von 10 beginnt die
    Zählung wieder bei 1 (älteste Generation wird überschrieben) - es liegen
    also nie mehr als 10 Backups derselben Datei gleichzeitig im Ordner;
    unterscheidbar sind gleich nummerierte Generationen dann nur noch über
    den Datei-Zeitstempel. Die nächste Nummer wird anhand der zuletzt
    geänderten passenden `.bak`-Datei im Ordner ermittelt (kein separater
    Zähler-Speicher nötig).
  - **Automatisches Aufräumen**: wurde die Datei seit dem Öffnen weder
    geändert noch neu gespeichert, wird ihr Backup beim Öffnen einer anderen
    Datei bzw. beim Schließen des Programms wieder gelöscht (überflüssige
    Sicherheitskopie). Dazu wurde ein einfaches Dirty-Tracking eingeführt:
    `TcdDocument.markDirty()`/`isDirty()`/`wasSavedSinceOpen()`; `save()`
    setzt `wasSavedSinceOpen` automatisch. `CategoryEditor` bekommt dafür
    einen neuen Konstruktor-Parameter `Runnable onModified`, aufgerufen bei
    Eintrag hinzufügen/löschen/importieren sowie bei echter (Wert-)Änderung
    von Tag-Name oder Textinhalt (Vergleich mit dem alten Wert, damit reines
    Durchklicken der Einträge nicht fälschlich als Änderung zählt).
  - **Wichtig, falls nicht wie gewünscht**: "nicht geändert oder neu
    gespeichert" wurde als "weder geändert NOCH gespeichert" interpretiert
    (Backup bleibt erhalten, sobald mindestens eines von beidem zutrifft) -
    bei Bedarf einfach Bescheid geben, falls andere Logik gewünscht ist.
- Neue Übersetzungsschlüssel (alle 6 Sprachen, beide `translations.properties`
  - Projektwurzel und Ressourcen-Kopie, weiterhin identisch):
  `settings.backupPath`, `settings.showType`, `settings.showSelectionCheckbox`,
  `error.backupFailed`.
- **Bugfix .tcdz-Speichern-als**: der interne `.tcd`-Eintragsname im ZIP
  richtete sich bisher nach dem Namen, unter dem die Datei ursprünglich
  geladen wurde (`zipEntryName`-Feld in `TcdDocument`, wiederverwendet beim
  Speichern). Dadurch enthielt z.B. nach "Speichern als 1A.tcdz" (geladen als
  `1.tcdz`) das Archiv weiterhin eine `1.tcd` statt der erwarteten `1A.tcd` -
  beim Entpacken (z.B. mit 7-Zip) erschien also der alte statt der neue Name.
  Behoben: der Eintragsname wird jetzt bei jedem Speichern immer frisch aus
  dem Namen der Zieldatei abgeleitet (`zipEntryName`-Feld komplett entfernt).
- Alle Änderungen wurden per sorgfältigem Code-Review geprüft; die
  Backup-Rotationslogik (1..10, Wraparound, "wer ist die zuletzt benutzte
  Generation") wurde zusätzlich per Python-Simulation gegen zwei Szenarien
  durchgespielt (immer unverändert vs. immer geändert/gespeichert) - beide
  verhalten sich wie vorgesehen. Ein echter Compile-Check war in dieser
  Sitzung nicht möglich: die Sandbox hat kein JDK 21 (Projekt verlangt es per
  Toolchain) und keine Root-Rechte, um es nachzuinstallieren; Gradle selbst
  lief (Wrapper wurde erfolgreich heruntergeladen), scheiterte aber an
  `Cannot find a Java installation ... languageVersion=21`.
  **Empfehlung wie zuvor:** einmal `gradlew.bat run` (oder `build`) lokal bei
  dir ausführen, um einen echten Compile-/Start-Check zu haben - besonders
  wichtig diesmal wegen der neuen Konstruktor-Signatur von `CategoryEditor`
  und des geänderten `HelloApplication`/`HelloController`-Zusammenspiels
  (Fenster-Schließen-Hook).

## Neu, zweite Runde am 18.07.2026

- **"Daten des Eintrags" umbenannt in "Daten-Explorer"** (linker Baum,
  Übersetzungsschlüssel `editor.structureLabel`, alle 6 Sprachen).
- **Rechte Spalte** (Tag/Textinhalt-Felder) hat jetzt fett gedruckt die
  Überschrift **"Daten-Einstellen"** (neuer Schlüssel
  `editor.dataEditorLabel`, alle 6 Sprachen).
- **Verbindungslinien im Daten-Explorer-Baum**: Untergruppen sind jetzt per
  Linie mit ihrer übergeordneten Gruppe verbunden (klassische
  Explorer-Baumdarstellung: `├─`/`└─`/durchgehende `│` für offene
  Geschwister-Äste), umgesetzt über eine neue innere Klasse
  `CategoryEditor.GuideLineTreeCell` (zeichnet die Linien als
  `javafx.scene.shape.Line` in einem kleinen `Pane` vor dem Zeilentext). Die
  Verzweigungslogik (Ebene, "ist letztes Kind", "hat Vorfahre noch weitere
  Geschwister") wurde per Python-Simulation gegen ein Beispiel-Baum
  gegengeprüft und verhält sich korrekt. **Aber**: die exakte Pixel-Höhe
  einer TreeCell-Zeile (`GUIDE_LINE_ROW_HEIGHT = 24.0`) konnte in dieser
  Sandbox nicht am echten JavaFX-Fenster nachgemessen werden - falls die
  Linien bei dir nicht exakt zur Zeilenmitte/-höhe passen, bitte den Wert
  in `CategoryEditor.java` anpassen oder mir Bescheid geben.

## Lokomotiven-Konfigurationstabelle (Bild "Konfiguration") - umgesetzt

Der Nutzer hat einen echten XML-Ausschnitt einer Lokomotive geliefert
(`<locomotive>` mit `<configuration count="19">` und `<parameter nr="..."
type="..." value="..."><description>...</description></parameter>`-Kindern).
Damit wurde die Konfigurationstabelle für die Kategorie "Lokomotiven"
gebaut:

- Wählt man im "Daten-Explorer"-Baum (linke Seite bei einem ausgewählten
  Lokomotiven-Eintrag) den Knoten `configuration` aus, zeigt der rechte
  "Daten-Einstellen"-Bereich statt der generischen Tag-/Textinhalt-Felder
  eine Tabelle mit den Spalten Aktiv (nur informativ, immer angehakt - eine
  Zeile existiert ja nur, wenn ein `<parameter>`-Element wirklich da ist),
  Nr (Attribut `nr`), Wert (Attribut `value`), Typ (Attribut `type`, bewusst
  UNÜBERSETZT wie vom Nutzer vorgegeben - iTrain selbst übersetzt intern
  z.B. "address"→"Adresse", das wird hier NICHT nachgebaut) und
  Beschreibung (Kind-Element `<description>`). Alle vier Datenspalten sind
  direkt in der Tabelle editierbar; fehlt ein Attribut/die Beschreibung im
  Original (z.B. Parameter Nr. 8/21 ohne `value`), bleibt die Zelle leer -
  wird sie leer gespeichert, wird das Attribut/Kind-Element wieder komplett
  entfernt (kein leeres `value=""`).
- Hinzufügen/Löschen einzelner Parameter über zwei Buttons unter der
  Tabelle (fragt beim Hinzufügen nur nach der Nr., der Rest bleibt leer bis
  editiert) - bewusst NICHT über das An-/Abhaken von "Aktiv", um konsistent
  mit dem sonstigen Hinzufügen/Löschen-Muster der Anwendung zu bleiben.
- Nur tatsächlich vorhandene `<parameter>`-Zeilen werden angezeigt - KEIN
  Platzhalter-Raster für alle theoretisch möglichen CV-Nummern (wie in
  iTrains eigenem Bild zu sehen, wo z.B. Nr. 7, 9-20, 22-24 als leere/
  unangehakte Zeilen erscheinen). Der volle gültige Nummernbereich war
  nicht bekannt und wurde bewusst nicht geraten - bei Bedarf einfach den
  maximalen CV-Bereich nennen, dann kann das ergänzt werden.
- Die Mapping-Logik (Nr/Wert/Beschreibung aus den Original-Attributen)
  wurde per Python-Simulation gegen den gelieferten XML-Ausschnitt
  gegengeprüft und stimmt exakt mit den erwarteten Werten überein.
- Andere Kategorien (Wagen, Züge, ...) haben noch keine spezialisierte
  Ansicht - die generische Baum-/Tag-/Textinhalt-Darstellung bleibt dort
  wie gehabt, bis dafür ebenfalls konkrete Vorgaben kommen.

## Bekannte Einschränkungen

- Die Sandbox hat kein JDK 21 (das Projekt verlangt es per Gradle-Toolchain)
  und keine Root-Rechte, um eins nachzuinstallieren (`openjdk-21-jdk-headless`
  wäre per `apt` verfügbar, `sudo` scheitert an fehlenden Privilegien).
  Netzwerkzugriff ist dagegen vorhanden - der Gradle-Wrapper lädt sich
  erfolgreich herunter und startet, scheitert dann aber an
  `Cannot find a Java installation ... languageVersion=21`. Alle Änderungen
  wurden stattdessen durch sorgfältiges manuelles Code-Review sowie durch
  Python-Simulationen der kritischen Algorithmen (Reorder-Logik,
  CSV-Rundtrip, Backup-Rotation, Baumlinien-Verzweigungslogik, Parameter-
  Mapping) gegen echte Dateien/Ausschnitte verifiziert. **Empfehlung:** vor
  dem produktiven Einsatz einmal lokal `gradlew.bat build` bzw.
  `gradlew.bat run` ausführen, um einen echten Compile-/Start-Check zu
  haben - besonders wichtig nach den strukturellen Änderungen vom
  18.07.2026 (neue `CategoryEditor`-Konstruktor-Signatur, neuer
  Fenster-Schließen-Hook, neue Tabellen-Spalten).
- `ZipEntryRow.java` ist eine unbenutzte Altlast aus einer früheren
  Iteration (kein Verweis mehr im Code) – kann bei Gelegenheit entfernt
  werden, ist aber unschädlich.
- Die Höhe der Baumlinien (`GUIDE_LINE_ROW_HEIGHT` in `CategoryEditor.java`)
  ist ein angenommener Wert (24px), nicht am echten Fenster nachgemessen.

## Offene Punkte / mögliche nächste Schritte

- Rückmeldung erwünscht, ob die Baumlinien und die Lokomotiven-
  Konfigurationstabelle bei dir wie erwartet aussehen (siehe die beiden
  Abschnitte oben) - insbesondere die Zeilenhöhe der Baumlinien und ob die
  fehlenden CV-Platzhalterzeilen (Nr. 7, 9-20, 22-24 etc.) doch noch
  ergänzt werden sollen (dafür wird der volle gültige Nummernbereich
  benötigt).
- Spezialisierte Ansichten für weitere Kategorien (Wagen, Züge, ...) -
  analog zur Lokomotiven-Konfigurationstabelle, sobald dafür konkrete
  Vorgaben/Referenzdaten kommen (Bild/Geschwindigkeit/Funktionen/Optionen/
  Erlaubnis/Kommentar wurden für Lokomotiven bereits als Screenshots
  gezeigt, aber noch nicht umgesetzt - nur "Konfiguration" ist fertig).
- Falls gewünscht: `ZipEntryRow.java` aufräumen, echten Gradle-Build einmal
  durchlaufen lassen, ggf. weitere Kategorien-Beispieldateien gegenprüfen
  (train-types, stations kamen in den bisher hochgeladenen Dateien kaum
  vor).
