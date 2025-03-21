package com.agenarisk.learning.structure.config;

import com.agenarisk.api.model.Model;
import com.agenarisk.learning.structure.exception.StructureLearningException;
import java.nio.file.Path;
import java.util.Map;

/**
 *
 * @author Eugene Dementiev
 */
public class MergerConfigurer extends ApplicableConfigurer implements Configurable {
	
	private Path inputDirPath;
	private Path outputDirPath;
	private String modelPrefix;
	private Map<String, String> modelPrefixes;
	private Model model;
	
	public MergerConfigurer(Config config) {
		super(config);
	}
	
	public MergerConfigurer() {
		super();
	}

	@Override
	public MergerExecutor apply() {
		if (inputDirPath == null || outputDirPath == null || modelPrefix == null || modelPrefixes == null){
			throw new StructureLearningException("MergerConfigurer not fully configured before applying");
		}
		return new MergerExecutor(config);
	}

	public Path getInputDirPath() {
		return inputDirPath;
	}

	public void setInputDirPath(Path inputDirPath) {
		this.inputDirPath = inputDirPath;
	}

	public Path getOutputDirPath() {
		return outputDirPath;
	}

	public void setOutputDirPath(Path outputDirPath) {
		this.outputDirPath = outputDirPath;
	}

	public String getModelPrefix() {
		return modelPrefix;
	}

	public void setModelPrefix(String modelPrefix) {
		this.modelPrefix = modelPrefix;
	}

	public Map<String, String> getModelPrefixes() {
		return modelPrefixes;
	}

	public void setModelPrefixes(Map<String, String> modelPrefixes) {
		this.modelPrefixes = modelPrefixes;
	}

	public Model getModel() {
		return model;
	}

	public void setModel(Model model) {
		this.model = model;
	}
	
	
}
