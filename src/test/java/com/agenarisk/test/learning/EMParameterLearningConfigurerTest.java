package com.agenarisk.test.learning;

import com.agenarisk.learning.structure.config.Config;
import com.agenarisk.learning.structure.config.EMParameterLearningConfigurer;
import com.agenarisk.learning.structure.exception.StructureLearningException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.util.Environment;

/**
 * {@code knowledge.skipNodes}/{@code knowledge.nodeDataWeightsCustom} used to be parsed with raw
 * {@code JSONArray.getString}/{@code getDouble} calls, so a malformed entry (wrong shape, wrong element type)
 * surfaced as an unhelpful raw org.json message with no indication of which field or index was at fault. This
 * confirms {@link EMParameterLearningConfigurer#configureFromJson} now rejects those shapes with a specific,
 * actionable message instead.
 */
public class EMParameterLearningConfigurerTest {

	{
		Environment.initialize();
	}

	private Path dataPath;

	@BeforeEach
	public void setUp() throws IOException {
		dataPath = Files.createTempFile("EMParameterLearningConfigurerTest", ".csv");
		Files.write(dataPath, "a,b\n1,2\n".getBytes());
	}

	@AfterEach
	public void tearDown() throws IOException {
		Files.deleteIfExists(dataPath);
	}

	private JSONObject baseConfig() {
		return new JSONObject().put("parameters", new JSONObject().put("dataPath", dataPath.toString()));
	}

	@Test
	public void testSkipNodesRejectsNonStringEntry() {
		JSONObject jConfig = baseConfig()
				.put("knowledge", new JSONObject().put("skipNodes", new JSONArray().put("a").put(42)));

		StructureLearningException ex = Assertions.assertThrows(StructureLearningException.class,
				() -> new EMParameterLearningConfigurer(Config.reset()).configureFromJson(jConfig));

		Assertions.assertTrue(ex.getMessage().contains("skipNodes[1]"), ex.getMessage());
	}

	@Test
	public void testNodeDataWeightsCustomRejectsWrongArity() {
		JSONObject jConfig = baseConfig()
				.put("knowledge", new JSONObject().put("nodeDataWeightsCustom",
						new JSONArray().put(new JSONArray().put("a").put(1.5).put("extra"))));

		StructureLearningException ex = Assertions.assertThrows(StructureLearningException.class,
				() -> new EMParameterLearningConfigurer(Config.reset()).configureFromJson(jConfig));

		Assertions.assertTrue(ex.getMessage().contains("nodeDataWeightsCustom[0]"), ex.getMessage());
	}

	@Test
	public void testNodeDataWeightsCustomRejectsNonNumericWeight() {
		JSONObject jConfig = baseConfig()
				.put("knowledge", new JSONObject().put("nodeDataWeightsCustom",
						new JSONArray().put(new JSONArray().put("a").put("not-a-number"))));

		StructureLearningException ex = Assertions.assertThrows(StructureLearningException.class,
				() -> new EMParameterLearningConfigurer(Config.reset()).configureFromJson(jConfig));

		Assertions.assertTrue(ex.getMessage().contains("nodeDataWeightsCustom[0]"), ex.getMessage());
	}

	@Test
	public void testWellFormedKnowledgeIsAccepted() {
		JSONObject jConfig = baseConfig()
				.put("knowledge", new JSONObject()
						.put("skipNodes", new JSONArray().put("a"))
						.put("nodeDataWeightsCustom", new JSONArray().put(new JSONArray().put("b").put(2.5))));

		EMParameterLearningConfigurer configurer = new EMParameterLearningConfigurer(Config.reset()).configureFromJson(jConfig);

		Assertions.assertEquals(0d, configurer.getNodeDataWeightsCustom().get("a"));
		Assertions.assertEquals(2.5d, configurer.getNodeDataWeightsCustom().get("b"));
	}
}
