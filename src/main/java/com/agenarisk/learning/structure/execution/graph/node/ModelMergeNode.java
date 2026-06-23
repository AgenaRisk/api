package com.agenarisk.learning.structure.execution.graph.node;

import com.agenarisk.api.model.Model;
import com.agenarisk.learning.structure.execution.graph.GraphExecutionContext;
import com.agenarisk.learning.structure.logger.BLogger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

public class ModelMergeNode extends GraphNode {

	private List<String> models = new ArrayList<>();
	private String outputFileName;

	@Override
	public String getSubType() {
		return "modelMerge";
	}

	@Override
	public void parseOptions(JSONObject jOptions) {
		JSONArray jModels = jOptions.optJSONArray("models");
		if (jModels != null) {
			for (int i = 0; i < jModels.length(); i++) {
				models.add(jModels.getString(i));
			}
		}
		outputFileName = jOptions.optString("outputFileName", "");
	}

	@Override
	public Set<String> getInputLabels() {
		return new LinkedHashSet<>(models);
	}

	@Override
	public void execute(GraphExecutionContext ctx) {
		String fileName = (outputFileName != null && !outputFileName.isEmpty()) ? outputFileName : getLabel() + ".cmpx";
		if (!fileName.endsWith(".cmpx")) {
			fileName = fileName + ".cmpx";
		}
		Path outputPath = ctx.getOutputDirPath().resolve(fileName);

		try {
			Model merged = Model.createModel();

			for (String modelLabel : models) {
				GraphNode parent = ctx.getNode(modelLabel);
				if (parent == null || !(parent instanceof ModelNode) || parent.getStatus() != Status.success) {
					BLogger.logConditional("Skipping model '" + modelLabel + "' for merge (not available or failed)");
					continue;
				}
				try {
					Path modelPath = ctx.modelPath(modelLabel);
					Model modelToImport = Model.loadModel(modelPath.toString());
					modelToImport.getNetworkList().get(0).setId(modelLabel);
					modelToImport.getNetworkList().get(0).setName(modelLabel);
					merged.absorb(modelToImport.toJson());
				}
				catch (Exception ex) {
					BLogger.logConditional("Failed to merge model '" + modelLabel + "': " + ex.getMessage());
					BLogger.logThrowableIfDebug(ex);
				}
			}

			byte[] bytes = merged.export(
							Model.ExportFlag.KEEP_META,
							Model.ExportFlag.KEEP_OBSERVATIONS,
							Model.ExportFlag.KEEP_RESULTS
			).toString().getBytes();
			Files.write(outputPath, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

			JSONObject result = new JSONObject();
			result.put("path", outputPath.toAbsolutePath().toString());
			setResult(result);
			setStatus(Status.success);
		}
		catch (Exception ex) {
			failWith("Failed to merge models: " + ex.getMessage(), ex);
		}
	}

	public List<String> getModels() {
		return models;
	}

	@Override
	public List<Path> getOutputFiles(GraphExecutionContext ctx) {
		String fileName = (outputFileName != null && !outputFileName.isEmpty()) ? outputFileName : getLabel() + ".cmpx";
		if (!fileName.endsWith(".cmpx")) {
			fileName = fileName + ".cmpx";
		}
		return Collections.singletonList(ctx.getOutputDirPath().resolve(fileName));
	}
}
