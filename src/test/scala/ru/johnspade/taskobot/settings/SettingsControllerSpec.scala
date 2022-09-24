package ru.johnspade.taskobot.settings

import cats.syntax.option.*
import org.mockserver.client.MockServerClient
import ru.johnspade.taskobot.{KeyboardServiceLive, TestBotApi, TestDatabase}
import ru.johnspade.taskobot.TestHelpers.callbackQuery
import ru.johnspade.taskobot.TestUsers.{john, johnTg}
import ru.johnspade.tgbot.callbackqueries.{CallbackQueryData, ContextCallbackQuery}
import ru.johnspade.taskobot.core.{ChangeLanguage, SetLanguage}
import ru.johnspade.taskobot.messages.{Language, MessageServiceLive, MsgConfig}
import ru.johnspade.taskobot.settings.SettingsController
import ru.johnspade.taskobot.user.{UserRepository, UserRepositoryLive}
import ru.johnspade.taskobot.TelegramBotApi.TelegramBotApi
import ru.johnspade.taskobot.TestBotApi.{Mocks, createMock}
import telegramium.bots.high.*
import zio.test.Assertion.{equalTo, hasField}
import zio.test.*
import zio.*

object SettingsControllerSpec extends ZIOSpecDefault:
  def spec: Spec[TestEnvironment with Scope, Any] = suite("SettingsControllerSpec")(
    suite("ChangeLanguage")(
      test("should list languages") {
        for
          _     <- createMock(Mocks.listLanguages, Mocks.messageResponse)
          reply <- sendChangeLanguageQuery()
          replyAssertions = assertTrue(reply.contains(Methods.answerCallbackQuery("0")))
        yield replyAssertions
      }
    ),
    suite("SetLanguage")(
      test("should change language") {
        for
          _     <- createMock(Mocks.listLanguagesRussian, Mocks.messageResponse)
          _     <- createMock(Mocks.languageChangedMessage, Mocks.messageResponse)
          reply <- sendSetLanguageQuery()
          user  <- UserRepository.findById(john.id)
          userAssertions = assert(user.get)(
            hasField("language", _.language, equalTo[Language](Language.Russian))
          )
          replyAssertions = assertTrue(reply.contains(Methods.answerCallbackQuery("0")))
        yield userAssertions && replyAssertions
      }
    )
  )
    .provideCustomShared(env)

  private def sendChangeLanguageQuery() =
    ZIO.serviceWithZIO[SettingsController] {
      _.userRoutes
        .run(
          ContextCallbackQuery(
            john,
            CallbackQueryData(ChangeLanguage, callbackQuery(ChangeLanguage, johnTg, inlineMessageId = "0".some))
          )
        )
        .value
        .map(_.flatten)
    }

  private def sendSetLanguageQuery() =
    val cbData = SetLanguage(Language.Russian)
    ZIO.serviceWithZIO[SettingsController] {
      _.routes
        .run(CallbackQueryData(cbData, callbackQuery(cbData, johnTg, inlineMessageId = "0".some)))
        .value
        .map(_.flatten)
    }

  private val env =
    ZLayer.make[MockServerClient with SettingsController with UserRepository](
      TestDatabase.layer,
      TestBotApi.testApiLayer,
      UserRepositoryLive.layer,
      MsgConfig.live,
      MessageServiceLive.layer,
      KeyboardServiceLive.layer,
      SettingsControllerLive.layer
    )
