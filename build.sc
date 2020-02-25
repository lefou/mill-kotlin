import mill._
import mill.define.{Module, Target}
import mill.scalalib._
import mill.scalalib.publish._
import $ivy.`de.tototec::de.tobiasroeser.mill.integrationtest:0.1.2`
import de.tobiasroeser.mill.integrationtest._
import mill.api.Loose
import mill.main.Tasks


object Deps {
  def kotlinVersion = "1.3.61"
  def millVersion = "0.5.7"
  def scalaVersion = "2.12.10"

  val kotlinCompiler = ivy"org.jetbrains.kotlin:kotlin-compiler:${kotlinVersion}"
  val logbackClassic = ivy"ch.qos.logback:logback-classic:1.1.3"
  val millMainApi = ivy"com.lihaoyi::mill-main-api:${millVersion}"
  val millMain = ivy"com.lihaoyi::mill-main:${millVersion}"
  val millScalalib = ivy"com.lihaoyi::mill-scalalib:${millVersion}"
  val osLib = ivy"com.lihaoyi::os-lib:0.6.2"
  val scalaTest = ivy"org.scalatest::scalatest:3.0.8"
  val slf4j = ivy"org.slf4j:slf4j-api:1.7.25"
  val utilsFunctional = ivy"de.tototec:de.tototec.utils.functional:2.0.1"
}

trait MillKotlinModule extends ScalaModule with PublishModule {

  def scalaVersion = T { Deps.scalaVersion }

  def publishVersion = GitSupport.publishVersion()._2

  override def javacOptions = Seq("-source", "1.8", "-target", "1.8")

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

}

object api extends MillKotlinModule {
  override def artifactName = T { "de.tobiasroeser.mill.kotlin-api" }
  override def compileIvyDeps: T[Loose.Agg[Dep]] = T{ Agg(
    Deps.millMainApi,
    Deps.osLib
  )}
}

object worker extends MillKotlinModule {
  override def artifactName = T { "de.tobiasroeser.mill.kotlin-worker" }
  override def moduleDeps: Seq[PublishModule] = Seq(api)
  override def compileIvyDeps: T[Loose.Agg[Dep]] = T{ Agg(
    Deps.osLib,
    Deps.millMainApi,
    Deps.kotlinCompiler
  )}
}

object main extends MillKotlinModule with ScalaModule {
  override def artifactName = T { "de.tobiasroeser.mill.kotlin" }
  override def moduleDeps: Seq[PublishModule] = Seq(api)
  override def ivyDeps = T {
    Agg(ivy"${scalaOrganization()}:scala-library:${scalaVersion()}")
  }
  override def compileIvyDeps = Agg(
    Deps.millMain,
    Deps.millScalalib
  )

  object test extends Tests {
    override def ivyDeps = Agg(
      Deps.scalaTest
    )
    def testFrameworks = Seq("org.scalatest.tools.Framework")
  }

  override def generatedSources: Target[Seq[PathRef]] = T{
    super.generatedSources() ++ {
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
          |  val buildTimeMillVersion = "${Deps.millVersion}"
          |  /** The ivy dependency holding the mill kotlin worker impl. */
          |  val millKotlinWorkerImplIvyDep = "${worker.pomSettings().organization}:${worker.artifactId()}:${worker.publishVersion()}"
          |  /** The default kotlin version used for the compiler. */
          |  val kotlinCompilerVersion = "${Deps.kotlinVersion}"
          |}
          |""".stripMargin

      os.write(dest / "Versions.scala", body)

      Seq(PathRef(dest))
    }
  }

}

object itest extends MillIntegrationTestModule {

  def millTestVersion = T {
    val ctx = T.ctx()
    ctx.env.get("TEST_MILL_VERSION").filterNot(_.isEmpty).getOrElse(Deps.millVersion)
  }

  def pluginsUnderTest = Seq(main)
  def temporaryIvyModules = Seq(api, worker)

  override def testTargets: T[Seq[String]] = T{ Seq("-d", "verify") }

}

object GitSupport extends Module {

  /**
   * The current git revision.
   */
  def gitHead: T[String] = T.input {
    sys.env.get("TRAVIS_COMMIT").getOrElse(
      os.proc('git, "rev-parse", "HEAD").call().out.trim
    ).toString()
  }

  /**
   * Calc a publishable version based on git tags and dirty state.
   *
   * @return A tuple of (the latest tag, the calculated version string)
   */
  def publishVersion: T[(String, String)] = T.input {
    val tag =
      try Option(
        os.proc('git, 'describe, "--exact-match", "--tags", "--always", gitHead()).call().out.trim
      )
      catch {
        case e => None
      }

    val dirtySuffix = os.proc('git, 'diff).call().out.string.trim() match {
      case "" => ""
      case s => "-DIRTY" + Integer.toHexString(s.hashCode)
    }

    tag match {
      case Some(t) => (t, t)
      case None =>
        val latestTaggedVersion = os.proc('git, 'describe, "--abbrev=0", "--always", "--tags").call().out.trim

        val commitsSinceLastTag =
          os.proc('git, "rev-list", gitHead(), "--not", latestTaggedVersion, "--count").call().out.trim.toInt

        (latestTaggedVersion, s"$latestTaggedVersion-$commitsSinceLastTag-${gitHead().take(6)}$dirtySuffix")
    }
  }

}


/** Run tests. */
def test() = T.command {
  main.test.test()()
  itest.test()()
}

def install() = T.command {
  T.ctx().log.info("Installing")
  test()()
  api.publishLocal()()
  worker.publishLocal()()
  main.publishLocal()()
}

def checkRelease: T[Boolean] = T.input {
  if (GitSupport.publishVersion()._2.contains("DIRTY")) {
    mill.api.Result.Failure("Project (git) state is dirty. Release not recommended!", Some(false))
  } else { true }
}

/** Test and release to Maven Central. */
def release(
             sonatypeCreds: String,
             release: Boolean = true
           ) = T.command {
  if(checkRelease()) {
    test()()
    PublishModule.publishAll(
      sonatypeCreds = sonatypeCreds,
      release = release,
      publishArtifacts = Tasks(Seq(
        api.publishArtifacts,
        worker.publishArtifacts,
        main.publishArtifacts
      )),
      readTimeout = 600000
    )()
  }
  ()
}

/**
 * Update the millw script.
 */
def millw() = T.command {
  val target = mill.modules.Util.download("https://raw.githubusercontent.com/lefou/millw/master/millw")
  val millw = build.millSourcePath / "millw"
  os.copy.over(target.path, millw)
  os.perms.set(millw, os.perms(millw) + java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE)
  target
}
