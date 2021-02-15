package ru.johnspade.taskobot.core

import kantan.csv.DecodeError.TypeError
import kantan.csv.enumeratum.values._
import kantan.csv.ops._
import kantan.csv.{ReadResult, _}
import ru.johnspade.taskobot.core.CbData._
import ru.johnspade.tgbot.callbackdata.annotated.MagnoliaRowDecoder._
import ru.johnspade.tgbot.callbackdata.annotated.MagnoliaRowEncoder._
import ru.johnspade.tgbot.callbackdata.annotated.TypeId
import ru.johnspade.taskobot.i18n.Language
import ru.johnspade.taskobot.tags.PageNumber
import ru.johnspade.taskobot.task.tags.TaskId
import ru.johnspade.taskobot.user.tags.UserId
import supertagged.@@
import supertagged.lift.LiftF

sealed abstract class CbData extends Product with Serializable {
  def toCsv: String = this.writeCsvRow(csvConfig)
}

@TypeId(0)
final case class ConfirmTask(id: Option[TaskId], senderId: Option[UserId]) extends CbData

@TypeId(1)
final case class Chats(page: PageNumber) extends CbData

@TypeId(2)
final case class Tasks(pageNumber: PageNumber, collaboratorId: UserId) extends CbData

@TypeId(3)
final case class CheckTask(pageNumber: PageNumber, id: TaskId) extends CbData

@TypeId(4)
case object ChangeLanguage extends CbData

@TypeId(5)
final case class SetLanguage(language: Language) extends CbData

object CbData {
  val Separator: Char = '%'

  val csvConfig: CsvConfiguration = rfc.withCellSeparator(Separator)

  implicit def liftedCellEncoder[T, U](implicit cellEncoder: CellEncoder[T]): CellEncoder[T @@ U] =
    LiftF[CellEncoder].lift
  implicit def liftedCellDecoder[T, U](implicit cellDecoder: CellDecoder[T]): CellDecoder[T @@ U] =
    LiftF[CellDecoder].lift
  private def caseObjectRowCodec[T <: CbData](data: T): RowCodec[T] = RowCodec.from(_ => Right(data))(_ => Seq.empty)

  implicit val confirmTaskRowCodec: RowCodec[ConfirmTask] = RowCodec.caseOrdered(ConfirmTask.apply _)(ConfirmTask.unapply)
  implicit val chatsRowCodec: RowCodec[Chats] = RowCodec.caseOrdered(Chats.apply _)(Chats.unapply)
  implicit val tasksRowCodec: RowCodec[Tasks] = RowCodec.caseOrdered(Tasks.apply _)(Tasks.unapply)
  implicit val checkTaskRowCodec: RowCodec[CheckTask] = RowCodec.caseOrdered(CheckTask.apply _)(CheckTask.unapply)
  implicit val changeLanguageRowCodec: RowCodec[ChangeLanguage.type] = caseObjectRowCodec(ChangeLanguage)
  implicit val setLanguageRowCodec: RowCodec[SetLanguage] = RowCodec.caseOrdered(SetLanguage.apply _)(SetLanguage.unapply)

  def decode(csv: String): ReadResult[CbData] = {
    def read(s: String) =
      s.readCsv[List, CbData](csvConfig)
        .headOption
        .getOrElse(Left(TypeError("Callback data is missing")))

    csv match {
      case c if c.startsWith("0%") => read(csv.replaceAll("(%)\\1+", "$1"))
      case s"3%$pageNumber%$_%$taskId" => Right(CheckTask(PageNumber(pageNumber.toInt), TaskId(taskId.toLong)))
      case _ => read(csv)
    }
  }
}
