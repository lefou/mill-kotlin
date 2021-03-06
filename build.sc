// mill plugins
import $ivy.`de.tototec::de.tobiasroeser.mill.integrationtest_mill0.7:0.4.0-5-9dce73`
import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version_mill0.7:0.1.0`
import $ivy.`com.lihaoyi::mill-contrib-scoverage:$MILL_VERSION`

// imports
import de.tobiasroeser.mill.integrationtest._
import de.tobiasroeser.mill.vcs.version.VcsVersion
import mill._
import mill.api.Loose
import mill.contrib.scoverage.ScoverageModule
import mill.define.{Module, Target, Task}
import mill.main.Tasks
import mill.scalalib._
import mill.scalalib.publish._

trait Deps {
  def kotlinVersion = "1.0.0"
  def millPlatform: String
  def millVersion: String
  def scalaVersion: String
  def testWithMill: Seq[String]

  val kotlinCompiler = ivy"org.jetbrains.kotlin:kotlin-compiler:${kotlinVersion}"
  val logbackClassic = ivy"ch.qos.logback:logback-classic:1.1.3"
  val millMainApi = ivy"com.lihaoyi::mill-main-api:${millVersion}"
  val millMain = ivy"com.lihaoyi::mill-main:${millVersion}"
  val millScalalib = ivy"com.lihaoyi::mill-scalalib:${millVersion}"
  val osLib = ivy"com.lihaoyi::os-lib:0.6.3"
  val scalaTest = ivy"org.scalatest::scalatest:3.2.3"
  val scoverageVersion = "1.4.6"
  val slf4j = ivy"org.slf4j:slf4j-api:1.7.25"
  val utilsFunctional = ivy"de.tototec:de.tototec.utils.functional:2.0.1"
}
object Deps_0_9 extends Deps {
  override def millVersion = "0.9.3"
  override def millPlatform = "0.9"
  override def scalaVersion = "2.13.4"
  // keep in sync with .github/workflows/build.yml
  override def testWithMill = Seq("0.9.7", "0.9.6", "0.9.5", "0.9.4", "0.9.3")
}
object Deps_0_7 extends Deps {
  override def millVersion = "0.7.0"
  override def millPlatform = "0.7"
  override def scalaVersion = "2.13.2"
  // keep in sync with .github/workflows/build.yml
  override def testWithMill = Seq("0.8.0", "0.7.4", "0.7.3", "0.7.2", "0.7.1", "0.7.0")
}
object Deps_0_6 extends Deps {
  override def millVersion = "0.6.0"
  override def millPlatform = "0.6"
  override def scalaVersion = "2.12.10"
  // keep in sync with .github/workflows/build.yml
  override def testWithMill = Seq("0.6.3", "0.6.2", "0.6.1", "0.6.0")
}

val millApiVersions = Seq(Deps_0_9, Deps_0_7, Deps_0_6).map(x => x.millPlatform -> x)

val millItestVersions = millApiVersions.flatMap { case (_, d) => d.testWithMill.map(_ -> d) }

val baseDir = build.millSourcePath


trait MillKotlinModule extends CrossScalaModule with PublishModule with ScoverageModule {
  def millPlatform: String
  def deps: Deps = millApiVersions.toMap.apply(millPlatform)
  override def crossScalaVersion = deps.scalaVersion
  override def publishVersion: T[String] = VcsVersion.vcsState().format()
  override def artifactSuffix: T[String] = s"_mill${millPlatform}_${artifactScalaVersion()}"

  override def javacOptions = Seq("-source", "1.8", "-target", "1.8", "-encoding", "UTF-8")
  override def scalacOptions = Seq("-target:jvm-1.8", "-encoding", "UTF-8")
  override def scoverageVersion = deps.scoverageVersion
  // we need to adapt to changed publishing policy - patch-level
  override def scoveragePluginDep = T {
    ivy"org.scoverage:::scalac-scoverage-plugin:${scoverageVersion()}"
  }

  def pomSettings = T {
    PomSettings(
      description = "Kotlin compiler support for mill",
      organization = "de.tototec",
      url = "https://github.com/lefou/mill-kotlin",
      licenses = Seq(License.`Apache-2.0`),
      versionControl = VersionControl.github("lefou", "mill-kotlin"),
      developers = Seq(Developer("lefou", "Tobias Roeser", "https.//github.com/lefou"))
    )
  }

  override def skipIdea: Boolean = millApiVersions.head._2.scalaVersion != crossScalaVersion
}

object api extends Cross[ApiCross](millApiVersions.map(_._1): _*)
class ApiCross(override val millPlatform: String) extends MillKotlinModule {
  override def artifactName = T { "de.tobiasroeser.mill.kotlin-api" }
  override def compileIvyDeps: T[Loose.Agg[Dep]] = T{ Agg(
    deps.millMainApi,
    deps.osLib
  )}
}

object worker extends Cross[WorkerCross](millApiVersions.map(_._1): _*)
class WorkerCross(override val millPlatform: String) extends MillKotlinModule {
  override def artifactName = T { "de.tobiasroeser.mill.kotlin-worker" }
  override def moduleDeps: Seq[PublishModule] = Seq(api(millPlatform))
  override def compileIvyDeps: T[Loose.Agg[Dep]] = T{ Agg(
    deps.osLib,
    deps.millMainApi,
    deps.kotlinCompiler
  )}
}

object main extends Cross[MainCross](millApiVersions.map(_._1): _*)
class MainCross(override val millPlatform: String) extends MillKotlinModule {
  override def artifactName = T { "de.tobiasroeser.mill.kotlin" }
  override def moduleDeps: Seq[PublishModule] = Seq(api(millPlatform))
  override def ivyDeps = T {
    Agg(ivy"${scalaOrganization()}:scala-library:${scalaVersion()}")
  }
  override def compileIvyDeps = Agg(
    deps.millMain,
    deps.millScalalib
  )

  object test extends Tests {
    override def ivyDeps = Agg(
      deps.scalaTest
    )
    def testFrameworks = Seq("org.scalatest.tools.Framework")
  }

  override def generatedSources: Target[Seq[PathRef]] = T{
    super.generatedSources() :+ versionFile()
  }

  def versionFile: Target[PathRef] = T{
    val dest = T.ctx().dest
    val body =
      s"""package de.tobiasroeser.mill.kotlin
        |
        |/**
        | * Build-time generated versions file.
        | */
        |object Versions {
        |  /** The mill-kotlin version. */
        |  val millKotlinVersion = "${publishVersion()}"
        |  /** The mill API version used to build mill-kotlin. */
        |  val buildTimeMillVersion = "${deps.millVersion}"
        |  /** The ivy dependency holding the mill kotlin worker impl. */
        |  val millKotlinWorkerImplIvyDep = "${worker(millPlatform).pomSettings().organization}:${worker(millPlatform).artifactId()}:${worker(millPlatform).publishVersion()}"
        |  /** The default kotlin version used for the compiler. */
        |  val kotlinCompilerVersion = "${deps.kotlinVersion}"
        |}
        |""".stripMargin

    os.write(dest / "Versions.scala", body)
    PathRef(dest)
}

}

object itest extends Cross[ItestCross](millItestVersions.map(_._1): _*)
class ItestCross(millItestVersion: String) extends MillIntegrationTestModule {
  val millPlatform = millItestVersions.toMap.apply(millItestVersion).millPlatform
  override def millSourcePath: os.Path = super.millSourcePath / os.up
  override def millTestVersion = millItestVersion
  override def pluginsUnderTest = Seq(main(millPlatform))
  override def temporaryIvyModules = Seq(api(millPlatform), worker(millPlatform))
  override def testTargets: T[Seq[String]] = T{ Seq("-d", "verify") }

  override def testCases: T[Seq[PathRef]] = T{
    super.testCases().
      filter { tc =>
//        sys.props("java.version").startsWith("1.8") ||
//          (!sys.props("java.version").startsWith("1.") &&
            !Seq("kotlin-1.0", "kotlin-1.1", "kotlin-1.2").exists(suffix => tc.path.last.endsWith(suffix))
//        )
      }
  }

  override def temporaryIvyModulesDetails: Task.Sequence[(PathRef, (PathRef, (PathRef, (PathRef, (PathRef, Artifact)))))] =
    Target.traverse(temporaryIvyModules) { p =>
      val jar = p match {
        case p: ScoverageModule => p.scoverage.jar
        case p => p.jar
      }
      jar zip (p.sourceJar zip (p.docJar zip (p.pom zip (p.ivy zip p.artifactMetadata))))
    }
  override def pluginUnderTestDetails: Task.Sequence[(PathRef, (PathRef, (PathRef, (PathRef, (PathRef, Artifact)))))] =
    Target.traverse(pluginsUnderTest) { p =>
      val jar = p match {
        case p: ScoverageModule => p.scoverage.jar
        case p => p.jar
      }
      jar zip (p.sourceJar zip (p.docJar zip (p.pom zip (p.ivy zip p.artifactMetadata))))
    }
}

object P extends Module {

  /**
   * Update the millw script.
   */
  def millw() = T.command {
    val target = mill.modules.Util.download("https://raw.githubusercontent.com/lefou/millw/master/millw")
    val millw = baseDir / "millw"
    os.copy.over(target.path, millw)
    os.perms.set(millw, os.perms(millw) + java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE)
    target
  }

}
