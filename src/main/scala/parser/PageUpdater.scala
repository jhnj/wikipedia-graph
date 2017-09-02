package parser

import java.nio.file.Paths

import cats.effect.IO
import fs2.io.file.{readAll, writeAll}
import fs2.{Stream, text}

class PageUpdater(config: Config) {
  val titles: String = config.titles
  val filteredRedirects: String = config.filteredRedirects
  val pages: String = config.pages
  val pagesWithRedirects: String = config.pagesWithRedirects

  private def allTitles(): IO[Set[String]] = {
    readAll[IO](Paths.get(titles), 4096)
      .through(text.utf8Decode)
      .through(text.lines)
      .runFold(Set[String]())(_ + _)
  }

  private def allRedirects(): IO[Map[String,String]] = {
    readAll[IO](Paths.get(filteredRedirects), 4096)
      .through(text.utf8Decode)
      .through(text.lines)
      .map(_.split('|'))
      .runFold(Map[String,String]())((map, arr) => map + (arr(0) -> arr(1)))
  }

  private def pages(titles: Set[String], redirects: Map[String, String]): IO[Unit] = {
    readAll[IO](Paths.get(pages), 4096)
      .through(text.utf8Decode)
      .through(text.lines)
      .map(_.split('|'))
      .map(arr => (arr(0), arr.tail))
      .map { case (title, links) =>
        s"$title|${updateLinks(links, titles, redirects).mkString("|")}" }
      .intersperse("\n")
      .through(text.utf8Encode)
      .through(writeAll(Paths.get(pagesWithRedirects)))
      .run
  }

  private def updateLinks(links: Array[String], titles: Set[String], redirects: Map[String,String]): Array[String] = {
    links.collect {
      case link if redirects.isDefinedAt(link) => redirects(link)
      case link if titles.contains(link) => link
    }
  }

  def updatePages(): IO[Unit] = for {
    pageUpdater <- Config.config.map(config => new PageUpdater(config))
    titles <- pageUpdater.allTitles()
    redirects <- pageUpdater.allRedirects()
    _ <- pageUpdater.pages(titles, redirects)
  } yield ()

}