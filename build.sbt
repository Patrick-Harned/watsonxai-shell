ThisBuild / scalaVersion := "3.7.1"
// bring in the crossProject / Scala.js imports

import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

// In your build.sbt
ThisBuild / scalacOptions ++= Seq(
  "-Xmax-inlines", "64"  // or higher if needed
)

lazy val shared = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)       // common code lives in shared/src/main/scala
  .in(file("shared"))
  .settings(
    name := "shared-domain",
    version := "0.1-SNAPSHOT"
  )
  .jvmSettings(
    // JVM-only artifacts
    libraryDependencies ++= Seq(
      "io.circe"        %% "circe-core"    % "0.14.14",
      "io.circe"        %% "circe-generic" % "0.14.14",
      "com.softwaremill.sttp.tapir" %% "tapir-core"       % "1.11.44",
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % "1.11.44"
    )
  )
  .jsSettings(
    // JS-only artifacts: note the %%%
    libraryDependencies ++= Seq(
      "io.circe"        %%% "circe-core"    % "0.14.14",
      "io.circe"        %%% "circe-generic" % "0.14.14",
      "com.softwaremill.sttp.tapir" %%% "tapir-core"       % "1.11.44",
      "com.softwaremill.sttp.tapir" %%% "tapir-json-circe" % "1.11.44"
    ),
    scalaJSUseMainModuleInitializer := true
  )
// aliases for clarity
lazy val sharedJvm = shared.jvm
lazy val sharedJs  = shared.js
lazy val server = project
  .in(file("server"))
  .dependsOn(sharedJvm)
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel"   %% "cats-effect"           % "3.6.3",
      "org.http4s"      %% "http4s-ember-server"   % "0.23.30",
      "org.http4s"      %% "http4s-dsl"            % "0.23.30",
      "org.http4s"      %% "http4s-circe"          % "0.23.30",
   "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % "2.20.0",
      "io.kubernetes" % "client-java" % "24.0.0",
      "com.softwaremill.sttp.client3" %% "circe" % "3.11.0",
      "com.softwaremill.sttp.client3" %% "async-http-client-backend-future" % "3.11.0",

      "org.xerial" % "sqlite-jdbc" % "3.50.3.0", // Or a newer version
      "io.circe"        %% "circe-core"    % "0.14.14",
      "io.circe"        %% "circe-generic" % "0.14.14",
"com.softwaremill.sttp.tapir" %% "tapir-http4s-server" % "1.11.44"
    ),
    Compile / resourceGenerators += Def.task {
      // 1. Grab the actual Scala version string
      val sv   = scalaVersion.value

      // 2. Compute the paths
      val base = (Compile / baseDirectory).value
      val js   = base / s"../client/target/scala-$sv/client-fastopt.js"
      val dest = (Compile / resourceManaged).value / "web"

      // 3. Copy and register
      IO.copyFile(js, dest / "client-fastopt.js")
      Seq(dest / "client-fastopt.js")
    }.taskValue

  )

lazy val client = project
  .in(file("client"))
  .enablePlugins(org.scalajs.sbtplugin.ScalaJSPlugin)
  .dependsOn(sharedJs)
  .settings(
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies ++= Seq(
      "com.raquo"        %%% "laminar"            % "17.2.1",
      "com.softwaremill.sttp.client3" %%% "core"  % "3.9.0",
      "com.softwaremill.sttp.client3" %%% "circe" % "3.9.0",
      "io.circe"        %%% "circe-core"    % "0.14.14",
      "io.circe"        %%% "circe-generic" % "0.14.14",
    )
  )

// Aggregate for convenience
lazy val root = (project in file("."))
  .aggregate(shared.jvm,shared.js, server, client)
  .settings(publish := {}, publishLocal := {})
