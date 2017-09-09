package inspect

import org.scalatest.{FlatSpec, Matchers}
import fs2._


class InspectSpec extends FlatSpec with Matchers{
  "getInts" should "produce a stream of ints" in {
    Stream(0x01, 0x23, 0x45, 0x67, 0x89, 0xAB, 0xCD, 0xEF, 0x99)
      .map(_.toByte)
      .through(Inspect.getInts[Pure])
      .toVector should be (Vector(0x01234567, 0x89ABCDEF))
  }

  it should "handle chunked streams" in {
    (Stream.chunk(Chunk(0x01, 0x23, 0x45)) ++ Stream(0x67, 0x89, 0xAB, 0xCD, 0xEF, 0x99))
      .map(_.toByte)
      .through(Inspect.getInts[Pure])
      .toVector should be (Vector(0x01234567, 0x89ABCDEF))
  }
}
