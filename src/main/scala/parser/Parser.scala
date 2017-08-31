package parser

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


  type PageTitle = String

  case class Page(title: PageTitle, links: List[PageTitle] = List[PageTitle]())
  case class Redirect(from: PageTitle, to: PageTitle)

  type PageOrRedirect = Either[Page, Redirect]

  case class State(title: Option[PageTitle] = None,
                   redirect: Option[String] = None,
                   links: List[PageTitle] = List(),
                   isRedirect: Boolean = false,
                   inTitle: Boolean = false,
                   inText: Boolean = false
                  )

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
                    links = List(),
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
                    Pull.output1(Left(Page(state.title.get))) >> go(tail, state)

                case "title" =>
                  go(tail, state.copy(inTitle = false))

                case _ => go(tail, state)
              }

            case EvText(value: String) =>
              if (state.inTitle) {
                go(tail, state.copy(title = Some(value)))
              } else if (state.inText) {
                go(tail, state.copy(links = List("link")))
              } else {
                go(tail, state)
              }

            case _ => go(tail, state)
          }
      }
    }

    in => go(in, State()).stream
  }

  def main(args: Array[String]): Unit = {
    val stream: IO[Vector[PageOrRedirect]] = for {
      config <- Config.config
      s <- staxFromFile(config.wikipediaDump).through(xmlHandler).take(10).runLog
    } yield s

    (for {
      _ <- IO { println("starting") }
      s <- stream
      _ <- IO { println(s) }
      _ <- IO { println("done") }
    } yield s).unsafeRunSync()
  }
}

