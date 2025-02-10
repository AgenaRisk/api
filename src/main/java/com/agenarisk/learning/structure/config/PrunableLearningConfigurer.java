package com.agenarisk.learning.structure.config;

import com.agenarisk.learning.structure.exception.StructureLearningException;
import org.json.JSONObject;

/**
 *
 * @author Eugene Dementiev
 * @param <T> Actual implementation type of LearningConfigurer
 */
public abstract class PrunableLearningConfigurer<T extends LearningConfigurer> extends LearningConfigurer<T> {
	
	public PrunableLearningConfigurer(Config config) {
		super(config);
	}
	
	public PrunableLearningConfigurer() {
		super();
	}
	
	/**
	 * Level 0: No pruning <br>
	 * Level 1: Parent-sets of size 1 vs. parent-sets of size 0 <br>
	 * Level 2: Plus parent-sets of size 2 vs. parent-sets of size 1 <br>
	 * Level 3: Plus parent-sets of size 3 vs. parent-sets of size 2
	 * @param level
	 * @return this configurer
	 */
	public T setPruningLevel(int level){
		switch (level) {
			case 0:
				config.setLearningHcPruning("Level 0: No pruning");
				break;
			case 1:
				config.setLearningHcPruning("Level 1: Parent-sets of size 1 vs. parent-sets of size 0");
				break;
			case 2:
				config.setLearningHcPruning("Level 2: Plus parent-sets of size 2 vs. parent-sets of size 1");
				break;
			case 3:
				config.setLearningHcPruning("Level 3: Plus parent-sets of size 3 vs. parent-sets of size 2");
				break;
			default:
				throw new StructureLearningException("Level must be in the range [0,3]");
		}
		
		return (T)this;
	}
	
	protected T configurePruningFromJson(JSONObject jConfig){
		if (jConfig.has("pruningLevel")){
			this.setPruningLevel(jConfig.getInt("pruningLevel"));
		}
		return (T)this;
	}
}
