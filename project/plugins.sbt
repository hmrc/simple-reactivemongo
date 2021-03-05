resolvers += Resolver.bintrayIvyRepo("hmrc", "sbt-plugin-releases")
resolvers += Resolver.bintrayRepo("hmrc", "releases")

addSbtPlugin("uk.gov.hmrc" % "sbt-auto-build"             % "2.13.0")
addSbtPlugin("uk.gov.hmrc" % "sbt-git-versioning"         % "2.2.0")
addSbtPlugin("uk.gov.hmrc" % "sbt-artifactory"            % "1.13.0")
addSbtPlugin("uk.gov.hmrc" % "sbt-play-cross-compilation" % "2.0.0")
