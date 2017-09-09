name := "wikipedia-graph"

version := "1.0"

scalaVersion := "2.12.3"

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats" % "0.9.0",
  "org.typelevel" %% "cats-effect" % "0.4",
  "org.scala-lang.modules" % "scala-xml_2.12" % "1.0.6",
  "co.fs2" % "fs2-core_2.12" % "0.10.0-M6",
  "co.fs2" % "fs2-io_2.12" % "0.10.0-M6",
  "com.github.pureconfig" %% "pureconfig" % "0.8.0",
  "org.xerial" % "sqlite-jdbc" % "3.20.0"
)