package io.github.fptje.betterwatchcalendar.shared

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class AttendeesAndRoomTest :
  FunSpec({
    context("attendees") {
      test("organizer first, then accepted, then alphabetical; user and rooms excluded") {
        val attendees =
          listOf(
            attendee(1, "zed@x", "zed", status = AttendeeStatus.Accepted),
            attendee(1, "amy@x", "Amy", status = AttendeeStatus.Invited),
            attendee(1, "bea@x", "Bea", status = AttendeeStatus.Accepted),
            attendee(1, "olga@x", "Olga", AttendeeType.Optional, AttendeeRelationship.Organizer),
            attendee(1, "Me@Work.example", "Me"),
            room(1, "Orion"),
          )
        attendeeList(attendees, "me@work.example") shouldBe
          AttendeeList(listOf("Olga", "Bea", "zed", "Amy"), more = 0)
      }

      test("falls back to the email address, and drops attendees with neither") {
        val attendees =
          listOf(attendee(1, "anon@x", name = " "), attendee(1, email = null, name = null))
        attendeeList(attendees, "me@x").names shouldBe listOf("anon@x")
      }

      test("caps the list and counts the rest") {
        val attendees = (1..45).map { attendee(1, "p$it@x", "Person %02d".format(it)) }
        val list = attendeeList(attendees, "me@x")
        list.names.size shouldBe MAX_ATTENDEES
        list.names.first() shouldBe "Person 01"
        list.more shouldBe 15
      }
    }

    context("room") {
      test("resource names win, joined with commas, falling back to their email") {
        val rooms = listOf(room(1, "Orion"), room(1, null, email = "c_42@resource.calendar"))
        resolveRoom(rooms, "Somewhere", "meet.google.com/x") shouldBe
          "Orion, c_42@resource.calendar"
      }

      test("then the event location") {
        resolveRoom(emptyList(), " Utrecht office ", "meet.google.com/x") shouldBe "Utrecht office"
      }

      test("then the conferencing service in the raw description") {
        resolveRoom(emptyList(), "", "Join: https://meet.google.com/abc") shouldBe "Google Meet"
        resolveRoom(emptyList(), null, "https://us02web.ZOOM.us/j/1") shouldBe "Zoom"
        resolveRoom(emptyList(), null, "<https://teams.microsoft.com/l/x>") shouldBe "Teams"
      }

      test("otherwise empty") {
        resolveRoom(emptyList(), null, "Just a chat") shouldBe ""
        detectConference("").shouldBeNull()
      }
    }
  })
