package com.agenarisk.learning.structure.execution.graph.node;

import BNlearning.Database;
import com.agenarisk.api.model.Model;
import com.agenarisk.api.util.TempFileCleanup;
import com.agenarisk.learning.structure.config.Config;
import com.agenarisk.learning.structure.config.GesConfigurer;
import com.agenarisk.learning.structure.config.HcConfigurer;
import com.agenarisk.learning.structure.config.HcStableConfigurer;
import com.agenarisk.learning.structure.config.MahcConfigurer;
import com.agenarisk.learning.structure.config.SaiyanHConfigurer;
import com.agenarisk.learning.structure.config.TabuConfigurer;
import com.agenarisk.learning.structure.config.ApplicableConfigurer;
import com.agenarisk.learning.structure.exception.StructureLearningException;
import com.agenarisk.learning.structure.execution.graph.GraphExecutionContext;
import com.agenarisk.learning.structure.logger.BLogger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONObject;

@SuppressWarnings("rawtypes")
public class DiscreteStructureDiscoveryNode extends ModelNode {

	private String dataSource;
	private String algorithm;
	private JSONObject jOptions;

	@Override
	public String getSubType() {
		return "discreteStructureDiscovery";
	}

	@Override
	public void parseOptions(JSONObject jOptions) {
		this.jOptions = jOptions;
		this.dataSource = jOptions.optString("dataSource", "");
		this.algorithm = jOptions.optString("algorithm", "");
	}

	@Override
	public Set<String> getInputLabels() {
		Set<String> labels = new LinkedHashSet<>();
		if (dataSource != null && !dataSource.isEmpty()) {
			labels.add(dataSource);
		}
		return labels;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void execute(GraphExecutionContext ctx) {
		Path workDir = null;
		try {
			DataSourceNode dsNode = requireDataSource(ctx, dataSource);
			Path dataPath = dsNode.resolvedPath(ctx);
			Path outputDirPath = ctx.getOutputDirPath();

			// Bayesys writes fixed-name intermediate files (correlation .cor,
			// preprocessed CSVs, the temp .cmp) into its input/output path. Two
			// runs sharing a directory therefore clobber each other's files,
			// producing a corrupt/missing .cmp ("Failed to convert CMP model
			// data"). This happens not only across different discovery nodes but
			// also when the SAME node runs twice concurrently (the parallel
			// coordinator computes it while a downstream Variable Mapping's
			// runAncestors also materialises it). Use a UNIQUE working directory
			// per execution — keyed on label + nanoTime — so no two runs can ever
			// share intermediates, and copy the input data into it.
			String safeLabel = getLabel().replaceAll("[^A-Za-z0-9._-]", "_");
			Path workDir_ = outputDirPath.resolve(".discovery").resolve(safeLabel + "-" + System.nanoTime());
			workDir = workDir_;
			Files.createDirectories(workDir);
			Path workData = workDir.resolve(dataPath.getFileName().toString());
			Files.copy(dataPath, workData, StandardCopyOption.REPLACE_EXISTING);

			Config config = Config.reset((c) -> {
				TempFileCleanup.cleanup(c);
				Database.reset();
			});

			config.setFileInputTrainingDataCsv(workData.getFileName().toString());
			config.setPathInput(workDir.toString());
			config.setPathOutput(workDir.toString());

			Config.LearningAlgorithm algo;
			try {
				algo = Config.LearningAlgorithm.valueOf(algorithm);
			}
			catch (Exception ex) {
				throw new StructureLearningException("Unknown algorithm: " + algorithm, ex);
			}

			HashMap<String, String> data = new HashMap<>();
			data.put("prefix", getLabel());
			data.put("outPath", outputDirPath.toString());

			ApplicableConfigurer configurer;
			switch (algo) {
				case HC:
					configurer = new HcConfigurer(config).useData(data).configureFromJson(jOptions);
					break;
				case HCStable:
					configurer = new HcStableConfigurer(config).useData(data).configureFromJson(jOptions);
					break;
				case SaiyanH:
					configurer = new SaiyanHConfigurer(config).useData(data).configureFromJson(jOptions);
					break;
				case GES:
					configurer = new GesConfigurer(config).useData(data).configureFromJson(jOptions);
					break;
				case MAHC:
					configurer = new MahcConfigurer(config).useData(data).configureFromJson(jOptions);
					break;
				case TABU:
					configurer = new TabuConfigurer(config).useData(data).configureFromJson(jOptions);
					break;
				default:
					throw new StructureLearningException("Unsupported algorithm: " + algorithm);
			}

			String cmpName = getLabel() + ".cmp";
			config.setFileOutputCmp(cmpName);

			configurer.apply().execute();

			Path cmpPath = workDir.resolve(cmpName);
			Model model = Model.loadModel(cmpPath.toString());

			Path cmpxPath = getModelPath(ctx);
			model.save(cmpxPath.toString());

			// Copy the auxiliary structure CSV to the output dir. This is a
			// secondary artifact — the model (.cmpx) above is the real output — so
			// its copy must NEVER fail the discovery. Under parallel runs the
			// destination can be transiently locked on Windows ("used by another
			// process"); retry a few times, then give up quietly rather than
			// throwing (which would fail an otherwise-successful discovery and, in
			// a gated run, leave its structure unimportable downstream).
			Path csvSrc = workDir.resolve(config.getFileOutputDagLearnedCsv());
			if (Files.exists(csvSrc)) {
				copyBestEffort(csvSrc, outputDirPath.resolve(getLabel() + ".csv"));
			}

			BLogger.logConditional("Discovery complete: " + getLabel());
			setResult(model.toJson().optJSONObject("model"));
			setStatus(Status.success);
		}
		catch (Exception ex) {
			failWith("Discovery failed: " + friendlyMessage(ex), ex);
		}
		finally {
			// Always remove the per-execution working dir — its intermediates are
			// no longer needed and would otherwise accumulate (unique names) and
			// be archived with the run.
			deleteQuietly(workDir);
		}
	}

	// Copy a file, retrying briefly if the destination is transiently locked
	// (common on Windows under concurrent runs), and swallowing a final failure —
	// the caller treats this artifact as optional.
	private void copyBestEffort(Path src, Path dest) {
		for (int attempt = 1; attempt <= 5; attempt++) {
			try {
				Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
				return;
			}
			catch (Exception ex) {
				if (attempt == 5) {
					BLogger.logConditional(
						"Could not copy structure CSV for " + getLabel() + " (non-fatal): " + ex.getMessage());
					return;
				}
				try {
					Thread.sleep(120L * attempt);
				}
				catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					return;
				}
			}
		}
	}

	// Best-effort recursive delete of a working directory. Collect the paths and
	// close the walk stream BEFORE deleting — on Windows an open directory handle
	// would otherwise block removal.
	private static void deleteQuietly(Path dir) {
		if (dir == null) {
			return;
		}
		try {
			if (!Files.exists(dir)) {
				return;
			}
			List<Path> paths;
			try (java.util.stream.Stream<Path> walk = Files.walk(dir)) {
				paths = walk.sorted(java.util.Comparator.reverseOrder())
					.collect(java.util.stream.Collectors.toList());
			}
			for (Path p : paths) {
				try {
					Files.deleteIfExists(p);
				}
				catch (Exception ignore) {
					// leave residual files rather than fail the run
				}
			}
		}
		catch (Exception ignore) {
			// non-fatal: intermediates just remain on disk
		}
	}

	public String getDataSource() {
		return dataSource;
	}

	public String getAlgorithm() {
		return algorithm;
	}

	@Override
	public List<Path> getOutputFiles(GraphExecutionContext ctx) {
		List<Path> files = new ArrayList<>(super.getOutputFiles(ctx));
		files.add(ctx.getOutputDirPath().resolve(getLabel() + ".csv"));
		return files;
	}
}
