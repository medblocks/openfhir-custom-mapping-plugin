package com.medblocks.plugins.unit;

import org.hl7.fhir.r4.model.Timing.UnitsOfTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Converter for mapping FHIR time units to ISO 8601 duration format
 */
public class DurationUnitConverter implements TimeUnitConverter {
    
    private static final Logger log = LoggerFactory.getLogger(DurationUnitConverter.class);
    
    @Override
    public boolean isValidUnit(UnitsOfTime timeUnit) {
        return timeUnit != null; // All standard time units are valid for ISO 8601 duration
    }
    
    @Override
    public String convertUnit(UnitsOfTime timeUnit) {
        if (!isValidUnit(timeUnit)) {
            return null;
        }
        
        // This method only returns the unit part, not the full duration string
        // The actual value formatting is done separately
        switch (timeUnit) {
            case S:
                return "S";
            case MIN:
                return "M";
            case H:
                return "H";
            case D:
                return "D";
            case WK:
                return "W";
            case MO:
                return "M";
            case A:
                return "Y";
            default:
                log.warn("Unexpected time unit: {}", timeUnit);
                return null;
        }
    }
    
    /**
     * Formats a value with the time unit into an ISO 8601 duration string
     * 
     * @param value The numeric value
     * @param timeUnit The time unit
     * @return Formatted ISO 8601 duration string
     */
    public String formatDuration(double value, UnitsOfTime timeUnit) {
        if (!isValidUnit(timeUnit)) {
            return null;
        }
        
        switch (timeUnit) {
            case S:
            case MIN:
            case H:
                return String.format("PT%.0f%s", value, convertUnit(timeUnit));
            case D:
            case WK:
            case MO:
            case A:
                return String.format("P%.0f%s", value, convertUnit(timeUnit));
            default:
                return null;
        }
    }
} 