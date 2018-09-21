import PlayCrossCompilation._
import sbt._
import uk.gov.hmrc.playcrosscompilation.PlayVersion._

object AppDependencies {

  val compile = DependenciesSeq(
    "com.github.nscala-time" %% "nscala-time"             % "2.2.0",
    "org.slf4j"              % "slf4j-api"                % "1.7.6" crossPlay Play25,
    "com.typesafe.play"      %% "play-json"               % "2.5.12" crossPlay Play25,
    "uk.gov.hmrc"            %% "reactivemongo"           % "0.15.1" crossPlay Play25,
    "org.reactivemongo"      %% "reactivemongo-play-json" % "0.12.6-play25" crossPlay Play25,
    "org.reactivemongo"      %% "reactivemongo-play-json" % "0.15.0-play26" crossPlay Play26,
    "org.reactivemongo"      %% "reactivemongo"           % "0.15.0" crossPlay Play26,
    "org.slf4j"              % "slf4j-api"                % "1.7.25" crossPlay Play26,
    "com.typesafe.play"      %% "play"                    % "2.5.12" crossPlay Play25,
    "com.typesafe.play"      %% "play"                    % "2.6.15" crossPlay Play26,
    "com.typesafe.play"      %% "play-guice"              % "2.6.15" crossPlay Play26
  )

  val test = DependenciesSeq(
    "org.pegdown"            % "pegdown"             % "1.6.0"  % Test,
    "org.scalacheck"         %% "scalacheck"         % "1.13.4" % Test,
    "org.scalamock"          %% "scalamock"          % "4.1.0"  % Test,
    "org.scalatest"          %% "scalatest"          % "3.0.5"  % Test,
    "ch.qos.logback"         % "logback-classic"     % "1.1.2"  % Test crossPlay Play25,
    "ch.qos.logback"         % "logback-classic"     % "1.2.3"  % Test crossPlay Play26,
    "com.typesafe.play"      %% "play-test"          % "2.6.15" % Test crossPlay Play26,
    "com.typesafe.play"      %% "play-test"          % "2.5.12" % Test crossPlay Play25,
    "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2"  % Test crossPlay Play26,
    "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.1"  % Test crossPlay Play25,
    "com.outworkers"         %% "util-samplers"      % "0.40.0" % Test crossPlay Play26
  )
}
