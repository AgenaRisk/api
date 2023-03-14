package com.agenarisk.api.tools;

import com.agenarisk.api.tools.sensitivity.SensitivityException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author Eugene Dementiev
 */
public class Utils {
	private static String readFile(Path path){
		String fileContents;
		try {
			fileContents = new String(Files.readAllBytes(path)).trim();
		}
		catch(IOException ex){
			throw new SensitivityException("Failed to read file: " + path, ex);
		}
		return fileContents;
	}
	
	/**
	 * Reads contents of the path as JSONObject
	 * @param path path to read
	 * 
	 * @return JSON object if read successfully
	 */
	public static JSONObject readJsonObject(Path path){
		return new JSONObject(readFile(path));
	}
	
	/**
	 * Reads contents of the path as JSONArray
	 * @param path path to read
	 * 
	 * @return JSON array if read successfully
	 */
	public static JSONArray readJsonArray(Path path){
		return new JSONArray(readFile(path));
	}
	
	/**
	 * Strips out optional double quotes enclosing this path
	 * 
	 * @param path
	 * @return 
	 */
	public static Path resolve(String path) {
		return Paths.get(path.replaceFirst("^[\"'](.*)[\"']$", "$1"));
	}
}
