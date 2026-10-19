package dev.falcop.betterwatchcalendar.shared

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import kotlinx.serialization.SerializationException
import kotlinx.serialization.protobuf.ProtoBuf

/** Constants shared by the phone sender and the watch receiver. */
object ScheduleProtocol {
  /** Data Layer path of the single, overwritten snapshot item. */
  const val PATH = "/schedule/v1"
  const val SCHEMA_VERSION = 1
}

sealed interface DecodeError {
  data class Malformed(val message: String) : DecodeError

  data class UnsupportedVersion(val version: Int) : DecodeError
}

fun ScheduleSnapshot.encode(): ByteArray =
  ProtoBuf.encodeToByteArray(ScheduleSnapshot.serializer(), this)

fun decodeSnapshot(bytes: ByteArray): Either<DecodeError, ScheduleSnapshot> = either {
  val snapshot =
    try {
      ProtoBuf.decodeFromByteArray(ScheduleSnapshot.serializer(), bytes)
    } catch (e: SerializationException) {
      raise(DecodeError.Malformed(e.message ?: "invalid protobuf"))
    } catch (e: IllegalArgumentException) {
      raise(DecodeError.Malformed(e.message ?: "invalid protobuf"))
    }
  ensure(snapshot.schemaVersion == ScheduleProtocol.SCHEMA_VERSION) {
    DecodeError.UnsupportedVersion(snapshot.schemaVersion)
  }
  snapshot
}
