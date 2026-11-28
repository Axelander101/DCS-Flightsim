DSC – Freunde und Multiplayer-Einladungen

Die Java-Dateien werden direkt im DSC-Projekt installiert. Vorherige Dateien
werden gesichert. Die vorhandene Supabase-Konfiguration bleibt erhalten.
Die Servereinrichtung ist noch nicht in deinem Supabase-Projekt ausgeführt.

EINMALIG IN SUPABASE AKTIVIEREN
1. Dein Projekt https://supabase.com/dashboard/project/kdvyhigsvnsnwrlurgpz öffnen.
2. SQL Editor > New query öffnen.
3. Den gesamten Inhalt von account-setup/FRIENDS_SETUP.sql einfügen und Run drücken.
4. Im Spiel nach dem Anmelden FRIENDS öffnen und REFRESH drücken.
Die SQL-Datei ergänzt drei Tabellen im privaten Schema dsc_social und fünf
abgesicherte öffentliche RPC-Funktionen. Sie ändert keine Passwörter, löscht
keine bestehenden Accounts und enthält keine Zugangsschlüssel.
Der komplette Ablauf ist in isoliertem PostgreSQL geprüft; die Browseranbindung
zum echten Dashboard schlug technisch fehl. Deshalb ist dieser Schritt noch offen.
Es werden keine Secret-/service_role-Schlüssel im Spiel benötigt.

FREUNDE HINZUFÜGEN
Mit dem Online-Account anmelden. FRIENDS im Hauptmenü oder in der Multiplayer-
Lobby öffnen. Den exakten Pilotennamen suchen, Spieler auswählen und SEND FRIEND
REQUEST drücken. Der Empfänger kann unter RECEIVED annehmen oder ablehnen.
SENT zeigt ausgehende Anfragen zum Zurückziehen. REMOVE beendet eine Freundschaft.
Gleiche Pilotennamen sind möglich; der persönliche Freundescode unterscheidet sie.
Der Freundescode bleibt stabil und ist kein Lobbycode. Er kann mit COPY kopiert
und ebenfalls gesucht werden. E-Mail-Adressen werden nicht in der Suche angezeigt.
Spieler erscheinen nach der ersten Anmeldung mit dieser Spielversion.

GEMEINSAM SPIELEN
- Host: Multiplayer-Lobby mit freiem Platz erstellen, FRIENDS öffnen, Freund
  auswählen und INVITE drücken.
- Eingeladener: FRIENDS öffnen, Einladung auswählen, gegebenenfalls Lobby-Passwort
  eingeben und ACCEPT & JOIN drücken. Der Lobbycode wird automatisch übermittelt.
- Alternativ: Einen Freund auswählen, der eine Lobby hostet, und ASK TO JOIN
  drücken. Der Host wählt die Anfrage und bestätigt ALLOW & INVITE. Anschließend
  erhält der Anfragende eine Einladung, die er mit ACCEPT & JOIN annimmt.
- Das Annehmen einer Einladung verlässt eine bereits verbundene eigene Lobby.
  Bei falschem Lobby-Passwort kann im Multiplayer-Formular erneut versucht werden;
  die Verbindungsdaten sind bereits eingetragen.
- Aus FRIENDS mit Escape zurück zur verbundenen Lobby. MAIN MENU verlässt sie.

NETZWERK UND GRENZEN
Die Einladung ersetzt das manuelle Versenden des Lobbycodes. Die Flugverbindung
bleibt direkt über TCP und nutzt das im Lobby-Menü ausgewählte Netzwerk. Beide
Spieler brauchen dasselbe LAN oder eine erreichbare VPN-/Host-Verbindung. Dieses
Update fügt keinen Internet-Relay und keine automatische NAT-Durchquerung hinzu.
Supabase vermittelt Anfragen, nicht die Flugbewegungen. Die Authentifizierung
schützt die sozialen Nachrichten; das bestehende Spielprotokoll bindet seine
Netzwerkspieler noch nicht kryptografisch an Supabase-Accounts.

Passwortgeschützte Lobbys behalten ihr Passwort. Es wird ausschließlich an die
bestehende Spielverbindung gegeben und nicht in Supabase gespeichert. Lobbycodes
und Sitzungskennungen sind nur innerhalb einer Einladung verfügbar; der Client
bekommt den Code erst bei expliziter Annahme. Bestätigte Freunde sehen Online-/
Lobby-Status; andere Spieler sehen bei der Suche nur Name und Freundescode.

Freundschaften werden online gespeichert und funktionieren auf anderen Geräten
mit demselben Account. Die Daten werden etwa alle 15 Sekunden aktualisiert.
Online-Status kann nach dem Schließen bis zu 60 Sekunden nachlaufen. Einladungen
laufen nach 10 Minuten ab. Veraltete Host-Sitzungen und entfernte Freundschaften
sperren alte Einladungen. Begrenzungen: 200 Freunde, 50 gesendete / 100 empfangene
offene Freundschaftsanfragen, 30 gesendete / 60 empfangene offene Einladungen.
Nach Ablehnen/Zurückziehen/Entfernen einer Freundschaft gilt eine Wartezeit von
10 Minuten für eine neue Anfrage dieses Paars. Für neue Einladungen an dieselbe
Person gilt eine kurze Wartezeit von 15 Sekunden. Doppelte offene Einladungen
werden zusammengeführt. Keine E-Mails oder Passwörter in den sozialen Tabellen.

PRÜFUNGEN
- Isoliertes PostgreSQL (PGlite): SQL-Datei wiederholt ausführbar; echte Rollen-
  und Zugriffsprüfungen, Empfängerrechte, Suchergebnisse, Freundschaftszustände,
  private Einladungen, Wiederholungs-/Ablaufschutz, Freund-entfernen und Limits.
- Zwei Java-Spieler gegen dieselbe SQL-Logik und eine echte lokale TCP-Lobby:
  Freundschaft, geschützte Einladung, Passwortprüfung, Beitreten, erneuter Versuch
  und vom Host genehmigte Beitrittsanfrage. Kein Lobby-Passwort im Backend-Verkehr.
- Formular-Klicks, Texteingaben, native Menüvorschau und Erhalt der Host-Lobby.
- Account-Anmeldung und Cockpit-/Spielmodus-Regression geprüft.
Die Beispielspieler in den Vorschaubildern sind isolierte Testdaten. Es wurden
keine echten Freundschaftsanfragen verschickt oder echten Testaccounts angelegt.
Ein Live-Test mit zwei angemeldeten Accounts bleibt nach der Servereinrichtung.

Technische Grundlagen:
https://supabase.com/docs/guides/database/functions
https://supabase.com/docs/guides/database/postgres/row-level-security
