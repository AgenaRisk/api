package com.agenarisk.api.tools.calculator;

import com.agenarisk.api.util.*;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import uk.co.agena.minerva.util.Config;
import uk.co.agena.minerva.util.Logger;
import uk.co.agena.minerva.util.VersionCore;

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
		
		OPTIONS.addOption(Option.builder().longOpt("model").hasArg().argName("path").desc("path to model file").build());
		OPTIONS.addOption(Option.builder().longOpt("data").hasArg().argName("path").desc("path to data file").build());
		OPTIONS.addOption(Option.builder().longOpt("out").hasArg().argName("path").desc("path to results file").build());
		OPTIONS.addOption(Option.builder().longOpt("use-cache").desc("Skip data recalculation if found in results file [default: false]").build());
		
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
			formatter.printHelp("agena.ai Java API v" + VersionApi.getVersionText(), OPTIONS);
			Logger.out().println("");
			Logger.out().println("NOTE");
			Logger.out().println("\toverride arguments will be applied first");
			Logger.out().println("\tOnly one licensing operation can be performed at a time");
			System.exit(0);
		}
		
		if (cmd.hasOption("v")){
			Logger.out().println("agena.ai Java API v" + VersionApi.getVersionText());
			Logger.out().println("agena.ai Core v" + VersionCore.getVersionText());
			System.exit(0);
		}
		
		if (cmd.hasOption("paths")){
			Logger.out().println("Important paths:");
			Logger.out().println("Application working directory: " + Config.getDirectoryWorking());
			Logger.out().println("Application ome directory: " + Config.getDirectoryHomeAgenaRisk());
			Logger.out().println("System temp directory: " + Config.getDirectoryTempSystem());
			Logger.out().println("Application temp directory: " + Config.getDirectoryTempAgenaRisk());
			Logger.out().println("Application config file: " + Config.getFilepathMinervaProperties());
			Logger.out().println("Application product directory: " + Config.getDirectoryAgenaRiskProduct());
			Logger.out().println("Application native libs directory: " + Config.getDirectoryNativeLibs());
		}
		
		if (!cmd.hasOption("model") || !cmd.hasOption("out") || !cmd.hasOption("data")){
			Logger.err().println("Parameters model, out and data are required");
			System.exit(1);
		}
		
		try {
		new Calculator()
				.withModel(cmd.getOptionValue("model"))
				.withData(cmd.getOptionValue("data"))
				.savingTo(cmd.getOptionValue("out"))
				.usingCache(cmd.hasOption("use-cache"))
				.execute();
		}
		catch(Exception ex){
			Logger.err().println("Failed: " + ex.getMessage());
			Logger.printThrowableIfDebug(ex, Logger.err(), 5);
			System.exit(2);
		}
		
	}
}
