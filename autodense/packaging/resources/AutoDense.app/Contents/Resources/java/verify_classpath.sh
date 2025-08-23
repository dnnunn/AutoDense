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
