package de.tobiasroeser.mill.kotlin

import java.io.File
import java.net.{URL, URLClassLoader}

import mill.{Agg, T}
import mill.api.{Ctx, PathRef, Result}
import mill.define.{Command, Task, Worker}
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
      if os.isFile(path) && !isHiddenFile(path) && Seq("kt", "kts", "java").exists(path.ext.toLowerCase() == _)
    } yield PathRef(path)
  }

  /**
   * All individual Java source files fed into the compiler.
   * Subset of [[allSourceFiles]].
   */
  def allJavaSourceFiles = T {
    allSourceFiles().filter(_.path.ext.toLowerCase() == "java")
  }

  /**
   * All individual Kotlin source files fed into the compiler.
   * Subset of [[allSourceFiles]].
   */
  def allKotlinSourceFiles = T {
    allSourceFiles().filter(path => Seq("kt", "kts").exists(path.path.ext.toLowerCase() == _))
  }

  /**
   * The Kotlin version to be used (for API and Language level settings).
   */
  def kotlinVersion: T[String]

  /**
   * The version of the Kotlin compiler to be used.
   * Default is derived from [[kotlinVersion]].
   */
  def kotlinCompilerVersion: T[String] = T {
    Versions.kotlinCompilerVersion
  }

  /**
   * The Ivy/Coursier dependencies resembling the Kotlin compiler.
   * Default is derived from [[kotlinCompilerVersion]].
   */
  def kotlinCompilerIvyDeps: T[Agg[Dep]] = T{ Agg(
    ivy"org.jetbrains.kotlin:kotlin-compiler:${kotlinCompilerVersion()}",
    ivy"org.jetbrains.kotlin:kotlin-scripting-compiler:${kotlinCompilerVersion()}",
//    ivy"org.jetbrains.kotlin:kotlin-scripting-compiler-impl:${kotlinCompilerVersion()}",
//    ivy"org.jetbrains.kotlin:kotlin-scripting-common:${kotlinCompilerVersion()}",
    ivy"${Versions.millKotlinWorkerImplIvyDep}"
  )}


  /**
   * The Java classpath resembling the Kotlin compiler.
   * Default is derived from [[kotlinCompilerIvyDeps]].
   */
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

  /**
   * Compiles all the sources to JVM class files.
   */
  override def compile: T[CompilationResult] = T {
    kotlinCompileTask()()
  }

  /**
   * Runs the Kotlin compiler with the `-help` argument to show you the built-in cmdline help.
   * You might want to add additional arguments like `-X` to see extra help.
   */
  def kotlincHelp(args: String*): Command[Unit] = T.command {
    kotlinCompileTask(Seq("-help") ++ args)()
    ()
  }

  /**
   * The actual Kotlin compile task (used by [[compile]] and [[kotlincHelp()]].
   */
  protected def kotlinCompileTask(extraKotlinArgs: Seq[String] = Seq()): Task[CompilationResult] = T.task {
    val ctx = T.ctx()
    val dest = ctx.dest
    val classes = dest / "classes"
    os.makeDir.all(classes)

    val isKotlin = !allKotlinSourceFiles().isEmpty
    val isJava = !allJavaSourceFiles().isEmpty
    val isMixed = isKotlin && isJava

    val counts = Seq("Kotlin" -> allKotlinSourceFiles().size, "Java" -> allJavaSourceFiles().size)
    ctx.log.info(s"Compiling ${counts.filter(_._2 > 0).map{ case (n,c) => s"$c $n" }.mkString(" and ")} sources to ${classes} ...")

    def compileJava = {
      zincWorker.worker().compileJava(
        upstreamCompileOutput(),
        allJavaSourceFiles().map(_.path),
        compileClasspath().map(_.path),
        javacOptions(),
        ctx.reporter(hashCode)
      )
    }

    if(isMixed || isKotlin) {
      val compilerArgs: Seq[String] = Seq(
        // destdir
        Seq("-d", classes.toIO.getAbsolutePath()),
        // classpath
        if (compileClasspath().isEmpty) Seq() else Seq(
          "-classpath",
          compileClasspath().toSeq
            .map(_.path.toIO.getAbsolutePath())
            .mkString(File.pathSeparator)
        ),
        Seq("-api-version", kotlinVersion().split("[.]", 3).take(2).mkString(".")),
        Seq("-language-version", kotlinVersion().split("[.]", 3).take(2).mkString(".")),
        kotlincOptions(),
        extraKotlinArgs,
        // parameters
        allSourceFiles().map(_.path.toIO.getAbsolutePath())
      ).flatten

      val workerResult = kotlinWorker().compile(compilerArgs: _*)

      val analysisFile = dest / "kotlin.analysis.dummy"
      os.write(target = analysisFile, data = "", createFolders = true)

      workerResult match {
        case Result.Success(value) =>
          val cr = CompilationResult(analysisFile, PathRef(classes))
          if (!isJava) {
            // pure Kotlin project
            cr
          }
          else {
            // also run Java compiler
            compileJava
          }
        case Result.Failure(reason, value) => Result.Failure(reason, Some(CompilationResult(analysisFile, PathRef(classes))))
        case e: Result.Exception => e
        case Result.Aborted => Result.Aborted
        case Result.Skipped => Result.Skipped
        //      case x => x
      }
    } else {
      // it's Java only
      compileJava
    }
  }

  /**
   * Additional Kotlin compiler options to be use by [[compile]].
   */
  def kotlincOptions: T[Seq[String]] = T { Seq.empty[String] }

  /**
   * A test sub-module linked to its parent module best suited for unit-tests.
   */
  trait Tests extends super.Tests with KotlinTestModule {
    override def kotlinVersion: T[String] = T{ outer.kotlinVersion() }
    override def kotlinCompilerVersion: T[String] = T{ outer.kotlinCompilerVersion }
    override def kotlincOptions: T[Seq[String]] = T{ super.kotlincOptions }
  }

}

/**
 * A [[TestModule]] with support for the Kotlin compiler.
 * @see [[KotlinModule]] for details.
 */
trait KotlinTestModule extends TestModule with KotlinModule {
}
