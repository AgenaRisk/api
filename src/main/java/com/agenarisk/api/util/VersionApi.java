package com.agenarisk.api.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import uk.co.agena.minerva.util.Logger;

/**
 * This class provides means to retrieve the version of this build from manifest or .properties
 * 
 * @author Eugene Dementiev
 */
public class VersionApi {
	private static final String VERSION_TEXT;
	
	static {
		VERSION_TEXT = retrieveVersion();
	}
	
	private static String retrieveVersion(){
		// Attempt to retrieve version from manifest
		String revString = VersionApi.class.getPackage().getImplementationVersion();
		
		if (revString == null){
			Logger.logIfDebug("Failed to read API version from manifest");
			
			// Attempt to retrieve version from properties
			try (InputStream is = VersionApi.class.getResourceAsStream("/api.properties"); BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
				revString = br.readLine();
			}
			catch (NullPointerException | IOException ex){
				Logger.logIfDebug("Failed to read API version from desktop.properties: " + ex.getMessage());
			}
		}
		return revString;
	}
	
	public static String getVersionText(){
		return VERSION_TEXT;
	}
}
