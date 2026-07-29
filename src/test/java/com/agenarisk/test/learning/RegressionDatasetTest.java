package com.agenarisk.test.learning;

import com.agenarisk.learning.structure.regression.PartitionEnumerator;
import com.agenarisk.learning.structure.regression.RegressionDataset;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.util.EM.Data;

public class RegressionDatasetTest {

	private Data writeAndLoad(String csv) throws IOException {
		Path tempFile = Files.createTempFile("regression-dataset-test-", ".csv");
		tempFile.toFile().deleteOnExit();
		Files.write(tempFile, csv.getBytes(StandardCharsets.UTF_8));
		try {
			return new Data(tempFile.toString(), "NA", ",");
		}
		catch (Exception ex){
			throw new RuntimeException(ex);
		}
	}

	@Test
	public void testSelectRowsSkipsMissingIndependentlyPerNode() throws IOException {
		String csv = "y,x1,x2,cat\n"
				+ "10,1,2,A\n"
				+ "NA,3,4,A\n" // y missing - excluded from y~x1,x2 fit
				+ "20,NA,5,A\n" // x1 missing - excluded from y~x1,x2 fit but would be fine for y~x2 alone
				+ "30,5,6,B\n"
				+ "40,7,8,A\n";

		Data data = writeAndLoad(csv);
		RegressionDataset dataset = new RegressionDataset(data);

		// Fit y ~ x1 + x2, no partition filter: only rows 1, 4, 5 (0-indexed data rows) qualify
		RegressionDataset.Selection selAll = dataset.selectRows("y", Arrays.asList("x1", "x2"), null);
		Assertions.assertEquals(3, selAll.getN());

		// Fit y ~ x2 only (drop x1 from the regressor set): row with x1=NA now qualifies too
		RegressionDataset.Selection selX2Only = dataset.selectRows("y", Arrays.asList("x2"), null);
		Assertions.assertEquals(4, selX2Only.getN());
	}

	@Test
	public void testSelectRowsFiltersByPartitionCombination() throws IOException {
		String csv = "y,x1,cat\n"
				+ "10,1,A\n"
				+ "20,2,A\n"
				+ "30,3,B\n"
				+ "40,4,B\n";

		Data data = writeAndLoad(csv);
		RegressionDataset dataset = new RegressionDataset(data);

		Map<String, String> stateMap = new LinkedHashMap<>();
		stateMap.put("cat", "A");
		PartitionEnumerator.Combination combinationA = combinationOf(stateMap);

		RegressionDataset.Selection selA = dataset.selectRows("y", Arrays.asList("x1"), combinationA);
		Assertions.assertEquals(2, selA.getN());
		Assertions.assertEquals(1.0, selA.getX()[0][0]);
		Assertions.assertEquals(2.0, selA.getX()[1][0]);

		stateMap.put("cat", "B");
		PartitionEnumerator.Combination combinationB = combinationOf(stateMap);
		RegressionDataset.Selection selB = dataset.selectRows("y", Arrays.asList("x1"), combinationB);
		Assertions.assertEquals(2, selB.getN());
	}

	@Test
	public void testMissingTargetColumnGivesEmptySelection() throws IOException {
		String csv = "x1,x2\n1,2\n3,4\n";
		Data data = writeAndLoad(csv);
		RegressionDataset dataset = new RegressionDataset(data);

		RegressionDataset.Selection sel = dataset.selectRows("y", Arrays.asList("x1"), null);
		Assertions.assertEquals(0, sel.getN());
	}

	private PartitionEnumerator.Combination combinationOf(Map<String, String> statesByNodeId) {
		return PartitionEnumerator.Combination.of(statesByNodeId);
	}

	@Test
	public void testSelectCategoricalRowsDummyEncodesMainEffectsOnly() throws IOException {
		String csv = "y,p1,p2\n"
				+ "S0,A,X\n" // p1=A (ref), p2=X (ref) -> all dummies 0
				+ "S1,B,X\n" // p1=B -> dummy1
				+ "S0,C,Y\n" // p1=C -> dummy2, p2=Y -> dummy3
				+ "NA,A,X\n" // target missing -> excluded
				+ "S0,Z,X\n"; // p1 value unrecognised -> excluded

		Data data = writeAndLoad(csv);
		RegressionDataset dataset = new RegressionDataset(data);

		List<String> targetStates = Arrays.asList("S0", "S1");
		List<String> parentIds = Arrays.asList("p1", "p2");
		List<List<String>> parentStates = Arrays.asList(Arrays.asList("A", "B", "C"), Arrays.asList("X", "Y"));

		RegressionDataset.CategoricalSelection sel = dataset.selectCategoricalRows("y", targetStates, parentIds, parentStates);

		Assertions.assertEquals(3, sel.getN());

		// dummy columns: [p1=B, p1=C, p2=Y]
		Assertions.assertArrayEquals(new double[]{0, 0, 0}, sel.getX()[0], 1e-9);
		Assertions.assertEquals(0, sel.getY()[0]); // S0

		Assertions.assertArrayEquals(new double[]{1, 0, 0}, sel.getX()[1], 1e-9);
		Assertions.assertEquals(1, sel.getY()[1]); // S1

		Assertions.assertArrayEquals(new double[]{0, 1, 1}, sel.getX()[2], 1e-9);
		Assertions.assertEquals(0, sel.getY()[2]); // S0
	}

	@Test
	public void testSelectCategoricalRowsWithNoParentsIsInterceptOnly() throws IOException {
		String csv = "y\nS0\nS1\nS0\n";
		Data data = writeAndLoad(csv);
		RegressionDataset dataset = new RegressionDataset(data);

		RegressionDataset.CategoricalSelection sel = dataset.selectCategoricalRows("y", Arrays.asList("S0", "S1"), Arrays.asList(), Arrays.asList());

		Assertions.assertEquals(3, sel.getN());
		Assertions.assertEquals(0, sel.getX()[0].length);
	}
}
