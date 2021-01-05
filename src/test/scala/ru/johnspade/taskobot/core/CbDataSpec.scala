package ru.johnspade.taskobot.core

import ru.johnspade.taskobot.tags.PageNumber
import zio.test.Assertion.{equalTo, isRight}
import zio.test._
import zio.test.environment.TestEnvironment

object CbDataSpec extends DefaultRunnableSpec {
  override def spec: ZSpec[TestEnvironment, Nothing] = suite("decode")(
    test("should decode CSV as a case class") {
      assert(CbData.decode("1%1"))(isRight(equalTo(Chats(PageNumber(1)))))
    }
  )
}
