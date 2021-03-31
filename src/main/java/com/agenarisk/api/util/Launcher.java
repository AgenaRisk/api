package com.agenarisk.api.util;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import uk.co.agena.minerva.util.Config;
import uk.co.agena.minerva.util.Logger;
import uk.co.agena.minerva.util.product.License;

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
		
		og1.addOption(Option.builder().longOpt("keyActivate").hasArg(false).desc("activate with license key").build());
		og1.addOption(Option.builder().longOpt("keyDeactivate").hasArg(false).desc("deactivate the license").build());
		
		og1.addOption(Option.builder().longOpt("offlineActivationRequest").hasArg(false).desc("save offline activation request file").build());
		og1.addOption(Option.builder().longOpt("offlineActivate").hasArg(false).desc("activate offline with activation file").build());
		og1.addOption(Option.builder().longOpt("offlineDeactivate").hasArg(false).desc("deactivate the license offline and save proof into file").build());
				
		og1.addOption(Option.builder().longOpt("floatingLease").numberOfArgs(2).argName("address:port").valueSeparator(':').desc("add floating license server settings").build());
		og1.addOption(Option.builder().longOpt("floatingRelease").hasArg(false).desc("remove floating license server settings").build());
		
		og1.addOption(Option.builder().longOpt("licenseSummary").hasArg(false).desc("print license summary").build());
		OPTIONS.addOptionGroup(og1);
		
		OPTIONS.addOption(Option.builder().longOpt("key").hasArg().argName("license key").desc("Your AgenaRisk 10 license key").build());
		OPTIONS.addOption(Option.builder().longOpt("oPath").hasArg().argName("path").desc("Path for an offline activation file").build());
		
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
			String key = cmd.getOptionValue("key");
			License.keyActivate(key);
		}
		
		if (cmd.hasOption("keyDeactivate")){
			License.keyRelease();
		}
		
		if (cmd.hasOption("offlineActivationRequest")){
			String key = cmd.getOptionValue("key");
			String path = cmd.getOptionValue("oPath");
			License.offlineActivationRequestSave(key, path);
		}
		
		if (cmd.hasOption("offlineActivate")){
			String path = cmd.getOptionValue("oPath");
			License.offlineActivate(path);
		}
		
		if (cmd.hasOption("offlineDeactivate")){
			String path = cmd.getOptionValue("oPath");
			License.offlineRelease(path);
		}

		if (cmd.hasOption("floatingLease")){
			String[] params = cmd.getOptionValues("floatingLease");
			License.floatingLicenseLease(params[0], params[1]);
		}
		
		if (cmd.hasOption("floatingRelease")){
			License.floatingLicenseRelease();
		}
		
		if (cmd.hasOption("licenseSummary")){
			System.out.println("License summary:");
			System.out.println(License.getSummary().toString(10));
		}
	}
}
