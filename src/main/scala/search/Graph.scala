package search

import cats.effect.IO

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
      println(node)
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

//object Graph {
//  def getTitle = IO { readLine("Enter start article") }
//
//
//  for {
//    start <- getTitle
//    stop <- getTitle
//  }
//
//}
