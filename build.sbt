organization := "com.msilb"

organizationHomepage := Some(new URL("http://msilb.com"))

name := "scalanda"

description := "Scala/Akka wrapper for Oanda REST and Stream APIs"

startYear := Some(2015)

scalaVersion := "2.11.7"

scalacOptions := Seq("-unchecked", "-feature", "-deprecation", "-encoding", "utf8")

resolvers += "spray repo" at "http://repo.spray.io"

libraryDependencies ++= {
  val akkaV = "2.4.0"
  val sprayV = "1.3.3"
  val sprayJsonV = "1.3.2"
  val scalaTestV = "2.2.5"
  Seq(
    "io.spray" %% "spray-json" % sprayJsonV,
    "io.spray" %% "spray-can" % sprayV,
    "io.spray" %% "spray-client" % sprayV,
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "com.typesafe.akka" %% "akka-testkit" % akkaV % "test",
    "org.scalatest" %% "scalatest" % scalaTestV % "test"
  )
}
