val nameApp = "simple-reactivemongo"

lazy val simpleReactiveMongo = Project(nameApp, file("."))
  .enablePlugins(SbtAutoBuildPlugin, SbtGitVersioning)
  .settings(
    scalaVersion        := "2.11.12",
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    resolvers           += Resolver.typesafeRepo("releases"),
    crossScalaVersions  := Seq("2.11.12", "2.12.6")
  )
