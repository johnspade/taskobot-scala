package ru.johnspade.taskobot.core

import telegramium.bots.{BoldMessageEntity, ItalicMessageEntity, MessageEntity, PreMessageEntity, TextLinkMessageEntity, TextMentionMessageEntity, User}

sealed abstract class TypedMessageEntity extends Product with Serializable {
  def text: String
}

object TypedMessageEntity {
  def toMessageEntities(entities: List[TypedMessageEntity]): List[MessageEntity] =
    entities.foldLeft((List.empty[MessageEntity], 0)) { case ((acc, offset), entity) =>
      def accumulate(me: MessageEntity) = (me :: acc, offset + me.length)

      entity match {
        case Plain(text) => (acc, offset + text.length)
        case Bold(text) => accumulate(BoldMessageEntity(offset, text.length))
        case Italic(text) => accumulate(ItalicMessageEntity(offset, text.length))
        case TextLinkMessage(text, url) => accumulate(TextLinkMessageEntity(offset, text.length, url))
        case TextMention(text, user) => accumulate(TextMentionMessageEntity(offset, text.length, user))
        case Pre(text, language) => accumulate(PreMessageEntity(offset, text.length, language))
      }
    }
      ._1
      .reverse

  final case class Plain(text: String) extends TypedMessageEntity {
    def value: String = text
  }

  object Plain {
    val lineBreak: Plain = Plain("\n")
  }

  final case class Bold(text: String) extends TypedMessageEntity

  final case class Italic(text: String) extends TypedMessageEntity

  final case class TextLinkMessage(text: String, url: String) extends TypedMessageEntity

  final case class TextMention(text: String, user: User) extends TypedMessageEntity

  final case class Pre(text: String, language: String) extends TypedMessageEntity

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
