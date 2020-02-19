package de.tobiasroeser.mill.kotlin.impl

import java.io.File
import java.util.List

import de.tobiasroeser.mill.kotlin.KotlinWorker
import mill.api.Ctx
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
  )(implicit ctx: Ctx): Unit = {

    val compiler = new K2JVMCompiler()

    val arguments = new K2JVMCompilerArguments()
    arguments.setClasspath(classpath.map(_.toIO.getAbsolutePath()).mkString(File.pathSeparator))

    arguments.setDestination(outDir.toIO.getAbsolutePath());

    if (sourceDirs.isEmpty) {
      throw new RuntimeException("No sources")
    }

    arguments.setFreeArgs(sourceDirs.map(_.toIO.getAbsolutePath()).asJava)

    kotlinVersion.foreach{v =>
      val version = v.split("[.]", 3).take(2).mkString(".")
      arguments.setApiVersion(version)

    }

    ctx.log.debug("Using compiler arguments: " + arguments)

    val messageCollector = new MessageCollector() {
      private var errors = 0L
      private var warnings = 0L

      override def clear(): Unit = {
        errors = 0L
        warnings = 0L
      }

      override def report(compilerMessageSeverity: CompilerMessageSeverity, s: String, compilerMessageLocation: CompilerMessageLocation): Unit = {
        val prefix =
          if (compilerMessageSeverity.isError()) {
            "error: "
          } else if (compilerMessageSeverity.isWarning()) {
            "warning: "
          } else {
            ""
          }
        val location =
          if (compilerMessageLocation != null) {
            compilerMessageLocation.toString() + ": "
          } else {
            ""
          }

        ctx.log.error(prefix + location + s);
      }
      override def hasErrors: Boolean = errors > 0;
    }

    val exitCode = compiler.exec(messageCollector, Services.EMPTY, arguments)

    

  }

}
