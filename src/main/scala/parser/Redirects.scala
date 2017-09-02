package parser

import java.io
import java.nio.file.Paths

import cats.effect.IO
import fs2.io.file._
import fs2._

class Redirects(config: Config) {
  val redirects: String = config.redirects
  val titles: String = config.titles
  val filteredRedirects: String = config.filteredRedirects

  def allTitles(): Stream[IO, Set[String]] = {
    readAll[IO](Paths.get(titles), 4096)
      .through(text.utf8Decode)
      .through(text.lines)
      .fold(Set[String]())(_ + _)
  }

  def filterRedirects(titles: Set[String]): Stream[IO, Unit] = {
    readAll[IO](Paths.get(redirects), 4096)
      .through(text.utf8Decode)
      .through(text.lines)
      .map(_.split('|'))
      .filter(arr => isValidRedirect(arr(0), arr(1), titles))
      .map(arr => s"${arr(0)}|${arr(1)}")
      .intersperse("\n")
      .through(text.utf8Encode)
      .through(writeAll(Paths.get(filteredRedirects)))
  }

  def isValidRedirect(from: String, to: String, titles: Set[String]): Boolean =
    titles.contains(to) && !titles.contains(from)
}

object Redirects {
  def filterRedirects(config: Config): Stream[IO, Unit] = {
    val redirects = new Redirects(config)
    for {
      allTitles <- redirects.allTitles()
      _ <- redirects.filterRedirects(allTitles)
    } yield ()
  }
}
