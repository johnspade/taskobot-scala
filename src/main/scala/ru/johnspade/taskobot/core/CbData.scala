package ru.johnspade.taskobot.core

import ru.johnspade.csv3s.codecs.RowDecoder
import ru.johnspade.csv3s.codecs.RowEncoder
import ru.johnspade.csv3s.codecs.StringDecoder
import ru.johnspade.csv3s.codecs.StringEncoder
import ru.johnspade.csv3s.codecs.instances.given
import ru.johnspade.csv3s.parser.*
import ru.johnspade.csv3s.printer.CsvPrinter
import ru.johnspade.taskobot.core.CbData.*
import ru.johnspade.taskobot.messages.Language
import ru.johnspade.tgbot.callbackdata.annotated.MagnoliaRowDecoder
import ru.johnspade.tgbot.callbackdata.annotated.MagnoliaRowEncoder
import ru.johnspade.tgbot.callbackdata.annotated.TypeId
import ru.johnspade.tgbot.callbackqueries.DecodeError
import ru.johnspade.tgbot.callbackqueries.DecodeFailure
import ru.johnspade.tgbot.callbackqueries.ParseError

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import scala.util.Try

sealed abstract class CbData extends Product with Serializable:
  import CbData.{encoder, csvPrinter}

  def toCsv: String =
    csvPrinter.print(encoder.encode(this))

@TypeId(0)
final case class ConfirmTask(id: Option[Long], senderId: Option[Long]) extends CbData

@TypeId(1)
final case class Chats(page: Int) extends CbData

@TypeId(2)
final case class Tasks(pageNumber: Int, collaboratorId: Long) extends CbData

@TypeId(3)
final case class CheckTask(pageNumber: Int, id: Long) extends CbData

@TypeId(4)
case object ChangeLanguage extends CbData

@TypeId(5)
final case class SetLanguage(language: Language) extends CbData

@TypeId(6)
case object Ignore extends CbData

@TypeId(7)
final case class TaskDetails(id: Long, pageNumber: Int) extends CbData

@TypeId(8)
final case class DatePicker(taskId: Long, date: LocalDate) extends CbData

@TypeId(9)
final case class Years(taskId: Long, yearMonth: YearMonth) extends CbData

@TypeId(10)
final case class Months(taskId: Long, year: Int) extends CbData

@TypeId(11)
final case class TaskDeadlineDate(id: Long, date: LocalDate) extends CbData

@TypeId(12)
final case class RemoveTaskDeadline(id: Long) extends CbData

@TypeId(13)
final case class TimePicker(
    taskId: Long,
    hour: Option[Int] = None,
    minute: Option[Int] = None,
    confirm: Boolean = false
) extends CbData

object CbData:
  given StringEncoder[Language] = _.value
  given StringDecoder[Language] = s => Language.withValue(s).toRight(StringDecoder.typeError(s, "Language"))

  given StringEncoder[LocalDate] = _.format(DateTimeFormatter.ISO_LOCAL_DATE)
  given StringDecoder[LocalDate] = s =>
    Try(LocalDate.parse(s)).toEither.left.map { e =>
      ru.johnspade.csv3s.codecs.DecodeError.TypeError(s"$s is not valid LocalDate: ${e.getMessage()}")
    }

  given StringEncoder[YearMonth] = _.toString()
  given StringDecoder[YearMonth] = s =>
    Try(YearMonth.parse(s)).toEither.left.map { e =>
      ru.johnspade.csv3s.codecs.DecodeError.TypeError(s"$s is not valid YearMonth: ${e.getMessage()}")
    }

  given encoder: RowEncoder[CbData] = MagnoliaRowEncoder.derived
  given decoder: RowDecoder[CbData] = MagnoliaRowDecoder.derived
  private val Separator: Char       = '%'
  private val csvParser             = new CsvParser(Separator)
  val csvPrinter: CsvPrinter        = CsvPrinter.withSeparator(Separator)

  def decode(csv: String): Either[DecodeFailure, CbData] =
    parseRow(csv, csvParser).left
      .map(e => ParseError(e.toString))
      .flatMap(decoder.decode(_).left.map(e => DecodeError(e.getMessage)))
