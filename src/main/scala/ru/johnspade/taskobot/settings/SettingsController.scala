package ru.johnspade.taskobot.settings

import zio.*
import zio.interop.catz.*

import cats.syntax.option.*
import ru.johnspade.tgbot.callbackqueries.CallbackQueryContextRoutes
import ru.johnspade.tgbot.callbackqueries.CallbackQueryDsl.*
import ru.johnspade.tgbot.callbackqueries.CallbackQueryRoutes
import telegramium.bots.CallbackQuery
import telegramium.bots.ChatIntId
import telegramium.bots.high.Api
import telegramium.bots.high.Methods.answerCallbackQuery
import telegramium.bots.high.Methods.editMessageText
import telegramium.bots.high.Methods.sendMessage
import telegramium.bots.high.implicits.*

import ru.johnspade.taskobot.CbDataRoutes
import ru.johnspade.taskobot.CbDataUserRoutes
import ru.johnspade.taskobot.KeyboardService
import ru.johnspade.taskobot.TelegramBotApi.TelegramBotApi
import ru.johnspade.taskobot.core.ChangeLanguage
import ru.johnspade.taskobot.core.SetLanguage
import ru.johnspade.taskobot.core.TelegramOps.execDiscardWithHandling
import ru.johnspade.taskobot.core.TelegramOps.toMessage
import ru.johnspade.taskobot.core.TelegramOps.toUser
import ru.johnspade.taskobot.messages.Language
import ru.johnspade.taskobot.messages.MessageService
import ru.johnspade.taskobot.messages.MsgId
import ru.johnspade.taskobot.user.UserRepository

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
    ZIO.foreachDiscard(cb.message.flatMap(_.toMessage)) { msg =>
      execDiscardWithHandling(
        editMessageText(
          msgService.currentLanguage(language),
          chatId = ChatIntId(msg.chat.id).some,
          messageId = msg.messageId.some,
          replyMarkup = kbService.languages(language).some
        )
      )
    }
  }

  private def notifyLanguageChanged(cb: CallbackQuery, language: Language): Task[Unit] = {
    ZIO.foreachDiscard(cb.message.flatMap(_.toMessage)) { msg =>
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
