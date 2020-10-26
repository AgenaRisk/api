package com.agenarisk.test.composite;

import com.agenarisk.api.model.CrossNetworkLink;
import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import org.junit.jupiter.api.Test;

/**
 * The purpose of this test is to exhaustively try to create cross network links between nodes of different types and check that all expected link types are allowed and all others are not.
 *
 * @author Eugene Dementiev
 */
public class LinkTypeTest {

	@Test
	public void testLinkTypes() throws Exception {

		Model model = Model.createModel();
		Network net1 = model.createNetwork("net1");
		Network net2 = model.createNetwork("net2");
		int nodeCounter = 0;
		
		{
			// Boolean → Boolean → Marginal
			Node n1 = net1.createNode("node_" + ++nodeCounter, Node.Type.Boolean);
			Node n2 = net2.createNode("node_" + ++nodeCounter, Node.Type.Boolean);
			Node.linkNodes(n1, n2, CrossNetworkLink.Type.Marginals);
		}
		{
			// Boolean → Continuous (Simulation) → State
			Node n1 = net1.createNode("node_" + ++nodeCounter, Node.Type.Boolean);
			Node n2 = net2.createNode("node_" + ++nodeCounter, Node.Type.ContinuousInterval);
			n2.convertToSimulated();
			Node.linkNodes(n1, n2, CrossNetworkLink.Type.State, n1.getStates().get(0).getLabel());
		}
		{
			// Boolean → Integer (Simulation) → State
			Node n1 = net1.createNode("node_" + ++nodeCounter, Node.Type.Boolean);
			Node n2 = net2.createNode("node_" + ++nodeCounter, Node.Type.IntegerInterval);
			n2.convertToSimulated();
			Node.linkNodes(n1, n2, CrossNetworkLink.Type.State, n1.getStates().get(0).getLabel());
		}
		
		{
			// Labelled → Labelled → Marginal
			Node n1 = net1.createNode("node_" + ++nodeCounter, Node.Type.Labelled);
			Node n2 = net2.createNode("node_" + ++nodeCounter, Node.Type.Labelled);
			Node.linkNodes(n1, n2, CrossNetworkLink.Type.Marginals);
		}
		{
			// Labelled → Continuous (Simulation) → State
			Node n1 = net1.createNode("node_" + ++nodeCounter, Node.Type.Labelled);
			Node n2 = net2.createNode("node_" + ++nodeCounter, Node.Type.ContinuousInterval);
			n2.convertToSimulated();
			Node.linkNodes(n1, n2, CrossNetworkLink.Type.State, n1.getStates().get(0).getLabel());
		}
		{
			// Labelled → Integer (Simulation) → State
			Node n1 = net1.createNode("node_" + ++nodeCounter, Node.Type.Labelled);
			Node n2 = net2.createNode("node_" + ++nodeCounter, Node.Type.IntegerInterval);
			n2.convertToSimulated();
			Node.linkNodes(n1, n2, CrossNetworkLink.Type.State, n1.getStates().get(0).getLabel());
		}
		
		{
			// Ranked → Ranked → Marginal
			Node n1 = net1.createNode("node_" + ++nodeCounter, Node.Type.Ranked);
			Node n2 = net2.createNode("node_" + ++nodeCounter, Node.Type.Ranked);
			Node.linkNodes(n1, n2, CrossNetworkLink.Type.Marginals);
		}
		{
			// Ranked → Continuous (Simulation) → State
			Node n1 = net1.createNode("node_" + ++nodeCounter, Node.Type.Ranked);
			Node n2 = net2.createNode("node_" + ++nodeCounter, Node.Type.ContinuousInterval);
			n2.convertToSimulated();
			Node.linkNodes(n1, n2, CrossNetworkLink.Type.State, n1.getStates().get(0).getLabel());
		}
		{
			// Ranked → Integer (Simulation) → State
			Node n1 = net1.createNode("node_" + ++nodeCounter, Node.Type.Ranked);
			Node n2 = net2.createNode("node_" + ++nodeCounter, Node.Type.IntegerInterval);
			n2.convertToSimulated();
			Node.linkNodes(n1, n2, CrossNetworkLink.Type.State, n1.getStates().get(0).getLabel());
		}
		
		{
			// Discrete Real → Discrete Real → Marginal
			Node n1 = net1.createNode("node_" + ++nodeCounter, Node.Type.DiscreteReal);
			Node n2 = net2.createNode("node_" + ++nodeCounter, Node.Type.DiscreteReal);
			Node.linkNodes(n1, n2, CrossNetworkLink.Type.Marginals);
		}
		
		{
			// Continuous → Continuous → Marginal
			Node n1 = net1.createNode("node_" + ++nodeCounter, Node.Type.ContinuousInterval);
			Node n2 = net2.createNode("node_" + ++nodeCounter, Node.Type.ContinuousInterval);
			Node.linkNodes(n1, n2, CrossNetworkLink.Type.Marginals);
		}
		{
			// Continuous → Continuous (Simulation) → Marginal
			Node n1 = net1.createNode("node_" + ++nodeCounter, Node.Type.ContinuousInterval);
			Node n2 = net2.createNode("node_" + ++nodeCounter, Node.Type.ContinuousInterval);
			n2.convertToSimulated();
			Node.linkNodes(n1, n2, CrossNetworkLink.Type.Marginals);
		}
		{
			// Continuous → Continuous (Simulation) → Summary Statistic Value
			Node n1 = net1.createNode("node_" + ++nodeCounter, Node.Type.ContinuousInterval);
			Node n2 = net2.createNode("node_" + ++nodeCounter, Node.Type.ContinuousInterval);
			n2.convertToSimulated();
			Node.linkNodes(n1, n2, CrossNetworkLink.Type.Mean);
		}
		{
			// Continuous (Simulation) → Continuous → Marginal
			Node n1 = net1.createNode("node_" + ++nodeCounter, Node.Type.ContinuousInterval);
			n1.convertToSimulated();
			Node n2 = net2.createNode("node_" + ++nodeCounter, Node.Type.ContinuousInterval);
			Node.linkNodes(n1, n2, CrossNetworkLink.Type.Marginals);
		}
		{
			// Continuous (Simulation) → Continuous (Simulation) → Marginal
			Node n1 = net1.createNode("node_" + ++nodeCounter, Node.Type.ContinuousInterval);
			n1.convertToSimulated();
			Node n2 = net2.createNode("node_" + ++nodeCounter, Node.Type.ContinuousInterval);
			n2.convertToSimulated();
			Node.linkNodes(n1, n2, CrossNetworkLink.Type.Marginals);
		}
		{
			// Continuous (Simulation) → Continuous (Simulation) → Summary Statistic Value
			Node n1 = net1.createNode("node_" + ++nodeCounter, Node.Type.ContinuousInterval);
			n1.convertToSimulated();
			Node n2 = net2.createNode("node_" + ++nodeCounter, Node.Type.ContinuousInterval);
			n2.convertToSimulated();
			Node.linkNodes(n1, n2, CrossNetworkLink.Type.Mean);
		}
		
		{
			// Integer → Integer → Marginal
			Node n1 = net1.createNode("node_" + ++nodeCounter, Node.Type.IntegerInterval);
			Node n2 = net2.createNode("node_" + ++nodeCounter, Node.Type.IntegerInterval);
			Node.linkNodes(n1, n2, CrossNetworkLink.Type.Marginals);
		}
		{
			// Integer → Integer (Simulation) → Marginal
			Node n1 = net1.createNode("node_" + ++nodeCounter, Node.Type.IntegerInterval);
			Node n2 = net2.createNode("node_" + ++nodeCounter, Node.Type.IntegerInterval);
			n2.convertToSimulated();
			Node.linkNodes(n1, n2, CrossNetworkLink.Type.Marginals);
		}
		{
			// Integer → Integer (Simulation) → Summary Statistic Value
			Node n1 = net1.createNode("node_" + ++nodeCounter, Node.Type.IntegerInterval);
			Node n2 = net2.createNode("node_" + ++nodeCounter, Node.Type.IntegerInterval);
			n2.convertToSimulated();
			Node.linkNodes(n1, n2, CrossNetworkLink.Type.Mean);
		}
		{
			// Integer (Simulation) → Integer → Marginal
			Node n1 = net1.createNode("node_" + ++nodeCounter, Node.Type.IntegerInterval);
			n1.convertToSimulated();
			Node n2 = net2.createNode("node_" + ++nodeCounter, Node.Type.IntegerInterval);
			Node.linkNodes(n1, n2, CrossNetworkLink.Type.Marginals);
		}
		{
			// Integer (Simulation) → Integer (Simulation) → Marginal
			Node n1 = net1.createNode("node_" + ++nodeCounter, Node.Type.IntegerInterval);
			n1.convertToSimulated();
			Node n2 = net2.createNode("node_" + ++nodeCounter, Node.Type.IntegerInterval);
			n2.convertToSimulated();
			Node.linkNodes(n1, n2, CrossNetworkLink.Type.Marginals);
		}
		{
			// Integer (Simulation) → Integer (Simulation) → Summary Statistic Value
			Node n1 = net1.createNode("node_" + ++nodeCounter, Node.Type.IntegerInterval);
			n1.convertToSimulated();
			Node n2 = net2.createNode("node_" + ++nodeCounter, Node.Type.IntegerInterval);
			n2.convertToSimulated();
			Node.linkNodes(n1, n2, CrossNetworkLink.Type.Mean);
		}
		
		/*
		Node n1 = net1.createNode("fromContD", Node.Type.ContinuousInterval);
		Node n2 = net2.createNode("inFromContD", Node.Type.ContinuousInterval);
		n2.convertToSimulated();
		Node.linkNodes(n1, n2, CrossNetworkLink.Type.Marginals);
		
		n1 = net1.createNode("fromContD2", Node.Type.ContinuousInterval);
		n2 = net2.createNode("inFromContD2", Node.Type.ContinuousInterval);
		n2.convertToSimulated();
		Node.linkNodes(n1, n2, CrossNetworkLink.Type.Mean);
		
		n1 = net1.createNode("fromSim", Node.Type.ContinuousInterval);
		n1.convertToSimulated();
		n2 = net2.createNode("inFromSim", Node.Type.ContinuousInterval);
		Node.linkNodes(n1, n2, CrossNetworkLink.Type.Marginals);
		
		n1 = net1.createNode("fromBool", Node.Type.Boolean);
		n2 = net2.createNode("inFromBool", Node.Type.ContinuousInterval);
		n2.convertToSimulated();
		Node.linkNodes(n1, n2, CrossNetworkLink.Type.State, "False");
		
		n1 = net1.createNode("fromRanked", Node.Type.Ranked);
		n2 = net2.createNode("inFromRanked", Node.Type.ContinuousInterval);
		n2.convertToSimulated();
		Node.linkNodes(n1, n2, CrossNetworkLink.Type.State, "Medium");
		*/
		
		boolean error = false;
		try {
			Node n1 = net1.createNode("discReal", Node.Type.DiscreteReal);
			n1.setStates(new String[]{"0.0", "1.0", "2.0"});
			Node n2 = net2.createNode("inFromdiscReal", Node.Type.ContinuousInterval);
			n2.convertToSimulated();
			Node.linkNodes(n1, n2, CrossNetworkLink.Type.State, "0.0");
		}
		catch (Exception ex){
			error = true;
		}
		if (!error){
			throw new RuntimeException("Invalid link created");
		}
		
		error = false;
		try {
			Node n1 = net1.createNode("bool2", Node.Type.Boolean);
			Node n2 = net2.createNode("inFromBool2", Node.Type.Labelled);
			n2.setStates(new String[]{"A", "B"});
			Node.linkNodes(n1, n2, CrossNetworkLink.Type.Marginals);
		}
		catch (Exception ex){
			error = true;
		}
		if (!error){
			throw new RuntimeException("Invalid link created");
		}
		
		error = false;
		try {
			Node n1 = net1.createNode("simInt", Node.Type.IntegerInterval);
			n1.convertToSimulated();
			Node n2 = net2.createNode("simContFromSimInt", Node.Type.ContinuousInterval);
			n2.convertToSimulated();
			Node.linkNodes(n1, n2, CrossNetworkLink.Type.Marginals);
		}
		catch (Exception ex){
			error = true;
		}
		if (!error){
			throw new RuntimeException("Invalid link created");
		}
		
		
	}
}
