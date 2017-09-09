package db

import java.sql.{Connection, DriverManager, ResultSet}

import cats.effect.{IO, Sync}
import cats.implicits._
import fs2.{Pipe, Stream}
import parser.Config

object DB {
  def useDb[O](use: Connection => Stream[IO, O])(implicit config: Config): Stream[IO, O] = {
    Stream.bracket(IO {
      Class.forName("org.sqlite.JDBC")
      DriverManager.getConnection(s"jdbc:sqlite:${config.database}")
    })(use, conn => IO { conn.close() })
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

  def executeQuery[I,O,F[_]](query: I => String, getValues: ResultSet => O)(conn: Connection)(implicit F: Sync[F]): Pipe[F, I, O] = {
    (in: Stream[F,I]) => in.flatMap(i => {
      try {
        val stm = conn.createStatement()
        val rs = stm.executeQuery(query(i))
        Stream.unfoldEval(rs)(r => {
          F.delay(rs.next())
            .ifM(ifTrue = F.delay(Option(getValues(rs))),
              ifFalse = F.pure(Option.empty[O]))
            .map(opt => opt.map((_, rs)))
        })
      } catch {
        case _: Exception => Stream.empty.covaryOutput[O]
      }
    })
  }
}
