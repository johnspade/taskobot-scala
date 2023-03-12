package ru.johnspade.taskobot.settings

import cats.syntax.option.*
import ru.johnspade.taskobot.TelegramBotApi.TelegramBotApi
import ru.johnspade.taskobot.core.TelegramOps.toUser
import ru.johnspade.taskobot.core.{ChangeLanguage, SetLanguage}
import ru.johnspade.taskobot.messages.{Language, MessageService}
import ru.johnspade.taskobot.user.UserRepository
import ru.johnspade.taskobot.{CbDataRoutes, CbDataUserRoutes, KeyboardService}
import ru.johnspade.tgbot.callbackqueries.CallbackQueryDsl.*
import ru.johnspade.tgbot.callbackqueries.{CallbackQueryContextRoutes, CallbackQueryRoutes}
import ru.johnspade.taskobot.messages.MsgId
import telegramium.bots.high.Api
import telegramium.bots.high.Methods.{answerCallbackQuery, editMessageText, sendMessage}
import telegramium.bots.high.implicits.*
import telegramium.bots.{CallbackQuery, ChatIntId}
import zio.*
import zio.interop.catz.*

trait SettingsController:
  def routes: CbDataRoutes[Task]

  def userRoutes: CbDataUserRoutes[Task]

final class SettingsControllerLive(
    userRepo: UserRepository,
    msgService: MessageService,
    kbService: KeyboardService
)(implicit
    api: Api[Task]
) extends SettingsController:
  override val userRoutes: CbDataUserRoutes[Task] = CallbackQueryContextRoutes.of { case ChangeLanguage in cb as user =>
    listLanguages(cb, user.language)
      .as(answerCallbackQuery(cb.id).some)

  }

  override val routes: CbDataRoutes[Task] = CallbackQueryRoutes.of { case SetLanguage(language) in cb =>
    for
      _ <- userRepo.createOrUpdateWithLanguage(toUser(cb.from).copy(language = language))
      _ <- listLanguages(cb, language) *> notifyLanguageChanged(cb, language)
      answer = answerCallbackQuery(cb.id)
    yield answer.some

  }

  private def listLanguages(cb: CallbackQuery, language: Language): Task[Unit] = {
    ZIO.foreachDiscard(cb.message) { msg =>
      editMessageText(
        msgService.currentLanguage(language),
        ChatIntId(msg.chat.id).some,
        msg.messageId.some,
        replyMarkup = kbService.languages(language).some
      )
        .exec[Task]
    }
  }

  private def notifyLanguageChanged(cb: CallbackQuery, language: Language): Task[Unit] = {
    ZIO.foreachDiscard(cb.message) { msg =>
      sendMessage(
        ChatIntId(msg.chat.id),
        msgService.getMessage(MsgId.`languages-changed`, language),
        replyMarkup = kbService.menu(language).some
      )
        .exec[Task]
    }
  }

object SettingsControllerLive:
  val layer: URLayer[UserRepository with TelegramBotApi with MessageService with KeyboardService, SettingsController] =
    ZLayer(
      for
        userRepo   <- ZIO.service[UserRepository]
        msgService <- ZIO.service[MessageService]
        kbService  <- ZIO.service[KeyboardService]
        api        <- ZIO.service[TelegramBotApi]
      yield new SettingsControllerLive(userRepo, msgService, kbService)(api)
    )
