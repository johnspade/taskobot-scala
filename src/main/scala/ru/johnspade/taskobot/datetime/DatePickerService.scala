package ru.johnspade.taskobot.datetime

import ru.johnspade.taskobot.core.DatePicker
import ru.johnspade.taskobot.core.Ignore
import ru.johnspade.taskobot.core.Months
import ru.johnspade.taskobot.core.RemoveTaskDeadline
import ru.johnspade.taskobot.core.TaskDeadlineDate
import ru.johnspade.taskobot.core.TelegramOps.inlineKeyboardButton
import ru.johnspade.taskobot.core.Years
import ru.johnspade.taskobot.messages.Language
import telegramium.bots.InlineKeyboardButton
import telegramium.bots.InlineKeyboardMarkup

import java.time.*
import java.time.format.TextStyle
import java.util.Locale
import ru.johnspade.taskobot.core.TaskDetails
import ru.johnspade.taskobot.messages.MessageService
import zio.ZLayer
import zio.ZIO
import ru.johnspade.taskobot.messages.MsgId

trait DatePickerService:
  def generateYearsKeyboard(taskId: Long, yearMonth: YearMonth, language: Language): InlineKeyboardMarkup
  def generateMonthsKeyboard(taskId: Long, year: Int, language: Language): InlineKeyboardMarkup
  def generateDaysKeyboard(taskId: Long, date: LocalDate, language: Language): InlineKeyboardMarkup

final class DatePickerServiceLive(msgService: MessageService) extends DatePickerService:
  private def weekRow(locale: Locale) = DayOfWeek
    .values()
    .map { d =>
      val narrowName = d.getDisplayName(TextStyle.NARROW_STANDALONE, locale)
      createIgnoredButton(narrowName)
    }
    .toList
  private val LengthOfWeek = 7

  // 5 + current year + 4 = 10
  private val PastYearsCount   = 5
  private val FutureYearsCount = 4

  private def cancelButton(language: Language, taskId: Long) =
    inlineKeyboardButton(msgService.getMessage(MsgId.`cancel`, language), TaskDetails(taskId, 0))
  private def removeButton(language: Language, taskId: Long) =
    inlineKeyboardButton(msgService.getMessage(MsgId.`remove`, language), RemoveTaskDeadline(taskId))

  override def generateYearsKeyboard(taskId: Long, yearMonth: YearMonth, language: Language): InlineKeyboardMarkup =
    val month = yearMonth.getMonth
    val years = yearMonth.getYear - PastYearsCount to yearMonth.getYear + FutureYearsCount
    val yearRows = years
      .map { year =>
        inlineKeyboardButton(year.toString, DatePicker(taskId, LocalDate.of(year, month, 1)))
      }
      .toList
      .grouped(5)
      .toList
      .reverse
    val controlsRow = List(
      inlineKeyboardButton("<", Years(taskId, YearMonth.of(years.previousRange.last - FutureYearsCount, month))),
      cancelButton(language, taskId),
      removeButton(language, taskId),
      inlineKeyboardButton(">", Years(taskId, YearMonth.of(years.nextRange.last - FutureYearsCount, month)))
    )
    InlineKeyboardMarkup((controlsRow +: yearRows).reverse)

  override def generateMonthsKeyboard(taskId: Long, year: Int, language: Language): InlineKeyboardMarkup =
    val monthRows = Month
      .values()
      .toList
      .map { month =>
        val shortName = month.getDisplayName(TextStyle.SHORT_STANDALONE, language.toLocale)
        inlineKeyboardButton(shortName, DatePicker(taskId, LocalDate.of(year, month, 1)))
      }
      .grouped(6)
      .toList
      .reverse
    val controlsRow = List(cancelButton(language, taskId), removeButton(language, taskId))
    InlineKeyboardMarkup((controlsRow +: monthRows).reverse)

  override def generateDaysKeyboard(taskId: Long, date: LocalDate, language: Language): InlineKeyboardMarkup =
    val locale                         = Locale.forLanguageTag(language.value)
    def createPlaceholders(count: Int) = List.fill(count)(createIgnoredButton(" "))

    val firstDay = date.withDayOfMonth(1)
    val headerRow = List(
      inlineKeyboardButton(
        firstDay.getMonth.getDisplayName(TextStyle.SHORT_STANDALONE, locale),
        Months(taskId, firstDay.getYear)
      ),
      inlineKeyboardButton(firstDay.getYear.toString, Years(taskId, YearMonth.from(firstDay)))
    )

    val lengthOfMonth = firstDay.lengthOfMonth

    val shiftStart = firstDay.getDayOfWeek.getValue - 1
    val shiftEnd   = LengthOfWeek - firstDay.withDayOfMonth(lengthOfMonth).getDayOfWeek.getValue

    val days = List.tabulate(lengthOfMonth) { n =>
      val day = firstDay.withDayOfMonth(n + 1)
      inlineKeyboardButton((n + 1).toString, TaskDeadlineDate(taskId, day))
    }
    val calendarRows = (createPlaceholders(shiftStart) ++ days.toList ++ createPlaceholders(shiftEnd))
      .grouped(LengthOfWeek)
      .toList

    val controlsRow = List(
      inlineKeyboardButton("<", DatePicker(taskId, firstDay.minusMonths(1))),
      cancelButton(language, taskId),
      removeButton(language, taskId),
      inlineKeyboardButton(">", DatePicker(taskId, firstDay.plusMonths(1)))
    )

    InlineKeyboardMarkup((headerRow :: weekRow(locale) :: calendarRows) :+ controlsRow)

  private def createIgnoredButton(text: String): InlineKeyboardButton = inlineKeyboardButton(text, Ignore)

  extension (range: Range)
    def shift(n: Int): Range = {
      val shiftedStart = range.start + n
      val shiftedEnd   = range.end + n

      (range, shiftedStart, shiftedEnd) match {
        case (r: Range.Inclusive, start, end) => Range.inclusive(start, end, r.step)
        case (_, start, end)                  => Range(start, end, range.step)
      }
    }

    def nextRange: Range     = shift(range.size)
    def previousRange: Range = shift(-range.size)
end DatePickerServiceLive

object DatePickerServiceLive:
  val layer: ZLayer[MessageService, Nothing, DatePickerServiceLive] =
    ZLayer(ZIO.service[MessageService].map(new DatePickerServiceLive(_)))
