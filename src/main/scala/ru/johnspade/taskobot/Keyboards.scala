package ru.johnspade.taskobot

import ru.johnspade.taskobot.core.TelegramOps.inlineKeyboardButton
import ru.johnspade.taskobot.core.{Chats, CheckTask, Page, SetLanguage, Tasks}
import ru.johnspade.taskobot.i18n.Language
import ru.johnspade.taskobot.tags.PageNumber
import ru.johnspade.taskobot.task.BotTask
import ru.johnspade.taskobot.user.User
import ru.makkarpov.scalingua.LanguageId
import telegramium.bots.high.InlineKeyboardMarkup

object Keyboards {
  def chats(page: Page[User], `for`: User)(implicit languageId: LanguageId): InlineKeyboardMarkup = {
    lazy val prevButton = inlineKeyboardButton(Messages.previousPage(), Chats(PageNumber(page.number - 1)))
    lazy val nextButton = inlineKeyboardButton(Messages.nextPage(), Chats(PageNumber(page.number + 1)))
    val chatsButtons = page.items.map { user =>
      val chatName = if (user.id == `for`.id) Messages.personalTasks() else user.fullName
      List(inlineKeyboardButton(chatName, Tasks(user.id, PageNumber(0))))
    }
    val nextButtonRow = if (page.hasNext) List(nextButton) else List.empty
    val prevButtonRow = if (page.hasPrevious) List(prevButton) else List.empty
    val navButtons = List(prevButtonRow, nextButtonRow)
    val keyboard = (navButtons ++ chatsButtons).filterNot(_.isEmpty)
    InlineKeyboardMarkup(keyboard)
  }

  def tasks(page: Page[BotTask], collaborator: User)(implicit languageId: LanguageId): InlineKeyboardMarkup = {
    lazy val prevButton = inlineKeyboardButton(Messages.previousPage(), Tasks(collaborator.id, PageNumber(page.number - 1)))
    lazy val nextButton = inlineKeyboardButton(Messages.nextPage(), Tasks(collaborator.id, PageNumber(page.number + 1)))
    val tasksButtons = page
      .items
      .zipWithIndex
      .map { case (task, i) =>
        inlineKeyboardButton((i + 1).toString, CheckTask(task.id, page.number, collaborator.id))
      }
    val nextButtonRow = if (page.hasNext) List(nextButton) else List.empty
    val prevButtonRow = if (page.hasPrevious) List(prevButton) else List.empty
    val navButtons = List(prevButtonRow, nextButtonRow)
    val listButtonRow = List(inlineKeyboardButton("Chat list", Chats(PageNumber(0))))
    val keyboard = (List(tasksButtons) ++ navButtons ++ List(listButtonRow)).filterNot(_.isEmpty)
    InlineKeyboardMarkup(keyboard)
  }

  val languages: InlineKeyboardMarkup =
    InlineKeyboardMarkup.singleColumn(
      Language.values
        .map { language =>
          inlineKeyboardButton(language.languageName, SetLanguage(language))
        }
        .toList
    )
}
