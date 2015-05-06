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
import ij.measure.ResultsTable;
import ij.plugin.filter.Analyzer;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;

public class PeakFitter implements ExtendedPlugInFilter, DialogListener {
	public static final double SIGMA_TO_FWHM = 2.0 * Math.sqrt(2.0 * Math.log(2));
	
	private int flags = DOES_8G | DOES_16 | DOES_32 | NO_CHANGES | FINAL_PROCESSING | PARALLELIZE_STACKS;
	
	private boolean useDiscoidalAveraging = Prefs.getBoolean("PeakFitter.useDiscoidalAveraging", true);
	private int innerRadius = Prefs.getInt("PeakFitter.innerRadius", 1);
	private int outerRadius = Prefs.getInt("PeakFitter.outerRadius", 3);
	private double threshold = Prefs.getDouble("PeakFitter.threshold", 6.0);
	private double thresholdValue = Prefs.getDouble("PeakFitter.thresholdValue", 0);
	private int minimumDistance = Prefs.getInt("PeakFitter.minimumDistance", 8);
	private int fitRadius = Prefs.getInt("PeakFitter.fitRadius", 4);
	private boolean isRoiFit = false;
	
	private int totalFittedPeaks = 0;
	private int foundPeaks = 0;
	
	private double[] maxError = new double[] {
			Prefs.getDouble("PeakFitter.maxErrorBaseline", 5000),
			Prefs.getDouble("PeakFitter.maxErrorHeight", 5000),
			Prefs.getDouble("PeakFitter.maxErrorX", 1),
			Prefs.getDouble("PeakFitter.maxErrorY", 1),
			Prefs.getDouble("PeakFitter.maxErrorSigmaX", 1),
			Prefs.getDouble("PeakFitter.maxErrorSigmaY", 1),
	};
	
	private PeakFinder peakFinder;
	
	private static LevenbergMarquardt lm = new LevenbergMarquardt() {
		
		@Override
		public double getValue(double[] x, double[] p) {
			
			double dx = x[0] - p[2];
			double dy = x[1] - p[3];
			
			return p[0] + p[1] * Math.exp(-((dx * dx) / (2 * p[4] * p[4]) + (dy * dy) / (2 * p[5] * p[5])));
		}

		@Override
		public void getGradient(double[] x, double[] p, double[] dyda) {
			
			double dx = x[0] - p[2];
			double dy = x[1] - p[3];
			
			dyda[0] = 1;
			dyda[1] = Math.exp(-((dx * dx) / (2 * p[4] * p[4]) + (dy * dy) / (2 * p[5] * p[5])));
			dyda[2] = (p[1] * dyda[1] * dx) / (p[4] * p[4]);
			dyda[3] = (p[1] * dyda[1] * dy) / (p[5] * p[5]);
			dyda[4] = (p[1] * dyda[1] * dx * dx) / (p[4] * p[4] * p[4]);
			dyda[5] = (p[1] * dyda[1] * dy * dy) / (p[5] * p[5] * p[5]);
			
		}

	};
	
	private ResultsTable table;
	private ImagePlus imp;
	
	private boolean isPreview = true;
	
	@Override
	public void run(ImageProcessor ip) {
		
		Rectangle roi = new Rectangle(0, 0, ip.getWidth(), ip.getHeight());
		
		if (imp.getRoi() != null)
			roi = imp.getRoi().getBounds();
		
		ArrayList<Point> peaks = peakFinder.findPeaks(ip);
		double[][] fitParameters = new double[peaks.size()][6];
		double[][] fitErrors = new double[peaks.size()][6];
		int fittedPeaks = 0;
		
		int fitWidth = fitRadius * 2 + 1;

		for (Point peak: peaks) {
			
			if (isRoiFit) {
				
				RoiManager roiManager = RoiManager.getInstance();
				
				if (roiManager != null) {
					
					boolean isInRoi = false;
					
					for (Roi r: roiManager.getRoisAsArray()) {
						if (r.contains(peak.x, peak.y)) {
							isInRoi = true;
							break;
						}
					}
					
					if (!isInRoi)
						continue;
				}
				
			}
			
			ip.setRoi(new Rectangle(peak.x - fitRadius, peak.y - fitRadius, fitWidth, fitWidth));
			
			double[] parameters = fitParameters[fittedPeaks];
			double[] errors = fitErrors[fittedPeaks];
			
			for (int i = 0; i < parameters.length; i++)
				parameters[i] = Double.NaN;
			
			parameters[2] = peak.x;
			parameters[3] = peak.y;
			
			fitPeak(ip, parameters, errors);
			
			boolean valid = true;
			
			for (int i = 0; valid && i < parameters.length; i++) {
				
				if (Double.isNaN(parameters[i]) || Double.isNaN(errors[i]) || Math.abs(errors[i]) > maxError[i])
					valid = false;
				
			}
			
			if (valid)
				fittedPeaks++;
			
		}
		
		ip.setRoi(roi);
		
		if (isPreview) {
			
			Polygon poly = new Polygon();
			
			for (int i = 0; i < fittedPeaks; i++)
				poly.addPoint((int)Math.round(fitParameters[i][2]), (int)Math.round(fitParameters[i][3]));
			
			PointRoi peakRoi = new PointRoi(poly);
			imp.setRoi(peakRoi);
			
		}
		else {
			
			int slice = ip.getSliceNumber();
			
			
			synchronized (this) {
				for (int i = 0; i < fittedPeaks; i++) {
					addToResultsTable(table, fitParameters[i], fitErrors[i], slice);
				}
				
				foundPeaks += peaks.size();
				totalFittedPeaks += fittedPeaks;
			}
			
		}
		
	}
	
	public static void addToResultsTable(ResultsTable table, double[] parameters, double[] errors, int slice) {
		
		// sigma_x and sigma_y should always be absolute
		parameters[4] = Math.abs(parameters[4]);
		parameters[5] = Math.abs(parameters[5]);
		
		table.incrementCounter();
		
		table.addValue("baseline", parameters[0]);
		table.addValue("height",   parameters[1]);
		table.addValue("x",        parameters[2]);
		table.addValue("y",        parameters[3]);
		table.addValue("sigma_x",  parameters[4]);
		table.addValue("sigma_y",  parameters[5]);
		
		double fwhmx = parameters[4] * SIGMA_TO_FWHM;
		double fwhmy = parameters[5] * SIGMA_TO_FWHM;
		
		table.addValue("fwhm_x",   fwhmx);
		table.addValue("fwhm_y",   fwhmy);
		table.addValue("fwhm",     (fwhmx + fwhmy) / 2);
		
		table.addValue("error_baseline", errors[0]);
		table.addValue("error_height",   errors[1]);
		table.addValue("error_x",        errors[2]);
		table.addValue("error_y",    	 errors[3]);
		table.addValue("error_sigma_x",  errors[4]);
		table.addValue("error_sigma_y",  errors[5]);
		
		double errorFwhmx = errors[4] * SIGMA_TO_FWHM;
		double errorFwhmy = errors[5] * SIGMA_TO_FWHM;
		
		table.addValue("error_fwhm_x",   errorFwhmx);
		table.addValue("error_fwhm_y",	 errorFwhmy);
		table.addValue("error_fwhm",     Math.sqrt(errorFwhmx * errorFwhmx + errorFwhmy * errorFwhmy) / 2);
		
		table.addValue("slice", slice);
		
	}
	
	public static void fitPeak(ImageProcessor ip, double[] p, double[] e) {
		Rectangle roi = ip.getRoi();
		
		double[][] xs = new double[roi.width * roi.height][2];
		double[] ys = new double[xs.length];
			
		int n = 0;
		int max = 0;
		int min = 0;
		
		for (int y = roi.y; y < roi.y + roi.height; y++) {
			for (int x = roi.x; x < roi.x + roi.width; x++) {
				
				xs[n][0] = x;
				xs[n][1] = y;
				ys[n] = ip.getf(x, y);
				
				if (ys[n] < ip.maxValue()) {	// ignore saturated values
					if (ys[n] > ys[max])
						max = n;
					if (ys[n] < ys[min])
						min = n;
					n++;
				}
				
			}
		}
		
		double[] guess = {ys[min], ys[max] - ys[min], xs[max][0], xs[max][1], 1, 1};
		
		if (!Double.isNaN(p[2]) && !Double.isNaN(p[3])) {
			p[0] = ys[min];
			p[1] = ip.getf((int)p[2], (int)p[3]) - p[0];
		}
		
		for (int i = 0; i < p.length; i++)
			if (Double.isNaN(p[i])) p[i] = guess[i];
		
		lm.solve(xs, ys, null, n, p, null, e, 0.001);
	}

	@Override
	public int setup(String arg, ImagePlus imp) {
		
		if (arg.equals("final")) {
			
			IJ.showStatus("found peaks : "  + foundPeaks + " fitted peaks : " + totalFittedPeaks);
			
			table.show("Results");
			return DONE;
		}
		
		this.imp = imp;
		table = Analyzer.getResultsTable();
		
		if (table == null) {
			table = new ResultsTable();
			Analyzer.setResultsTable(table);
		}
		
		return flags;
	}

	@Override
	public boolean dialogItemChanged(GenericDialog dialog, AWTEvent e) {
		
		useDiscoidalAveraging = dialog.getNextBoolean();
		innerRadius = (int)dialog.getNextNumber();
		outerRadius = (int)dialog.getNextNumber();
		threshold = dialog.getNextNumber();
		thresholdValue = dialog.getNextNumber();
		minimumDistance = (int)dialog.getNextNumber();
		fitRadius = (int)dialog.getNextNumber();
		
		for (int i = 0; i < maxError.length; i++)
			maxError[i] = dialog.getNextNumber();

		isRoiFit = dialog.getNextBoolean();
		
		DiscoidalAveragingFilter filter = new DiscoidalAveragingFilter();
		filter.setCircleOffsets(imp.getWidth(), innerRadius, outerRadius);
		
		peakFinder = new PeakFinder(useDiscoidalAveraging,
				new DiscoidalAveragingFilter(imp.getWidth(), innerRadius, outerRadius),
				threshold, thresholdValue, minimumDistance, 0);
		
		return true;
	}

	@Override
	public void setNPasses(int arg0) {
		
	}

	@Override
	public int showDialog(ImagePlus imp, String arg, PlugInFilterRunner pfr) {
		
		GenericDialog dialog = new GenericDialog("Peak Fitter");
		
		dialog.addCheckbox("Use_Discoidal_Averaging_Filter", useDiscoidalAveraging);
		dialog.addNumericField("Inner_radius", innerRadius, 0);
		dialog.addNumericField("Outer_radius", outerRadius, 0);
		
		dialog.addNumericField("Threshold (mean + n times standard deviation)", threshold, 2);
		dialog.addNumericField("Threshold_value (0 = ignore)", thresholdValue, 2);
		dialog.addNumericField("Minimum_distance between peaks (in pixels)", minimumDistance, 0);
		
		dialog.addNumericField("Fit_radius", fitRadius, 0);
		
		dialog.addNumericField("Max_error_baseline", maxError[0], 2);
		dialog.addNumericField("Max_error_height", maxError[1], 2);
		dialog.addNumericField("Max_error_x", maxError[2], 2);
		dialog.addNumericField("Max_error_y", maxError[3], 2);
		dialog.addNumericField("Max_error_sigma_x", maxError[4], 2);
		dialog.addNumericField("Max_error_sigma_y", maxError[5], 2);
		
		dialog.addCheckbox("Fit_peaks_inside_rois", isRoiFit);
		
		dialog.addDialogListener(this);
		dialog.addPreviewCheckbox(pfr);
		dialog.showDialog();
		
		if (dialog.wasCanceled())
			return DONE;
		
		isPreview = false;
		
		return IJ.setupDialog(imp, flags);
	}
	
}
