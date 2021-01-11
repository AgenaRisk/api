package com.agenarisk.api.tools;

import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Eugene Dementiev
 */
public class TestSensitivityAnalyser {
	
	private Model model;
	private JSONObject jsonConfig;
	JSONObject reportSettings;
	
	@BeforeEach
	public void init(){
		model = Model.createModel();
		Network net = model.createNetwork("net");
		Node n1 = net.createNode("n1", Node.Type.ContinuousInterval);
		Node n2 = net.createNode("n2", Node.Type.Boolean);
		
		jsonConfig = new JSONObject();
		jsonConfig.put("network", net.getId());
		jsonConfig.put("targetNode", n1.getId());
		jsonConfig.put("sensitivityNodes", "*");
		
		reportSettings = new JSONObject();
		reportSettings.put("summaryStats", new JSONArray(new String[]{"mean"}));
		
		jsonConfig.put("reportSettings", reportSettings);
	}

	@Test
	public void basic() throws Exception {
		reportSettings.put("sumsLowerPercentileValue", 25);
		reportSettings.put("sumsUpperPercentileValue", 75);
		reportSettings.put("sensLowerPercentileValue", 0);
		reportSettings.put("sensUpperPercentileValue", 100);
		SensitivityAnalyser sa = new SensitivityAnalyser(model, jsonConfig);
		sa.getFullReport();
	}
	
	@Test
	public void fail1() throws Exception {
		reportSettings.put("sumsLowerPercentileValue", -25);
		reportSettings.put("sumsUpperPercentileValue", 75);
		reportSettings.put("sensLowerPercentileValue", 0);
		reportSettings.put("sensUpperPercentileValue", 100);
		Assertions.assertThrows(SensitivityAnalyserException.class, ()->{
			SensitivityAnalyser sa = new SensitivityAnalyser(model, jsonConfig);
			sa.getFullReport();
		});
		
		reportSettings.put("sumsLowerPercentileValue", 125);
		reportSettings.put("sumsUpperPercentileValue", 75);
		reportSettings.put("sensLowerPercentileValue", 0);
		reportSettings.put("sensUpperPercentileValue", 100);
		Assertions.assertThrows(SensitivityAnalyserException.class, ()->{
			SensitivityAnalyser sa = new SensitivityAnalyser(model, jsonConfig);
			sa.getFullReport();
		});
		
		reportSettings.put("sumsLowerPercentileValue", 25);
		reportSettings.put("sumsUpperPercentileValue", -75);
		reportSettings.put("sensLowerPercentileValue", 0);
		reportSettings.put("sensUpperPercentileValue", 100);
		Assertions.assertThrows(SensitivityAnalyserException.class, ()->{
			SensitivityAnalyser sa = new SensitivityAnalyser(model, jsonConfig);
			sa.getFullReport();
		});
		
		reportSettings.put("sumsLowerPercentileValue", 25);
		reportSettings.put("sumsUpperPercentileValue", 175);
		reportSettings.put("sensLowerPercentileValue", 0);
		reportSettings.put("sensUpperPercentileValue", 100);
		Assertions.assertThrows(SensitivityAnalyserException.class, ()->{
			SensitivityAnalyser sa = new SensitivityAnalyser(model, jsonConfig);
			sa.getFullReport();
		});
		
		reportSettings.put("sumsLowerPercentileValue", 25);
		reportSettings.put("sumsUpperPercentileValue", 75);
		reportSettings.put("sensLowerPercentileValue", -10);
		reportSettings.put("sensUpperPercentileValue", 100);
		Assertions.assertThrows(SensitivityAnalyserException.class, ()->{
			SensitivityAnalyser sa = new SensitivityAnalyser(model, jsonConfig);
			sa.getFullReport();
		});
		
		reportSettings.put("sumsLowerPercentileValue", 25);
		reportSettings.put("sumsUpperPercentileValue", 75);
		reportSettings.put("sensLowerPercentileValue", 110);
		reportSettings.put("sensUpperPercentileValue", 100);
		Assertions.assertThrows(SensitivityAnalyserException.class, ()->{
			SensitivityAnalyser sa = new SensitivityAnalyser(model, jsonConfig);
			sa.getFullReport();
		});
		
		reportSettings.put("sumsLowerPercentileValue", 25);
		reportSettings.put("sumsUpperPercentileValue", 75);
		reportSettings.put("sensLowerPercentileValue", 0);
		reportSettings.put("sensUpperPercentileValue", -100);
		Assertions.assertThrows(SensitivityAnalyserException.class, ()->{
			SensitivityAnalyser sa = new SensitivityAnalyser(model, jsonConfig);
			sa.getFullReport();
		});
		
		reportSettings.put("sumsLowerPercentileValue", 25);
		reportSettings.put("sumsUpperPercentileValue", 75);
		reportSettings.put("sensLowerPercentileValue", 0);
		reportSettings.put("sensUpperPercentileValue", 110);
		Assertions.assertThrows(SensitivityAnalyserException.class, ()->{
			SensitivityAnalyser sa = new SensitivityAnalyser(model, jsonConfig);
			sa.getFullReport();
		});
	}
	
	public void fail2(){
		reportSettings.put("sumsLowerPercentileValue", 75);
		reportSettings.put("sumsUpperPercentileValue", 25);
		reportSettings.put("sensLowerPercentileValue", 0);
		reportSettings.put("sensUpperPercentileValue", 100);
		Assertions.assertThrows(SensitivityAnalyserException.class, ()->{
			SensitivityAnalyser sa = new SensitivityAnalyser(model, jsonConfig);
			sa.getFullReport();
		});
		
		reportSettings.put("sumsLowerPercentileValue", 25);
		reportSettings.put("sumsUpperPercentileValue", 75);
		reportSettings.put("sensLowerPercentileValue", 100);
		reportSettings.put("sensUpperPercentileValue", 0);
		Assertions.assertThrows(SensitivityAnalyserException.class, ()->{
			SensitivityAnalyser sa = new SensitivityAnalyser(model, jsonConfig);
			sa.getFullReport();
		});
	}
	

	
}
