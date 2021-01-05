package ru.johnspade.taskobot

import zio.Has
import zio.blocking.Blocking
import zio.test.TestAspect.before
import zio.test.TestAspectAtLeastR

object MigrationAspects {
  val migrate: TestAspectAtLeastR[Has[Configuration.DbConfig] with Blocking] =
    before(FlywayMigration.migrate.orDie)
}
