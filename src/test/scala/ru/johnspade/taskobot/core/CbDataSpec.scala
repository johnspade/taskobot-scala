package ru.johnspade.taskobot.core

import cats.syntax.option._
import ru.johnspade.taskobot.i18n.Language
import ru.johnspade.taskobot.tags.PageNumber
import ru.johnspade.taskobot.task.tags.TaskId
import ru.johnspade.taskobot.user.tags.UserId
import zio.test.Assertion.{equalTo, isRight}
import zio.test._
import zio.test.environment.TestEnvironment

object CbDataSpec extends DefaultRunnableSpec {
  override def spec: ZSpec[TestEnvironment, Nothing] = suite("decode")(
    test("should decode CSV as a case class") {
      assert(CbData.decode("0%911%1337"))(isRight(equalTo(ConfirmTask(TaskId(911L).some, UserId(1337L).some)))) &&
        assert(CbData.decode("1%1"))(isRight(equalTo(Chats(PageNumber(1))))) &&
        assert(CbData.decode("2%1%1337"))(isRight(equalTo(Tasks(PageNumber(1), UserId(1337L))))) &&
        assert(CbData.decode("3%2%911"))(isRight(equalTo(CheckTask(PageNumber(2), TaskId(911L))))) &&
        assert(CbData.decode("5%ru"))(isRight(equalTo(SetLanguage(Language.Russian))))
    },

    test("should be compatible with old formats") {
      assert(CbData.decode("0%%%911"))(isRight(equalTo(ConfirmTask(TaskId(911L).some, None)))) &&
        assert(CbData.decode("1%0%%"))(isRight(equalTo(Chats(PageNumber(0))))) &&
        assert(CbData.decode("2%1%1337%"))(isRight(equalTo(Tasks(PageNumber(1), UserId(1337L))))) &&
        assert(CbData.decode("3%2%1337%911"))(isRight(equalTo(CheckTask(PageNumber(2), TaskId(911L)))))
    }
  )
}
