package com.agenarisk.learning.structure.execution.graph.node;

import com.agenarisk.api.model.Model;
import com.agenarisk.learning.structure.execution.graph.GraphExecutionContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Set;
import org.json.JSONObject;

public class ModelImportNode extends ModelNode {

	private String path;

	@Override
	public String getSubType() {
		return "modelImport";
	}

	@Override
	public void parseOptions(JSONObject jOptions) {
		this.path = jOptions.optString("path", "");
	}

	@Override
	public Set<String> getInputLabels() {
		return Collections.emptySet();
	}

	@Override
	public void execute(GraphExecutionContext ctx) {
		try {
			if (path == null || path.isEmpty()) {
				throw new IllegalArgumentException("Model import path is required");
			}
			Model model = Model.loadModel(ctx.resolveConfigPath(path).toString());
			JSONObject exported = model.export(
							Model.ExportFlag.KEEP_META,
							Model.ExportFlag.KEEP_OBSERVATIONS,
							Model.ExportFlag.KEEP_RESULTS
			);
			JSONObject modelSection = exported.optJSONObject("model");
			if (modelSection != null) {
				modelSection.remove("riskTable");
				modelSection.remove("graphics");
			}

			Path outputPath = getModelPath(ctx);
			Files.write(outputPath, exported.toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

			setResult(modelSection != null ? modelSection : new JSONObject());
			setStatus(Status.success);
		}
		catch (Exception ex) {
			failWith("Failed to import model: " + friendlyMessage(ex), ex);
		}
	}

	public String getPath() {
		return path;
	}
}
