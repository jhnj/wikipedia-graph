package inspect

import java.nio.ByteBuffer
import java.nio.file.Paths
import java.sql.Connection

import cats.data.{Reader, ReaderT}
import cats.effect.IO
import db.DB
import db.DB.Work
import runner.Config
import fs2._
import fs2.io.file.readAll

class Inspect(config: Config) {

  def inspect(title: String): Stream[IO, Int] = DB.useDb(findOffset(title))(config)

  def findOffset(title: String): Work[IO,Int] =
    Reader { connection: Connection =>
      Stream(title).covary[IO]
        .through(DB.getOffset(connection))
        .flatMap(offset => {
          Inspect.inspectLinks(offset, config)
        })
    }
}

object Inspect {
  val run: ReaderT[IO,Config,Unit] = ReaderT { config =>
    for {
      title <- IO { scala.io.StdIn.readLine("Enter title to inspect: ") }
      l <- {
        val a = new Inspect(config)
        a.inspect(title).runLog
      }
      _ <- IO { println(s"$title:\n$l\nlength: ${l.length}") }
    } yield ()
  }

  def inspectLinks(offset: Int, config: Config): Stream[IO, Int] = {
    graphStream(config)
      .drop(offset)
      .through(takeLinks)
  }

  def graphStream(config: Config): Stream[IO, Int] =
    readAll[IO](Paths.get(config.graph), 4096)
      .through(getInts)

  def takeLinks[F[_]]: Pipe[F, Int, Int] = in =>
    in.head.flatMap(numberOfLinks => in.take(numberOfLinks + 2))

  def getInts[F[_]]: Pipe[F,Byte,Int] = {
    def getInt(buffer: Vector[Byte], chunk: Chunk[Byte]): (Vector[Byte], Chunk[Int]) = {
      @annotation.tailrec
      def loop(buffer: Vector[Byte], output: Vector[Int]): (Vector[Byte], Chunk[Int]) = {
        if (buffer.length >= 4) {
          val (head, tail) = buffer.splitAt(4)
          val int = ByteBuffer.wrap(head.toArray).getInt
          loop(tail, output :+ int)
        } else {
          (buffer, Chunk.indexedSeq(output))
        }
      }

      loop(buffer ++ chunk.toVector, Vector.empty)
    }

    def go(bytes: Vector[Byte], s: Stream[F,Byte]): Pull[F, Int, None.type] = {
      s.pull.unconsChunk.flatMap {
        case Some((head, tail)) =>
          val (newBytes, ints) = getInt(bytes, head)
          Pull.output(ints) >> go(newBytes, tail)
        case None => Pull.pure(None)
      }
    }

    in => go(Vector.empty, in).stream
  }
}
