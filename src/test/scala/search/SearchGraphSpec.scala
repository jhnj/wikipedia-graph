package search

import org.scalatest.{FlatSpec, Matchers}

class SearchGraphSpec extends FlatSpec with Matchers {
  "Graph" should "find the correct path" in {
    val nodeA = Vector(2,0,4,8)     // offset 0
    val nodeB = Vector(2,1,0,8)     // offset 4
    val nodeC = Vector(2,2,0,12)    // offset 8
    val nodeD = Vector(1,3,8)       // offset 12

    val nodes = nodeA ++ nodeB ++ nodeC ++ nodeD
    val graph = new SearchGraph(nodes, 10)
    graph.bfs(0,12) should be (Vector(0,8,12))
  }
}
