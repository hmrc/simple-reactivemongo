import sbt.Keys._
import sbt._
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.versioning.SbtGitVersioning

object HmrcBuild extends Build {

  import uk.gov.hmrc.DefaultBuildSettings
  import DefaultBuildSettings._
  import SbtAutoBuildPlugin._

  val nameApp = "simple-reactivemongo"

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
    .enablePlugins(SbtAutoBuildPlugin, SbtGitVersioning)
    .settings(
      autoSourceHeader := false,
      scalaVersion := "2.11.7",
      libraryDependencies ++= appDependencies,
      resolvers += Resolver.typesafeRepo("releases"),
      crossScalaVersions := Seq("2.11.7")
    )
}

object Dependencies {

  object Compile {
    val reactiveMongoJson = "uk.gov.hmrc" %% "reactivemongo-json" % "2.2.1"
    //NOTE: 0.11.6 Netty 3.10.4.Final clashes with Play (2.3.10) version of Netty 3.9.8
    val reactiveMongo = "org.reactivemongo" %% "reactivemongo" % "0.11.5"
    val playJson = "com.typesafe.play" %% "play-json" % "2.3.10" % "provided"
    val nscalaTime = "com.github.nscala-time" %% "nscala-time" % "2.2.0"
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

