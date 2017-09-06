package parser

import java.nio.file.Paths
import java.sql.{Connection, DriverManager}

import cats.effect.IO
import fs2.io.file.readAll
import fs2.{Pipe, Sink, Stream, text}

class SQLIndex(config: Config) {

  def useDb[O](use: Connection => Stream[IO, O]): Stream[IO, O] = {
    Stream.bracket(IO {
      Class.forName("org.sqlite.JDBC")
      DriverManager.getConnection(s"jdbc:sqlite:${config.database}")
    })(use, conn => IO { conn.close() })
  }

  def executeSQL[I](query: I => String)(conn: Connection): Pipe[IO, I, Unit] = {
    in => in.map(i => {
      try {
        val stm = conn.createStatement()
        stm.executeUpdate(query(i))
      } catch {
        case _: Exception =>
      }
    })
  }

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
    executeSQL(_ => query)
  }

  def insertOffset(connection: Connection): Sink[IO, (TitleAndLength, Long)] = {
    val query: ((TitleAndLength, Long)) => String = { case (t, offset) =>
      s"INSERT INTO pages (title, offset) values (${'"' + t.title + '"'}, $offset)"
    }
    executeSQL(query)(connection)
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
      println(titleAndLength)
      offset + titleAndLength.length
    })((t,offset) => (t,offset))

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
  def main(args: Array[String]): Unit = {
    (for {
      config <- Config.config
      sqlInd <- IO { new SQLIndex(config) }
      _ <- sqlInd.run
    } yield ()).unsafeRunSync()
  }
}