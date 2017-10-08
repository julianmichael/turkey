val monocleVersion = "1.4.0-M2"
val scalaJSReactVersion = "0.11.1"

lazy val root = project.in(file("."))
  .aggregate(turkeyJVM, turkeyJS, turkeySampleJVM, turkeySampleJS)
  .settings(
  publish := {},
  publishLocal := {})

lazy val turkey = crossProject.settings(
  name := "turkey",
  organization := "com.github.julianmichael",
  version := "0.1-SNAPSHOT",
  scalaOrganization in ThisBuild := "org.typelevel", // for fixing stupid serialization woes
  scalaVersion in ThisBuild := "2.11.8",
  scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked"),
  resolvers += Resolver.sonatypeRepo("snapshots"),
  libraryDependencies ++= Seq(
    "com.lihaoyi" %%% "upickle" % "0.4.1",
    "com.lihaoyi" %%% "scalatags" % "0.6.5",
    "com.softwaremill.macmemo" %% "macros" % "0.4-SNAPSHOT"
  ),
  addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
).jvmSettings(
  libraryDependencies ++= Seq(
    "org.scala-lang.modules" %% "scala-xml" % "1.0.2", // for one line of code parsing answer XML
    "com.typesafe.akka" %% "akka-actor" % "2.4.8",
    "com.typesafe.akka" %% "akka-http" % "10.0.10",
    "ch.megard" %% "akka-http-cors" % "0.2.2",
    "com.jsuereth" % "scala-arm_2.11" % "2.0-RC1",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
    // java deps:
    "com.amazonaws" % "aws-java-sdk" % "1.11.198")
).jsSettings(
  libraryDependencies ++= Seq(
    "org.scala-js" %%% "scalajs-dom" % "0.9.0",
    "be.doeraene" %%% "scalajs-jquery" % "0.9.0"),
  // TODO decide which of these are necessary
  relativeSourceMaps := true,
  scalaJSStage in Global := FastOptStage,
  persistLauncher in Compile := true,
  persistLauncher in Test := false,
  skip in packageJSDependencies := false,
  jsDependencies ++= Seq(
    RuntimeDOM,
    "org.webjars" % "jquery" % "2.1.4" / "2.1.4/jquery.js")
)

lazy val turkeyJS = turkey.js
lazy val turkeyJVM = turkey.jvm

lazy val turkeySample = crossProject.in(file("sample")).settings(
  name := "turkey-sample",
  organization := "com.github.julianmichael",
  version := "0.1-SNAPSHOT",
  scalaVersion in ThisBuild := "2.11.8",
  scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked"),
  resolvers += Resolver.sonatypeRepo("snapshots"),
  libraryDependencies ++= Seq(
    "com.lihaoyi" %%% "upickle" % "0.4.1",
    "com.lihaoyi" %%% "scalatags" % "0.4.6",
    "com.github.julien-truffaut" %%% "monocle-core"  % monocleVersion,
    "com.github.julien-truffaut" %%% "monocle-macro" % monocleVersion
  )
).jvmSettings(
  fork in console := true,
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor" % "2.4.8",
    "com.typesafe.akka" %% "akka-http-experimental" % "2.4.9",
    // java deps:
    "ch.qos.logback" % "logback-classic" % "1.2.3")
).jsSettings(
  addCompilerPlugin("org.scalamacros" %% "paradise" % "2.1.0" cross CrossVersion.full),
  libraryDependencies ++= Seq(
    "org.scala-js" %%% "scalajs-dom" % "0.9.0",
    "be.doeraene" %%% "scalajs-jquery" % "0.9.0",
    "com.github.japgolly.scalajs-react" %%% "core" % scalaJSReactVersion,
    "com.github.japgolly.scalajs-react" %%% "ext-monocle" % scalaJSReactVersion,
    "com.github.japgolly.scalacss" %%% "core" % "0.4.1",
    "com.github.japgolly.scalacss" %%% "ext-react" % "0.4.1"
  ),
  relativeSourceMaps := true,
  scalaJSStage in Global := FastOptStage,
  persistLauncher in Compile := true,
  persistLauncher in Test := false,
  skip in packageJSDependencies := false,
  jsDependencies ++= Seq(
    RuntimeDOM,
    "org.webjars" % "jquery" % "2.1.4" / "2.1.4/jquery.js",

    "org.webjars.bower" % "react" % "15.0.2"
      /        "react-with-addons.js"
      minified "react-with-addons.min.js"
      commonJSName "React",

    "org.webjars.bower" % "react" % "15.0.2"
      /         "react-dom.js"
      minified  "react-dom.min.js"
      dependsOn "react-with-addons.js"
      commonJSName "ReactDOM",

    "org.webjars.bower" % "react" % "15.0.2"
      /         "react-dom-server.js"
      minified  "react-dom-server.min.js"
      dependsOn "react-dom.js"
      commonJSName "ReactDOMServer"
  )
)

lazy val turkeySampleJS = turkeySample.js.dependsOn(turkeyJS)
lazy val turkeySampleJVM = turkeySample.jvm.dependsOn(turkeyJVM).settings(
  (resources in Compile) += (fastOptJS in (turkeySampleJS, Compile)).value.data,
  (resources in Compile) += (packageScalaJSLauncher in (turkeySampleJS, Compile)).value.data,
  (resources in Compile) += (packageJSDependencies in (turkeySampleJS, Compile)).value
)

