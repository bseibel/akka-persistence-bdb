organization := "com.github.bseibel"

name := "akka-persistence-bdb"

version := "1.0-RELEASE"

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

resolvers += "krasserm at bintray" at "http://dl.bintray.com/krasserm/maven"

libraryDependencies += "com.github.krasserm" %% "akka-persistence-testkit" % "0.3" % "test"

libraryDependencies += "com.sleepycat" % "je" % "5.0.103"

libraryDependencies += "com.typesafe.akka" %% "akka-persistence-experimental" % "2.3.1" % "compile"

libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % "2.3.1" % "test"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.0" % "test"

libraryDependencies += "commons-io" % "commons-io" % "2.4" % "test"

libraryDependencies += "commons-codec" % "commons-codec" % "1.9" % "compile"

