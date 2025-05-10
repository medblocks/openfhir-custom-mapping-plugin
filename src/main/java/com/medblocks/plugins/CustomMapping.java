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
import java.util.function.Supplier;

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
                        String unit = MappingUtils.getFrequencyUnit(repeat);
                        
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
                    }
                    
                    // Map interval (period)
                    if (repeat.hasPeriod()) {
                        double period = repeat.getPeriod().doubleValue();
                        
                        // Only proceed if we have a valid unit
                        if (repeat.hasPeriodUnit() && MappingUtils.isValidIntervalUnit(repeat.getPeriodUnit())) {
                            org.hl7.fhir.r4.model.Timing.UnitsOfTime periodUnit = repeat.getPeriodUnit();
                            String durationValue = MappingUtils.periodToDuration(period, periodUnit);
                            
                            // Check if periodMax exists for range notation
                            if (repeat.hasPeriodMax()) {
                                double periodMax = repeat.getPeriodMax().doubleValue();
                                String durationMaxValue = MappingUtils.periodToDuration(periodMax, periodUnit);
                                
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
                
                // Format the duration, with range if durationMax exists
                double duration = repeat.getDuration().doubleValue();
                String durationText = String.valueOf(duration);
                
                if (repeat.hasDurationMax()) {
                    double durationMax = repeat.getDurationMax().doubleValue();
                    durationText = duration + "-" + durationMax;
                }
                
                // Add unit if available
                if (repeat.hasDurationUnit()) {
                    durationText += " " + repeat.getDurationUnit().getDisplay().toLowerCase();
                }
                
                // Add the duration to the flat composition
                setValueInJson(flatJson, openEhrPath, durationText);
                
                log.info("Mapped administration duration: {}", durationText);
                return true;
            }, false);
        }
        
        /**
         * Converts FHIR Ratio to OpenEHR DV_QUANTITY
         */
        private boolean ratio_to_dv_quantity(String openEhrPath, Object fhirValue, 
                                     String openEhrType, Object flatComposition) {
            return executeWithExceptionHandling("FHIR Ratio to OpenEHR DV_QUANTITY", () -> {
                log.info("Converting FHIR Ratio to OpenEHR Administration Rate");
                
                ValidationResult validation = validateRatio(fhirValue, "ratio conversion");
                if (!validation.success || !validation.numeratorValid) {
                    return false;
                }
                
                JsonObject flatJson = (JsonObject) flatComposition;
                
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
            }, false);
        }
    }
} 
