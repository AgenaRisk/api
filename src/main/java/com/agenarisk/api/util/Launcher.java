package com.agenarisk.api.util;

import java.io.File;
import java.util.Arrays;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import uk.co.agena.minerva.util.Config;
import uk.co.agena.minerva.util.Logger;
import uk.co.agena.minerva.util.helpers.License;

/**
 *
 * @author Eugene Dementiev
 */
public class Launcher {
	
	private static final CommandLineParser PARSER = new DefaultParser();
	private static CommandLine cmd;
	private static final Options OPTIONS = new Options();
	
	static {
		// Keep these options in line with uk.co.agena.minerva.util.Launcher and Config
		OPTIONS.addOption(new Option("h", "help", false, "print this message"));
		OPTIONS.addOption(new Option("v", "version", false, "print version"));
		OPTIONS.addOption(new Option(null, "paths", false, "print important paths"));
		
		OptionGroup og1 = new OptionGroup();
		og1.addOption(Option.builder().longOpt("keyActivate").hasArg().argName("key").desc("activate with license key").build());
		og1.addOption(new Option(null, "keyDeactivate", false, "deactivate the license"));
		og1.addOption(Option.builder().longOpt("floatingLease").hasArgs().argName("address:port").valueSeparator(':').numberOfArgs(2).desc("add floating license server settings").build());
		og1.addOption(new Option(null, "floatingRelease", false, "remove floating license server settings"));
		OPTIONS.addOptionGroup(og1);
		
		Logger.getOptions().getOptions().stream().forEach(option -> OPTIONS.addOption(option));
		Config.getOptions().getOptions().stream().forEach(option -> OPTIONS.addOption(option));
		
	}
	
	public static void main(String[] args) {
		
		if (args.length > 0){
			Config.init(args);
			Logger.init(args);
		}
		
		try {
			cmd = PARSER.parse(OPTIONS, args);
		}
		catch (ParseException ex){
			Logger.err().println(ex.getMessage());
		}
		
		if (cmd.hasOption("h")){
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("AgenaRisk Java API v" + VersionApi.getVersionText(), OPTIONS);
			Logger.out().println("");
			Logger.out().println("NOTE");
			Logger.out().println("\toverride arguments will be applied first");
			Logger.out().println("\tOnly one licensing operation can be performed at a time");
			System.exit(0);
		}
		
		if (cmd.hasOption("v")){
			Logger.out().println("AgenaRisk Java API v" + VersionApi.getVersionText());
			System.exit(0);
		}
		
		if (cmd.hasOption("paths")){
			Logger.out().println("Important AgenaRisk paths:");
			Logger.out().println("Application working directory: " + Config.getDirectoryWorking());
			Logger.out().println("AgenaRisk home directory: " + Config.getDirectoryHomeAgenaRisk());
			Logger.out().println("System temp directory: " + Config.getDirectoryTempSystem());
			Logger.out().println("AgenaRisk temp directory: " + Config.getDirectoryTempAgenaRisk());
			Logger.out().println("AgenaRisk config file: " + Config.getFilepathMinervaProperties());
			Logger.out().println("AgenaRisk product directory: " + Config.getDirectoryAgenaRiskProduct());
			Logger.out().println("AgenaRisk native libs directory: " + Config.getDirectoryNativeLibs());
		}
		
		if (cmd.hasOption("keyActivate")){
			String key = cmd.getOptionValue("keyActivate");
			License.keyActivate(key);
		}
		
		if (cmd.hasOption("keyDeactivate")){
			License.keyRelease();
		}

		if (cmd.hasOption("floatingLease")){
			String[] params = cmd.getOptionValues("floatingLease");
			License.floatingLicenseLease(params[0], params[1]);
		}
		
		if (cmd.hasOption("floatingRelease")){
			License.floatingLicenseRelease();
		}
	}
}
