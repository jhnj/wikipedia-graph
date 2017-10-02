package parser

import java.nio.file.Paths

import io.Source
import cats._
import cats.data.ReaderT
import implicits._
import cats.effect._
import cats.effect.IO
import fs2._
import runner.Config

import scala.xml.pull._

object Parser {
  case class Page(title: String, links: Set[String] = Set[String]()) {
    override def toString: String = s"$title|${links.mkString("|")}"
  }
  case class Redirect(from: String, to: String) {
    override def toString: String = s"$from|$to"
  }

  type PageOrRedirect = Either[Page, Redirect]

  case class State(title: String = "",
                   redirect: Option[String] = None,
                   text: String = "",
                   isRedirect: Boolean = false,
                   inTitle: Boolean = false,
                   inText: Boolean = false) {
    def addToTitle(toAdd: String): State =
      copy(title = title + toAdd.toLowerCase)

    def addToText(toAdd: String): State =
      copy(text = text + toAdd)
  }

  def xmlHandler[F[_]]: Pipe[F, XMLEvent, PageOrRedirect] = {
    def go(s: Stream[F, XMLEvent], state: State): Pull[F, PageOrRedirect, Unit] = {
      s.pull.uncons1.flatMap {
        case Some((head, tail)) =>
          head match {
            case EvElemStart(_, label, attrs, _) =>
              label match {
                case "page" =>
                  go(tail, state.copy(
                    title = "",
                    text = "",
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
                  if (state.isRedirect && state.title.length > 0 && state.redirect.isDefined)
                    outputRedirect(state.title, state.redirect.get) >>
                      go(tail, state.copy(inText = false))
                  else if (state.title.length > 0)
                    outputPage(state.title, state.text) >>
                      go(tail, state)
                  else
                    go(tail, state)

                case "title" =>
                  go(tail, state.copy(inTitle = false))

                case _ => go(tail, state)
              }

            case EvText(value: String) =>
              if (state.inTitle) {
                go(tail, state.addToTitle(value))
              } else if (state.inText) {
                go(tail, state.addToText(value))
              } else {
                go(tail, state)
              }

            case EvEntityRef("amp") =>
              if (state.inTitle) {
                go(tail, state.addToTitle("&"))
              } else if (state.inText) {
                go(tail, state.addToText("&"))
              } else {
                go(tail, state)
              }

            case _ => go(tail, state)
          }

        case _ => Pull.done
      }
    }

    in => go(in, State()).stream
  }

  def outputRedirect(from: String, to: String): Pull[Nothing, Either[Page, Redirect], Unit] =
    Pull.output1(
      Right(Redirect(from, to)))

  def outputPage(title: String, text: String): Pull[Nothing, Either[Page, Redirect], Unit] =
    Pull.output1(
      Left(Page(title, LinkParser.getLinks(text))))

  import scala.concurrent.ExecutionContext.Implicits.global

  def getLinks(path: String): Stream[IO, PageOrRedirect] =
      XmlStream.staxFromFile(path)
        .through(xmlHandler)

  def writeToFile(path: String): Sink[IO, String] =
    in => in
      .intersperse("\n")
      .through(text.utf8Encode)
      .to(fs2.io.file.writeAll(Paths.get(path)))

  def handlePage(pagePath: String, titlePath: String): Sink[IO, PageOrRedirect] =
    in => in
      .filter(_.isLeft)
      .collect { case Left(page) => page }
      .filter(page => wantedPage(page.title))
      .observe(writeTitle(titlePath))
      .map(_.toString)
      .to(writeToFile(pagePath))

  def wantedPage(title: String): Boolean =
    !(title.startsWith("File:") ||
      title.startsWith("Template:") ||
      title.startsWith("Help:") ||
      title.startsWith("Draft:"))

  def writeTitle(path: String): Sink[IO,Page] =
    in => in
      .map(_.title)
      .to(writeToFile(path))

  def writeRedirect(path: String): Sink[IO, PageOrRedirect] =
    in => in
      .filter(_.isRight)
      .collect { case Right(redirect) => redirect.toString }
      .to(writeToFile(path))

  def run(compressed: Boolean): ReaderT[IO, Config, Unit] = ReaderT { config =>
    val path =
      if(compressed) config.compressedWikipediaDump
      else config.wikipediaDump

    for {
      _ <- getLinks(path)
        .observe(handlePage(config.pages, config.titles))
        .observe(writeRedirect(config.redirects))
        .run
    } yield ()
  }
}

