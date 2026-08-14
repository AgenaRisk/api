package com.agenarisk.learning.structure.regression;

import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writing a relation that is known to hold exactly onto a node, instead of fitting one.
 *
 * <p>Some columns are arithmetic on other columns — a total and its parts, a rate and its numerator and
 * denominator. Fitting such a node returns R2 = 1 and a residual variance that only avoids being zero because
 * {@link ContinuousRegressionLearner} floors it, so the fitted node asserts an uncertainty that does not exist,
 * and carries coefficients estimated from a sample when the true ones are known by algebra. Writing
 * {@code Arithmetic(expression)} states the relation instead of approximating it.
 *
 * <p>Shared by regression structure discovery (which fits tables as part of its own run) and regression
 * parameter learning (which fits an existing structure), so a candidate is treated the same way whichever
 * produced it.
 */
public final class DeterministicExpressions {

	private DeterministicExpressions() {
	}

	/**
	 * Why the expression cannot be written onto this node, or null if it can.
	 *
	 * <p>The caller derives these relations from data, not from the model, so the model has to be checked to
	 * agree before one is imposed: a search that honours a directed constraint only softly can leave the node
	 * with different parents, and an expression naming a non-parent cannot be resolved.
	 *
	 * <p>Names are matched by consuming each parent id out of the expression and checking that nothing
	 * alphabetic survives, rather than by tokenising — node ids come from CSV headers and may contain spaces
	 * or punctuation that no identifier pattern would match.
	 */
	public static String rejection(Node node, String expression) {
		if (node == null){
			return "the node is not in this model";
		}
		if (!isContinuous(node)){
			return "node '" + node.getId() + "' is " + node.getType()
					+ ", and an arithmetic expression over discretised states has no meaning";
		}
		Set<Node> parents = node.getParents();
		if (parents.isEmpty()){
			return "node '" + node.getId() + "' has no parents in this structure";
		}
		List<Node> byLongestId = new ArrayList<>(parents);
		byLongestId.sort((a, b) -> b.getId().length() - a.getId().length());
		String remainder = expression;
		for (Node parent : byLongestId){
			if (!isContinuous(parent)){
				return "parent '" + parent.getId() + "' is " + parent.getType() + ", not continuous";
			}
			if (!remainder.contains(parent.getId())){
				return "the expression does not use parent '" + parent.getId() + "'";
			}
			remainder = remainder.replace(parent.getId(), " ");
		}
		for (char c : remainder.toCharArray()){
			if (Character.isLetter(c)){
				return "the expression refers to something that is not a parent of '" + node.getId() + "'";
			}
		}
		return null;
	}

	/**
	 * Apply every declared expression the model agrees with. Returns one outcome line per declared node —
	 * applied or declined with a reason — so the caller can report it rather than let a silently-unapplied
	 * expression pass for an applied one.
	 */
	public static Map<String, String> apply(Network network, Map<String, String> declared) {
		Map<String, String> outcomes = new LinkedHashMap<>();
		if (network == null || declared == null || declared.isEmpty()){
			return outcomes;
		}
		for (Map.Entry<String, String> entry : declared.entrySet()){
			Node node = network.getNode(entry.getKey());
			String reason = rejection(node, entry.getValue());
			if (reason != null){
				outcomes.put(entry.getKey(), "not applied: " + reason);
				continue;
			}
			try {
				node.setTableFunction("Arithmetic(" + entry.getValue() + ")");
				outcomes.put(entry.getKey(), "set to " + entry.getValue());
			}
			catch (Exception ex){
				outcomes.put(entry.getKey(), "not applied: " + ex.getMessage());
			}
		}
		return outcomes;
	}

	public static boolean isContinuous(Node node) {
		return node.getType() == Node.Type.ContinuousInterval || node.getType() == Node.Type.IntegerInterval;
	}
}
