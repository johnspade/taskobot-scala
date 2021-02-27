package ru.johnspade.taskobot

import cats.syntax.option._
import ru.johnspade.taskobot.core.TelegramOps.inlineKeyboardButton
import ru.johnspade.taskobot.core.{Chats, CheckTask, Page, SetLanguage, Tasks}
import ru.johnspade.taskobot.i18n.{Language, messages}
import ru.johnspade.taskobot.tags.PageNumber
import ru.johnspade.taskobot.task.BotTask
import ru.johnspade.taskobot.user.User
import ru.makkarpov.scalingua.I18n._
import ru.makkarpov.scalingua.LanguageId
import telegramium.bots.high.keyboards.{InlineKeyboardButtons, InlineKeyboardMarkups, KeyboardButtons}
import telegramium.bots.{InlineKeyboardMarkup, ReplyKeyboardMarkup}

object Keyboards {
  def chats(page: Page[User], `for`: User)(implicit languageId: LanguageId): InlineKeyboardMarkup = {
    lazy val prevButton = inlineKeyboardButton(Messages.previousPage(), Chats(PageNumber(page.number - 1)))
    lazy val nextButton = inlineKeyboardButton(Messages.nextPage(), Chats(PageNumber(page.number + 1)))
    val chatsButtons = page.items.map { user =>
      val chatName = if (user.id == `for`.id) Messages.personalTasks() else user.fullName
      List(inlineKeyboardButton(chatName, Tasks(PageNumber(0), user.id)))
    }
    val nextButtonRow = if (page.hasNext) List(nextButton) else List.empty
    val prevButtonRow = if (page.hasPrevious) List(prevButton) else List.empty
    val navButtons = List(prevButtonRow, nextButtonRow)
    val supportButtonRow = List(List(InlineKeyboardButtons.url("Buy me a coffee ☕", "https://buymeacoff.ee/johnspade")))
    val keyboard = (chatsButtons ++ navButtons ++ supportButtonRow).filterNot(_.isEmpty)
    InlineKeyboardMarkup(keyboard)
  }

  def tasks(page: Page[BotTask], collaborator: User)(implicit languageId: LanguageId): InlineKeyboardMarkup = {
    lazy val prevButton = inlineKeyboardButton(Messages.previousPage(), Tasks(PageNumber(page.number - 1), collaborator.id))
    lazy val nextButton = inlineKeyboardButton(Messages.nextPage(), Tasks(PageNumber(page.number + 1), collaborator.id))
    val tasksButtons = page
      .items
      .zipWithIndex
      .map { case (task, i) =>
        inlineKeyboardButton((i + 1).toString, CheckTask(page.number, task.id))
      }
    val nextButtonRow = if (page.hasNext) List(nextButton) else List.empty
    val prevButtonRow = if (page.hasPrevious) List(prevButton) else List.empty
    val navButtons = List(prevButtonRow, nextButtonRow)
    val listButtonRow = List(inlineKeyboardButton("Chat list", Chats(PageNumber(0))))
    val keyboard = (List(tasksButtons) ++ navButtons ++ List(listButtonRow)).filterNot(_.isEmpty)
    InlineKeyboardMarkup(keyboard)
  }

  val languages: InlineKeyboardMarkup =
    InlineKeyboardMarkups.singleColumn(
      Language.values
        .map { language =>
          inlineKeyboardButton(language.languageName, SetLanguage(language))
        }
        .toList
    )

  def menu()(implicit languageId: LanguageId): ReplyKeyboardMarkup =
    ReplyKeyboardMarkup(
      List(
        List(KeyboardButtons.text("\uD83D\uDCCB " + t"Tasks"), KeyboardButtons.text("➕ " + t"New personal task")),
        List(KeyboardButtons.text("\uD83D\uDE80 " + t"New collaborative task")),
        List(KeyboardButtons.text("⚙️ " + t"Settings"), KeyboardButtons.text("❓ " + t"Help"))
      ),
      resizeKeyboard = true.some
    )
}
