package de.tobiasroeser.mill.kotlin

import mill.{Agg, T}
import mill.api.PathRef
import mill.scalalib.{CoursierModule, Dep}

trait KotlinModulePlatform extends CoursierModule {

  def kotlinCompilerIvyDeps: T[Agg[Dep]]

  /**
   * The Java classpath resembling the Kotlin compiler.
   * Default is derived from [[kotlinCompilerIvyDeps]].
   */
  def kotlinCompilerClasspath: T[Seq[PathRef]] = T {
    resolveDeps(T.task { kotlinCompilerIvyDeps().map(bindDependency()) })().toSeq
  }

}
