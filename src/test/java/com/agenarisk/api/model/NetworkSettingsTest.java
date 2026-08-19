package com.agenarisk.api.model;

import com.agenarisk.api.io.JSONAdapter;
import com.agenarisk.api.io.XMLAdapter;
import com.agenarisk.test.TestHelper;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.model.extendedbn.ExtendedBN;

/**
 * Per-network simulation settings: a network may override any of the model-level dynamic
 * discretisation settings, and a network that overrides nothing must behave (and serialise)
 * exactly as it did before per-network settings existed.
 *
 * @author Claude
 */
public class NetworkSettingsTest {

	/**
	 * A model with no network settings block anywhere: every network inherits, and the saved JSON
	 * gains no new keys. This is the legacy-compatibility guarantee.
	 */
	@Test
	public void testAbsentBlockMeansInherit() throws Exception {
		Model model = TestHelper.loadModelFromResource("/common/Biased Coin Flip Experiment.cmpx");
		Network network = model.getNetworkList().get(0);
		ExtendedBN ebn = network.getLogicNetwork();

		assertFalse(ebn.hasSimulationSettingOverrides());
		assertNull(network.getSettingsJson());

		uk.co.agena.minerva.model.Model logicModel = model.getLogicModel();
		assertEquals(logicModel.getSimulationEntropyConvergenceTolerance(),
				logicModel.getSimulationEntropyConvergenceTolerance(ebn));
		assertEquals(logicModel.getSimulationNoOfIterations(), logicModel.getSimulationNoOfIterations(ebn));
		assertEquals(logicModel.getSimulationEvidenceTolerancePercent(),
				logicModel.getSimulationEvidenceTolerancePercent(ebn));
		assertEquals(logicModel.getSplitMetric(), logicModel.getSplitMetric(ebn));

		JSONObject json = JSONAdapter.toJSONObject(model.getLogicModel());
		JSONObject jsonNetwork = json.getJSONObject(Model.Field.model.toString())
				.getJSONArray(Network.Field.networks.toString()).getJSONObject(0);
		assertFalse(jsonNetwork.has(Settings.Field.settings.toString()));
	}

	/**
	 * Overrides set on a network take precedence over the model settings, and only the fields
	 * actually overridden do so.
	 */
	@Test
	public void testPartialOverrideResolution() throws Exception {
		Model model = TestHelper.loadModelFromResource("/common/Biased Coin Flip Experiment.cmpx");
		Network network = model.getNetworkList().get(0);
		ExtendedBN ebn = network.getLogicNetwork();
		uk.co.agena.minerva.model.Model logicModel = model.getLogicModel();

		logicModel.setSimulationEntropyConvergenceTolerance(0.001);
		logicModel.setSimulationNoOfIterations(50);
		logicModel.setSplitMetric(uk.co.agena.minerva.model.Model.SPLIT_METRIC_ENTROPY);

		JSONObject jsonSettings = new JSONObject();
		jsonSettings.put(Settings.Field.convergence.toString(), 1e-5);
		jsonSettings.put(Settings.Field.splitMetric.toString(), uk.co.agena.minerva.model.Model.SPLIT_METRIC_ENTROPY_VARIANCE_LEVERAGE);
		network.setSettings(jsonSettings);

		// Overridden
		assertEquals(1e-5, logicModel.getSimulationEntropyConvergenceTolerance(ebn));
		assertEquals(uk.co.agena.minerva.model.Model.SPLIT_METRIC_ENTROPY_VARIANCE_LEVERAGE, logicModel.getSplitMetric(ebn));
		// Not overridden - still the model value
		assertEquals(50, logicModel.getSimulationNoOfIterations(ebn));
		// And the model settings themselves are untouched
		assertEquals(0.001, logicModel.getSimulationEntropyConvergenceTolerance());
		assertEquals(uk.co.agena.minerva.model.Model.SPLIT_METRIC_ENTROPY, logicModel.getSplitMetric());

		// Null network resolves to the model settings (used by helpers outside a propagation)
		assertEquals(0.001, logicModel.getSimulationEntropyConvergenceTolerance(null));
	}

	/**
	 * The overrides survive a save/load round trip through both CMPX (JSON) and XML.
	 */
	@Test
	public void testRoundTrip() throws Exception {
		Model model = TestHelper.loadModelFromResource("/common/Biased Coin Flip Experiment.cmpx");
		Network network = model.getNetworkList().get(0);
		String networkId = network.getId();

		JSONObject jsonSettings = new JSONObject();
		jsonSettings.put(Settings.Field.convergence.toString(), 1e-5);
		jsonSettings.put(Settings.Field.iterations.toString(), 25);
		jsonSettings.put(Settings.Field.tolerance.toString(), 2.5);
		jsonSettings.put(Settings.Field.splitMetric.toString(), uk.co.agena.minerva.model.Model.SPLIT_METRIC_ENTROPY_VARIANCE_LEVERAGE);
		network.setSettings(jsonSettings);

		JSONObject json = JSONAdapter.toJSONObject(model.getLogicModel());

		// JSON round trip
		assertNetworkSettings(Model.createModel(json).getNetwork(networkId));

		// XML round trip - the settings block is a plain nested object, so the generic
		// XMLAdapter carries it without any wrapper configuration.
		String xml = XMLAdapter.toXMLString(json);
		assertTrue(xml.contains("<convergence>"));
		assertNetworkSettings(Model.createModel(XMLAdapter.xmlToJson(xml)).getNetwork(networkId));
	}

	private void assertNetworkSettings(Network network) {
		assertNotNull(network);
		ExtendedBN ebn = network.getLogicNetwork();
		assertTrue(ebn.hasSimulationSettingOverrides());
		assertEquals(1e-5, ebn.getSimulationEntropyConvergenceToleranceOverride().doubleValue());
		assertEquals(25, ebn.getSimulationNoOfIterationsOverride().intValue());
		assertEquals(2.5, ebn.getSimulationEvidenceTolerancePercentOverride().doubleValue());
		assertEquals(uk.co.agena.minerva.model.Model.SPLIT_METRIC_ENTROPY_VARIANCE_LEVERAGE, ebn.getSplitMetricOverride());
	}

	/**
	 * Clearing the overrides puts the network back to inheriting, and drops the block from the file.
	 */
	@Test
	public void testClearOverrides() throws Exception {
		Model model = TestHelper.loadModelFromResource("/common/Biased Coin Flip Experiment.cmpx");
		Network network = model.getNetworkList().get(0);

		JSONObject jsonSettings = new JSONObject();
		jsonSettings.put(Settings.Field.iterations.toString(), 10);
		network.setSettings(jsonSettings);
		assertNotNull(network.getSettingsJson());

		network.setSettings(null);
		assertNull(network.getSettingsJson());
		assertFalse(network.getLogicNetwork().hasSimulationSettingOverrides());
	}
}
