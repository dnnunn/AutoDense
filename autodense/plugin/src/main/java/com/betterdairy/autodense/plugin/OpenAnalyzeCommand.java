package com.betterdairy.autodense.plugin;

import net.imagej.ImageJ;
import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;

@Plugin(type = Command.class, menuPath = "Plugins>AutoDense>Open & Analyze")
public class OpenAnalyzeCommand implements Command {

    @Parameter(label = "Input image", style = "open", required = false)
    private File inputFile;

    @Parameter
    private Context context;

    @Override
    public void run() {
        GelUI ui = new GelUI(context);
        ui.show();
        // TODO: if inputFile != null, open it automatically
    }

    // For local testing without ImageJ launcher
    public static void main(String[] args) {
        final ImageJ ij = new ImageJ();
        ij.ui().showUI();
    }
}
