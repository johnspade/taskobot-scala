package ru.johnspade.taskobot.datetime

import zio.*
import zio.test.*
import zio.test.Assertion.*

import telegramium.bots.InlineKeyboardMarkup

import ru.johnspade.taskobot.core.Ignore
import ru.johnspade.taskobot.core.TaskDetails
import ru.johnspade.taskobot.core.TelegramOps.inlineKeyboardButton
import ru.johnspade.taskobot.core.TimePicker
import ru.johnspade.taskobot.messages.Language
import ru.johnspade.taskobot.messages.MessageServiceLive
import ru.johnspade.taskobot.messages.MsgConfig

object TimePickerServiceSpec extends ZIOSpecDefault:
  private val testEnv = ZLayer.make[TimePickerService](
    MsgConfig.live,
    MessageServiceLive.layer,
    TimePickerServiceLive.layer
  )

  val spec = suite("TimePickerServiceSpec")(
    test("generateTimePicker should generate correct inline keyboard markup") {
      val taskId: Long                = 1
      val selectedHour: Option[Int]   = Some(10)
      val selectedMinute: Option[Int] = Some(15)
      val language: Language          = Language.English

      val expectedMarkup = InlineKeyboardMarkup(
        List(
          List(inlineKeyboardButton("Hours", Ignore)),
          List(
            inlineKeyboardButton("00", TimePicker(taskId, Some(0), selectedMinute)),
            inlineKeyboardButton("01", TimePicker(taskId, Some(1), selectedMinute)),
            inlineKeyboardButton("02", TimePicker(taskId, Some(2), selectedMinute)),
            inlineKeyboardButton("03", TimePicker(taskId, Some(3), selectedMinute)),
            inlineKeyboardButton("04", TimePicker(taskId, Some(4), selectedMinute)),
            inlineKeyboardButton("05", TimePicker(taskId, Some(5), selectedMinute))
          ),
          List(
            inlineKeyboardButton("06", TimePicker(taskId, Some(6), selectedMinute)),
            inlineKeyboardButton("07", TimePicker(taskId, Some(7), selectedMinute)),
            inlineKeyboardButton("08", TimePicker(taskId, Some(8), selectedMinute)),
            inlineKeyboardButton("09", TimePicker(taskId, Some(9), selectedMinute)),
            inlineKeyboardButton("[10]", TimePicker(taskId, Some(10), selectedMinute)),
            inlineKeyboardButton("11", TimePicker(taskId, Some(11), selectedMinute))
          ),
          List(
            inlineKeyboardButton("12", TimePicker(taskId, Some(12), selectedMinute)),
            inlineKeyboardButton("13", TimePicker(taskId, Some(13), selectedMinute)),
            inlineKeyboardButton("14", TimePicker(taskId, Some(14), selectedMinute)),
            inlineKeyboardButton("15", TimePicker(taskId, Some(15), selectedMinute)),
            inlineKeyboardButton("16", TimePicker(taskId, Some(16), selectedMinute)),
            inlineKeyboardButton("17", TimePicker(taskId, Some(17), selectedMinute))
          ),
          List(
            inlineKeyboardButton("18", TimePicker(taskId, Some(18), selectedMinute)),
            inlineKeyboardButton("19", TimePicker(taskId, Some(19), selectedMinute)),
            inlineKeyboardButton("20", TimePicker(taskId, Some(20), selectedMinute)),
            inlineKeyboardButton("21", TimePicker(taskId, Some(21), selectedMinute)),
            inlineKeyboardButton("22", TimePicker(taskId, Some(22), selectedMinute)),
            inlineKeyboardButton("23", TimePicker(taskId, Some(23), selectedMinute))
          ),
          List(inlineKeyboardButton("Minutes", Ignore)),
          List(
            inlineKeyboardButton("00", TimePicker(taskId, selectedHour, Some(0))),
            inlineKeyboardButton("[15]", TimePicker(taskId, selectedHour, Some(15))),
            inlineKeyboardButton("30", TimePicker(taskId, selectedHour, Some(30))),
            inlineKeyboardButton("45", TimePicker(taskId, selectedHour, Some(45)))
          ),
          List(
            inlineKeyboardButton("Cancel", TaskDetails(taskId, 0)),
            inlineKeyboardButton("OK", TimePicker(taskId, selectedHour, selectedMinute, confirm = true))
          )
        )
      )

      for
        timePicker <- ZIO.service[TimePickerService]
        actualMarkup = timePicker.generateTimePicker(taskId, selectedHour, selectedMinute, language)
      yield assert(actualMarkup)(equalTo(expectedMarkup))
    }
      .provideLayerShared(testEnv)
  )
