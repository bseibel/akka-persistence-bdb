organization := "com.github.bseibel"

name := "akka-persistence-bdb"

version := "0.1-SNAPSHOT"

scalaVersion := "2.10.3"

parallelExecution in Test := false


fork := true

resolvers += "Oracle" at "http://download.oracle.com/maven"

scalacOptions ++= Seq(
  "-encoding",
  "UTF-8",
  "-deprecation",
  "-unchecked",
  "-feature",
  "-optimise",
  "-language:implicitConversions",
  "-language:reflectiveCalls",
  "-language:postfixOps",
  "-Yinline-warnings"
)


libraryDependencies += "com.sleepycat" % "je" % "5.0.103"

libraryDependencies += "com.typesafe.akka" %% "akka-persistence-experimental" % "2.3.0-RC2" % "compile"

libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % "2.3.0-RC2" % "test"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.0" % "test"

libraryDependencies += "commons-io" % "commons-io" % "2.4" % "test"

libraryDependencies += "commons-codec" % "commons-codec" % "1.9" % "compile"

