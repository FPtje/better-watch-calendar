package io.github.fptje.betterwatchcalendar.shared

import arrow.core.right
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
          "next meeting tomorrow, within 24 h",
          at(15, 30, day = 18),
          ComplicationContent(
            "14:30",
            "Design review",
            "Next meeting: Design review on Monday at 14:30 in Orion",
          ),
        ),
        Triple(
          "next meeting more than 24 h away",
          at(14, day = 18),
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

    val ownName = DEFAULT_TITLE_FILTER.replace("your_name_here", "Falco")

    test("the title filter shortens the title, but not the description") {
      val oneOnOne = snapshotOf(meeting(at(10), title = "Falco / Anna sync"), titleFilter = ownName)
      complicationContent(oneOnOne, at(9), format24) shouldBe
        ComplicationContent("10:00", "Anna sync", "Next meeting: Falco / Anna sync at 10:00")
      complicationContent(oneOnOne, at(10, 5), format24).title shouldBe "Anna sync"
      complicationTimeline(oneOnOne, at(9), format24).first().content.title shouldBe "Anna sync"
    }

    test("a blank or invalid title filter leaves the title alone") {
      listOf("", "(unclosed").forEach { pattern ->
        val m = snapshotOf(meeting(at(10), title = "Falco / Anna"), titleFilter = pattern)
        complicationContent(m, at(9), format24).title shouldBe "Falco / Anna"
      }
    }

    context("short title with the default filter, name filled in") {
      val filter = compileTitleFilter(ownName).getOrNull()
      withData(
        "Falco / Anna" to "Anna",
        "falco/anna 1:1" to "anna 1:1",
        "  FALCO  /  Anna" to "Anna",
        "Anna / Falco" to "Anna",
        "Falco" to "Falco",
        "Falco / " to "Falco / ",
        "Falcon / Anna" to "Falcon / Anna",
        "Anna / Falco / Bob" to "Anna / Falco / Bob",
        "Lunch with Falco" to "Lunch with Falco",
      ) { (title, expected) ->
        shortTitle(title, filter) shouldBe expected
      }
    }

    test("a filter that matches everything falls back to the full title") {
      shortTitle("Standup", Regex(".*")) shouldBe "Standup"
    }

    test("the default filter changes nothing until a name is filled in") {
      shortTitle("Falco / Anna", compileTitleFilter(DEFAULT_TITLE_FILTER).getOrNull()) shouldBe
        "Falco / Anna"
    }

    context("compile title filter") {
      test("blank is no filter") { compileTitleFilter("  ") shouldBe null.right() }
      test("syntax errors carry the position") {
        compileTitleFilter("ab(c").leftOrNull()?.index shouldBe 4
        compileTitleFilter("*a").leftOrNull()?.index shouldBe 0
      }
    }

    test("weekday follows the system locale") {
      val dutch = TimeFormat(amsterdam, Locale.forLanguageTag("nl-NL"), is24Hour = true)
      complicationContent(snapshotOf(review), at(9, day = 18), dutch).text shouldBe "ma"
    }

    test("the weekday is decided in the watch's time zone") {
      val lateNight = meeting(at(23, 30), title = "Late")
      val tokyo = TimeFormat(ZoneId.of("Asia/Tokyo"), Locale.US, is24Hour = true)
      // Monday 23:30 Amsterdam is already Tuesday 06:30 in Tokyo.
      val now = at(22, day = 18)
      complicationContent(snapshotOf(lateNight, syncedAt = now), now, tokyo).text shouldBe "Tue"
      complicationContent(snapshotOf(lateNight, syncedAt = now), now, format24).text shouldBe "Mon"
    }

    test("DST: a meeting the day after the clocks go back still shows its local time") {
      // Clocks go back on Sunday 2026-10-25 at 03:00 in Amsterdam.
      val monday = meeting(at(9, day = 26), title = "After DST")
      complicationContent(snapshotOf(monday, syncedAt = at(8, day = 25)), at(7, day = 26), format24)
        .text shouldBe "09:00"
      complicationContent(
          snapshotOf(monday, syncedAt = at(8, day = 25)),
          at(8, day = 25),
          format24,
        )
        .text shouldBe "Mon"
    }

    test("DST: a meeting later on the 25-hour day still shows its time") {
      val late = meeting(at(23, 30, day = 25), title = "Late")
      val now = at(0, day = 25)
      complicationContent(snapshotOf(late, syncedAt = now), now, format24).text shouldBe "23:30"
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

      test("a meeting coming within 24 h is a transition") {
        val tomorrow = meeting(at(10, day = 20))
        complicationTransitions(listOf(tomorrow), at(8), amsterdam) shouldContainExactly
          listOf(at(10), at(0, day = 20))
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

    context("timeline") {
      test("starts with the current state and lists each change") {
        complicationTimeline(snapshotOf(review), at(12), format24).map {
          it.from to it.content.text
        } shouldContainExactly listOf(at(12) to "14:30", at(14, 30) to "now", at(14, 40) to "—")
      }

      test("a weekday turns into a time 24 h before the meeting") {
        val tomorrow = meeting(at(10, day = 20))
        complicationTimeline(snapshotOf(tomorrow), at(8), format24).map {
          it.from to it.content.text
        } shouldContainExactly
          // Midnight only changes the description: "on Tuesday at 10:00" becomes "at 10:00".
          listOf(at(8) to "Tue", at(10) to "10:00", at(0, day = 20) to "10:00")
      }

      test("a midnight that changes nothing is merged away") {
        // Free before and after midnight.
        complicationTimeline(snapshotOf(review), at(15), format24).map {
          it.from
        } shouldContainExactly listOf(at(15))
      }

      test("the window end turns the content into sync") {
        val snapshot = snapshotOf(syncedAt = at(9, day = 12))
        complicationTimeline(snapshot, at(8), format24).map {
          it.from to it.content.title
        } shouldContainExactly listOf(at(8) to "free", at(9) to "sync")
      }

      test("no snapshot is a single sync state") {
        complicationTimeline(null, at(8), format24).map { it.content.title } shouldContainExactly
          listOf("sync")
      }

      test("property: each state matches complicationContent until the next one") {
        checkAll(meetingsArb(), instantArb()) { meetings, now ->
          val snapshot =
            snapshotOf(
              *meetings.toTypedArray(),
              syncedAt = now.minus(LOOKAHEAD).plusSeconds(12 * 3600L),
            )
          val timeline = complicationTimeline(snapshot, now, format24)
          timeline.first().from shouldBe now
          timeline.zipWithNext().forEach { (a, b) -> (a.content == b.content) shouldBe false }
          (0..48)
            .map { now.plusSeconds(it * 1800L) }
            .forEach { probe ->
              timeline.last { it.from <= probe }.content shouldBe
                complicationContent(snapshot, probe, format24)
            }
        }
      }
    }
  })
