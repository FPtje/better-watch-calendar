package dev.falcop.betterwatchcalendar.shared

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.property.checkAll
import java.time.ZoneId
import java.util.Locale

class ComplicationTest :
  FunSpec({
    val review = meeting(at(14, 30), minutes = 45, title = "Design review", room = "Orion")

    context("states, 24 h") {
      withData(
        nameFn = { it.first },
        Triple(
          "next meeting today",
          at(9),
          ComplicationContent(
            "14:30",
            "Design review",
            "Next meeting: Design review at 14:30 in Orion",
          ),
        ),
        Triple(
          "current, first 10 minutes",
          at(14, 35),
          ComplicationContent(
            "now",
            "Design review",
            "Current meeting: Design review, started at 14:30 in Orion",
          ),
        ),
        Triple(
          "next meeting on a later day",
          at(15, 30, day = 18),
          ComplicationContent(
            "Mon",
            "Design review",
            "Next meeting: Design review on Monday at 14:30 in Orion",
          ),
        ),
        Triple(
          "nothing left in the snapshot",
          at(14, 41),
          ComplicationContent(
            "—",
            "free",
            "No meetings in the next 7 days",
          ),
        ),
      ) { (_, now, expected) ->
        complicationContent(snapshotOf(review, syncedAt = at(8, day = 18)), now, format24) shouldBe
          expected
      }
    }

    context("states, 12 h") {
      test("compact time on top, AM/PM spelled out for screen readers") {
        complicationContent(snapshotOf(review), at(9), format12) shouldBe
          ComplicationContent(
            "2:30",
            "Design review",
            "Next meeting: Design review at 2:30 PM in Orion",
          )
      }

      test("morning meetings have no leading zero") {
        val early = meeting(at(9, 5), title = "Early")
        complicationContent(snapshotOf(early), at(8, 30), format12).text shouldBe "9:05"
        complicationContent(snapshotOf(early), at(8, 30), format24).text shouldBe "09:05"
      }
    }

    test("no snapshot ever received") {
      complicationContent(null, at(9), format24) shouldBe
        ComplicationContent("—", "sync", "Waiting for the phone to sync")
    }

    test("an expired snapshot is unknown, not free") {
      val snapshot = snapshotOf(syncedAt = at(8))
      complicationContent(snapshot, at(8).plus(LOOKAHEAD), format24).title shouldBe "sync"
    }

    test("the room is omitted from the description when empty") {
      val m = meeting(at(10), title = "Chat")
      complicationContent(snapshotOf(m), at(9), format24).contentDescription shouldBe
        "Next meeting: Chat at 10:00"
    }

    test("weekday follows the system locale") {
      val dutch = TimeFormat(amsterdam, Locale.forLanguageTag("nl-NL"), is24Hour = true)
      complicationContent(snapshotOf(review), at(9, day = 18), dutch).text shouldBe "ma"
    }

    test("'today' is decided in the watch's time zone") {
      val lateNight = meeting(at(23, 30), title = "Late")
      val tokyo = TimeFormat(ZoneId.of("Asia/Tokyo"), Locale.US, is24Hour = true)
      // 23:30 Amsterdam is already Tuesday 06:30 in Tokyo.
      complicationContent(snapshotOf(lateNight), at(9), tokyo).text shouldBe "Tue"
      complicationContent(snapshotOf(lateNight), at(9), format24).text shouldBe "23:30"
    }

    test("DST: a meeting the day after the clocks go back still shows its local time") {
      // Clocks go back on Sunday 2026-10-25 at 03:00 in Amsterdam.
      val monday = meeting(at(9, day = 26), title = "After DST")
      complicationContent(snapshotOf(monday, syncedAt = at(8, day = 25)), at(7, day = 26), format24)
        .text shouldBe "09:00"
      complicationContent(
          snapshotOf(monday, syncedAt = at(8, day = 25)),
          at(23, day = 25),
          format24,
        )
        .text shouldBe "Mon"
    }

    context("timeline transitions") {
      test("each start, end of the 'now' phase, and local midnight within 24 h") {
        val short = meeting(at(16), minutes = 5, title = "Short")
        val tomorrow = meeting(at(9, day = 20), title = "Tomorrow")
        val far = meeting(at(9, day = 21), title = "Too far")
        complicationTransitions(
          listOf(review, short, tomorrow, far),
          at(12),
          amsterdam,
        ) shouldContainExactly
          listOf(
            at(14, 30),
            at(14, 40),
            at(16),
            at(16, 5),
            at(0, day = 20),
            at(9, day = 20),
            at(9, 10, day = 20),
          )
      }

      test("only moments strictly after now are included") {
        complicationTransitions(listOf(review), at(14, 30), amsterdam) shouldContainExactly
          listOf(at(14, 40), at(0, day = 20))
      }

      test("a meeting entering the 7-day lookahead is a transition") {
        val nextWeek = meeting(at(10).plus(LOOKAHEAD).plusSeconds(3600))
        complicationTransitions(listOf(nextWeek), at(10), amsterdam) shouldContainExactly
          listOf(at(11), at(0, day = 20))
      }

      test("DST night still has exactly one midnight") {
        complicationTransitions(emptyList(), at(12, day = 24), amsterdam) shouldContainExactly
          listOf(at(0, day = 25))
      }

      test("property: content only changes at a transition") {
        checkAll(meetingsArb(), instantArb()) { meetings, now ->
          val snapshot = snapshotOf(*meetings.toTypedArray(), syncedAt = at(0))
          val edges = listOf(now) + complicationTransitions(meetings, now, amsterdam)
          val probes = (0..48).map { now.plusSeconds(it * 1800L - 1) }.filter { it >= now }
          probes.forEach { probe ->
            val lastEdge = edges.last { it <= probe }
            complicationContent(snapshot, probe, format24) shouldBe
              complicationContent(snapshot, lastEdge, format24)
          }
        }
      }
    }
  })
