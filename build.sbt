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

mainClass in Compile := Some("io.parapet.p2p.App")

enablePlugins(JavaAppPackaging)
enablePlugins(DockerPlugin)

libraryDependencies ++= Seq(
  "org.zeromq" % "jeromq" % "0.5.1",
  "org.apache.commons" % "commons-lang3" % "3.9",
  "com.google.guava" % "guava" % "28.1-jre",
  //"com.thesamet.scalapb" %% "compilerplugin" % "0.9.0",
  "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "compile"
)

// Protobuf compiler settings

PB.protoSources in Compile := Seq(file("src/main/protobuf"))
PB.targets in Compile := Seq(
  PB.gens.java -> (sourceManaged in Compile).value
)

//enablePlugins(ProtobufPlugin)

//sourceDirectories in ProtobufConfig += (protobufExternalIncludePath in ProtobufConfig).value

daemonUserUid in Docker := None
daemonUser in Docker    := "root"
dockerCommands ++= Seq(
  ExecCmd("RUN",
    "apt-get", "update"
  ),
  ExecCmd("RUN",
    "apt-get", "install", "nano"
  )
)