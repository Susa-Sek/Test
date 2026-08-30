# ShortBlock & Wissenshappen

Zwei Apps in einem Repo, die zusammengehören: **ShortBlock** nimmt den Kurzvideo-Sog weg,
**Wissenshappen** füllt die Lücke. Beide werden vom selben CI-Lauf gebaut und liegen dort als
getrennte Artifacts (`shortblock-debug-apk`, `wissenshappen-debug-apk`).

---

# ShortBlock

Android-App, die Kurzvideo-Sog abschaltet, ohne Instagram, YouTube oder TikTok zu sperren:

| Blocker | Was passiert |
|---|---|
| **Instagram Reels** | Der Reels-Viewer wird sofort geschlossen — aus dem Tab, aus Explore, aus einer DM oder aus dem Browser. Stories bleiben unangetastet. |
| **Instagram-Feed nur mit Gefolgten** | Erzwingt den chronologischen „Folge ich“-Feed und navigiert an dessen Ende heraus, bevor Instagram wieder Vorschläge nachschiebt. |
| **YouTube Shorts** | Shorts-Player, Shorts-Tab und `youtube.com/shorts` im Browser. Normale Videos, Suche und Abos funktionieren weiter. |
| **TikTok „Für dich“** | Erzwingt den „Folge ich“-Tab. DMs, Suche und Profile bleiben nutzbar. |
| **TikTok ganz** | Jeder Öffnungsversuch führt zurück — App und `tiktok.com` im Browser. Standardmäßig **aus**. |

Dazu eine Übersicht mit geschätzter Zeitersparnis und den letzten sieben Tagen.

Kein Play Store, keine Konten, **keine Internet-Berechtigung**. Die App kann technisch
nichts nach außen senden.

## Was der Browser damit zu tun hat

Ein Blocker, der nur Apps kennt, ist eine Papiertür: `youtube.com/shorts` in Chrome liefert
dieselbe Endlosschleife. ShortBlock liest deshalb auch die **Adressleiste** gängiger Browser.

Entscheidend ist dabei ein UND-Gatter auf die View-ID der Adressleiste. Ohne das würde die
Regel auch dort greifen, wo „youtube.com/shorts" bloß als Text im Suchergebnis steht — und
einen mitten aus der Google-Suche werfen. Genau dieser Fall ist als Test festgehalten.

## Installieren

1. Im Repo auf **Actions** → letzten erfolgreichen Lauf des Workflows *Android* öffnen.
2. Unter **Artifacts** `shortblock-debug-apk` herunterladen und entpacken.
3. `app-debug.apk` auf das Telefon kopieren und installieren („Installation aus unbekannten
   Quellen“ für den Datei-Manager oder Browser erlauben).
4. App öffnen und die drei Einrichtungsschritte durchgehen.

> **Update-Hinweis:** Die Debug-APK wird mit dem Debug-Keystore signiert, den GitHub Actions
> bei jedem Lauf neu erzeugen kann. Meldet die Installation einen Signatur-Konflikt, muss die
> alte Version einmal deinstalliert werden. Wer das dauerhaft vermeiden will, hinterlegt einen
> eigenen Keystore als GitHub Secret und baut `assembleRelease`.

## Einrichtung — die Reihenfolge ist wichtig

1. **Eingeschränkte Einstellungen zulassen.** Ab Android 13 sperrt das System Bedienungshilfen
   für sideloadete Apps. In der App-Info oben rechts das ⋮-Menü öffnen und *„Eingeschränkte
   Einstellungen zulassen“* wählen. Fehlt der Eintrag, ist nichts zu tun.
2. **Bedienungshilfe einschalten.** Einstellungen → Bedienungshilfen → ShortBlock.
   Ohne Schritt 1 ist dieser Schalter ausgegraut — das ist der häufigste Grund, warum solche
   Apps „nicht funktionieren“.
3. **Akku-Optimierung ausnehmen** (optional). Auf Xiaomi, Samsung und OnePlus empfohlen.

## Wenn etwas falsch geblockt wird

Auf dem Home-Screen steht unter **„Zuletzt ausgelöst"** die ID der Regel, die zuletzt gefeuert
hat, plus der Knoten, an dem sie gegriffen hat. Wenn ShortBlock aus einer App wirft, obwohl es
nicht sollte, ist das die entscheidende Information — ohne sie bleibt nur Raten zwischen allen
Mustern.

Zwei Leitplanken machen Fehlalarme unwahrscheinlicher, beide je einmal schmerzhaft gelernt:

- **Nur sichtbare Knoten zählen.** Der Accessibility-Baum enthält auch recycelte Listeneinträge
  und Knoten weit unterhalb des Bildschirms. Ohne diese Prüfung gilt ein „Vorgeschlagene
  Beiträge"-Knoten als Feed-Ende, bevor man ihn überhaupt gesehen hat.
- **Größe entscheidet mit.** Die Reels-Regel greift nur bei einem Treffer, der mindestens 60 %
  des Fensters einnimmt. So bleibt eine eingebettete Clips-Vorschau im Feed unangetastet, der
  Vollbild-Viewer nicht.
- **Browser-Regeln nur in der Adressleiste.** Siehe oben — ein Treffer im Seiteninhalt zählt
  nicht.

## Wenn plötzlich nichts mehr geblockt wird

Instagram und YouTube ändern ihre internen View-IDs mit größeren Updates. Dann ist genau eine
Datei anzupassen: [`Rules.kt`](app/src/main/java/de/shortblock/app/service/Rules.kt).

Die IDs muss man dafür nicht raten:

1. In der App auf **Diagnose** gehen und *View-IDs aufzeichnen* einschalten.
2. Den Bildschirm öffnen, der nicht mehr geblockt wird (z. B. ein Reel).
3. Zurück in die Diagnose — dort stehen die IDs des gerade gesehenen Bildschirms.
4. Das passende Muster in `Rules.kt` ergänzen, neu bauen.

Alternativ am Rechner: `adb shell uiautomator dump && adb pull /sdcard/window_dump.xml`.

**Fallstrick, der beim Nachpflegen leicht passiert:** Instagram nennt Reels intern *clips*,
und `reel_*` bezeichnet dort die **Stories**. Ein Muster `reel_` würde also Stories blocken
statt Reels. Bei YouTube ist es umgekehrt: dort heißen Shorts intern *reel*. Beides ist in
`Rules.kt` kommentiert und durch Tests abgesichert.

**TikTok bricht als erstes.** Dort gibt es keine stabilen View-IDs — die Oberfläche ist
verschleiert, die Kürzel wechseln mit jeder Version. Die Tab-Erkennung läuft deshalb über den
sichtbaren Text („Für dich" / „Folge ich"), und der ist übersetzt. Wenn der Tabwechsel nicht
mehr greift, stehen die tatsächlichen Beschriftungen im Diagnose-Screen.

## Selbst bauen

```bash
echo "sdk.dir=/pfad/zum/Android/Sdk" > local.properties
./gradlew :app:testDebugUnitTest    # Erkennungslogik prüfen
./gradlew :app:assembleDebug        # APK nach app/build/outputs/apk/debug/
```

Benötigt JDK 17 und das Android SDK mit Platform 36.

## Aufbau

```
AccessibilityEvent  →  UiNode  →  RuleMatcher / FeedPolicy  →  Zurück oder Tippen
   (nur IG + YT)     (testbar)         (Rules.kt)               (mit Cooldown)
```

| Datei | Rolle |
|---|---|
| `service/Rules.kt` | **Alle** Erkennungsmuster. Die einzige Datei, die ein IG/YT-Update betrifft. |
| `service/RuleMatcher.kt` | Baum-Traversierung mit Tiefen- und Knotenlimit, Regel-Abgleich. |
| `service/FeedPolicy.kt` | Zustandslogik für den Instagram-Feed: umschalten, nichts tun oder raus. |
| `service/BlockerAccessibilityService.kt` | Ereignis-Eingang, Drosselung, Cooldowns, Zähler. |
| `service/TikTokPolicy.kt` | Tabwechsel „Für dich“ → „Folge ich“, rein textbasiert. |
| `service/UiNode.kt` | Abstraktion über `AccessibilityNodeInfo` — macht die Logik JVM-testbar. |
| `data/StatsHistory.kt` | Tageshistorie und Zeitersparnis, reine Funktionen, JVM-testbar. |

Die drei Zeitschranken im Service sind kein Feintuning, sondern tragen die Stabilität:
höchstens ein Baum-Scan pro 150 ms, 800 ms Ruhe nach jedem Zurück (sonst entsteht eine
Back-Schleife, die aus der App wirft) und 600 ms nach einem Tipp auf den Feed-Umschalter.

## Grenzen

- Views in fremden Apps lassen sich nicht entfernen, nur wegnavigieren. Ein Reel ist also für
  einen Sekundenbruchteil sichtbar, bevor es schließt.
- Schlägt das Umschalten auf „Folge ich“ zweimal fehl, gibt die App auf und zeigt einen
  Hinweis, statt weiter blind zu tippen.
- Nicht getestet auf Geräten mit stark angepasster Bedienungshilfe-Implementierung.
- **Im Browser ist nur der direkte Kurzvideo-Einstieg abgedeckt** — Reels- und Shorts-URLs sowie
  die TikTok-Domain. Der Instagram-Startfeed und Explore laufen im Browser weiter. Das ist
  Absicht: Die Web-Version kennt keinen „Folge ich"-Umschalter, den der Dienst antippen könnte,
  dort gäbe es nur ganz-oder-gar-nicht.
- **Browser-Regeln brauchen eine sichtbare Adressleiste.** Chrome und andere klappen sie beim
  Scrollen ein; dann ist der Knoten nicht mehr sichtbar und die Regel feuert nicht. Beim Öffnen
  eines Links ist sie sichtbar, der Einstieg wird also erwischt — wer schon in der Seite steckt
  und weiterscrollt, nicht mehr. Das ist die direkte Folge der Sichtbarkeitsprüfung aus v0.2.0,
  und der Tausch ist richtig herum: lieber eine Lücke als wieder aus der App zu fliegen.

## Lizenz

Noch keine gewählt.

---

# Wissenshappen

Derselbe Wisch, anderer Inhalt: Vollbild-Karten, vertikal durchgewischt wie Reels — nur stehen
darauf Wikipedia-Artikel zu deinen Themen.

Blocken allein trägt nicht weit. Es entsteht eine Lücke, und die Lücke gewinnt die alte
Gewohnheit oft zurück. Diese App besetzt sie mit demselben Bewegungsmuster.

| | |
|---|---|
| **Quelle** | Wikipedia, kostenlos, ohne Schlüssel und ohne Konto |
| **Feed** | Themenkarten plus Artikel des Tages und „An diesem Tag" |
| **Themen** | frei wählbar, eigene Begriffe möglich — Wikipedia hat zu fast allem etwas |
| **Merken** | Lesezeichen je Karte; die Merkliste zeigt erst den Titel, der Text kommt auf Tippen |
| **Tagesziel** | einstellbar, Fortschrittsbalken oben |

## Warum kein Zufallsartikel

Naheliegend wäre `generator=random` gewesen. Ausprobiert und verworfen: Wikipedia liefert damit
zuverlässig Namenslisten, Denkmalverzeichnisse und Begriffsklärungen — also genau das
Weiterwischen ohne Ertrag, das die App abschaffen soll.

Stattdessen zwei kuratierte Quellen plus ein Filter:

1. **Themensuche** (`generator=search`) mit zufälligem Offset, damit der Feed nicht nach zwei
   Tagen leer wirkt.
2. **Tagesfeed** (`feed/featured`) — Artikel des Tages und „An diesem Tag" sind redaktionell
   ausgewählt und damit die verlässlichste kostenlose Qualitätsquelle.
3. **`WikipediaParser.isWorthShowing`** wirft aus, was trotzdem durchrutscht: Listen,
   Begriffsklärungen, reine Jahreszahlen, Texte unter 120 Zeichen. Jede dieser Regeln hat einen
   eigenen Test.

## Merken heißt Abfragen

Die Merkliste ist bewusst keine Leseliste. Zuerst steht nur der Titel da; der Text erscheint
erst auf Tippen. Dieser kurze Moment, in dem man versucht sich zu erinnern, ist der Unterschied
zwischen Wiederlesen und Behalten.

## Unterschied zu ShortBlock

Wissenshappen braucht **Internet** (die Happen kommen von Wikipedia), dafür **keine
Bedienungshilfe** und keinen Zugriff auf fremde Apps. Umgekehrt bei ShortBlock. Deshalb sind es
zwei getrennte Apps und nicht eine: So bleibt ShortBlocks Zusage „kann technisch nichts senden"
wahr.

## Grenzen

- Nur deutschsprachige Wikipedia. Die Sprache steckt in `WikipediaSource(language = "de")`.
- Kein Offline-Vorrat: Ohne Verbindung bleibt der Feed leer, statt gespeicherte Karten zu zeigen.
- Die Merkliste liegt als JSON in DataStore. Für ein paar hundert Karten reicht das; wer
  Tausende sammelt, sollte auf Room umstellen.
