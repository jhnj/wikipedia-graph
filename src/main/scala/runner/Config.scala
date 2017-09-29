package runner

import cats.effect.IO
import pureconfig._

case class Config(wikipediaDump: String,
                  compressedWikipediaDump: String,
                  pages: String,
                  redirects: String,
                  titles: String,
                  filteredRedirects: String,
                  pagesWithRedirects: String,
                  database: String,
                  graph: String)

object Config {
  def config: IO[Config] = IO { loadConfigOrThrow[Config] }
}
