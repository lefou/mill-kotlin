package de.tobiasroeser.mill.kotlin.impl;

import java.io.File;
import java.util.List;

import de.tobiasroeser.mill.kotlin.Log;
import de.tototec.utils.functional.FList;
import de.tobiasroeser.mill.kotlin.KotlinWorker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.cli.common.ExitCode;
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments;
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation;
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity;
import org.jetbrains.kotlin.cli.common.messages.MessageCollector;
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler;
import org.jetbrains.kotlin.config.Services;

public class KotlinWorkerImpl implements KotlinWorker {

	public KotlinWorkerImpl() {
	}

	@Override
	public void compile(
		final List<File> classpath,
		final File outDir,
		final List<File> sourceDirs,
		final Log log
	) {
		final K2JVMCompiler compiler = new K2JVMCompiler();

		K2JVMCompilerArguments arguments = new K2JVMCompilerArguments();
		arguments.setClasspath(FList.mkString(FList.map(classpath, c -> c.getAbsolutePath()), File.pathSeparator));

		arguments.setDestination(outDir.getAbsolutePath());

		if(sourceDirs.isEmpty()) {
			throw new RuntimeException("No sources");
		}

		arguments.setFreeArgs(FList.map(sourceDirs, s -> s.getAbsolutePath()));

		log.debug("Using compiler arguments: " + arguments);

		MessageCollector messageCollector = new MessageCollector() {
			private long errors = 0;
			private long warnings = 0;

			@Override
			public void clear() {
				//
			}

			@Override
			public void report(@NotNull CompilerMessageSeverity compilerMessageSeverity, @NotNull String s, @Nullable CompilerMessageLocation compilerMessageLocation) {
				final String prefix;
				if(compilerMessageSeverity.isError()) {
					prefix = "error: ";
				} else if(compilerMessageSeverity.isWarning()){
					prefix = "warning: ";
				} else {
					prefix = "";
				}
				final String location;
				if(compilerMessageLocation != null) {
					location = compilerMessageLocation.toString() + ": ";
				} else {
					location = "";
				}
				System.out.println(prefix + location + s);
				log.error(prefix + location + s);
			}

			@Override
			public boolean hasErrors() {
				return errors > 0;
			}

		};
		final ExitCode exitCode = compiler.exec(messageCollector, Services.EMPTY, arguments);

	}


}
