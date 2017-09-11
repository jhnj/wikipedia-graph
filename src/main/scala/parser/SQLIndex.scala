package parser

import java.nio.file.Paths

import cats.data.{Reader, ReaderT}
import cats.effect.IO
import fs2.io.file.readAll
import fs2.{Pipe, Stream, text}
import db.DB._
import parser.SQLIndex.TitleAndLength
import runner.Config

class SQLIndex(config: Config) {
  implicit val c: Config = config

  def allTitles: Work[IO, Unit] = Reader { connection =>
    Stream(()).covary[IO].to(createTable(connection)) ++
      readAll[IO](Paths.get(config.pagesWithRedirects), 4096)
        .through(text.utf8Decode)
        .through(text.lines)
        .through(parsePage)
        .through(accumulateOffset)
        .to(insertOffset(connection))
  }

  val accumulateOffset: Pipe[IO, TitleAndLength, (TitleAndLength, Long)] =
    in => in.zipWithScan(0L) { case (offset, titleAndLength) =>
      offset + titleAndLength.length + 1
    }

  val parsePage: Pipe[IO,String,TitleAndLength] = {
    in => in.map(line => line.split('|')).map(arr => TitleAndLength(arr(0), arr.length))
  }

  val run: IO[Unit] = useDb(allTitles).run

}

object SQLIndex {
  case class TitleAndLength(title: String, length: Int)

  val run: ReaderT[IO,Config,Unit] = ReaderT { config =>
      val sqlIndex = new SQLIndex(config)
      sqlIndex.run
  }
}