package runner

import cats.data.ReaderT
import cats.effect.IO
import cats.implicits._
import parser._
import inspect._
import search.SearchGraph

object Runner {
  def main(args: Array[String]): Unit = {
    parseArgs(args).unsafeRunSync()
  }

  def parseArgs(args: Array[String]): IO[Unit] = {
    if (args.length <= 0)
      help
    else
      for {
        _ <- args.toList
          .map(task => {
            runTask(task, getTask(task))
          })
          .sequence
      } yield ()
  }

  def getTask(task: String): ReaderT[IO,Config,Unit] = task match {
    case "pipeline" =>
      for {
        _ <- Parser.run(compressed = true)
        _ <- Redirects.run
        _ <- PageUpdater.run
        _ <- SQLIndex.run
        _ <- GraphFile.run
      } yield ()

    case "parse" =>
      Parser.run(compressed = false)

    case "parse-compressed" =>
      Parser.run(compressed = true)

    case "redirects" =>
      Redirects.run

    case "updatepages" =>
      PageUpdater.run

    case "sqlindex" =>
      SQLIndex.run

    case "graphfile" =>
      GraphFile.run

    case "inspect" =>
      Inspect.run

    case "search" =>
      SearchGraph.run

    case _ => ReaderT { _ => help}
  }

  val help: IO[Unit] = IO {
    println("Usage:  'sbt run [commands]' where '[commands] is any of the following separated by spaces:")
    println("pipeline, parse, redirects, updatepages, sqlindex, graphfile, inspect, search")
  }

  def runTask[A](taskName: String, task: ReaderT[IO,Config,A]): IO[A] = {
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