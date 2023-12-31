package ru.johnspade.taskobot.datetime

import java.time.*

import zio.ZIO
import zio.ZLayer
import zio.test.Assertion.*
import zio.test.*

import telegramium.bots.*

import ru.johnspade.taskobot.core.TelegramOps.inlineKeyboardButton
import ru.johnspade.taskobot.core.*
import ru.johnspade.taskobot.messages.*

object DatePickerServiceSpec extends ZIOSpecDefault:
  private val taskId: Long         = 1L
  private val language: Language   = Language.English
  private val yearMonth: YearMonth = YearMonth.of(2023, 3)
  private val date: LocalDate      = LocalDate.of(2023, 3, 1)

  private val testEnv = ZLayer.make[DatePickerService](
    MsgConfig.live,
    MessageServiceLive.layer,
    DatePickerServiceLive.layer
  )

  def spec = suite("DatePickerService")(
    test("generateYearsKeyboard") {
      val expectedControlsRow = List(
        inlineKeyboardButton("<", Years(taskId, YearMonth.of(2013, 3))),
        inlineKeyboardButton("Cancel", TaskDetails(taskId, 0)),
        inlineKeyboardButton("Remove", RemoveTaskDeadline(taskId)),
        inlineKeyboardButton(">", Years(taskId, YearMonth.of(2033, 3)))
      )
      for
        datePickerService <- ZIO.service[DatePickerService]
        yearsKeyboard = datePickerService.generateYearsKeyboard(taskId, yearMonth, language)
      yield assert(yearsKeyboard.inlineKeyboard)(contains(expectedControlsRow))
    },
    test("generateMonthsKeyboard") {
      for
        datePickerService <- ZIO.service[DatePickerService]
        monthsKeyboard = datePickerService.generateMonthsKeyboard(taskId, 2023, language)
      yield assert(monthsKeyboard.inlineKeyboard.flatten)(exists(hasField("text", _.text, equalTo("Mar"))))
    },
    test("generateDaysKeyboard") {
      for
        datePickerService <- ZIO.service[DatePickerService]
        daysKeyboard = datePickerService.generateDaysKeyboard(taskId, date, language)
      yield assert(daysKeyboard.inlineKeyboard.flatten)(exists(hasField("text", _.text, equalTo("1"))))
    }
  ).provideLayerShared(testEnv)
