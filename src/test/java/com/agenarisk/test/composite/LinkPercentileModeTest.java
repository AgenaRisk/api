package com.agenarisk.test.composite;

import com.agenarisk.api.model.CrossNetworkLink;
import com.agenarisk.api.model.Model;
import com.agenarisk.api.model.Network;
import com.agenarisk.api.model.Node;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import uk.co.agena.minerva.model.ConstantSummaryMessagePassingLink;
import uk.co.agena.minerva.model.MessagePassingLink;
import uk.co.agena.minerva.model.MessagePassingLinks;
import uk.co.agena.minerva.util.helpers.MathsHelper;

/**
 * A cross-network link's own percentile, and the Mode statistic, through CMPX.
 *
 * <p>Neither could be expressed before: the percentile came from the source node so every percentile
 * link out of it shared one value, and there was no Mode at all. The percentile is written only when
 * the link carries one, because "absent" is what every model saved earlier says and it has to keep
 * meaning "use the source node's setting".</p>
 */
public class LinkPercentileModeTest {

	private static final double DELTA = 1e-9;

	/** net1.src (simulated, output) -> net2.tgt (simulated, input). */
	private static Model twoNetworkModel() throws Exception {
		Model model = Model.createModel();
		Network net1 = model.createNetwork("net1");
		Network net2 = model.createNetwork("net2");
		Node src = net1.createNode("src", Node.Type.ContinuousInterval);
		src.convertToSimulated();
		Node tgt = net2.createNode("tgt", Node.Type.ContinuousInterval);
		tgt.convertToSimulated();
		return model;
	}

	private static Node src(Model m) { return m.getNetwork("net1").getNode("src"); }
	private static Node tgt(Model m) { return m.getNetwork("net2").getNode("tgt"); }

	private static ConstantSummaryMessagePassingLink logicSummaryLink(Model m) {
		for (Object o : m.getLogicModel().getMessagePassingLinks()) {
			for (MessagePassingLink mpl : ((MessagePassingLinks) o).getLinks()) {
				if (mpl instanceof ConstantSummaryMessagePassingLink) {
					return (ConstantSummaryMessagePassingLink) mpl;
				}
			}
		}
		return null;
	}

	private static JSONObject onlyLink(Model m) throws Exception {
		JSONArray links = m.toJson().getJSONObject("model").getJSONArray("links");
		assertEquals(1, links.length(), "expected exactly one cross network link");
		return links.getJSONObject(0);
	}

	// --- percentile -----------------------------------------------------------------------------

	@Test
	public void aPercentileSetOnTheLinkReachesTheEngineLink() throws Exception {
		Model m = twoNetworkModel();
		Node.linkNodes(src(m), tgt(m), CrossNetworkLink.Type.UpperPercentile, null, 95d);

		assertEquals(95d, logicSummaryLink(m).getPercentile(), DELTA);
		assertEquals(MathsHelper.SummaryStatistic.UPPER_PERCENTILE, logicSummaryLink(m).getSummaryStatistic());
	}

	@Test
	public void withoutAPercentileTheEngineLinkKeepsDeferringToTheSourceNode() throws Exception {
		Model m = twoNetworkModel();
		Node.linkNodes(src(m), tgt(m), CrossNetworkLink.Type.LowerPercentile, null);

		assertNull(logicSummaryLink(m).getPercentile(),
				"null is what makes the engine read the source node's own setting");
	}

	@Test
	public void thePercentileIsWrittenToCmpxAndReadBack() throws Exception {
		Model m = twoNetworkModel();
		Node.linkNodes(src(m), tgt(m), CrossNetworkLink.Type.LowerPercentile, null, 5d);

		JSONObject link = onlyLink(m);
		assertEquals("LowerPercentile", link.getString(CrossNetworkLink.Field.type.toString()));
		assertEquals(5d, link.getDouble(CrossNetworkLink.Field.percentile.toString()), DELTA);

		Model back = Model.createModel(m.toJson());
		assertEquals(5d, logicSummaryLink(back).getPercentile(), DELTA);
	}

	/** Omission is meaningful: it is how every earlier model says "use the source node's setting". */
	@Test
	public void anUnsetPercentileIsOmittedFromTheJsonEntirely() throws Exception {
		Model m = twoNetworkModel();
		Node.linkNodes(src(m), tgt(m), CrossNetworkLink.Type.LowerPercentile, null);

		assertFalse(onlyLink(m).has(CrossNetworkLink.Field.percentile.toString()));

		Model back = Model.createModel(m.toJson());
		assertNull(logicSummaryLink(back).getPercentile());
	}

	/** A link JSON with no percentile key is exactly what a legacy file looks like. */
	@Test
	public void aLegacyLinkJsonWithoutTheKeyStillLoads() throws Exception {
		Model m = twoNetworkModel();
		Node.linkNodes(src(m), tgt(m), CrossNetworkLink.Type.UpperPercentile, null, 90d);

		JSONObject json = m.toJson();
		JSONObject link = json.getJSONObject("model").getJSONArray("links").getJSONObject(0);
		link.remove(CrossNetworkLink.Field.percentile.toString()); // make it look pre-change

		Model back = Model.createModel(json);
		assertNull(logicSummaryLink(back).getPercentile());
		assertEquals(MathsHelper.SummaryStatistic.UPPER_PERCENTILE, logicSummaryLink(back).getSummaryStatistic());
	}

	@Test
	public void thePercentileSurvivesSeveralRoundTrips() throws Exception {
		Model m = twoNetworkModel();
		Node.linkNodes(src(m), tgt(m), CrossNetworkLink.Type.LowerPercentile, null, 2.5d);

		Model back = Model.createModel(Model.createModel(m.toJson()).toJson());
		assertEquals(2.5d, logicSummaryLink(back).getPercentile(), DELTA);
	}

	// --- Mode ------------------------------------------------------------------------------------

	@Test
	public void aModeLinkMapsToTheEngineStatistic() throws Exception {
		Model m = twoNetworkModel();
		Node.linkNodes(src(m), tgt(m), CrossNetworkLink.Type.Mode, null);

		assertEquals(MathsHelper.SummaryStatistic.MODE, logicSummaryLink(m).getSummaryStatistic());
	}

	/**
	 * Before Mode existed, cnLinkToJSON's switch had a default that THREW, so an unrecognised
	 * statistic failed the whole save rather than one link.
	 */
	@Test
	public void aModeLinkSavesAndReloadsRatherThanThrowing() throws Exception {
		Model m = twoNetworkModel();
		Node.linkNodes(src(m), tgt(m), CrossNetworkLink.Type.Mode, null);

		assertEquals("Mode", onlyLink(m).getString(CrossNetworkLink.Field.type.toString()));

		Model back = Model.createModel(m.toJson());
		assertEquals(MathsHelper.SummaryStatistic.MODE, logicSummaryLink(back).getSummaryStatistic());
	}

	/** Every Type must survive the JSON round trip, not just the two just added. */
	@Test
	public void everySummaryTypeRoundTrips() throws Exception {
		CrossNetworkLink.Type[] types = {
			CrossNetworkLink.Type.Mean, CrossNetworkLink.Type.Median, CrossNetworkLink.Type.Variance,
			CrossNetworkLink.Type.StandardDeviation, CrossNetworkLink.Type.LowerPercentile,
			CrossNetworkLink.Type.UpperPercentile, CrossNetworkLink.Type.Mode,
		};
		for (CrossNetworkLink.Type t : types) {
			Model m = twoNetworkModel();
			Node.linkNodes(src(m), tgt(m), t, null);
			assertEquals(t.toString(),
					onlyLink(m).getString(CrossNetworkLink.Field.type.toString()), t.toString());
			Model back = Model.createModel(m.toJson());
			assertNotNull(logicSummaryLink(back), t.toString());
		}
	}
}
