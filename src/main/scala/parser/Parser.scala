package parser

import java.nio.file.Paths

import io.Source
import cats._
import implicits._
import cats.effect._
import cats.effect.IO
import fs2._

import scala.xml.pull._

object Parser {
  def stax[F[_]](e: XMLEventReader)(implicit F: Sync[F]): Stream[F, XMLEvent] =
    Stream.unfoldEval(e) { e: XMLEventReader =>
      F.delay(e.hasNext)
        .ifM(ifTrue = F.delay(Option(e.next)),
          ifFalse = F.pure(Option.empty[XMLEvent]))
        .map(opt => opt.map((_, e)))
    }

  def staxFromFile(file: String): Stream[IO, XMLEvent] =
    Stream.bracket(IO {
      Source.fromFile(file)
    })(source => {
      val e = new XMLEventReader(source)
      stax(e)(Sync[IO])
    }, source => IO {
      source.close()
    })

  case class Page(title: String, links: Set[String] = Set[String]()) {
    override def toString: String = s"$title|${links.mkString("|")}"
  }
  case class Redirect(from: String, to: String) {
    override def toString: String = s"$from|$to"
  }

  type PageOrRedirect = Either[Page, Redirect]

  case class State(title: Option[String] = None,
                   redirect: Option[String] = None,
                   links: Set[String] = Set(),
                   isRedirect: Boolean = false,
                   inTitle: Boolean = false,
                   inText: Boolean = false)

  def xmlHandler[F[_]]: Pipe[F, XMLEvent, PageOrRedirect] = {
    def go(s: Stream[F, XMLEvent], state: State): Pull[F, PageOrRedirect, Unit] = {
      s.pull.uncons1.flatMap {
        case Some((head, tail)) =>
          head match {
            case EvElemStart(_, label, attrs, _) =>
              label match {
                case "page" =>
                  go(tail, state.copy(
                    title = None,
                    links = Set(),
                    isRedirect = false
                  ))

                case "redirect" =>
                  attrs.asAttrMap.get("title").map(redirect => {
                    go(tail, state.copy(isRedirect = true, redirect = Some(redirect)))
                  }).getOrElse(go(tail, state))

                case "title" =>
                  go(tail, state.copy(inTitle = true))

                case "text" =>
                  go(tail, state.copy(inText = true))

                case _ => go(tail, state)
              }

            case EvElemEnd(_, label) =>
              label match {
                case "text" =>
                  if (state.isRedirect)
                    Pull.output1(Right(Redirect(state.title.get, state.redirect.get))) >> go(tail, state.copy(inText = false))
                  else
                    Pull.output1(Left(Page(state.title.get, state.links))) >> go(tail, state)

                case "title" =>
                  go(tail, state.copy(inTitle = false))

                case _ => go(tail, state)
              }

            case EvText(value: String) =>
              if (state.inTitle) {
                go(tail, state.copy(title = Some(value)))
              } else if (state.inText) {
                go(tail, state.copy(links = LinkParser.getLinks(value)))
              } else {
                go(tail, state)
              }

            case _ => go(tail, state)
          }
      }
    }

    in => go(in, State()).stream
  }

  import scala.concurrent.ExecutionContext.Implicits.global

  def getLinks: (String) => Stream[IO, PageOrRedirect] =
    (path: String) =>
      staxFromFile(path)
        .through(xmlHandler)

  def writeToFile(path: String): Sink[IO, String] =
    in => in
      .intersperse("\n")
      .through(text.utf8Encode)
      .to(fs2.io.file.writeAll(Paths.get(path)))

  def writeLink(path: String): Sink[IO,PageOrRedirect] =
    in => in
      .filter(_.isLeft)
      .map { case Left(page) => page.toString }
      .to(writeToFile(path))

  def writeRedirect(path: String): Sink[IO,PageOrRedirect] =
    in => in
    .filter(_.isRight)
    .map { case Right(redirect) => redirect.toString }
    .to(writeToFile(path))

  val stream: IO[Unit] = for {
    config <- Config.config
    _ <- getLinks(config.wikipediaDump)
      .observe(writeLink(config.links))
      .observe(writeRedirect(config.redirects))
      .run
  } yield ()

  val main: IO[Unit] = for {
    startTime <- IO { println("starting"); System.currentTimeMillis() }
    _ <- stream
    _ <- IO {
      val duration = System.currentTimeMillis() - startTime
      println(s"done in ${duration / 1000}s")
    }
  } yield ()

  def main(args: Array[String]): Unit = {
    main.unsafeRunSync()
  }
}

