package com.medblocks.plugins.unit;

import org.hl7.fhir.r4.model.Timing.UnitsOfTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Converter for mapping FHIR time units to OpenEHR frequency units (1/d, 1/h, etc.)
 */
public class FrequencyUnitConverter implements TimeUnitConverter {
    
    private static final Logger log = LoggerFactory.getLogger(FrequencyUnitConverter.class);
    
    @Override
    public boolean isValidUnit(UnitsOfTime timeUnit) {
        if (timeUnit == null) {
            return false;
        }
        
        switch (timeUnit) {
            case S:
            case MIN:
            case H:
            case D:
                return true;
            default:
                log.warn("Unsupported time unit for frequency conversion: {}", timeUnit);
                return false;
        }
    }
    
    @Override
    public String convertUnit(UnitsOfTime timeUnit) {
        if (!isValidUnit(timeUnit)) {
            return null;
        }
        
        switch (timeUnit) {
            case S:
                return "1/s";
            case MIN:
                return "1/min";
            case H:
                return "1/h";
            case D:
                return "1/d";
            default:
                return null; // This shouldn't happen due to isValidUnit check
        }
    }
} 