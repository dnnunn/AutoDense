# AutoDense Demo System

> **Doc Meta**
> - **Purpose:** Demo system for testing and showcasing AutoDense capabilities
> - **Scope:** Demo workflows, test datasets, and presentation modes
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-08-26

## Overview

The AutoDense demo system provides interactive tutorials and examples that enhance user experience through guided learning, feature discovery, and validation testing.

## Strategic Benefits

### 1. **New User Onboarding**
- Step-by-step guided tutorials for first-time users
- Progressive complexity from basic to advanced features
- Visual examples with expected outputs for validation

### 2. **Feature Discovery**
- Showcase advanced capabilities users might overlook
- Demonstrate real-world workflow scenarios
- Interactive examples that users can modify and extend

### 3. **Educational Value**
- Academic and research training materials
- Reproducible examples for classroom use
- Quality assurance reference implementations

### 4. **Troubleshooting Support**
- Known-working examples for bug reporting
- Performance benchmarking and regression testing
- Validation of installation and configuration

## Directory Structure

```
autodense/plugin/src/main/java/com/betterdairy/autodense/demos/
├── gel-analysis/           # Gel electrophoresis workflows
│   ├── BandAssistDemo.java       # Interactive band identification
│   ├── CoreDetectorDemo.java     # Lane/band detection algorithms
│   ├── StandardCurveDemo.java    # Molecular weight calibration
│   ├── LaneComparisonDemo.java   # Protein expression comparison
│   ├── PCRNormalizationDemo.java # Reference sample normalization
│   └── FunctionalApiDemo.java    # Functional API examples
├── colony-analysis/        # Colony counting and classification
│   ├── ColonyClassifierDemo.java # X-gal color classification
│   ├── ColonyAnalysisDemo.java   # Automated counting/sizing
│   ├── ColonyAssistDemo.java     # User-guided identification
│   ├── ColonyNormalizerDemo.java # Colony normalization methods
│   └── PlateAnalysisDefaultsDemo.java # Default parameter testing
├── integration/           # Complete pipeline demonstrations
│   └── IntegrationDemo.java      # End-to-end workflow
├── performance/           # Speed and accuracy testing
│   ├── SyntheticGelTest.java     # Performance with synthetic data
│   └── ResponseShapeDemo.java    # API response standardization
└── README.md             # Demo system documentation
```

## UI Integration

### Access Method
- **Menu Path**: Help > Demos & Tutorials
- **Organization**: Hierarchical submenus by category
- **Execution**: Click-to-run with real-time output capture

### Demo Categories

#### Gel Analysis Demos
- **Band Assist Demo**: Interactive band identification across lanes
- **Core Detection Demo**: Core lane and band detection algorithms  
- **Standard Curve Demo**: Molecular weight calibration curves
- **Lane Comparison Demo**: Compare protein expressions between lanes
- **PCR Normalization Demo**: Normalize gel bands to reference samples

#### Colony Analysis Demos
- **Colony Classifier Demo**: X-gal color classification with background sampling
- **Colony Detection Demo**: Automated colony counting and sizing
- **Colony Assist Demo**: User-guided colony identification

#### System & Performance Demos
- **Integration Demo**: Complete workflow pipeline demonstration
- **Synthetic Gel Performance**: Performance testing with synthetic data
- **Response Shape Demo**: Tool response standardization

### User Experience Features

#### Real-Time Execution
- Demos run in background threads (non-blocking UI)
- Live output streaming to chat area
- Progress indicators in status bar
- Error handling with helpful messages

#### Output Capture
- System.out redirection during demo execution
- Formatted output with emojis and structure
- Success/failure indicators
- Exception details for troubleshooting

#### Interactive Elements
- Tooltip descriptions for each demo
- Visual progress feedback
- Click-to-expand output sections
- Copy-paste friendly results

## Implementation Details

### Demo Execution Framework

```java
private void runDemo(String className, String name, String description) {
    // Background execution with SwingWorker
    // System.out capture and redirection
    // Reflection-based class loading and execution
    // Real-time UI updates via publish/process pattern
}
```

### Package Organization
- **Consistent Naming**: All demos follow `*Demo.java` convention
- **Package Structure**: Mirrors functional organization
- **Import Management**: Clean separation from main codebase
- **Main Method**: Standard entry point for all demos

### Error Handling
- **Class Not Found**: Graceful handling of missing demo classes
- **Method Missing**: Validation of main method presence
- **Execution Errors**: Exception capture and user-friendly messages
- **Output Buffering**: Safe handling of demo output streams

## Usage Scenarios

### 1. First-Time User Tutorial
```
Help > Demos & Tutorials > Gel Analysis > Core Detection Demo
→ Shows basic lane/band detection with synthetic gel
→ User sees expected output and can compare with their results
→ Builds confidence in tool functionality
```

### 2. Advanced Feature Exploration
```
Help > Demos & Tutorials > Gel Analysis > Band Assist Demo
→ Demonstrates interactive band identification
→ User discovers click-to-identify functionality
→ Learns about confidence scoring and validation
```

### 3. Academic Classroom Use
```
Instructor: "Everyone run the Standard Curve Demo"
→ Consistent synthetic data ensures identical results
→ Students learn molecular weight calibration concepts
→ Reproducible examples for homework/exams
```

### 4. Bug Reporting Support
```
User: "Band detection isn't working properly"
Support: "Please run the Core Detection Demo and send output"
→ Provides known-working reference for comparison
→ Helps isolate whether issue is algorithmic or data-specific
```

## Future Enhancements

### Interactive Tutorials
- Step-by-step guided workflows with user interaction
- Progress tracking and completion indicators
- Adaptive difficulty based on user experience

### Custom Demo Creation
- User-generated demo templates
- Save/load custom workflows as demos
- Community sharing of demo scenarios

### Performance Benchmarking
- Automated performance comparison across versions
- Memory usage and timing analysis
- Regression testing integration

### Visual Examples
- Bundled sample gel/colony images
- Before/after comparison galleries
- Interactive parameter adjustment demos

## Technical Benefits

### Code Quality
- **Living Documentation**: Demos serve as executable specifications
- **Regression Testing**: Validates functionality across updates
- **API Usage Examples**: Shows proper tool usage patterns

### User Adoption
- **Reduced Learning Curve**: Interactive tutorials accelerate learning
- **Feature Discovery**: Users find advanced capabilities organically
- **Confidence Building**: Working examples validate installation

### Support Efficiency
- **Self-Service Learning**: Users can explore features independently  
- **Standardized References**: Consistent examples for support cases
- **Quality Validation**: Users can verify proper functionality

## Conclusion

The organized demo system transforms scattered example code into a comprehensive learning and validation framework. By providing structured, accessible demonstrations through the UI, we enhance user experience while reducing support burden and accelerating feature adoption.

The hierarchical organization makes demos discoverable and relevant, while the execution framework ensures they remain current and functional as the codebase evolves.