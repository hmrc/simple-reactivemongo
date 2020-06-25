import PlayCrossCompilation._

val libName = "simple-reactivemongo"

lazy val simpleReactiveMongo = Project(libName, file("."))
  .enablePlugins(SbtAutoBuildPlugin, SbtGitVersioning, SbtArtifactory)
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(
    makePublicallyAvailableOnBintray := true,
    majorVersion                     := 7
  )
  .settings(
    scalaVersion        := "2.11.12",
    libraryDependencies ++= LibraryDependencies.compile ++ LibraryDependencies.test,
    resolvers           += Resolver.typesafeRepo("releases"),
    crossScalaVersions  := Seq("2.11.12", "2.12.8"),
    playCrossCompilationSettings
  )
