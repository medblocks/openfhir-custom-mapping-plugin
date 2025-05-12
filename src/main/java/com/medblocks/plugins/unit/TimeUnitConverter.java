package com.medblocks.plugins.unit;

import org.hl7.fhir.r4.model.Timing.UnitsOfTime;

/**
 * Interface for time unit conversion following the Single Responsibility and Open/Closed principles.
 * Each implementation should focus on a specific conversion type.
 */
public interface TimeUnitConverter {
    
    /**
     * Checks if the provided time unit is valid for this converter
     * 
     * @param timeUnit The time unit to check
     * @return true if valid, false otherwise
     */
    boolean isValidUnit(UnitsOfTime timeUnit);
    
    /**
     * Converts the time unit to the appropriate format
     * 
     * @param timeUnit The time unit to convert
     * @return Converted unit string or null if not valid
     */
    String convertUnit(UnitsOfTime timeUnit);
} 