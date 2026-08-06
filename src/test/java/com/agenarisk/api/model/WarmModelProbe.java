package com.agenarisk.api.model;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Disabled;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Sizing the "stateful engine session" idea (DD_CLIENT_STAGEWISE_CALC.md §13): the sidecar rebuilds the
 * whole model from JSON on EVERY request, and that fixed cost is what sank stagewise calculation.
 *
 * <p>Answers, all measured on Portfolio_Model_10Y (196 networks, 12 MB wire, 9480 observations):
 * parse ~260 ms + build ~1500 ms + dataSet ~1320 ms = <b>~3.0 s of fixed cost per request</b>; and a warm
 * model answers exactly what a fresh build answers, including when given different evidence. The
 * questions:</p>
 * <ol>
 *   <li>How much of a request is the rebuild? (Is the prize worth a session protocol at all?)</li>
 *   <li>Is a WARM model — one already calculated once — safe to calculate again? Dynamic discretisation
 *       replaces node states as it refines, so a second run does not start from the same discretisation
 *       as a fresh build. If the answers differ, a cached model changes results, and that governs the
 *       whole design.</li>
 * </ol>
 */
public class WarmModelProbe {

	private static final String BIG =
			"C:/Users/marti/Documents/Valinor/agenarisk/martin test cases/"
			+ "claude parallelisation and DD bounds propagation/"
			+ "Portfolio_Model_10Y_v1.0 runs in 1 minute under concurrency.cmpx";

	/**
	 * Q1: what a sidecar request pays BEFORE any propagation, on the model the §13 measurements used.
	 * Every step here is repeated on every request today, and every one of them is what a warm session
	 * would avoid. Measured separately so the design targets the right one.
	 */
	@Test
	@Disabled("measurement, not a regression test: loads an 18 MB model three times (~30s). Run on demand.")
	public void whatDoesASidecarRequestPayBeforeItCalculatesAnything() throws Exception {
		Assumptions.assumeTrue(new File(BIG).exists(), "big model not present");
		Model m = Model.loadModel(BIG);
		String wire = m.toJson().toString();          // what the client actually POSTs
		System.out.println("[warm] wire payload = " + (wire.length() / (1024 * 1024)) + " MB");

		// Three runs: JIT and allocation noise otherwise dominate a single sample.
		for (int i = 0; i < 3; i++) {
			long t0 = System.nanoTime();
			JSONObject parsed = new JSONObject(wire);
			long parseMs = (System.nanoTime() - t0) / 1_000_000;

			t0 = System.nanoTime();
			Model rebuilt = Model.createModel(parsed);
			long buildMs = (System.nanoTime() - t0) / 1_000_000;

			// The client also posts the case; the server rebuilds it every time too. Use the model's own
			// stored dataSet, which is the real one (9480 observations on this model).
			long dsMs = -1;
			int obs = -1;
			JSONArray stored = parsed.getJSONObject("model").optJSONArray("dataSets");
			if (stored != null && stored.length() > 0) {
				JSONObject dsJson = stored.getJSONObject(0);
				obs = dsJson.optJSONArray("observations") == null ? 0 : dsJson.optJSONArray("observations").length();
				// createModel already built the stored dataSets; re-add under a new id to time it alone.
				dsJson = new JSONObject(dsJson.toString()).put("id", "probe_" + i);
				long t1 = System.nanoTime();
				rebuilt.createDataSet(dsJson);
				dsMs = (System.nanoTime() - t1) / 1_000_000;
			}

			System.out.println("[warm] run " + i + ": jsonParse=" + parseMs + "ms"
					+ " createModel=" + buildMs + "ms"
					+ " createDataSet(" + obs + " obs)=" + dsMs + "ms"
					+ " TOTAL fixed=" + (parseMs + buildMs + Math.max(dsMs, 0)) + "ms"
					+ " networks=" + rebuilt.getNetworkList().size());
		}
	}

	/**
	 * Q2, on a small dynamically-discretised model so it runs fast: calculate a fresh model, then
	 * calculate the SAME instance again, and compare against a second fresh build. If warm != fresh,
	 * a cached model silently changes what the user sees.
	 */
	@Test
	public void doesRecalculatingAWarmModelGiveTheSameAnswer() throws Exception {
		Model fresh1 = ddModel();
		String a = resultDigest(calcAndRead(fresh1));

		// Same instance, calculated a second time - what a warm session would do.
		String b = resultDigest(calcAndRead(fresh1));

		// An independently built model, calculated once - what the sidecar does today.
		String c = resultDigest(calcAndRead(ddModel()));

		assertEquals(c, a, "a fresh build must be repeatable, or this test can prove nothing");
		assertEquals(c, b, "recalculating a WARM model must answer what a fresh build answers - "
				+ "dynamic discretisation carries its refined states over, so this is not free by construction");
	}

	/**
	 * Q3, the case that actually governs the design: a warm model still holds the PREVIOUS request's
	 * evidence and its DD-refined states. If the next request's case is applied to that model, does it
	 * answer what a fresh build would? Observation entered on the first call, then a DIFFERENT
	 * observation on the second — the classic stale-evidence trap.
	 */
	@Test
	public void aWarmModelGivenNewEvidenceMustNotRememberTheOld() throws Exception {
		// Fresh build, observe a=50, calculate.
		Model warm = ddModel();
		DataSet w1 = warm.createDataSet("case1");
		w1.setObservationHard(warm.getNetwork("net").getNode("a"), 50d);
		warm.calculate();

		// Same instance, a SECOND case observing a=-50. Does the old case leak in?
		DataSet w2 = warm.createDataSet("case2");
		w2.setObservationHard(warm.getNetwork("net").getNode("a"), -50d);
		warm.calculate();
		String warmSecond = resultDigest(new ArrayList<>(w2.getCalculationResults()));

		// The control: a brand-new model that only ever saw a=-50.
		Model fresh = ddModel();
		DataSet f = fresh.createDataSet("case2");
		f.setObservationHard(fresh.getNetwork("net").getNode("a"), -50d);
		fresh.calculate();
		String freshOnly = resultDigest(new ArrayList<>(f.getCalculationResults()));

		assertEquals(freshOnly, warmSecond,
				"a warm model given new evidence must not remember the old - if this fails, the engine "
				+ "session in agena-engine-server is unsafe and must clear more than the DataSets");
	}

	/** A simulation chain: DD refines its states on every calculation, which is the risky case. */
	private static Model ddModel() throws Exception {
		Model model = Model.createModel();
		Network net = model.createNetwork("net");
		Node a = net.createNode("a", Node.Type.ContinuousInterval);
		a.convertToSimulated();
		a.setTableFunction("Normal(0,100)");
		Node b = net.createNode("b", Node.Type.ContinuousInterval);
		b.convertToSimulated();
		Node.linkNodes(a, b);
		b.setTableFunction("Normal(a,10)");
		return model;
	}

	private static List<CalculationResult> calcAndRead(Model m) throws Exception {
		DataSet ds = m.getDataSetList().isEmpty() ? m.createDataSet("case") : m.getDataSetList().get(0);
		m.calculate();
		return new ArrayList<>(ds.getCalculationResults());
	}

	/**
	 * Node -> its mean and state count, rounded, as one comparable string. SORTED: results come back in
	 * whatever order the networks finished, which varies run to run and is not a difference in the answer.
	 */
	private static String resultDigest(List<CalculationResult> results) {
		List<String> lines = new ArrayList<>();
		for (CalculationResult cr : results) {
			StringBuilder sb = new StringBuilder();
			JSONObject j = cr.toJson();
			JSONArray vals = j.optJSONArray("resultValues");
			sb.append(j.optString("node")).append('[').append(vals == null ? -1 : vals.length()).append(']');
			JSONObject summary = j.optJSONObject("summaryStatistics");
			if (summary != null) {
				sb.append("mean=").append(String.format("%.6f", summary.optDouble("mean", Double.NaN)));
			}
			lines.add(sb.toString());
		}
		java.util.Collections.sort(lines);
		return String.join(" ", lines);
	}
}
