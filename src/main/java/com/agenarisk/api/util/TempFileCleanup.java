package com.agenarisk.api.util;

import com.agenarisk.learning.structure.config.Config;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import uk.co.agena.minerva.util.Logger;

/**
 *
 * @author Eugene Dementiev
 */
public class TempFileCleanup {
    // Thread-safe map to store temporary files per Config
    private static final Map<Config, Set<File>> tempFilesMap = new ConcurrentHashMap<>();

    static {
        // Register a shutdown hook to clean up remaining files
        Runtime.getRuntime().addShutdownHook(new Thread(TempFileCleanup::cleanupAll));
    }

    /**
     * Registers a temporary file for cleanup under a specific config.
     *
     * @param file   the file to be registered for deletion
     * @param config the associated configuration
     */
    public static void registerTempFile(File file, Config config) {
        if (file != null && config != null) {
            tempFilesMap.computeIfAbsent(config, k -> ConcurrentHashMap.newKeySet()).add(file);
        }
    }

    /**
     * Deletes all registered temporary files for the given config and clears the registry for that config.
     *
     * @param config the associated configuration
     */
    public static void cleanup(Config config) {
        if (config != null) {
            Set<File> files = tempFilesMap.remove(config);
            if (files != null) {
                for (File file : files) {
                    try {
						Files.deleteIfExists(file.toPath());
					}
					catch (IOException ex){
						Logger.printThrowableIfDebug(ex, 8);
					}
                }
            }
        }
    }

    /**
     * Deletes all remaining registered temporary files and clears the entire registry.
     */
    private static void cleanupAll() {
        for (Set<File> files : tempFilesMap.values()) {
            for (File file : files) {
                try {
					Files.deleteIfExists(file.toPath());
				}
				catch (IOException ex){
					Logger.printThrowableIfDebug(ex, 8);
				}
            }
        }
        tempFilesMap.clear(); // Clear all entries after attempting deletions
    }
}
