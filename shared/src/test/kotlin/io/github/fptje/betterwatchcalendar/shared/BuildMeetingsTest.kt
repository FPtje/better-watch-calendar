package io.github.fptje.betterwatchcalendar.shared

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class BuildMeetingsTest :
  FunSpec({
    fun build(
      instances: List<RawInstance>,
      attendees: List<RawAttendee>,
      calendars: List<RawCalendar> = listOf(work, personal),
    ) = buildMeetings(calendars, instances, attendees)

    context("skipping") {
      test("cancelled") {
        val i = instance(1, status = EventStatus.Canceled)
        skipReason(i, work, meetingAttendees(1)) shouldBe SkipReason.Canceled
      }

      test("all-day") {
        skipReason(instance(1, allDay = true), work, meetingAttendees(1)) shouldBe SkipReason.AllDay
      }

      test("declined by the user") {
        val i = instance(1, selfStatus = AttendeeStatus.Declined)
        skipReason(i, work, meetingAttendees(1)) shouldBe SkipReason.Declined
      }

      test("no attendees at all: a personal block or reminder") {
        skipReason(instance(1), work, emptyList()) shouldBe SkipReason.NoOtherAttendees
      }

      test("only the user and a room: focus time with a booked room") {
        val attendees = listOf(attendee(1, "ME@work.example"), room(1, "Orion"))
        skipReason(instance(1), work, attendees) shouldBe SkipReason.NoOtherAttendees
      }

      test("events on an included secondary calendar are kept without attendees") {
        val family =
          RawCalendar(CalendarId(6), accountName = "me@gmail.example", "family@group.example")
        skipReason(instance(1, calendar = family), family, emptyList()).shouldBeNull()
      }

      test("tentative, invited and statusless instances with colleagues are kept") {
        listOf(AttendeeStatus.Tentative, AttendeeStatus.Invited, AttendeeStatus.None).forEach {
          skipReason(instance(1, selfStatus = it), work, meetingAttendees(1)).shouldBeNull()
        }
        skipReason(instance(1, status = null), work, meetingAttendees(1)).shouldBeNull()
      }

      test("skipped instances never reach the snapshot") {
        val instances =
          listOf(
            instance(1, allDay = true),
            instance(2, status = EventStatus.Canceled),
            instance(3, selfStatus = AttendeeStatus.Declined),
            instance(4),
          )
        val attendees = (1L..3L).flatMap { meetingAttendees(it) } + attendee(4, "me@work.example")
        build(instances, attendees).shouldBeEmpty()
      }
    }

    context("deduplication") {
      test("the same invite in two accounts is kept once, from the alphabetically first account") {
        val instances =
          listOf(
            instance(10, calendar = work, uid = "shared-uid"),
            instance(20, calendar = personal, uid = "shared-uid"),
          )
        val attendees = meetingAttendees(10, work) + meetingAttendees(20, personal)
        build(instances, attendees).map { it.id } shouldContainExactly
          listOf("5:20:${at(10).toEpochMilli()}")
      }

      test("occurrences of one recurring event at different times are all kept") {
        val instances = listOf(instance(10, begin = at(10)), instance(10, begin = at(10, day = 20)))
        build(instances, meetingAttendees(10)).size shouldBe 2
      }

      test("instances without a UID are never merged") {
        val instances = listOf(instance(10, uid = null), instance(11, uid = ""))
        build(instances, meetingAttendees(10) + meetingAttendees(11)).size shouldBe 2
      }

      test("a declined copy doesn't hide the accepted one") {
        val instances =
          listOf(
            instance(10, calendar = personal, uid = "u", selfStatus = AttendeeStatus.Declined),
            instance(20, calendar = work, uid = "u"),
          )
        val attendees = meetingAttendees(10, personal) + meetingAttendees(20, work)
        build(instances, attendees).map { it.id } shouldContainExactly
          listOf("29:20:${at(10).toEpochMilli()}")
      }
    }

    test("calendars that aren't included are dropped") {
      build(listOf(instance(1)), meetingAttendees(1), calendars = listOf(personal)).shouldBeEmpty()
    }

    test("a kept instance is normalized into a meeting") {
      val raw =
        instance(
          7,
          title = "  Design review ",
          description = "<p>Walk through the <b>states</b>.</p>",
          location = "Utrecht office",
        )
      val attendees =
        meetingAttendees(7) +
          room(7, "Utrecht (NL)-0-The Classroom (24)") +
          attendee(7, "bob@work.example", "Bob", status = AttendeeStatus.Tentative)
      build(listOf(raw), attendees) shouldContainExactly
        listOf(
          Meeting(
            id = "29:7:${at(10).toEpochMilli()}",
            title = "Design review",
            start = at(10).toEpochMilli(),
            end = at(10, 30).toEpochMilli(),
            room = "Utrecht (NL)-0-The Classroom (24)",
            description = "Walk through the states.",
            attendees = listOf("Ada Lovelace", "Bob"),
            moreAttendees = 0,
          )
        )
    }

    test("an untitled event gets Google's placeholder title") {
      build(listOf(instance(1, title = " ")), meetingAttendees(1)).single().title shouldBe UNTITLED
    }

    test("output is sorted by start") {
      val instances = listOf(instance(1, begin = at(15)), instance(2, begin = at(9)))
      build(instances, meetingAttendees(1) + meetingAttendees(2)).map { it.start } shouldBe
        listOf(at(9).toEpochMilli(), at(15).toEpochMilli())
    }

    test("the report counts meetings per account, skips and duplicates") {
      val instances =
        listOf(
          instance(1, calendar = work, uid = "shared"),
          instance(2, calendar = personal, uid = "shared"),
          instance(3, calendar = work, begin = at(11)),
          instance(4, calendar = work, allDay = true),
          instance(5, calendar = work),
        )
      val attendees =
        meetingAttendees(1, work) +
          meetingAttendees(2, personal) +
          meetingAttendees(3) +
          meetingAttendees(4)
      val report = buildReport(listOf(work, personal), instances, attendees)

      report.meetings.map { it.accountName } shouldContainExactly
        listOf("me@gmail.example", "me@work.example")
      report.perAccount shouldBe mapOf("me@work.example" to 1, "me@gmail.example" to 1)
      report.skipped shouldBe mapOf(SkipReason.AllDay to 1, SkipReason.NoOtherAttendees to 1)
      report.duplicates shouldBe 1
    }

    test("an included account without meetings is reported with zero") {
      buildReport(listOf(work, personal), emptyList(), emptyList()).perAccount shouldBe
        mapOf("me@work.example" to 0, "me@gmail.example" to 0)
    }
  })
