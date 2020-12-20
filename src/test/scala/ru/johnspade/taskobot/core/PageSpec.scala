package ru.johnspade.taskobot.core

import cats.Id
import ru.johnspade.taskobot.tags.{Offset, PageNumber, PageSize}
import zio.test.environment.TestEnvironment
import zio.test._
import zio.test.Assertion._

object PageSpec extends DefaultRunnableSpec {
  private val items = List(1, 2, 3, 4, 5)
  private val makeRequest: (Offset, PageSize) => Id[List[Int]] =
    (offset, pageSize) => items.slice(offset.toInt, offset.toInt + pageSize)

  override def spec: ZSpec[TestEnvironment, Nothing] = suite("PageSpec")(
    test("single page") {
      val page = Page.request(PageNumber(0), PageSize(5), makeRequest)
      assert(page)(equalTo(Page(List(1, 2, 3, 4, 5), PageNumber(0), hasPrevious = false, hasNext = false)))
    },
    test("first page") {
      val page = Page.request(PageNumber(0), PageSize(3), makeRequest)
      assert(page)(equalTo(Page(List(1, 2, 3), PageNumber(0), hasPrevious = false, hasNext = true)))
    },
    test("page N") {
      val page = Page.request(PageNumber(1), PageSize(2), makeRequest)
      assert(page)(equalTo(Page(List(3, 4), PageNumber(1), hasPrevious = true, hasNext = true)))
    },
    test("last page") {
      val page = Page.request(PageNumber(2), PageSize(2), makeRequest)
      assert(page)(equalTo(Page(List(5), PageNumber(2), hasPrevious = true, hasNext = false)))
    }
  )
}
