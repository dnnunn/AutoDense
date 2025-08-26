// @String in
// @String out
open(in);
run("8-bit");
run("Subtract Background...", "rolling=50");
saveAs("PNG", out + "/lanes_overlay.png"); // placeholder; real overlay comes from plugin in app runs
// Make a simple bands.csv stub to exercise verify script structure
File.saveString("id,lane,y_px,intensity\n1,1,120,1234\n2,1,180,1111\n", out + "/bands.csv");
saveAs("PNG", out + "/bands_overlay.png");