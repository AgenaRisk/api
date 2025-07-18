package com.agenarisk.learning.structure.config;

import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import com.agenarisk.api.util.CsvReader;
import com.agenarisk.learning.structure.exception.StructureLearningException;
import com.agenarisk.learning.structure.logger.BLogger;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 *
 * @author Eugene Dementiev
 */
public class MergerExecutor extends Configurer<MergerExecutor> implements Executable {
	
	private MergerConfigurer originalConfigurer;
	
	protected MergerExecutor(Config config) {
		super(config);
	}
	
	protected MergerExecutor() {
		super();
	}

	public void setOriginalConfigurer(MergerConfigurer originalConfigurer) {
		this.originalConfigurer = originalConfigurer;
	}
	
	@Override
	public void execute() throws StructureLearningException {
		try {
			if (originalConfigurer == null){
				BLogger.logConditional("Original configurer not set");
				return;
			}
			
			Model model = Model.createModel();
			
			originalConfigurer.getModelPrefixes().entrySet().forEach(entry -> {
				try {
					Model modelToImport = Model.loadModel(originalConfigurer.getOutputDirPath().resolve(entry.getKey()+".cmpx").toString());
					modelToImport.getNetworkList().get(0).setId(entry.getKey());
					modelToImport.getNetworkList().get(0).setName(entry.getValue());
					model.absorb(modelToImport.toJson());                                                                                                                                                                              
				}
				catch(Exception ex2){
					BLogger.logThrowableIfDebug(ex2);
					BLogger.logConditional(ex2);
				}
			});
			byte[] bytes = model.export(Model.ExportFlag.KEEP_META, Model.ExportFlag.KEEP_OBSERVATIONS, Model.ExportFlag.KEEP_RESULTS).toString().getBytes();
			Files.write(originalConfigurer.getOutputDirPath().resolve(originalConfigurer.getModelPrefix()+".cmpx"), bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			originalConfigurer.setModel(model);
		}
		catch (Exception ex){
			throw new StructureLearningException(ex.getMessage(), ex);
		}
	}

}
