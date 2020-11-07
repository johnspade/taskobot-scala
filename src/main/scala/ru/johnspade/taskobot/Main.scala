package ru.johnspade.taskobot

import ru.johnspade.taskobot.Environments.{AppEnvironment, appEnvironment}
import ru.johnspade.taskobot.Taskobot.Taskobot
import zio._
import zio.interop.catz._

object Main extends zio.App {
  val program: ZIO[AppEnvironment, Throwable, Unit] =
    for {
      _ <- FlywayMigration.migrate
      _ <- ZIO.accessM[Taskobot](_.get.useForever)
    } yield ()

  def run(args: List[String]): URIO[ZEnv, ExitCode] =
    program
      .provideSomeLayer(appEnvironment)
      .exitCode
}
