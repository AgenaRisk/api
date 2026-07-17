package com.agenarisk.test.learning;

import com.agenarisk.learning.structure.regressiondiscovery.CandidateGraph;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CandidateGraphTest {

	private Set<String> nodes(String... ids) {
		return new LinkedHashSet<>(java.util.Arrays.asList(ids));
	}

	@Test
	public void testAddRemoveHasEdge() {
		CandidateGraph graph = new CandidateGraph(nodes("a", "b"));
		Assertions.assertFalse(graph.hasEdge("a", "b"));
		graph.addEdge("a", "b");
		Assertions.assertTrue(graph.hasEdge("a", "b"));
		Assertions.assertEquals(1, graph.getParents("b").size());
		graph.removeEdge("a", "b");
		Assertions.assertFalse(graph.hasEdge("a", "b"));
	}

	@Test
	public void testDirectCycleDetected() {
		CandidateGraph graph = new CandidateGraph(nodes("a", "b"));
		graph.addEdge("a", "b");
		// a -> b already exists; adding b -> a would create a 2-cycle
		Assertions.assertTrue(graph.wouldCreateCycle("b", "a"));
		// re-adding a -> b (already present) is not itself considered here - test the genuinely new reverse edge only
	}

	@Test
	public void testMultiHopCycleDetected() {
		CandidateGraph graph = new CandidateGraph(nodes("a", "b", "c"));
		graph.addEdge("a", "b");
		graph.addEdge("b", "c");
		// a -> b -> c already exists; c -> a would close the cycle
		Assertions.assertTrue(graph.wouldCreateCycle("c", "a"));
	}

	@Test
	public void testConvergingStructureIsNotFalsePositiveCycle() {
		// Diamond: a -> b, a -> c, b -> d, c -> d. No cycle anywhere.
		CandidateGraph graph = new CandidateGraph(nodes("a", "b", "c", "d"));
		graph.addEdge("a", "b");
		graph.addEdge("a", "c");
		graph.addEdge("b", "d");
		graph.addEdge("c", "d");
		Assertions.assertFalse(graph.wouldCreateCycle("a", "d")); // a is already an ancestor of d via two paths but adding a->d directly is still acyclic
		Assertions.assertTrue(graph.wouldCreateCycle("d", "a")); // d -> a WOULD create a cycle (a is an ancestor of d)
	}

	@Test
	public void testSelfLoopIsCycle() {
		CandidateGraph graph = new CandidateGraph(nodes("a"));
		Assertions.assertTrue(graph.wouldCreateCycle("a", "a"));
	}

	@Test
	public void testReverseEdgeHelper() {
		CandidateGraph graph = new CandidateGraph(nodes("a", "b"));
		graph.addEdge("a", "b");
		graph.reverseEdge("a", "b");
		Assertions.assertFalse(graph.hasEdge("a", "b"));
		Assertions.assertTrue(graph.hasEdge("b", "a"));
	}

	@Test
	public void testCopyConstructorIsIndependent() {
		CandidateGraph graph = new CandidateGraph(nodes("a", "b"));
		graph.addEdge("a", "b");
		CandidateGraph copy = new CandidateGraph(graph);
		copy.removeEdge("a", "b");
		Assertions.assertTrue(graph.hasEdge("a", "b")); // original unaffected
		Assertions.assertFalse(copy.hasEdge("a", "b"));
	}
}
