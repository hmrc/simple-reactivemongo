import sbt._
import Keys._

object HmrcBuild extends Build {

  import de.heikoseeberger.sbtheader.AutomateHeaderPlugin
  import uk.gov.hmrc.DefaultBuildSettings
  import DefaultBuildSettings._
  import BuildDependencies._
  import uk.gov.hmrc.{SbtBuildInfo, ShellPrompt}

  val nameApp = "simple-reactivemongo"
  val versionApp = "2.6.1"

  val appDependencies = {
    import Dependencies._

    Seq(
      Compile.reactiveMongoJson,
      Compile.reactiveMongo,
      Compile.playJson,
      Compile.nscalaTime,
      Compile.logback,

      Test.scalaTest,
      Test.pegdown
    )
  }

  lazy val simpleReactiveMongo = Project(nameApp, file("."))
    .enablePlugins(AutomateHeaderPlugin)
    .settings(version := versionApp)
    .settings(scalaSettings: _*)
    .settings(defaultSettings(): _*)
    .settings(
      targetJvm := "jvm-1.7",
      scalaVersion := "2.11.6",
      shellPrompt := ShellPrompt(versionApp),
      libraryDependencies ++= appDependencies,
      resolvers := Seq(
        Resolver.bintrayRepo("hmrc", "releases"),
        Opts.resolver.sonatypeReleases,
        Opts.resolver.sonatypeSnapshots,
        "typesafe-releases" at "http://repo.typesafe.com/typesafe/releases/",
        "typesafe-snapshots" at "http://repo.typesafe.com/typesafe/snapshots/"
      ),
      crossScalaVersions := Seq("2.11.6"),
      publishArtifact := true,
      publishArtifact in Test := true
    )
    .settings(SbtBuildInfo(): _*)
    .settings(BuildDescriptionSettings(): _*)
    .settings(HeaderSettings())

}

object Dependencies {

  object Compile {
    val reactiveMongoJson = "uk.gov.hmrc" %% "reactivemongo-json" % "1.5.0"
    val reactiveMongo = "org.reactivemongo" %% "reactivemongo" % "0.10.5.0.akka23"
    val playJson = "com.typesafe.play" %% "play-json" % "2.3.8" % "provided"
    val nscalaTime = "com.github.nscala-time" %% "nscala-time" % "1.8.0"
    val logback = "ch.qos.logback" % "logback-classic" % "1.1.2"
  }

  sealed abstract class Test(scope: String) {

    val scalaTest = "org.scalatest" %% "scalatest" % "2.2.4" % scope
    val pegdown = "org.pegdown" % "pegdown" % "1.5.0" % scope
  }

  object Test extends Test("test")

  object IntegrationTest extends Test("it")

}


object BuildDescriptionSettings {

  def apply() = Seq(
      pomExtra := (<url>https://www.gov.uk/government/organisations/hm-revenue-customs</url>
        <licenses>
          <license>
            <name>Apache 2</name>
            <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
          </license>
        </licenses>
        <scm>
          <connection>scm:git@github.com:hmrc/simple-reactivemongo.git</connection>
          <developerConnection>scm:git@github.com:hmrc/simple-reactivemongo.git</developerConnection>
          <url>git@github.com:hmrc/simple-reactivemongo.git</url>
        </scm>
        <developers>
          <developer>
            <id>xnejp03</id>
            <name>Petr Nejedly</name>
            <url>http://www.equalexperts.com</url>
          </developer>
          <developer>
            <id>charleskubicek</id>
            <name>Charles Kubicek</name>
            <url>http://www.equalexperts.com</url>
          </developer>
          <developer>
            <id>duncancrawford</id>
            <name>Duncan Crawford</name>
            <url>http://www.equalexperts.com</url>
          </developer>
        </developers>)
    )
}


object HeaderSettings {

  import org.joda.time.DateTime
  import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport._
  import de.heikoseeberger.sbtheader.license.Apache2_0

  def apply() = headers := Map("scala" -> Apache2_0(DateTime.now().getYear.toString, "HM Revenue & Customs"))
}


