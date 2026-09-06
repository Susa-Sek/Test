package de.klarzeit.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import de.klarzeit.app.MainActivity
import de.klarzeit.app.R
import de.klarzeit.app.data.AppCatalog
import de.klarzeit.app.data.GoalState
import de.klarzeit.app.data.ScreenTimeRepository
import de.klarzeit.app.data.TimeFormat
import kotlinx.coroutines.runBlocking

/**
 * Das Widget auf dem Startbildschirm: die bereinigte Zahl, daneben die Gesamtzeit, darunter
 * die drei grössten Zeitfresser, die auch zählen.
 *
 * Android gibt einem Widget nur ein paar Sekunden, deshalb steht hier `runBlocking` — das
 * Lesen der Nutzungsdaten dauert Millisekunden, und ein Widget, das seinen Inhalt später
 * nachreicht, zeigt beim Einblenden erst einmal nichts. Der regelmässige Anstoss kommt von
 * [WidgetRefreshWorker]; `updatePeriodMillis` allein greift frühestens alle 30 Minuten.
 */
class KlarzeitWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, buildViews(context)) }
    }

    override fun onEnabled(context: Context) {
        // Sobald das erste Widget liegt, lohnt der Viertelstundentakt.
        WidgetRefreshWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        WidgetRefreshWorker.cancel(context)
    }

    companion object {

        /** Von aussen anstossen, etwa nachdem sich die Ausschlussliste geändert hat. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, KlarzeitWidget::class.java))
            if (ids.isEmpty()) return
            val views = buildViews(context)
            ids.forEach { id -> manager.updateAppWidget(id, views) }
        }

        private fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_klarzeit)
            val today = runBlocking { ScreenTimeRepository(context).load() }

            views.setOnClickPendingIntent(R.id.widget_net, openApp(context))
            views.setOnClickPendingIntent(R.id.widget_label, openApp(context))

            if (!today.hasPermission) {
                views.setTextViewText(R.id.widget_net, "—")
                views.setTextViewText(R.id.widget_total, "")
                views.setTextViewText(R.id.widget_app_1, context.getString(R.string.widget_no_permission))
                views.setViewVisibility(R.id.widget_goal, View.GONE)
                views.setTextViewText(R.id.widget_app_2, "")
                views.setTextViewText(R.id.widget_app_3, "")
                return views
            }

            views.setTextViewText(R.id.widget_net, TimeFormat.short(today.summary.countedMillis))
            views.setTextViewText(
                R.id.widget_total,
                context.getString(R.string.widget_total_short, TimeFormat.short(today.summary.totalMillis)),
            )

            // Über dem Ziel wird die Zahl rot — das ist die einzige Wertung, die das
            // Widget sich erlaubt.
            val over = today.status == GoalState.Status.OVER
            views.setTextColor(
                R.id.widget_net,
                context.getColor(if (over) R.color.widget_over else R.color.widget_text),
            )

            if (today.goalMillis > 0) {
                views.setViewVisibility(R.id.widget_goal, View.VISIBLE)
                views.setTextViewText(
                    R.id.widget_goal,
                    if (over) {
                        context.getString(
                            R.string.home_goal_over,
                            TimeFormat.short(today.summary.countedMillis - today.goalMillis),
                        )
                    } else {
                        context.getString(
                            R.string.home_goal_left,
                            TimeFormat.short(today.goalMillis - today.summary.countedMillis),
                        )
                    },
                )
                views.setTextColor(
                    R.id.widget_goal,
                    context.getColor(if (over) R.color.widget_over else R.color.widget_muted),
                )
            } else {
                views.setViewVisibility(R.id.widget_goal, View.GONE)
            }

            val catalog = AppCatalog(context)
            val rows = listOf(R.id.widget_app_1, R.id.widget_app_2, R.id.widget_app_3)
            val top = today.summary.topCounted(rows.size)
            rows.forEachIndexed { index, viewId ->
                val app = top.getOrNull(index)
                views.setTextViewText(
                    viewId,
                    if (app == null) {
                        ""
                    } else {
                        "${catalog.label(app.packageName)}   ${TimeFormat.short(app.millis)}"
                    },
                )
            }

            return views
        }

        private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
