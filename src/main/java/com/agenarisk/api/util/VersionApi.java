package com.agenarisk.api.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import uk.co.agena.minerva.util.Environment;

/**
 * This class provides means to retrieve the version of this build from manifest or .properties
 * 
 * @author Eugene Dementiev
 */
public class VersionApi {
	private static final int revision;
	private static final String versionText;
	
	static {
		versionText = retrieveVersion();
		revision = retrieveRevision(versionText);
	}
	
	private static String retrieveVersion(){
		// Attempt to retrieve version from manifest
		String revString = VersionApi.class.getPackage().getImplementationVersion();
		
		if (revString == null){
			Environment.logIfDebug("Failed to read API version from manifest");
			
			// Attempt to retrieve version from properties
			try (InputStream is = VersionApi.class.getResourceAsStream("/api.properties"); BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
				revString = br.readLine();
			}
			catch (NullPointerException | IOException ex){
				Environment.logIfDebug("Failed to read API version from desktop.properties: " + ex.getMessage());
			}
		}
		return revString;
	}
	
	private static Integer retrieveRevision(String version){
		
		Integer revParsed = null;
		
		if (version != null){
			try {
				revParsed = Integer.parseInt(version);
			}
			catch (NumberFormatException ex){
				Environment.logIfDebug("Failed to parse API version string: " + ex.getMessage());
			}
		}
		
		if (revParsed == null){
			return 0;
		}
		
		return revParsed;
	}
	
	public static int getRevision(){
		return revision;
	}
	
	public static String getVersionText(){
		return versionText;
	}
}
