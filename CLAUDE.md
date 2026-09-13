# ShortBlock, Wissenshappen, TrimBox & Klarzeit

Vier Android-Apps in einem Gradle-Projekt. Ausführliches in der `README.md`.

| Modul | App | Besonderheit |
|---|---|---|
| `app` | **ShortBlock** — blockt Reels, Shorts, TikTok-Algorithmus | Bedienungshilfe, **keine** `INTERNET`-Berechtigung |
| `wissen` | **Wissenshappen** — Wikipedia-Karten statt Kurzvideos | Internet, **keine** Bedienungshilfe |
| `trimbox` | **TrimBox** — meldet von Newslettern ab und räumt sie weg | Internet + IMAP/SMTP, Zugangsdaten im Keystore |
| `klarzeit` | **Klarzeit** — Bildschirmzeit ohne die Apps, die nicht zählen | Nutzungsdaten-Zugriff, Widget, **keine** `INTERNET`-Berechtigung |

Die Trennung ist Absicht und darf nicht aufgehoben werden: Nur so bleibt ShortBlocks Zusage „kann
technisch nichts senden" wahr. TrimBox spricht ausschliesslich mit dem Mailserver des Nutzers —
es gibt keinen eigenen Server, und dabei bleibt es.

## Leitsatz der Erkennung

**Ein Fehlalarm ist teurer als eine Lücke.** Wer versehentlich aus Instagram fliegt, kann die App
nicht mehr benutzen; wer ein Reel zu viel sieht, ärgert sich kurz. Im Zweifel nicht blocken.

## Fallstricke, die schon einmal wehgetan haben

- **Instagram nennt Reels intern `clips`.** `reel_*` sind dort die **Stories**. Bei YouTube ist es
  umgekehrt: Shorts heißen intern `reel`. Muster nie zwischen den Apps kopieren.
- **Nur sichtbare Knoten dürfen etwas auslösen** (`UiNode.isVisible`). Der Baum enthält recycelte
  und ausgeblendete Views. Ohne diese Prüfung warf v0.1 beim Öffnen sofort aus Instagram heraus.
- **Browser-Regeln brauchen `viewIdMustContain`** (UND-Gatter auf die Adressleiste). Sonst genügt
  „youtube.com/shorts" als Text in einem Suchergebnis und die App wirft aus der Google-Suche.
- **`Rules.BROWSER_URL_BAR_IDS` muss vor `BLOCK_RULES` stehen.** Kotlin initialisiert
  object-Eigenschaften in Textreihenfolge; andersherum ist die Liste noch null und die ganze
  Klasse schlägt beim Laden fehl.
- **Der Cheat hebt in `shouldIntervene` die Sperre auf, nicht die Uhr.** Bis v0.7 stand er vor
  der Kontingent-Uhr, damit die geschenkten Minuten das Budget nicht aufbrauchen. Seit v0.8 ist
  genau das gewollt: Die Uhr tickt weiter, ihr Ergebnis wird nur nicht zum Blocken benutzt. Wer
  die Reihenfolge „aufräumt“, macht den Cheat wieder gratis.
- **Der Cheat hängt an einem einzigen Zeitstempel** (`cheatArmedAtMillis`). Wartezeit, Laufzeit
  und Ende rechnet `CheatPass` daraus aus — kein Wecker, der bei abgeräumtem Dienst verloren
  ginge. Ein Beginn, der weiter als die Wartezeit in der Zukunft liegt, heißt zurückgestellte
  Systemuhr: dann gilt der Cheat als verbraucht, nie als endlos.
- **Kein Erkennungsweg in `FeedPolicy.evaluate` darf die Rückfallkette kappen.** Bis v0.10.1
  gab Weg 1 (Titel über View-ID) sein `Idle` ungeprüft durch, sobald er irgendeinen Knoten
  mit Titel-Kennung fand — die Wege 2 und 3 kamen nie zum Zug. Bei Instagrams mittiger
  Kopfzeile, deren Beschriftung in einem Kindknoten steckt, war der Filter damit vollständig
  wirkungslos, ohne dass irgendetwas auffiel. Jede Stufe gibt ihr Ergebnis nur weiter, wenn
  es **nicht** `Idle` ist.
- **Ein Titelknoten trägt seine Beschriftung nicht immer selbst.** `labelOf` schaut deshalb
  eine Ebene tiefer. Nur eine: Wer tiefer sucht, findet irgendwann den ersten Beitrag und
  hält ihn für den Titel.
- **Instagram nennt den gefilterten Feed inzwischen „Gefolgt", nicht mehr „Folge ich".**
  Stand die neue Beschriftung nicht in `FOLLOWING_TITLES`, hielt die App den umgeschalteten
  Feed für einen unbekannten Titel und tat gar nichts mehr — auch das Feed-Ende feuerte nie.
  Fehlte sie in den Menüeinträgen, öffnete die App das Menü und fand nichts zum Antippen.
- **„Gefolgt" ist auch der Folgen-Knopf an einem Beitrag.** Deshalb steht das Wort in
  `MENU_AMBIGUOUS_FOLLOWING_ENTRIES` und wird nur angetippt, wenn ein zweiter Menüeintrag
  aus `MENU_COMPANION_ENTRIES` („Favoriten") daneben sichtbar ist. Ohne diesen Nachweis
  könnte die App im „Für dich"-Feed auf den Knopf eines vorgeschlagenen Beitrags tippen und
  stillschweigend ein Abo kündigen. Wer die Liste „aufräumt" und das Wort nach
  `MENU_FOLLOWING_ENTRIES` schiebt, baut genau diesen Fehler wieder ein.
- **Explore hat keinen „Folge ich"-Schalter.** Deshalb wird dort nicht umgeschaltet wie im
  Startfeed, sondern verlassen. `ExplorePolicy` fällt bewusst **nicht** auf „Lupen-Tab ist
  ausgewählt" zurück, wenn keine Raster-Kennung passt: Dieser Rückfall würde auf einer
  unbekannten Instagram-Fassung auch bei jeder Suche feuern. Lieber still nichts tun.
- **Explore und Suche sind derselbe Tab.** Ohne die Schonfrist in `ExplorePolicy` käme
  niemand mehr ans Suchfeld — die Sperre nähme eine Funktion mit, die niemand abschalten
  wollte. Die Suchleiste selbst gilt deshalb NICHT als Suchbeleg (sie steht auch über dem
  Raster); nur Trefferliste, „Zuletzt gesucht" und dergleichen.
- **Ein Feature darf Policy *und* Browser-Regel haben.** `EnforcementCoverageTest` verbietet
  den Doppelweg nur innerhalb der App; `INSTAGRAM_EXPLORE` wird in Instagram von einer Policy
  und im Browser von einer Adressregel durchgesetzt. Das sind zwei Oberflächen.
- **`org.json` ist im JVM-Unit-Test nur ein Stub**, der bei jedem Aufruf wirft. Module, die es
  benutzen, brauchen `testImplementation(libs.org.json)`.

Für TrimBox zusätzlich:

- **Niemals `folder.expunge()` ohne Argumente.** Der Aufruf löscht *alles* endgültig, was im
  Ordner als gelöscht markiert ist — auch was der Nutzer vor Wochen selbst markiert hat.
  Erlaubt sind nur `MOVE` (RFC 6851) und, als Rückfall, `expunge(messages)` alias `UID EXPUNGE`
  (RFC 4315). Aus demselben Grund steht überall `close(false)`: `close(true)` räumt den Ordner
  aus.
- **`mail.*.ssl.checkserveridentity` muss von Hand auf `true`.** JavaMail 1.6 prüft von sich aus
  **nicht**, ob das Zertifikat zum Server gehört. Ohne die Zeile in `MailSession` nimmt die App
  jedes gültige Zertifikat der Welt an, und wer im selben WLAN sitzt, liest Passwort und
  Postfach mit.
- **Ein-Klick-Abmeldung nur mit `List-Unsubscribe-Post` UND `https`.** Ohne diese Zusage des
  Absenders ist der Link nur ein Link — ein stilles GET darauf kann eine Bestätigungsseite oder
  ein Zählpixel sein. Dann gehört er in den Browser, nicht in einen Hintergrundaufruf.
- **Der Durchlauf lädt nur Kopfzeilen.** Der `FetchProfile` in `ImapScanner` ist kein Feintuning,
  sondern der Grund, warum die App Sekunden statt Minuten braucht und ihre Zusage halten kann,
  Mail-Inhalte nicht anzufassen. Wer dort ein Feld ergänzt, das nicht in der Kopfzeile steht,
  löst den Download ganzer Nachrichten aus.
- **Keine `ssl.protocols`-Liste festschreiben.** JavaMail reicht sie unverändert an
  `SSLSocket.setEnabledProtocols` weiter, und das wirft, sobald eine Fassung dem Gerät
  unbekannt ist — auf Android 8 und 9 gibt es kein TLS 1.3. Ohne die Zeile handelt Android
  selbst aus, und das tut es richtig.
- **App-Passwörter werden als vier Vierergruppen angezeigt.** Wer sie einfügt, hat
  Leerzeichen dabei; über IMAP gehen die mit und der Server lehnt ab. `AppPassword.normalize`
  entfernt sie — aber nur bei genau diesem Muster, damit ein selbst vergebenes Passwort mit
  Leerzeichen unangetastet bleibt.
- **Formularzustand gehört nicht in den Bildschirm, der ihn absendet.** `ConnectScreen`
  verschwindet beim Verbinden aus der Komposition; lag der Zustand dort, war nach jedem
  Fehlversuch alles gelöscht und die Anmeldung fühlte sich an, als passiere nichts.
- **JavaMail bleibt auf `com.sun.mail:android-mail` (Namensraum `javax.mail`).** Die neuere
  Jakarta-/Angus-Linie 2.x braucht `jakarta.activation` und Java 11 und lässt sich auf Android
  nicht sauber bauen.

Für Klarzeit zusätzlich:

- **`PACKAGE_USAGE_STATS` lässt sich nicht per Dialog erfragen.** Es ist keine gewöhnliche
  Berechtigung: `checkSelfPermission` sagt darüber nichts, gefragt wird `AppOpsManager`, und
  erteilt wird sie nur von Hand in den Einstellungen. Der Aufruf heisst dort ab Android 10
  `unsafeCheckOpNoThrow` und davor `checkOpNoThrow` — ohne die Weiche in `UsageReader`
  stürzt die App auf Android 8 und 9 beim ersten Start ab.
- **Nicht `queryUsageStats`, sondern `queryEvents`.** Die fertige Summe ist gerundet, je nach
  Hersteller verschieden und am laufenden Tag unzuverlässig. `UsageSessions` rechnet aus den
  rohen Ereignissen; die vier Fälle, die dabei zählen (offene Sitzung, Sitzung von gestern,
  Bildschirm aus, Wechsel ohne Pause), haben jeder einen eigenen Test.
- **`ACTIVITY_STOPPED` darf nicht als "im Hintergrund" gelten.** Android schickt es
  verspätet, wenn die nächste Ansicht derselben App längst läuft (YouTube-Liste → Player).
  Wer es auswertet, beendet die gerade offene Sitzung und verliert alles danach — in v0.1.0
  rund ein Viertel der Bildschirmzeit. `UsageEventTypes` verwirft es deshalb; Sitzungen
  enden über `ACTIVITY_PAUSED`, das nächste `ACTIVITY_RESUMED` oder den Bildschirm.
- **Der Startbildschirm zählt nicht mit.** Er taucht in den Nutzungsdaten als normale App
  auf und sammelt viel Zeit, ist aber der Weg zu einer App und nicht das Ziel. Ermittelt
  wird er über `CATEGORY_HOME` statt festgeschrieben — jeder Hersteller bringt einen
  anderen mit.
- **Ohne `SCREEN_OFF` läuft die App die ganze Nacht weiter** und meldet morgens acht Stunden
  Instagram. Wer die Ereignisliste in `UsageReader.typeOf` aufräumt, nimmt genau das wieder
  heraus.
- **Ohne das `<queries>`-Element im Manifest gibt es nur Paketnamen.** Seit Android 11 sieht
  eine App die anderen nicht mehr von selbst; ohne den Filter auf Startmenü-Einträge stünde
  in der Liste `com.instagram.android` statt „Instagram".
- **Das Widget liest synchron (`runBlocking`).** Android gibt einem Widget nur Sekunden. Wer
  daraus einen Hintergrundaufruf macht, bekommt beim Einblenden einen leeren Kasten.

## Wo Logik hingehört

Alles Fehleranfällige liegt als **reine Funktion ohne Android** in testbaren Dateien; das
Android-Abhängige bleibt eine dünne Hülle drumherum. Neue Erkennung genauso bauen.

| Testbar (JVM) | Hülle |
|---|---|
| `service/RuleMatcher.kt`, `service/Rules.kt` | `service/BlockerAccessibilityService.kt` |
| `service/FeedPolicy.kt`, `service/TikTokPolicy.kt` | `service/AccessibilityUiNode.kt` |
| `service/ExplorePolicy.kt` | — |
| `data/StatsHistory.kt`, `data/WatchBudget.kt` | `data/StatsRepository.kt` |
| `data/CheatPass.kt`, `data/CheatPhrase.kt`, `service/Reminders.kt` | `service/ReminderOverlay.kt` |
| `service/SharedClip.kt` | — |
| `wissen/data/WikipediaParser.kt` | `wissen/data/WikipediaSource.kt` |
| `trimbox/data/UnsubscribeHeader.kt`, `SenderKey.kt` | `trimbox/mail/ImapScanner.kt` |
| `trimbox/data/TrashFolder.kt`, `ProviderPresets.kt` | `trimbox/mail/MailboxCleaner.kt` |
| `trimbox/data/SenderTally.kt` | `trimbox/mail/Unsubscriber.kt`, `data/AccountStore.kt` |
| `klarzeit/data/UsageSessions.kt`, `TimeFormat.kt` | `klarzeit/data/UsageReader.kt` |
| `klarzeit/data/GoalState.kt`, `DefaultExclusions.kt` | `klarzeit/widget/KlarzeitWidget.kt` |

Möglich macht das `service/UiNode.kt`: Es kapselt `AccessibilityNodeInfo`, das auf der JVM nicht
instanziierbar ist.

**Alle Erkennungsmuster stehen in `service/Rules.kt`** — die einzige Datei, die ein Instagram-,
YouTube- oder TikTok-Update betrifft.

## Bauen und prüfen

```bash
echo "sdk.dir=/opt/android-sdk" > local.properties   # in dieser Umgebung
./gradlew testDebugUnitTest lintDebug assembleDebug
```

APKs: `app/build/outputs/apk/debug/app-debug.apk`, `wissen/build/outputs/apk/debug/wissen-debug.apk`,
`trimbox/build/outputs/apk/debug/trimbox-debug.apk`,
`klarzeit/build/outputs/apk/debug/klarzeit-debug.apk`

Tests und Lint müssen grün bleiben. Die Tests unter `app/src/test/.../service/` sichern ab, dass
Änderungen an Oberfläche oder Statistik die Erkennung nicht angefasst haben.

## Sprache

Kommentare und Nutzertexte **Deutsch**, Bezeichner **Englisch**. Strings immer in `values/` *und*
`values-de/` pflegen, sonst schlägt Lint fehl.
