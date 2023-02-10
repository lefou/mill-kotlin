package de.tobiasroeser.mill.kotlin

import mill.{Agg, T}
import mill.api.{CompileProblemReporter, PathRef, Result}
import mill.define.Task
import mill.scalalib.api.{CompilationResult, ZincWorkerApi}
import mill.scalalib.{Dep, JavaModule}

trait KotlinModulePlatform extends JavaModule {

  def kotlinCompilerIvyDeps: T[Agg[Dep]]

  /**
   * The Java classpath resembling the Kotlin compiler.
   * Default is derived from [[kotlinCompilerIvyDeps]].
   */
  def kotlinCompilerClasspath: T[Seq[PathRef]] = T {
    resolveDeps(T.task { kotlinCompilerIvyDeps().map(bindDependency()) })().toSeq
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
      reporter = compileProblemReporter,
      reportCachedProblems = reportOldProblems
    )
  }

  private[kotlin] def internalReportOldProblems: Task[Boolean] = zincReportCachedProblems

}
