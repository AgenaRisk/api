package com.agenarisk.learning.structure.execution.graph;

import com.agenarisk.learning.structure.exception.StructureLearningException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * A node with an unrecognized {@code type} used to be silently dropped (along with every node downstream of it)
 * instead of failing - {@link WorkflowGraph#fromJson} must now reject the whole graph with a clear, actionable
 * message instead, since a typo'd/outdated type string should never result in a partial, silently-incomplete run.
 */
public class WorkflowGraphTest {

	private static JSONObject node(String label, String type) {
		return new JSONObject().put("label", label).put("type", type).put("options", new JSONObject());
	}

	@Test
	public void testUnrecognizedNodeTypeThrowsClearError() {
		JSONObject jGraph = new JSONObject().put("nodes", new JSONArray()
				.put(node("ds1", "dataSource").put("options", new JSONObject().put("path", "data.csv")))
				.put(node("regr1", "regressionTableLearning")));

		StructureLearningException ex = Assertions.assertThrows(StructureLearningException.class,
				() -> WorkflowGraph.fromJson(jGraph));

		Assertions.assertTrue(ex.getMessage().contains("regr1"), "Message should name the offending node: " + ex.getMessage());
		Assertions.assertTrue(ex.getMessage().contains("regressionTableLearning"), "Message should name the unrecognized type: " + ex.getMessage());
		Assertions.assertTrue(ex.getMessage().contains("regressionParameterLearning"), "Message should list valid types: " + ex.getMessage());
	}

	@Test
	public void testMultipleUnrecognizedNodeTypesAreAllReportedTogether() {
		JSONObject jGraph = new JSONObject().put("nodes", new JSONArray()
				.put(node("bad1", "fooBar"))
				.put(node("bad2", "bazQux")));

		StructureLearningException ex = Assertions.assertThrows(StructureLearningException.class,
				() -> WorkflowGraph.fromJson(jGraph));

		Assertions.assertTrue(ex.getMessage().contains("bad1") && ex.getMessage().contains("fooBar"), ex.getMessage());
		Assertions.assertTrue(ex.getMessage().contains("bad2") && ex.getMessage().contains("bazQux"), ex.getMessage());
	}

	@Test
	public void testRecognizedNodeTypeIsUnaffected() {
		JSONObject jGraph = new JSONObject().put("nodes", new JSONArray()
				.put(node("ds1", "dataSource").put("options", new JSONObject().put("path", "data.csv"))));

		WorkflowGraph graph = WorkflowGraph.fromJson(jGraph);

		Assertions.assertNotNull(graph.getNode("ds1"));
	}
}
