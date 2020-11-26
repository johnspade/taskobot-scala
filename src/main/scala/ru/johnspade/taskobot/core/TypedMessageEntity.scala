package ru.johnspade.taskobot.core

import telegramium.bots.{MessageEntity, User}

sealed abstract class TypedMessageEntity extends Product with Serializable {
  def `type`: String
  def value: String
  def url: Option[String] = None
  def user: Option[User] = None
  def language: Option[String] = None
}

object TypedMessageEntity {
  def toMessageEntities(entities: List[TypedMessageEntity]): List[MessageEntity] =
    entities.foldLeft((List.empty[MessageEntity], 0)) { case ((acc, offset), entity) =>
      entity match {
        case Plain(text) => (acc, offset + text.length)
        case e: TypedMessageEntity =>
          val messageEntity = MessageEntity(e.`type`, offset, e.value.length, e.url, e.user, e.language)
          (messageEntity :: acc, offset + e.value.length)
      }
    }
      ._1
      .reverse

  final case class Plain(text: String) extends TypedMessageEntity {
    def `type`: String = "plain"
    def value: String = text
  }

  final case class Bold(text: String) extends TypedMessageEntity {
    def `type`: String = "bold"
    def value: String = text
  }

  final case class Italic(text: String) extends TypedMessageEntity {
    def `type`: String = "italic"
    def value: String = text
  }

  final case class Url(text: String, targetUrl: String) extends TypedMessageEntity {
    def `type`: String = "url"
    def value: String = text
    override def url: Option[String] = Some(targetUrl)
  }

  final case class TextMention(text: String, mentionedUser: User) extends TypedMessageEntity {
    def `type`: String = "text_mention"
    def value: String = text
    override def user: Option[User] = Some(mentionedUser)
  }

  final case class Pre(text: String, programmingLanguage: String) extends TypedMessageEntity {
    def `type`: String = "pre"
    def value: String = text
    override def language: Option[String] = Some(programmingLanguage)
  }

  implicit class StringMessageEntityHelper(val sc: StringContext) extends AnyVal {
    def plain(args: Any*): Plain = Plain(build(args: _*))

    def bold(args: Any*): Bold = Bold(build(args: _*))

    def italic(args: Any*): Italic = Italic(build(args: _*))

    private def build(args: Any*): String = {
      val strings = sc.parts.iterator
      val expressions = args.iterator
      val buf = new java.lang.StringBuilder(strings.next())
      while (expressions.hasNext) {
        buf.append(expressions.next().toString)
        buf.append(strings.next())
      }
      buf.toString
    }
  }
}
