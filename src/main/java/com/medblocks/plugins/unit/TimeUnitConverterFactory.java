package com.medblocks.plugins.unit;

/**
 * Factory for creating appropriate time unit converters
 * Following the Factory pattern to create the right converter based on the conversion type
 */
public class TimeUnitConverterFactory {
    
    // Singleton instances to avoid recreating converters
    private static final FrequencyUnitConverter FREQUENCY_CONVERTER = new FrequencyUnitConverter();
    private static final DurationUnitConverter DURATION_CONVERTER = new DurationUnitConverter();
    
    /**
     * Get the frequency unit converter (for converting period units to frequency units)
     * 
     * @return FrequencyUnitConverter instance
     */
    public static FrequencyUnitConverter getFrequencyConverter() {
        return FREQUENCY_CONVERTER;
    }
    
    /**
     * Get the duration unit converter (for converting time units to ISO 8601 durations)
     * 
     * @return DurationUnitConverter instance
     */
    public static DurationUnitConverter getDurationConverter() {
        return DURATION_CONVERTER;
    }
} 