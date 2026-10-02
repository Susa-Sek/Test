# ShortBlock, Wissenshappen, TrimBox & Klarzeit

Vier Android-Apps in einem Gradle-Projekt. Ausführliches in der `README.md`.

| Modul | App | Besonderheit |
|---|---|---|
| `app` | **ShortBlock** — blockt Reels, Shorts, TikTok-Algorithmus | Bedienungshilfe, Geräteadmin, **keine** `INTERNET`-Berechtigung |
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
- **Kein Präfix-Muster für YouTube — `yt_shorts_fullscreen` war genau das und ist raus.**
  v0.11.2 ergänzte `viewIdContains = ["reel_"]` ab 60 % Fensterfläche als Netz gegen
  umbenannte Kennungen. Sie blieb zunächst wirkungslos, weil `allowsSingleClip` damals jeden
  Shorts-Treffer durchwinkte; als v0.11.3 dieses Loch schloss, blockte sie **normale Videos**.
  `reel_` trifft als Präfix auch `reel_shelf_*`, und das Regal steht auf der Startseite UND in
  der Empfehlungsliste unter jedem Video. Der Vergleich mit `ig_clips_viewer` trug nicht: Der
  matcht auf einen **genauen Namen**, nicht auf ein Präfix. Ein drittes Bein darf wiederkommen
  — mit Kennungen aus dem Diagnose-Schirm eines echten Geräts, nicht geraten.
- **`minAreaFraction` misst die gelegten Bounds, nicht den sichtbaren Ausschnitt.**
  `getBoundsInScreen` liefert die Bounds der Layout-Position; ein scrollbarer Container ist
  höher als der Bildschirm und reisst damit jede Flächenschranke, während nur ein Streifen zu
  sehen ist — `isVisibleToUser` bleibt wahr. Die Schranke ist deshalb kein Ersatz für ein
  enges Muster. Richtig wäre, vor dem Vergleich auf das Fenster zu beschneiden; das betrifft
  `Rule.matches`, `Actions.clickNearest` und `ExplorePolicy.hasGridSize` gemeinsam und steht
  noch aus.
- **Zurück hat eine Obergrenze, und die muss bleiben** (`BackGuard`). `blockAndGoBack` drückt
  Zurück und wartet 800 ms; trifft die Regel danach wieder, drückt es erneut — ohne Ende. Bei
  einem richtigen Block ist das harmlos, weil das erste Zurück den Bildschirm verlässt. Bei
  einem Fehlalarm drückt es, bis die fremde App geschlossen ist; genau so hat sich YouTube
  zugemacht. Drei Zurück in Folge mit weniger als `CHAIN_GAP_MS` Abstand heissen: Zurück löst
  die Lage nicht. Dann `back_runaway` ins Protokoll — **so wird ein Fehlalarm überhaupt erst
  sichtbar** — und `PAUSE_MS` lang nicht eingreifen.
- **Die Ausnahme „einmal ansehen" darf nie an einer Regel-ID hängen.** Bis v0.11.2 stand in
  `allowsSingleClip` für YouTube `match.rule.id != "yt_shorts_tab_selected"`.
  `findFirstMatch` liefert aber die **erste** Regel der Liste, und `yt_shorts_player` steht
  vor der Tab-Regel — im Shorts-Tab war `match.rule.id` deshalb nie die Tab-Regel, jeder
  Short galt als bewusst ausgewählt. Weil `allowSingleClip` per Vorgabe an ist, blockte für
  Reels und Shorts **gar nichts mehr**, während Wand und TikTok weiterliefen. Genau diese
  Aufteilung war der Hinweis: Betroffen waren exakt die zwei Features, die durch diese
  Funktion laufen. Entschieden wird seither am Bildschirm, nicht an der Regel.
- **Die Ausnahme „einmal ansehen" gilt nur für Instagram, und das muss so bleiben.** Bei
  YouTube liess sie beide Wege durch: den Short aus dem Regal (so gebaut) und den aus dem
  Shorts-Tab (nicht so gebaut). Der Tab wird nur als Algorithmus-Strom erkannt, wenn ein
  Knoten `isSelected` meldet — und YouTubes untere Leiste tut das nicht verlässlich; an
  derselben Stelle scheitert auch `yt_shorts_tab_selected` mit seinem `requireSelected`.
  Ohne diesen Beleg galt auch der Tab als bewusste Wahl, und auf YouTube blockte gar nichts.
  Der geschützte Fall ist bei Instagram echt (ein Reel aus einer DM), bei YouTube gibt es ihn
  praktisch nicht: Ein Short aus dem Regal führt in dieselbe Endlosschleife wie der Tab. Die
  YouTube-Listen in `Rules.SharedClip` sind deshalb **leer** — das ist ein zweites Schloss,
  nicht Aufräumen: `canPolicySwipes` findet ohne Seitenliste nichts und verweigert, selbst
  wenn jemand das Feature-Gatter wieder aufmacht.
- **`isSelected` ist bei YouTube kein verlässlicher Beleg.** Bei Instagrams und TikToks
  Tab-Leisten trägt es; YouTube meldet es an der unteren Leiste oft nicht. Wer eine
  YouTube-Erkennung darauf stützt, baut etwas, das auf dem einen Gerät greift und auf dem
  nächsten still nichts tut.
- **Keine Ausnahme ohne funktionierende Reissleine.** Die Ausnahme verspricht „ein Video,
  aber Wischen blockt"; das ist so viel wert wie `isFromPager`. Sind die Pager-Kennungen
  umbenannt, wird nie ein Wisch gezählt, `mayWatch` bleibt bis `MAX_WATCH_MS` wahr — und weil
  ein Scan ohne Treffer den Zustand zurücksetzt, beginnen die fünf Minuten danach von vorn.
  Die Ausnahme stand damit praktisch dauerhaft offen. `SharedClip.canPolicySwipes` prüft
  deshalb beim Erteilen, ob die Seitenliste im Baum überhaupt auffindbar ist. Fehlt sie, wird
  geblockt. So kippt der nächste Umbau nach unten statt nach oben.
- **`Rules.SharedClip` ist nach Paket getrennt, und das muss so bleiben.** Eine gemeinsame
  Pager-Liste liess `reel_recycler` auch bei Instagram zählen — dort sind `reel_*` die
  Stories. Ein Story-Wisch hätte ein bewusst angetipptes Reel mitten im Video beendet.
- **Die Partnersperre darf niemanden aussperren.** `GuardianLock.verify` gibt bei leerem oder
  beschädigtem gespeichertem Wert `false` zurück und wirft **nie** — sonst stürzt die App genau
  in dem Moment ab, in dem jemand an die Einstellungen müsste. Aus demselben Grund schreibt
  `setGuardian` Passwort und Wiederherstellungscode in **einem** Vorgang: Ein abgebrochener
  Schreibvorgang dürfte keine Sperre ohne gültigen Notausgang hinterlassen. Der Code meidet
  `0`, `O`, `1`, `I` und `L` — er wird von Papier abgetippt.
- **`GuardianLock` benutzt `javax.crypto` und `java.util.Base64`, nicht `android.util.Base64`.**
  Beides gibt es ab API 26, also genau ab unserem `minSdk`, und nur so lässt sich die Sperre
  als gewöhnlicher JVM-Test prüfen. Bei einer Sperre, die aussperren kann, ist Testbarkeit
  keine Stilfrage.
- **Die Entsperrung gilt für die Sitzung, nicht dauerhaft.** In `AppRoot` liegt sie in einem
  `remember` — bewusst kein `rememberSaveable` und kein DataStore — und `onPauseOrDispose`
  setzt sie zurück. Wer daraus einen gespeicherten Wert macht, lässt die Sperre offen stehen,
  sobald jemand die App nur in den Hintergrund schiebt.
- **Der Geräteadmin verlangt keine einzige Richtlinie.** Der Deinstallationsschutz hängt allein
  daran, *dass* ein Admin aktiv ist. `res/xml/device_admin.xml` hat deshalb ein leeres
  `<uses-policies/>`. Wer dort etwas ergänzt, muss sagen können, wofür — eine App, die den
  Bildschirm fremder Apps liest, soll nicht auch das Gerät löschen dürfen. Und der
  Wiederherstellungscode entfernt den Admin **mit**: Sonst bliebe die App unentfernbar,
  obwohl niemand mehr die Schlüssel hat.
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
- **Reparieren beim Aufwachen, nicht nach Uhr.** Bis v0.11 hing `refreshServiceInfo()` allein
  am Herzschlag — einem `delay(5 min)` in einer Koroutine. Genau solche Timer setzt Doze über
  Nacht aus, weshalb morgens manchmal nichts blockte: Die Ereignis-Pipeline war eingeschlafen
  und die Reparatur kam frühestens fünf Minuten zu spät. Jetzt weckt ein zur Laufzeit
  registrierter Empfänger auf `ACTION_USER_PRESENT`/`ACTION_SCREEN_ON`, und `WakeRepair`
  entscheidet zusätzlich im Ereignispfad nach langer Stille. Der Herzschlag bleibt Rückfall.
  Wer den Empfänger entfernt, holt den Morgen-Fehler zurück.
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
- **Kein Fenster, das Berührungen schluckt — die Wand ist raus und darf nicht wiederkommen.**
  v0.11 legte über den „Für dich"-Feed ein Overlay ohne `FLAG_NOT_TOUCHABLE`. Es blieb nach
  dem Verlassen von Instagram über fremden Apps stehen und machte das Gerät unbenutzbar. Das
  ist schlimmer als jeder Fehlalarm: Ein falscher Block wirft dich aus einer App, ein hängendes
  Fenster nimmt dir das Gerät. Seit v0.13 wird bei Instagram nur noch **erinnert**
  (`FeedDecision.RemindToSwitch`), und die Erinnerung räumt sich über `postDelayed(::hide)`
  immer selbst ab. **TikTok schaltet weiterhin um** (`ChooseFollowing`) — dessen Tab-Leiste ist
  stabil, und beide Policies teilen sich `FeedDecision`.
- **Die Bremse braucht ein Echo-Gatter** (`FeedGuard.isEcho`). Ein Zurück-Scroll erzeugt selbst
  ein Scroll-Ereignis. Ohne das Gatter bremst die App gegen ihr eigenes Bremsen und Instagram
  ist unbedienbar — dieselbe Sorte Schleife wie die Zurück-Kette, die v0.11.4 eine ganze App
  zugedrückt hat. Was `BackGuard` fürs Zurück leistet, leistet `isEcho` fürs Scrollen. Eine
  zurückgestellte Uhr gilt hier bewusst **nicht** als Echo: Eine Bremse, die sich für ihr
  eigenes Echo hält, bremst nie wieder.
- **Die Bremse sitzt im Scan-Pfad, nicht am Scroll-Ereignis — und das muss so bleiben.**
  v0.14.0 hing am `TYPE_VIEW_SCROLLED`-Zweig und verlangte, dass das Ereignis selbst eine der
  Feed-Kennungen trägt; Scroll-Ereignisse tragen aber oft **gar keine** View-ID. Schlimmer:
  Sie prüfte `forYouActive`, und das wurde bei `FeedDecision.Idle` gelöscht — also ständig,
  sobald die Kopfzeile beim Scrollen aus dem Bild wandert. **Die Bremse war damit genau dann
  aus, wenn gescrollt wurde.** Jetzt kommt die Entscheidung frisch aus dem Baum, und die Liste
  wird über dieselben Kennungen gesucht, mit denen `FeedPolicy` den Startfeed erkennt.
- **`FeedDecision.Idle` darf nichts zurücksetzen.** „Unklar" ist kein Beleg für irgendetwas.
  Nur `AlreadyFiltered` — der positive Beleg, dass umgeschaltet wurde — leert die Zähler,
  dazu der Paketwechsel. Dieselbe Regel gilt in `FeedPolicy.evaluate` seit v0.10.1; sie
  einmal ins Gegenteil zu verkehren hat die ganze Bremse lahmgelegt. Sicher ist diese
  Lockerung nur, **weil** die Bremse im Scan sitzt und dort jedes Mal frisch
  `RemindToSwitch` verlangt: Auf einer Profilseite gibt es das nicht, dort wird also nie
  gebremst.
- **Jede Abbruchstelle der Bremse muss sich melden** (`brake_no_list`, `brake_not_scrollable`,
  `brake_at_top`, einmal je Grund wie bei `noteSingleClipDenied`). v0.14.0 hatte acht
  Abbruchstellen und protokollierte keine einzige — zu melden blieb „scrollt weiter", zu tun
  blieb raten. `brake_at_top` und `brake_not_scrollable` sehen von aussen gleich aus und sind
  es nicht: einmal ist alles in Ordnung, einmal greift die Bremse nie. Dafür trägt `UiNode`
  seit v0.14.1 `isScrollable`.
- **Der Tipp auf „Gefolgt" darf nie über eine Textsuche laufen.** „Gefolgt" steht bei Instagram
  auch als Knopf unter jedem fremden Profil — ein Fehlgriff **entfolgt jemanden**, ohne
  Rückmeldung und ohne Rückgängig. Deshalb liegt die Beschriftung in
  `MENU_AMBIGUOUS_FOLLOWING_ENTRIES` und nicht bei den eindeutigen, und `followingTabToTap`
  liefert nur Knoten aus der obersten `HEADER_FRACTION` des Fensters; ohne bekannte
  Fenstermasse liefert es gar nichts. Dazu kommt der Flächendeckel in `Actions.clickNearest`.
  Drei Gatter, alle nötig. Der Test `a follow button in the middle of the screen is never a
  target` hält sie fest.
- **Scheitert die Bremse, wird nichts gezählt.** Sonst zählte ein wirkungsloser Zurück-Scroll
  zur harten Stufe hoch, und Instagram würde geschlossen, obwohl nie etwas gebremst hat.
- **Eine Zusicherung, die an einem Ereignis hängt, gilt nur so weit wie der Ereignisempfang.**
  Das ist die Lehre aus der hängenden Wand, und sie ist allgemein. `resetFeedState()` räumte
  die Wand beim Paketwechsel ab und trug den Kommentar „Wer Instagram verlässt, soll die Wand
  nicht über der nächsten App wiederfinden". Nur läuft das in `onAccessibilityEvent`, und der
  Dienst ist über `packageNames` auf die überwachten Apps beschränkt — **beim Wechsel auf den
  Startbildschirm oder irgendeine andere App kommt gar kein Ereignis**. Der Kommentar behauptete
  eine Absicherung, die es nie gab. Wer etwas baut, das aufgeräumt werden muss, sobald der
  Nutzer eine überwachte App verlässt, darf sich dafür nicht auf ein Ereignis verlassen.
- **Ein Wächter im Herzschlag ist kein Wächter.** `WALL_STALE_MS` versprach 30 Sekunden, aber
  `dropStaleFeedWall()` lief nur aus `startHeartbeat()` — `delay(5 min)` in einer Koroutine,
  also genau der Timer, den Doze aussetzt. Die versprochene Zeit und die tatsächliche
  Prüffrequenz müssen zusammenpassen, sonst ist die Zusage Dekoration.
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
- **Abfragefenster und Rechenfenster sind nicht dasselbe.** Bis v0.2.0 begannen beide um
  Mitternacht — und damit fehlte jede Sitzung, die vor Mitternacht begann und danach
  weiterlief: Ihr `ACTIVITY_RESUMED` lag vor dem Abfragefenster und wurde nie geliefert.
  `UsageWindow` trennt beides: abgefragt wird mit `LOOKBEHIND_MS` Vorlauf, gerechnet ab
  Tagesbeginn. Der bestehende Test zum Fall „Sitzung von gestern" war grün, weil er die reine
  Funktion mit Daten fütterte, die das Gerät so nie liefert — **ein grüner Test ist kein
  Beweis, wenn die Hülle die Eingabe gar nicht erzeugen kann.**
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
| `service/ExplorePolicy.kt`, `service/WakeRepair.kt` | — |
| `service/FeedGuard.kt`, `service/BackGuard.kt` | `service/Actions.kt` |
| `data/StatsHistory.kt`, `data/WatchBudget.kt` | `data/StatsRepository.kt` |
| `data/CheatPass.kt`, `data/CheatPhrase.kt`, `service/Reminders.kt` | `service/ReminderOverlay.kt` |
| `data/GuardianLock.kt` | `system/GuardianDeviceAdmin.kt`, `system/SystemSettings.kt` |
| `service/SharedClip.kt` | — |
| `wissen/data/WikipediaParser.kt` | `wissen/data/WikipediaSource.kt` |
| `trimbox/data/UnsubscribeHeader.kt`, `SenderKey.kt` | `trimbox/mail/ImapScanner.kt` |
| `trimbox/data/TrashFolder.kt`, `ProviderPresets.kt` | `trimbox/mail/MailboxCleaner.kt` |
| `trimbox/data/SenderTally.kt` | `trimbox/mail/Unsubscriber.kt`, `data/AccountStore.kt` |
| `klarzeit/data/UsageSessions.kt`, `TimeFormat.kt` | `klarzeit/data/UsageReader.kt` |
| `klarzeit/data/GoalState.kt`, `DefaultExclusions.kt` | `klarzeit/widget/KlarzeitWidget.kt` |
| `klarzeit/data/UsageWindow.kt`, `DayHistory.kt` | `klarzeit/data/HistoryRepository.kt` |

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
