package com.agenarisk.api.util;

import java.io.File;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import uk.co.agena.minerva.util.Config;
import uk.co.agena.minerva.util.Logger;

/**
 *
 * @author Eugene Dementiev
 */
public class Launcher {
	
	private static CommandLineParser parser = new DefaultParser();
	private static CommandLine cmd;
	private static final Options options = new Options();
	
	private static final String FS = System.getProperty("file.separator");
	
	static {
		options.addOption(new Option("h", "help", false, "print this message"));
		options.addOption(new Option("v", "version", false, "print version"));
		options.addOption(new Option(null, "paths", false, "print important paths"));
		options.addOption(new Option("a", "activate", true, "activate with license key"));
		options.addOption(new Option("d", "deactivate", false, "deactivate the license"));
	}
	
	public static void main(String[] args) {
		
		args = new String[]{
			"-h"
		};
		
		try {
			cmd = parser.parse(options, args);
		}
		catch (ParseException ex){
			Logger.err().println(ex.getMessage());
		}
		
		if (cmd.hasOption("h")){
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("AgenaRisk Java API v" + VersionApi.getVersionText(), options);
			System.exit(0);
		}
		
		if (cmd.hasOption("v")){
			Logger.out().println("AgenaRisk Java API v" + VersionApi.getVersionText());
			System.exit(0);
		}
		
		if (cmd.hasOption("paths")){
			Logger.out().println("Application working directory: " + Config.getDirectoryWorking());
			Logger.out().println("AgenaRisk home directory: " + Config.getDirectoryHomeAgenaRisk());
			Logger.out().println("AgenaRisk temp directory: " + Config.getDirectoryTempAgenaRisk());
			Logger.out().println("AgenaRisk config file: " + Config.getFilepathMinervaProperties());
		}
		
		if (cmd.hasOption("a")){
			String key = "";
		}
	}
}
