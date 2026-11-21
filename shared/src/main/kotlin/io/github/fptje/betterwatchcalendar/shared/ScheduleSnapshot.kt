package io.github.fptje.betterwatchcalendar.shared

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/** Everything the watch needs for the next 7 days, pushed by the phone as one Data Layer item. */
@Serializable
data class ScheduleSnapshot(
  /** Bumped on breaking changes; the watch ignores versions it doesn't know. */
  @ProtoNumber(1) val schemaVersion: Int,
  /** Epoch millis. */
  @ProtoNumber(2) val syncedAt: Long,
  /** Epoch millis; selection beyond this is "unknown", not "free". */
  @ProtoNumber(3) val windowEnd: Long,
  /** Filtered, deduplicated, sorted by start. */
  @ProtoNumber(4) val meetings: List<Meeting>,
  /**
   * Regex whose matches are removed from complication titles (see [shortTitle]); blank for none.
   * Added without a version bump: older snapshots decode with the default.
   */
  @ProtoNumber(5) val titleFilter: String = "",
)

@Serializable
data class Meeting(
  /** `<calendarId>:<eventId>:<begin>`, unique per instance. */
  @ProtoNumber(1) val id: String,
  @ProtoNumber(2) val title: String,
  /** Epoch millis. */
  @ProtoNumber(3) val start: Long,
  /** Epoch millis. */
  @ProtoNumber(4) val end: Long,
  /** Empty when absent. */
  @ProtoNumber(5) val room: String,
  /** Cleaned plain text, at most 2,000 characters. */
  @ProtoNumber(6) val description: String,
  /** At most 30 entries; the rest are counted in [moreAttendees]. */
  @ProtoNumber(7) val attendees: List<String>,
  @ProtoNumber(8) val moreAttendees: Int,
)
