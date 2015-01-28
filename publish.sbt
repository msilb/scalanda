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
