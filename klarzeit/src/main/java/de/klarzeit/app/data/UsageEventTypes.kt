package de.klarzeit.app.data

/**
 * Übersetzt Androids Ereignis-Nummern in die drei Fälle, die für Bildschirmzeit zählen.
 *
 * Steht bewusst hier als reine Funktion und nicht in [UsageReader]: Welche Ereignisse
 * mitzählen, ist die folgenreichste Entscheidung der ganzen Rechnung — und die einzige,
 * die sich ohne Gerät prüfen lässt.
 *
 * **`ACTIVITY_STOPPED` wird ausdrücklich nicht ausgewertet.** Android schickt es verspätet,
 * nachdem die nächste Ansicht derselben App längst im Vordergrund ist. Beim Wechsel von
 * der YouTube-Liste in den Player kommt `PAUSED(Liste)`, `RESUMED(Player)`, und erst dann
 * `STOPPED(Liste)`. Wer das als "App im Hintergrund" wertet, beendet die gerade laufende
 * Sitzung und zählt den Rest des Videos nicht mehr mit. Das war der Grund, warum die App
 * in v0.1.0 rund ein Viertel der Bildschirmzeit verschluckt hat.
 *
 * Ohne `STOPPED` bleibt keine Sitzung hängen: Sie endet beim `PAUSED`, beim nächsten
 * `RESUMED` einer anderen App — der Startbildschirm zählt dazu — oder wenn der Bildschirm
 * ausgeht.
 */
object UsageEventTypes {

    // Die Zahlen aus android.app.usage.UsageEvents.Event. Hier als Konstanten, damit die
    // Zuordnung ohne Android-Klassen prüfbar bleibt.
    const val ACTIVITY_RESUMED = 1
    const val ACTIVITY_PAUSED = 2
    const val SCREEN_INTERACTIVE = 15
    const val SCREEN_NON_INTERACTIVE = 16
    const val KEYGUARD_SHOWN = 17
    const val KEYGUARD_HIDDEN = 18
    const val ACTIVITY_STOPPED = 23
    const val DEVICE_SHUTDOWN = 26

    /** `null` heisst: sagt nichts über Bildschirmzeit aus und wird verworfen. */
    fun of(eventType: Int): UsageSessions.Type? = when (eventType) {
        ACTIVITY_RESUMED -> UsageSessions.Type.FOREGROUND
        ACTIVITY_PAUSED -> UsageSessions.Type.BACKGROUND
        SCREEN_NON_INTERACTIVE, KEYGUARD_SHOWN, DEVICE_SHUTDOWN -> UsageSessions.Type.SCREEN_OFF
        else -> null
    }
}
