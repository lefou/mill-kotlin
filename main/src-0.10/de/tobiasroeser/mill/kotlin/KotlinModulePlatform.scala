package de.tobiasroeser.mill.kotlin

import mill.{Agg, T}
import mill.api.{PathRef, Result}
import mill.define.Task
import mill.scalalib.{Dep, JavaModule, ZincWorkerModule}
import mill.scalalib.api.{CompilationResult, ZincWorkerApi}

trait KotlinModulePlatform extends JavaModule {

  type CompileProblemReporter = mill.api.CompileProblemReporter

  // compatibility with Mill 0.11
  protected type ModuleRef[T] = Function0[T]
  protected def zincWorkerRef: ModuleRef[ZincWorkerModule] = () => zincWorker

  def kotlinCompilerIvyDeps: T[Agg[Dep]]

  /**
   * The Java classpath resembling the Kotlin compiler.
   * Default is derived from [[kotlinCompilerIvyDeps]].
   */
  def kotlinCompilerClasspath: T[Seq[PathRef]] = T {
    resolveDeps(kotlinCompilerIvyDeps)().toSeq
  }

  private[kotlin] def internalCompileJavaFiles(
      worker: ZincWorkerApi,
      upstreamCompileOutput: Seq[CompilationResult],
      javaSourceFiles: Seq[os.Path],
      compileCp: Agg[os.Path],
      javacOptions: Seq[String],
      compileProblemReporter: Option[CompileProblemReporter],
      reportOldProblems: Boolean
  )(implicit ctx: ZincWorkerApi.Ctx): Result[CompilationResult] = {
    worker.compileJava(
      upstreamCompileOutput = upstreamCompileOutput,
      sources = javaSourceFiles,
      compileClasspath = compileCp,
      javacOptions = javacOptions,
      reporter = compileProblemReporter
    )
  }

  private[kotlin] def internalReportOldProblems: Task[Boolean] = T.task(false)

}
