package de.tobiasroeser.mill.kotlin

import mill.{Agg, T}
import mill.api.PathRef
import mill.scalalib.{Dep, JavaModule}

trait KotlinModulePlatform extends JavaModule {

  def kotlinCompilerIvyDeps: T[Agg[Dep]]

  /**
   * The Java classpath resembling the Kotlin compiler.
   * Default is derived from [[kotlinCompilerIvyDeps]].
   */
  def kotlinCompilerClasspath: T[Seq[PathRef]] = T {
    resolveDeps(kotlinCompilerIvyDeps)().toSeq
  }

}
