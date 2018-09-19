import PlayCrossCompilation._

val libName = "simple-reactivemongo"

lazy val simpleReactiveMongo = Project(libName, file("."))
  .enablePlugins(SbtAutoBuildPlugin, SbtGitVersioning)
  .settings(
    makePublicallyAvailableOnBintray := true,
    majorVersion                     := 6
  )
  .settings(
    scalaVersion        := "2.11.12",
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    resolvers           += Resolver.typesafeRepo("releases"),
    crossScalaVersions  := Seq("2.11.12", "2.12.6"),
    playCrossCompilationSettings
  )
