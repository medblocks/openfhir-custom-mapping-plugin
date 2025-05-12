package com.medblocks.plugins;

import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.gson.JsonObject;
import com.medblocks.openfhir.plugin.api.FormatConverter;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Timing;
import org.hl7.fhir.r4.model.Dosage;
import org.hl7.fhir.r4.model.Range;
import org.hl7.fhir.r4.model.Quantity;

import java.util.function.Supplier;

import com.medblocks.plugins.unit.TimeUnitConverterFactory;

import static com.medblocks.plugins.MappingUtils.*;

public class CustomMapping extends Plugin {

    private static final Logger log = LoggerFactory.getLogger(CustomMapping.class);

    public CustomMapping(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public void start() {
        log.info("Plugin is starting...");
    }

    @Override
    public void stop() {
        log.info("Plugin is stopping...");
    }

    /**
     * Extension implementation for converting between FHIR and OpenEHR formats
     */
    @Extension
    public static class TestFormatConverter implements FormatConverter {
        
        private static final Logger log = LoggerFactory.getLogger(TestFormatConverter.class);
        
        @Override
        public boolean applyFhirToOpenEhrMapping(String mappingCode, String openEhrPath, Object fhirValue, 
                                               String openEhrType, Object flatComposition) {
            log.info("Applying FHIR to OpenEHR mapping function: {}", mappingCode);
            log.info("OpenEHR Path: {}, Value type: {}, OpenEHR Type: {}", openEhrPath, 
                     fhirValue != null ? fhirValue.getClass().getName() : "null", openEhrType);
            
            // Dispatch to the appropriate mapping function based on the mappingCode
            switch (mappingCode) {
                case "dosageDurationToAdministrationDuration":
                    return dosageDurationToAdministrationDuration(openEhrPath, fhirValue, openEhrType, flatComposition);
                case "ratio_to_dv_quantity":
                    return ratio_to_dv_quantity(openEhrPath, fhirValue, openEhrType, flatComposition);
                case "timingToDaily_NonDaily":
                    return timingToDaily_NonDaily(openEhrPath, fhirValue, openEhrType, flatComposition);
                case "dosageQuantityToRange":
                    return dosageQuantityToRange(openEhrPath, fhirValue, openEhrType, flatComposition);
                default:
                    log.warn("Unknown mapping code: {}", mappingCode);
                    return false;
            }
        }
        
        @Override
        public Object applyOpenEhrToFhirMapping(String mappingCode, String openEhrPath, 
                                               JsonObject flatJsonObject, String fhirPath, 
                                               Resource targetResource) {
            log.info("OpenEHR to FHIR mapping is currently disabled");
            return null;
        }
        
        /**
         * Utility method for executing operations with exception handling
         */
        private <T> T executeWithExceptionHandling(String operationName, Supplier<T> operation, T defaultValue) {
            try {
                return operation.get();
            } catch (Exception e) {
                log.error("Error in operation {}: {}", operationName, e.getMessage(), e);
                return defaultValue;
            }
        } 

        /**
         * Mapping function for FHIR Timing to OpenEHR timing_daily cluster
         */
        private boolean timingToDaily_NonDaily(String openEhrPath, Object fhirValue, 
                                      String openEhrType, Object flatComposition) {
            return executeWithExceptionHandling("FHIR Timing to OpenEHR timing_daily", () -> {
                log.info("Converting FHIR Timing to OpenEHR timing_daily");
                
                if (!(fhirValue instanceof Timing)) {
                    log.warn("Expected Timing type but got: {}", fhirValue != null ? fhirValue.getClass().getName() : "null");
                    return false;
                }
                
                Timing timing = (Timing) fhirValue;
                JsonObject flatJson = (JsonObject) flatComposition;
                boolean success = false;
                
                // Only proceed if timing has repeat component
                if (timing.hasRepeat()) {
                    Timing.TimingRepeatComponent repeat = timing.getRepeat();
                    
                    // Map specific time (timeOfDay)
                    if (repeat.hasTimeOfDay() && !repeat.getTimeOfDay().isEmpty()) {
                        String timeOfDay = repeat.getTimeOfDay().get(0).getValue();
                        if (timeOfDay != null) {
                            // Validate and format timeOfDay to match DV_TIME ISO 8601 format
                            String formattedTime = validateAndFormatDvTime(timeOfDay);
                            if (formattedTime != null) {
                                setValueInJson(flatJson, openEhrPath + "/zeitpunkt", formattedTime);
                                log.info("Mapped specific time: {}", formattedTime);
                                success = true;
                            } else {
                                log.warn("Time value '{}' does not conform to DV_TIME format", timeOfDay);
                            }
                        }
                    }
                    
                    // Map frequency
                    if (repeat.hasFrequency()) {
                        int frequency = repeat.getFrequency();
                        
                        // Validate and get the frequency unit using our new converter
                        if (repeat.hasPeriodUnit() && 
                            TimeUnitConverterFactory.getFrequencyConverter().isValidUnit(repeat.getPeriodUnit())) {
                            
                            String unit = TimeUnitConverterFactory.getFrequencyConverter()
                                .convertUnit(repeat.getPeriodUnit());
                            
                            // Only proceed if we have a valid unit
                            if (unit != null) {
                                // Check if frequencyMax exists for range notation
                                if (repeat.hasFrequencyMax()) {
                                    int frequencyMax = repeat.getFrequencyMax();
                                    
                                    // Set the lower value and unit
                                    setValueInJson(flatJson, openEhrPath + "/frequenz/quantity_value/lower|magnitude", frequency);
                                    setValueInJson(flatJson, openEhrPath + "/frequenz/quantity_value/lower|unit", unit);
                                    
                                    // Set the upper value and unit
                                    setValueInJson(flatJson, openEhrPath + "/frequenz/quantity_value/upper|magnitude", frequencyMax);
                                    setValueInJson(flatJson, openEhrPath + "/frequenz/quantity_value/upper|unit", unit);
                                    
                                    log.info("Mapped frequency range: {}-{} {}", frequency, frequencyMax, unit);
                                } else {
                                    // Set single value and unit
                                    setValueInJson(flatJson, openEhrPath + "/frequenz/quantity_value|magnitude", frequency);
                                    setValueInJson(flatJson, openEhrPath + "/frequenz/quantity_value|unit", unit);
                                    
                                    log.info("Mapped frequency: {} {}", frequency, unit);
                                }
                                success = true;
                            } else {
                                log.warn("Skipping frequency mapping due to missing or unsupported period unit");
                            }
                        } else {
                            log.warn("Skipping frequency mapping due to missing or unsupported period unit");
                        }
                    }
                    
                    // Map interval (period)
                    if (repeat.hasPeriod()) {
                        double period = repeat.getPeriod().doubleValue();
                        
                        // Validate period unit using our new converter
                        if (repeat.hasPeriodUnit() && 
                            TimeUnitConverterFactory.getDurationConverter().isValidUnit(repeat.getPeriodUnit())) {
                            
                            Timing.UnitsOfTime periodUnit = repeat.getPeriodUnit();
                            String durationValue = TimeUnitConverterFactory.getDurationConverter()
                                .formatDuration(period, periodUnit);
                            
                            // Check if periodMax exists for range notation
                            if (repeat.hasPeriodMax()) {
                                double periodMax = repeat.getPeriodMax().doubleValue();
                                String durationMaxValue = TimeUnitConverterFactory.getDurationConverter()
                                    .formatDuration(periodMax, periodUnit);
                                
                                // Set the lower and upper duration values
                                setValueInJson(flatJson, openEhrPath + "/intervall/duration_value/lower|value", durationValue);
                                setValueInJson(flatJson, openEhrPath + "/intervall/duration_value/upper|value", durationMaxValue);
                                
                                log.info("Mapped interval range: {} to {}", durationValue, durationMaxValue);
                            } else {
                                // Set single duration value
                                setValueInJson(flatJson, openEhrPath + "/intervall/duration_value", durationValue);
                                
                                log.info("Mapped interval: {}", durationValue);
                            }
                            success = true;
                        } else {
                            log.warn("Skipping interval mapping due to missing or unsupported period unit");
                        }
                    }
                    
                    // Map repeat count to dosierungsreihenfolge
                    if (repeat.hasCount()) {
                        int count = repeat.getCount();
                        // Set the dosierungsreihenfolge value
                        setValueInJson(flatJson, openEhrPath + "/dosierungsreihenfolge", count);
                        log.info("Mapped repeat count to dosierungsreihenfolge: {}", count);
                        success = true;
                    }
                }
                
                return success;
            }, false);
        }

        /**
         * Converts FHIR dosage duration to OpenEHR administration duration
         */
        private boolean dosageDurationToAdministrationDuration(String openEhrPath, Object fhirValue, 
                                                          String openEhrType, Object flatComposition) {
            return executeWithExceptionHandling("dosage duration to administration duration", () -> {
                log.info("Converting timing repeat to administration duration");
                
                if (!(fhirValue instanceof Timing.TimingRepeatComponent)) {
                    log.warn("Expected TimingRepeatComponent but got: {}", 
                           fhirValue != null ? fhirValue.getClass().getName() : "null");
                    return false;
                }
                
                Timing.TimingRepeatComponent repeat = (Timing.TimingRepeatComponent) fhirValue;
                JsonObject flatJson = (JsonObject) flatComposition;
                
                // Check if duration exists
                if (!repeat.hasDuration()) {
                    log.info("No duration found in timing repeat");
                    return false;
                }
                
                double duration = repeat.getDuration().doubleValue();
                
                // Check if durationUnit exists
                if (!repeat.hasDurationUnit()) {
                    log.warn("No duration unit found in timing repeat");
                    return false;
                }
                
                Timing.UnitsOfTime durationUnit = repeat.getDurationUnit();
                
                // Validate duration unit using our new converter
                if (!TimeUnitConverterFactory.getDurationConverter().isValidUnit(durationUnit)) {
                    log.warn("Invalid duration unit: {}", durationUnit);
                    return false;
                }
                
                // Check if we have both duration and durationMax (range case)
                if (repeat.hasDurationMax()) {
                    double durationMax = repeat.getDurationMax().doubleValue();
                    
                    // Convert duration to ISO 8601 format using our new converter
                    String lowerDuration = TimeUnitConverterFactory.getDurationConverter()
                        .formatDuration(duration, durationUnit);
                    String upperDuration = TimeUnitConverterFactory.getDurationConverter()
                        .formatDuration(durationMax, durationUnit);
                    
                    if (lowerDuration == null || upperDuration == null) {
                        log.warn("Could not convert duration to ISO 8601 format");
                        return false;
                    }
                    
                    // Set lower and upper values
                    setValueInJson(flatJson, openEhrPath + "/duration_value/lower|value", lowerDuration);
                    setValueInJson(flatJson, openEhrPath + "/duration_value/upper|value", upperDuration);
                    
                    log.info("Mapped administration duration range: {} to {}", lowerDuration, upperDuration);
                } else {
                    // Convert single duration to ISO 8601 format using our new converter
                    String durationStr = TimeUnitConverterFactory.getDurationConverter()
                        .formatDuration(duration, durationUnit);
                    
                    if (durationStr == null) {
                        log.warn("Could not convert duration to ISO 8601 format");
                        return false;
                    }
                    
                    // Set single duration value
                    setValueInJson(flatJson, openEhrPath + "/duration_value|value", durationStr);
                    
                    log.info("Mapped administration duration: {}", durationStr);
                }
                
                return true;
            }, false);
        }
        
        /**
         * Converts FHIR Ratio to OpenEHR DV_QUANTITY
         * Also handles Dosage.DosageAndRate.rate.rateRatio for verabreichungsrate
         */
        private boolean ratio_to_dv_quantity(String openEhrPath, Object fhirValue, 
                                     String openEhrType, Object flatComposition) {
            return executeWithExceptionHandling("FHIR Ratio to OpenEHR DV_QUANTITY", () -> {
                log.info("Converting FHIR Ratio to OpenEHR Administration Rate");
                
                // Set of allowed units for verabreichungsrate
                final String[] ALLOWED_RATE_UNITS = {"l/h", "ml/min", "ml/s", "ml/h"};
                
                Object ratioValue = fhirValue;
                boolean isRateRatio = false;
                
                // Check if the value is Dosage.DosageDoseAndRateComponent
                if (fhirValue instanceof Dosage.DosageDoseAndRateComponent) {
                    Dosage.DosageDoseAndRateComponent doseAndRate = 
                        (Dosage.DosageDoseAndRateComponent) fhirValue;
                    
                    // Check if it has rateRatio
                    if (doseAndRate.hasRateRatio()) {
                        ratioValue = doseAndRate.getRateRatio();
                        isRateRatio = true;
                        log.info("Found rateRatio in DosageDoseAndRateComponent");
                    } else {
                        log.info("DosageDoseAndRateComponent doesn't have rateRatio, using value directly");
                    }
                }
                
                // Validate the ratio (either direct ratio or rateRatio)
                ValidationResult validation = validateRatio(ratioValue, "ratio conversion");
                if (!validation.success || !validation.numeratorValid || !validation.denominatorValid) {
                    log.warn("Invalid ratio structure for conversion");
                    return false;
                }
                
                JsonObject flatJson = (JsonObject) flatComposition;
                
                // For rateRatio handling (verabreichungsrate)
                if (isRateRatio) {
                    // Calculate magnitude (numerator value / denominator value)
                    double magnitude = validation.numeratorValue / validation.denominatorValue;
                    
                    // Create the unit string (numerator.unit / denominator.unit)
                    String unitString = validation.numeratorUnit + "/" + validation.denominatorUnit;
                    
                    // Normalize unit string (convert units to standard form if needed)
                    String normalizedUnit = normalizeUnitString(unitString);
                    
                    // Check if the normalized unit is in the allowed list
                    boolean unitAllowed = false;
                    for (String allowedUnit : ALLOWED_RATE_UNITS) {
                        if (normalizedUnit.equals(allowedUnit)) {
                            unitAllowed = true;
                            break;
                        }
                    }
                    
                    if (!unitAllowed) {
                        log.warn("Unit '{}' is not in the allowed list for verabreichungsrate", normalizedUnit);
                        return false;
                    }
                    
                    // Set magnitude and unit
                    setValueInJson(flatJson, openEhrPath + "/quantity_value|magnitude", magnitude);
                    setValueInJson(flatJson, openEhrPath + "/quantity_value|unit", normalizedUnit);
                    
                    log.info("Mapped rateRatio to verabreichungsrate: magnitude={}, unit={}", 
                             magnitude, normalizedUnit);
                    return true;
                }
                // Standard ratio handling (for other cases)
                else {
                    // Format as numerator/denominator (e.g., "600 mg/h")
                    String formattedRate = validation.numeratorValue + " " + validation.numeratorUnit;
                    if (validation.denominatorValid) {
                        formattedRate += "/" + validation.denominatorUnit;
                    }
                    
                    // Set the formatted rate directly on the path
                    setValueInJson(flatJson, openEhrPath, formattedRate);
                    
                    log.info("Mapped Ratio to Administration Rate: path={}, value={}", 
                             openEhrPath, formattedRate);
                    return true;
                }
            }, false);
        }
        
        /**
         * Normalizes unit strings to standard format for comparison with allowed units.
         * Handles cases like "milliliter/hour" -> "ml/h" for consistent checking.
         */
        private String normalizeUnitString(String unitString) {
            // Convert full names to abbreviations if needed
            String normalized = unitString.toLowerCase()
                .replace("liter", "l")
                .replace("milliliter", "ml")
                .replace("hour", "h")
                .replace("minute", "min")
                .replace("second", "s")
                // Clean up any spaces around the slash
                .replace(" / ", "/")
                .replace(" /", "/")
                .replace("/ ", "/");
            
            // Handle special unit combinations
            if (normalized.equals("ml/hour")) normalized = "ml/h";
            if (normalized.equals("l/hour")) normalized = "l/h";
            if (normalized.equals("ml/minute")) normalized = "ml/min";
            if (normalized.equals("ml/second")) normalized = "ml/s";
            
            return normalized;
        }

        /**
         * Converts FHIR Dosage dose (Quantity or Range) to OpenEHR Range
         * This specifically handles the dose component from Dosage.DosageAndRate.dose,
         * which can be either a Range or Quantity.
         */
        private boolean dosageQuantityToRange(String openEhrPath, Object fhirValue, 
                                     String openEhrType, Object flatComposition) {
            return executeWithExceptionHandling("dosageQuantityToRange", () -> {
                log.info("Converting FHIR Dosage dose to OpenEHR Range/Quantity");
                
                JsonObject flatJson = (JsonObject) flatComposition;
                
                // The fhirValue should directly be the dose, which is either a Range or Quantity
                // Check if the dose is a Range
                if (fhirValue instanceof Range) {
                    Range doseRange = (Range) fhirValue;
                    
                    // Check if we have a valid low value
                    if (doseRange.hasLow() && doseRange.getLow().hasValue()) {
                        setValueInJson(flatJson, openEhrPath + "/quantity_value/lower|magnitude", 
                                     doseRange.getLow().getValue().doubleValue());
                        
                        // Set the unit if present
                        if (doseRange.getLow().hasUnit()) {
                            setValueInJson(flatJson, openEhrPath + "/quantity_value/lower|unit", 
                                         doseRange.getLow().getUnit());
                        }
                    } else {
                        log.warn("DoseRange is missing required low value");
                        return false;
                    }
                    
                    // Check if we have a valid high value
                    if (doseRange.hasHigh() && doseRange.getHigh().hasValue()) {
                        setValueInJson(flatJson, openEhrPath + "/quantity_value/upper|magnitude", 
                                     doseRange.getHigh().getValue().doubleValue());
                        
                        // Set the unit if present
                        if (doseRange.getHigh().hasUnit()) {
                            setValueInJson(flatJson, openEhrPath + "/quantity_value/upper|unit", 
                                         doseRange.getHigh().getUnit());
                        }
                    } else {
                        log.warn("DoseRange is missing required high value");
                        return false;
                    }
                    
                    log.info("Mapped DoseRange to OpenEHR Range");
                    return true;
                }
                // Check if the dose is a Quantity
                else if (fhirValue instanceof Quantity) {
                    Quantity doseQuantity = (Quantity) fhirValue;
                    
                    // Check if we have a valid value
                    if (doseQuantity.hasValue()) {
                        setValueInJson(flatJson, openEhrPath + "/quantity_value|magnitude", 
                                     doseQuantity.getValue().doubleValue());
                        
                        // Set the unit if present
                        if (doseQuantity.hasUnit()) {
                            setValueInJson(flatJson, openEhrPath + "/quantity_value|unit", 
                                         doseQuantity.getUnit());
                        }
                        
                        log.info("Mapped DoseQuantity to OpenEHR Quantity");
                        return true;
                    } else {
                        log.warn("DoseQuantity is missing required value");
                        return false;
                    }
                } else {
                    log.warn("Expected Range or Quantity type for dose but got: {}", 
                           fhirValue != null ? fhirValue.getClass().getName() : "null");
                    return false;
                }
            }, false);
        }
    }
} 
