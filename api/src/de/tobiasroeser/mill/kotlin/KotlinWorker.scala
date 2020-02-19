package de.tobiasroeser.mill.kotlin

import mill.api.{Ctx, Result}

trait KotlinWorker {

  def compile(
    classpath: Seq[os.Path],
    outDir: os.Path,
    sourceDirs: Seq[os.Path],
    kotlinVersion: Option[String]
  )(implicit ctx: Ctx): Result[Unit]

}
