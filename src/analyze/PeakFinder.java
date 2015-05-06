package analyze;

import java.awt.AWTEvent;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.util.ArrayList;

import process.DiscoidalAveragingFilter;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;

public class PeakFinder implements ExtendedPlugInFilter, DialogListener {
	private int flags = DOES_8G | DOES_16 | DOES_32 | NO_CHANGES | PARALLELIZE_STACKS;
	
	private boolean useDiscoidalAveraging = Prefs.getBoolean("PeakFinder.useDiscoidalAveraging", true);
	private int innerRadius = Prefs.getInt("PeakFinder.innerRadius", 1);
	private int outerRadius = Prefs.getInt("PeakFinder.outerRadius", 3);
	
	private double threshold = Prefs.getDouble("PeakFinder.threshold", 6);
	private double thresholdValue = Prefs.getDouble("PeakFinder.thresholdValue", 0);
	
	private int selectionRadius = Prefs.getInt("PeakFinder.selectionRadius", 4);
	private int minimumDistance = Prefs.getInt("PeakFinder.minimumDistance", 8);
	
	private ImagePlus imp;
	private RoiManager roiManager;
	private DiscoidalAveragingFilter filter = new DiscoidalAveragingFilter();
	
	private boolean isPreview = true;
	
	public PeakFinder() {
		roiManager = RoiManager.getInstance();
		
		if (roiManager == null) {
			roiManager = new RoiManager();
		}
	}
	
	public PeakFinder(boolean useDiscoidalAveraging, DiscoidalAveragingFilter filter,
			double threshold, double thresholdValue, int minimumDistance, int selectionRadius) {
		this();
		
		this.useDiscoidalAveraging = useDiscoidalAveraging;
		this.threshold = threshold;
		this.thresholdValue = thresholdValue;
		this.filter = filter;
		this.minimumDistance = minimumDistance;
		this.selectionRadius = selectionRadius;
		this.isPreview = false;
		
	}
	
	@Override
	public void run(ImageProcessor ip) {
		
		IJ.log("run(..) invocation :");
		IJ.log("useDiscoidalAveraging = " + useDiscoidalAveraging);
		IJ.log("threshold = " + threshold);
		IJ.log("thresholdValue = " + thresholdValue);
		IJ.log("minimumDistance = " + minimumDistance);
		IJ.log("selectionRadius = " + selectionRadius);
		IJ.log("isPreview = " + isPreview);

		
		ArrayList<Point> peaks = findPeaks(ip);
		
		IJ.log("number of peaks = " + peaks.size());
		
		if (!peaks.isEmpty()) {
			
			if (isPreview) {
				
				Polygon poly = new Polygon();
				
				for (Point p: peaks)
					poly.addPoint(p.x, p.y);
				
				PointRoi peakRoi = new PointRoi(poly);
				imp.setRoi(peakRoi);
				
			}
			else {
				
				int selectionWidth = selectionRadius * 2 + 1;
				
				for (Point p: peaks) {
					Roi peakRoi = new Roi(p.x - selectionRadius, p.y - selectionRadius, selectionWidth, selectionWidth);
					peakRoi.setPosition(ip.getSliceNumber());
					roiManager.addRoi(peakRoi);
				}
				
			}
			
		}
		
	}
	
	public ArrayList<Point> findPeaks(ImageProcessor ip) {
		
		ArrayList<Point> peaks = new ArrayList<Point>();
		ImageProcessor duplicate = ip.duplicate();
		
		if (useDiscoidalAveraging) {
			filter.run(duplicate);
		}
		
		Rectangle roi = ip.getRoi();
		double t = thresholdValue; 
		
		if (t == 0) {
			
			// determine mean and standard deviation
			double mean = 0;
			double stdDev = 0;
			
			for (int y = roi.y; y < roi.y + roi.height; y++) {
				for (int x = roi.x; x < roi.x + roi.width; x++) {
					
					mean += duplicate.getf(x, y);
				}
			}
			
			mean /= roi.width * roi.height;
			
			for (int y = roi.y; y < roi.y + roi.height; y++) {
				for (int x = roi.x; x < roi.x + roi.width; x++) {
					
					double d = duplicate.getf(x, y) - mean;
					
					stdDev += d * d;
				}
			}
			
			stdDev /= roi.width * roi.height;
			stdDev = Math.sqrt(stdDev);
			
			t = mean + threshold * stdDev;
			
		}
		
		// determine which pixels are above the threshold
		int[] offsets = new int[roi.width * roi.height];
		int numberOfPixels = 0;
		int width = ip.getWidth();
		
		for (int y = roi.y; y < roi.y + roi.height; y++) {
			for (int x = roi.x; x < roi.x + roi.width; x++) {
				
				double pixel = duplicate.getf(x, y);
				
				if (pixel >= t)
					offsets[numberOfPixels++] = x + y * width;
				
			}
		}
		
		if (numberOfPixels > 0) {
			
			int distanceWidth = minimumDistance * 2 + 1;
			double minValue = duplicate.minValue();
			
			while (true) {
			
				// find maximum
				double maxValue = minValue;
				int maxOffset = offsets[0];
				
				for (int i = 0; i < numberOfPixels; i++) {
					
					double pixel = duplicate.getf(offsets[i]);
					
					if (pixel > maxValue) {
						maxValue = pixel;
						maxOffset = offsets[i];
					}
					
				}
				
				if (maxValue < t)
					break;
				
				// remove peak so we don't count it twice
				int x = maxOffset % width;
				int y = maxOffset / width;
				
				duplicate.setValue(minValue);
				duplicate.fillOval(x - minimumDistance, y - minimumDistance, distanceWidth, distanceWidth);
				
				peaks.add(new Point(x, y));
			}
			
		}
		
		return peaks;
	}

	@Override
	public int setup(String arg, ImagePlus imp) {
		
		this.imp = imp;
		
		return flags;
	}

	@Override
	public void setNPasses(int n) {
		
	}

	@Override
	public int showDialog(ImagePlus imp, String arg, PlugInFilterRunner pfr) {
		
		GenericDialog dialog = new GenericDialog("Peak Finder");
		
		dialog.addCheckbox("Use_Discoidal_Averaging_Filter", useDiscoidalAveraging);
		dialog.addNumericField("Inner_radius", innerRadius, 0);
		dialog.addNumericField("Outer_radius", outerRadius, 0);
		
		dialog.addNumericField("Threshold (mean + n times standard deviation)", threshold, 2);
		dialog.addNumericField("Threshold_value (0 = ignore)", thresholdValue, 2);
		dialog.addNumericField("Selection_radius (in pixels)", selectionRadius, 0);
		dialog.addNumericField("Minimum_distance between peaks (in pixels)", minimumDistance, 0);
		
		dialog.addDialogListener(this);
		dialog.addPreviewCheckbox(pfr);
		
		dialog.showDialog();
		
		if (dialog.wasCanceled())
			return DONE;
		
		isPreview = false;

		return IJ.setupDialog(imp, flags);
	}

	@Override
	public boolean dialogItemChanged(GenericDialog dialog, AWTEvent e) {
		
		useDiscoidalAveraging = dialog.getNextBoolean();
		innerRadius = (int)dialog.getNextNumber();
		outerRadius = (int)dialog.getNextNumber();
		
		threshold = dialog.getNextNumber();
		thresholdValue = (int)dialog.getNextNumber();
		
		selectionRadius = (int)dialog.getNextNumber();
		minimumDistance = (int)dialog.getNextNumber();
		
		filter.setCircleOffsets(imp.getWidth(), innerRadius, outerRadius);
		
		return (!useDiscoidalAveraging || (innerRadius >= 0 && innerRadius < outerRadius)) && selectionRadius >= 0 && minimumDistance > 0;
	}
	
}
