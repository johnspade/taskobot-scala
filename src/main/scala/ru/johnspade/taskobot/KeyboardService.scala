package ru.johnspade.taskobot

import cats.syntax.option.*
import ru.johnspade.taskobot.core.Chats
import ru.johnspade.taskobot.core.Ignore
import ru.johnspade.taskobot.core.Page
import ru.johnspade.taskobot.core.SetLanguage
import ru.johnspade.taskobot.core.TaskDetails
import ru.johnspade.taskobot.core.Tasks
import ru.johnspade.taskobot.core.TelegramOps.inlineKeyboardButton
import ru.johnspade.taskobot.messages.Language
import ru.johnspade.taskobot.messages.MessageService
import ru.johnspade.taskobot.messages.MsgId
import ru.johnspade.taskobot.messages.MsgId.*
import ru.johnspade.taskobot.task.BotTask
import ru.johnspade.taskobot.user.User
import telegramium.bots.InlineKeyboardMarkup
import telegramium.bots.KeyboardButton
import telegramium.bots.ReplyKeyboardMarkup
import telegramium.bots.WebAppInfo
import telegramium.bots.high.keyboards.InlineKeyboardButtons
import telegramium.bots.high.keyboards.InlineKeyboardMarkups
import telegramium.bots.high.keyboards.KeyboardButtons
import zio.URLayer
import zio.ZIO
import zio.ZLayer

trait KeyboardService:
  def chats(page: Page[User], `for`: User): InlineKeyboardMarkup

  def tasks(page: Page[BotTask], collaborator: User, language: Language): InlineKeyboardMarkup

  def languages(currentLanguage: Language): InlineKeyboardMarkup

  def menu(language: Language): ReplyKeyboardMarkup

final class KeyboardServiceLive(msgService: MessageService) extends KeyboardService:
  def chats(page: Page[User], `for`: User): InlineKeyboardMarkup = {
    lazy val prevButton = inlineKeyboardButton(msgService.previousPage(`for`.language), Chats(page.number - 1))
    lazy val nextButton = inlineKeyboardButton(msgService.nextPage(`for`.language), Chats(page.number + 1))
    val chatsButtons = page.items.map { user =>
      val chatName = if (user.id == `for`.id) msgService.personalTasks(`for`.language) else user.fullName
      List(inlineKeyboardButton(chatName, Tasks(0, user.id)))
    }
    val nextButtonRow = if (page.hasNext) List(nextButton) else List.empty
    val prevButtonRow = if (page.hasPrevious) List(prevButton) else List.empty
    val navButtons    = List(prevButtonRow, nextButtonRow)
    val supportButtonRow = List(
      List(
        InlineKeyboardButtons.url(
          msgService.getMessage(`buy-coffee`, `for`.language) + " ☕",
          "https://buymeacoff.ee/johnspade"
        )
      )
    )
    val keyboard = (chatsButtons ++ navButtons ++ supportButtonRow).filterNot(_.isEmpty)
    InlineKeyboardMarkup(keyboard)
  }

  def tasks(page: Page[BotTask], collaborator: User, language: Language): InlineKeyboardMarkup = {
    lazy val prevButton =
      inlineKeyboardButton(msgService.previousPage(language), Tasks(page.number - 1, collaborator.id))
    lazy val nextButton = inlineKeyboardButton(msgService.nextPage(language), Tasks(page.number + 1, collaborator.id))
    val tasksButtons = page.items.zipWithIndex
      .map { case (task, i) =>
        inlineKeyboardButton((i + 1).toString, TaskDetails(task.id, page.number))
      }
    val nextButtonRow = if (page.hasNext) List(nextButton) else List.empty
    val prevButtonRow = if (page.hasPrevious) List(prevButton) else List.empty
    val navButtons    = List(prevButtonRow, nextButtonRow)
    val listButtonRow = List(
      inlineKeyboardButton(
        msgService.getMessage(`chats-list`, language),
        Chats(0)
      )
    )
    val keyboard = (List(tasksButtons) ++ navButtons ++ List(listButtonRow)).filterNot(_.isEmpty)
    InlineKeyboardMarkup(keyboard)
  }

  def languages(currentLanguage: Language): InlineKeyboardMarkup =
    InlineKeyboardMarkups.singleColumn(
      Language.values.map { language =>
        val cbData = if (language == currentLanguage) Ignore else SetLanguage(language)
        inlineKeyboardButton(language.languageName, cbData)
      }.toList
    )

  def menu(language: Language): ReplyKeyboardMarkup =
    ReplyKeyboardMarkup(
      List(
        List(
          KeyboardButtons.text("\uD83D\uDCCB " + msgService.getMessage(MsgId.`tasks`, language)),
          KeyboardButtons.text("➕ " + msgService.getMessage(`tasks-personal-new`, language))
        ),
        List(
          KeyboardButtons.text("\uD83D\uDE80 " + msgService.getMessage(`tasks-collaborative-new`, language)),
          KeyboardButtons.text("❓ " + msgService.getMessage(`help`, language))
        ),
        List(
          KeyboardButtons.text("⚙️ " + msgService.getMessage(`settings`, language)),
          KeyboardButton(
            text = "🌍 " + msgService.getMessage(`timezone`, language),
            webApp = Some(WebAppInfo("https://timezones.johnspade.ru"))
          )
        )
      ),
      resizeKeyboard = true.some
    )

object KeyboardServiceLive:
  val layer: URLayer[MessageService, KeyboardService] =
    ZLayer(ZIO.service[MessageService].map(new KeyboardServiceLive(_)))
