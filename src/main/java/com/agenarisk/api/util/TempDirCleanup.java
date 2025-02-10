package com.agenarisk.api.util;

/**
 *
 * @author Eugene Dementiev
 */
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import uk.co.agena.minerva.util.Logger;

public class TempDirCleanup {

	private static final Set<Path> tempDirs = Collections.synchronizedSet(new HashSet<>());

	static {
		// Register a shutdown hook to clean up all tracked directories
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			for (Path dir : tempDirs) {
				try {
					deleteRecursively(dir);
					Logger.logIfDebug("Deleted temporary directory: " + dir, 6);

				}
				catch (IOException e) {
					Logger.printThrowableIfDebug(e, 6);
				}
			}
		}));
	}

	/**
	 * Adds a directory to the collection for cleanup on shutdown if it is within the system's temp directory.
	 *
	 * @param dir the directory to be cleaned up
	 */
	public static void registerTempDirectory(Path dir) {
		if (Files.exists(dir) && Files.isDirectory(dir) && isTemporaryDirectory(dir)) {
			tempDirs.add(dir);
		}
	}

	/**
	 * Checks if a given directory is within the system's temp directory.
	 *
	 * @param dir Path of the directory
	 * @return true if it is a temporary directory
	 */
	private static boolean isTemporaryDirectory(Path dir) {
		try {
			Path tempRoot = Paths.get(System.getProperty("java.io.tmpdir")).toRealPath();
			Path realPath = dir.toRealPath();
			return realPath.startsWith(tempRoot);
		}
		catch (IOException e) {
			return false; // Unable to verify, assume it's not temporary
		}
	}

	/**
	 * Recursively deletes a directory and its contents.
	 *
	 * @param path the path to delete
	 * @throws IOException if an I/O error occurs
	 */
	private static void deleteRecursively(Path path) throws IOException {
		if (Files.exists(path)) {
			Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					Files.deleteIfExists(file);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
					Files.deleteIfExists(dir);
					return FileVisitResult.CONTINUE;
				}
			});
		}
	}
}
