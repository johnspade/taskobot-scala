package ru.johnspade.taskobot.settings

import cats.syntax.option._
import org.mockito.captor.ArgCaptor
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import ru.johnspade.taskobot.TestEnvironments
import ru.johnspade.taskobot.TestHelpers.{callbackQuery, mockMessage}
import ru.johnspade.taskobot.TestUsers.{john, johnChatId, johnTg}
import ru.johnspade.taskobot.core.TelegramOps.inlineKeyboardButton
import ru.johnspade.tgbot.callbackqueries.{CallbackQueryData, ContextCallbackQuery}
import ru.johnspade.taskobot.core.{ChangeLanguage, Ignore, SetLanguage}
import ru.johnspade.taskobot.i18n.Language
import ru.johnspade.taskobot.settings.SettingsController.SettingsController
import ru.johnspade.taskobot.user.UserRepository
import ru.johnspade.taskobot.user.UserRepository.UserRepository
import telegramium.bots.client.Method
import telegramium.bots.high._
import telegramium.bots.high.keyboards.{InlineKeyboardMarkups, KeyboardButtons}
import telegramium.bots.{ChatIntId, Message, ReplyKeyboardMarkup}
import zio.blocking.Blocking
import zio.test.Assertion.{equalTo, hasField, isSome}
import zio.test._
import zio.test.environment.TestEnvironment
import zio.{Task, URLayer, ZIO, ZLayer}

object SettingsControllerSpec extends DefaultRunnableSpec with MockitoSugar with ArgumentMatchersSugar {
  def spec: ZSpec[TestEnvironment, Throwable] = suite("SettingsControllerSpec")(
    suite("ChangeLanguage")(
      testM("should list languages") {
        for {
          reply <- sendChangeLanguageQuery()
          _ <- ZIO.effect(Thread.sleep(1000))
          listLanguagesAssertions = verifyMethodCalled(Methods.editMessageText(
            ChatIntId(johnChatId).some,
            messageId = 0.some,
            text = "Current language: English",
            replyMarkup = InlineKeyboardMarkups.singleColumn(
              List(
                inlineKeyboardButton("English", Ignore),
                inlineKeyboardButton("Русский", SetLanguage(Language.Russian)),
                inlineKeyboardButton("Turkish", SetLanguage(Language.Turkish)),
                inlineKeyboardButton("Italian", SetLanguage(Language.Italian)),
                inlineKeyboardButton("Traditional Chinese", SetLanguage(Language.TraditionalChinese))
              )
            ).some
          ))
          replyAssertions = assert(reply)(isSome(equalTo(Methods.answerCallbackQuery("0"))))
        } yield listLanguagesAssertions && replyAssertions
      }
    ),

    suite("SetLanguage")(
      testM("should change language") {
        for {
          reply <- sendSetLanguageQuery()
          _ <- ZIO.effect(Thread.sleep(1000))
          user <- UserRepository.findById(john.id)
          userAssertions = assert(user.get)(hasField("language", _.language, equalTo[Language, Language](Language.Russian)))
          listLanguagesAssertions = verifyMethodCalled(Methods.editMessageText(
            ChatIntId(johnChatId).some,
            messageId = 0.some,
            text = "Текущий язык: Русский",
            replyMarkup = InlineKeyboardMarkups.singleColumn(
              List(
                inlineKeyboardButton("English", SetLanguage(Language.English)),
                inlineKeyboardButton("Русский", Ignore),
                inlineKeyboardButton("Turkish", SetLanguage(Language.Turkish)),
                inlineKeyboardButton("Italian", SetLanguage(Language.Italian)),
                inlineKeyboardButton("Traditional Chinese", SetLanguage(Language.TraditionalChinese))
              )
            ).some
          ))
          replyAssertions = assert(reply)(isSome(equalTo(Methods.answerCallbackQuery("0"))))
          notificationAssertions = verifyMethodCalled(Methods.sendMessage(
            ChatIntId(johnChatId),
            text = "Язык изменен",
            replyMarkup = ReplyKeyboardMarkup(
              List(
                List(KeyboardButtons.text("\uD83D\uDCCB Задачи"), KeyboardButtons.text("➕ Новая личная задача")),
                List(KeyboardButtons.text("\uD83D\uDE80 Новая совместная задача")),
                List(KeyboardButtons.text("⚙️ Настройки"), KeyboardButtons.text("❓ Справка"))
              ),
              resizeKeyboard = true.some
            )
              .some
          ))
        } yield userAssertions && listLanguagesAssertions && replyAssertions && notificationAssertions
      }
    )
  )
    .provideCustomLayerShared(TestEnvironment.env)

  private def sendChangeLanguageQuery() =
    ZIO.accessM[SettingsController] {
      _.get
        .userRoutes
        .run(ContextCallbackQuery(
          john, CallbackQueryData(ChangeLanguage, callbackQuery(ChangeLanguage, johnTg, inlineMessageId = "0".some))
        ))
        .value
        .map(_.flatten)
    }

  private def sendSetLanguageQuery() = {
    val cbData = SetLanguage(Language.Russian)
    ZIO.accessM[SettingsController] {
      _.get
        .routes
        .run(CallbackQueryData(cbData, callbackQuery(cbData, johnTg, inlineMessageId = "0".some)))
        .value
        .map(_.flatten)
    }
  }

  private val botApiMock = mock[Api[Task]]
  when(botApiMock.execute[Message](*)).thenReturn(Task.succeed(mockMessage()))
  when(botApiMock.execute[Either[Boolean, Message]](*)).thenReturn(Task.right(mockMessage()))

  private def verifyMethodCalled[Res](method: Method[Res]) = {
    val captor = ArgCaptor[Method[Res]]
    verify(botApiMock, atLeastOnce).execute(captor).asInstanceOf[Unit]
    assert(captor.values.map(_.payload))(Assertion.exists(equalTo(method.payload)))
  }


  private object TestEnvironment {
    private val botApi = ZLayer.succeed(botApiMock)
    private val userRepo = UserRepository.live
    private val settingsController = botApi ++ userRepo >>> SettingsController.live

    val env: URLayer[Blocking, SettingsController with UserRepository] =
      TestEnvironments.itLayer >>> settingsController ++ userRepo
  }
}
