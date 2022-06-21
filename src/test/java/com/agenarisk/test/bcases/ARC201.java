package com.agenarisk.test.bcases;

import com.agenarisk.api.io.JSONAdapter;
import com.agenarisk.api.model.DataSet;
import com.agenarisk.api.model.Model;
import com.agenarisk.test.TestHelper;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Eugene Dementiev
 */
public class ARC201 {
	
	@Test
	public void test() throws Exception {
		Model model1 = TestHelper.loadModelFromResource("/misc/arc201.json");
		JSONObject export = model1.export(Model.ExportFlag.KEEP_OBSERVATIONS);
		Files.write(Paths.get("x:\\asd.json"), export.toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		DataSet ds1 = model1.getDataSetList().get(0);
		model1.calculate(null, null, Model.CalculationFlag.CALCULATE_LOGPE);
		
		Map<String, Double> logPeOriginal = ds1.getLogicScenario().getLogPeMap();
		Assertions.assertFalse(logPeOriginal.isEmpty());
		
		model1.getNetworks().values().forEach(net -> {
			Assertions.assertTrue(logPeOriginal.containsKey(net.getId()));
			Assertions.assertEquals(logPeOriginal.get(net.getId()), ds1.getLogProbabilityOfEvidence(net));
			Assertions.assertEquals(ds1.getLogicScenario().getProbabilityEvidence(net.getId()), ds1.getProbabilityOfEvidence(net));
		});
		
		String outString = JSONAdapter.toJSONObject(model1.getLogicModel()).toString();
		
		Model model2 = Model.createModel(new JSONObject(outString));
		DataSet ds2 = model2.getDataSetList().get(0);
		Assertions.assertEquals(ds1.getLogicScenario().getLogPeMap().size(), ds2.getLogicScenario().getLogPeMap().size());
		
		model2.getNetworks().values().forEach(net -> {
			Assertions.assertTrue(logPeOriginal.containsKey(net.getId()));
			Assertions.assertEquals(logPeOriginal.get(net.getId()), ds2.getLogProbabilityOfEvidence(net));
			Assertions.assertEquals(ds1.getLogicScenario().getProbabilityEvidence(net.getId()), ds2.getProbabilityOfEvidence(net));
		});
	}
}

