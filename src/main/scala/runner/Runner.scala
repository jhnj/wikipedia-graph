package runner

import parser._

object Runner {
  def main(args: Array[String]): Unit = {
    (for {
      config <- Config.config
      _ <- Parser.parse(config)
      _ <- Redirects.filterRedirects(config)
      _ <- PageUpdater.updatePages(config)
      _ <- SQLIndex.run(config)
      _ <- GraphFile.run(config)
    } yield ()).unsafeRunSync()
  }
}