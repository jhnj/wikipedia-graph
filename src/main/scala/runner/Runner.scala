package runner

import cats.implicits._
import cats.effect.IO
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
        config <- Config.config
        tasks <- args.toList
          .map(task => {
            logRun(task, getTask(task)(config))
          })
          .sequence
      } yield tasks
  }

  def getTask(task: String): Config => IO[Unit] = task match {
    case "pipeline" =>
      config => for {
        _ <- Parser.run(config)
        _ <- Redirects.run(config)
        _ <- PageUpdater.run(config)
        _ <- SQLIndex.run(config)
        _ <- GraphFile.run(config)
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

    case _ =>
      _ => IO.unit

  }

  val help: IO[Unit] = IO {
    println("Usage 'sbt run [commands]' where '[commands] is any of the following separated by spaces:")
    println("pipeline, parse, redirects, updatepages, sqlindex, graphfile")
  }

  def logRun[A](taskName: String, task: IO[A]): IO[A] = {
    for {
      startTime <- IO {
        println(s"starting task: $taskName"); System.currentTimeMillis()
      }
      res <- task
      _ <- IO {
        println(s"done task $taskName in ${(System.currentTimeMillis() - startTime) / 1000}s")
      }
    } yield res
  }
}