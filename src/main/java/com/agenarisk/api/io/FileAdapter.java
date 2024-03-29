package com.agenarisk.api.io;

import com.agenarisk.api.exception.AdapterException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * FileAdapter is a helper class for AgenaRisk API to abstract file I/O from other components.
 * 
 * @author Eugene Dementiev
 */
public class FileAdapter {

	/**
	 * Reads the content of the file at the provided path.It checks whether the content begins as JSON or XML and will try to read it as such.
	 * 
	 * @param filePath file path from which to attempt loading the file
	 * 
	 * @return the JSON representation of the resource
	 * 
	 * @throws AdapterException when failed to open file or JSON/XML is malformed
	 */
	public static JSONObject extractJSONObject(String filePath) throws AdapterException {
		String fileContents = getFileContents(filePath);
		
		JSONObject json = null;
		
		if (fileContents.startsWith("{")){
			json = new JSONObject(fileContents);
		}
		else if (fileContents.startsWith("<")){
			json = XMLAdapter.xmlToJson(fileContents);
		}
		
		return json;
	}
	
	/**
	 * Reads the content of the file at the provided path as JSONArray
	 * 
	 * @param filePath file path from which to attempt loading the file
	 * 
	 * @return the JSONArray representation of the resource
	 * 
	 * @throws AdapterException when failed to open file or JSONArray is malformed
	 */
	public static JSONArray extractJSONArray(String filePath) throws AdapterException {
		String fileContents = getFileContents(filePath);
		try {
			return new JSONArray(fileContents);
		}
		catch(Exception ex){
			throw new AdapterException("Failed to read resource as JSON array", ex);
		}
	}
	
	/**
	 * Reads the contents of the file at the provided path.
	 * 
	 * @param filePath file path from which to attempt loading the file
	 * 
	 * @return the contents as string
	 * 
	 * @throws AdapterException when failed to open file
	 */
	private static String getFileContents(String filePath) throws AdapterException {
		// If file path is wrapped in quotes, remove them
		filePath = filePath.replaceFirst("^[\"'](.*)[\"']$", "$1");
		
		String fileContents;
		try {
			fileContents = new String(Files.readAllBytes(Paths.get(filePath))).trim();
		}
		catch(IOException ex){
			throw new AdapterException("Failed to read file", ex);
		}
		return fileContents;
	}
	
}
