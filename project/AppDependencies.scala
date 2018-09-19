import PlayCrossCompilation._
import sbt._
import uk.gov.hmrc.playcrosscompilation.PlayVersion._

object AppDependencies {

  val compile = DependenciesSeq(
    "com.github.nscala-time" %% "nscala-time"             % "2.2.0",
    "org.slf4j"              % "slf4j-api"                % "1.7.6" crossPlay Play25,
    "com.typesafe.play"      %% "play-json"               % "2.5.12" crossPlay Play25,
    "org.reactivemongo"      %% "reactivemongo-play-json" % "0.12.6-play25" crossPlay Play25,
    "uk.gov.hmrc"            %% "reactivemongo"           % "0.15.1" crossPlay Play25
  )

  val test = DependenciesSeq(
    "org.pegdown"    % "pegdown"         % "1.6.0" % Test,
    "org.scalatest"  %% "scalatest"      % "3.0.5" % Test,
    "ch.qos.logback" % "logback-classic" % "1.1.2" % Test crossPlay Play25
  )
}
