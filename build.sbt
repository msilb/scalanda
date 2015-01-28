import SonatypeKeys._

sonatypeSettings

organization := "com.msilb"

profileName := "com.msilb"

organizationHomepage := Some(new URL("http://msilb.com"))

name := "scalanda"

description := "Scala/Akka wrapper around Oanda REST and Stream API"

startYear := Some(2015)

version := "0.3.0-SNAPSHOT"

scalaVersion := "2.11.5"

scalacOptions := Seq("-unchecked", "-feature", "-deprecation", "-encoding", "utf8")

resolvers += "spray repo" at "http://repo.spray.io"

libraryDependencies ++= {
  val akkaV = "2.3.9"
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

publishMavenStyle := true

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

publishArtifact in Test := false

pomIncludeRepository := { _ => false}

pomExtra := {
  <url>http://msilb.com</url>
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
      </license>
    </licenses>
    <scm>
      <connection>scm:git:https://github.com/msilb/scalanda.git</connection>
      <url>https://github.com/msilb/scalanda</url>
    </scm>
    <developers>
      <developer>
        <id>msilb</id>
        <name>Michael Silbermann</name>
        <url>http://msilb.com</url>
      </developer>
    </developers>
}
