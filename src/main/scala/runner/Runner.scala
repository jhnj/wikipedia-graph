package runner

import parser._

object Pipeline {
  def main(args: Array[String]): Unit = {
    Parse.main(args)
    UpdatePages.main(args)
  }
}

object Parse {
  def main(args: Array[String]): Unit = {
    Parser.main.unsafeRunSync()
  }
}

object FilterRedirects {
  def main(args: Array[String]): Unit = {
    (for {
      config <- Config.config
      _ <- Redirects.filterRedirects(config)
    } yield ()).unsafeRunSync()
  }
}

object UpdatePages {
  def main(args: Array[String]): Unit = {
    (for {
      config <- Config.config
      _ <- PageUpdater.updatePages(config)
    } yield ()).unsafeRunSync()
  }
}
