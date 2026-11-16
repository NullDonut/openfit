package com.openfit.app;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class HeartRateParserTest {
    @Test public void parsesEightBitMeasurement() {
        assertEquals(72, HeartRateParser.parse(new byte[] {0, 72}));
    }

    @Test public void parsesSixteenBitLittleEndianMeasurement() {
        assertEquals(300, HeartRateParser.parse(new byte[] {1, 44, 1}));
    }

    @Test public void rejectsIncompleteMeasurement() {
        assertEquals(-1, HeartRateParser.parse(new byte[] {1, 44}));
        assertEquals(-1, HeartRateParser.parse(new byte[] {0}));
        assertEquals(-1, HeartRateParser.parse(null));
    }
}
