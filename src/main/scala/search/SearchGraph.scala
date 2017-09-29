package search

import java.sql.Connection

import cats.data.{Reader, ReaderT}
import cats.effect.IO
import db.DB
import runner.Config
import fs2.Stream
import fs2.io._
import inspect.Inspect
import cats.implicits._

import scala.collection.mutable
import scala.io.StdIn.readLine

class SearchGraph(graph: Vector[Int], size: Int) {
  val prev: Array[Int] = Array.fill(size)(-1)

  def links(offset: Int): Vector[Int] = {
    val numLinks = graph(offset)
    graph.slice(offset + 2, offset + numLinks + 2)
  }

  def bfs(start: Int, stop: Int): List[Int] = {
    val q = new mutable.Queue[Int]

    q.enqueue(start)

    var found = false
    while (q.nonEmpty && !found) {
      val node = q.dequeue
      if (node == stop)
        found = true
      else {
        links(node).foreach(n => {
          if (getPrev(n) < 0) {
            setPrev(n,node)
            q.enqueue(n)
          }
        })
      }
    }

    @annotation.tailrec
    def getPath(node: Int, list: List[Int] = List()): List[Int] = {
      if (node == start)
        node +: list
      else
        getPath(getPrev(node), node +: list)
    }

    getPath(stop)
  }

  def getPrev(i: Int): Int = {
    prev(graph(i + 1))
  }

  def setPrev(i: Int, value: Int): Unit = {
    prev(graph(i + 1)) = value
  }
}

object SearchGraph {
  def getTitleOffset(question: String): ReaderT[IO, Config, Int] = ReaderT { config =>
    for {
      title <- IO {
        readLine(question)
      }
      offset <- getOffset(title)(config)
    } yield offset.head
  }

  def getOffset(title: String)(config: Config): IO[Vector[Int]] = {
    DB.useDb(Reader[Connection, Stream[IO, Int]] { conn: Connection =>
      Stream(title).covary[IO].through(DB.getOffset(conn))
    })(config).runLog
  }

  def getTitle(offset: Int)(config: Config): IO[Option[String]] = {
    DB.useDb(Reader[Connection, Stream[IO, String]] { conn: Connection =>
      Stream(offset).covary[IO].through(DB.getTitle(conn))
    })(config).runLog.map(_.headOption)
  }

  def printResult(res: Option[List[String]]): IO[Unit] = IO {
    val toPrint = res
      .map(path => s"Shortest path: ${path.mkString(" -> ")}")
      .getOrElse("Path not found")
    println(toPrint)
  }

  val run: ReaderT[IO,Config,Unit] = ReaderT { config =>
    for {
      start <- getTitleOffset("Enter start title: ").run(config)
      stop <- getTitleOffset("Enter end title: ").run(config)
      graphVec <- Inspect.graphStream(config).runLog
      path <- IO {
        val graph = new SearchGraph(graphVec, graphVec.size)
        graph.bfs(start, stop)
      }
      seq <- path.map(getTitle(_)(config)).sequence
      _ <- printResult(seq.sequence)
    } yield ()
  }
}
