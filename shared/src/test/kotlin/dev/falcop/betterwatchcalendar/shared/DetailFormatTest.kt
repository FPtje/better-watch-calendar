package dev.falcop.betterwatchcalendar.shared

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class DetailFormatTest :
  FunSpec({
    val review = meeting(at(10, 30), minutes = 45)

    test("time range today, in 24 h and 12 h") {
      meetingTimeRange(review, at(9), format24) shouldBe "10:30–11:15"
      meetingTimeRange(meeting(at(13)), at(9), format12) shouldBe "1:00–1:30"
    }

    test("weekday prefix when the meeting isn't today") {
      meetingTimeRange(review, at(9, day = 17), format24) shouldBe "Mon 10:30–11:15"
    }

    test("more-attendees label") {
      moreAttendeesLabel(0).shouldBeNull()
      moreAttendeesLabel(12) shouldBe "+12 more"
    }

    context("sync footer") {
      test("fresh") {
        syncFooter(at(14, 2).toEpochMilli(), at(14, 30), format24) shouldBe
          SyncFooter("Synced 14:02", staleWarning = null)
      }

      test("exactly one hour old is still fresh") {
        syncFooter(at(13).toEpochMilli(), at(14), format24).staleWarning.shouldBeNull()
      }

      test("stale for hours") {
        syncFooter(at(11).toEpochMilli(), at(14, 30), format24) shouldBe
          SyncFooter("Synced 11:00", "Phone not synced for 3 h")
      }

      test("stale for days, synced on an earlier day") {
        syncFooter(at(9, day = 16).toEpochMilli(), at(14), format24) shouldBe
          SyncFooter("Synced Fri 09:00", "Phone not synced for 3 d")
      }
    }
  })
