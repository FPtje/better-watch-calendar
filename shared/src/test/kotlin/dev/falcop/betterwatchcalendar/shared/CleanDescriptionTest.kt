package dev.falcop.betterwatchcalendar.shared

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldNotContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

private fun fixture(name: String): String =
  checkNotNull(CleanDescriptionTest::class.java.getResource("/descriptions/$name")) { name }
    .readText()

class CleanDescriptionTest :
  FunSpec({
    context("real-world invite shapes") {
      test("legacy Google Meet block after the agenda") {
        cleanDescription(fixture("meet-legacy.txt")) shouldBe
          "Weekly planning for the platform team.\nBring your top three priorities."
      }

      test("a description that is only the Meet block becomes empty") {
        cleanDescription(fixture("meet-only.txt")) shouldBe ""
      }

      test("HTML from Google Calendar web, wrapped in html-blob, followed by Meet") {
        cleanDescription(fixture("meet-html.txt")) shouldBe
          """
          Hi all,

          This is the kick-off for the hackathon! You can already start before this time.
          Think of a good team name & bring snacks.

          • Pitch ideas
          • Form teams
          """
            .trimIndent()
      }

      test("newer Join with Google Meet / Join by phone blocks") {
        cleanDescription(fixture("meet-new-style.txt")) shouldBe "Quarterly review of the roadmap."
      }

      test("Zoom invite framed by box-drawing separators") {
        cleanDescription(fixture("zoom.txt")) shouldBe
          "Every quarter we look back at the OKRs and introduce the ones for Q4!"
      }

      test("text after a Zoom block survives") {
        cleanDescription(fixture("zoom-then-notes.txt")) shouldBe
          "Agenda:\n1. Hiring update\n2. Budget"
      }

      test("classic Microsoft Teams block") {
        cleanDescription(fixture("teams-classic.txt")) shouldBe
          "Let's align on the integration timeline."
      }

      test("new Teams block with an inner rule; angle-bracket addresses are not tags") {
        cleanDescription(fixture("teams-new.txt")) shouldBe
          "Contact <jane.doe@example.com> with questions.\n\nSee you there!"
      }

      test("an underscore line that isn't a Teams invite is kept; paragraphs stay apart") {
        cleanDescription(fixture("underscore-separator.txt")) shouldBe
          """
          We want to ensure our workplace remains a safe and supportive environment.

          ${"_".repeat(48)}

          Note:  You can join online or at the office. There will be a recording.
          """
            .trimIndent()
      }
    }

    context("HTML") {
      test("entities are decoded after tags are stripped") {
        cleanDescription("a &lt;b&gt; &amp; c&#33; &#x1F600; &unknown; &quot;q&quot;") shouldBe
          "a <b> & c! 😀 &unknown; \"q\""
      }

      test("links keep their text") {
        cleanDescription("""See <a href="https://x.example/doc">the doc</a>.""") shouldBe
          "See the doc."
      }

      test("comparison operators in plain text survive") {
        cleanDescription("latency < 200ms and > 50ms") shouldBe "latency < 200ms and > 50ms"
      }
    }

    context("whitespace and length") {
      test("runs of blank lines collapse to one, CRLF is normalized, edges are trimmed") {
        cleanDescription("\r\n\n  one  \r\n\r\n\r\n\n\ntwo\t\n\n") shouldBe "one\n\ntwo"
      }

      test("long text is capped at 2,000 characters with an ellipsis") {
        val out = cleanDescription("x".repeat(5000))
        out.length shouldBe MAX_DESCRIPTION_LENGTH
        out shouldEndWith "…"
      }

      test("truncation doesn't split a surrogate pair") {
        val out = cleanDescription("a" + "😀".repeat(1500))
        out.length shouldBeLessThanOrEqual MAX_DESCRIPTION_LENGTH
        Character.isHighSurrogate(out[out.length - 2]) shouldBe false
      }

      test("text at the limit is untouched") {
        cleanDescription("y".repeat(MAX_DESCRIPTION_LENGTH)) shouldBe "y".repeat(2000)
      }
    }

    test("property: output is bounded, trimmed, and has no runs of blank lines") {
      checkAll(Arb.string(0..3000)) { raw ->
        val out = cleanDescription(raw)
        out.length shouldBeLessThanOrEqual MAX_DESCRIPTION_LENGTH
        out shouldBe out.trim()
        out shouldNotContain "\n\n\n"
      }
    }
  })
