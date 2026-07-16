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
 * Reproduces two escalating versions of the same underlying problem: entering more of a validation row as hard
 * evidence than the target's own root causes routinely produces mutually-inconsistent evidence, since a learned
 * model's downstream nodes are tied to their inputs by fitted (or, here, deterministic) relationships that a raw
 * validation row is not guaranteed to respect exactly.
 * <br>
 * The first pair of tests covers "entering non-parent columns" - "side" and "target" are both deterministic
 * (Comparative) functions of "x1", but "target"'s only parent is "x1" - "side" is a sibling, not a parent. The
 * second pair covers the deeper problem: even entering only the target's <em>direct</em> parents isn't enough
 * when one of those parents is itself a downstream node computed from another of the target's parents ("mid" is
 * both a parent of "target" and a deterministic function of "x1", which is target's other parent) - entering
 * both of target's direct parents at their raw, possibly-mutually-contradictory CSV values reproduces the same
 * class of failure one level deeper. The fix in both cases: only the target's true root (parentless) ancestors
 * are entered as evidence, and every downstream node - including the target's own direct parents, if they
 * themselves have parents - is left for the model to compute.
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

	/**
	 * "target" has two direct parents: "x1" (a root) and "mid" (itself a deterministic function of "x1", i.e. a
	 * grandparent-via-"mid" relationship). target = x1 + mid = x1 + 2*x1 = 3*x1.
	 */
	private Path buildChainModel(Path tempDir) throws Exception {
		Model model = Model.createModel();
		Network net = model.createNetwork("net");

		Node x1 = net.createNode("x1", Node.Type.ContinuousInterval);
		x1.convertToSimulated();
		x1.setTableFunction("Arithmetic(0)");

		Node mid = net.createNode("mid", Node.Type.ContinuousInterval);
		mid.convertToSimulated();
		mid.linkFrom(x1);
		mid.setTableFunction("Arithmetic(2*x1)");

		Node target = net.createNode("target", Node.Type.ContinuousInterval);
		target.convertToSimulated();
		target.linkFrom(x1);
		target.linkFrom(mid);
		target.setTableFunction("Arithmetic(x1+mid)");

		model.createDataSet("ds");

		Path modelPath = tempDir.resolve("model1.cmpx");
		model.save(modelPath.toString());
		return modelPath;
	}

	@Test
	public void testDirectParentThatIsItselfDownstreamDoesNotFailEvaluation() throws Exception {
		Path tempDir = Files.createTempDirectory("perf-eval-root-only-test");
		tempDir.toFile().deleteOnExit();

		buildChainModel(tempDir);

		// x1=5 deterministically forces mid=10 and target=15. The CSV's "mid" column instead supplies 999 - a
		// value that flatly contradicts the model's own Arithmetic(2*x1) relationship for mid given x1=5. Entering
		// both of target's direct parents (x1 and mid) at their raw values would be inconsistent; entering only
		// the root (x1) and letting the model compute mid itself must still succeed, and correctly predict 15.
		Path dataPath = tempDir.resolve("validation.csv");
		Files.write(dataPath, "x1,mid,target\n5,999,15\n".getBytes(StandardCharsets.UTF_8));

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

		Assertions.assertTrue(evaluation.isSuccess(), "Evaluation should succeed despite the contradictory 'mid' column: " + evaluation.getMessage());
		PerformanceEvaluation targetResult = evaluation.getTargetResults().get(0);
		Assertions.assertTrue(targetResult.isSuccess(), "Target should succeed: " + targetResult.getMessage());

		// The model correctly derives mid=10 from x1=5 (ignoring the CSV's contradictory raw mid value), so
		// target=15 exactly matches the actual - a perfect prediction, MAE/RMSE of 0.
		Assertions.assertEquals("continuous", targetResult.getTargetKind());
		Assertions.assertNotNull(targetResult.getMae());
		Assertions.assertEquals(0.0, targetResult.getMae(), 1e-6);
	}

	@Test
	public void testDirectParentsEnteredTogetherWouldHaveBeenInconsistent() throws Exception {
		// Sanity check that the scenario above is a real reproduction: entering BOTH of target's direct parents
		// (x1 and the contradictory mid) as hard evidence together must actually fail, confirming this is a
		// genuinely deeper case than the sibling-column scenario above, not something parents-only already handled.
		Path tempDir = Files.createTempDirectory("perf-eval-root-only-sanity-test");
		tempDir.toFile().deleteOnExit();

		Path modelPath = buildChainModel(tempDir);
		Model model = Model.loadModel(modelPath.toString());
		Network network = model.getNetworkList().get(0);
		DataSet dataCase = model.getDataSetList().get(0);

		dataCase.setObservation(network.getNode("x1"), "5");
		dataCase.setObservation(network.getNode("mid"), "999");

		Assertions.assertThrows(Exception.class, model::calculate,
				"Entering x1=5 and mid=999 together should be inconsistent, since mid is a deterministic function of x1 (mid=2*x1=10)");
	}
}
