package runner

import cats.data.{Kleisli, Reader, ReaderT}
import cats.effect.IO
import cats.implicits._
import parser._

object Runner {
  def main(args: Array[String]): Unit = {
    parseArgs(args).unsafeRunSync()
  }

  def parseArgs(args: Array[String]): IO[Unit] = {
    if (args.length <= 0)
      help
    else
      for {
        tasks <- args.toList
          .map(task => {
            logRun(task, getTask(task))
          })
          .sequence
      } yield tasks
  }

  def getTask(task: String): ReaderT[IO,Config,Unit] = task match {
    case "pipeline" =>
      for {
        _ <- Parser.run
        _ <- Redirects.run
        _ <- PageUpdater.run
        _ <- SQLIndex.run
        _ <- GraphFile.run
      } yield ()

    case "parse" =>
      Parser.run

    case "redirects" =>
      Redirects.run

    case "updatepages" =>
      PageUpdater.run

    case "sqlindex" =>
      SQLIndex.run

    case "graphfile" =>
      GraphFile.run

    case _ => Kleisli.pure(())
  }

  val help: IO[Unit] = IO {
    println("Usage 'sbt run [commands]' where '[commands] is any of the following separated by spaces:")
    println("pipeline, parse, redirects, updatepages, sqlindex, graphfile")
  }

  def logRun[A](taskName: String, task: ReaderT[IO,Config,A]): IO[A] = {
    for {
      startTime <- IO {
        println(s"starting task: $taskName"); System.currentTimeMillis()
      }
      config <- Config.config
      res <- task(config)
      _ <- IO {
        println(s"done task $taskName in ${(System.currentTimeMillis() - startTime) / 1000}s")
      }
    } yield res
  }
}