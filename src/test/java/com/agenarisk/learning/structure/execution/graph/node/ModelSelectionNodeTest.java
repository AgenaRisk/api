package com.agenarisk.learning.structure.execution.graph.node;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ModelSelectionNode#resolveDefaultMetric}, the fallback used when a Selection node is
 * connected but never configured (e.g. the user never opened its properties) - it must still pick a sensible
 * metric/direction from whatever the evaluation results actually contain, since that's the one place always
 * guaranteed to reflect what kind of run actually happened, regardless of any UI interaction.
 */
public class ModelSelectionNodeTest {

	private ModelSelectionNode nodeWithNoConfiguredMetric() {
		ModelSelectionNode node = new ModelSelectionNode();
		node.parseOptions(new JSONObject().put("evaluation", "eval1"));
		return node;
	}

	@Test
	public void testInfersCrpsForContinuousTargetKind() {
		ModelSelectionNode node = nodeWithNoConfiguredMetric();
		JSONArray results = new JSONArray()
				.put(new JSONObject().put("modelLabel", "m1").put("targetKind", "continuous").put("crps", 0.5));

		node.resolveDefaultMetric(results);

		Assertions.assertEquals("crps", node.getMetric());
		Assertions.assertEquals(ModelSelectionNode.Direction.minimize, node.getDirection());
	}

	@Test
	public void testInfersAbsoluteErrorForDiscreteTargetKind() {
		ModelSelectionNode node = nodeWithNoConfiguredMetric();
		JSONArray results = new JSONArray()
				.put(new JSONObject().put("modelLabel", "m1").put("targetKind", "discrete").put("absoluteError", 0.2));

		node.resolveDefaultMetric(results);

		Assertions.assertEquals("absoluteError", node.getMetric());
		Assertions.assertEquals(ModelSelectionNode.Direction.minimize, node.getDirection());
	}

	@Test
	public void testInfersBicScoreForStructureEvaluation() {
		ModelSelectionNode node = nodeWithNoConfiguredMetric();
		JSONArray results = new JSONArray()
				.put(new JSONObject().put("modelLabel", "m1").put("bicScore", 123.4));

		node.resolveDefaultMetric(results);

		Assertions.assertEquals("bicScore", node.getMetric());
		Assertions.assertEquals(ModelSelectionNode.Direction.maximize, node.getDirection());
	}

	@Test
	public void testExplicitDirectionIsNotOverridden() {
		ModelSelectionNode node = new ModelSelectionNode();
		node.parseOptions(new JSONObject().put("evaluation", "eval1").put("direction", "maximize"));
		JSONArray results = new JSONArray()
				.put(new JSONObject().put("modelLabel", "m1").put("targetKind", "continuous").put("crps", 0.5));

		node.resolveDefaultMetric(results);

		Assertions.assertEquals("crps", node.getMetric());
		// User explicitly chose maximize - inference must not silently flip it to minimize even though
		// crps is normally lower-is-better.
		Assertions.assertEquals(ModelSelectionNode.Direction.maximize, node.getDirection());
	}

	@Test
	public void testLeavesMetricNullWhenNoUsableEntries() {
		ModelSelectionNode node = nodeWithNoConfiguredMetric();
		JSONArray results = new JSONArray().put(new JSONObject().put("modelLabel", "m1").put("success", false));

		node.resolveDefaultMetric(results);

		Assertions.assertTrue(node.getMetric() == null || node.getMetric().isEmpty());
	}
}
