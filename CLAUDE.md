# ShortBlock, Wissenshappen & TrimBox

Drei Android-Apps in einem Gradle-Projekt. Ausführliches in der `README.md`.

| Modul | App | Besonderheit |
|---|---|---|
| `app` | **ShortBlock** — blockt Reels, Shorts, TikTok-Algorithmus | Bedienungshilfe, **keine** `INTERNET`-Berechtigung |
| `wissen` | **Wissenshappen** — Wikipedia-Karten statt Kurzvideos | Internet, **keine** Bedienungshilfe |
| `trimbox` | **TrimBox** — meldet von Newslettern ab und räumt sie weg | Internet + IMAP/SMTP, Zugangsdaten im Keystore |

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
- **JavaMail bleibt auf `com.sun.mail:android-mail` (Namensraum `javax.mail`).** Die neuere
  Jakarta-/Angus-Linie 2.x braucht `jakarta.activation` und Java 11 und lässt sich auf Android
  nicht sauber bauen.

## Wo Logik hingehört

Alles Fehleranfällige liegt als **reine Funktion ohne Android** in testbaren Dateien; das
Android-Abhängige bleibt eine dünne Hülle drumherum. Neue Erkennung genauso bauen.

| Testbar (JVM) | Hülle |
|---|---|
| `service/RuleMatcher.kt`, `service/Rules.kt` | `service/BlockerAccessibilityService.kt` |
| `service/FeedPolicy.kt`, `service/TikTokPolicy.kt` | `service/AccessibilityUiNode.kt` |
| `data/StatsHistory.kt`, `data/WatchBudget.kt` | `data/StatsRepository.kt` |
| `data/CheatPass.kt`, `data/CheatPhrase.kt`, `service/Reminders.kt` | `service/ReminderOverlay.kt` |
| `service/SharedClip.kt` | — |
| `wissen/data/WikipediaParser.kt` | `wissen/data/WikipediaSource.kt` |
| `trimbox/data/UnsubscribeHeader.kt`, `SenderKey.kt` | `trimbox/mail/ImapScanner.kt` |
| `trimbox/data/TrashFolder.kt`, `ProviderPresets.kt` | `trimbox/mail/MailboxCleaner.kt` |
| `trimbox/data/SenderTally.kt` | `trimbox/mail/Unsubscriber.kt`, `data/AccountStore.kt` |

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
`trimbox/build/outputs/apk/debug/trimbox-debug.apk`

Tests und Lint müssen grün bleiben. Die Tests unter `app/src/test/.../service/` sichern ab, dass
Änderungen an Oberfläche oder Statistik die Erkennung nicht angefasst haben.

## Sprache

Kommentare und Nutzertexte **Deutsch**, Bezeichner **Englisch**. Strings immer in `values/` *und*
`values-de/` pflegen, sonst schlägt Lint fehl.
