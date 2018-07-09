import sbt._

object AppDependencies {

  val compile = Seq(
    "org.reactivemongo"      %% "reactivemongo-play-json" % "0.14.0-play26",
    "org.reactivemongo"      %% "reactivemongo"           % "0.14.0",
    "com.typesafe.play"      %% "play-json"               % "2.6.9",
    "com.github.nscala-time" %% "nscala-time"             % "2.2.0",
    "ch.qos.logback"         % "logback-classic"          % "1.1.2"
  )

  val test = Seq(
    "org.scalatest" %% "scalatest" % "3.0.5" % Test,
    "org.pegdown"   % "pegdown"    % "1.5.0" % Test
  )

}
