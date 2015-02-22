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

licenses := Seq("MIT" -> url("http://opensource.org/licenses/MIT"))

pomExtra := {
  <url>http://msilb.com</url>
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
