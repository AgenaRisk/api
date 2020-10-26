package com.agenarisk.test;

import com.agenarisk.api.io.XMLAdapter;
import com.agenarisk.api.model.Model;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.XML;
import org.junit.jupiter.api.io.TempDir;

import uk.co.agena.minerva.util.Config;
import uk.co.agena.minerva.util.Environment;

/**
 *
 * @author Eugene Dementiev
 */
public class TestHelper {
	
	static {
		Environment.initialize();
		try {
			tempDir = Files.createTempDirectory("agenarisk_test");
		}
		catch (IOException ex){
			throw new RuntimeException("Failed to create temp directory for test files", ex);
		}
	}
	
    static Path tempDir;
	
	public static Path tempFileCopyOfResource(String resourcePath) {
		try (InputStream is = TestHelper.class.getResourceAsStream(resourcePath)) {
			Path tempFilePath = Files.createTempFile(Paths.get(Config.getDirectoryTempAgenaRisk()),"agenarisk-test-",Arrays.stream(resourcePath.split("\\.")).reduce((a, b) -> "." + b).orElse(null));
			Files.copy(is, tempFilePath, StandardCopyOption.REPLACE_EXISTING);
			tempFilePath.toFile().deleteOnExit();
			return tempFilePath;

		}
		catch (Exception ex){
			throw new RuntimeException("Failed to copy resource file: " + resourcePath, ex);
		}
	}
	
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
					System.out.println(tempDir);
					System.out.println(inFileName);
					System.out.println("---");
					String pathNewIn = tempDir.resolve(inFileName).toAbsolutePath().toString();
					String pathNewOut = tempDir.resolve(outFileName).toAbsolutePath().toString();
					
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
	
	public static String readResourceContent(String resourcePath){
		String content = "";
		
		try (InputStream is = TestHelper.class.getResourceAsStream(resourcePath); BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
			content = br.lines().collect(Collectors.joining("\n"));
		}
		catch (Exception ex){
			throw new RuntimeException("Failed to read from resource `"+resourcePath+"`", ex);
		}
		return content;
	}
	
	public static Model loadModelFromResource(String resourcePath) {
		
		String content = readResourceContent(resourcePath);
		
		JSONObject json = null;
		
		try {
			json = new JSONObject(content);
		}
		catch (JSONException ex){
			// Failed to parse as JSON, let's try to convert to XML
			try {
				json = XML.toJSONObject(content);
				Method method = XMLAdapter.class.getDeclaredMethod("convertXmlJson", Class.forName("java.lang.Object"));
				method.setAccessible(true);
				method.invoke(null, json);
			}
			catch (Exception ex2){
				json = null;
				throw new RuntimeException("Failed to convert XML in `"+resourcePath+"`", ex2);
			}
		}
		
		if (json == null){
			throw new RuntimeException("Failed to extract JSON from `"+resourcePath+"`");
		}
		
		try {
			return Model.createModel(json);
		}
		catch(Exception ex){
			throw new RuntimeException("Failed to create model from `"+resourcePath+"`", ex);
		}
		
	}
}
