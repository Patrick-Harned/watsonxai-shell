ThisBuild / scalaVersion := "3.7.1"

lazy val shared = project
  .in(file("shared"))
  .settings(
    libraryDependencies ++= Seq(
      "io.circe"        %% "circe-core"    % "0.14.14",
      "io.circe"        %% "circe-generic" % "0.14.14",
      "com.softwaremill.sttp.tapir" %% "tapir-core"       % "1.11.44",
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % "1.11.44"
    )
  )

lazy val server = project
  .in(file("server"))
  .dependsOn(shared)
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel"   %% "cats-effect"           % "3.6.3",
      "org.http4s"      %% "http4s-ember-server"   % "0.23.30",
      "org.http4s"      %% "http4s-dsl"            % "0.23.30",
      "org.http4s"      %% "http4s-circe"          % "0.23.30",
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
  .dependsOn(shared)
  .settings(
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies ++= Seq(
      "com.raquo"        %%% "laminar"            % "17.2.1",
      "com.softwaremill.sttp.client3" %%% "core"  % "3.9.0",
      "com.softwaremill.sttp.client3" %%% "circe" % "3.9.0",
    )
  )

// Aggregate for convenience
lazy val root = (project in file("."))
  .aggregate(shared, server, client)
  .settings(publish := {}, publishLocal := {})
