# AutoDense Development Startup Guide

## Quick Start Commands

### 1. Build AutoDense
```bash
cd /path/to/AutoDense
mvn -q -DskipTests=true -f autodense/pom.xml -pl plugin -am clean install
```

### 2. Run AutoDense for Testing
```bash
mvn -q -f autodense/plugin/pom.xml exec:java \
  -Dexec.mainClass=com.betterdairy.autodense.plugin.OpenAnalyzeCommand \
  -Dexec.classpathScope=runtime
```

## What You Should See

After running the commands above, you should see:

1. **ImageJ2 Main Window**: Full ImageJ interface with toolbar and menu
2. **AutoDense UI Window**: Placeholder window titled "AutoDense - [context]"
3. **Plugin Available**: Plugins > AutoDense > Open & Analyze in ImageJ menu
4. **Image Loading**: If you have an image file, it will load and show the analysis dialog

## Development Workflow

### After Code Changes:
1. **Kill existing processes** (if running):
   ```bash
   pkill -f ImageJ
   ```
2. **Rebuild**:
   ```bash
   mvn -q -DskipTests=true -f autodense/pom.xml -pl plugin -am clean install
   ```
3. **Restart**:
   ```bash
   mvn -q -f autodense/plugin/pom.xml exec:java \
     -Dexec.mainClass=com.betterdairy.autodense.plugin.OpenAnalyzeCommand \
     -Dexec.classpathScope=runtime
   ```

### Testing Gel Analysis:
1. Launch AutoDense using commands above
2. Load an image via File > Open or drag & drop
3. Go to Plugins > AutoDense > Open & Analyze
4. Use the parameter optimization dialog to adjust detection settings
5. Watch real-time overlays: Green = lanes, Red = bands

## Expected Behavior

- **Build time**: ~5-10 seconds
- **Startup time**: ~3-5 seconds
- **Interface**: Both ImageJ2 main window and AutoDense UI should appear
- **Image analysis**: Real-time parameter adjustment with visual feedback
- **Warnings**: JavaScript plugin errors are normal and non-critical

## Troubleshooting

### Build fails:
- Check Java 17 is installed: `java -version`
- Ensure Maven is available: `mvn -version`
- Try from project root directory

### No UI appears:
- Check for Java process: `ps aux | grep java`
- Look for windows behind other applications
- Try clicking Java icon in dock

### Plugin not in menu:
- Ensure you used `install` not `package` in build command
- Plugin discovery requires proper classpath from `exec:java`
- Classic ImageJ (`ij.ImageJ`) won't work - need ImageJ2

### Natural Language Interface:
- Currently requires separate NL server startup
- LLM functionality is bundled but not yet integrated into main UI
- Future enhancement will show chat interface in AutoDense UI window

## Next Steps

Once basic gel analysis is working, you can:
1. Test parameter optimization with real gel images
2. Start natural language server for AI functionality 
3. Develop additional analysis features
4. Package into standalone macOS app bundle

## Architecture Notes

- **Entry Point**: `OpenAnalyzeCommand.java` main method
- **UI**: Currently split between ImageJ2 (main) and Swing (AutoDense UI)
- **Analysis**: Real-time processing with overlay feedback
- **Plugin System**: Uses SciJava annotations for ImageJ2 integration
- **Natural Language**: Separate module with local LLM server