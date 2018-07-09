import sbt._

object AppDependencies {

  val compile = Seq(
    "org.reactivemongo"      %% "reactivemongo-play-json" % "0.12.6-play25",
    "uk.gov.hmrc"            %% "reactivemongo"           % "0.15.1",
    "com.typesafe.play"      %% "play-json"               % "2.5.12" % "provided",
    "com.github.nscala-time" %% "nscala-time"             % "2.2.0",
    "ch.qos.logback"         % "logback-classic"          % "1.1.2"
  )

  val test = Seq(
    "org.scalatest" %% "scalatest" % "2.2.4" % Test,
    "org.pegdown"   % "pegdown"    % "1.5.0" % Test
  )

}
