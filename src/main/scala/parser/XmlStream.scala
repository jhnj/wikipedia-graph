package parser

import org.apache.commons.compress.compressors.CompressorStreamFactory
import java.io.BufferedInputStream
import java.io.FileInputStream

import cats.effect.Sync
import cats.implicits
import fs2.Stream

import io.Source
import cats._
import implicits._
import cats.effect.IO

import scala.xml.pull.{XMLEvent, XMLEventReader}

object XmlStream {
  def getSourceForCompressed(fileIn: String): Source = {
    val fin = new FileInputStream(fileIn)
    val bis = new BufferedInputStream(fin)
    val cis = new CompressorStreamFactory().createCompressorInputStream(bis)
    Source.fromInputStream(cis)
  }

  def stax[F[_]](e: XMLEventReader)(implicit F: Sync[F]): Stream[F, XMLEvent] =
    Stream.unfoldEval(e) { e: XMLEventReader =>
      F.delay(e.hasNext)
        .ifM(ifTrue = F.delay(Option(e.next)),
          ifFalse = F.pure(Option.empty[XMLEvent]))
        .map(opt => opt.map((_, e)))
    }

  def staxFromFile(file: String): Stream[IO, XMLEvent] =
    Stream.bracket(IO {
      if (isCompressed(file))
        getSourceForCompressed(file)
      else
        Source.fromFile(file)
    })(source => {
      val e = new XMLEventReader(source)
      stax(e)(Sync[IO])
    }, source => IO {
      source.close()
    })

  def isCompressed(file: String): Boolean =
    file.takeRight(3) == "bz2"
}
