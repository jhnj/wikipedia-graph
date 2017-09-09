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
          .map(getTask(_)(config))
          .sequence
      } yield tasks
  }

  def getTask(task: String): Config => IO[Unit] = task match {
    case "pipeline" =>
      config => for {
        _ <- Parser.parse(config)
        _ <- Redirects.run(config)
        _ <- PageUpdater.run(config)
        _ <- SQLIndex.run(config)
        _ <- GraphFile.run(config)
      } yield ()

    case "parse" =>
      _ => IO { println("dank") }

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
}