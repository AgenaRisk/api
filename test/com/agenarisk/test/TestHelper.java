package com.agenarisk.test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import org.junit.rules.TemporaryFolder;

/**
 *
 * @author Eugene Dementiev
 */
public class TestHelper {
	
	private static TemporaryFolder TEMPORARY_FOLDER = new TemporaryFolder();
	
//	static {
//		try {
//			TEMPORARY_FOLDER.create();
//		}
//		catch (IOException ex){
//			throw new RuntimeException("Failed to create temp directory", ex);
//		}
//	}
	
	
	public static void copyInputOuputResources(Path packagePath, String extIn, String extOut, List<String> pathsIn, List<String> pathsOut) {
		
		try {
			File resourceDirectory = Paths.get(TestHelper.class.getProtectionDomain().getCodeSource().getLocation().toURI()).resolve(packagePath).toFile();
			
			Files.find(Paths.get(resourceDirectory.toString()), 1, (originalInputPath, attrs) -> {
				return attrs.isRegularFile() && originalInputPath.toString().toLowerCase().endsWith("."+extIn);
			}).forEach(originalInputPath -> {
				// Get input and corresponding output file names
				String inFileName = originalInputPath.getFileName().toString();
				Path originalOutputPath = originalInputPath.resolveSibling(inFileName.replaceFirst("(?i)\\."+extIn+"$", "."+extOut));
				String outFileName = originalOutputPath.getFileName().toString();

				// Copy to temporary files
				try {
					String pathNewIn = TEMPORARY_FOLDER.newFile(inFileName).getAbsoluteFile().toString();
					String pathNewOut = TEMPORARY_FOLDER.newFile(outFileName).getAbsoluteFile().toString();
					
					Files.copy(originalInputPath, Paths.get(pathNewIn), StandardCopyOption.REPLACE_EXISTING);
					Files.copy(originalOutputPath, Paths.get(pathNewOut), StandardCopyOption.REPLACE_EXISTING);
					
					pathsIn.add(pathNewIn);
					pathsOut.add(pathNewOut);
					
					new File(pathNewIn).getParentFile().deleteOnExit();
					new File(pathNewIn).deleteOnExit();
					new File(pathNewOut).deleteOnExit();
				}
				catch (IOException ex){
					throw new RuntimeException("Failed to copy test resources files", ex);
				}
			});
		}
		catch(IOException | URISyntaxException ex){
			throw new RuntimeException("Failed to load test files", ex);
		}
	}
	
	public static TemporaryFolder initTemporaryFolder(){
		TEMPORARY_FOLDER = new TemporaryFolder();
		return TEMPORARY_FOLDER;
	}
}
