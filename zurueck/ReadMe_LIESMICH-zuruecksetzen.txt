===============================================================================
 iTrain Import/Export - Einstellungen zuruecksetzen
 Stand: Version 2.0 (August 2026)
 Anleitung DEUTSCH (weiter unten: ENGLISH)
===============================================================================

WOFUER SIND DIESE DATEIEN?
--------------------------
iTrain Import/Export merkt sich Ihre Einstellungen dauerhaft:

  Pfade
    - Pfad fuer iTrain-Dateien
    - Pfad fuer Exports
    - Pfad fuer Backups
    - Pfad fuer Decoder-Vorlagen                          (ab Version 1.17)
  Ansicht
    - Farbschema (hell/dunkel)
    - Bildschirm fuer neu geoeffnete Fenster              (neu ab Version 2.0)
    - die Haken unter Voreinstellungen -> Ansicht
      (Typ anzeigen, Auswahlbox anzeigen, Daten aendern)
    - "Beim Start automatisch nach Updates suchen"
    - "Decoder-Hinweise anzeigen"                         (neu ab Version 2.0)
  Fenster                                                 (neu ab Version 2.0)
    - Groesse und Position jedes Fensters einzeln: Hauptfenster,
      "Decoder erfassen", "Decoder-Konfiguration", Vorlagen-Uebersicht
    - die gemerkte Verschiebung der Dialoge gegenueber der Fenstermitte
  Sonstiges
    - die Liste der zuletzt geoeffneten Dateien
    - Version des zuletzt installierten Decoder-Vorlagen-Pakets    (ab 1.17)
    - Zeitpunkt dieser Installation                                (ab 1.17)

Diese Angaben liegen NICHT im Programmordner, sondern dort, wo Java sie je
nach Betriebssystem ablegt. Eine Neuinstallation des Programms loescht sie
deshalb NICHT. Mit den drei Dateien in diesem Ordner setzen Sie alles auf den
Auslieferungszustand zurueck. Beim naechsten Start erscheint dann wieder der
Ersteinrichtungs-Dialog, in dem Sie Sprache, Farbschema und die vier Ordner
neu waehlen.

Ausserdem erscheint danach der Hinweistext vor der ersten Decoder-Bearbeitung
wieder, und alle Fenster oeffnen in ihrer Standardgroesse und -lage.

Die Skripte loeschen jeweils den GANZEN Einstellungsbereich des Programms und
nicht einzelne Eintraege. Kuenftig neu hinzukommende Einstellungen sind damit
automatisch mit abgedeckt.

IHRE EIGENEN DATEIEN BLEIBEN UNANGETASTET
-----------------------------------------
Nicht geloescht werden:
    - Ihre iTrain-Dateien (.tcd/.tcdz)
    - Ihre CSV-Exporte
    - Ihre Backups
    - Ihre Decoder-Vorlagen im Vorlagen-Ordner

Alles davon bleibt unveraendert liegen. Weil das Programm den Pfad danach
aber nicht mehr kennt, geben Sie den Ordner fuer Decoder-Vorlagen nach dem
Neustart bitte einmal neu an (Voreinstellungen -> Pfade, oder gleich im
Ersteinrichtungs-Dialog). Danach stehen alle vorhandenen Vorlagen sofort
wieder zur Verfuegung - die decoder.zip muss NICHT erneut installiert werden.

HINWEIS ZUR SPRACHE
-------------------
Die Sprache der Oberflaeche wird nicht dauerhaft gespeichert und deshalb auch
nicht zurueckgesetzt. Das Programm startet immer in der Sprache Ihres
Betriebssystems (sofern eine der 10 verfuegbaren Sprachen) und laesst sich
jederzeit unter Voreinstellungen -> Sprache umstellen.

WELCHE DATEI FUER WELCHES SYSTEM?
---------------------------------
  Windows   iTrain-ImportExport-Einstellungen-zuruecksetzen-Windows.reg
  macOS     iTrain-ImportExport-Einstellungen-zuruecksetzen-macOS.command
  Linux     iTrain-ImportExport-Einstellungen-zuruecksetzen-Linux.sh

Bitte in jedem Fall zuerst das Programm iTrain Import/Export beenden.


WINDOWS
-------
1. Die Datei ...-Windows.reg herunterladen.
2. Doppelklick darauf.
3. Windows fragt nach, ob die Aenderung an der Registrierungs-Datenbank
   ausgefuehrt werden soll - mit "Ja" bestaetigen.
4. Programm neu starten.

Hinweis: Windows warnt bei .reg-Dateien grundsaetzlich, weil sie die
Registrierungs-Datenbank aendern. Diese Datei loescht ausschliesslich den
Zweig des Programms:
HKEY_CURRENT_USER\Software\JavaSoft\Prefs\com\example\itrain_import_export
Sie koennen das vor dem Ausfuehren pruefen, indem Sie die Datei mit einem
Rechtsklick -> "Bearbeiten" im Editor oeffnen; es ist eine reine Textdatei.


macOS
-----
1. Die Datei ...-macOS.command herunterladen.
2. Doppelklick darauf - es oeffnet sich ein Terminal-Fenster, das Skript
   laeuft und meldet, was es getan hat.
3. Programm neu starten.

Falls der Doppelklick nicht funktioniert:
  - "Die Datei kann nicht geoeffnet werden, da sie von einem nicht
    verifizierten Entwickler stammt": Rechtsklick auf die Datei ->
    "Oeffnen" -> im Dialog nochmals "Oeffnen" waehlen.
  - "Permission denied": Die Datei ist nach dem Download nicht mehr
    ausfuehrbar. Terminal oeffnen und eingeben:
        bash ~/Downloads/iTrain-ImportExport-Einstellungen-zuruecksetzen-macOS.command
    (Pfad ggf. anpassen. Mit "bash davor" wird das Ausfuehrrecht nicht
    benoetigt.)

WICHTIG fuer macOS: Java legt dort die Einstellungen ALLER Java-Programme in
einer gemeinsamen Datei ab (~/Library/Preferences/com.apple.java.util.prefs.plist).
Ein zuverlaessiges Loeschen nur dieses einen Programms ist von aussen nicht
moeglich, deshalb entfernt das Skript diese gemeinsame Datei. Betroffen sind
nur Java-Programme, die diese Einstellungs-Schnittstelle nutzen. Wenn Sie
ausser iTrain Import/Export keine weiteren solchen Programme verwenden - bei
den meisten Anwendern der Fall - verlieren Sie dadurch nichts anderes.


LINUX
-----
1. Die Datei ...-Linux.sh herunterladen.
2. Ein Terminal im Download-Ordner oeffnen und eingeben:
        bash iTrain-ImportExport-Einstellungen-zuruecksetzen-Linux.sh
3. Programm neu starten.

Alternativ per Doppelklick, dann muss die Datei aber zuerst ausfuehrbar
gemacht werden:
        chmod +x iTrain-ImportExport-Einstellungen-zuruecksetzen-Linux.sh
Der Aufruf mit "bash" davor ist der einfachere Weg, da er das Ausfuehrrecht
nicht benoetigt.

Das Skript loescht gezielt nur den Ordner dieses Programms unter
~/.java/.userPrefs/com/example/itrain_import_export - andere Java-Programme
bleiben unberuehrt.


WAS PASSIERT, WENN NICHTS DA IST?
---------------------------------
Wurden die Einstellungen bereits geloescht oder das Programm noch nie
gestartet, melden die Skripte das und machen nichts weiter. Ein mehrfaches
Ausfuehren schadet also nicht.



===============================================================================
 iTrain Import/Export - Resetting the settings
 Status: version 2.0 (August 2026)
 Instructions ENGLISH
===============================================================================

WHAT ARE THESE FILES FOR?
-------------------------
iTrain Import/Export stores your settings permanently:

  Paths
    - path for iTrain files
    - path for exports
    - path for backups
    - path for decoder templates                      (since version 1.17)
  View
    - colour scheme (light/dark)
    - screen for newly opened windows                 (new in version 2.0)
    - the check boxes under Preferences -> View
      (Show Type, Show Selection Checkbox, Change Data)
    - "Automatically check for updates on startup"
    - "Show decoder notes"                            (new in version 2.0)
  Windows                                             (new in version 2.0)
    - size and position of each window separately: main window,
      "Capture decoder", "Decoder configuration", template overview
    - the remembered offset of dialogs from the centre of the window
  Other
    - the list of recently opened files
    - version of the most recently installed decoder template pack (1.17)
    - the date and time of that installation                       (1.17)

These are NOT kept in the program folder but in the place where Java stores
them on each operating system. Reinstalling the program therefore does NOT
remove them. The three files in this folder reset everything to the state of
a fresh installation. On the next start the first-run dialog appears again,
where you choose the language, the colour scheme and the four folders anew.

The notes shown before the first decoder edit will also appear again, and all
windows open at their default size and position.

The notes shown before the first decoder edit will appear again as well, and
all windows open at their default size and position.

Each script deletes the program's ENTIRE settings area rather than individual
entries. Settings added in future versions are therefore covered automatically.

YOUR OWN FILES ARE LEFT UNTOUCHED
---------------------------------
Not deleted are:
    - your iTrain files (.tcd/.tcdz)
    - your CSV exports
    - your backups
    - your decoder templates in the templates folder

All of these remain unchanged. However, since the program no longer knows the
path afterwards, please set the decoder templates folder once again after
restarting (Preferences -> Paths, or directly in the first-run dialog). All
existing templates are then immediately available again - there is NO need to
install decoder.zip a second time.

NOTE ON THE LANGUAGE
--------------------
The interface language is not stored permanently and is therefore not reset.
The program always starts in the language of your operating system (if it is
one of the 10 available languages) and can be changed at any time under
Preferences -> Language.

WHICH FILE FOR WHICH SYSTEM?
----------------------------
  Windows   iTrain-ImportExport-Einstellungen-zuruecksetzen-Windows.reg
  macOS     iTrain-ImportExport-Einstellungen-zuruecksetzen-macOS.command
  Linux     iTrain-ImportExport-Einstellungen-zuruecksetzen-Linux.sh

In every case, please close iTrain Import/Export first.


WINDOWS
-------
1. Download the file ...-Windows.reg.
2. Double-click it.
3. Windows asks whether the change to the registry should be applied -
   confirm with "Yes".
4. Start the program again.

Note: Windows always warns about .reg files because they modify the registry.
This file deletes nothing but the program's own branch:
HKEY_CURRENT_USER\Software\JavaSoft\Prefs\com\example\itrain_import_export
You can check this before running it: right-click the file -> "Edit" opens it
in Notepad; it is a plain text file.


macOS
-----
1. Download the file ...-macOS.command.
2. Double-click it - a Terminal window opens, the script runs and reports
   what it did.
3. Start the program again.

If the double-click does not work:
  - "cannot be opened because it is from an unidentified developer":
    right-click the file -> "Open" -> confirm with "Open" in the dialog.
  - "Permission denied": the file lost its executable flag during the
    download. Open Terminal and enter:
        bash ~/Downloads/iTrain-ImportExport-Einstellungen-zuruecksetzen-macOS.command
    (adjust the path if needed; with "bash" in front no executable flag is
    required.)

IMPORTANT for macOS: Java stores the settings of ALL Java programs in one
shared file there (~/Library/Preferences/com.apple.java.util.prefs.plist).
Removing only this one program's entries from outside is not reliably
possible, so the script deletes that shared file. This only affects Java
programs that use this settings interface. If you do not use any such program
besides iTrain Import/Export - which is the case for most users - you lose
nothing else.


LINUX
-----
1. Download the file ...-Linux.sh.
2. Open a terminal in the download folder and enter:
        bash iTrain-ImportExport-Einstellungen-zuruecksetzen-Linux.sh
3. Start the program again.

You can also double-click it, but then the file has to be made executable
first:
        chmod +x iTrain-ImportExport-Einstellungen-zuruecksetzen-Linux.sh
Calling it with "bash" in front is simpler because no executable flag is
needed.

The script deletes only this program's folder under
~/.java/.userPrefs/com/example/itrain_import_export - other Java programs are
left untouched.


WHAT IF THERE IS NOTHING TO DELETE?
-----------------------------------
If the settings were already removed, or the program has never been started,
the scripts say so and do nothing else. Running them more than once does no
harm.
