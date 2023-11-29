package de.tobiasroeser.mill.kotlin

import mill.api.{Ctx, PathRef}

trait KotlinWorkerManager {
  def get(toolsClasspath: Seq[PathRef])(implicit ctx: Ctx): KotlinWorker
}
