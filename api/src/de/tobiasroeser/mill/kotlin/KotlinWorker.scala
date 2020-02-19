package de.tobiasroeser.mill.kotlin

import mill.api.{Ctx, Result}

trait KotlinWorker {

  def compile(
    classpath: Seq[os.Path],
    outDir: os.Path,
    sources: Seq[os.Path],
    apiVersion: Option[String],
    languageVersion: Option[String],
    kotlincOptions: Seq[String],
//    javacOptions: Seq[String]
  )(implicit ctx: Ctx): Result[Unit]

}
