===============================================================================
 iTrain Import/Export - Installation
 Anleitung DEUTSCH (weiter unten: ENGLISH)
===============================================================================

VORAB
-----
Es wird KEIN Java benoetigt. Jeder Installer bringt alles Noetige mit.
Laden Sie nur die Datei fuer Ihr Betriebssystem herunter:

  Windows   iTrain-Import-Export-Windows-<Version>.msi
  macOS     iTrain-Import-Export-macOS-<Version>.dmg
  Linux     iTrain-Import-Export-Linux-<Version>.deb


WINDOWS
-------
1. Die .msi-Datei herunterladen und doppelklicken.
2. Falls "Der Computer wurde durch Windows geschuetzt" erscheint:
   auf "Weitere Informationen" klicken, dann "Trotzdem ausfuehren".
3. Dem Installationsassistenten folgen (Installationsordner, Startmenue-
   Eintrag und Desktop-Verknuepfung koennen gewaehlt werden).
4. Starten ueber das Startmenue oder die Desktop-Verknuepfung.

Deinstallieren: Einstellungen -> Apps -> "iTrain-Import-Export" -> Deinstallieren.


macOS
-----
1. Die .dmg-Datei herunterladen und doppelklicken.
2. Im geoeffneten Fenster das Programmsymbol in den Ordner "Programme"
   ("Applications") ziehen.
3. Beim ERSTEN Start: Rechtsklick auf das Programm -> "Oeffnen" -> im Dialog
   nochmals "Oeffnen". (Ein normaler Doppelklick wird beim ersten Mal
   abgelehnt, weil das Programm nicht bei Apple registriert ist.)
   Falls macOS gar keine Moeglichkeit anbietet: Systemeinstellungen ->
   "Datenschutz & Sicherheit" -> ganz unten "Dennoch oeffnen".
4. Danach startet das Programm wie jedes andere per Doppelklick.

Deinstallieren: das Programm aus dem Ordner "Programme" in den Papierkorb ziehen.


LINUX (Debian/Ubuntu und verwandte)
-----------------------------------
1. Die .deb-Datei herunterladen.
2. Installieren, entweder per Doppelklick (Softwarecenter) oder im Terminal:
        sudo apt install ./iTrain-Import-Export-Linux-<Version>.deb
   ("./" davor ist wichtig, sonst sucht apt im Internet.)
3. Starten ueber das Anwendungsmenue (Eintrag "iTrain-Import-Export").
   Alternativ liegt das Startprogramm unter
        /opt/itrain-import-export*/bin/iTrain-Import-Export

Deinstallieren - zuerst den genauen Paketnamen anzeigen lassen:
        dpkg -l | grep -i itrain
   und dann entfernen:
        sudo apt remove <angezeigter-Paketname>


ERSTER START
------------
Beim allerersten Start fragt das Programm einmalig nach Sprache, Farbschema
und den Ordnern fuer iTrain-Dateien, Exporte, Backups und Decoder-Vorlagen.
Alles davon laesst sich spaeter jederzeit unter "Einstellungen ->
Voreinstellungen" aendern.


HANDBUCH
--------
Das Handbuch liegt in der Proton-Drive-Freigabe im Ordner

        Manual_Handbuch

als PDF, in Deutsch und in Englisch. Es beschreibt alle Funktionen
ausfuehrlich - darunter die Decoder-Vorlagen, das Erfassungsfenster und die
Fensterverwaltung bei mehreren Bildschirmen.

Dieselben Texte stecken auch im Programm selbst: Menue "Hilfe" -> "Hilfe",
in zehn Sprachen. Der Menuepunkt "Hilfe" -> "Handbuch" oeffnet die
Proton-Drive-Freigabe im Browser.


DECODER-VORLAGEN
----------------
Im Download-Bereich liegt der Ordner

        decoder

mit den fertigen Decoder-Vorlagen (CV-Tabellen je Decoder-Typ, eine .csv je
Vorlage) sowie die gepackte Fassung

        decoder.zip

WICHTIG: Es genuegt NICHT, den Ordner irgendwohin zu kopieren. Die Vorlagen
muessen im Programm installiert werden:

  1. Programm starten.
  2. Menue "Einstellungen" -> "Decoder-Vorlagen installieren".
  3. Auf "Decoder-Datei installieren" klicken und die heruntergeladene
     decoder.zip auswaehlen.
  4. Das Programm entpackt die Vorlagen in Ihren Vorlagen-Ordner (der Pfad
     steht unter "Voreinstellungen -> Pfade" und wurde beim ersten Start
     abgefragt).

Mit demselben Knopf lassen sich auch einzelne .csv-Dateien einlesen - etwa
eine selbst erstellte oder eine zugeschickte Vorlage, auch mehrere auf
einmal.

Was danach zur Verfuegung steht, zeigt "Einstellungen" ->
"Decoder-Vorlagen".

Hinweis: Die mitgelieferten Vorlagen sind per Skript aus den PDF-Anleitungen
der Hersteller abgeleitet. Ihr Inhalt muss nicht in jedem Punkt stimmen -
bitte gegen die Anleitung Ihres Decoders pruefen.


EINSTELLUNGEN ZURUECKSETZEN
---------------------------
Im Ordner

        Einstellungen_Preferences_Back

liegen drei kleine Dateien - eine je Betriebssystem -, mit denen sich alle
gespeicherten Einstellungen des Programms auf den Auslieferungszustand
zuruecksetzen lassen: Pfade, Farbschema, Fenstergroessen, die Liste der
zuletzt geoeffneten Dateien und so weiter.

Das wird selten gebraucht (etwa wenn ein Fenster nach einem Monitorwechsel
unauffindbar ist oder der Ersteinrichtungs-Dialog erneut erscheinen soll).
Eine ausfuehrliche Anleitung in Deutsch und Englisch liegt in demselben
Ordner: ReadMe_LIESMICH-zuruecksetzen.txt

Ihre eigenen Dateien - iTrain-Dateien, Exporte, Backups und die
Decoder-Vorlagen - werden dabei NICHT angetastet.


AKTUALISIEREN
-------------
Das Programm meldet sich, wenn eine neue Version vorliegt, und der Knopf
"Herunterladen" oeffnet den Download-Ordner im Browser. Manuell pruefen:
Menue "Hilfe" -> "Update".
Die neue Version wird einfach ueber die alte installiert (gleiche Schritte
wie oben). Ihre Einstellungen bleiben dabei erhalten.

HINWEIS ZU WARNMELDUNGEN
------------------------
iTrain Import/Export ist ein kleines, kostenfreies Programm und durchlaeuft
nicht die (kostenpflichtigen) Zertifizierungsverfahren von Microsoft und
Apple. Deshalb warnen Windows und macOS vor der Installation. Das sagt nichts
ueber den Inhalt aus. Beim Herunterladen kann im Proton-Drive-Ordner
zusaetzlich "Scannen" aktiviert werden - Proton prueft die Datei dann auf
Schadsoftware.



===============================================================================
 iTrain Import/Export - Installation
 Instructions ENGLISH
===============================================================================

BEFORE YOU START
----------------
NO Java is required. Every installer contains everything needed.
Download only the file for your operating system:

  Windows   iTrain-Import-Export-Windows-<version>.msi
  macOS     iTrain-Import-Export-macOS-<version>.dmg
  Linux     iTrain-Import-Export-Linux-<version>.deb


WINDOWS
-------
1. Download the .msi file and double-click it.
2. If "Windows protected your PC" appears: click "More info", then
   "Run anyway".
3. Follow the setup wizard (you can choose the installation folder, a start
   menu entry and a desktop shortcut).
4. Start it from the start menu or the desktop shortcut.

Uninstall: Settings -> Apps -> "iTrain-Import-Export" -> Uninstall.


macOS
-----
1. Download the .dmg file and double-click it.
2. In the window that opens, drag the program icon into the "Applications"
   folder.
3. On the FIRST start: right-click the program -> "Open" -> confirm with
   "Open" in the dialog. (A plain double-click is refused the first time
   because the program is not registered with Apple.)
   If macOS offers no such option: System Settings -> "Privacy & Security"
   -> at the bottom, "Open Anyway".
4. After that it starts like any other program with a double-click.

Uninstall: drag the program from "Applications" to the bin.


LINUX (Debian/Ubuntu and related)
---------------------------------
1. Download the .deb file.
2. Install it either by double-clicking (software centre) or in a terminal:
        sudo apt install ./iTrain-Import-Export-Linux-<version>.deb
   (The leading "./" matters, otherwise apt searches the internet.)
3. Start it from the application menu (entry "iTrain-Import-Export").
   Alternatively the launcher is located at
        /opt/itrain-import-export*/bin/iTrain-Import-Export

Uninstall - first show the exact package name:
        dpkg -l | grep -i itrain
   then remove it:
        sudo apt remove <package name shown>


FIRST START
-----------
On the very first start the program asks once for the language, the colour
scheme and the folders for iTrain files, exports, backups and decoder
templates. All of it can be changed later at any time under
"Settings -> Preferences".


MANUAL
------
The manual is in the Proton Drive share, in the folder

        Manual_Handbuch

as a PDF, in German and in English. It describes every function in detail -
among them the decoder templates, the capture window and window handling
with several screens.

The same texts are built into the program: menu "Help" -> "Help", in ten
languages. The menu item "Help" -> "Manual" opens the Proton Drive share in
your browser.


DECODER TEMPLATES
-----------------
The download area contains the folder

        decoder

with the ready-made decoder templates (CV tables per decoder type, one .csv
per template) and the packed version

        decoder.zip

IMPORTANT: Copying the folder somewhere is NOT enough. The templates have to
be installed in the program:

  1. Start the program.
  2. Menu "Settings" -> "Install decoder templates".
  3. Click "Install decoder file" and pick the decoder.zip you downloaded.
  4. The program unpacks the templates into your template folder (the path
     is under "Preferences -> Paths" and was asked for on the first start).

The same button also reads in individual .csv files - one you made yourself
or one somebody sent you, several at once if you like.

To see what is available afterwards, use "Settings" -> "Decoder templates".

Note: the templates supplied were derived from the manufacturers' PDF manuals
by a script. Their content may not be correct in every detail - please check
against the manual of your own decoder.


RESETTING THE SETTINGS
----------------------
The folder

        Einstellungen_Preferences_Back

holds three small files - one per operating system - that reset all stored
settings of the program to the state of a fresh installation: paths, colour
scheme, window sizes, the list of recently opened files and so on.

This is rarely needed (for instance when a window cannot be found after
changing monitors, or when you want the first-run dialog to appear again).
Detailed instructions in German and English are in the same folder:
ReadMe_LIESMICH-zuruecksetzen.txt

Your own files - iTrain files, exports, backups and the decoder templates -
are NOT touched.


UPDATING
--------
The program tells you when a new version is available, and the "Download"
button opens the download folder in your browser. To check manually:
menu "Help" -> "Update".
The new version is simply installed over the old one (same steps as above).
Your settings are kept.

ABOUT THE WARNING MESSAGES
--------------------------
iTrain Import/Export is a small, free program and does not go through the
(paid) certification procedures of Microsoft and Apple. That is why Windows
and macOS warn before the installation. It says nothing about the content of
the file. While downloading you can additionally enable "Scan" in the Proton
Drive folder - Proton then checks the file for malware.
