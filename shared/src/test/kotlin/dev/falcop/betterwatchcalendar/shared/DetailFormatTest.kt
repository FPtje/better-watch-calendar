package dev.falcop.betterwatchcalendar.shared

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Instant

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

    context("detail content") {
      val standup =
        meeting(at(10), minutes = 15, title = "Standup", room = "Orion")
          .copy(
            description = "Agenda",
            attendees = listOf("Ada Lovelace", "Alan Turing"),
            moreAttendees = 3,
          )
      val review2 = meeting(at(11), title = "Review")
      val tomorrow = meeting(at(9, day = 20), title = "Planning")
      val snapshot = snapshotOf(standup, review2, tomorrow)

      fun schedule(snapshot: ScheduleSnapshot?, now: Instant) =
        detailContent(snapshot, now, format24).shouldBeInstanceOf<DetailContent.Schedule>()

      test("no snapshot ever received") {
        detailContent(null, at(9), format24) shouldBe DetailContent.WaitingForPhone
      }

      test("next meeting and the one after it, fully formatted") {
        val content = schedule(snapshot, at(9))
        content.cards shouldBe
          listOf(
            MeetingCard(
              id = standup.id,
              title = "Standup",
              time = "10:00–10:15",
              isCurrent = false,
              room = "Orion",
              description = "Agenda",
              attendees = listOf("Ada Lovelace", "Alan Turing"),
              moreAttendees = "+3 more",
            ),
            MeetingCard(review2.id, "Review", "11:00–11:30", false, "", "", emptyList(), null),
          )
        content.emptyMessage.shouldBeNull()
        content.footer shouldBe SyncFooter("Synced 08:00", staleWarning = null)
      }

      test("current meeting gets the now badge") {
        schedule(snapshot, at(10, 5)).cards.map { it.title to it.isCurrent } shouldContainExactly
          listOf("Standup" to true, "Review" to false)
      }

      test("after the first 10 minutes the next meeting is shown") {
        schedule(snapshot, at(10, 12)).cards.map { it.title } shouldContainExactly
          listOf("Review", "Planning")
      }

      test("a meeting on a later day gets a weekday prefix") {
        schedule(snapshot, at(11, 20)).cards.map { it.time } shouldContainExactly
          listOf("Tue 09:00–09:30")
      }

      test("nothing left in the snapshot") {
        val content = schedule(snapshot, at(10, day = 20))
        content.cards shouldBe emptyList()
        content.emptyMessage shouldBe NO_MEETINGS
        content.footer.staleWarning shouldBe "Phone not synced for 26 h"
      }

      test("an expired snapshot waits for the phone instead of claiming free time") {
        val content = schedule(snapshot, at(9, day = 26))
        content.cards shouldBe emptyList()
        content.emptyMessage shouldBe WAITING_FOR_PHONE
      }
    }
  })
