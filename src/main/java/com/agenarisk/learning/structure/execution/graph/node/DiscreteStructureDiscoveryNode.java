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
		try {
			DataSourceNode dsNode = requireDataSource(ctx, dataSource);
			Path dataPath = dsNode.resolvedPath(ctx);
			Path dataDir = dataPath.getParent();
			Path outputDirPath = ctx.getOutputDirPath();

			Config config = Config.reset((c) -> {
				TempFileCleanup.cleanup(c);
				Database.reset();
			});

			config.setFileInputTrainingDataCsv(dataPath.getFileName().toString());
			config.setPathInput(dataDir.toString());
			// Bayesys writes its temp CMP to pathOutput; use data dir to match pipeline behaviour
			config.setPathOutput(dataDir.toString());

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

			Path cmpPath = dataDir.resolve(cmpName);
			Model model = Model.loadModel(cmpPath.toString());

			Path cmpxPath = getModelPath(ctx);
			model.save(cmpxPath.toString());

			// Copy structure CSV to output dir
			Path csvSrc = dataDir.resolve(config.getFileOutputDagLearnedCsv());
			if (Files.exists(csvSrc)) {
				Files.copy(csvSrc, outputDirPath.resolve(getLabel() + ".csv"), StandardCopyOption.REPLACE_EXISTING);
			}

			BLogger.logConditional("Discovery complete: " + getLabel());
			setResult(model.toJson().optJSONObject("model"));
			setStatus(Status.success);
		}
		catch (Exception ex) {
			failWith("Discovery failed: " + friendlyMessage(ex), ex);
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
