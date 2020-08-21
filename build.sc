// mill plugins
import $ivy.`de.tototec::de.tobiasroeser.mill.integrationtest:0.3.1-16-f356b6`
import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version:0.0.1`
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
  def kotlinVersion = "1.3.61"
  def millVersion = "0.7.0"
  def scalaVersion = "2.13.2"

  val kotlinCompiler = ivy"org.jetbrains.kotlin:kotlin-compiler:${kotlinVersion}"
  val logbackClassic = ivy"ch.qos.logback:logback-classic:1.1.3"
  val millMainApi = ivy"com.lihaoyi::mill-main-api:${millVersion}"
  val millMain = ivy"com.lihaoyi::mill-main:${millVersion}"
  val millScalalib = ivy"com.lihaoyi::mill-scalalib:${millVersion}"
  val osLib = ivy"com.lihaoyi::os-lib:0.6.3"
  val scalaTest = ivy"org.scalatest::scalatest:3.2.1"
  val slf4j = ivy"org.slf4j:slf4j-api:1.7.25"
  val utilsFunctional = ivy"de.tototec:de.tototec.utils.functional:2.0.1"
}
object Deps_0_7 extends Deps
object Deps_0_6 extends Deps {
  override def millVersion = "0.6.0"
  override def scalaVersion = "2.12.10"
}

val millApiVersions = Seq(
  "0.7" -> Deps_0_7,
  "0.6" -> Deps_0_6,
)

val millItestVersions = Seq(
  "0.7.4", "0.7.3", "0.7.2", "0.7.1", "0.7.0",
  "0.6.3", "0.6.2", "0.6.1", "0.6.0",
)

val baseDir = build.millSourcePath


trait MillKotlinModule extends CrossScalaModule with PublishModule with ScoverageModule {
  def deps: Deps
  override def crossScalaVersion = deps.scalaVersion
  override def publishVersion: T[String] = VcsVersion.vcsState().format()

  override def javacOptions = Seq("-source", "1.8", "-target", "1.8", "-encoding", "UTF-8")
  override def scalacOptions = Seq("-target:jvm-1.8", "-encoding", "UTF-8")
  override def scoverageVersion = "1.4.1"

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
class ApiCross(millApiVersion: String) extends MillKotlinModule {
  override def deps: Deps = millApiVersions.toMap.apply(millApiVersion)
  override def artifactName = T { "de.tobiasroeser.mill.kotlin-api" }
  override def compileIvyDeps: T[Loose.Agg[Dep]] = T{ Agg(
    deps.millMainApi,
    deps.osLib
  )}
}

object worker extends Cross[WorkerCross](millApiVersions.map(_._1): _*)
class WorkerCross(millApiVersion: String) extends MillKotlinModule {
  override def deps: Deps = millApiVersions.toMap.apply(millApiVersion)
  override def artifactName = T { "de.tobiasroeser.mill.kotlin-worker" }
  override def moduleDeps: Seq[PublishModule] = Seq(api(millApiVersion))
  override def compileIvyDeps: T[Loose.Agg[Dep]] = T{ Agg(
    deps.osLib,
    deps.millMainApi,
    deps.kotlinCompiler
  )}
}

object main extends Cross[MainCross](millApiVersions.map(_._1): _*)
class MainCross(millApiVersion: String) extends MillKotlinModule {
  override def deps: Deps = millApiVersions.toMap.apply(millApiVersion)
  override def artifactName = T { "de.tobiasroeser.mill.kotlin" }
  override def moduleDeps: Seq[PublishModule] = Seq(api(millApiVersion))
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
        |  val millKotlinWorkerImplIvyDep = "${worker(millApiVersion).pomSettings().organization}:${worker(millApiVersion).artifactId()}:${worker(millApiVersion).publishVersion()}"
        |  /** The default kotlin version used for the compiler. */
        |  val kotlinCompilerVersion = "${deps.kotlinVersion}"
        |}
        |""".stripMargin

    os.write(dest / "Versions.scala", body)
    PathRef(dest)
}

}

object itest extends Cross[ItestCross](millItestVersions: _*)
class ItestCross(millItestVersion: String) extends MillIntegrationTestModule {
  val millApiVersion = millItestVersion.split("[.]").take(2).mkString(".")
  override def millSourcePath: os.Path = super.millSourcePath / os.up
  override def millTestVersion = millItestVersion
  override def pluginsUnderTest = Seq(main(millApiVersion))
  override def temporaryIvyModules = Seq(api(millApiVersion), worker(millApiVersion))
  override def testTargets: T[Seq[String]] = T{ Seq("-d", "verify") }

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
