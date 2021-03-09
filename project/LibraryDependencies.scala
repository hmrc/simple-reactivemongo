import sbt._

object LibraryDependencies {

  private val play26Version = "2.6.25"
  private val play27Version = "2.7.9"
  private val play28Version = "2.8.7"

  val compile: Seq[ModuleID] = PlayCrossCompilation.dependencies(
    shared = Seq(
      "com.github.nscala-time" %% "nscala-time"      % "2.22.0",
      "org.reactivemongo"      %% "reactivemongo"    % "0.18.8",
      "org.slf4j"              %  "slf4j-api"        % "1.7.26",
      "org.slf4j"              %  "log4j-over-slf4j" % "1.7.26",
      "commons-codec"          %  "commons-codec"    % "1.15"
    ),
    play26 = Seq(
      "com.typesafe.play" %% "play"                    % play26Version,
      "com.typesafe.play" %% "play-guice"              % play26Version,
      "org.reactivemongo" %% "reactivemongo-play-json" % "0.18.8-play26"
    ),
    play27 = Seq(
      "com.typesafe.play" %% "play"                    % play27Version,
      "com.typesafe.play" %% "play-guice"              % play27Version,
      "org.reactivemongo" %% "reactivemongo-play-json" % "0.18.8-play27"
    ),
    play28 = Seq(
      "com.typesafe.play" %% "play"                    % play28Version,
      "com.typesafe.play" %% "play-guice"              % play28Version,
      "org.reactivemongo" %% "reactivemongo-play-json" % "0.18.8-play27" // "0.18.8" does not offer play28. It's only play dependency is play-json, which will be safely evicted.
    )
  )

  val test: Seq[ModuleID] = PlayCrossCompilation.dependencies(
    shared = Seq(
      "com.outworkers"       %% "util-samplers"            % "0.40.0"      % Test,
      "org.scalatestplus"    %% "scalatestplus-scalacheck" % "3.1.0.0-RC2" % Test,
      "org.scalamock"        %% "scalamock"                % "4.1.0"       % Test,
      "com.vladsch.flexmark" %  "flexmark-all"             % "0.35.10"     % Test,
      "ch.qos.logback"       %  "logback-classic"          % "1.2.3"       % Test
    ),
    play26 = Seq(
      "com.typesafe.play"      %% "play-test"          % play26Version % Test,
      "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2"       % Test
    ),
    play27 = Seq(
      "com.typesafe.play"      %% "play-test"          % play27Version % Test,
      "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.3"       % Test
    ),
    play28 = Seq(
      "com.typesafe.play"      %% "play-test"          % play28Version % Test,
      "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0"       % Test
    )
  )
}
