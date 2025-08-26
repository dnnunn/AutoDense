# AutoDense Dependency Management Strategy

> **Doc Meta**
> - **Purpose:** Dependency management strategy and library selection criteria
> - **Scope:** Maven dependencies, version management, and compatibility requirements
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-08-26

## Overview

AutoDense uses a hybrid approach for managing dependencies to avoid version conflicts with Fiji while ensuring proper functionality of ServiceLoader-based libraries like TwelveMonkeys ImageIO.

## Strategy Summary

| Dependency Type | Approach | Rationale | Examples |
|----------------|----------|-----------|----------|
| **ServiceLoader/SPI** | 🔹 **INJECT** | Requires META-INF/services discovery at runtime | TwelveMonkeys ImageIO codecs |
| **Version-Sensitive Stacks** | 🔸 **SHADE** | Fiji often has different versions, causing conflicts | Jackson, Gson, OkHttp, Apache Commons |
| **Fiji Built-ins** | ✅ **AVOID** | Already provided by Fiji, would cause duplicates | ImageJ, SciJava, ImgLib2 |
| **Native Bindings** | 🔹 **INJECT** | Platform-specific libraries need direct access | JNA, JavaCPP (when needed) |

## Implementation Details

### 1. Injection Strategy (lib/ directory)

**What gets injected:**
- `imageio-core-3.12.0.jar` - TwelveMonkeys core
- `imageio-jpeg-3.12.0.jar` - Enhanced JPEG support  
- `imageio-tiff-3.12.0.jar` - Extended TIFF support
- `imageio-bmp-3.12.0.jar` - BMP support
- `imageio-metadata-3.12.0.jar` - Metadata handling
- `autodense-nl-0.1.0-SNAPSHOT.jar` - AutoDense NLP module
- `json-20240303.jar` - Lightweight JSON library
- Other dependencies that don't conflict with Fiji

**Runtime injection process:**
1. `EnhancedImageJLauncher.injectLibJars()` discovers lib directory
2. Uses reflection to add JARs to URLClassLoader (Java 8/11/17 compatible)
3. ServiceLoader automatically discovers ImageIO codecs
4. Detailed logging shows injection success/failure

**App bundle structure:**
```
AutoDense.app/Contents/Resources/java/
├── autodense-plugin-0.1.0-SNAPSHOT.jar    # Main JAR with shaded deps
└── lib/                                     # Injected dependencies (~162 JARs)
    ├── imageio-*.jar                       # TwelveMonkeys codecs
    ├── autodense-nl-*.jar                  # NLP module
    └── [other non-conflicting deps]        # Safe to inject
```

### 2. Shading Strategy (inside main JAR)

**What gets shaded:**
- `com.fasterxml.jackson.*` → `com.betterdairy.autodense.shaded.jackson.*`
- `com.google.gson.*` → `com.betterdairy.autodense.shaded.gson.*`  
- `okhttp3.*` → `com.betterdairy.autodense.shaded.okhttp3.*`
- `okio.*` → `com.betterdairy.autodense.shaded.okio.*`
- `org.apache.http.*` → `com.betterdairy.autodense.shaded.http.*`
- `org.apache.commons.*` → `com.betterdairy.autodense.shaded.*`

**Shading configuration (pom.xml):**
```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-shade-plugin</artifactId>
  <configuration>
    <relocations>
      <!-- Version-sensitive relocations -->
    </relocations>
    <transformers>
      <!-- Preserve META-INF/services for ServiceLoader -->
      <transformer implementation="org.apache.maven.plugins.shade.resource.ServicesResourceTransformer"/>
    </transformers>
  </configuration>
</plugin>
```

### 3. Build Process

**Build script (`build_and_package.sh`):**
1. **Maven build** with shading enabled
2. **Copy main JAR** to app bundle (contains shaded dependencies)
3. **Copy dependencies** to lib/ directory
4. **Remove shaded JARs** from lib/ directory (avoid duplication)
5. **Verify TwelveMonkeys** JARs are present for injection
6. **Create verification scripts** for troubleshooting

**Exclusion process:**
```bash
# Copy all runtime dependencies
mvn dependency:copy-dependencies

# Remove shaded dependencies to avoid conflicts
rm lib/jackson-*.jar lib/gson-*.jar lib/okhttp-*.jar lib/commons-*.jar
```

## Classpath Audit System

**Audit capabilities:**
- ✅ Verify ImageIO codec registration (TwelveMonkeys)
- ⚠️ Detect version conflicts (Jackson, Gson, OkHttp present)
- ❌ Identify missing critical dependencies
- 📊 Count supported image formats
- 🔍 Check for Bio-Formats, Excel support

**Usage:**
```java
// Automatic during startup
ClasspathAudit.run();

// Manual summary
String summary = ClasspathAudit.getAuditSummary();
```

**Sample audit output:**
```
📸 ImageIO Codec Audit:
  HEIC reader present: ❌ NO (expected)
  TwelveMonkeys JPEG: ✅ YES
  TwelveMonkeys TIFF: ✅ YES
  Total formats supported: 25

📄 JSON Libraries Audit:
  org.json: ✅ Present
  Jackson: ⚠️ Present (should be SHADED)
  Gson: ⚠️ Present (should be SHADED)
```

## Version Conflict Resolution

### Common Problems and Solutions

**Problem**: Jackson version conflicts with Fiji
- **Solution**: Shade Jackson into AutoDense namespace
- **Result**: AutoDense uses `com.betterdairy.autodense.shaded.jackson.*`

**Problem**: Missing ImageIO codecs for HEIC/enhanced JPEG
- **Solution**: Inject TwelveMonkeys JARs before Fiji boots
- **Result**: ServiceLoader finds codecs in lib/ directory

**Problem**: Duplicate Apache Commons libraries
- **Solution**: Shade Commons into AutoDense, exclude from injection
- **Result**: No conflicts with Fiji's Commons versions

### Development vs Production

**Development Mode (Maven classpath):**
- All dependencies visible (including shaded ones)
- Audit shows warnings about "should be shaded" dependencies
- TwelveMonkeys works via Maven dependency resolution

**Production Mode (App bundle):**
- Shaded dependencies isolated inside main JAR
- Only essential dependencies in lib/ directory
- Clean separation prevents version conflicts

## Future Enhancements

### HEIC Support
Currently recommends conversion (HEIC → JPEG):
- **Reason**: Platform-specific native libraries required
- **Future**: Consider adding imageio-heif when stable on macOS
- **Workaround**: "For HEIC images: please convert to JPEG/PNG format first"

### Excel Export Support
To add Apache POI for Excel export:
```xml
<!-- Shade POI to avoid XML library conflicts -->
<dependency>
  <groupId>org.apache.poi</groupId>
  <artifactId>poi-ooxml</artifactId>
  <version>5.2.4</version>
</dependency>
```
- **Approach**: SHADE (not inject) to avoid xmlbeans conflicts
- **Relocation**: `org.apache.poi.*` → `com.betterdairy.autodense.shaded.poi.*`

### Bio-Formats Integration
Two options for specialized microscopy formats:
1. **Rely on Fiji's Bio-Formats** (recommended)
2. **Inject consistent Bio-Formats set** (advanced)

**If injecting Bio-Formats:**
```bash
# Add to injection list
REQUIRED_JARS=(
    "bio-formats_plugins"
    "formats-gpl"
    "ome-common"
    "ome-xml"
)
```

## Troubleshooting Guide

### JAR Injection Issues

**Symptom**: "TwelveMonkeys JPEG: ❌ NO" in audit
- **Check**: lib/ directory contains imageio-*.jar files
- **Fix**: Run build script to copy dependencies

**Symptom**: "System classloader is not URLClassLoader"
- **Cause**: Java 17+ uses different classloader
- **Impact**: Minimal - dependencies still work in development
- **Solution**: App bundle injection uses proper classloader

### Version Conflicts

**Symptom**: Excel export fails with XMLBeans errors  
- **Cause**: Fiji and AutoDense have different XML library versions
- **Solution**: Shade Apache POI and dependencies

**Symptom**: JSON parsing errors
- **Cause**: Multiple Jackson versions on classpath
- **Status**: Should be resolved by shading in production

### Format Support Issues

**Symptom**: "HEIC reader present: ❌ NO"
- **Expected**: HEIC support not yet available
- **Workaround**: Convert HEIC to JPEG before processing

**Symptom**: Enhanced TIFF features not working
- **Check**: TwelveMonkeys TIFF injection successful
- **Debug**: Run classpath audit to verify

## Testing Commands

```bash
# Build with shading and injection
./autodense/packaging/scripts/build_and_package.sh

# Verify classpath construction  
./AutoDense.app/Contents/Resources/java/verify_classpath.sh

# Test in development mode
mvn exec:java -Dexec.mainClass=com.betterdairy.autodense.plugin.EnhancedImageJLauncher

# Check for specific dependency conflicts
ls lib/ | grep -E "(gson|jackson|okhttp|commons)"  # Should be empty in production
```

This strategy ensures AutoDense has access to advanced image format support while maintaining compatibility with Fiji's extensive plugin ecosystem.