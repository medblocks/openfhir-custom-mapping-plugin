// package com.medblocks.plugins.unit;

// import org.hl7.fhir.r4.model.Timing.UnitsOfTime;
// import org.junit.jupiter.api.Test;
// import static org.junit.jupiter.api.Assertions.*;

// public class TimeUnitConverterTest {

//     @Test
//     public void testFrequencyConverter() {
//         FrequencyUnitConverter converter = TimeUnitConverterFactory.getFrequencyConverter();
        
//         // Test valid units
//         assertTrue(converter.isValidUnit(UnitsOfTime.S));
//         assertTrue(converter.isValidUnit(UnitsOfTime.MIN));
//         assertTrue(converter.isValidUnit(UnitsOfTime.H));
//         assertTrue(converter.isValidUnit(UnitsOfTime.D));
        
//         // Test invalid units
//         assertFalse(converter.isValidUnit(UnitsOfTime.WK));
//         assertFalse(converter.isValidUnit(UnitsOfTime.MO));
//         assertFalse(converter.isValidUnit(UnitsOfTime.A));
//         assertFalse(converter.isValidUnit(null));
        
//         // Test conversions
//         assertEquals("1/s", converter.convertUnit(UnitsOfTime.S));
//         assertEquals("1/min", converter.convertUnit(UnitsOfTime.MIN));
//         assertEquals("1/h", converter.convertUnit(UnitsOfTime.H));
//         assertEquals("1/d", converter.convertUnit(UnitsOfTime.D));
        
//         // Test invalid conversion
//         assertNull(converter.convertUnit(UnitsOfTime.WK));
//         assertNull(converter.convertUnit(null));
//     }
    
//     @Test
//     public void testDurationConverter() {
//         DurationUnitConverter converter = TimeUnitConverterFactory.getDurationConverter();
        
//         // Test valid units (all units are valid for duration)
//         assertTrue(converter.isValidUnit(UnitsOfTime.S));
//         assertTrue(converter.isValidUnit(UnitsOfTime.MIN));
//         assertTrue(converter.isValidUnit(UnitsOfTime.H));
//         assertTrue(converter.isValidUnit(UnitsOfTime.D));
//         assertTrue(converter.isValidUnit(UnitsOfTime.WK));
//         assertTrue(converter.isValidUnit(UnitsOfTime.MO));
//         assertTrue(converter.isValidUnit(UnitsOfTime.A));
        
//         // Test null
//         assertFalse(converter.isValidUnit(null));
        
//         // Test duration formatting
//         assertEquals("PT5S", converter.formatDuration(5, UnitsOfTime.S));
//         assertEquals("PT10M", converter.formatDuration(10, UnitsOfTime.MIN));
//         assertEquals("PT2H", converter.formatDuration(2, UnitsOfTime.H));
//         assertEquals("P3D", converter.formatDuration(3, UnitsOfTime.D));
//         assertEquals("P1W", converter.formatDuration(1, UnitsOfTime.WK));
//         assertEquals("P6M", converter.formatDuration(6, UnitsOfTime.MO));
//         assertEquals("P2Y", converter.formatDuration(2, UnitsOfTime.A));
        
//         // Test invalid formatting
//         assertNull(converter.formatDuration(1, null));
//     }
// } 