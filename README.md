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

> Wer am Code arbeitet: `CLAUDE.md` im Wurzelverzeichnis fasst Architektur und die Fallstricke
> zusammen, die hier schon einmal wehgetan haben.

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

## Wenn der Blocker nach Stunden verstummt

Bekanntes Verhalten, an dem fast jede Bedienungshilfe leidet: Nach ein paar Stunden blockt
nichts mehr, und erst Aus- und Wiedereinschalten hilft. Dahinter stecken zwei Zustände, die von
außen gleich aussehen:

| Zustand | Ursache | Was hilft |
|---|---|---|
| Prozess abgeräumt | Energieverwaltung des Herstellers | Akku-Ausnahme, „Dienst am Leben halten" |
| Prozess lebt, keine Ereignisse mehr | eingeschlafene Android-Pipeline | wird automatisch alle 5 Min repariert |
| **Schalter steht wieder auf aus** | Hersteller-ROM hat die Bedienungshilfe mit abgeschaltet | nur von Hand wieder einschalten — der Wächter meldet es |

Was ShortBlock ab v0.4 dagegen tut:

- **Alle 5 Minuten** setzt der Dienst seine eigene Konfiguration neu (`setServiceInfo`). Das ist
  der dokumentierte Weg, eine eingeschlafene Ereignis-Pipeline anzustoßen, und kostet nichts,
  wenn alles läuft.
- **Rückfall auf den Ereignisknoten**, wenn `rootInActiveWindow` null liefert. Vorher stieg die
  App in dem Fall einfach aus und blockte stillschweigend nichts mehr.
- **„Dienst am Leben halten"** (Schalter, standardmäßig aus): ein Vordergrunddienst mit stummer
  Dauerbenachrichtigung. Bedienungshilfe und dieser Dienst teilen sich den Prozess — für Android
  ist ein Vordergrunddienst ein starkes Signal, ihn zu verschonen.
- **Gesundheitskarte** auf der Startseite: Meldet Android den Dienst als an, läuft in diesem
  Prozess aber keiner, erscheint eine Warnung mit genau diesem Befund. Bloße Stille — weil man
  Instagram vier Stunden nicht geöffnet hat — löst sie ausdrücklich *nicht* aus. Eine App, die
  ständig falschen Alarm gibt, wird bei der einen wichtigen Warnung nicht mehr gelesen.

### Der dritte Zustand: Android hat den Schalter selbst umgelegt

Der unangenehmste Fall, seit v0.4.2 behandelt. Xiaomi/HyperOS, Samsung und Oppo räumen nicht nur
den Prozess ab, sie **deaktivieren die Bedienungshilfe gleich mit** — typischerweise über Nacht
oder nach einem Neustart. Danach steht in den Systemeinstellungen wieder *aus*, und im Alltag
merkt man es nicht: Eine Blocker-App, die nichts tut, verhält sich exakt wie eine, bei der
gerade nichts zu blocken war.

Wieder einschalten kann sich die App **nicht** selbst — das verbietet Android, und das ist
richtig so. Sie kann nur schnell Bescheid sagen: Alle zwei Stunden prüft ein
[Wächter](app/src/main/java/de/shortblock/app/service/ServiceWatchdog.kt), ob der Dienst noch
gelistet ist, und meldet sich **einmal** per Benachrichtigung, wenn nicht. Ein Tipp darauf führt
direkt in die Bedienungshilfen. Genau einmal, nicht alle zwei Stunden — wer den Dienst bewusst
ausgelassen hat, soll nicht genervt werden.

Dagegen hilft dauerhaft nur die Geräteeinstellung:

| Hersteller | Was einzuschalten ist |
|---|---|
| Xiaomi / HyperOS | App-Info → **Autostart** erlauben, Energiesparmodus auf *Keine Einschränkungen*, App in der Übersicht der offenen Apps **fixieren** |
| Samsung | Akku → *Apps im Ruhemodus* → ShortBlock entfernen, „Nicht optimierte Apps" |
| OnePlus / Oppo / Realme | Akku → *Hintergrundaktivität zulassen*, Autostart erlauben |

## Einrichtung — die Reihenfolge ist wichtig

1. **Eingeschränkte Einstellungen zulassen.** Ab Android 13 sperrt das System Bedienungshilfen
   für sideloadete Apps. In der App-Info oben rechts das ⋮-Menü öffnen und *„Eingeschränkte
   Einstellungen zulassen“* wählen. Fehlt der Eintrag, ist nichts zu tun.
2. **Bedienungshilfe einschalten.** Einstellungen → Bedienungshilfen → ShortBlock.
   Ohne Schritt 1 ist dieser Schalter ausgegraut — das ist der häufigste Grund, warum solche
   Apps „nicht funktionieren“.
3. **Akku-Optimierung ausnehmen** (optional). Auf Xiaomi, Samsung und OnePlus empfohlen.

## Tageskontingent

Statt ganz-oder-gar-nicht kann jeder Kurzvideo-Blocker ein Tagesbudget bekommen: Unter der
Schalterzeile stehen `Immer · 5 · 10 · 20 · 30 Min`. Bis das Budget aufgebraucht ist, läuft das
Video; danach blockt die App wie bisher. Je App ein eigenes Kontingent.

Seit v0.4.2 gilt das auch für **„TikTok ganz blocken"**: *TikTok ist zu, außer X Minuten am Tag.*
App und `tiktok.com` im Browser teilen sich dieses eine Kontingent — sonst wäre es mit einem
Tab umgangen. Läuft der Ganz-Block gerade auf Kontingent, ist TikTok bewusst offen, und der
„Für dich"-Filter greift innerhalb dieser Minuten mit seinem **eigenen**, kleineren Kontingent
weiter. Erst ein Ganz-Block auf „Immer" macht die „Für dich"-Zeile wirklich bedeutungslos —
nur dann ist sie in der Oberfläche gedämpft.

**Voreinstellung ist „Immer".** Ein Zeitkontingent macht aus einer geschlossenen Tür eine
Verhandlung, und Verhandeln ist der Mechanismus, den diese App eigentlich abschafft. Wer eins
will, wählt es bewusst.

Gemessen wird ohne die Berechtigung *Nutzungsdaten*: Die Uhr läuft nur, solange eine Blockregel
gerade zuträfe. Entscheidend ist die Schrittgrenze von 2 Sekunden — wer die App wechselt oder
den Bildschirm ausschaltet, erzeugt keine Ereignisse mehr, und ohne diese Grenze wäre das
Kontingent nach einer Nacht Standby aufgebraucht. Die Uhr kann dadurch nur zu wenig zählen, nie
zu viel.

Bekannte Ungenauigkeit: Steht das Video still, feuert Android kaum Ereignisse und die Uhr
stockt.

## Instagram: „Für dich" abschalten

Der Schalter erzwingt den chronologischen „Folge ich"-Feed und stoppt an dessen Ende, bevor
Instagram wieder Fremd-Inhalte nachschiebt. Umgeschaltet wird auf **zwei** Wegen, weil Instagram
je nach Version zwei Oberflächen hat:

1. **Titel oben links antippen** und im Aufklappmenü „Folge ich" wählen — über bekannte
   View-IDs gefunden.
2. **Tab-Leiste** — „Für dich" und „Folge ich" nebeneinander, wie bei TikTok, erkannt über den
   Auswahl-Zustand (seit v0.6).
3. **Mittiger Titel über Text und Position**, ganz ohne View-ID (seit v0.8.1): ein sichtbarer
   Knoten in den obersten 20 % des Fensters, dessen Text **exakt** „Für dich" oder „Folge ich"
   lautet.

Die Reihenfolge ist Absicht — jeder spätere Weg ist ungenauer als der vorige und kommt deshalb
später dran.

**Warum es den dritten Weg braucht.** Instagram hat die Kopfzeile inzwischen dreimal umgebaut.
Jedes Mal brachen die View-IDs, und die App tat auf der neuen Oberfläche *still gar nichts* — der
stillste aller Fehler, zweimal in derselben Datei. Der Text „Für dich" hat alle drei Layouts
überlebt. Das ist dieselbe Lehre, die `TikTokPolicy` von Anfang an ziehen musste.

Zwei Gatter halten den Textweg zusammen, beide notwendig:

- **Exakte Gleichheit, nicht `contains`.** Im Feed steht „Vorgeschlagen für dich" an einzelnen
  Beiträgen; mit `contains` würde die App mitten in den Feed tippen.
- **Oberste 20 %.** Der Folge-ich-Knopf in einem fremden Profil sitzt unter Bild und Bio und
  bleibt damit draußen; der Stories-Streifen beginnt erst bei rund einem Viertel der Höhe.

Meldet Instagram bei einer Tab-Leiste keinen Tab als ausgewählt, greift der Textweg und tippt
„Folge ich" in der Kopfzeile an. Das ist gewollt: Entweder schaltet es um, oder wir sind schon
dort und nichts passiert. Bis v0.8.0 tat die App in diesem Fall gar nichts.

## Tages-Cheat

Einmal am Tag **fünf Minuten für alles** — Reels, Shorts, TikTok, auch bei aktivem Ganz-Block.

In v0.5 war das ein einziger Tipp auf den Bedienungshilfen-Knopf und lief sofort. Genau die
Geste, die man aus Reflex macht, und genau in dem Moment, in dem der Reflex am stärksten ist:
direkt nachdem geblockt wurde. Seit v0.8 stehen drei Hürden davor:

| Hürde | Wogegen |
|---|---|
| **Satz abtippen** | gegen die gedankenlose Geste. Drei Sätze im Wechsel, nie zweimal hintereinander derselbe — einen festen hätte man nach drei Tagen im Muskelgedächtnis |
| **60 Sekunden Wartezeit** | gegen den Impuls. In dieser Minute wird weiter geblockt. Die wirksamste der drei |
| **Kostet Tageskontingent** | gegen „ist ja gratis". Wo „Immer" eingestellt ist, gibt es nichts zu zahlen — das steht auch so im Dialog |

Der Bedienungshilfen-Knopf gewährt damit nichts mehr, er **führt nur noch zur Tür**: Ein Druck
öffnet ShortBlock auf der Cheat-Anfrage. Läuft oder wartet schon einer, zeigt er nur die
Restzeit.

**Für ein geschicktes Reel ist der Cheat damit unbrauchbar** — bis er läuft, ist die Minute
vorbei. Das ist kein Nebeneffekt, sondern der Zweck. Dafür gibt es „Geteilte Videos ansehen".

Zwei Entwurfsentscheidungen, die man beim Anfassen kennen muss:

- **Alle Phasen hängen an einem einzigen gespeicherten Zeitstempel**
  ([`CheatPass.kt`](app/src/main/java/de/shortblock/app/data/CheatPass.kt)). Sonst bräuchte es
  einen Wecker, der genau beim Ende der Wartezeit feuert — und ein Dienst, den der Hersteller
  zwischendurch abräumt, verlöre ihn.
- **Der Cheat hebt in `shouldIntervene` die Sperre auf, nicht die Uhr.** Bis v0.7 stand er vor
  der Kontingent-Uhr; seit v0.8 tickt sie weiter, ihr Ergebnis wird nur nicht zum Blocken
  benutzt. Wer die Reihenfolge „aufräumt", macht den Cheat wieder gratis.

Die Uhr-Falle bleibt: Ein Beginn, der weiter als die Wartezeit in der Zukunft liegt, heißt
zurückgestellte Systemuhr — dann gilt der Cheat als verbraucht, nie als endlos.

## Das Erinnerungs-Popup

Statt wortlos zurückzuspringen, legt ShortBlock ein Kärtchen über die App: ein Satz, der an das
eigene Vorhaben erinnert. Die Sprüche stehen als `reminder_lines` in den `values`-Ordnern; die
Auswahl in [`Reminders.kt`](app/src/main/java/de/shortblock/app/service/Reminders.kt) sorgt
dafür, dass sich nie zweimal hintereinander derselbe zeigt — ein bekannter Spruch wird nicht
gelesen, und ein ungelesenes Popup ist nur eine Verzögerung.

Zwei Entscheidungen dahinter:

- **Keine Berechtigung.** Das Fenster ist ein `TYPE_ACCESSIBILITY_OVERLAY`; dafür braucht eine
  Bedienungshilfe kein „Über anderen Apps anzeigen“. Bei einer App, die fremde Bildschirme
  liest, ist jede eingesparte Berechtigung ein Argument. Klappt das Einhängen nicht, bleibt der
  alte Toast — ein Popup darf den Blocker nie mit sich reißen.
- **Höchstens alle 20 Sekunden.** Nach einem Block läuft nur eine Sperre von 800 ms; ein Popup in
  diesem Takt wäre unerträglich. Dazwischen wird still geblockt wie bisher.

## Geteilte Videos einmal ansehen

Wer eine DM mit einem Reel bekommt, soll sie lesen können. Der Feind ist nicht das einzelne
Video, sondern die Endlosschleife danach. Deshalb der Schalter **„Geteilte Videos ansehen"**
(Voreinstellung an): Ein geschicktes Reel oder Short läuft einmal ganz, **der Wisch zum nächsten
blockt**.

„Geteilt" heißt dabei immer dasselbe — *du bist nicht in der App dorthin navigiert*:

- aus einer anderen App heraus geöffnet (der Viewer war das Erste nach dem App-Wechsel), **oder**
- unmittelbar aus einem DM-Verlauf, und zwar innerhalb von 10 Sekunden. Ohne diese Frist würde
  eine DM von vorhin später den Reels-Tab freischalten — der Dienst merkt sich den letzten
  Bildschirm, nicht die Absicht dahinter.

Aus dem Reels- oder Shorts-Tab bleibt alles gesperrt: Dorthin kommt man über die Startseite, die
vorher sichtbar ist.

Drei Enden, jedes für sich ausreichend:

| Ende | Wozu |
|---|---|
| **Der Wisch** | zählt nur aus der Video-Seitenliste (`PAGER_VIEW_IDS`). Im Kommentar-Bereich zu scrollen wirft ausdrücklich **nicht** raus |
| **Reißleine nach 90 s** | falls ein Update die Pager-ID nicht mehr meldet. Ohne sie stünde die Ausnahme irgendwann still auf Dauer offen, ohne dass es jemand merkt |
| **App verlassen** | setzt die Herkunftsprüfung zurück |

Die Regel steht rein in
[`SharedClip.kt`](app/src/main/java/de/shortblock/app/service/SharedClip.kt), die Muster wie
immer in `Rules.kt`. TikTok und der Feed-Filter sind davon unberührt.

## Wenn ein Blocker gar nichts tut

Meist stimmt der Paketname nicht. TikTok allein läuft unter vier davon — `musically`,
`musically.go` (**Lite**), `trill`, `aweme` —, und wer die Lite-Variante nutzt, bei dem tat
ShortBlock vor v0.4.1 bei TikTok schlicht nichts.

Das Tückische daran: Der Dienst bekommt für nicht gelistete Pakete überhaupt keine Ereignisse,
kann das Fehlen also nicht melden. Deshalb weitet er, **solange die Diagnose-Aufzeichnung läuft**,
seinen Empfang auf alle Apps und listet unter *Diagnose → Gesehene Apps* die echten Paketnamen.
Geblockt wird trotzdem ausschließlich, wofür er eingerichtet ist — die Weitung dient dem Zusehen.

Steht dort ein Paketname, der nicht in `Packages` in
[`Rules.kt`](app/src/main/java/de/shortblock/app/service/Rules.kt) und nicht in
`res/xml/accessibility_service_config.xml` steht: an beiden Stellen nachtragen, fertig.

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

## Aussehen

Feste eigene Palette, **kein** Material You. Bis v0.6 übernahm die App die Farben aus dem
Hintergrundbild des Nutzers — das Ergebnis sah auf jedem Gerät anders und auf keinem nach etwas
aus. Der Preis der Umstellung ist genau diese Anpassung; der Gewinn ist ein Gesicht.

Ein Akzent, sonst nichts Buntes: Bernstein trägt die große Zahl, den aktiven Reiter und die
gewählte Kontingent-Option. **Grün heißt ausschließlich „läuft", Rot ausschließlich „kaputt".**
Wer Farben für Dekoration verbraucht, kann mit ihnen später nichts mehr sagen. Die einzige
Ausnahme sind die drei Farbpunkte der App-Gruppen; sie ordnen zu, ohne dass die Karten selbst
bunt werden müssten.

Die Werte stehen benannt in
[`ui/theme/Color.kt`](app/src/main/java/de/shortblock/app/ui/theme/Color.kt), die Zuordnung zu
den Material-Rollen in `Theme.kt`. Wer eine Fläche einfärbt, greift zu einer Rolle
(`tertiaryContainer` = gut, `errorContainer` = kaputt), nie zu einer Farbe direkt — sonst driften
die Bedeutungen mit jedem neuen Bildschirm auseinander.

Gemeinsame Bausteine in `ui/components/`: `InfoCard` (Karte mit Ton), `SettingRow` (Titel,
Beschreibung, Schalter), `SectionHeader` (Farbpunkt plus Name), `StatusPill` (Dienst-Zustand),
`HeroCard` (Zahl und Wochenverlauf in einem). Vorher baute jeder Screen dieselben Dinge selbst —
daran sieht man einer App an, dass sie gewachsen und nicht gestaltet ist.

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
| `data/StatsHistory.kt` | Tageshistorie, Sehdauer und Zeitersparnis, reine Funktionen, JVM-testbar. |
| `data/WatchBudget.kt` | Rechenregeln fürs Kontingent, inklusive Schrittgrenze. |
| `service/ServiceHealth.kt` | Lebenszeichen und Befund, warum der Dienst stumm ist. |

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
