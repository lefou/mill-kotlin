package de.tobiasroeser.mill.kotlin

import mill.api.{Ctx, Result}

trait KotlinWorker {

  def compile(args: String*)(implicit ctx: Ctx): Result[Unit]

}
