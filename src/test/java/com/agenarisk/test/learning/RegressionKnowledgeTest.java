package com.agenarisk.test.learning;

import com.agenarisk.learning.structure.regressiondiscovery.CandidateGraph;
import com.agenarisk.learning.structure.regressiondiscovery.RegressionKnowledge;
import java.util.Arrays;
import java.util.LinkedHashSet;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RegressionKnowledgeTest {

	@Test
	public void testRequiredEdgeCannotBeRemoved() {
		RegressionKnowledge knowledge = new RegressionKnowledge();
		knowledge.requireEdge("a", "b");
		Assertions.assertTrue(knowledge.isEdgeRequired("a", "b"));

		CandidateGraph graph = new CandidateGraph(new LinkedHashSet<>(Arrays.asList("a", "b")));
		graph.addEdge("a", "b");
		Assertions.assertFalse(knowledge.isMoveLegal(graph, "a", "b", CandidateGraph.MoveType.REMOVE_EDGE));
		Assertions.assertFalse(knowledge.isMoveLegal(graph, "a", "b", CandidateGraph.MoveType.REVERSE_EDGE));
	}

	@Test
	public void testForbidDirectedOnlyBlocksOneDirection() {
		RegressionKnowledge knowledge = new RegressionKnowledge();
		knowledge.forbidDirectedEdge("a", "b");
		Assertions.assertFalse(knowledge.isEdgeAllowed("a", "b"));
		Assertions.assertTrue(knowledge.isEdgeAllowed("b", "a"));
	}

	@Test
	public void testForbidUndirectedBlocksBothDirections() {
		RegressionKnowledge knowledge = new RegressionKnowledge();
		knowledge.forbidEdge("a", "b");
		Assertions.assertFalse(knowledge.isEdgeAllowed("a", "b"));
		Assertions.assertFalse(knowledge.isEdgeAllowed("b", "a"));
	}

	@Test
	public void testTemporalTierBlocksBackwardEdgeOnly() {
		RegressionKnowledge knowledge = new RegressionKnowledge();
		knowledge.addTemporalTier(Arrays.asList("early"));
		knowledge.addTemporalTier(Arrays.asList("late"));

		Assertions.assertTrue(knowledge.isEdgeAllowed("early", "late"));
		Assertions.assertFalse(knowledge.isEdgeAllowed("late", "early"));
	}

	@Test
	public void testProhibitSameTierEdgesBlocksWithinTier() {
		RegressionKnowledge knowledge = new RegressionKnowledge();
		knowledge.addTemporalTier(Arrays.asList("a", "b"));
		knowledge.setProhibitSameTierEdges(true);

		Assertions.assertFalse(knowledge.isEdgeAllowed("a", "b"));
		Assertions.assertFalse(knowledge.isEdgeAllowed("b", "a"));
	}

	@Test
	public void testSameTierAllowedWhenNotProhibited() {
		RegressionKnowledge knowledge = new RegressionKnowledge();
		knowledge.addTemporalTier(Arrays.asList("a", "b"));
		// prohibitSameTierEdges defaults to false

		Assertions.assertTrue(knowledge.isEdgeAllowed("a", "b"));
	}

	@Test
	public void testMaxParentsOverrideFallsBackToGlobalDefault() {
		RegressionKnowledge knowledge = new RegressionKnowledge();
		knowledge.setMaxParents("a", 2);

		Assertions.assertEquals(2, knowledge.maxParentsFor("a", 5));
		Assertions.assertEquals(5, knowledge.maxParentsFor("b", 5));
	}

	@Test
	public void testRegressionRoleAndIndicatorEncodingFlags() {
		RegressionKnowledge knowledge = new RegressionKnowledge();
		knowledge.forceRegressionRole("a");
		knowledge.forbidRegressionRole("b");
		knowledge.forbidIndicatorEncoding("c");

		Assertions.assertTrue(knowledge.mustUseRegressionRole("a"));
		Assertions.assertFalse(knowledge.mustUseRegressionRole("b"));
		Assertions.assertTrue(knowledge.mustNotUseRegressionRole("b"));
		Assertions.assertTrue(knowledge.isIndicatorEncodingForbidden("c"));
		Assertions.assertFalse(knowledge.isIndicatorEncodingForbidden("a"));
	}

	@Test
	public void testFromJsonParsesAllConstraintTypes() {
		JSONObject jKnowledge = new JSONObject(
				"{"
				+ "\"connectionsDirected\": [{\"parent\": \"a\", \"child\": \"b\"}],"
				+ "\"connectionsForbidden\": [{\"a\": \"c\", \"b\": \"d\"}],"
				+ "\"connectionsForbiddenDirected\": [{\"parent\": \"e\", \"child\": \"f\"}],"
				+ "\"connectionsTemporal\": [[\"g\"], [\"h\"]],"
				+ "\"prohibitConnectionsSameTemporalTier\": true,"
				+ "\"forceRegressionRole\": [\"i\"],"
				+ "\"forbidRegressionRole\": [\"j\"],"
				+ "\"forbidIndicatorEncoding\": [\"k\"],"
				+ "\"maxParentsOverrides\": {\"l\": 3}"
				+ "}");

		RegressionKnowledge knowledge = RegressionKnowledge.fromJson(jKnowledge);

		Assertions.assertTrue(knowledge.isEdgeRequired("a", "b"));
		Assertions.assertFalse(knowledge.isEdgeAllowed("c", "d"));
		Assertions.assertFalse(knowledge.isEdgeAllowed("d", "c"));
		Assertions.assertFalse(knowledge.isEdgeAllowed("e", "f"));
		Assertions.assertTrue(knowledge.isEdgeAllowed("f", "e"));
		Assertions.assertFalse(knowledge.isEdgeAllowed("h", "g")); // h is a later tier than g
		Assertions.assertTrue(knowledge.mustUseRegressionRole("i"));
		Assertions.assertTrue(knowledge.mustNotUseRegressionRole("j"));
		Assertions.assertTrue(knowledge.isIndicatorEncodingForbidden("k"));
		Assertions.assertEquals(3, knowledge.maxParentsFor("l", 99));
	}
}
