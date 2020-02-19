package de.tobiasroeser.mill.kotlin

import java.net.{URL, URLClassLoader}

import mill.{Agg, T}
import mill.api.{Ctx, PathRef, Result}
import mill.define.Worker
import mill.modules.{Jvm, Util}
import mill.scalalib.{Dep, DepSyntax, JavaModule, TestModule}
import mill.scalalib.api.CompilationResult

trait KotlinModule extends JavaModule { outer =>

  /**
   * All individual source files fed into the compiler.
   */
  override def allSourceFiles = T {
    def isHiddenFile(path: os.Path) = path.last.startsWith(".")

    for {
      root <- allSources()
      if os.exists(root.path)
      path <- (if (os.isDir(root.path)) os.walk(root.path) else Seq(root.path))
      if os.isFile(path) && !isHiddenFile(path) && ((path.ext.toLowerCase() == "kt" || path.ext.toLowerCase() == "java"))
    } yield PathRef(path)
  }

  def kotlinVersion: T[String]

  def kotlinCompilerIvyDeps: T[Agg[Dep]] = T{ Agg(
    ivy"org.jetbrains.kotlin:kotlin-compiler:${kotlinCompilerVersion()}",
//    ivy"org.jetbrains.kotlin:kotlin-scripting-compiler:${kotlinCompilerVersion()}",
//    ivy"org.jetbrains.kotlin:kotlin-scripting-compiler-impl:${kotlinCompilerVersion()}",
//    ivy"org.jetbrains.kotlin:kotlin-scripting-common:${kotlinCompilerVersion()}",
    ivy"${Versions.millKotlinWorkerImplIvyDep}"
  )}

  def kotlinCompilerClasspath: T[Seq[PathRef]] = T {
    resolveDeps(kotlinCompilerIvyDeps)().toSeq
  }

  def kotlinWorker: Worker[KotlinWorker] = T.worker {
    val cl = new URLClassLoader(kotlinCompilerClasspath().map(_.path.toIO.toURI().toURL()).toArray[URL], getClass().getClassLoader())
    val className = classOf[KotlinWorker].getPackage().getName() + ".impl." + classOf[KotlinWorker].getSimpleName() + "Impl"
    val impl = cl.loadClass(className)
    val worker = impl.newInstance().asInstanceOf[KotlinWorker]
    if(worker.getClass().getClassLoader() != cl) {
      T.ctx().log.error(
        """Worker not loaded from worker classloader.
          |You should not add the mill-kotlin-worker JAR to the mill build classpath""".stripMargin)
    }
    if(worker.getClass().getClassLoader() == classOf[KotlinWorker].getClassLoader()) {
      T.ctx().log.error("Worker classloader used to load interface and implementation")
    }
    worker
  }

  override def compile: T[CompilationResult] = T {
    kotlinCompileTask()()
  }

  def kotlincHelp(args: String*) = T.command {
    kotlinCompileTask((Seq("-help") ++ args): _*)
  }

  protected def kotlinCompileTask(extraArgs: String*) = T.task {
    val dest = T.ctx().dest
    val classes = dest / "classes"
    os.makeDir.all(classes)

//    val kotlinHome = kotlinCompilerHome().path

    val worker = kotlinWorker()

    val workerResult = worker.compile(
      classpath = compileClasspath().map(_.path).toSeq,
      outDir = classes,
      sources = allSourceFiles().map(_.path),
      apiVersion = Option(kotlinVersion()).filterNot(_.isEmpty),
      languageVersion = Option(kotlinVersion()).filterNot(_.isEmpty),
      kotlincOptions = kotlincOptions() ++ extraArgs,
//      javacOptions()
    )

    //    aspectjWorker().compile(
    //      classpath = compileClasspath().toSeq.map(_.path),
    //      sourceDirs = allSources().map(_.path),
    //      options = ajcOptions(),
    //      aspectPath = effectiveAspectPath().toSeq.map(_.path),
    //      inPath = weavePath().map(_.path)
    //    )(T.ctx())

    val analysisFile = dest / "analysis.dummy"
    os.write(target = analysisFile, data = "", createFolders = true)

    workerResult match {
      case Result.Success(value) => CompilationResult(analysisFile, PathRef(classes))
      case Result.Failure(reason, value) => Result.Failure(reason, Some(CompilationResult(analysisFile, PathRef(classes))))
      case e: Result.Exception => e
      case Result.Aborted => Result.Aborted
      case Result.Skipped => Result.Skipped
//      case x => x
    }
  }

  def kotlinCompilerVersion: T[String] = T {
    Versions.kotlinCompilerVersion
  }

  def kotlincOptions: T[Seq[String]] = T { Seq.empty[String] }

//  def kotlinCompilerHome: T[PathRef] = T.persistent {
//    val version = kotlinCompilerVersion()
//    if (version.isEmpty()) sys.error("Undefined kotlinCompilerVersion")
//
//    val path = T.ctx().dest / version / "kotlinc"
//    val kotlinHome = if (os.isFile(path / "bin" / "kotlinc")) {
//      path
//    }
//    else {
//      val unpacked = Util.downloadUnpackZip(
//        url = s"https://github.com/JetBrains/kotlin/releases/download/v${version}/kotlin-compiler-${version}.zip",
//        dest = os.rel / version
//      )
//
//      unpacked.path / "kotlinc"
//    }
//    PathRef(kotlinHome)
//  }

  trait Tests extends super.Tests with KotlinTestModule {
    override def kotlinVersion: T[String] = T{ outer.kotlinVersion() }
    override def kotlinCompilerVersion: T[String] = T{ outer.kotlinCompilerVersion }
    override def kotlincOptions: T[Seq[String]] = T{ super.kotlincOptions }
  }

}

trait KotlinTestModule extends TestModule with KotlinModule {

}
