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
            case _ =>
              val message = msgService.getMessage(msgId, lang)
              assertTrue(message.nonEmpty)
        }
      }
    )
  )
    .provideCustomShared(testEnv)
