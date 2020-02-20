package de.tobiasroeser.mill.kotlin.impl

import java.io.File
import java.util.List

import de.tobiasroeser.mill.kotlin.KotlinWorker
import mill.api.{Ctx, Result}
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import scala.collection.JavaConverters._

class KotlinWorkerImpl extends KotlinWorker {

  def compile(args: String*)(implicit ctx: Ctx): Result[Unit] = {
    ctx.log.debug("Using compiler arguments: " + args.map(v => s"'${v}'").mkString(" "))

    val compiler = new K2JVMCompiler()
    val exitCode = compiler.exec(ctx.log.errorStream, args: _*)
    if(exitCode.getCode() != 0) {
      Result.Failure(s"Kotlin compiler failed with exit code ${exitCode.getCode()} (${exitCode})")
    } else {
      Result.Success()
    }
  }



}
