package com.agenarisk.test.learning;

import com.agenarisk.learning.structure.config.Config;
import com.agenarisk.learning.structure.config.PerformanceEvaluationConfigurer;
import com.agenarisk.learning.structure.config.PerformanceEvaluationExecutor;
import com.agenarisk.learning.structure.result.PerformanceEvaluation;
import com.agenarisk.learning.structure.result.Result;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.util.Environment;

/**
 * Reproduces the reported bug: performance evaluation against a continuous (simulated ContinuousInterval) target
 * used to fail every case with "Target node states does not contain actual node state from case data", since it
 * only ever compared the raw CSV value against the target's discrete state labels. Confirms the fix: continuous
 * targets now get MAE/RMSE/CRPS instead, computed from the predicted mean/standard deviation.
 */
public class PerformanceEvaluationContinuousTargetTest {

	{
		Environment.initialize();
	}

	private static final String MODEL_JSON = "{"
			+ "\"model\": {"
			+ "  \"networks\": [{"
			+ "    \"id\": \"net\","
			+ "    \"nodes\": [{"
			+ "        \"id\": \"x1\","
			+ "        \"configuration\": {\"simulated\": true, \"type\": \"ContinuousInterval\", \"table\": {\"type\": \"Expression\", \"expressions\": [\"Arithmetic(0)\"]}}"
			+ "      }, {"
			+ "        \"id\": \"total_cost\","
			+ "        \"configuration\": {\"simulated\": true, \"type\": \"ContinuousInterval\", \"table\": {\"type\": \"Expression\", \"expressions\": [\"Normal(2 + 3*x1, 4)\"]}}"
			+ "      }"
			+ "    ],"
			+ "    \"links\": [{\"parent\": \"x1\", \"child\": \"total_cost\"}]"
			+ "  }]"
			+ "}"
			+ "}";

	@Test
	public void testContinuousTargetProducesMaeRmseCrpsInsteadOfThrowing() throws Exception {
		Path tempDir = Files.createTempDirectory("perf-eval-continuous-test");
		tempDir.toFile().deleteOnExit();

		Path modelPath = tempDir.resolve("model1.cmpx");
		Files.write(modelPath, new JSONObject(MODEL_JSON).toString().getBytes(StandardCharsets.UTF_8));

		// predicted mean = 2 + 3*x1, predicted stdDev = 2 (variance 4) for every row, since x1 is fully observed
		Path dataPath = tempDir.resolve("validation.csv");
		Files.write(dataPath, "x1,total_cost\n0,2\n1,7\n2,8\n".getBytes(StandardCharsets.UTF_8));

		Config config = Config.reset(c -> {});
		config.setPathInput(tempDir.toString());

		PerformanceEvaluationConfigurer configurer = new PerformanceEvaluationConfigurer(config);
		JSONObject jConfig = new JSONObject();
		JSONObject jParams = new JSONObject();
		jParams.put("dataPath", dataPath.toString());
		jParams.put("targets", new org.json.JSONArray().put("total_cost"));
		jParams.put("calculateRoc", false);
		jConfig.put("parameters", jParams);
		configurer.configureFromJson(jConfig);

		configurer.setOutputDirPath(tempDir);
		Map<String, String> modelPrefixes = new HashMap<>();
		modelPrefixes.put("model1", "Model One");
		configurer.setModelPrefixes(modelPrefixes);
		configurer.setPipelineResult(new Result());
		configurer.setStageLabel("eval1");

		PerformanceEvaluationExecutor executor = configurer.apply();
		executor.execute();

		Result result = configurer.getPipelineResult();
		Assertions.assertEquals(1, result.getPerformanceEvaluations().size());
		PerformanceEvaluation evaluation = result.getPerformanceEvaluations().get(0);

		Assertions.assertTrue(evaluation.isSuccess(), "Evaluation should succeed: " + evaluation.getMessage());
		Assertions.assertEquals(1, evaluation.getTargetResults().size());
		PerformanceEvaluation targetResult = evaluation.getTargetResults().get(0);
		Assertions.assertTrue(targetResult.isSuccess(), "Target should succeed: " + targetResult.getMessage());

		// Loose tolerance: Agena builds a Normal(...) continuous node's NPT via sampling
		// (Normal.java forces setForceSampling(true)), so the predicted mean/stddev carry
		// small inherent numerical noise rather than being exactly analytic.
		Assertions.assertEquals("continuous", targetResult.getTargetKind());
		Assertions.assertNotNull(targetResult.getMae());
		Assertions.assertEquals(0.666667, targetResult.getMae(), 1e-2);
		Assertions.assertNotNull(targetResult.getRmse());
		Assertions.assertEquals(1.154701, targetResult.getRmse(), 1e-2);
		Assertions.assertNotNull(targetResult.getCrps());
		Assertions.assertEquals(0.713221, targetResult.getCrps(), 1e-2);

		// Classification-only metrics are meaningless here and must not be silently populated
		Assertions.assertTrue(targetResult.getRocAucs().isEmpty());

		// Model-level aggregate mirrors the single target's result and kind
		Assertions.assertEquals("continuous", evaluation.getTargetKind());
		Assertions.assertNotNull(evaluation.getMae());
		Assertions.assertEquals(targetResult.getMae(), evaluation.getMae(), 1e-9);
	}

	@Test
	public void testMixedContinuousAndDiscreteTargetsIsRejected() throws Exception {
		Path tempDir = Files.createTempDirectory("perf-eval-mixed-targets-test");
		tempDir.toFile().deleteOnExit();

		String modelWithMixedTargets = "{"
				+ "\"model\": {"
				+ "  \"networks\": [{"
				+ "    \"id\": \"net\","
				+ "    \"nodes\": [{"
				+ "        \"id\": \"x1\","
				+ "        \"configuration\": {\"simulated\": true, \"type\": \"ContinuousInterval\", \"table\": {\"type\": \"Expression\", \"expressions\": [\"Arithmetic(0)\"]}}"
				+ "      }, {"
				+ "        \"id\": \"total_cost\","
				+ "        \"configuration\": {\"simulated\": true, \"type\": \"ContinuousInterval\", \"table\": {\"type\": \"Expression\", \"expressions\": [\"Normal(2 + 3*x1, 4)\"]}}"
				+ "      }, {"
				+ "        \"id\": \"car_type\","
				+ "        \"configuration\": {\"type\": \"Labelled\", \"states\": [\"Small\", \"Large\"]}"
				+ "      }"
				+ "    ],"
				+ "    \"links\": [{\"parent\": \"x1\", \"child\": \"total_cost\"}]"
				+ "  }]"
				+ "}"
				+ "}";

		Path modelPath = tempDir.resolve("model1.cmpx");
		Files.write(modelPath, modelWithMixedTargets.getBytes(StandardCharsets.UTF_8));

		Path dataPath = tempDir.resolve("validation.csv");
		Files.write(dataPath, "x1,total_cost,car_type\n0,2,Small\n1,7,Large\n".getBytes(StandardCharsets.UTF_8));

		Config config = Config.reset(c -> {});
		config.setPathInput(tempDir.toString());

		PerformanceEvaluationConfigurer configurer = new PerformanceEvaluationConfigurer(config);
		JSONObject jConfig = new JSONObject();
		JSONObject jParams = new JSONObject();
		jParams.put("dataPath", dataPath.toString());
		jParams.put("targets", new org.json.JSONArray().put("total_cost").put("car_type"));
		jConfig.put("parameters", jParams);
		configurer.configureFromJson(jConfig);

		configurer.setOutputDirPath(tempDir);
		Map<String, String> modelPrefixes = new HashMap<>();
		modelPrefixes.put("model1", "Model One");
		configurer.setModelPrefixes(modelPrefixes);
		configurer.setPipelineResult(new Result());
		configurer.setStageLabel("eval1");

		PerformanceEvaluationExecutor executor = configurer.apply();
		executor.execute();

		Result result = configurer.getPipelineResult();
		PerformanceEvaluation evaluation = result.getPerformanceEvaluations().get(0);

		Assertions.assertFalse(evaluation.isSuccess());
		Assertions.assertTrue(evaluation.getMessage().contains("all continuous or all discrete"), evaluation.getMessage());
	}
}
