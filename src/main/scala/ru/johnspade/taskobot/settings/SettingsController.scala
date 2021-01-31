package ru.johnspade.taskobot.settings

import cats.effect.ConcurrentEffect
import cats.syntax.option._
import ru.johnspade.taskobot.TelegramBotApi.TelegramBotApi
import ru.johnspade.taskobot.core.TelegramOps.toUser
import ru.johnspade.tgbot.callbackqueries.CallbackQueryDsl._
import ru.johnspade.tgbot.callbackqueries.{CallbackQueryContextRoutes, CallbackQueryRoutes}
import ru.johnspade.taskobot.core.{CbData, ChangeLanguage, SetLanguage}
import ru.johnspade.taskobot.i18n.{Language, messages}
import ru.johnspade.taskobot.user.UserRepository.UserRepository
import ru.johnspade.taskobot.user.{User, UserRepository}
import ru.johnspade.taskobot.{CbDataRoutes, CbDataUserRoutes, Keyboards, Messages}
import ru.makkarpov.scalingua.I18n._
import ru.makkarpov.scalingua.LanguageId
import telegramium.bots.high.Api
import telegramium.bots.high.Methods.{answerCallbackQuery, editMessageText}
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

  val live: URLayer[UserRepository with TelegramBotApi, SettingsController] = ZLayer.fromServicesM[
    UserRepository.Service,
    Api[Task],
    Any,
    Nothing,
    Service
  ] { (userRepo, api) =>
    ZIO.concurrentEffect.map { implicit CE: ConcurrentEffect[Task] =>
      new LiveSettingsController(userRepo)(api, CE)
    }
  }

  final class LiveSettingsController(
    userRepo: UserRepository.Service
  )(
    implicit api: Api[Task],
    CE: ConcurrentEffect[Task]
  ) extends Service {
    override val userRoutes: CbDataUserRoutes[Task] = CallbackQueryContextRoutes.of[CbData, User, Task] {

      case ChangeLanguage in cb as user =>
        listLanguages(cb, user.language).fork
          .as(answerCallbackQuery(cb.id).some)

    }

    override val routes: CbDataRoutes[Task] = CallbackQueryRoutes.of[CbData, Task] {

      case SetLanguage(language) in cb =>
        for {
          _ <- userRepo.createOrUpdate(toUser(cb.from).copy(language = language))
          implicit0(languageId: LanguageId) = LanguageId(language.languageTag)
          _ <- listLanguages(cb, language).fork
          answer = answerCallbackQuery(cb.id, t"Language has been changed".some)
        } yield answer.some

    }

    private def listLanguages(cb: CallbackQuery, language: Language): UIO[Unit] = {
      implicit val languageId: LanguageId = LanguageId(language.languageTag)
      ZIO.foreach_(cb.message) { msg =>
        editMessageText(
          ChatIntId(msg.chat.id).some,
          msg.messageId.some,
          text = Messages.currentLanguage(language),
          replyMarkup = Keyboards.languages.some
        )
          .exec
          .orDie
      }
    }
  }
}
