package parser

import java.nio.file.Paths
import java.sql.Connection

import cats.effect.IO
import fs2.io.file.readAll
import fs2.{Pipe, Sink, Stream, text}
import db.DB._

class SQLIndex(config: Config) {
  implicit val c: Config = config

  def createTable: (Connection) => Sink[IO, Unit] = {
    val query =
      """
      CREATE TABLE pages (
        title VARCHAR(256) PRIMARY KEY,
        offset INT
      );
      CREATE INDEX pages_offset ON pages (offset);
      PRAGMA synchronous = OFF;
        """
    executeUpdate(_ => query)
  }

  def insertOffset(connection: Connection): Sink[IO, (TitleAndLength, Long)] = {
    val query: ((TitleAndLength, Long)) => String = { case (t, offset) =>
      s"INSERT INTO pages (title, offset) values (${'"' + t.title + '"'}, $offset)"
    }
    executeUpdate(query)(connection)
  }

  def allTitles(): (Connection) => Stream[IO, Unit] = {
    connection: Connection =>
      Stream(()).covary[IO].to(createTable(connection)) ++
        readAll[IO](Paths.get(config.pagesWithRedirects), 4096)
          .through(text.utf8Decode)
          .through(text.lines)
          .through(parsePage)
          .through(accumulateOffset)
          .to(insertOffset(connection))
          .through(log)
  }

  val accumulateOffset: Pipe[IO, TitleAndLength, (TitleAndLength, Long)] =
    in => in.zipWith(in.scan(0L) { case (offset, titleAndLength) =>
      offset + titleAndLength.length
    })((_,_))

  case class TitleAndLength(title: String, length: Int)

  val parsePage: Pipe[IO,String,TitleAndLength] = {
    in => in.map(line => line.split('|')).map(arr => TitleAndLength(arr(0), arr.tail.length))
  }

  def log[A,B]: Pipe[IO, A, A] = {
    in => in.zipWithIndex.map(tuple => {
      if (tuple._2 % 1000 == 0)
        print(".")
      tuple._1
    })
  }

  val run: IO[Unit] = useDb(allTitles()).run

}

object SQLIndex {
  def run(config: Config): IO[Unit] = {
    val sqlIndex = new SQLIndex(config)
    sqlIndex.run
  }
}