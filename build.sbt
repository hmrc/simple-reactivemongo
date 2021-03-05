import PlayCrossCompilation.playCrossCompilationSettings

lazy val simpleReactiveMongo = Project("simple-reactivemongo", file("."))
  .enablePlugins(SbtAutoBuildPlugin, SbtGitVersioning)
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(
    makePublicallyAvailableOnBintray := true,
    majorVersion                     := 7
  )
  .settings(
    scalaVersion        := "2.12.13",
    libraryDependencies ++= LibraryDependencies.compile ++ LibraryDependencies.test,
    resolvers           += Resolver.typesafeRepo("releases"),
    playCrossCompilationSettings
  )
