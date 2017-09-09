package search

import org.scalatest.{FlatSpec, Matchers}

class GraphSpec extends FlatSpec with Matchers {
  "Graph" should "find the correct path" in {
    val nodeA = Vector(2,3,6) // offset 0
    val nodeB = Vector(2,0,6) // offset 3
    val nodeC = Vector(2,0,9) // offset 6
    val nodeD = Vector(1, 6)  // offset 9

    val nodes = nodeA ++ nodeB ++ nodeC ++ nodeD
    val graph = new Graph(nodes, 11)
    graph.bfs(0,9) should be (Vector(0,6,9))
  }
}
