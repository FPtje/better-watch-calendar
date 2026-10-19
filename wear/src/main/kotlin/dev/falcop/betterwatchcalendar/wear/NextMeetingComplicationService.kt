package dev.falcop.betterwatchcalendar.wear

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataTimeline
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingTimelineComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.TimeInterval
import androidx.wear.watchface.complications.datasource.TimelineEntry
import dev.falcop.betterwatchcalendar.shared.ComplicationContent
import dev.falcop.betterwatchcalendar.shared.TIMELINE_HORIZON
import dev.falcop.betterwatchcalendar.shared.TimeFormat
import dev.falcop.betterwatchcalendar.shared.complicationTimeline
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * The `SHORT_TEXT` "next meeting" complication. Each request returns a 24-hour timeline, so the
 * watch face switches state on time without waking the app.
 */
class NextMeetingComplicationService : SuspendingTimelineComplicationDataSourceService() {
  override suspend fun onComplicationRequest(
    request: ComplicationRequest
  ): ComplicationDataTimeline? {
    if (request.complicationType != ComplicationType.SHORT_TEXT) return null
    val now = Instant.now()
    val timeline = complicationTimeline(graph.snapshots.current(), now, timeFormat(this))
    val tap = detailIntent(this)
    val ends = timeline.drop(1).map { it.from } + now.plus(TIMELINE_HORIZON)
    Log.i(TAG, "complication: ${timeline.size} states")
    timeline.forEach { Log.i(TAG, "  ${it.from} ${it.content.text} | ${it.content.title}") }
    return ComplicationDataTimeline(
      defaultComplicationData = timeline.first().content.toData(tap),
      timelineEntries =
        timeline.zip(ends).drop(1).map { (state, end) ->
          TimelineEntry(TimeInterval(state.from, end), state.content.toData(tap))
        },
    )
  }

  override fun getPreviewData(type: ComplicationType): ComplicationData? =
    if (type == ComplicationType.SHORT_TEXT)
      ComplicationContent("10:30", "Design review", "Next meeting: Design review at 10:30")
        .toData(tap = null)
    else null
}

/** The system time settings, read at the edge for the pure formatters. */
fun timeFormat(context: Context) =
  TimeFormat(ZoneId.systemDefault(), Locale.getDefault(), DateFormat.is24HourFormat(context))

private fun detailIntent(context: Context): PendingIntent =
  PendingIntent.getActivity(
    context,
    0,
    Intent(context, MainActivity::class.java),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
  )

private fun ComplicationContent.toData(tap: PendingIntent?): ShortTextComplicationData =
  ShortTextComplicationData.Builder(text.plain(), contentDescription.plain())
    .setTitle(title.plain())
    .setTapAction(tap)
    .build()

private fun String.plain() = PlainComplicationText.Builder(this).build()
