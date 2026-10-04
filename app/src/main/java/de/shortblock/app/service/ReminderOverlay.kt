package de.shortblock.app.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import de.shortblock.app.R

/**
 * Das Kärtchen, das sich über die geblockte App legt.
 *
 * Warum ein eigenes Fenster statt eines Toasts: Ein Toast verschwindet, bevor man einen Satz
 * gelesen hat, und ab Android 12 werden Toasts aus dem Hintergrund ohnehin beschnitten. Ein
 * Satz, der zum Nachdenken bringen soll, braucht ein paar Sekunden Standzeit.
 *
 * Wichtig: [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY] braucht **keine**
 * Berechtigung „Über anderen Apps anzeigen“ — genau dafür gibt es den Fenstertyp. Bei einer
 * App, die fremde Bildschirme liest, ist jede eingesparte Berechtigung ein Argument.
 *
 * Schlägt das Einhängen fehl (ältere Hersteller-ROMs sind da eigenwillig), fällt der Aufrufer
 * auf den Toast zurück. Ein Popup darf den Blocker nie mit sich reißen.
 */
class ReminderOverlay(private val context: Context) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var shown: View? = null

    /** @return false, wenn das Fenster nicht gezeigt werden konnte — dann Toast benutzen. */
    fun show(line: String, detail: String?): Boolean {
        val manager = windowManager ?: return false
        hide()

        val view = runCatching {
            LayoutInflater.from(context).inflate(R.layout.overlay_reminder, null)
        }.getOrNull() ?: return false

        view.findViewById<TextView>(R.id.reminder_line).text = line
        view.findViewById<TextView>(R.id.reminder_detail).apply {
            text = detail.orEmpty()
            visibility = if (detail.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        view.findViewById<Button>(R.id.reminder_dismiss).setOnClickListener { hide() }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // NOT_FOCUSABLE: Die darunterliegende App behält Tastatur und Zurück-Taste. Ein
            // Fenster, das den Fokus stiehlt, macht aus einer Erinnerung eine Geiselnahme.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
        }

        val added = runCatching { manager.addView(view, params) }.isSuccess
        if (!added) return false

        shown = view
        handler.postDelayed(::hide, VISIBLE_MS)
        return true
    }

    fun hide() {
        handler.removeCallbacksAndMessages(null)
        val view = shown ?: return
        shown = null
        runCatching { windowManager?.removeView(view) }
    }

    private companion object {
        /** Lang genug für zwei Zeilen, kurz genug, dass es nicht im Weg steht. */
        const val VISIBLE_MS = 4_000L
    }
}
