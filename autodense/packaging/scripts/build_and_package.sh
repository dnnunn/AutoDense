#!/usr/bin/env bash
set -euo pipefail

# AutoDense Build and Packaging Script
# Builds the project and creates a modular macOS app bundle with runtime dependencies

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
PACKAGING_DIR="$PROJECT_ROOT/packaging"
RESOURCES_DIR="$PACKAGING_DIR/resources"

echo "🔨 Building AutoDense with modular dependency packaging..."
echo "Project root: $PROJECT_ROOT"

# Clean and build the project
echo "📦 Building Maven project..."
cd "$PROJECT_ROOT"
mvn clean install -DskipTests -q

# Verify build success
if [ ! -f "$PROJECT_ROOT/plugin/target/autodense-plugin-0.1.0-SNAPSHOT.jar" ]; then
    echo "❌ Build failed - plugin JAR not found"
    exit 1
fi

echo "✅ Build completed successfully"

# Prepare app bundle structure
APP_BUNDLE="$RESOURCES_DIR/AutoDense.app"
JAVA_DIR="$APP_BUNDLE/Contents/Resources/java"
LIB_DIR="$JAVA_DIR/lib"

echo "📁 Setting up app bundle structure..."

# Create java directory if it doesn't exist
mkdir -p "$JAVA_DIR"
mkdir -p "$LIB_DIR"

# Copy main plugin JAR to java directory
echo "📋 Copying main plugin JAR..."
cp "$PROJECT_ROOT/plugin/target/autodense-plugin-0.1.0-SNAPSHOT.jar" "$JAVA_DIR/"

# Copy runtime dependencies to lib directory (only inject SPI/codec dependencies)
echo "📚 Copying runtime dependencies (excluding shaded libraries)..."
echo "   🔹 INJECTING: TwelveMonkeys ImageIO (needs ServiceLoader)"
echo "   🔹 INJECTING: AutoDense-nl module, JSON, validation libs"
echo "   🔸 SHADING: Jackson, Gson, OkHttp, Apache Commons (avoid Fiji conflicts)"
cd "$PROJECT_ROOT/plugin"
mvn dependency:copy-dependencies \
    -DincludeScope=runtime \
    -DoutputDirectory="$LIB_DIR" \
    -DexcludeGroupIds="net.imagej,org.scijava,org.scijava.plugins,net.imglib2,org.jdom" \
    -q

# Remove version-sensitive dependencies that should be shaded instead
echo "🗑️  Removing shaded dependencies from injection directory..."
SHADED_PATTERNS=(
    "jackson-*"
    "gson-*" 
    "okhttp-*"
    "okio-*"
    "httpclient-*"
    "httpcore-*"
    "commons-codec-*"
    "commons-io-*"
    "commons-lang-*"
    "commons-logging-*"
)

REMOVED_COUNT=0
for pattern in "${SHADED_PATTERNS[@]}"; do
    if ls "$LIB_DIR"/${pattern}.jar 1> /dev/null 2>&1; then
        for jar in "$LIB_DIR"/${pattern}.jar; do
            echo "  🗑️  Removed: $(basename "$jar")"
            rm "$jar"
            ((REMOVED_COUNT++))
        done
    fi
done

echo "📊 Removed $REMOVED_COUNT shaded dependencies from injection"

# Verify TwelveMonkeys JARs are copied
echo "🔍 Verifying TwelveMonkeys dependencies..."
REQUIRED_JARS=(
    "imageio-core" 
    "imageio-jpeg"
    "imageio-tiff"
    "imageio-metadata"
    "imageio-bmp"
)

for jar_name in "${REQUIRED_JARS[@]}"; do
    if ls "$LIB_DIR"/${jar_name}-*.jar 1> /dev/null 2>&1; then
        echo "  ✅ Found ${jar_name}"
    else
        echo "  ❌ Missing ${jar_name}"
        exit 1
    fi
done

# Count total JARs in lib directory
JAR_COUNT=$(find "$LIB_DIR" -name "*.jar" | wc -l)
echo "📊 Total runtime JARs copied: $JAR_COUNT"

# List all JARs for verification
echo "📋 Runtime dependencies:"
find "$LIB_DIR" -name "*.jar" -exec basename {} \; | sort

# Update Info.plist with classpath information
INFO_PLIST="$APP_BUNDLE/Contents/Info.plist"
if [ -f "$INFO_PLIST" ]; then
    echo "📝 App bundle Info.plist exists"
else
    echo "⚠️  Info.plist not found at $INFO_PLIST"
fi

# Create a verification script for the app bundle
VERIFY_SCRIPT="$JAVA_DIR/verify_classpath.sh"
cat > "$VERIFY_SCRIPT" << 'EOF'
#!/bin/bash
# Verification script to test classpath injection

JAVA_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="$JAVA_DIR/lib"

echo "Java directory: $JAVA_DIR"
echo "Lib directory: $LIB_DIR"
echo "JARs in lib:"
ls -la "$LIB_DIR"/*.jar | wc -l
echo "TwelveMonkeys JARs:"
ls "$LIB_DIR"/imageio-*.jar 2>/dev/null || echo "No TwelveMonkeys JARs found"

# Test Java classpath construction
CLASSPATH="$JAVA_DIR/autodense-plugin-0.1.0-SNAPSHOT.jar"
for jar in "$LIB_DIR"/*.jar; do
    CLASSPATH="$CLASSPATH:$jar"
done

echo "Sample classpath length: ${#CLASSPATH}"
echo "First few classpath entries:"
echo "$CLASSPATH" | cut -d: -f1-5
EOF

chmod +x "$VERIFY_SCRIPT"

echo "✅ Modular app bundle packaging completed!"
echo ""
echo "📦 PACKAGING SUMMARY"
echo "===================="
echo "App bundle: $APP_BUNDLE"
echo "Main JAR: $JAVA_DIR/autodense-plugin-0.1.0-SNAPSHOT.jar"
echo "  └── Contains SHADED: Jackson, Gson, OkHttp, Apache Commons"
echo "Runtime libs: $LIB_DIR ($JAR_COUNT files)"
echo "  └── Contains INJECTED: TwelveMonkeys ImageIO, AutoDense-nl, JSON"
echo ""
echo "🎯 DEPENDENCY STRATEGY:"
echo "  🔹 INJECT: ServiceLoader dependencies (ImageIO codecs)"
echo "  🔸 SHADE: Version-sensitive stacks (HTTP, JSON, Commons)"
echo "  ✅ AVOID: Fiji built-ins (ImageJ, SciJava, ImgLib2)"
echo ""
echo "🔍 Run verification: $VERIFY_SCRIPT"
echo "📦 Ready for DMG creation with: ./package_app.sh"