package com.agenarisk.test.learning;

import com.agenarisk.api.model.DataSet;
import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
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
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.util.Environment;

/**
 * Reproduces the reported problem: entering an entire validation row as evidence (every column except the
 * target) routinely produces mutually-inconsistent evidence, because non-parent columns can be deterministically
 * (or near-deterministically) tied to other observed columns in the learned model, and a validation row is not
 * guaranteed to respect those learned relationships exactly. Confirms the fix: only the target's direct parents
 * are entered as evidence, so a non-parent column that contradicts the model no longer breaks the evaluation.
 *
 * The model here: "side" and "target" are both deterministic (Comparative) functions of "x1", but "target"'s only
 * parent is "x1" - "side" is a sibling, not a parent. The validation row supplies a "side" value that contradicts
 * what "side"'s own deterministic function of "x1" requires, so entering it as hard evidence alongside "x1"
 * produces a zero-probability joint (InconsistentEvidenceException) - reproducing the bug when the full row is
 * entered, and confirming the fix once only the target's parents ("x1") are entered.
 */
public class PerformanceEvaluationParentsOnlyEvidenceTest {

	{
		Environment.initialize();
	}

	private Path buildModel(Path tempDir) throws Exception {
		Model model = Model.createModel();
		Network net = model.createNetwork("net");

		Node x1 = net.createNode("x1", Node.Type.ContinuousInterval);
		x1.convertToSimulated();
		x1.setTableFunction("Arithmetic(0)");

		Node side = net.createNode("side", Node.Type.Boolean);
		side.linkFrom(x1);
		side.setTableFunction("Comparative(if(x1>0,\"True\",\"False\"))");

		Node target = net.createNode("target", Node.Type.Boolean);
		target.linkFrom(x1);
		target.setTableFunction("Comparative(if(x1>0,\"True\",\"False\"))");

		model.createDataSet("ds");

		Path modelPath = tempDir.resolve("model1.cmpx");
		model.save(modelPath.toString());
		return modelPath;
	}

	@Test
	public void testNonParentColumnContradictingModelDoesNotFailEvaluation() throws Exception {
		Path tempDir = Files.createTempDirectory("perf-eval-parents-only-test");
		tempDir.toFile().deleteOnExit();

		buildModel(tempDir);

		// x1=5 (>0) deterministically forces both "side" and "target" to "True". Supplying side=False here
		// contradicts that - entering it as evidence alongside x1 is a zero-probability joint - but since
		// "side" is not a parent of "target", the fix must simply ignore this column and still evaluate the
		// row correctly against the true "target" value ("True").
		Path dataPath = tempDir.resolve("validation.csv");
		Files.write(dataPath, "x1,side,target\n5,False,True\n3,False,True\n".getBytes(StandardCharsets.UTF_8));

		Config config = Config.reset(c -> {});
		config.setPathInput(tempDir.toString());

		PerformanceEvaluationConfigurer configurer = new PerformanceEvaluationConfigurer(config);
		JSONObject jConfig = new JSONObject();
		JSONObject jParams = new JSONObject();
		jParams.put("dataPath", dataPath.toString());
		jParams.put("targets", new JSONArray().put("target"));
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
		PerformanceEvaluation evaluation = result.getPerformanceEvaluations().get(0);

		Assertions.assertTrue(evaluation.isSuccess(), "Evaluation should succeed despite the contradictory non-parent column: " + evaluation.getMessage());
		PerformanceEvaluation targetResult = evaluation.getTargetResults().get(0);
		Assertions.assertTrue(targetResult.isSuccess(), "Target should succeed: " + targetResult.getMessage());

		// Both rows deterministically resolve to target=True given x1>0, and the actual target column agrees,
		// so the model's prediction should be a perfect match: absoluteError (1 - P(actual)) of 0.
		Assertions.assertEquals("discrete", targetResult.getTargetKind());
		Assertions.assertNotNull(targetResult.getAbsoluteError());
		Assertions.assertEquals(0.0, targetResult.getAbsoluteError(), 1e-6);
	}

	@Test
	public void testFullRowEvidenceWouldHaveBeenInconsistent() throws Exception {
		// Sanity check that the scenario above is a real reproduction and not a no-op: entering the
		// contradictory "side" column as hard evidence alongside "x1" must actually fail with inconsistent
		// evidence, confirming the fix is doing real work rather than the model just tolerating it anyway.
		Path tempDir = Files.createTempDirectory("perf-eval-parents-only-sanity-test");
		tempDir.toFile().deleteOnExit();

		Path modelPath = buildModel(tempDir);
		Model model = Model.loadModel(modelPath.toString());
		Network network = model.getNetworkList().get(0);
		DataSet dataCase = model.getDataSetList().get(0);

		dataCase.setObservation(network.getNode("x1"), "5");
		dataCase.setObservation(network.getNode("side"), "False");

		Assertions.assertThrows(Exception.class, model::calculate,
				"Entering x1=5 and side=False together should be inconsistent, since side is a deterministic function of x1");
	}
}
