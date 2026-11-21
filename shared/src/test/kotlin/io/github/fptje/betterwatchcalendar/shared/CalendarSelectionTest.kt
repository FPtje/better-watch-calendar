package io.github.fptje.betterwatchcalendar.shared

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class CalendarSelectionTest :
  FunSpec({
    fun info(
      id: Long,
      account: String,
      owner: String = account,
      name: String = owner,
      synced: Boolean = true,
    ) = CalendarInfo(RawCalendar(CalendarId(id), account, owner), name, synced, color = 0)

    val primary = info(29, "me@work.example")
    val shared = info(30, "me@work.example", "team@group.calendar.google.com", "Team")
    val unsyncedPrimary = info(15, "me@work.example", synced = false)

    context("defaults") {
      test("the account's own calendar is on, others are off") {
        isIncluded(primary, emptyMap()) shouldBe true
        isIncluded(shared, emptyMap()) shouldBe false
      }

      test("owner matching ignores case") {
        isIncluded(info(1, "Me@Gmail.example", "me@gmail.example"), emptyMap()) shouldBe true
      }
    }

    test("overrides win over defaults") {
      val overrides = mapOf(primary.id to false, shared.id to true)
      isIncluded(primary, overrides) shouldBe false
      isIncluded(shared, overrides) shouldBe true
    }

    test("an unsynced calendar is never included but keeps its toggle state") {
      toggleState(unsyncedPrimary, emptyMap()) shouldBe true
      isIncluded(unsyncedPrimary, emptyMap()) shouldBe false
      isIncluded(unsyncedPrimary, mapOf(unsyncedPrimary.id to true)) shouldBe false
    }

    test("grouping: accounts alphabetically; synced, then primary, then by name") {
      val room =
        info(11, "me@work.example", "c_1@resource.calendar.google.com", "Aardvark Room", false)
      val birthdays = info(3, "Me@gmail.example", "b@group.calendar.google.com", "Birthdays")
      val groups = groupByAccount(listOf(room, shared, unsyncedPrimary, birthdays, primary))
      groups.map { it.accountName } shouldContainExactly
        listOf("Me@gmail.example", "me@work.example")
      groups[1].calendars.map { it.id.value } shouldContainExactly listOf(29L, 30L, 15L, 11L)
    }
  })
