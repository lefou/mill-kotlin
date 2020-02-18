package de.tobiasroeser.mill.kotlin;

import java.io.File;
import java.util.List;

public interface KotlinWorker {

	public void compile(
		List<File> classpath,
		File outDir,
		List<File> sourceDirs,
		Log log
	);

}
