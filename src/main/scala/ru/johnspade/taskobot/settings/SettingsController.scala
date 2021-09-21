package ru.johnspade.taskobot.settings

import cats.syntax.option._
import ru.johnspade.taskobot.TelegramBotApi.TelegramBotApi
import ru.johnspade.taskobot.core.TelegramOps.toUser
import ru.johnspade.taskobot.core.{ChangeLanguage, SetLanguage}
import ru.johnspade.taskobot.i18n.{Language, messages}
import ru.johnspade.taskobot.user.UserRepository
import ru.johnspade.taskobot.user.UserRepository.UserRepository
import ru.johnspade.taskobot.{CbDataRoutes, CbDataUserRoutes, Keyboards, Messages}
import ru.johnspade.tgbot.callbackqueries.CallbackQueryDsl._
import ru.johnspade.tgbot.callbackqueries.{CallbackQueryContextRoutes, CallbackQueryRoutes}
import ru.makkarpov.scalingua.I18n._
import ru.makkarpov.scalingua.LanguageId
import telegramium.bots.high.Api
import telegramium.bots.high.Methods.{answerCallbackQuery, editMessageText, sendMessage}
import telegramium.bots.high.implicits._
import telegramium.bots.{CallbackQuery, ChatIntId}
import zio._
import zio.interop.catz._

object SettingsController {
  type SettingsController = Has[Service]

  trait Service {
    def routes: CbDataRoutes[Task]

    def userRoutes: CbDataUserRoutes[Task]
  }

  val live: URLayer[UserRepository with TelegramBotApi, SettingsController] = ZLayer.fromServices[
    UserRepository.Service,
    Api[Task],
    Service
  ] { (userRepo, api) =>
    new LiveSettingsController(userRepo)(api)
  }

  final class LiveSettingsController(
    userRepo: UserRepository.Service
  )(
    implicit api: Api[Task]
  ) extends Service {
    override val userRoutes: CbDataUserRoutes[Task] = CallbackQueryContextRoutes.of {

      case ChangeLanguage in cb as user =>
        listLanguages(cb, user.language).fork
          .as(answerCallbackQuery(cb.id).some)

    }

    override val routes: CbDataRoutes[Task] = CallbackQueryRoutes.of {

      case SetLanguage(language) in cb =>
        for {
          _ <- userRepo.createOrUpdateWithLanguage(toUser(cb.from).copy(language = language))
          implicit0(languageId: LanguageId) = LanguageId(language.value)
          _ <- (listLanguages(cb, language) *> notifyLanguageChanged(cb, language)).fork
          answer = answerCallbackQuery(cb.id)
        } yield answer.some

    }

    private def listLanguages(cb: CallbackQuery, language: Language): Task[Unit] = {
      implicit val languageId: LanguageId = LanguageId(language.value)
      ZIO.foreach_(cb.message) { msg =>
        editMessageText(
          ChatIntId(msg.chat.id).some,
          msg.messageId.some,
          text = Messages.currentLanguage(language),
          replyMarkup = Keyboards.languages(language).some
        )
          .exec[Task]
      }
    }

    private def notifyLanguageChanged(cb: CallbackQuery, language: Language): Task[Unit] = {
      implicit val languageId: LanguageId = LanguageId(language.value)
      ZIO.foreach_(cb.message) { msg =>
        sendMessage(
          ChatIntId(msg.chat.id),
          t"Language has been changed",
          replyMarkup = Keyboards.menu().some
        )
          .exec[Task]
      }
    }
  }
}
