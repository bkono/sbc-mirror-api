import spray.revolver.RevolverPlugin.Revolver

organization  := "com.solvedbycode"

version       := "0.1.0"

scalaVersion  := "2.10.3"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

resolvers ++= Seq(
  "spray repo" at "http://repo.spray.io/"
)

libraryDependencies ++= {
  val akkaV = "2.3-M2"
  val sprayV = "1.3-M2"
  Seq(
    "com.typesafe.akka"   %%  "akka-actor"                    % akkaV,
    "com.typesafe.akka"   %%  "akka-testkit"                  % akkaV,
    "com.typesafe.akka"   %%  "akka-persistence-experimental" % akkaV,
    "ch.qos.logback"      %   "logback-classic"               % "1.0.13",
    "org.specs2"          %%  "specs2"                        % "2.2.3" % "test"
  )
}

seq(Revolver.settings: _*)
