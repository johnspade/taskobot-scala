package ru.johnspade.taskobot.core

import cats.syntax.option.*
import ru.johnspade.taskobot.messages.Language
import zio.Scope
import zio.test.Assertion.{equalTo, isRight}
import zio.test.*

object CbDataSpec extends ZIOSpecDefault:
  override def spec: Spec[TestEnvironment with Scope, Any] = decodeSuite + encodeSuite

  private val decodeSuite = suite("decode")(
    test("should decode CSV as a case class") {
      assert(CbData.decode("0%911%1337"))(isRight(equalTo(ConfirmTask(911L.some, 1337L.some)))) &&
      assert(CbData.decode("1%1"))(isRight(equalTo(Chats(1)))) &&
      assert(CbData.decode("2%1%1337"))(isRight(equalTo(Tasks(1, 1337L)))) &&
      assert(CbData.decode("3%2%911"))(isRight(equalTo(CheckTask(2, 911L)))) &&
      assert(CbData.decode("4"))(isRight(equalTo(ChangeLanguage))) &&
      assert(CbData.decode("5%ru"))(isRight(equalTo(SetLanguage(Language.Russian)))) &&
      assert(CbData.decode("6"))(isRight(equalTo(Ignore)))
    }
  )

  private val encodeSuite = suite("encode")(
    test("should encode a case class as CSV") {
      assertTrue(ConfirmTask(911L.some, 1337L.some).toCsv == "0%911%1337") &&
      assertTrue(Chats(1).toCsv == "1%1") &&
      assertTrue(Tasks(1, 1337L).toCsv == "2%1%1337") &&
      assertTrue(CheckTask(2, 911L).toCsv == "3%2%911") &&
      assertTrue(ChangeLanguage.toCsv == "4") &&
      assertTrue(SetLanguage(Language.Russian).toCsv == "5%ru") &&
      assertTrue(Ignore.toCsv == "6")
    }
  )
