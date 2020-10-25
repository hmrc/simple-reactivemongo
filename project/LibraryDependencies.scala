import sbt._

object LibraryDependencies {

  private val play25Version = "2.5.19"
  private val play26Version = "2.6.23" // this version is pinned, since later versions are incompatible with the version of akka that reactivemongo was build with on scala 2.11.
  private val play27Version = "2.7.4"

  val compile: Seq[ModuleID] = PlayCrossCompilation.dependencies(
    shared = Seq(
      "com.github.nscala-time" %% "nscala-time"      % "2.22.0",
      "org.reactivemongo"      %% "reactivemongo"    % "0.20.12-fix1",
      "org.slf4j"              %  "slf4j-api"        % "1.7.26",
      "org.slf4j"              %  "log4j-over-slf4j" % "1.7.26",
      // force commons-codec to avoid 1.13 and 1.14 until 1.15 is available
      // see JIRA ticket BDOG-612
      "commons-codec"          %  "commons-codec" % "1.12" force()
    ),
    play25 = Seq(
      "com.typesafe.play" %% "play"                    % play25Version,
      "org.reactivemongo" %% "reactivemongo-play-json" % "0.20.12-play25-fix1",
      "org.reactivemongo"      %% "reactivemongo-play-json-compat"    % "0.20.12-play25-fix1",
      // force dependencies due to security flaws found in jackson-databind < 2.9.x using XRay
      "com.fasterxml.jackson.core"     % "jackson-core"            % "2.9.8",
      "com.fasterxml.jackson.core"     % "jackson-databind"        % "2.9.8",
      "com.fasterxml.jackson.core"     % "jackson-annotations"     % "2.9.8",
      "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8"   % "2.9.8",
      "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % "2.9.8",
      // force dependencies due to security flaws found in xercesImpl 2.11.0
      "xerces" % "xercesImpl" % "2.12.0"
    ),
    play26 = Seq(
      "com.typesafe.play" %% "play"                    % play26Version,
      "com.typesafe.play" %% "play-guice"              % play26Version,
      "org.reactivemongo" %% "reactivemongo-play-json" % "0.20.12-play26-fix1",
      "org.reactivemongo"      %% "reactivemongo-play-json-compat"    % "0.20.12-play26-fix1"
    ),
    play27 = Seq(
      "com.typesafe.play" %% "play"                    % play27Version,
      "com.typesafe.play" %% "play-guice"              % play27Version,
      "org.reactivemongo" %% "reactivemongo-play-json" % "0.20.12-play27-fix1",
      "org.reactivemongo"      %% "reactivemongo-play-json-compat"    % "0.20.12-play27-fix1"
    )
  )

  val test: Seq[ModuleID] = PlayCrossCompilation.dependencies(
    shared = Seq(
      "com.outworkers" %% "util-samplers"  % "0.40.0" % Test,
      "org.pegdown"    % "pegdown"         % "1.6.0"  % Test,
      "org.scalacheck" %% "scalacheck"     % "1.13.5" % Test,
      "org.scalamock"  %% "scalamock"      % "4.1.0"  % Test,
      "org.scalatest"  %% "scalatest"      % "3.0.6"  % Test,
      "ch.qos.logback" % "logback-classic" % "1.2.3"  % Test
    ),
    play25 = Seq(
      "com.typesafe.play"      %% "play-test"          % play25Version % Test,
      "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.1"       % Test
    ),
    play26 = Seq(
      "com.typesafe.play"      %% "play-test"          % play26Version % Test,
      "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2"       % Test
    ),
    play27 = Seq(
      "com.typesafe.play"      %% "play-test"          % play27Version % Test,
      "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2"       % Test
    )
  )
}
