organization := "com.github.bseibel"

name := "akka-persistence-bdb"

version := "1.0.1"

scalaVersion := "2.10.4"

crossScalaVersions := Seq("2.11.1")

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

publishMavenStyle := true

pomIncludeRepository := { _ => false }

publishTo <<= version {
  (v: String) =>
      Some("bintray" at "https://api.bintray.com/maven/bseibel/release/akka-persistence-bdb")
}

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials-bintree")

resolvers += "krasserm at bintray" at "http://dl.bintray.com/krasserm/maven"

libraryDependencies += "com.github.krasserm" %% "akka-persistence-testkit" % "0.3.3" % "test"

libraryDependencies += "com.sleepycat" % "je" % "S"

libraryDependencies += "com.typesafe.akka" %% "akka-persistence-experimental" % "2.3.4" % "compile"

libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % "2.3.4" % "test"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.1.4" % "test"

libraryDependencies += "commons-io" % "commons-io" % "2.4" % "test"

libraryDependencies += "commons-codec" % "commons-codec" % "1.9" % "compile"

