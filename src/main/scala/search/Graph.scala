package search

import java.sql.Connection

import cats.data.{Reader, ReaderT}
import cats.effect.IO
import db.DB
import runner.Config
import fs2.Stream
import fs2.io._
import inspect.Inspect

import scala.collection.mutable
import scala.io.StdIn.readLine

class Graph(graph: Vector[Int], size: Int) {
  def links(offset: Int): Vector[Int] = {
    val numLinks = graph(offset)
    graph.slice(offset + 1, offset + numLinks + 1)
  }

  def bfs(start: Int, stop: Int): List[Int] = {
    val q = new mutable.Queue[Int]
    val prev = Array.fill(size)(-1)

    q.enqueue(start)

    var found = false
    while (q.nonEmpty && !found) {
      val node = q.dequeue
      if (node == stop)
        found = true
      else {
        links(node).foreach(n => {
          if (prev(n) < 0) {
            prev(n) = node
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
        getPath(prev(node), node +: list)
    }

    getPath(stop)
  }
}

object Graph {
  def getTitleOffset: ReaderT[IO, Config, Int] = ReaderT { config =>
    for {
      title <- IO {
        readLine("Enter article: ")
      }
      offset <- getOffset(title)(config)
    } yield offset.head
  }

  def getOffset(title: String)(config: Config): IO[Vector[Int]] = {
    DB.useDb(Reader[Connection, Stream[IO, Int]] { conn: Connection =>
      Stream(title).covary[IO].through(DB.getOffset(conn))
    })(config).runLog
  }

  def main(args: Array[String]): Unit = {
    (for {
      config <- Config.config
      start <- getTitleOffset.run(config)
      stop <- getTitleOffset.run(config)
      graphVec <- Inspect.graphStream(config).runLog
      _ <- IO {
        val graph = new Graph(graphVec, graphVec.size)
        println(graph.bfs(start, stop))
      }
    } yield ()).unsafeRunSync()
  }
}
