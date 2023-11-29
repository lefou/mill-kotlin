// mill plugins
import $ivy.`de.tototec::de.tobiasroeser.mill.integrationtest::0.7.1`
import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version::0.4.0`
import $ivy.`com.lihaoyi::mill-contrib-scoverage:`

// imports
import de.tobiasroeser.mill.integrationtest._
import de.tobiasroeser.mill.vcs.version.VcsVersion

import mill._
import mill.api.Loose
import mill.contrib.scoverage.ScoverageModule
import mill.define.{Command, Module, TaskModule, Target, Task}
import mill.scalalib._
import mill.scalalib.api.ZincWorkerUtil
import mill.scalalib.publish._

trait Deps {
  def kotlinVersion = "1.0.0"
  def millPlatform: String
  def millVersion: String
  def scalaVersion: String = "2.13.12"
  def testWithMill: Seq[String]

  val kotlinCompiler = ivy"org.jetbrains.kotlin:kotlin-compiler:${kotlinVersion}"
  val millMainApi = ivy"com.lihaoyi::mill-main-api:${millVersion}"
  val millMain = ivy"com.lihaoyi::mill-main:${millVersion}"
  val millScalalib = ivy"com.lihaoyi::mill-scalalib:${millVersion}"
  def osLib: Dep
  val scalaTest = ivy"org.scalatest::scalatest:3.2.17"
  val scoverageVersion = "2.0.11"
  val slf4j = ivy"org.slf4j:slf4j-api:1.7.25"
  val utilsFunctional = ivy"de.tototec:de.tototec.utils.functional:2.0.1"
}
object Deps_0_11 extends Deps {
  override def millVersion = "0.11.0" // scala-steward:off
  override def millPlatform = "0.11"
  // keep in sync with .github/workflows/build.yml
  override def testWithMill = Seq(millVersion)
  override val osLib = ivy"com.lihaoyi::os-lib:0.9.1" // scala-steward:off
}
object Deps_0_10 extends Deps {
  override def millVersion = "0.10.0" // scala-steward:off
  override def millPlatform = "0.10"
  // keep in sync with .github/workflows/build.yml
  override def testWithMill = Seq("0.10.11", "0.10.3", millVersion)
  override val osLib = ivy"com.lihaoyi::os-lib:0.8.0" // scala-steward:off
}
object Deps_0_9 extends Deps {
  override def millVersion = "0.9.3" // scala-steward:off
  override def millPlatform = "0.9"
  // keep in sync with .github/workflows/build.yml
  override def testWithMill = Seq("0.9.12", millVersion)
  override val osLib = ivy"com.lihaoyi::os-lib:0.6.3" // scala-steward:off
}
object Deps_0_7 extends Deps {
  override def millVersion = "0.7.0" // scala-steward:off
  override def millPlatform = "0.7"
  // keep in sync with .github/workflows/build.yml
  override def testWithMill = Seq("0.8.0", "0.7.4", "0.7.1", millVersion)
  override val osLib = ivy"com.lihaoyi::os-lib:0.6.3" // scala-steward:off
}

val millApiVersions = Seq(Deps_0_11, Deps_0_10, Deps_0_9, Deps_0_7).map(x => x.millPlatform -> x)

val millItestVersions = millApiVersions.flatMap { case (_, d) => d.testWithMill.map(_ -> d) }

lazy val baseDir: os.Path = build.millSourcePath

trait MillKotlinModule extends PublishModule with ScoverageModule with Cross.Module[String] {
  def millPlatform: String = crossValue
  def deps: Deps = millApiVersions.toMap.apply(millPlatform)
  override def scalaVersion = deps.scalaVersion
  override def publishVersion: T[String] = VcsVersion.vcsState().format()
  override def artifactSuffix: T[String] = s"_mill${millPlatform}_${artifactScalaVersion()}"

  override def javacOptions = {
    val release =
      if (scala.util.Properties.isJavaAtLeast(11)) Seq("-release", "8")
      else Seq("-source", "1.8", "-target", "1.8")
    release ++ Seq("-encoding", "UTF-8", "-deprecation")
  }
  override def scalacOptions = Seq("-release:8", "-encoding", "UTF-8", "-deprecation")

  override def scoverageVersion = deps.scoverageVersion

  def pomSettings = T {
    PomSettings(
      description = "Kotlin compiler support for Mill",
      organization = "de.tototec",
      url = "https://github.com/lefou/mill-kotlin",
      licenses = Seq(License.`Apache-2.0`),
      versionControl = VersionControl.github("lefou", "mill-kotlin"),
      developers = Seq(Developer("lefou", "Tobias Roeser", "https.//github.com/lefou"))
    )
  }

  override def skipIdea: Boolean = millApiVersions.head._2.millPlatform != millPlatform
}

object main extends Cross[MainCross](millApiVersions.map(_._1))
trait MainCross extends MillKotlinModule {
  override def artifactName = "de.tobiasroeser.mill.kotlin"
  override def moduleDeps: Seq[PublishModule] = Seq(worker)
  override def ivyDeps = Agg(
    ivy"${scalaOrganization()}:scala-library:${scalaVersion()}"
  )
  override def sources = T.sources {
    val suffixes =
      ZincWorkerUtil.matchingVersions(millPlatform) ++
        ZincWorkerUtil.versionRanges(millPlatform, millApiVersions.map(_._1))

    PathRef(millSourcePath / s"src") +:
      suffixes.map(v => PathRef(millSourcePath / ("src-" + v)))
  }

  override def compileIvyDeps = Agg(
    deps.millMain,
    deps.millScalalib
  )

  object test extends ScalaTests with TestModule.ScalaTest {
    override def ivyDeps = Agg(
      deps.scalaTest
    )
  }

  override def generatedSources: Target[Seq[PathRef]] = T {
    super.generatedSources() :+ versionFile()
  }

  def versionFile: Target[PathRef] = T {
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
         |  val millKotlinWorkerImplIvyDep = "${worker.impl.pomSettings().organization}:${worker.impl.artifactId()}:${worker.impl.publishVersion()}"
         |  /** The default kotlin version used for the compiler. */
         |  val kotlinCompilerVersion = "${deps.kotlinVersion}"
         |}
         |""".stripMargin

    os.write(dest / "Versions.scala", body)
    PathRef(dest)
  }

  object worker extends MillKotlinModule with CrossValue {
    override def artifactName = "de.tobiasroeser.mill.kotlin.worker"
    override def compileIvyDeps: T[Agg[Dep]] = Agg(
      deps.millMainApi,
      deps.osLib
    )

    object impl extends MillKotlinModule with CrossValue {
      override def artifactName = "de.tobiasroeser.mill.kotlin.worker.impl"
      override def moduleDeps: Seq[PublishModule] = Seq(worker)
      override def compileIvyDeps: T[Agg[Dep]] = Agg(
        deps.osLib,
        deps.millMainApi,
        deps.kotlinCompiler
      )
    }
  }
}

object itest extends Cross[ItestCross](millItestVersions.map(_._1))
trait ItestCross extends MillIntegrationTestModule with Cross.Module[String] {
  def millItestVersion = crossValue
  val millPlatform = millItestVersions.toMap.apply(millItestVersion).millPlatform
  def deps: Deps = millApiVersions.toMap.apply(millPlatform)

  override def millTestVersion = millItestVersion
  override def pluginsUnderTest = Seq(main(millPlatform))
  override def temporaryIvyModules = Seq(main(millPlatform).worker, main(millPlatform).worker.impl)
  override def testTargets: T[Seq[String]] = T { Seq("-d", "verify") }

  override def testCases: T[Seq[PathRef]] = T {
    super.testCases().filter { tc =>
//        sys.props("java.version").startsWith("1.8") ||
//          (!sys.props("java.version").startsWith("1.") &&
      !Seq("kotlin-1.0", "kotlin-1.1", "kotlin-1.2").exists(suffix => tc.path.last.endsWith(suffix))
//        )
    }
  }

  override def temporaryIvyModulesDetails: Task[Seq[(PathRef, (PathRef, (PathRef, (PathRef, (PathRef, Artifact)))))]] =
    Target.traverse(temporaryIvyModules) { p =>
      val jar = p match {
        case p: ScoverageModule => p.scoverage.jar
        case p => p.jar
      }
      jar zip (p.sourceJar zip (p.docJar zip (p.pom zip (p.ivy zip p.artifactMetadata))))
    }
  override def pluginUnderTestDetails: Task[Seq[(PathRef, (PathRef, (PathRef, (PathRef, (PathRef, Artifact)))))]] =
    Target.traverse(pluginsUnderTest) { p =>
      val jar = p match {
        case p: ScoverageModule => p.scoverage.jar
        case p => p.jar
      }
      jar zip (p.sourceJar zip (p.docJar zip (p.pom zip (p.ivy zip p.artifactMetadata))))
    }

  override def perTestResources = T.sources {
    Seq(generatedSharedSrc())
  }

  def generatedSharedSrc = T {
    os.write(
      T.dest / "shared.sc",
      s"""import $$ivy.`org.scoverage::scalac-scoverage-runtime:${deps.scoverageVersion}`
         |import $$ivy.`org.scalatest::scalatest:${deps.scalaTest.dep.version}`
         |""".stripMargin
    )
    PathRef(T.dest)
  }
}

object P extends Module {

  /**
   * Update the millw script.
   */
  def millw() = T.command {
    val target = mill.util.Util.download("https://raw.githubusercontent.com/lefou/millw/master/millw")
    val millw = baseDir / "millw"
    os.copy.over(target.path, millw)
    os.perms.set(millw, os.perms(millw) + java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE)
    target
  }

}
