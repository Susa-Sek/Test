package de.klarzeit.app.data

/**
 * Voreinstellungen, damit die App beim ersten Start schon eine sinnvolle Zahl zeigt.
 *
 * [DEFAULT_EXCLUDED] ist ein Vorschlag und nichts weiter — Navigation und Musik laufen
 * lange, ohne dass jemand auf den Bildschirm starrt. Alles davon lässt sich abwählen.
 *
 * [ALWAYS_IGNORED] dagegen taucht überhaupt nicht auf: Die Systemoberfläche ist keine App,
 * die man benutzt, und der Startbildschirm ist der Weg zu einer App, nicht das Ziel. Beide
 * in der Liste zu führen hiesse, den Nutzer über etwas entscheiden zu lassen, das keine
 * Entscheidung ist.
 */
object DefaultExclusions {

    val DEFAULT_EXCLUDED: Set<String> = setOf(
        // Navigation
        "com.google.android.apps.maps",
        "com.waze",
        "de.hafas.android.db",
        "de.bahn.dbnavigator",
        // Musik und Podcasts
        "com.spotify.music",
        "com.google.android.apps.youtube.music",
        "deezer.android.app",
        "de.danoeh.antennapod",
        // Werkzeuge, die niemand aus Langeweile öffnet
        "com.google.android.deskclock",
        "com.android.deskclock",
        "com.google.android.dialer",
        "com.android.dialer",
        "com.google.android.calendar",
        "com.google.android.apps.messaging",
    )

    val ALWAYS_IGNORED: Set<String> = setOf(
        "android",
        "com.android.systemui",
        "com.google.android.permissioncontroller",
        "com.android.settings",
    )
}
