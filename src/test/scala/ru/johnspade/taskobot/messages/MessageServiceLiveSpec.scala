package ru.johnspade.taskobot.messages

import zio.*
import zio.test.*

object MessageServiceLiveSpec extends ZIOSpecDefault:
  private val testEnv = ZLayer.make[MessageService](
    MsgConfig.live,
    MessageServiceLive.layer
  )

  def spec: Spec[TestEnvironment with Scope, Any] = suite("MessageServiceSpec")(
    ZIO.collectAll(
      for
        lang  <- Language.values.toList
        msgId <- MsgId.values.toList
      yield ZIO.serviceWith[MessageService] { msgService =>
        test(s"construct the '$msgId' message: $lang") {
          msgId match
            case MsgId.`tasks-personal-created` =>
              val message = msgService.taskCreated("test-task", lang)
              assertTrue(message.contains("test-task"))
            case MsgId.`languages-current` =>
              val message = msgService.currentLanguage(lang)
              assertTrue(message.contains(lang.languageName))
            case MsgId.`tasks-completed-by` =>
              val message = msgService.getMessage(MsgId.`tasks-completed-by`, lang, "test-task", "completed-by")
              assertTrue(message.contains("test-task")) && assertTrue(message.contains("completed-by"))
            case MsgId.`reminders-minutes-before` =>
              val message = msgService.remindersMinutesBefore(999, lang)
              assertTrue(message.contains("999"))
            case MsgId.`reminders-hours-before` =>
              val message = msgService.remindersHoursBefore(17, lang)
              assertTrue(message.contains("17"))
            case MsgId.`reminders-days-before` =>
              val message = msgService.remindersDaysBefore(31, lang)
              assertTrue(message.contains("31"))
            case MsgId.`reminders-reminder` =>
              val message = msgService.getMessage(MsgId.`reminders-reminder`, lang, "3d ", "4h ", "5m ")
              assertTrue(message.contains("3d 4h 5m"))
            case _ =>
              val message = msgService.getMessage(msgId, lang)
              assertTrue(message.nonEmpty)
        }
      }
    )
  )
    .provideCustomShared(testEnv)
