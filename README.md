# OpenFHIR Custom Mapping Plugin

A PF4J-based plugin for mapping FHIR to OpenEHR format for the [openFHIR](https://github.com/medblocks/openFHIR) project

## Key Features

- **Medication Timing Mapping**: Converts FHIR Timing resources to OpenEHR timing_daily clusters, handling:

  - Time of day specifications
  - Frequency and interval calculations
  - Period-based scheduling

- **Dosage Rate Conversion**: Transforms FHIR Ratio resources to OpenEHR DV_QUANTITY format for medication administration rates

- **Duration Mapping**: Handles conversion of FHIR Timing.repeat duration to OpenEHR administration duration, including:
  - Single duration values
  - Range-based durations
  - Unit conversions

## Technical Details

- Built with Java 17
- Uses PF4J (Plugin Framework for Java) for extensibility

## Prerequisites

- Java Development Kit (JDK) 17 or higher
- Maven 3.6.0 or higher
- Git

## Building the Plugin

1. Clone the repository:

   ```bash
   git clone https://github.com/your-username/openfhir-custom-mapping-plugin.git
   cd openfhir-custom-mapping-plugin
   ```

2. Build the project:

   ```bash
   mvn clean package
   ```

3. The built JAR file will be available in the `target` directory:
   ```
   target/openfhir-custom-mapping-plugin-1.0.0.jar
   ```

## Installation

1. Copy the generated JAR file to the [openFHIR](https://github.com/medblocks/openFHIR) plugin directory
2. Ensure the plugin dependencies are available in your application's classpath.
3. The plugin will be automatically loaded by the PF4J framework

## Dependencies

- PF4J 3.11.0
- HAPI FHIR 7.2.1
- OpenFHIR Plugin API 1.0.0
- Gson 2.10.1

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support

For support, support@medblocks.com
