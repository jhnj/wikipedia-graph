package parser

import scala.util.matching.Regex

object LinkParser {
  val linkRegex: Regex =
    raw"/\[\[([^|\n\]]+)(?:\|[^\n\]]+)?\]\]|(&lt;!--)|(--&gt;)/".r
}
