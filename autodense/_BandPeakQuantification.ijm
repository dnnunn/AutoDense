// _BandPeakQuantification.ijm
// Written by Kenji OHGANE (Univ. Tokyo), based on Image Studio Lite's gel quantification function.
// - If multiple ROIs are registered in the ROI Manager, then this macro process all the ROIs listed in the ROI Manager.
// - If no ROI is found on the ROI Manager, then this macro perform band/peak quantification for currently selected ROI.
// - Applicable to any ROI shape.
// - In this macro, baseline was estimated from mean or median pixel value from the expanded bounding box (default 3 pixels).
// - Users can select to use mean or median for background estimation.

Dialog.create("Band/Peak Quantification Tool");
Dialog.addNumber("background width (pixels)", 3);
Dialog.addChoice("Estimate background from (rectangle selection only): ", newArray("all", "top/bottom", "sides"));
Dialog.addChoice("Background estimation by: ", newArray("median", "mean"));
Dialog.addCheckbox("Reset scale", true);
Dialog.show();

// Check selection shape
if (roiManager("count") == 0) {
	type=selectionType();
} else {
	roiManager("Select", 0);
	type=selectionType();
}

// Get dialog options
expand=Dialog.getNumber();
backpos=Dialog.getChoice();
backtype=Dialog.getChoice();
resetscale=Dialog.getCheckbox();

// Reset scale, if scale is set for the image (without this block, this macro gives error when "scaled" image is used)
if (resetscale) {
	run("Set Scale...", "distance=0 known=0 pixel=1 unit=pixel");
}

// Warn if top/bottom or sides option was selected for non-rectangular selections
if ((backpos != "all") && (type != 0)) {
	print("'Top/bottom' or 'sides' can be only applicable to rectangle selections. 'All' is used instead.");
}

// Main
if ((backpos == "all") | (type != 0)){
if (roiManager("count") == 0) {
	Roi.getBounds(x,y,w,h);
	getStatistics(area, mean);
	run("Make Band...", "band="+expand);
	if (backtype == "median") {
		mean_back=getValue("Median");
	} else {
		mean_back=getValue("Mean");
	}
	i=nResults();
	setResult("signal", i, area*(mean-mean_back));
	setResult("total", i, area*mean);
	setResult("area", i, area);
	setResult("mean", i, mean);
	if (backtype == "mean"){
		setResult("mean_background", i, mean_back);
	} else {
		setResult("median_background", i, mean_back);
	}
	setResult("ROI_x", i, x);
	setResult("ROI_y", i, y);
	setResult("ROI_w", i, w);
	setResult("ROI_h", i, h);
	updateResults();
	makeRectangle(x, y, w, h);
} else {
	for (k=0; k < roiManager("count"); k=k+1) {
		roiManager("Select", k);
		Roi.getBounds(x,y,w,h);
		getStatistics(area, mean);
		run("Make Band...", "band=3");
		if (backtype == "median") {
			mean_back=getValue("Median");
		} else {
			mean_back=getValue("Mean");
		}
		i=nResults();
		setResult("signal", i, area*(mean-mean_back));
		setResult("total", i, area*mean);
		setResult("area", i, area);
		setResult("mean", i, mean);
		if (backtype == "mean"){
			setResult("mean_background", i, mean_back);
		} else {
			setResult("median_background", i, mean_back);
		}
		setResult("ROI_x", i, x);
		setResult("ROI_y", i, y);
		setResult("ROI_w", i, w);
		setResult("ROI_h", i, h);
		updateResults();
		roiManager("Select", k);
	}
}
} else {
if (backpos == "top/bottom" ){
if (roiManager("count") == 0) {
	Roi.getBounds(x,y,w,h);//Note that x and y denotes top-lefts point
	getStatistics(area, mean);
	makePolygon(x, y-expand, x+w, y-expand,
				x+w, y, x, y,
				x, y+h, x+w, y+h,
				x+w, y+h+expand, x, y+h+expand,
				x, y-expand);
	if (backtype == "median") {
		mean_back=getValue("Median");
	} else {
		mean_back=getValue("Mean");
	}
	i=nResults();
	setResult("signal", i, area*(mean-mean_back));
	setResult("total", i, area*mean);
	setResult("area", i, area);
	setResult("mean", i, mean);
	if (backtype == "mean"){
		setResult("mean_background", i, mean_back);
	} else {
		setResult("median_background", i, mean_back);
	}
	setResult("ROI_x", i, x);
	setResult("ROI_y", i, y);
	setResult("ROI_w", i, w);
	setResult("ROI_h", i, h);
	updateResults();
	makeRectangle(x, y, w, h);
} else {
	for (k=0; k < roiManager("count"); k=k+1) {
		roiManager("Select", k);
		Roi.getBounds(x,y,w,h);
		getStatistics(area, mean);
		makePolygon(x, y-expand, x+w, y-expand,
				x+w, y, x, y,
				x, y+h, x+w, y+h,
				x+w, y+h+expand, x, y+h+expand,
				x, y-expand);
		if (backtype == "median") {
			mean_back=getValue("Median");
		} else {
			mean_back=getValue("Mean");
		}
		i=nResults();
		setResult("signal", i, area*(mean-mean_back));
		setResult("total", i, area*mean);
		setResult("area", i, area);
		setResult("mean", i, mean);
		if (backtype == "mean"){
			setResult("mean_background", i, mean_back);
		} else {
			setResult("median_background", i, mean_back);
		}
		setResult("ROI_x", i, x);
		setResult("ROI_y", i, y);
		setResult("ROI_w", i, w);
		setResult("ROI_h", i, h);
		updateResults();
		roiManager("Select", k);
	}
}
} else {// sides
if (roiManager("count") == 0) {
	Roi.getBounds(x,y,w,h);
	getStatistics(area, mean);
	makePolygon(x-expand, y, x-expand, y+h,
				x, y+h, x, y,
				x+w, y, x+w, y+h,
				x+w+expand,y+h, x+w+expand, y,
				x-expand, y);
	if (backtype == "median") {
		mean_back=getValue("Median");
	} else {
		mean_back=getValue("Mean");
	}
	i=nResults();
	setResult("signal", i, area*(mean-mean_back));
	setResult("total", i, area*mean);
	setResult("area", i, area);
	setResult("mean", i, mean);
	if (backtype == "mean"){
		setResult("mean_background", i, mean_back);
	} else {
		setResult("median_background", i, mean_back);
	}
	setResult("ROI_x", i, x);
	setResult("ROI_y", i, y);
	setResult("ROI_w", i, w);
	setResult("ROI_h", i, h);
	updateResults();
	makeRectangle(x, y, w, h);
} else {
	for (k=0; k < roiManager("count"); k=k+1) {
		roiManager("Select", k);
		Roi.getBounds(x,y,w,h);
		getStatistics(area, mean);
		makePolygon(x-expand, y, x-expand, y+h,
				x, y+h, x, y,
				x+w, y, x+w, y+h,
				x+w+expand,y+h, x+w+expand, y,
				x-expand, y);
		if (backtype == "median") {
			mean_back=getValue("Median");
		} else {
			mean_back=getValue("Mean");
		}
		i=nResults();
		setResult("signal", i, area*(mean-mean_back));
		setResult("total", i, area*mean);
		setResult("area", i, area);
		setResult("mean", i, mean);
		if (backtype == "mean"){
			setResult("mean_background", i, mean_back);
		} else {
			setResult("median_background", i, mean_back);
		}
		setResult("ROI_x", i, x);
		setResult("ROI_y", i, y);
		setResult("ROI_w", i, w);
		setResult("ROI_h", i, h);
		updateResults();
		roiManager("Select", k);
	}
}
}
}
