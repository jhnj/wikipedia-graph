package parser

import scala.util.matching.Regex
import scala.util.matching.Regex.Match

object LinkParser {
  /*
   * Regex matching [[linkText]] or [[linkText|shown linkName]]
   * Or comments commentStart "<!--" / commentEnd "-->"
   */
  val linkRegex: Regex =
    raw"\[\[(?<linkText>[^|\n\]]+)(?:\|[^\n\]]+)?\]\]|(?<commentStart>&lt;!--)|(?<commentEnd>--&gt;)".r

  case class State(inComment: Boolean = false, links: List[String] = List[String]()) {
    def addLink(link: String): State = copy(links = link +: links)
  }
  def getLinks(text: String): List[String] = {
    linkRegex.findAllMatchIn(text).foldLeft(State()) { (state, matched) =>
      def getGroup = (group: String) =>
        Option(matched.group(group))

      getGroup("linkText").filter(_ => !state.inComment).map(state.addLink)
        .orElse(getGroup("commentStart")
          .map(_ => state.copy(inComment = true)))
        .orElse(getGroup("commentEnd")
          .map(_ => state.copy(inComment = false)))
        .getOrElse(state)
    }.links
  }

}
