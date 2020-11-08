package ru.johnspade.taskobot.core

import kantan.csv.DecodeError.TypeError
import kantan.csv.ops._
import kantan.csv.{ReadResult, _}
import ru.johnspade.taskobot.core.CbData._
import ru.johnspade.taskobot.csv.MagnoliaRowDecoder._
import ru.johnspade.taskobot.csv.MagnoliaRowEncoder._
import ru.johnspade.taskobot.csv.TypeId
import ru.johnspade.taskobot.task.tags.TaskId
import supertagged.@@
import supertagged.lift.LiftF

sealed abstract class CbData extends Product with Serializable {
  def toCsv: String = this.writeCsvRow(csvConfig)
}

@TypeId(0)
final case class ConfirmTask(taskId: Option[TaskId]) extends CbData

@TypeId(4)
case object ChangeLanguage extends CbData

object CbData {
  val Separator: Char = '%'

  val csvConfig: CsvConfiguration = rfc.withCellSeparator(Separator)

  implicit def liftedCellEncoder[T, U](implicit cellEncoder: CellEncoder[T]): CellEncoder[T @@ U] =
    LiftF[CellEncoder].lift
  implicit def liftedCellDecoder[T, U](implicit cellDecoder: CellDecoder[T]): CellDecoder[T @@ U] =
    LiftF[CellDecoder].lift
  private def caseObjectRowCodec[T <: CbData](data: T): RowCodec[T] = RowCodec.from(_ => Right(data))(_ => Seq.empty)

  implicit val confirmTaskRowCodec: RowCodec[ConfirmTask] = RowCodec.caseOrdered(ConfirmTask.apply _)(ConfirmTask.unapply)
  implicit val changeLanguageRowCodec: RowCodec[ChangeLanguage.type] = caseObjectRowCodec(ChangeLanguage)

  def decode(csv: String): ReadResult[CbData] =
    csv.readCsv[List, CbData](csvConfig)
      .headOption
      .getOrElse(Left(TypeError("Callback data is missing")))
}
