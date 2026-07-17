package com.agenarisk.test.learning;

import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import com.agenarisk.learning.structure.regressiondiscovery.ShellModelBuilder;
import com.agenarisk.learning.structure.regressiondiscovery.VariableDeclaration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.util.Environment;

/**
 * Confirms {@link ShellModelBuilder} builds a fully-typed, link-free shell model directly from a CSV header and a
 * (possibly partial) set of {@link VariableDeclaration}s - no pre-existing {@code .cmpx} model involved anywhere,
 * the whole point being that {@code regressionStructureDiscovery} never needs one.
 */
public class ShellModelBuilderTest {

	{
		Environment.initialize();
	}

	@Test
	public void testExplicitDeclarationsAreRespectedIncludingRankedOrdering() throws Exception {
		Path tempDir = Files.createTempDirectory("shell-model-builder-test");
		tempDir.toFile().deleteOnExit();

		List<String> headers = Arrays.asList("rank", "cat", "x");
		Path dataPath = tempDir.resolve("data.csv");
		Files.write(dataPath, "rank,cat,x\nLow,True,1.0\nHigh,False,2.0\n".getBytes(StandardCharsets.UTF_8));

		Map<String, VariableDeclaration> declarations = new HashMap<>();
		declarations.put("rank", VariableDeclaration.fromJson(new JSONObject()
				.put("type", "Ranked")
				.put("states", new JSONArray(Arrays.asList("Low", "Medium", "High")))));
		declarations.put("cat", VariableDeclaration.fromJson(new JSONObject().put("type", "Boolean")));
		declarations.put("x", VariableDeclaration.fromJson(new JSONObject()
				.put("type", "ContinuousInterval")
				.put("simulated", true)));

		Model model = ShellModelBuilder.build(headers, dataPath, declarations, "");
		Network network = model.getNetworkList().get(0);

		Node rankNode = network.getNode("rank");
		Assertions.assertEquals(Node.Type.Ranked, rankNode.getType());
		Assertions.assertEquals(3, rankNode.getStates().size());
		Assertions.assertEquals("Low", rankNode.getStates().get(0).getLabel());
		Assertions.assertEquals("Medium", rankNode.getStates().get(1).getLabel());
		Assertions.assertEquals("High", rankNode.getStates().get(2).getLabel());

		Node catNode = network.getNode("cat");
		Assertions.assertEquals(Node.Type.Boolean, catNode.getType());

		Node xNode = network.getNode("x");
		Assertions.assertEquals(Node.Type.ContinuousInterval, xNode.getType());
		Assertions.assertTrue(xNode.isSimulated());

		Assertions.assertTrue(network.getNode("rank").getLinksIn().isEmpty());
		Assertions.assertTrue(network.getNode("rank").getLinksOut().isEmpty());
	}

	@Test
	public void testUndeclaredNumericColumnDefaultsToSimulatedContinuousInterval() throws Exception {
		Path tempDir = Files.createTempDirectory("shell-model-builder-test-numeric");
		tempDir.toFile().deleteOnExit();

		List<String> headers = Arrays.asList("x1", "x2");
		Path dataPath = tempDir.resolve("data.csv");
		Files.write(dataPath, "x1,x2\n1.5,2.5\n3.5,4.5\n5.5,6.5\n".getBytes(StandardCharsets.UTF_8));

		Model model = ShellModelBuilder.build(headers, dataPath, new HashMap<>(), "");
		Network network = model.getNetworkList().get(0);

		Node x1Node = network.getNode("x1");
		Assertions.assertEquals(Node.Type.ContinuousInterval, x1Node.getType());
		Assertions.assertTrue(x1Node.isSimulated());

		Node x2Node = network.getNode("x2");
		Assertions.assertEquals(Node.Type.ContinuousInterval, x2Node.getType());
		Assertions.assertTrue(x2Node.isSimulated());
	}

	@Test
	public void testUndeclaredNonNumericColumnGetsAutoDetectedLabelledStates() throws Exception {
		Path tempDir = Files.createTempDirectory("shell-model-builder-test-labelled");
		tempDir.toFile().deleteOnExit();

		List<String> headers = Arrays.asList("colour");
		Path dataPath = tempDir.resolve("data.csv");
		Files.write(dataPath, "colour\nRed\nGreen\nBlue\nRed\n".getBytes(StandardCharsets.UTF_8));

		Model model = ShellModelBuilder.build(headers, dataPath, new HashMap<>(), "");
		Network network = model.getNetworkList().get(0);

		Node colourNode = network.getNode("colour");
		Assertions.assertEquals(Node.Type.Labelled, colourNode.getType());
		Assertions.assertFalse(colourNode.isSimulated());

		List<String> stateLabels = new java.util.ArrayList<>();
		colourNode.getStates().forEach(s -> stateLabels.add(s.getLabel()));
		Assertions.assertEquals(3, stateLabels.size());
		Assertions.assertTrue(stateLabels.containsAll(Arrays.asList("Red", "Green", "Blue")));
	}

	@Test
	public void testMixedDeclaredAndUndeclaredColumnsInSameModel() throws Exception {
		Path tempDir = Files.createTempDirectory("shell-model-builder-test-mixed");
		tempDir.toFile().deleteOnExit();

		List<String> headers = Arrays.asList("rank", "x1", "colour");
		Path dataPath = tempDir.resolve("data.csv");
		Files.write(dataPath, "rank,x1,colour\nLow,1.0,Red\nHigh,2.0,Blue\n".getBytes(StandardCharsets.UTF_8));

		Map<String, VariableDeclaration> declarations = new HashMap<>();
		declarations.put("rank", VariableDeclaration.fromJson(new JSONObject()
				.put("type", "Ranked")
				.put("states", new JSONArray(Arrays.asList("Low", "High")))));

		Model model = ShellModelBuilder.build(headers, dataPath, declarations, "");
		Network network = model.getNetworkList().get(0);

		Assertions.assertEquals(Node.Type.Ranked, network.getNode("rank").getType());
		Assertions.assertEquals(2, network.getNode("rank").getStates().size());

		Assertions.assertEquals(Node.Type.ContinuousInterval, network.getNode("x1").getType());
		Assertions.assertTrue(network.getNode("x1").isSimulated());

		Assertions.assertEquals(Node.Type.Labelled, network.getNode("colour").getType());
		Assertions.assertEquals(2, network.getNode("colour").getStates().size());
	}

	@Test
	public void testUndeclaredColumnsWithMissingValuesAreNotMisclassified() throws Exception {
		Path tempDir = Files.createTempDirectory("shell-model-builder-test-missing");
		tempDir.toFile().deleteOnExit();

		// x1 has a blank cell - without missing-value handling, Double.parseDouble("") throws and the column gets
		// wrongly demoted to Labelled; colour also has a blank cell, which must not become a bogus "" state.
		List<String> headers = Arrays.asList("x1", "colour");
		Path dataPath = tempDir.resolve("data.csv");
		Files.write(dataPath, "x1,colour\n1.0,Red\n,Blue\n3.0,\n".getBytes(StandardCharsets.UTF_8));

		Model model = ShellModelBuilder.build(headers, dataPath, new HashMap<>(), "");
		Network network = model.getNetworkList().get(0);

		Node x1Node = network.getNode("x1");
		Assertions.assertEquals(Node.Type.ContinuousInterval, x1Node.getType());
		Assertions.assertTrue(x1Node.isSimulated());

		Node colourNode = network.getNode("colour");
		Assertions.assertEquals(Node.Type.Labelled, colourNode.getType());
		List<String> stateLabels = new java.util.ArrayList<>();
		colourNode.getStates().forEach(s -> stateLabels.add(s.getLabel()));
		Assertions.assertEquals(2, stateLabels.size());
		Assertions.assertTrue(stateLabels.containsAll(Arrays.asList("Red", "Blue")));
	}
}
