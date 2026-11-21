package io.github.fptje.betterwatchcalendar.shared

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe

class SnapshotBudgetTest :
  FunSpec({
    fun detailed(hour: Int, day: Int = 19) =
      meeting(at(hour, day = day), title = "M$day-$hour")
        .copy(
          description = "d".repeat(MAX_DESCRIPTION_LENGTH),
          attendees = (1..MAX_ATTENDEES).map { "Attendee number $it" },
          moreAttendees = 2,
        )

    val week = (19..25).flatMap { day -> (9..17).map { hour -> detailed(hour, day) } }
    val big = snapshotOf(*week.toTypedArray(), syncedAt = at(8))

    test("a snapshot within budget is untouched") {
      val small = snapshotOf(detailed(10))
      fitToBudget(small) shouldBe small
    }

    test("strips detail from the latest meetings first until it fits") {
      (big.encode().size > SNAPSHOT_BUDGET_BYTES) shouldBe true

      val fitted = fitToBudget(big)
      fitted.encode().size shouldBeLessThanOrEqual SNAPSHOT_BUDGET_BYTES
      fitted.meetings.size shouldBe big.meetings.size
      fitted.windowEnd shouldBe big.windowEnd

      val stripped = fitted.meetings.map { it.description.isEmpty() }
      // Detail survives on a prefix and is gone from the rest.
      stripped shouldBe stripped.sorted()
      fitted.meetings.last().moreAttendees shouldBe MAX_ATTENDEES + 2
      fitted.meetings.take(FULL_DETAIL_COUNT) shouldBe big.meetings.take(FULL_DETAIL_COUNT)
    }

    test("the next three upcoming meetings keep detail even if past ones are stripped") {
      val past = detailed(6)
      val next = (9..11).map { detailed(it) }
      val snapshot = snapshotOf(past, *next.toTypedArray(), syncedAt = at(8))
      val fitted =
        fitToBudget(snapshot, budgetBytes = 1) { it.meetings.sumOf { m -> m.description.length } }
      fitted.meetings.first().description shouldBe ""
      fitted.meetings.drop(1) shouldBe next
    }

    test("as a last resort, drops the latest meetings and pulls in windowEnd") {
      val fitted = fitToBudget(big, budgetBytes = 400) { it.meetings.size * 100 }
      fitted.meetings shouldBe
        big.meetings.take(4).let { kept ->
          kept.take(FULL_DETAIL_COUNT) +
            kept.drop(FULL_DETAIL_COUNT).map {
              it.copy(description = "", attendees = emptyList(), moreAttendees = MAX_ATTENDEES + 2)
            }
        }
      fitted.windowEnd shouldBe big.meetings[4].start
    }
  })
