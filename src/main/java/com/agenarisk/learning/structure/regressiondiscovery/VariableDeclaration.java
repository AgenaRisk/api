package com.agenarisk.learning.structure.regressiondiscovery;

import com.agenarisk.api.model.Node;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Declares one CSV column's node type/states for {@link ShellModelBuilder}, so
 * {@link com.agenarisk.learning.structure.config.RegressionStructureSearchExecutor} can build a fully-typed shell
 * model directly from data, with no pre-existing {@code .cmpx} model required.
 * <br>
 * For {@code Boolean}/{@code Labelled}/{@code Ranked}/{@code DiscreteReal}, {@code states} is the ordered
 * label/value list (order matters for {@code Ranked} - it defines the rank order). For non-simulated
 * {@code ContinuousInterval}/{@code IntegerInterval}, {@code states} are literal range strings (e.g. {@code "0 - 10"}
 * or a single numeric string) - the exact format {@code Node.setStates} already parses, so no new encoding is
 * needed. For simulated continuous nodes (the default for those two types), {@code states} is ignored.
 *
 * @author Eugene Dementiev
 */
public class VariableDeclaration {

	private final Node.Type type;
	private final List<String> states;
	private final boolean simulated;

	private VariableDeclaration(Node.Type type, List<String> states, boolean simulated) {
		this.type = type;
		this.states = states;
		this.simulated = simulated;
	}

	public Node.Type getType() {
		return type;
	}

	/**
	 * @return ordered states/range-strings for this variable; empty for a simulated continuous node
	 */
	public List<String> getStates() {
		return states;
	}

	/**
	 * @return whether this variable should be a simulated continuous node (only meaningful for
	 * {@code ContinuousInterval}/{@code IntegerInterval}; defaults to true for those types when unspecified)
	 */
	public boolean isSimulated() {
		return simulated;
	}

	/**
	 * @param jVariable a single column's declaration: {@code {"type": "ContinuousInterval", "simulated": true}} or
	 * {@code {"type": "Ranked", "states": ["Low","Medium","High"]}}, etc.
	 *
	 * @return the parsed VariableDeclaration
	 */
	public static VariableDeclaration fromJson(JSONObject jVariable) {
		Node.Type type = Node.Type.valueOf(jVariable.getString("type"));
		boolean simulated = jVariable.optBoolean("simulated", true);
		List<String> states = new ArrayList<>();
		JSONArray jStates = jVariable.optJSONArray("states");
		if (jStates != null){
			for (int i = 0; i < jStates.length(); i++){
				states.add(jStates.getString(i));
			}
		}
		return new VariableDeclaration(type, states, simulated);
	}
}
