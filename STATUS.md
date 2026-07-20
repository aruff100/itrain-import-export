# iTrain Import/Export – Projektstatus

Stand: 20.07.2026

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
  "Markierte exportieren") inkl. automatisch mit-exportierter, über
  condition/items verknüpfter Einträge aus anderen Kategorien (siehe unten,
  20.07.2026). Import einer CSV verteilt jede Zeile automatisch in ihre
  eigene (in der CSV angegebene) Kategorie, unabhängig vom aktuell
  geöffneten Reiter.
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
- **Import an falscher Stelle / Datei danach unlesbar**: ursprünglich durch
  eine strikte Kategorie-Validierung behoben (Abbruch, falls CSV-Kategorie
  nicht zum aktuellen Reiter passt). Seit 20.07.2026 ersetzt durch
  automatisches Einsortieren jeder Zeile in ihre eigene, in der CSV
  angegebene Kategorie (siehe unten) - das ist beim jetzt möglichen
  Mehrfach-Kategorie-Export nötig und tatsächlich sicherer, da nicht mehr
  geraten, sondern anhand der expliziten Kategorie-Spalte einsortiert wird.
  Zusätzlich weiterhin durch den verify-then-atomic-write-Ablauf beim
  Speichern abgesichert.
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

## Neu am 20.07.2026 - Oberflächen-Umbau

- **"Daten ändern"-Bereich standardmäßig ausgeblendet**: die Ansicht war
  bisher immer dreigeteilt (Liste | Daten-Explorer-Baum | Daten ändern).
  Der dritte Bereich (bisher "Daten-Einstellen" geheißen, jetzt umbenannt in
  "Daten ändern") erscheint jetzt nur noch, wenn die neue Einstellung
  "Daten ändern" (Voreinstellungen → Ansicht) aktiviert ist - Standard: aus,
  Ansicht ist dann nur zweigeteilt. Technisch: `CategoryEditor` fügt den
  Bereich dem inneren `SplitPane` nur noch bedingt hinzu (SplitPane blendet
  Kinder nicht wie andere Layouts einfach per `setVisible/managed` aus).
- **Voreinstellungen zusammengeführt**: die drei bisherigen Dialoge (Pfade,
  Sprache, Ansicht) sind jetzt Reiter in einem einzigen Fenster
  "Voreinstellungen" (`SettingsDialog.showPreferences`). Im
  "Einstellungen"-Menü gibt es dafür nur noch einen Eintrag
  "Voreinstellungen" statt drei.
- **Neues "Hilfe"-Menü**: "Hilfe (kommt noch)" und "Update (kommt noch)"
  (beide deaktiviert, reine Platzhalter), Trennstrich, "Über". Der
  Über-Dialog (`AboutDialog`) zeigt Programmname "iTrain Import/Export",
  Autor "Andre Ruff" und die Version.
- **Build-Nummer**: `build.gradle.kts` erzeugt bei jedem Build (auch
  `gradlew run`, da `processResources` immer mitläuft) aus der Vorlage
  `build-info.properties` (Platzhalter `${version}`/`${buildTimestamp}`)
  eine frische Datei mit echtem Zeitstempel. `AppInfo.getBuildNumber()`
  liest sie zur Laufzeit und liefert z.B. "1.0-SNAPSHOT (Build
  20260720-1432)"; Fallback auf reine Versionsnummer, falls die Datei aus
  irgendeinem Grund fehlt.
- **Ribbon/Werkzeugleiste**: neue `ToolBar` unter der Menüzeile mit
  Icon-Buttons für Öffnen/Speichern (bestehende Icons wiederverwendet, 22px
  statt 16px) und Voreinstellungen (neues `settings-icon.png`, ein
  einfaches Zahnrad-Icon im selben flachen Stil wie die bestehenden
  Ordner-Icons, per Pillow/Python erzeugt, da kein Bildgenerierungs-Tool in
  dieser Sitzung verfügbar war). Tooltips zeigen den jeweiligen
  Menü-Text. Danach folgt wie bisher die Dateiname-Zeile, danach die
  gewohnte Ansicht (TabPane).
- **Statuszeile erweitert**: zusätzlich zu "Kategorie: N Einträge gelesen"
  jetzt auch "Ausgewählt: N" (Anzahl markierter Zeilen in der aktiven
  Kategorie-Tabelle), reaktiv über eine neue
  `CategoryEditor.selectedCountProperty()`.
- Nebenbei einen alten Textfehler in `translations.properties` (Projekt-
  wurzel-Kopie) bereinigt: eine verirrte Zeile mit dem Wort "claude" stand
  zwischen zwei Übersetzungsblöcken (wurde von der Properties-Datei
  ignoriert, war aber sichtlich falsch).
- Neue/geänderte Dateien: `AppInfo.java` (neu), `AboutDialog.java` (neu),
  `icons/settings-icon.png` (neu), `build-info.properties`-Vorlage (neu),
  `SettingsDialog.java` (komplett umgebaut auf ein TabPane-Fenster),
  `hello-view.fxml` (Ribbon + Hilfe-Menü + reduziertes Einstellungen-Menü),
  `HelloController.java`, `CategoryEditor.java`, `AppSettings.java`,
  `build.gradle.kts`, `dark-theme.css` (`.tool-bar`-Regel ergänzt).
- Wie immer kein echter Compile-Check möglich (kein JDK 21 in der Sandbox).
  Diesmal besonders wichtig zu testen: die neue FXML-Struktur (neue
  `fx:id`s müssen exakt zu den `@FXML`-Feldern passen), der
  `expand()`-Aufruf in `build.gradle.kts` (Kotlin-DSL-Pair-Syntax für
  Gradles `CopySpec.expand`, konnte nicht gegen einen echten Gradle-Build
  verifiziert werden), und ob das Zahnrad-Icon und die Ribbon-Höhe optisch
  passen.

## Neu am 20.07.2026 - Verknüpfte Einträge (condition/items) bei Export/Import

Der Nutzer hat eine echte Referenzdatei `Aktion1.tcdz` geliefert: eine
Aktion (`<action name="Aktion1">`) referenziert in ihrem `<condition>`-Kind
zwei Feedback-Einträge (`<feedback name="RM1" change="on"/>`,
`<feedback name="RM2" .../>`) und in `<items>` einen Signal-Eintrag
(`<signal name="S1" state="hp1"/>`) - diese Referenzen zeigen jeweils per
Tag-Name + `name`-Attribut auf einen vollständigen Eintrag in einer ANDEREN
Kategorie (`feedbacks` bzw. `accessories`). Fehlen diese referenzierten
Einträge in der Zieldatei, lässt sich die Datei danach in iTrain nicht mehr
öffnen. Umgesetzt:

- **Referenz-Auflösung** (`CategoryEditor.resolveLinkedEntries`): sucht zu
  einem Eintrag rekursiv alle `<condition>`/`<items>`-Kindelemente, liest
  darin jedes Element als Referenz (Tag-Name + `name`-Attribut) und sucht
  den tatsächlichen Eintrag dazu **in allen Kategorien** des Dokuments -
  bewusst OHNE feste Tag→Kategorie-Zuordnung (anders als
  `DEFAULT_TAG_BY_CATEGORY`), weil z.B. "accessories" mehrere
  unterschiedliche Tags enthält (turnout/signal/...) und diese Zuordnung
  sich so nicht sauber fest verdrahten lässt. Wird rekursiv auch auf neu
  gefundene Einträge angewendet (falls die selbst wieder condition/items
  hätten). Verifiziert per Python-Simulation gegen die echte
  `Aktion1.tcd`-Struktur: aus der Aktion "Aktion1" ergeben sich exakt die
  erwarteten 3 verknüpften Einträge (RM1, RM2 aus feedbacks; S1 aus
  accessories); ein Block-Eintrag (der zwar auch RM1/RM2/S1/W1 referenziert,
  aber nicht über condition/items, sondern über prev/next/feedbacks) liefert
  bewusst 0 Treffer - der Scope bleibt exakt auf condition/items begrenzt,
  wie vom Nutzer vorgegeben.
- **Export** (`onExport`): berechnet vor der Dateinamens-Eingabe die
  verknüpften Zusatz-Einträge und zeigt einen Bestätigungsdialog: "N
  markierte Datensätze ausgewählt. Der Export wird M zusätzliche
  Datensätze enthalten. Diese Datensätze sind verknüpft und müssen mit
  importiert/exportiert werden." mit den Schaltflächen "Abbruch"/"Weiter"
  (übersetzte `ButtonType`s, nicht die JavaFX-Standardknöpfe, damit der
  Wortlaut exakt stimmt). Erst nach "Weiter" folgt wie bisher die
  Ordner-/Dateinamens-Auswahl. Die CSV enthält danach zusätzliche Zeilen für
  die verknüpften Einträge, jede mit ihrer JEWEILS EIGENEN Kategorie in der
  ersten Spalte (nicht der Kategorie des Reiters, von dem aus exportiert
  wurde).
- **Import** (`onImport`): akzeptiert jetzt CSV-Dateien mit mehreren
  Kategorien in einer Datei. Jede Zeile wird anhand ihrer eigenen
  Kategorie-Spalte einsortiert (`ensureCategoryNodeGeneric`, legt die
  Kategorie bei Bedarf neu an) - unabhängig davon, von welchem Reiter aus
  "Importieren" gedrückt wurde. Berührt der Import Kategorien außer der
  des aktuellen Reiters, wird danach `rebuildTabs()` ausgelöst (neuer
  Konstruktor-Parameter `Runnable onStructuralChange` an `CategoryEditor`,
  in `HelloController` auf `this::rebuildTabs` verdrahtet) - notwendig,
  weil die anderen Reiter sonst weiter an ihren alten (leeren oder
  veralteten) XML-Stand gebunden blieben und die neu importierten Einträge
  nicht anzeigen würden.
- Bewusst NICHT umgesetzt: Duplikat-Erkennung beim Import (existiert ein
  gleichnamiger Eintrag bereits, wird trotzdem eine zweite Kopie angelegt -
  das entspricht dem bisherigen Verhalten beim erneuten Import derselben
  Datei und wurde nicht extra angefasst, da nicht angefordert). Bei Bedarf
  einfach Bescheid geben.
- Neue Übersetzungsschlüssel (alle 6 Sprachen, beide `translations.properties`,
  weiterhin identisch): `editor.exportConfirmTitle`,
  `editor.exportConfirmMessage`, `editor.exportConfirmCancel`,
  `editor.exportConfirmContinue`. Der alte Schlüssel
  `editor.importCategoryMismatch` wird nicht mehr verwendet, aber aus
  Vorsicht nicht gelöscht.
- Geänderte Dateien: `CategoryEditor.java` (neue Methoden
  `resolveLinkedEntries`/`findDescendantsByTag`/`allDescendants`/
  `findEntryByTagAndName`/`findCategoryNameOf`/`ensureCategoryNodeGeneric`/
  `toExportRow`, geänderter Konstruktor, geänderte `onExport`/`onImport`),
  `HelloController.java` (neuer vierter Konstruktor-Parameter beim Anlegen
  der `CategoryEditor`-Instanzen), beide `translations.properties`.
- Wie immer kein echter Compile-Check möglich (kein JDK 21 in der Sandbox) -
  die Referenz-Auflösungslogik wurde stattdessen 1:1 in Python nachgebaut
  und gegen die echte `Aktion1.tcd` verifiziert (siehe oben), der restliche
  Code wurde sorgfältig manuell durchgesehen (Imports, Signaturen, einzige
  Aufrufstelle von `new CategoryEditor(...)` geprüft). **Empfehlung wie
  immer:** einmal `gradlew.bat run` lokal ausführen, besonders um den neuen
  Export-Bestätigungsdialog und den Mehrfach-Kategorie-Import an einer
  echten Datei zu testen.

## Nachtrag 20.07.2026 - Verknüpfungs-Logik generalisiert (nicht mehr nur condition/items)

Nutzer meldete: `Aktion1a.tcdz` (über Export/Import mit der App entstanden) lässt sich
in iTrain nicht öffnen - "Wrong attribute value 'DB Dampf GZ' specified in
attribute 'name' in tag 'train-type'". Geprüft: die Datei referenziert über
sechs `<train>`-Einträge diverse `<train-type name="...">`, aber es gibt
gar keine `train-types`-Kategorie in der Datei. Der Unterschied zu
Aktion1.tcd: `<train-type>` steht direkt unter `<train>`, nicht in einem
`<condition>`/`<items>`-Wrapper - die bisherige Verknüpfungs-Logik
(`resolveLinkedEntries`) hat also gar nicht danach gesucht.

Auf Rückfrage (Bug ja/nein, Umfang erweitern ja/nein) hat der Nutzer
bestätigt: Bug, und die Verknüpfungs-Logik soll generell für ALLE
Name-Referenzen gelten, nicht nur condition/items. Umgesetzt:

- `resolveLinkedEntries` sucht jetzt in der **gesamten** Struktur eines
  Eintrags (alle Nachfahren, keine Beschränkung auf bestimmte umschließende
  Tags mehr) nach Elementen mit `name`-Attribut, deren Tag+Name zu einem
  echten Eintrag einer anderen Kategorie passt - das deckt jetzt u.a.
  train→train-type, block→feedback/signal/turnout (auch außerhalb
  condition/items, z.B. `<block><feedbacks>`/`<prev>`/`<next>`) automatisch
  mit ab, ohne eine feste Liste erlaubter Wrapper-Tags. Die dafür nicht mehr
  gebrauchten Hilfsmethoden `findDescendantsByTag`/`collectByTag` sowie die
  Konstante `REFERENCE_HOLDER_TAGS` wurden entfernt.
- Verifiziert per Python-Simulation gegen beide echten Dateien: gegen
  `Aktion1.tcd` liefert die generalisierte Logik weiterhin exakt dieselben
  3 Einträge (RM1/RM2/S1) wie vorher - keine Regression. Gegen die 6
  Zug-Einträge aus `Aktion1a.tcd` liefert sie jeweils 0 zusätzliche
  Einträge, weil die referenzierten Kategorien (train-types, sowie die
  referenzierten Blöcke B95/B44, Lokomotiven, Wagen, Fahrwege) in DIESER
  Datei an KEINER Stelle im Dokument vorhanden sind - die Logik kann nur
  Einträge mitnehmen, die irgendwo im aktuell geladenen Dokument existieren.
- **Wichtig für den Nutzer**: Der Code-Fix behebt das Grundproblem (fehlende
  Referenzen werden ab jetzt automatisch mit exportiert, wo immer sie im
  Dokument vorhanden sind). Er kann aber `Aktion1a.tcdz` selbst nicht mehr
  reparieren, da diese Datei bereits ohne die referenzierten Daten erzeugt
  wurde - dafür bräuchte es die ursprüngliche Quelldatei (vor dem
  Export/Import), aus der train-types/Loks/Wagen/Fahrwege/Blöcke B95+B44
  tatsächlich stammen. Empfehlung: mit der aktualisierten App erneut aus
  der ORIGINAL-Quelldatei exportieren/importieren und prüfen, ob die
  Zieldatei dann vollständig ist.
- Keine neuen Übersetzungsschlüssel nötig (reine Logik-Änderung, keine neuen
  Texte).
- Geänderte Datei: `CategoryEditor.java`.

## Nachtrag 20.07.2026 - Duplikate beim Import verhindert

Nutzer meldete: `Aktion3.tcdz` lässt sich in iTrain nicht öffnen - "Duplicate
Rückmeldung elements: RM2". Geprüft: die `feedbacks`-Kategorie enthält
tatsächlich zwei vollständige `<feedback name="RM2">`-Definitionen. Das
war genau die in den vorherigen Nachträgen als "bewusst nicht umgesetzt"
vermerkte Lücke: ein per Verknüpfung mit-exportierter Eintrag (z.B. RM2, weil
ein Block/eine Aktion ihn referenziert) wurde beim Import auch dann erneut
eingefügt, wenn im Ziel bereits ein gleichnamiger Eintrag vorhanden war -
iTrain verlangt aber eindeutige Namen pro Kategorie.

Behoben in `onImport()`: vor dem Einfügen wird geprüft
(`categoryHasEntry`), ob die Zielkategorie bereits einen direkten Eintrag
mit demselben Tag-Namen + `name`-Attribut hat; falls ja, wird die Zeile
übersprungen statt dupliziert. Einträge ohne (leeres) `name`-Attribut sind
davon nicht betroffen, da dort kein eindeutiger Schlüssel existiert. Die
Erfolgsmeldung nennt jetzt zusätzlich die Anzahl übersprungener Duplikate
(neuer Übersetzungsschlüssel `editor.importSuccessWithSkipped`, alle 6
Sprachen, beide `translations.properties`). Per Python-Simulation gegen
ein Mini-Szenario (Ziel enthält bereits RM2, CSV bringt RM2 erneut plus
einen echt neuen Eintrag) verifiziert: genau ein Duplikat wird übersprungen,
der neue Eintrag kommt trotzdem an.

Geänderte Datei: `CategoryEditor.java`.

## Nachtrag 20.07.2026 - Referenz-Tag kann von Definitions-Tag abweichen

Nutzer meldete: `Aktion4.tcdz` lässt sich in iTrain nicht öffnen - "Wrong
attribute value 'B67_RS_N' specified in attribute 'name' in tag
'shunt-signal'". Anhand der bereitgestellten Original-Referenzdatei
(`Kellerbahn_20241221.extern.analyse.tcdz`) verifiziert: der Eintrag ist dort
tatsächlich vorhanden, aber unter einem ANDEREN Tag-Namen als die Referenz -
Definition: `<signal name="B67_RS_N" type="de_sh0_1f" state="sh0">` unter
`accessories`; Referenz im Rangier-Kontext eines Blocks:
`<shunt-signal name="B67_RS_N"/>` (an anderer Stelle referenziert derselbe
Block-Baum denselben Eintrag korrekt als `<signal name="B67_RS_N"/>`). Der
bisherige exakte Tag+Name-Abgleich in `resolveLinkedEntries` konnte den
Eintrag deshalb nicht finden - der Reference-Tag "shunt-signal" existiert in
`accessories` schlicht nicht als eigener Tag.

Behoben durch eine neue Fallback-Stufe (`findEntryByReference`): zuerst wird
weiterhin ein exakter Tag+Name-Treffer versucht (der Normalfall); schlägt
der fehl, wird zusätzlich NUR über das `name`-Attribut gesucht (ohne
Tag-Bedingung), da `name` in iTrains Datenmodell der eigentliche eindeutige
Objekt-Identifikator ist. Bewusst NICHT direkt auf reinen Name-Abgleich
umgestellt, da es in echten Dateien tatsächlich kategorieübergreifende
Namensgleichheit gibt (z.B. teilt sich eine Lokomotive mit ihrem Zug oft
denselben Namen, siehe `Kellerbahn_20241221...`: 13 solcher Fälle gefunden,
u.a. Lok+Zug "V36", sowie eine Rückmeldung + eine Drehscheibe, beide
"Drehscheibe" genannt) - der exakte Tag-Treffer hat deshalb weiterhin
Vorrang und wird nur bei einem Fehlschlag durch die Namenssuche ergänzt.

Verifiziert per Python-Simulation gegen die echte Kellerbahn-Datei:
Export von Block "B67" findet jetzt korrekt `B67_RS_N` (als "signal" unter
accessories, via Fallback); die Auflösung von Lok/Zug "V36" bleibt trotz
Namensgleichheit korrekt (exakter Tag-Treffer verhindert Verwechslung). Auch
gegen `Aktion1.tcd` erneut ohne Regression geprüft (weiterhin exakt 3
verknüpfte Einträge).

Geänderte Datei: `CategoryEditor.java` (neue Methode
`findEntryByReference`, neue Methode `findEntryByName`,
`resolveLinkedEntries` ruft jetzt `findEntryByReference` statt direkt
`findEntryByTagAndName` auf).

## Nachtrag 20.07.2026 - "~"-Markierung für mit-importierte, verknüpfte Einträge

Nutzerwunsch: in der Datenansicht (Tabelle je Kategorie-Reiter) sollen
Einträge, die nur wegen einer Referenz eines anderen exportierten Eintrags
mit importiert wurden (siehe `resolveLinkedEntries`), auf einen Blick von
bewusst/direkt exportierten Einträgen unterscheidbar sein - "wichtig ist die
Kategorie selbst" (jeder verknüpfte Eintrag landet ja in SEINER EIGENEN
Kategorie, nicht der des Reiters, von dem aus exportiert wurde). Umgesetzt:

- **`XmlNode`**: neues rein UI-seitiges, nicht persistiertes Flag
  `linkedImport` (Getter/Setter `isLinkedImport()`/`setLinkedImport()`),
  nach demselben Muster wie das bestehende `selected`-Flag - geht beim
  Neuladen der Datei wieder verloren (reine Session-Markierung, kein
  Bestandteil der iTrain-Datei).
- **CSV-Format erweitert** um eine 6. Spalte "Verknüpft" (`editor.columnLinked`,
  alle 6 Sprachen): beim Export bekommen die verknüpften Zusatz-Einträge
  (aus `linkedEntries`) hier ein "1", direkt ausgewählte Einträge bleiben
  leer. Ältere Exporte ohne diese Spalte funktionieren weiterhin (gelten als
  "nicht verknüpft") - der Format-Check verlangt weiterhin nur mindestens 5
  Spalten.
- **Import** setzt `node.setLinkedImport(true)`, wenn die 6. Spalte gesetzt
  ist, bevor der Eintrag eingefügt wird (Duplikate werden wie zuvor vorher
  ausgefiltert und bekommen daher keine neue Markierung, da der bereits
  vorhandene Eintrag gar nicht angefasst wird).
- **Tabellen-Anzeige**: die Name-Spalte in der Einträge-Tabelle stellt bei
  `linkedImport == true` ein `~` vor den Namen (nur optisch, das
  `name`-Attribut selbst bleibt unverändert - wichtig, damit andere
  Referenzen auf genau diesen Namen weiterhin funktionieren). Die Spalte ist
  reiner Anzeige-Text (kein Bearbeitungs-`CellFactory` gesetzt), es besteht
  also keine Gefahr, dass das "~" versehentlich mit ins `name`-Attribut
  übernommen wird.
- Verifiziert per Python-Simulation: Export/Import-Rundlauf mit einer
  direkt gewählten Aktion + zwei verknüpften Einträgen zeigt exakt die
  erwarteten Anzeigenamen ("Aktion1" unverändert, "~RM1"/"~S1" für die
  verknüpften Feedback-/Signal-Einträge in ihren jeweils eigenen
  Kategorien).
- Geänderte Dateien: `XmlNode.java`, `CategoryEditor.java`, beide
  `translations.properties`.

## Nachtrag 20.07.2026 - Toolbar-Umbau + eingebaute Hilfe

- **Toolbar der Kategorie-Ansicht neu geordnet**: links jetzt zuerst
  "Markierte exportieren", dann "Importieren" (vorher: Neuer Eintrag/
  Löschen/Import/Export). "Neuer Eintrag" und "Löschen" sind jetzt reine
  Symbol-Buttons ("+" bzw. rotes "X", per `-fx-text-fill`/`-fx-font-*`
  gestylt statt eigener Icon-Dateien) und stehen rechtsbündig über der
  Tabelle (Spacer-`Region` mit `HBox.setHgrow(..., Priority.ALWAYS)`
  zwischen den beiden Gruppen). Die vollen Bezeichnungen stehen weiterhin
  als Tooltip zur Verfügung.
- **Eingebaute Hilfe**: neue `HelpDialog.java` (analog `AboutDialog.java`),
  erreichbar über Hilfe → Hilfe (der Menüpunkt zeigt jetzt nur noch "Hilfe"
  statt "Hilfe (kommt noch)" und ist nicht mehr deaktiviert). Inhalt: kurze
  Einleitung (wozu das Programm dient - Import/Export einzelner Einträge
  zwischen iTrain-Dateien inkl. automatischem Mitnehmen referenzierter
  Einträge), danach je ein Abschnitt mit Überschrift zu den Menüs "Datei",
  "Einstellungen", "Hilfe" sowie zur Kategorie-Ansicht selbst (Export/
  Import-Workflow, "~"-Markierung, "+"/rotes-X). Komplett in `I18n`
  hinterlegt (`help.introText`, `help.fileMenuTitle`/`Text`,
  `help.settingsMenuTitle`/`Text`, `help.helpMenuTitle`/`Text`,
  `help.categoryViewTitle`/`Text`), alle 6 Sprachen, wechselt also
  automatisch mit der Oberflächensprache.
- Geänderte/neue Dateien: `CategoryEditor.java`, `HelloController.java`
  (neue `onHelp()`-Methode), `hello-view.fxml` (Hilfe-Menüpunkt: `disable`
  entfernt, `onAction="#onHelp"` ergänzt), `HelpDialog.java` (neu), beide
  `translations.properties`.
- Wie immer kein echter Compile-Check möglich (kein JDK 21 in der Sandbox) -
  Klammer-Balance aller geänderten Dateien geprüft, einzige Aufrufstelle von
  `toExportRow(...)` und die neue Toolbar-Struktur manuell durchgesehen.

## Nachtrag 20.07.2026 - Rückgängig/Wiederholen + Referenzen werden beim Export umbenannt

### Rückgängig / Wiederholen (neu)

- **Neues Menü "Bearbeiten"** (zwischen "Datei" und "Einstellungen") mit
  "Rückgängig" und "Wiederholen"; zusätzlich zwei neue Ribbon-Symbole
  (`undo-icon.png`/`redo-icon.png`, gleicher flacher Stil wie die
  bestehenden Icons, per Pillow/Python erzeugt: kreisförmiger Pfeil
  gegen/im Uhrzeigersinn). Menüpunkte und Symbole sind deaktiviert, solange
  der jeweilige Verlauf leer ist.
- **Funktionsweise**: `HelloController` hält zwei Stacks
  (`undoStack`/`redoStack`) aus tiefen Kopien aller Kategorie-Knoten unter
  control-items. Jeder `CategoryEditor` bekommt einen neuen
  Konstruktor-Parameter `Runnable beforeChange` (Aufruf:
  `recordUndoSnapshot()`), der VOR jeder tatsächlichen Änderung aufgerufen
  wird (Eintrag hinzufügen/löschen, Tag-Name-/Textinhalt-Änderung,
  Lokomotiven-Parameter hinzufügen/löschen/ändern, Import - ein kompletter
  Import zählt als EIN Rückgängig-Schritt). "Rückgängig"/"Wiederholen"
  tauschen den kompletten Inhalt von control-items gegen die gesicherte
  Version aus und lösen `rebuildTabs()` aus, damit alle Reiter neu an den
  wiederhergestellten Baum gebunden werden. Der Verlauf wird beim Öffnen
  einer neuen Datei geleert und ist auf 50 Schritte begrenzt.
- Neue Übersetzungsschlüssel (`menu.edit`, `menu.undo`, `menu.redo`,
  `help.editMenuTitle`/`Text`), alle 6 Sprachen.
- Geänderte/neue Dateien: `hello-view.fxml` (neues Menü + 2 Ribbon-Buttons),
  `HelloController.java`, `CategoryEditor.java` (neuer
  Konstruktor-Parameter, `beforeChange.run()` an 9 Mutationsstellen
  ergänzt), `icons/undo-icon.png`/`icons/redo-icon.png` (neu), beide
  `translations.properties`.

### Verknüpfte Referenzen: jetzt per Umbenennung statt nur optischer Markierung

Der Nutzer wollte die zuvor gebaute rein optische "~"-Markierung (Sitzung
zuvor) durch etwas Robusteres ersetzen ("Mache es anders"): beim Export
werden verknüpfte Einträge jetzt tatsächlich UMBENANNT (Original-Name →
"~"+Original-Name) - sowohl ihre eigene Definition als auch JEDE Stelle
innerhalb der exportierten Menge, an der sie referenziert werden. Damit
bleibt die Zieldatei nach dem Import in iTrain durchgehend konsistent und
öffnbar (Referenz und Definition tragen denselben "~"-Namen), auch wenn der
Nutzer die "~"-Namen noch nicht manuell bereinigt hat; das Bereinigen
(Zurückbenennen) macht der Nutzer anschließend selbst in iTrain.

- **`CategoryEditor.onExport()`**: baut aus `resolveLinkedEntries(...)` eine
  `renameMap` (Original-Name → "~"+Original-Name) für alle verknüpften
  Einträge. Für jeden zu exportierenden Eintrag (direkt ausgewählt ODER
  verknüpft) wird eine TIEFE KOPIE angelegt (`XmlNode.deepCopy()` - das
  Original im geöffneten Dokument bleibt unangetastet); auf der Kopie
  werden alle Nachfahren-Referenzen umbenannt (neue Methode
  `renameReferencedChildren`), bei verknüpften Einträgen zusätzlich der
  eigene Name der Kopie selbst. Erst diese umbenannten Kopien werden
  serialisiert und in die CSV geschrieben.
- **CSV-Format**: wieder bei 5 Spalten (die zwischenzeitliche 6. Spalte
  "Verknüpft" aus der vorherigen, jetzt verworfenen Lösung wurde entfernt) -
  die Umbenennung steckt direkt im Namen/XML, keine separate Markierung
  nötig.
- **Import** bleibt unverändert bis auf den Wegfall der (jetzt unnötigen)
  UI-Flag-Logik: die bereits umbenannten Daten werden 1:1 übernommen: die
  Namensspalte der Tabelle zeigt automatisch "~Name", weil das
  `name`-Attribut selbst bereits so lautet.
- **`XmlNode.linkedImport`-Flag entfernt** (war rein UI-seitig, nicht
  persistiert - überflüssig, da die Umbenennung jetzt echte Daten sind).
  `nameColumn` zeigt wieder einfach `getName()`.
- Verifiziert per Python-Simulation gegen die echte `Aktion1.tcd`-Struktur:
  Export von "Aktion1" liefert eine Kopie mit unverändertem eigenem Namen,
  aber intern umbenannten Referenzen (`~RM1`/`~RM2`/`~S1`), sowie drei
  zusätzliche Zeilen für die (jetzt selbst umbenannten) Einträge
  `~RM1`/`~RM2` (feedbacks) und `~S1` (accessories) - exakt konsistent.
- Geänderte Dateien: `CategoryEditor.java`, `XmlNode.java`.

### Hilfedatei ergänzt

- Neuer Abschnitt "Verknüpfte Einträge: Kennzeichnung mit '~'"
  (`help.referenceRenameTitle`/`Text`) erklärt die Umbenennung und dass der
  Nutzer die "~"-Namen anschließend manuell in iTrain zurückbenennen sollte.
- Neuer Abschnitt zum "Bearbeiten"-Menü (`help.editMenuTitle`/`Text`).
- Der bestehende Abschnitt zur Kategorie-Ansicht wurde korrigiert (verwies
  noch auf die alte, rein optische "~"-Tabellenmarkierung) und verweist
  jetzt auf den neuen Abschnitt.
- Geänderte Datei: `HelpDialog.java` (zwei neue Absätze eingefügt), beide
  `translations.properties`.

### Verifikation

Wie immer kein echter Compile-Check möglich (kein JDK 21 in der Sandbox) -
Klammer-Balance aller geänderten Dateien geprüft, einzige Aufrufstelle von
`new CategoryEditor(...)` auf die neue 5-Parameter-Signatur geprüft, keine
verwaisten Referenzen auf das entfernte `linkedImport`-Flag mehr im Code.
**Empfehlung wie immer:** lokal `gradlew.bat run` ausführen und gezielt
testen: Rückgängig/Wiederholen über mehrere verschiedene Änderungsarten
hinweg (Eintrag anlegen/löschen, Text ändern, Import), sowie einen Export
mit verknüpften Referenzen (z.B. `Aktion1.tcdz`) gefolgt von einem Import in
eine andere Datei - die importierten Referenzen sollten dort mit "~" im
Namen erscheinen und die Datei sollte sich in iTrain öffnen lassen.

## Nachtrag 20.07.2026 - Bereitstellung als Installer (Windows/macOS/Linux)

Nutzerwunsch: das Projekt als Download für Windows, macOS und Linux bereitstellen -
sowohl echte native Installer als auch ein automatisierter Build für alle drei
Plattformen. Bestätigt: beides gewünscht, Repository liegt bisher nur lokal vor
(noch kein GitHub-Remote).

- **`build.gradle.kts`**: neuer `jpackage { ... }`-Block innerhalb des
  bestehenden `jlink { ... }`-Blocks (das im Projekt schon vorhandene
  `org.beryx.jlink`-Plugin bringt jpackage-Unterstützung direkt mit, kein
  Zusatz-Plugin nötig). Erzeugt bei Aufruf von `./gradlew jpackage` einen
  echten, plattformspezifischen Installer: `.msi` unter Windows, `.dmg` unter
  macOS, `.deb` unter Linux (jeweils abhängig vom Betriebssystem, auf dem der
  Befehl läuft - `OperatingSystem.current()`). Konfiguriert: `imageName`/
  `installerName` = "iTrain-Import-Export", `vendor` = "Andre Ruff",
  `appVersion` = "1.0.0" (bewusst fest vergeben statt `project.version`
  ("1.0-SNAPSHOT") zu verwenden, da Windows/macOS beim Installer-Bauen ein
  reines Zahlen-/Punkt-Format ohne Suffix verlangen), passendes Icon je
  Plattform sowie `--win-menu`/`--win-shortcut`/`--win-dir-chooser`
  (Windows) bzw. `--linux-shortcut` (Linux). Die genauen Property-Typen
  (`Property<String>` vs. `ListProperty<String>`) wurden vorab anhand der
  echten Plugin-Quelle (`JPackageData.groovy`) geprüft, um nicht wie bei
  früheren Gradle-DSL-Änderungen ins Blaue zu raten.
- **Neue Icons** unter `packaging/icons/` (`app-icon.ico`, `app-icon.icns`,
  `app-icon-256.png`): per Pillow/Python aus dem bestehenden, niedrig
  aufgelösten `app-icon.png` (60×60) auf 512×512 hochskaliert (LANCZOS) und
  jeweils als Multi-Size-`.ico`/`.icns`/einfaches `.png` gespeichert - diese
  liegen bewusst außerhalb von `src/main/resources` (reine Build-Artefakte
  für jpackage, keine Laufzeit-Ressource der App).
- **`.github/workflows/release.yml`** (neu): GitHub-Actions-Workflow, der auf
  drei Runnern parallel baut (`windows-latest`, `macos-latest`,
  `ubuntu-latest`) - notwendig, weil jpackage NICHT plattformübergreifend
  bauen kann (kein Cross-Compiling), jeder Installer muss auf seinem
  Ziel-Betriebssystem entstehen. Jeder Job: JDK 21 einrichten
  (`actions/setup-java@v4`, Temurin), unter Linux zusätzlich `fakeroot`
  installieren (von jpackage für `--type deb` benötigt, auf `ubuntu-latest`
  nicht garantiert vorinstalliert), `./gradlew jpackage --no-daemon`
  ausführen (über `shell: bash`, damit `./gradlew` auf allen drei
  Plattformen gleich funktioniert - unter Windows nutzt der Runner dafür
  automatisch die mitgelieferte Git-Bash), Installer als Artefakt hochladen.
  Auslöser: Push eines Tags `v*` (baut zusätzlich automatisch ein
  GitHub-Release mit allen drei Installern über
  `softprops/action-gh-release@v2`) oder manuell über "Run workflow"
  (`workflow_dispatch`, baut nur die Artefakte zum Testen, ohne Release).
- **Nicht in dieser Sitzung erledigt** (liegt außerhalb dessen, was diese
  Cowork-Sitzung im Namen des Nutzers tun kann): das GitHub-Repository selbst
  anlegen und den lokalen Stand dorthin pushen, sowie ein
  produktives Release-Tag setzen - das erfordert Zugriff auf den
  GitHub-Account des Nutzers.
- Weder `build.gradle.kts` (Gradle-DSL) noch die YAML-Workflow-Datei konnten
  in dieser Sandbox real ausgeführt werden (kein JDK 21, keine GitHub-Actions-
  Umgebung). Die YAML-Datei wurde zumindest per `pyyaml` auf syntaktische
  Gültigkeit geprüft (lädt fehlerfrei). **Empfehlung:** vor dem ersten
  echten Release einmal `gradlew.bat jpackage` lokal unter Windows testen
  (Ergebnis landet unter `build/jpackage/`), und den Workflow zunächst per
  "Run workflow" (`workflow_dispatch`) ohne Tag testen, bevor ein `v*`-Tag
  gepusht und damit ein echtes Release erzeugt wird.
- Geänderte/neue Dateien: `build.gradle.kts`, `.github/workflows/release.yml`
  (neu), `packaging/icons/app-icon.ico`/`.icns`/`app-icon-256.png` (neu).

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
