package de.tobiasroeser.mill.kotlin.impl

import de.tobiasroeser.mill.kotlin.KotlinWorker
import mill.api.{Ctx, Result}
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler

class KotlinWorkerImpl extends KotlinWorker {

  def compile(args: String*)(implicit ctx: Ctx): Result[Unit] = {
    ctx.log.debug("Using kotlin compiler arguments: " + args.map(v => s"'${v}'").mkString(" "))

    val compiler = new K2JVMCompiler()
    val exitCode = compiler.exec(ctx.log.errorStream, args: _*)
    if (exitCode.getCode() != 0) {
      Result.Failure(s"Kotlin compiler failed with exit code ${exitCode.getCode()} (${exitCode})")
    } else {
      Result.Success(())
    }
  }

}
