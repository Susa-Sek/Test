package de.shortblock.app.service

/**
 * Die Eskalation gegen den „Für dich“-Feed: erst tippen, dann bremsen, dann schliessen.
 *
 * **Warum es keine Wand mehr gibt.** v0.11 legte ein Overlay über den Feed. Es blieb nach dem
 * Verlassen von Instagram über fremden Apps stehen und machte das Gerät unbenutzbar — abgeräumt
 * wurde es nur im Ereignispfad, und aus einer nicht überwachten App kommt kein Ereignis. Die
 * Lehre steht in `CLAUDE.md` und gibt diesem Entwurf den Rahmen: **nur Mittel, die nichts
 * hinterlassen.** Ein Tipp, ein Zähler, ein Zurück-Scroll sind Vorgänge, keine Dinge. Stirbt
 * der Dienst mittendrin, bleibt nichts stehen.
 *
 * Reine Rechenregel, kein Android — wie [BackGuard] und [WakeRepair]. Der Zustand liegt im Dienst.
 */
object FeedGuard {

    /**
     * So oft wird versucht, auf „Gefolgt“ zu tippen.
     *
     * Greift der Tipp zweimal nicht, greift er hier nicht — dann übernimmt die Bremse, statt
     * weiter ins Leere zu tippen. Instagrams Kopfzeile ist dreimal umgebaut worden; blindes
     * Weitertippen war jedes Mal der Fehler.
     */
    const val TAP_LIMIT = 2

    /** So oft wird zurückgescrollt, bevor die harte Stufe kommt. */
    const val BRAKE_LIMIT = 8

    /**
     * Wie lange nach einer Bremsung ein Scroll-Ereignis als eigenes Echo gilt.
     *
     * **Das ist die Abbruchbedingung, kein Feinschliff.** Ein Zurück-Scroll erzeugt selbst ein
     * Scroll-Ereignis. Ohne dieses Gatter bremst die App gegen ihr eigenes Bremsen — eine
     * Endlosschleife, die Instagram unbedienbar macht. Was [BackGuard] für das Zurück leistet,
     * leistet [isEcho] fürs Scrollen.
     */
    const val ECHO_MS = 700L

    enum class Step {
        /** Der „Gefolgt“-Tab ist nachweisbar da — antippen und das Problem lösen. */
        TAP_FOLLOWING,

        /** Zurückscrollen: Der erste Beitrag bleibt, das Endlose ist unerreichbar. */
        BRAKE,

        /** Bleibt jemand trotz Bremse dran, wird Instagram geschlossen. */
        LEAVE,
    }

    /**
     * Die nächste Stufe — von der sanftesten aufwärts.
     *
     * @param tapTargetFound ob [FeedPolicy.followingTabToTap] ein Ziel geliefert hat.
     */
    fun next(tapTargetFound: Boolean, tapsTried: Int, brakesUsed: Int): Step = when {
        tapTargetFound && tapsTried < TAP_LIMIT -> Step.TAP_FOLLOWING
        brakesUsed < BRAKE_LIMIT -> Step.BRAKE
        else -> Step.LEAVE
    }

    /**
     * Ist dieses Scroll-Ereignis unser eigener Zurück-Scroll?
     *
     * @param lastBrakeAtMs Zeitpunkt der letzten Bremsung; 0 heisst „es gab noch keine“.
     */
    fun isEcho(lastBrakeAtMs: Long, nowMs: Long, windowMs: Long = ECHO_MS): Boolean {
        if (lastBrakeAtMs <= 0L) return false
        val since = nowMs - lastBrakeAtMs
        // Negative Differenz heisst zurückgestellte Systemuhr. Dann gilt es NICHT als Echo:
        // Eine Bremse, die sich für ihr eigenes Echo hält, bremst nie wieder.
        if (since < 0L) return false
        return since <= windowMs
    }
}
