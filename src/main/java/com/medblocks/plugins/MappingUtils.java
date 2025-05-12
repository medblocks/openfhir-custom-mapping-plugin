package com.medblocks.plugins;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Ratio;
import org.hl7.fhir.r4.model.Timing.UnitsOfTime;
import org.hl7.fhir.r4.model.Timing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.medblocks.plugins.unit.TimeUnitConverterFactory;

/**
 * Utility class containing helper methods for FHIR to OpenEHR mapping operations
 */
public class MappingUtils {
    private static final Logger log = LoggerFactory.getLogger(MappingUtils.class);

    /**
     * Utility method for executing operations with exception handling
     */
    public static <T> T executeWithExceptionHandling(String operationName, Supplier<T> operation, T defaultValue) {
        try {
            return operation.get();
        } catch (Exception e) {
            log.error("Error in operation {}: {}", operationName, e.getMessage(), e);
            return defaultValue;
        }
    }

    /**
     * Sets a value in a flat JSON object
     */
    public static void setValueInJson(JsonObject jsonObject, String path, Object value) {
        if (jsonObject == null) {
            return;
        }
        
        try {
            if (value instanceof String) {
                jsonObject.add(path, new JsonPrimitive((String) value));
            } else if (value instanceof Number) {
                jsonObject.add(path, new JsonPrimitive((Number) value));
            } else if (value instanceof Boolean) {
                jsonObject.add(path, new JsonPrimitive((Boolean) value));
            }
        } catch (Exception e) {
            log.error("Failed to set value at path {}: {}", path, e.getMessage());
        }
    }

    /**
     * Validates and formats a time string to ensure it conforms to DV_TIME ISO 8601 format.
     * Valid formats are:
     * - Extended (preferred): hh:mm:ss[(,|.)sss][Z|±hh[:mm]]
     * - Compact: hhmmss[(,|.)sss][Z|±hh[mm]]
     * 
     * @param timeStr The time string to validate
     * @return The validated/formatted time string, or null if invalid
     */
    public static String validateAndFormatDvTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return null;
        }
        
        // Regular expressions for extended and compact formats
        // Extended: hh:mm:ss[(,|.)sss][Z|±hh[:mm]]
        String extendedPattern = "^([01]\\d|2[0-3]):([0-5]\\d):([0-5]\\d)([,.][0-9]+)?(Z|[+-]([01]\\d|2[0-3])(:?[0-5]\\d)?)?$";
        
        // Compact: hhmmss[(,|.)sss][Z|±hh[mm]]
        String compactPattern = "^([01]\\d|2[0-3])([0-5]\\d)([0-5]\\d)([,.][0-9]+)?(Z|[+-]([01]\\d|2[0-3])([0-5]\\d)?)?$";
        
        // Check if time string already matches one of the patterns
        if (timeStr.matches(extendedPattern)) {
            return timeStr; // Already in extended format
        }
        
        if (timeStr.matches(compactPattern)) {
            // Convert compact to extended format
            try {
                // Extract hours, minutes, seconds
                String hours = timeStr.substring(0, 2);
                String minutes = timeStr.substring(2, 4);
                String seconds = timeStr.substring(4, 6);
                
                // Start with basic extended format
                String formattedTime = hours + ":" + minutes + ":" + seconds;
                
                // Handle fraction and timezone if present
                if (timeStr.length() > 6) {
                    String remainder = timeStr.substring(6);
                    
                    // Check for fraction
                    if (remainder.startsWith(",") || remainder.startsWith(".")) {
                        int tzIndex = -1;
                        for (int i = 1; i < remainder.length(); i++) {
                            char c = remainder.charAt(i);
                            if (c == 'Z' || c == '+' || c == '-') {
                                tzIndex = i;
                                break;
                            }
                            if (!Character.isDigit(c)) {
                                return null; // Invalid character in fraction
                            }
                        }
                        
                        if (tzIndex > 0) {
                            formattedTime += remainder.substring(0, tzIndex);
                            remainder = remainder.substring(tzIndex);
                        } else {
                            formattedTime += remainder;
                            remainder = "";
                        }
                    }
                    
                    // Handle timezone
                    if (!remainder.isEmpty()) {
                        if (remainder.equals("Z")) {
                            formattedTime += "Z";
                        } else if (remainder.startsWith("+") || remainder.startsWith("-")) {
                            String sign = remainder.substring(0, 1);
                            remainder = remainder.substring(1);
                            
                            if (remainder.length() >= 2) {
                                String tzHours = remainder.substring(0, 2);
                                formattedTime += sign + tzHours;
                                
                                if (remainder.length() >= 4) {
                                    String tzMinutes = remainder.substring(2, 4);
                                    formattedTime += ":" + tzMinutes;
                                }
                            }
                        }
                    }
                }
                
                return formattedTime;
            } catch (Exception e) {
                log.error("Error converting compact time format to extended: {}", e.getMessage());
                return null;
            }
        }
        
        // Try to interpret common time formats and convert to extended ISO format
        try {
            // Handle simple "HH:mm" format
            if (timeStr.matches("^([01]\\d|2[0-3]):([0-5]\\d)$")) {
                return timeStr + ":00"; // Add seconds
            }
            
            // Handle simple "HH" format
            if (timeStr.matches("^([01]\\d|2[0-3])$")) {
                return timeStr + ":00:00"; // Add minutes and seconds
            }
        } catch (Exception e) {
            log.error("Error parsing time format: {}", e.getMessage());
        }
        
        // Could not validate or transform the time string
        return null;
    }
    
    /**
     * Validates a FHIR Ratio and extracts its components
     */
    public static ValidationResult validateRatio(Object fhirValue, String context) {
        ValidationResult result = new ValidationResult();
        
        if (fhirValue == null) {
            log.warn("No FHIR value provided for {}", context);
            result.success = false;
            return result;
        }
        
        if (!(fhirValue instanceof Ratio)) {
            log.warn("Expected Ratio type for {} but got: {}", context, fhirValue.getClass().getName());
            result.success = false;
            return result;
        }
        
        Ratio ratio = (Ratio) fhirValue;
        result.ratio = ratio;
        
        // Extract numerator data
        Quantity numerator = ratio.getNumerator();
        if (numerator != null && numerator.getValue() != null) {
            result.numeratorValue = numerator.getValue().doubleValue();
            result.numeratorUnit = numerator.getUnit() != null ? numerator.getUnit() : numerator.getCode();
        } else {
            result.numeratorValid = false;
        }
        
        // Extract denominator data
        Quantity denominator = ratio.getDenominator();
        if (denominator != null && denominator.getValue() != null) {
            result.denominatorValue = denominator.getValue().doubleValue();
            result.denominatorUnit = denominator.getUnit() != null ? denominator.getUnit() : denominator.getCode();
        } else {
            result.denominatorValid = false;
        }
        
        result.success = true;
        return result;
    }
    
    /**
     * Helper class to store validation results
     */
    public static class ValidationResult {
        public boolean success = true;
        public Ratio ratio;
        
        public double numeratorValue;
        public String numeratorUnit = "";
        public boolean numeratorValid = true;
        
        public double denominatorValue;
        public String denominatorUnit = "";
        public boolean denominatorValid = true;
    }
    
    /**
     * Helper method to extract numeric value from ISO 8601 duration string
     */
    public static int extractNumericValue(String durationStr, String unitChar) {
        try {
            Pattern pattern = Pattern.compile("(\\d+)" + unitChar);
            Matcher matcher = pattern.matcher(durationStr);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
            
            // For cases like "PT3H" where it might be in a different format
            if (unitChar.equals("H")) {
                pattern = Pattern.compile("PT(\\d+)H");
                matcher = pattern.matcher(durationStr);
                if (matcher.find()) {
                    return Integer.parseInt(matcher.group(1));
                }
            }
            
            if (unitChar.equals("M")) {
                pattern = Pattern.compile("PT(\\d+)M");
                matcher = pattern.matcher(durationStr);
                if (matcher.find()) {
                    return Integer.parseInt(matcher.group(1));
                }
            }
            
            if (unitChar.equals("S")) {
                pattern = Pattern.compile("PT(\\d+)S");
                matcher = pattern.matcher(durationStr);
                if (matcher.find()) {
                    return Integer.parseInt(matcher.group(1));
                }
            }
            
            log.warn("Could not extract numeric value for unit {} from duration: {}", unitChar, durationStr);
            return 0;
        } catch (Exception e) {
            log.error("Error extracting numeric value from duration: {}", durationStr, e);
            return 0;
        }
    }
    
    /**
     * Converts FHIR Timing period unit to OpenEHR frequency unit format.
     * The allowed OpenEHR units are 1/d, 1/h, 1/min, 1/s based on the constraints.
     * 
     * @param repeat The FHIR Timing repeat component
     * @return The appropriate OpenEHR frequency unit or null if no valid unit
     */
    public static String getFrequencyUnit(Timing.TimingRepeatComponent repeat) {
        if (!repeat.hasPeriodUnit()) {
            return null;
        }
        
        UnitsOfTime periodUnit = repeat.getPeriodUnit();
        return TimeUnitConverterFactory.getFrequencyConverter().convertUnit(periodUnit);
    }
    
    /**
     * Converts FHIR Timing period and periodUnit to ISO 8601 duration format.
     * 
     * @param period The period value as a decimal
     * @param periodUnit The time unit
     * @return ISO 8601 duration string or null if invalid unit
     */
    public static String periodToDuration(double period, UnitsOfTime periodUnit) {
        return TimeUnitConverterFactory.getDurationConverter().formatDuration(period, periodUnit);
    }
    
    /**
     * Checks if periodUnit is a valid time unit for OpenEHR interval.
     * 
     * @param periodUnit The FHIR time unit
     * @return true if valid, false otherwise
     */
    public static boolean isValidIntervalUnit(UnitsOfTime periodUnit) {
        return TimeUnitConverterFactory.getDurationConverter().isValidUnit(periodUnit);
    }

    
} 


