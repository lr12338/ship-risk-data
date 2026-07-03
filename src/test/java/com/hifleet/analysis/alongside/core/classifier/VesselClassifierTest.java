package com.hifleet.analysis.alongside.core.classifier;

import com.hifleet.analysis.alongside.core.model.PairPriority;
import com.hifleet.analysis.alongside.core.model.VesselClass;
import com.hifleet.analysis.alongside.core.model.VesselProfile;
import com.hifleet.analysis.alongside.core.model.VesselType;
import com.hifleet.analysis.portcall.config.AlongsideConfig;
import com.hifleet.analysis.portcall.model.CleanAisPoint;
import org.junit.Assert;
import org.junit.Test;

import java.util.Properties;

public class VesselClassifierTest {

    @Test
    public void shouldClassifyTankerAsHighPriority() {
        Assert.assertEquals(VesselClass.HIGH_PRIORITY, classifier().classify(point("OCEAN TANKER")));
    }

    @Test
    public void shouldClassifyTugAsNormalService() {
        Assert.assertEquals(VesselClass.NORMAL_SERVICE, classifier().classify(point("HARBOUR TUG")));
    }

    @Test
    public void shouldClassifyAisShipTypeTankerAsHighPriority() {
        CleanAisPoint point = point(null);
        point.setShipType(Integer.valueOf(80));

        Assert.assertEquals(VesselClass.HIGH_PRIORITY, classifier().classify(point));
    }

    @Test
    public void shouldKeepUnknownAsUnknownAndNotExcludeByPairPriority() {
        VesselProfile a = new VesselProfile(100L, VesselType.CARGO, VesselClass.HIGH_PRIORITY, null);
        VesselProfile b = new VesselProfile(200L, VesselType.UNKNOWN, VesselClass.UNKNOWN, null);

        Assert.assertEquals(VesselClass.UNKNOWN, classifier().classify(point(null)));
        Assert.assertEquals(PairPriority.HIGH, new AlongsidePairPriorityClassifierImpl().classify(a, b));
    }

    @Test
    public void shouldClassifyTankerTankerAsHighAndCargoTugAsDowngradedAndSameMmsiAsExclude() {
        AlongsidePairPriorityClassifierImpl classifier = new AlongsidePairPriorityClassifierImpl();

        Assert.assertEquals(PairPriority.HIGH, classifier.classify(
            new VesselProfile(100L, VesselType.TANKER, VesselClass.HIGH_PRIORITY, null),
            new VesselProfile(200L, VesselType.TANKER, VesselClass.HIGH_PRIORITY, null)));
        Assert.assertEquals(PairPriority.DOWNGRADED, classifier.classify(
            new VesselProfile(100L, VesselType.CARGO, VesselClass.HIGH_PRIORITY, null),
            new VesselProfile(200L, VesselType.TUG, VesselClass.NORMAL_SERVICE, null)));
        Assert.assertEquals(PairPriority.EXCLUDE, classifier.classify(
            new VesselProfile(100L, VesselType.CARGO, VesselClass.HIGH_PRIORITY, null),
            new VesselProfile(100L, VesselType.CARGO, VesselClass.HIGH_PRIORITY, null)));
    }

    private VesselTypeClassifierImpl classifier() {
        Properties props = new Properties();
        AlongsideConfig.addDefaults(props);
        AlongsideConfig config = AlongsideConfig.fromProperties(props);
        return new VesselTypeClassifierImpl(
            config.getHighPriorityShipTypes(),
            config.getNormalServiceShipTypes(),
            config.getLowPriorityShipTypes()
        );
    }

    private CleanAisPoint point(String shipName) {
        CleanAisPoint point = new CleanAisPoint();
        point.setShipName(shipName);
        return point;
    }
}
