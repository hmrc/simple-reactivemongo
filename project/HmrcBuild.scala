import sbt._
import Keys._

object HmrcBuild extends Build {


  import uk.gov.hmrc.DefaultBuildSettings

  val nameApp = "simple-reactivemongo"
  val versionApp = "1.2.0"

  val appDependencies = {
    import Dependencies._

    Seq(
      Compile.reactiveMongo,
      Compile.playJson,
      Compile.nscalaTime,

      Test.junit,
      Test.scalaTest,
      Test.pegdown
    )
  }

  lazy val root = Project(nameApp, file("."), settings = DefaultBuildSettings(nameApp, versionApp, targetJvm = "jvm-1.7")() ++ Seq(
    libraryDependencies ++= appDependencies,
    publishArtifact in Test := true,
    resolvers := Seq(
      Opts.resolver.sonatypeReleases,
      Opts.resolver.sonatypeSnapshots,
      "typesafe-releases" at "http://repo.typesafe.com/typesafe/releases/",
      "typesafe-snapshots" at "http://repo.typesafe.com/typesafe/snapshots/"
    )
  ) ++ SonatypeBuild()
  )

}

object Dependencies {

  object Compile {
    val reactiveMongo = "org.reactivemongo" %% "reactivemongo" % "0.10.0"
    val playJson = "com.typesafe.play" %% "play-json" % "[2.1.0,2.2.3]" % "provided"
    val nscalaTime = "com.github.nscala-time" %% "nscala-time" % "1.0.0"
  }

  sealed abstract class Test(scope: String) {

    val scalaTest = "org.scalatest" %% "scalatest" % "2.1.5" % scope
    val junit = "junit" % "junit" % "4.11" % scope
    val pegdown = "org.pegdown" % "pegdown" % "1.4.2" % scope
  }

  object Test extends Test("test")

  object IntegrationTest extends Test("it")

}


object SonatypeBuild {

  import xerial.sbt.Sonatype._

  def apply() = {
    sonatypeSettings ++ Seq(
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
            <id>DougC</id>
            <name>Doug Clinton</name>
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
}

