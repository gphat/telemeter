name := "telemeter"

version := "1.0-SNAPSHOT"

resolvers += "gphat" at "https://raw.github.com/gphat/mvn-repo/master/releases/"

libraryDependencies ++= Seq(
  jdbc,
  anorm,
  cache,
  "wabisabi" %% "wabisabi" % "2.0.8",
  "joda-time" % "joda-time" % "2.3"
)     

play.Project.playScalaSettings
