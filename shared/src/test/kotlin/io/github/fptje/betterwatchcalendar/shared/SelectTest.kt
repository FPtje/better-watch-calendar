package io.github.fptje.betterwatchcalendar.shared

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.checkAll

class SelectTest :
  FunSpec({
    val standup = meeting(at(10), minutes = 15, title = "Standup")
    val review = meeting(at(11), minutes = 60, title = "Review")
    val lunch = meeting(at(12, 30), minutes = 45, title = "Lunch talk")
    val all = listOf(lunch, standup, review)

    test("before anything starts, the earliest upcoming meeting is next") {
      select(all, at(9)) shouldBe Selection.Next(standup, review)
    }

    test("a meeting is current from its start until 10 minutes in") {
      select(all, at(10)) shouldBe Selection.Current(standup, review)
      select(all, at(10, 9)) shouldBe Selection.Current(standup, review)
      select(all, at(10, 10)) shouldBe Selection.Next(review, lunch)
    }

    test("the current window ends at the meeting's end when that comes first") {
      val short = meeting(at(10), minutes = 5)
      select(listOf(short, review), at(10, 4)) shouldBe Selection.Current(short, review)
      select(listOf(short, review), at(10, 5)) shouldBe Selection.Next(review, null)
    }

    test("a zero-length meeting is never current") {
      val instant = meeting(at(10), minutes = 0)
      select(listOf(instant), at(10)) shouldBe Selection.Free
    }

    test("back-to-back: the next meeting takes over the moment it starts") {
      val first = meeting(at(10), minutes = 30, title = "First")
      val second = meeting(at(10, 30), minutes = 30, title = "Second")
      select(listOf(first, second), at(10, 29)) shouldBe Selection.Next(second, null)
      select(listOf(first, second), at(10, 30)) shouldBe Selection.Current(second, null)
    }

    test("of overlapping current meetings, the earliest start wins") {
      val early = meeting(at(10), minutes = 60, title = "Early")
      val late = meeting(at(10, 5), minutes = 60, title = "Late")
      select(listOf(late, early), at(10, 6)) shouldBe Selection.Current(early, late)
    }

    test("equal starts are ordered by end, then id") {
      val long = meeting(at(10), minutes = 60, id = "a")
      val shortB = meeting(at(10), minutes = 30, id = "b")
      val shortA = meeting(at(10), minutes = 30, id = "a2")
      select(listOf(long, shortB, shortA), at(9)) shouldBe Selection.Next(shortA, shortB)
    }

    test("the following meeting skips short meetings that have already ended") {
      val main = meeting(at(10), minutes = 60, title = "Main")
      val blip = meeting(at(10, 2), minutes = 2, title = "Blip")
      select(listOf(main, blip, review), at(10, 5)) shouldBe Selection.Current(main, review)
    }

    test("a long meeting past its first 10 minutes yields to the next one") {
      val workshop = meeting(at(9), minutes = 240, title = "Workshop")
      select(listOf(workshop, review), at(9, 30)) shouldBe Selection.Next(review, null)
    }

    test("meetings beyond the 7-day lookahead are ignored") {
      val far = meeting(at(10).plus(LOOKAHEAD).plusSeconds(60))
      select(listOf(far), at(10)) shouldBe Selection.Free
      select(listOf(review, far), at(10))
        .shouldBeInstanceOf<Selection.Next>()
        .following
        .shouldBeNull()
    }

    test("an empty list is free") { select(emptyList(), at(10)) shouldBe Selection.Free }

    test("property: the shown meeting is never in the past unless current") {
      checkAll(meetingsArb(), instantArb()) { meetings, now ->
        val t = now.toEpochMilli()
        when (val s = select(meetings, now)) {
          is Selection.Current -> s.meeting.isCurrentAt(t) shouldBe true
          is Selection.Next -> (s.meeting.start > t) shouldBe true
          Selection.Free ->
            meetings.none {
              it.isCurrentAt(t) || it.start in (t + 1)..t + LOOKAHEAD.toMillis()
            } shouldBe true
        }
      }
    }

    test("property: the following meeting is never the shown one and never before it") {
      checkAll(meetingsArb(), instantArb()) { meetings, now ->
        val s = select(meetings, now)
        if (s is Selection.Shown && s.following != null) {
          s.following shouldNotBe s.meeting
          (MeetingOrder.compare(s.following, s.meeting) > 0) shouldBe true
          (s.following!!.end > now.toEpochMilli()) shouldBe true
        }
      }
    }

    test("property: a current meeting wins over any upcoming meeting") {
      checkAll(meetingsArb(), instantArb()) { meetings, now ->
        if (meetings.any { it.isCurrentAt(now.toEpochMilli()) }) {
          select(meetings, now).shouldBeInstanceOf<Selection.Current>()
        }
      }
    }
  })
