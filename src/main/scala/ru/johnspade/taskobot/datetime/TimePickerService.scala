package ru.johnspade.taskobot.datetime

import ru.johnspade.taskobot.core.Ignore
import ru.johnspade.taskobot.core.TaskDetails
import ru.johnspade.taskobot.core.TelegramOps.inlineKeyboardButton
import ru.johnspade.taskobot.core.TimePicker
import ru.johnspade.taskobot.messages.MessageService
import telegramium.bots.InlineKeyboardMarkup
import zio.ZIO
import zio.ZLayer
import ru.johnspade.taskobot.messages.Language
import ru.johnspade.taskobot.messages.MsgId

trait TimePickerService:
  def generateTimePicker(
      taskId: Long,
      selectedHour: Option[Int],
      selectedMinute: Option[Int],
      language: Language
  ): InlineKeyboardMarkup

final class TimePickerServiceLive(msgService: MessageService) extends TimePickerService:
  override def generateTimePicker(
      taskId: Long,
      selectedHour: Option[Int],
      selectedMinute: Option[Int],
      language: Language
  ): InlineKeyboardMarkup =
    val hours   = (0 to 23).toList
    val minutes = List(0, 15, 30, 45)

    val minuteButtons = minutes.map { minute =>
      val minuteStr  = f"$minute%02d"
      val isSelected = selectedMinute.contains(minute)
      val buttonText = if (isSelected) s"[$minuteStr]" else minuteStr
      inlineKeyboardButton(buttonText, TimePicker(taskId, selectedHour, Some(minute)))
    }

    val hourRows = hours
      .grouped(6)
      .map(_.map { hour =>
        val hourStr    = f"$hour%02d"
        val isSelected = selectedHour.contains(hour)
        val buttonText = if (isSelected) s"[$hourStr]" else hourStr
        inlineKeyboardButton(buttonText, TimePicker(taskId, Some(hour), selectedMinute))
      })
      .toList

    val confirmAction =
      if selectedHour.isDefined && selectedMinute.isDefined
      then TimePicker(taskId, selectedHour, selectedMinute, confirm = true)
      else Ignore

    val keyboard = List(
      List(inlineKeyboardButton(msgService.getMessage(MsgId.`hours`, language), Ignore)),
      hourRows(0),
      hourRows(1),
      hourRows(2),
      hourRows(3),
      List(inlineKeyboardButton(msgService.getMessage(MsgId.`minutes`, language), Ignore)),
      minuteButtons,
      List(
        inlineKeyboardButton(msgService.getMessage(MsgId.`cancel`, language), TaskDetails(taskId, 0)),
        inlineKeyboardButton(msgService.getMessage(MsgId.`ok`, language), confirmAction)
      )
    )

    InlineKeyboardMarkup(keyboard.map(_.toList))
end TimePickerServiceLive

object TimePickerServiceLive:
  val layer: ZLayer[MessageService, Nothing, TimePickerServiceLive] =
    ZLayer(ZIO.service[MessageService].map(new TimePickerServiceLive(_)))
