package com.agenarisk.test.learning;

import com.agenarisk.learning.structure.regression.RegressionDataset;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.co.agena.minerva.util.EM.Data;

public class RegressionDatasetMixedCategoricalTest {

	private Data writeAndLoad(String csv) throws IOException {
		Path tempFile = Files.createTempFile("regression-dataset-mixed-test-", ".csv");
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
	public void testColumnLayoutIsContinuousThenDummy() throws IOException {
		String csv = "y,x1,cat\n"
				+ "True,1.5,A\n"
				+ "False,2.5,B\n";

		Data data = writeAndLoad(csv);
		RegressionDataset dataset = new RegressionDataset(data);

		RegressionDataset.MixedCategoricalSelection selection = dataset.selectMixedCategoricalRows(
				"y", Arrays.asList("False", "True"),
				Arrays.asList("x1"),
				Arrays.asList("cat"), Collections.singletonList(Arrays.asList("A", "B")));

		Assertions.assertEquals(2, selection.getN());
		// Row 0: x1=1.5, cat=A (reference state, no dummy set)
		Assertions.assertArrayEquals(new double[]{1.5, 0.0}, selection.getX()[0], 1e-9);
		Assertions.assertEquals(1, selection.getY()[0]); // "True" -> index 1
		// Row 1: x1=2.5, cat=B (non-reference, dummy=1)
		Assertions.assertArrayEquals(new double[]{2.5, 1.0}, selection.getX()[1], 1e-9);
		Assertions.assertEquals(0, selection.getY()[1]); // "False" -> index 0
	}

	@Test
	public void testRowsExcludedWhenAnyRequiredColumnMissingOrUnrecognised() throws IOException {
		String csv = "y,x1,cat\n"
				+ "True,1.5,A\n"
				+ "True,NA,A\n" // x1 missing
				+ "NA,2.5,A\n" // target missing
				+ "True,2.5,Z\n" // unrecognised categorical value
				+ "True,3.5,B\n";

		Data data = writeAndLoad(csv);
		RegressionDataset dataset = new RegressionDataset(data);

		RegressionDataset.MixedCategoricalSelection selection = dataset.selectMixedCategoricalRows(
				"y", Arrays.asList("False", "True"),
				Arrays.asList("x1"),
				Arrays.asList("cat"), Collections.singletonList(Arrays.asList("A", "B")));

		Assertions.assertEquals(2, selection.getN()); // only rows 0 and 4 qualify
	}

	@Test
	public void testNoCategoricalParentsIsPureContinuousDesignMatrix() throws IOException {
		String csv = "y,x1\nTrue,1.0\nFalse,2.0\n";
		Data data = writeAndLoad(csv);
		RegressionDataset dataset = new RegressionDataset(data);

		RegressionDataset.MixedCategoricalSelection selection = dataset.selectMixedCategoricalRows(
				"y", Arrays.asList("False", "True"),
				Arrays.asList("x1"),
				Collections.emptyList(), Collections.emptyList());

		Assertions.assertEquals(2, selection.getN());
		Assertions.assertEquals(1, selection.getX()[0].length);
	}
}
