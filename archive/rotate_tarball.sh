#!/bin/bash
# AutoDense Documentation Archive Rotation Script
# 
# Purpose: Atomically rotate deprecated documentation into single rolling tarball
# Usage: ./rotate_tarball.sh [--commit]
# 
# This script implements the "one tarball to rule them all" strategy for
# deprecated documentation management.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ARCHIVE_DIR="$SCRIPT_DIR"
STAGING_DIR="$ARCHIVE_DIR/staging"
TARBALL="$ARCHIVE_DIR/deprecated-current.tar.gz"
INDEX_FILE="$ARCHIVE_DIR/INDEX.txt"
TEMP_TARBALL="$ARCHIVE_DIR/.deprecated-current.tmp.tar.gz"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

usage() {
    echo "Usage: $0 [--commit]"
    echo ""
    echo "Options:"
    echo "  --commit    Actually perform the rotation (default: dry-run)"
    echo "  --help      Show this help message"
    echo ""
    echo "This script rotates files from archive/staging/ into deprecated-current.tar.gz"
    echo "Run without --commit to see what would happen (dry-run mode)"
}

log() {
    echo -e "${GREEN}[$(date '+%Y-%m-%d %H:%M:%S')]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1" >&2
}

# Parse command line arguments
COMMIT=false
while [[ $# -gt 0 ]]; do
    case $1 in
        --commit)
            COMMIT=true
            shift
            ;;
        --help)
            usage
            exit 0
            ;;
        *)
            error "Unknown option: $1"
            usage
            exit 1
            ;;
    esac
done

# Change to archive directory
cd "$ARCHIVE_DIR"

log "AutoDense Documentation Archive Rotation"
log "Archive directory: $ARCHIVE_DIR"
log "Staging directory: $STAGING_DIR"
log "Target tarball: $TARBALL"

# Check if staging directory exists and has content
if [[ ! -d "$STAGING_DIR" ]]; then
    log "No staging directory found - nothing to archive"
    exit 0
fi

if [[ ! "$(ls -A "$STAGING_DIR" 2>/dev/null)" ]]; then
    log "Staging directory is empty - nothing to archive"
    exit 0
fi

# List files to be archived
log "Files in staging directory:"
find "$STAGING_DIR" -type f -name "*.md" | while read -r file; do
    size=$(stat -f%z "$file" 2>/dev/null || stat -c%s "$file" 2>/dev/null || echo "unknown")
    echo "  $(basename "$file") (${size} bytes)"
done

# Count files
file_count=$(find "$STAGING_DIR" -type f -name "*.md" | wc -l | tr -d ' ')
log "Found $file_count markdown files to archive"

if [[ "$file_count" -eq 0 ]]; then
    warn "No markdown files found in staging directory"
    exit 0
fi

if [[ "$COMMIT" = false ]]; then
    log "DRY RUN MODE - use --commit to actually perform rotation"
    log "Would archive $file_count files into $TARBALL"
    exit 0
fi

log "COMMIT MODE - performing actual rotation"

# Create or update tarball atomically
if [[ -f "$TARBALL" ]]; then
    log "Existing tarball found - extracting for merge"
    # Extract existing tarball to temporary location
    TEMP_EXTRACT="$ARCHIVE_DIR/.temp_extract"
    mkdir -p "$TEMP_EXTRACT"
    tar -xzf "$TARBALL" -C "$TEMP_EXTRACT" 2>/dev/null || true
    
    # Create new tarball with existing + new content
    log "Creating updated tarball with existing + new content"
    tar -czf "$TEMP_TARBALL" -C "$TEMP_EXTRACT" . -C "$STAGING_DIR" .
    
    # Cleanup temporary extraction
    rm -rf "$TEMP_EXTRACT"
else
    log "No existing tarball - creating new one"
    tar -czf "$TEMP_TARBALL" -C "$STAGING_DIR" .
fi

# Verify tarball was created successfully
if [[ ! -f "$TEMP_TARBALL" ]]; then
    error "Failed to create temporary tarball"
    exit 1
fi

# Atomic move to final location
log "Atomically moving tarball to final location"
mv "$TEMP_TARBALL" "$TARBALL"

# Update index file
log "Updating archive index"
{
    if [[ -f "$INDEX_FILE" ]]; then
        cat "$INDEX_FILE"
    fi
    echo "$(date '+%Y-%m-%d %H:%M:%S'): Archived $file_count files"
    find "$STAGING_DIR" -type f -name "*.md" -exec basename {} \; | sed 's/^/  - /'
} > "${INDEX_FILE}.tmp"
mv "${INDEX_FILE}.tmp" "$INDEX_FILE"

# Clean staging directory
log "Cleaning staging directory"
rm -rf "$STAGING_DIR"/*

# Verify final state
tarball_size=$(stat -f%z "$TARBALL" 2>/dev/null || stat -c%s "$TARBALL" 2>/dev/null || echo "unknown")
log "Archive rotation completed successfully"
log "Final tarball size: $tarball_size bytes"

# List tarball contents
log "Tarball contents:"
tar -tzf "$TARBALL" | head -20 | sed 's/^/  /'
if [[ $(tar -tzf "$TARBALL" | wc -l) -gt 20 ]]; then
    echo "  ... ($(tar -tzf "$TARBALL" | wc -l) total files)"
fi

log "Archive rotation completed successfully!"