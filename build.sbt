lazy val `http4s-resource-example` = project.in(file("."))
    .settings(commonSettings)
    .settings(
      name := "http4s-resource-example"
    )

/***********************************************************************\
                      Boilerplate below these lines
\***********************************************************************/




lazy val commonSettings = Seq(
  organization := "org.http4s",
  licenses += ("MIT", url("https://opensource.org/licenses/MIT")),

  scalaVersion := "2.12.4",

  addCompilerPlugin("org.spire-math" % "kind-projector" % "0.9.10" cross CrossVersion.binary),

  libraryDependencies ++= Seq(

    "org.http4s"                  %% "http4s-dsl"                 % "0.20.15",
    "org.http4s"                  %% "http4s-blaze-server"        % "0.20.15",
    "org.http4s"                  %% "http4s-blaze-client"        % "0.20.15",
//    "org.http4s"                  %% "http4s-circe"               % "0.18.0-M5",

    "ch.qos.logback"              % "logback-classic"             % "1.2.3",

    "org.specs2"                  %% "specs2-core"                % "4.6.0"       % Test,
  )
)
