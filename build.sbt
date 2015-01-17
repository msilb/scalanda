organization := "com.msilb"

organizationHomepage := Some(new URL("http://msilb.com"))

name := "scalanda"

description := "Scala/Akka wrapper around Oanda REST and Stream API"

startYear := Some(2015)

version := "0.1-SNAPSHOT"

scalaVersion := "2.11.5"

scalacOptions := Seq("-unchecked", "-feature", "-deprecation", "-encoding", "utf8")

resolvers += "spray repo" at "http://repo.spray.io"

libraryDependencies ++= {
  val akkaV = "2.3.8"
  val sprayV = "1.3.2"
  val sprayJsonV = "1.3.1"
  val scalaTestV = "2.2.1"
  Seq(
    "io.spray" %% "spray-json" % sprayJsonV,
    "io.spray" %% "spray-can" % sprayV,
    "io.spray" %% "spray-client" % sprayV,
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "com.typesafe.akka" %% "akka-testkit" % akkaV % "test",
    "org.scalatest" %% "scalatest" % scalaTestV % "test"
  )
}
