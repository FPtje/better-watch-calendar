package io.github.fptje.betterwatchcalendar.shared

import arrow.core.left
import arrow.core.right
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber

private val meetingArb = arbitrary {
  Meeting(
    id = Arb.string(0..40).bind(),
    title = Arb.string(0..80).bind(),
    start = Arb.long().bind(),
    end = Arb.long().bind(),
    room = Arb.string(0..40).bind(),
    description = Arb.string(0..200).bind(),
    attendees = Arb.list(Arb.string(0..30), 0..30).bind(),
    moreAttendees = Arb.int(0..1000).bind(),
  )
}

private val snapshotArb = arbitrary {
  ScheduleSnapshot(
    schemaVersion = ScheduleProtocol.SCHEMA_VERSION,
    syncedAt = Arb.long().bind(),
    windowEnd = Arb.long().bind(),
    meetings = Arb.list(meetingArb, 0..10).bind(),
    titleFilter = Arb.string(0..60).bind(),
  )
}

/** The snapshot as it was before `titleFilter`, to check that old payloads still decode. */
@Serializable
private data class SnapshotV1Fields(
  @ProtoNumber(1) val schemaVersion: Int,
  @ProtoNumber(2) val syncedAt: Long,
  @ProtoNumber(3) val windowEnd: Long,
  @ProtoNumber(4) val meetings: List<Meeting>,
)

class ScheduleProtocolTest :
  FunSpec({
    test("encode then decode round-trips") {
      checkAll(snapshotArb) { snapshot ->
        decodeSnapshot(snapshot.encode()) shouldBe snapshot.right()
      }
    }

    test("a snapshot without a title filter decodes with a blank one") {
      val old = SnapshotV1Fields(ScheduleProtocol.SCHEMA_VERSION, 1, 2, emptyList())
      val bytes = ProtoBuf.encodeToByteArray(SnapshotV1Fields.serializer(), old)
      decodeSnapshot(bytes).map { it.titleFilter } shouldBe "".right()
    }

    test("unknown schema versions are rejected") {
      val future =
        ScheduleSnapshot(schemaVersion = 2, syncedAt = 0, windowEnd = 0, meetings = emptyList())
      decodeSnapshot(future.encode()) shouldBe DecodeError.UnsupportedVersion(2).left()
    }

    test("garbage bytes are malformed, not an exception") {
      decodeSnapshot(byteArrayOf(0x0a, 0x7f, 0x01))
        .leftOrNull()
        .shouldBeInstanceOf<DecodeError.Malformed>()
    }
  })
