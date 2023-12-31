package ru.johnspade.taskobot.datetime

import zio.*
import zio.interop.catz.*

import ru.johnspade.tgbot.callbackqueries.CallbackQueryContextRoutes
import ru.johnspade.tgbot.callbackqueries.CallbackQueryDsl.*
import telegramium.bots.CallbackQuery
import telegramium.bots.ChatIntId
import telegramium.bots.InlineKeyboardMarkup
import telegramium.bots.high.Api
import telegramium.bots.high.Methods.*

import ru.johnspade.taskobot.CbDataUserRoutes
import ru.johnspade.taskobot.TelegramBotApi.TelegramBotApi
import ru.johnspade.taskobot.core.DatePicker
import ru.johnspade.taskobot.core.Months
import ru.johnspade.taskobot.core.TelegramOps.execDiscardWithHandling
import ru.johnspade.taskobot.core.TelegramOps.toMessage
import ru.johnspade.taskobot.core.TimePicker
import ru.johnspade.taskobot.core.Years

trait DateTimeController:
  def routes: CbDataUserRoutes[Task]

final class DateTimeControllerLive(datePicker: DatePickerService, timePicker: TimePickerService)(using api: Api[Task])
    extends DateTimeController:
  override def routes: CbDataUserRoutes[Task] = CallbackQueryContextRoutes.of {
    case DatePicker(taskId, date) in cb as user =>
      editMarkup(cb, datePicker.generateDaysKeyboard(taskId, date, user.language))

    case Years(taskId, yearMonth) in cb as user =>
      editMarkup(cb, datePicker.generateYearsKeyboard(taskId, yearMonth, user.language))

    case Months(taskId, year) in cb as user =>
      editMarkup(cb, datePicker.generateMonthsKeyboard(taskId, year, user.language))

    case TimePicker(taskId, hour, minute, false) in cb as user =>
      editMarkup(cb, timePicker.generateTimePicker(taskId, hour, minute, user.language))
  }

  private def editMarkup(cb: CallbackQuery, markup: InlineKeyboardMarkup) =
    ZIO
      .foreachDiscard(cb.message.flatMap(_.toMessage)) { msg =>
        execDiscardWithHandling(
          editMessageReplyMarkup(
            chatId = Some(ChatIntId(msg.chat.id)),
            messageId = Some(msg.messageId),
            replyMarkup = Some(markup)
          )
        )
      }
      .as(Some(answerCallbackQuery(cb.id)))

object DateTimeControllerLive:
  val layer: ZLayer[TelegramBotApi & DatePickerService & TimePickerService, Nothing, DateTimeControllerLive] =
    ZLayer(
      for
        api        <- ZIO.service[TelegramBotApi]
        datePicker <- ZIO.service[DatePickerService]
        timePicker <- ZIO.service[TimePickerService]
      yield new DateTimeControllerLive(datePicker, timePicker)(using api)
    )
