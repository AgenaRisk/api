package com.agenarisk.api.io;

import com.agenarisk.api.exception.AdapterException;
import com.agenarisk.api.util.JSONUtils;
import com.agenarisk.test.TestHelper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Test;

/**
 * The purpose of this test is to make sure that JSON to XML arrives to the same 
 * 
 * @author Eugene Dementiev
 */
public class XMLAdapterTest {
	
	@Test
	public void testJsonToXmlFromFiles() throws IOException {
		// Get all json files
		List<String> pathsIn = new ArrayList<>();
		List<String> pathsOut = new ArrayList<>();
		
		TestHelper.copyInputOuputResources(Paths.get("com", "agenarisk", "api", "io", "FileAdapterTest"), "cmpx", "xml", pathsIn, pathsOut);
		
		assertTrue(!pathsIn.isEmpty());
		assertEquals(pathsIn.size(), pathsOut.size());
		
		for (int i = 0; i < pathsIn.size(); i++) {
			String input = new String(Files.readAllBytes(Paths.get(pathsIn.get(i))), "UTF-8").trim();
			String outputExpected = new String(Files.readAllBytes(Paths.get(pathsOut.get(i))), "UTF-8").trim();
			
			if (input.trim().isEmpty()){
				throw new RuntimeException("Test file reading failed");
			}
			
			/* org.json */
			JSONObject json = null;
			try {
				json = new JSONObject(input);
			}
			catch (JSONException ex){
				ex.printStackTrace();
				fail("Failed to parse Json string");
			}
			/* * */
			
			/* GSON */
//			JsonParser jparser = new JsonParser();
//			JsonElement json = jparser.parse(input).getAsJsonObject();
			/* * */
			
			String output = XMLAdapter.toXMLString(json);
			
//			System.out.println(pathsIn.get(i));
//			System.out.println("Input size: "+input.length());
//			System.out.println("Output expected size: "+outputExpected.length());
//			System.out.println("Output size: "+output.length());
			
//			Files.write(Paths.get("d:\\Dropbox\\Agena\\updates\\data format\\xml\\out"+i+".xml"), output.getBytes());
			
			assertEquals(outputExpected, output);
		}
	}
	
	@Test
	public void testXmlToJsonFromFiles() throws IOException {
		// Get all json files
		List<String> pathsIn = new ArrayList<>();
		List<String> pathsOut = new ArrayList<>();
		
		TestHelper.copyInputOuputResources(Paths.get("com", "agenarisk", "api", "io", "FileAdapterTest"), "xml", "cmpx", pathsIn, pathsOut);
		
		assertTrue(!pathsIn.isEmpty());
		assertEquals(pathsIn.size(), pathsOut.size());
		
		for (int i = 0; i < pathsIn.size(); i++) {
			String input = new String(Files.readAllBytes(Paths.get(pathsIn.get(i))), "UTF-8").trim();
			String outputExpected = new String(Files.readAllBytes(Paths.get(pathsOut.get(i))), "UTF-8").trim();
			
			if (input.trim().isEmpty()){
				throw new RuntimeException("Test file reading failed");
			}
			
			String output = "";
			try {
				JSONObject jsonOut = XMLAdapter.xmlToJson(input);
				output = jsonOut.toString();
			}
			catch (AdapterException ex){
				ex.printStackTrace();
				fail("Failed to parse Json string");
			}
			
			// Have to compare using a custom method because order is not guaranteed in org.jason and can't use it with a LinkedHashMap
			assertTrue(JSONUtils.equalsIgnoreCase(new JSONObject(outputExpected), new JSONObject(output)));
			
			// Very shoddy way of assrerting equality
			// When XML is parsed into a JSON, all values are treated as String, because JSON converter has no idea how to read them
			// Therefore we will remove all double quotes before comparing
			// We could implement our own XML to JSON converter that will expect to see TYPE attribute for each XML tag, but this is undesireable
//			outputExpected = outputExpected.replaceAll("\"", "");
//			output = output.replaceAll("\"", "");
////			Files.write(Paths.get("d:\\Dropbox\\Agena\\updates\\data format\\xml\\json"+i+"_expected.json"), outputExpected.getBytes());
////			Files.write(Paths.get("d:\\Dropbox\\Agena\\updates\\data format\\xml\\json"+i+"_actual.json"), output.getBytes());
//			assertEquals(outputExpected, output);
			
//			System.out.println(pathsIn.get(i));
//			System.out.println("Input size: "+input.length());
//			System.out.println("Output expected size: "+outputExpected.length());
//			System.out.println("Output size: "+output.length());
			
//			Files.write(Paths.get("d:\\work\\repos\\agenarisk\\sdk\\test\\com\\agenarisk\\api\\io\\out"+i+".xml"), output.getBytes());
		}
	}
}
