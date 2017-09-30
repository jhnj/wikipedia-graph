package db

import java.sql.{Connection, DriverManager, ResultSet}

import cats.Id
import cats.data.{Reader, ReaderT}
import cats.effect.{IO, Sync}
import cats.implicits._
import fs2.{Pipe, Sink, Stream}
import runner.Config

object DB {
  type Work[F[_],O] = Reader[Connection,Stream[F,O]]

  def useDb[O](work: Work[IO, O])(implicit config: Config): Stream[IO, O] = {
    Stream.bracket(IO {
      Class.forName("org.sqlite.JDBC")
      DriverManager.getConnection(s"jdbc:sqlite:${config.database}")
    })(work.apply, conn => IO { conn.close() })
  }

  def executeUpdate[I](query: I => String)(conn: Connection): Pipe[IO, I, Unit] = {
    in => in.map(i => {
      try {
        val stm = conn.createStatement()
        stm.executeUpdate(query(i))
      } catch {
        case _: Exception =>
      }
    })
  }

  def executeQuery[I,O](query: I => String, getValues: ResultSet => O)(conn: Connection)(implicit F: Sync[IO]): Pipe[IO, I, O] = {
    (in: Stream[IO,I]) => in.flatMap(i => {
      try {
        Stream.bracket(IO {
          val stm = conn.createStatement()
          val rs = stm.executeQuery(query(i))
          stm.close()
          rs
        })(rs => {
          Stream.unfoldEval(rs)(r => {
            F.delay(rs.next())
              .ifM(ifTrue = F.delay(Option(getValues(rs))),
                ifFalse = F.pure(Option.empty[O]))
              .map(opt => opt.map((_, rs)))
          })
        }, rs => IO {  rs.close() })
      } catch {
        case _: Exception =>
          Stream.empty.covaryOutput[O]
      }
    })
  }

  def escapeQuotes: String => String = _.replaceAll("'", "''")

  def getOffset(connection: Connection): Pipe[IO,String,Int] = {
    val query = (title: String) =>
      s"select (offset) from pages where title = '${escapeQuotes(title)}'"
    val getOffset = (rs: ResultSet) =>
      rs.getInt(1)

    executeQuery(query, getOffset)(connection)(Sync[IO])
  }

  def getTitle(connection: Connection): Pipe[IO,Int,String] = {
    val query = (offset: Int) =>
      s"select (title) from pages where offset = $offset"
    val getOffset = (rs: ResultSet) =>
      rs.getString(1)

    executeQuery(query, getOffset)(connection)(Sync[IO])
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
    executeUpdate(_ => query)
  }

  import parser.SQLIndex.TitleAndLength

  def insertOffset(connection: Connection): Sink[IO, (TitleAndLength, Long)] = {
    val query: ((TitleAndLength, Long)) => String = { case (t, offset) =>
      s"INSERT INTO pages (title, offset) values ('${escapeQuotes(t.title)}', $offset)"
    }
    executeUpdate(query)(connection)
  }
}
