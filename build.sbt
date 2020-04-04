import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}
import sbt.url

useGpg := false

name := "p2p"
organization := "io.parapet"
version := "1.0.0"
description := "A Framework for Distributed Computing"
scalaVersion := "2.12.8"
// Enables publishing to maven repo
publishMavenStyle := true
// Do not append Scala versions to the generated artifacts
crossPaths := false
// This forbids including Scala related libraries into the dependency
autoScalaLibrary := false


libraryDependencies ++= Seq(
  "org.zeromq" % "jeromq" % "0.5.1",
  "org.apache.commons" % "commons-lang3" % "3.9",
  "com.google.guava" % "guava" % "28.1-jre",
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "compile"
)

// Protobuf compiler settings

credentials += Credentials(Path.userHome / ".sbt" / "sonatype_credential")
PB.protoSources in Compile := Seq(file("src/main/protobuf"))
PB.targets in Compile := Seq(
  PB.gens.java -> (sourceManaged in Compile).value
)

// PUBLISH TO MAVEN

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/parapet-io/p2p"),
    "scm:git@github.com:parapet-io/p2p.git"
  )
)
ThisBuild / developers := List(
  Developer(
    id = "dmgcodevil",
    name = "Roman Pleshkov",
    email = "dmgcodevil@gmail.com",
    url = url("http://parapet.io/")
  )
)

ThisBuild / description := "A purely functional library to develop distributed and event driven systems."
ThisBuild / licenses := List("Apache 2" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt"))
ThisBuild / homepage := Some(url("http://parapet.io/"))

// Remove all additional repository other than Maven Central from POM
ThisBuild / pomIncludeRepository := { _ => false }
ThisBuild / publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}
ThisBuild / publishMavenStyle := true