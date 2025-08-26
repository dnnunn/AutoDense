// @String in
// @String out
open(in);
run("Duplicate...", "title=work");
run("8-bit");
run("Gaussian Blur...", "sigma=1.0");
setAutoThreshold("Default dark");
run("Convert to Mask");
run("Open"); run("Fill Holes");
run("Analyze Particles...", "size=40-Infinity circularity=0.30-1.00 add show=None");
selectWindow("Results");
saveAs("Results", out + "/colonies.csv");
selectWindow("work");
saveAs("PNG", out + "/overlay.png");