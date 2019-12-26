import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}

name := "p2p"
organization := "io.parapet"
version := "1.0-SNAPSHOT"
description := "A Framework for Distributed Computing"

// Enables publishing to maven repo
publishMavenStyle := true
// Do not append Scala versions to the generated artifacts
crossPaths := false
// This forbids including Scala related libraries into the dependency
autoScalaLibrary := false

mainClass in Compile := Some("io.parapet.p2p.MainApp")

enablePlugins(JavaAppPackaging)
enablePlugins(DockerPlugin)

libraryDependencies ++= Seq(
  "org.zeromq" % "jeromq" % "0.5.1"
)

daemonUserUid in Docker := None
daemonUser in Docker    := "root"
dockerCommands ++= Seq(
  ExecCmd("RUN",
    "apt-get", "update"
  ),
  ExecCmd("RUN",
    "apt-get", "install", "nano"
  ),
)