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

  override def compile(
    classpath: Seq[os.Path],
    outDir: os.Path,
    sourceDirs: Seq[os.Path],
    kotlinVersion: Option[String]
  )(implicit ctx: Ctx): Result[Unit] = {

    val compiler = new K2JVMCompiler()

    classOf[K2JVMCompilerArguments]

    val compilerArgs: Seq[String] = Seq(
      Seq("-d", outDir.toIO.getAbsolutePath()),
      addNonEmpty[os.Path]("-classpath", classpath, _.toIO.getAbsolutePath()),
      addNonEmpty[String]("-api-version", kotlinVersion.toSeq,   _.split("[.]", 3).take(2).mkString(".")),
      // parameters
      sourceDirs.map(_.toIO.getAbsolutePath())
    ).flatten

    ctx.log.debug("Using compiler arguments: " + compilerArgs.map(v => s"'${v}'").mkString(" "))


    val exitCode = compiler.exec(ctx.log.errorStream, compilerArgs.toArray[String]: _*)
    if(exitCode.getCode() != 0) {
      Result.Failure(s"Kotlin compiler failed with exit code ${exitCode}")
    } else {
      Result.Success()
    }

  }

  def addNonEmpty[T](arg: String, seq: Seq[T], render: T => String, sep: String = File.pathSeparator): Seq[String] = {
    if (seq.isEmpty) {
      Seq()
    } else {
      Seq(arg, seq.map(render).mkString(sep))
    }
  }

}
