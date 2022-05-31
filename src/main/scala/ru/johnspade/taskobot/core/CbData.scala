package ru.johnspade.taskobot.core

import ru.johnspade.taskobot.core.CbData.*
import ru.johnspade.tgbot.callbackdata.annotated.TypeId
import ru.johnspade.taskobot.messages.Language
import ru.johnspade.csv3s.printer.CsvPrinter
import ru.johnspade.csv3s.parser.*
import ru.johnspade.tgbot.callbackdata.annotated.MagnoliaRowEncoder
import ru.johnspade.csv3s.codecs.instances.given
import ru.johnspade.tgbot.callbackdata.annotated.MagnoliaRowDecoder
import ru.johnspade.tgbot.callbackqueries.{DecodeFailure, ParseError, DecodeError}
import ru.johnspade.csv3s.codecs.{RowDecoder, RowEncoder, StringDecoder, StringEncoder}
import ru.johnspade.csv3s.core.CSV

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

object CbData:
  given StringEncoder[Language] = _.value
  given StringDecoder[Language] = s => Language.withValue(s).toRight(StringDecoder.typeError(s, "Language"))

  given encoder: RowEncoder[CbData] = MagnoliaRowEncoder.derived
  given decoder: RowDecoder[CbData] = MagnoliaRowDecoder.derived
  private val Separator: Char       = '%'
  private val csvParser             = new CsvParser(Separator)
  val csvPrinter: CsvPrinter        = CsvPrinter.withSeparator(Separator)

  def decode(csv: String): Either[DecodeFailure, CbData] =
    parseRow(csv, csvParser).left
      .map(e => ParseError(e.toString))
      .flatMap(decoder.decode(_).left.map(e => DecodeError(e.getMessage)))
