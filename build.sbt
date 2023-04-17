lazy val scalaVersions = Seq("3.2.2", "2.13.10")

ThisBuild / scalaVersion := scalaVersions.head
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / organization := "de.bitmarck.bms"
name := (gateway / name).value

val V = new {
  val betterMonadicFor = "0.3.1"
  val catsEffect = "3.4.9"
  val circe = "0.14.5"
  val circeConfig = "0.10.0"
  val fs2Secon = "0.1.0"
  val http4s = "0.23.18"
  val http4sJdkHttpClient = "0.9.0"
  val logbackClassic = "1.4.6"
  val munit = "0.7.29"
  val munitTaglessFinal = "0.2.1"
  val proxyVole = "1.0.18"
}

lazy val commonSettings: SettingsDefinition = Def.settings(
  version := {
    val Tag = "refs/tags/v?([0-9]+(?:\\.[0-9]+)+(?:[+-].*)?)".r
    sys.env.get("CI_VERSION").collect { case Tag(tag) => tag }
      .getOrElse("0.0.1-SNAPSHOT")
  },

  licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0")),

  homepage := scmInfo.value.map(_.browseUrl),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/bitmarck-service/fs2-secon"),
      "scm:git@github.com:bitmarck-service/fs2-secon.git"
    )
  ),
  developers := List(
    Developer(id = "u016595", name = "Pierre Kisters", email = "pierre.kisters@bitmarck.de", url = url("https://github.com/lhns/"))
  ),

  libraryDependencies ++= Seq(
    "ch.qos.logback" % "logback-classic" % V.logbackClassic % Test,
    "de.lhns" %% "munit-tagless-final" % V.munitTaglessFinal % Test,
    "org.scalameta" %% "munit" % V.munit % Test,
  ),

  testFrameworks += new TestFramework("munit.Framework"),

  libraryDependencies ++= virtualAxes.?.value.getOrElse(Seq.empty).collectFirst {
    case VirtualAxis.ScalaVersionAxis(version, _) if version.startsWith("2.") =>
      compilerPlugin("com.olegpy" %% "better-monadic-for" % V.betterMonadicFor)
  },

  assembly / assemblyJarName := s"${name.value}-${version.value}.sh.bat",
  assembly / assemblyOption := (assembly / assemblyOption).value
    .withPrependShellScript(Some(AssemblyPlugin.defaultUniversalScript(shebang = false))),
  assembly / assemblyMergeStrategy := {
    case PathList(paths@_*) if paths.last == "module-info.class" => MergeStrategy.discard
    case x =>
      val oldStrategy = (assembly / assemblyMergeStrategy).value
      oldStrategy(x)
  },

  Compile / doc / sources := Seq.empty,

  publishMavenStyle := true,

  publishTo := sonatypePublishToBundle.value,

  sonatypeCredentialHost := "oss.sonatype.org",

  credentials ++= (for {
    username <- sys.env.get("SONATYPE_USERNAME")
    password <- sys.env.get("SONATYPE_PASSWORD")
  } yield Credentials(
    "Sonatype Nexus Repository Manager",
    sonatypeCredentialHost.value,
    username,
    password
  )).toList
)

lazy val root: Project = project.in(file("."))
  .settings(commonSettings)
  .settings(
    publishArtifact := false,
    publish / skip := true
  )
  .aggregate(core.projectRefs: _*)
  .aggregate(gateway)

lazy val core = projectMatrix.in(file("core"))
  .settings(commonSettings)
  .settings(
    name := "http4s-secon",
    libraryDependencies ++= Seq(
      "de.bitmarck.bms" %% "fs2-secon" % V.fs2Secon,
      "org.http4s" %% "http4s-server" % V.http4s,
      "io.circe" %% "circe-generic" % V.circe,
    )
  )
  .jvmPlatform(scalaVersions)

lazy val gateway = project.in(file("gateway"))
  .dependsOn(core.jvm(scalaVersions.head))
  .settings(commonSettings)
  .settings(
    name := "secon-gateway",
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % V.logbackClassic,
      "org.bidib.com.github.markusbernhardt" % "proxy-vole" % V.proxyVole,
      "org.http4s" %% "http4s-ember-server" % V.http4s,
      "org.http4s" %% "http4s-jdk-http-client" % V.http4sJdkHttpClient,
      "org.typelevel" %% "cats-effect" % V.catsEffect,
      "io.circe" %% "circe-config" % V.circeConfig,
      "io.circe" %% "circe-generic" % V.circe,
    ),
  )
