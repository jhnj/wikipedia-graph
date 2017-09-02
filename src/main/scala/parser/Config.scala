package parser

import cats.effect.IO
import pureconfig._

case class Config(wikipediaDump: String,
                  links: String,
                  redirects: String)

object Config {
  def config: IO[Config] = IO { loadConfigOrThrow[Config] }
}
