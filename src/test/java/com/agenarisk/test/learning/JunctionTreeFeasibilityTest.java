package com.agenarisk.test.learning;

import com.agenarisk.api.exception.CalculationException;
import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.util.Environment;

/**
 * "Insufficient memory" is a true but useless thing to tell someone: it names no cause and suggests no
 * action. It is also the one failure whose cause is exactly measurable, since the junction tree can be
 * built structure-only and the offending clique read straight off it. These tests cover the probe and
 * the failure message that now carries it.
 *
 * <p>The model used is the case neither factorisation pass can rescue — a categorical child over many
 * DENSE continuous parents, where every predictor references every covariate. Extraction cannot help
 * (the scores all depend on the same covariates, so moralising the child couples them) and nesting
 * cannot either (it bounds state arity, and arity is not the problem here). The cost is the child's own
 * family, K x bins^parents, so it is genuinely infeasible rather than merely un-optimised — which makes
 * it the honest case for a diagnostic to explain.</p>
 */
public class JunctionTreeFeasibilityTest {

	{
		Environment.initialize();
	}

	/** Categorical child over {@code nCov} simulated continuous parents, every predictor over all of them. */
	static Model denseLogit(int k, int nCov) throws Exception {
		Model model = Model.createModel();
		Network net = model.createNetwork("net", "net");

		for (int i = 1; i <= nCov; i++) {
			Node x = net.createNode("x" + i, Node.Type.ContinuousInterval);
			x.convertToSimulated();
			x.setTableFunction("Normal(" + (i * 2.0) + ", 1.0)");
		}

		JSONArray states = new JSONArray();
		for (int s = 0; s < k; s++) {
			states.put("s" + s);
		}
		Node y = net.createNode("y", Node.Type.Labelled);
		y.setStates(states);
		for (int i = 1; i <= nCov; i++) {
			net.getNode("x" + i).linkTo(y);
		}

		StringBuilder[] preds = new StringBuilder[k - 1];
		for (int j = 0; j < k - 1; j++) {
			preds[j] = new StringBuilder(String.valueOf(0.1 * (j + 1)));
			for (int c = 1; c <= nCov; c++) {
				preds[j].append(" + 0.0").append(c).append("*x").append(c);
			}
		}
		String[] exprs = new String[k - 1];
		for (int j = 0; j < k - 1; j++) {
			exprs[j] = preds[j].toString();
		}
		y.setTableFunction("MultinomialLogit(" + String.join(", ", exprs) + ")");
		return model;
	}

	/** The probe must report a small model as feasible and describe its cliques. */
	@Test
	public void probeReportsFeasibleModel() throws Exception {
		JSONObject report = denseLogit(4, 2).inspectJunctionTrees(20);

		Assertions.assertFalse(report.getBoolean("infeasible"),
				"a 2-covariate logit should be feasible: " + report.get("modelMaxCliqueCells"));
		Assertions.assertTrue(report.getDouble("modelMaxCliqueCells") > 0, "should report a clique size");
		Assertions.assertTrue(report.getLong("heapMaxBytes") > 0, "should report the heap ceiling");

		JSONArray networks = report.getJSONArray("networks");
		Assertions.assertEquals(1, networks.length());
		Assertions.assertTrue(networks.getJSONObject(0).getJSONArray("cliques").length() > 0,
				"should enumerate the cliques");
	}

	/** And must flag the dense many-covariate case, which no factorisation pass can rescue. */
	@Test
	public void probeFlagsInfeasibleModel() throws Exception {
		JSONObject report = denseLogit(4, 10).inspectJunctionTrees(20);

		Assertions.assertTrue(report.getBoolean("infeasible"),
				"a 10-covariate dense logit should be reported infeasible, got "
						+ report.get("modelMaxCliqueCells"));
		// K * 20^10; the point is the order of magnitude, not the exact figure.
		Assertions.assertTrue(report.getDouble("modelMaxCliqueCells") > 1e12,
				"expected a clique past 1e12, got " + report.get("modelMaxCliqueCells"));
	}

	/**
	 * Densely-coupled base nodes: every family is tiny ({@code states^2 x 2}), so nothing trips the
	 * node-table guard when the model is built, but moralising the pairwise children makes all
	 * {@code n} base nodes one clique of {@code states^n}. This fails inside {@code calculate()} in
	 * well under a second with the heap untouched — which is what makes it usable in a normal test run.
	 */
	private static Model coupledClique(int n, int states) throws Exception {
		Model model = Model.createModel();
		Network net = model.createNetwork("net", "net");

		JSONArray baseStates = new JSONArray();
		for (int s = 0; s < states; s++) {
			baseStates.put("v" + s);
		}
		for (int i = 0; i < n; i++) {
			net.createNode("a" + i, Node.Type.Labelled).setStates(baseStates);
		}

		int c = 0;
		for (int i = 0; i < n; i++) {
			for (int j = i + 1; j < n; j++) {
				Node child = net.createNode("c" + (c++), Node.Type.Labelled);
				child.setStates(new JSONArray().put("no").put("yes"));
				net.getNode("a" + i).linkTo(child);
				net.getNode("a" + j).linkTo(child);
			}
		}
		return model;
	}

	/**
	 * The payload: a model that cannot calculate must fail with a message quantifying the size and
	 * saying what to change — not just "Insufficient memory".
	 *
	 * <p>Note what this case looks like in the report, because it is the case that matters and it is
	 * the opposite of the obvious assumption: when a cluster is too large to allocate, the junction-tree
	 * build aborts <em>at that cluster</em>, so it records the offending size and an error but never
	 * enumerates any cliques. A diagnostic anchored on the clique list therefore says nothing precisely
	 * when the model is worst. This asserts the network-level path with {@code cliques} empty.</p>
	 */
	@Test
	public void outOfMemoryFailureExplainsItself() throws Exception {
		Model model = coupledClique(8, 30);

		// The premise: no cliques to enumerate, but a size and an error that can still be reported.
		JSONObject network = model.inspectJunctionTrees(20).getJSONArray("networks").getJSONObject(0);
		Assertions.assertEquals(0, network.getJSONArray("cliques").length(),
				"premise of this test is an aborted build with no enumerable cliques");
		Assertions.assertTrue(network.getDouble("maxCliqueCells") > Integer.MAX_VALUE,
				"the offending size should still be reported");

		try {
			model.calculate();
			// If a future engine change makes this calculable, the test has stopped testing anything.
			Assertions.fail("expected a densely-coupled 8x30 model to fail to calculate");
		}
		catch (CalculationException ex) {
			String message = ex.getMessage();
			Assertions.assertNotNull(message);
			Assertions.assertTrue(message.startsWith("Insufficient memory"),
					"should keep the existing classification, was: " + message);
			Assertions.assertTrue(message.contains("Largest clique"),
					"failure should quantify the offending clique, was: " + message);
			Assertions.assertTrue(message.contains("net"),
					"failure should name the network, was: " + message);
			Assertions.assertTrue(message.toLowerCase().contains("reduce"),
					"failure should suggest what to change, was: " + message);
			Assertions.assertTrue(message.length() > "Insufficient memory".length() + 40,
					"failure should carry real detail, was: " + message);
			System.out.println("DIAGNOSTIC: " + message);
		}
	}
}
