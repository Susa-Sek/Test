# ShortBlock & Wissenshappen

Zwei Android-Apps in einem Gradle-Projekt. Ausführliches in der `README.md`.

| Modul | App | Besonderheit |
|---|---|---|
| `app` | **ShortBlock** — blockt Reels, Shorts, TikTok-Algorithmus | Bedienungshilfe, **keine** `INTERNET`-Berechtigung |
| `wissen` | **Wissenshappen** — Wikipedia-Karten statt Kurzvideos | Internet, **keine** Bedienungshilfe |

Die Trennung ist Absicht und darf nicht aufgehoben werden: Nur so bleibt ShortBlocks Zusage „kann
technisch nichts senden" wahr.

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

Möglich macht das `service/UiNode.kt`: Es kapselt `AccessibilityNodeInfo`, das auf der JVM nicht
instanziierbar ist.

**Alle Erkennungsmuster stehen in `service/Rules.kt`** — die einzige Datei, die ein Instagram-,
YouTube- oder TikTok-Update betrifft.

## Bauen und prüfen

```bash
echo "sdk.dir=/opt/android-sdk" > local.properties   # in dieser Umgebung
./gradlew testDebugUnitTest lintDebug assembleDebug
```

APKs: `app/build/outputs/apk/debug/app-debug.apk`, `wissen/build/outputs/apk/debug/wissen-debug.apk`

Tests und Lint müssen grün bleiben. Die Tests unter `app/src/test/.../service/` sichern ab, dass
Änderungen an Oberfläche oder Statistik die Erkennung nicht angefasst haben.

## Sprache

Kommentare und Nutzertexte **Deutsch**, Bezeichner **Englisch**. Strings immer in `values/` *und*
`values-de/` pflegen, sonst schlägt Lint fehl.
