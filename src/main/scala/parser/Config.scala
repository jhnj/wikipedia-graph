package parser

import cats.effect.IO
import pureconfig._

case class Config(wikipediaDump: String,
                  pages: String,
                  redirects: String,
                  titles: String,
                  filteredRedirects: String)

object Config {
  def config: IO[Config] = IO { loadConfigOrThrow[Config] }
}
