package de.shortblock.app.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/**
 * Nimmt kurz den Audio-Fokus, damit ein laufendes Video stumm wird.
 *
 * Das kleinste wirksame Mittel gegen einen Feed, und das einzige, das gar nicht navigiert: Eine
 * App, die den Audio-Fokus verliert, pausiert oder duckt — das ist derselbe Mechanismus, der
 * Instagram bei einem Anruf verstummen lässt. Ohne Ton verliert ein automatisch startendes
 * Video den grössten Teil seines Zugs.
 *
 * **Die Zeitschranke ist das Wesentliche, nicht das Detail.** Der Fokus wird nach
 * [MUTE_MS] von selbst zurückgegeben — über `postDelayed`, wie die Erinnerung sich selbst
 * abräumt. Ohne diese Selbstfreigabe wäre das genau die Sorte Hinterlassenschaft, an der die
 * Wand gescheitert ist: Wer Instagram verlässt, erzeugt kein Ereignis, auf das wir reagieren
 * könnten — und bis irgendwann eines käme, wäre der Ton **jeder** App gedämpft. Ein
 * stummgeschaltetes Telefon ist nicht so schlimm wie ein blockiertes, aber derselbe Fehler.
 *
 * Dünne Hülle um `AudioManager`; es gibt hier keine Logik, die sich auf der JVM prüfen liesse.
 */
class AudioDuck(context: Context) {

    private val manager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var request: AudioFocusRequest? = null

    private val release = Runnable { release() }

    /** Fordert den Fokus an, falls er nicht schon gehalten wird. */
    fun duck() {
        val audio = manager ?: return
        if (request != null) {
            // Schon stumm: nur die Uhr neu stellen, nicht ein zweites Mal anfordern.
            handler.removeCallbacks(release)
            handler.postDelayed(release, MUTE_MS)
            return
        }

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        // TRANSIENT_EXCLUSIVE: Der andere Spieler soll pausieren, nicht leiser werden. Und
        // „transient“ ist die Zusage, dass wir ihn zurückgeben — die unten eingehalten wird.
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { }
            .build()

        val granted = runCatching { audio.requestAudioFocus(focus) }
            .getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        if (granted != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return

        request = focus
        handler.postDelayed(release, MUTE_MS)
    }

    /** Gibt den Fokus zurück. Mehrfach aufzurufen ist harmlos. */
    fun release() {
        handler.removeCallbacks(release)
        val focus = request ?: return
        request = null
        runCatching { manager?.abandonAudioFocusRequest(focus) }
    }

    companion object {
        /**
         * So lange bleibt der Ton weg.
         *
         * Lange genug, dass ein automatisch startendes Video stumm bleibt, kurz genug, dass
         * niemand ein dauerhaft stummes Telefon hat, wenn der Dienst in diesem Moment stirbt.
         */
        const val MUTE_MS = 8_000L
    }
}
