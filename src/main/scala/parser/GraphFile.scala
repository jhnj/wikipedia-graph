package parser
import java.nio.ByteBuffer
import java.nio.file.Paths
import java.sql.{Connection, ResultSet}

import cats.data.{Reader, ReaderT}
import cats.effect.{IO, Sync}
import db.DB._
import fs2.{Chunk, Pipe, Sink, Stream, text}
import fs2.io.file.readAll
import runner.Config

class GraphFile(config: Config) {
  case class TitleAndLinks(title: String, links: List[String])

  val parsePage: Pipe[IO,String,TitleAndLinks] = {
    in => in.collect {
      case line: String if line.length > 0 => line.split('|')
    } .map(arr => TitleAndLinks(arr(0), arr.tail.toList))
  }

  def createGraph: Work[IO, Unit] = Reader { connection =>
    readAll[IO](Paths.get(config.pagesWithRedirects), 4096)
      .through(text.utf8Decode)
      .through(text.lines)
      .through(parsePage)
      .zipWithIndex
      .through(toBin(connection))
      .flatMap { s =>
        val buffer = ByteBuffer.allocate(4)
        buffer.putInt(s)
        Stream.chunk(Chunk.bytes(buffer.array))
      }
      .to(fs2.io.file.writeAll(Paths.get(config.graph)))
      .through(log)
  }

  def toBin(connection: Connection): Pipe[IO, (TitleAndLinks, Long), Int] =
    in => in.flatMap { case (tl, index) =>
      Stream(tl.links.length, index.toInt).covary[IO] ++
        Stream
          .emits(tl.links)
          .covary[IO]
          .through(getOffset(connection))
    }

  def log[A,B]: Pipe[IO, A, A] = {
    in => in.zipWithIndex.map(tuple => {
      if (tuple._2 % 1000 == 0)
        print(".")
      tuple._1
    })
  }

  val run: IO[Unit] = useDb(createGraph)(config).run
}

object GraphFile {
  val run: ReaderT[IO,Config,Unit] = ReaderT { config =>
      val graphFile = new GraphFile(config)
      graphFile.run
  }
}
