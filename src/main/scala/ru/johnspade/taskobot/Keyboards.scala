package ru.johnspade.taskobot

import ru.johnspade.taskobot.core.TelegramOps.inlineKeyboardButton
import ru.johnspade.taskobot.core.{Chats, Page, Tasks}
import ru.johnspade.taskobot.tags.PageNumber
import ru.johnspade.taskobot.user.User
import ru.makkarpov.scalingua.LanguageId
import telegramium.bots.high.InlineKeyboardMarkup

object Keyboards {
  def chats(page: Page[User], `for`: User)(implicit languageId: LanguageId): InlineKeyboardMarkup = {
    lazy val prevButton = inlineKeyboardButton("Previous page", Chats(PageNumber(page.number - 1)))
    lazy val nextButton = inlineKeyboardButton("Next page", Chats(PageNumber(page.number + 1)))
    val chatsButtons = page.items.map { user =>
      val fullName = if (user.id == `for`.id) Messages.personalTasks()
      else user.firstName + user.lastName.fold("")(n => s" $n")
      List(inlineKeyboardButton(fullName, Tasks(user.id, PageNumber(0))))
    }
    val nextButtonRow = if (page.hasNext) List(nextButton) else List.empty
    val prevButtonRow = if (page.hasPrevious) List(prevButton) else List.empty
    val navButtons = List(nextButtonRow, prevButtonRow)
    val keyboard = (navButtons ++ chatsButtons.reverse).reverse
    InlineKeyboardMarkup(keyboard)
  }
}
