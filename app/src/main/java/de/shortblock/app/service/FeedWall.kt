package de.shortblock.app.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import de.shortblock.app.R

/**
 * Die Wand über dem „Für dich“-Feed.
 *
 * Sie ersetzt seit v0.11 das automatische Umschalten. Der alte Weg — Titel antippen, Menü
 * lesen, Eintrag antippen — hing an Instagrams Menüaufbau und ist dreimal gebrochen; jedes
 * Mal war der Filter danach still wirkungslos. Eine Wand braucht nur den Titeltext.
 *
 * Technisch derselbe Kniff wie bei [ReminderOverlay]: [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY]
 * braucht **keine** Berechtigung, die Bedienungshilfe erlaubt es von sich aus. Und weil
 * `FLAG_NOT_TOUCHABLE` fehlt, schluckt das Fenster jede Berührung in seinen Grenzen — genau
 * das sperrt das Scrollen.
 *
 * Drei Eigenschaften, jede davon notwendig:
 *
 * 1. **Die Kopfzeile bleibt frei.** Die Wand beginnt bei `topPx`, nicht bei 0. Deckte sie den
 *    Umschalter mit ab, verlangte sie etwas, das sie selbst unmöglich macht.
 * 2. **`FLAG_NOT_FOCUSABLE` bleibt.** Zurück-Taste und Startbildschirm funktionieren weiter.
 *    Eine Wand, aus der man nicht herauskommt, ist keine Sperre, sondern eine Geiselnahme.
 * 3. **Kein Selbstausblenden.** Anders als die Erinnerung steht sie, bis der Feed umgeschaltet
 *    ist. Das Aufräumen übernimmt der Dienst — inklusive Wächter gegen eine hängende Wand.
 */
class FeedWall(private val context: Context) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private var shown: View? = null
    private var shownTop = -1

    val isShowing: Boolean get() = shown != null

    /**
     * Wand hochziehen, beginnend bei [topPx].
     *
     * Idempotent: Steht sie schon an derselben Stelle, passiert nichts. Ohne diese Prüfung
     * würde sie bei jedem Baum-Scan neu gebaut und flackerte im Takt der Ereignisse.
     */
    fun show(topPx: Int): Boolean {
        if (shown != null && shownTop == topPx) return true

        val manager = windowManager ?: return false
        hide()

        val view = runCatching {
            LayoutInflater.from(context).inflate(R.layout.overlay_feed_wall, null)
        }.getOrNull() ?: return false

        view.findViewById<TextView>(R.id.wall_line).setText(R.string.feed_wall_line)
        view.findViewById<TextView>(R.id.wall_hint).setText(R.string.feed_wall_hint)
        // Berührungen enden hier. Ohne diesen Verbraucher reicht ein Wisch durch die Wand
        // hindurch an den Feed darunter.
        view.setOnTouchListener { _, _ -> true }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            y = topPx.coerceAtLeast(0)
        }

        val added = runCatching { manager.addView(view, params) }.isSuccess
        if (!added) return false

        shown = view
        shownTop = topPx
        return true
    }

    fun hide() {
        handler.removeCallbacksAndMessages(null)
        val view = shown ?: return
        shown = null
        shownTop = -1
        runCatching { windowManager?.removeView(view) }
    }
}
