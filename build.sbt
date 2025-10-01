ThisBuild / scalaVersion := "2.13.12"

libraryDependencies ++= Seq(
  "com.softwaremill.sttp.client3" %% "core" % "3.9.0",
  "com.softwaremill.sttp.client3" %% "circe" % "3.9.0",
  "com.softwaremill.sttp.client3" %% "async-http-client-backend-cats" % "3.9.0",
  "com.typesafe.akka" %% "akka-actor-typed" % "2.8.7",
  "com.typesafe.akka" %% "akka-stream" % "2.8.7",
  "com.typesafe.akka" %% "akka-http" % "10.5.1",
  "de.heikoseeberger" %% "akka-http-circe" % "1.39.2",
  "io.circe" %% "circe-core" % "0.14.5",
  "io.circe" %% "circe-generic" % "0.14.5",
  "io.circe" %% "circe-parser" % "0.14.5",
  "org.typelevel" %% "cats-core" % "2.10.0",
  "org.typelevel" %% "cats-effect" % "3.5.2",
  "ch.qos.logback" % "logback-classic" % "1.5.19"
)