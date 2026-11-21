package io.github.fptje.betterwatchcalendar.mobile

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber

/** What started a sync, shown in the debug log. */
enum class SyncTrigger {
  AppOpen,
  Toggle,
  Periodic,
  Manual,
  /** The content-URI trigger: something in the calendar database changed (SPEC P5a). */
  CalendarChange,
  /** Saved from the Settings tab. */
  SettingsChange,
}

@Serializable
data class AccountCount(@ProtoNumber(1) val account: String, @ProtoNumber(2) val meetings: Int)

/** A meeting as sent, kept so the log shows what the watch received. */
@Serializable
data class LoggedMeeting(
  @ProtoNumber(1) val start: Long,
  @ProtoNumber(2) val end: Long,
  @ProtoNumber(3) val title: String,
  @ProtoNumber(4) val room: String,
  @ProtoNumber(5) val account: String,
  @ProtoNumber(6) val attendees: Int,
  @ProtoNumber(7) val descriptionChars: Int,
)

/** One sync attempt (SPEC P8). */
@Serializable
data class SyncLogEntry(
  /** Epoch millis. */
  @ProtoNumber(1) val at: Long,
  @ProtoNumber(2) val trigger: SyncTrigger,
  @ProtoNumber(3) val durationMs: Long,
  @ProtoNumber(4) val accounts: List<AccountCount> = emptyList(),
  /** Encoded snapshot size, or null when nothing was sent. */
  @ProtoNumber(5) val payloadBytes: Int? = null,
  @ProtoNumber(6) val error: String? = null,
  /** Instances read but left out, as `reason → count`. */
  @ProtoNumber(7) val skipped: Map<String, Int> = emptyMap(),
  @ProtoNumber(8) val duplicates: Int = 0,
  /** Only kept on the newest [LOG_DETAIL_ENTRIES] entries to bound the file size. */
  @ProtoNumber(9) val meetings: List<LoggedMeeting> = emptyList(),
)

@Serializable data class SyncLogFile(@ProtoNumber(1) val entries: List<SyncLogEntry> = emptyList())

const val LOG_ENTRIES = 100
const val LOG_DETAIL_ENTRIES = 10

/** Prepends [entry], keeping [LOG_ENTRIES] entries and meeting lists on the newest few. */
fun SyncLogFile.append(entry: SyncLogEntry): SyncLogFile =
  SyncLogFile(
    (listOf(entry) + entries).take(LOG_ENTRIES).mapIndexed { i, e ->
      if (i < LOG_DETAIL_ENTRIES) e else e.copy(meetings = emptyList())
    }
  )

class SyncLog(private val store: DataStore<SyncLogFile>) {
  /** Newest first. */
  val entries: Flow<List<SyncLogEntry>> = store.data.map { it.entries }

  suspend fun add(entry: SyncLogEntry) {
    store.updateData { it.append(entry) }
  }
}

object SyncLogSerializer : Serializer<SyncLogFile> {
  override val defaultValue = SyncLogFile()

  override suspend fun readFrom(input: InputStream): SyncLogFile =
    try {
      ProtoBuf.decodeFromByteArray(SyncLogFile.serializer(), input.readBytes())
    } catch (e: SerializationException) {
      throw CorruptionException("unreadable sync log", e)
    }

  override suspend fun writeTo(t: SyncLogFile, output: OutputStream) {
    output.write(ProtoBuf.encodeToByteArray(SyncLogFile.serializer(), t))
  }
}
