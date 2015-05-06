package analyze;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.filter.Analyzer;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;

public class DriftCorrection implements PlugInFilter, ActionListener {
	private int flags = DOES_8G | DOES_16 | DOES_32 | FINAL_PROCESSING | DOES_STACKS;
	
	private RoiManager roiManager;
	
	private int fromSlice;
	private int toSlice;
	
	private double[] maxError = new double[] {
			Prefs.getDouble("PeakFitter.maxErrorBaseline", 5000),
			Prefs.getDouble("PeakFitter.maxErrorHeight", 5000),
			Prefs.getDouble("PeakFitter.maxErrorX", 1),
			Prefs.getDouble("PeakFitter.maxErrorY", 1),
			Prefs.getDouble("PeakFitter.maxErrorSigmaX", 1),
			Prefs.getDouble("PeakFitter.maxErrorSigmaY", 1),
	};
	
	private int degreeOfPolynomial = Prefs.getInt("DriftCorrection.degreeOfPolynomial", 5);
	
	private double[][] x;
	private double[][] xPositions;
	private double[][] yPositions;
	private int[] fittedPeaks;
	
	private double[] xParameters;
	private double[] yParameters;
	private double[] xError;
	private double[] yError;

	private JButton correctResultsButton = new JButton("Correct Results");
	private JButton correctImageButton = new JButton("Correct Image");
	
	@Override
	public void run(ImageProcessor ip) {
		
		if (ip.getSliceNumber() >= fromSlice && ip.getSliceNumber() <= toSlice) {
			Roi[] rois = roiManager.getRoisAsArray();
			
			for (int selection = 0; selection < rois.length; selection++) {
				
				double[] p = new double[]{Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN};
				double[] e = new double[p.length];
				
				rois[selection].setPosition(0);	// make sure that this selection is global
				ip.setRoi(rois[selection]);
				PeakFitter.fitPeak(ip, p, e);
				
				boolean valid = true;
				
				for (int i = 0; i < e.length; i++) {
					if (Double.isNaN(p[i]) || Double.isNaN(e[i]) || Math.abs(e[i]) > maxError[i])
						valid = false;
				}
				
				if (valid) {
					int index = fittedPeaks[selection];
					
					x[selection][index] = ip.getSliceNumber() - 1;
					xPositions[selection][index] = p[2];
					yPositions[selection][index] = p[3];
					
					fittedPeaks[selection]++;
				}
				
			}
		}
		
	}
	
	private double polynomial(double x, double[] p) {
		double value = 0;
		
		for (int i = 0; i < p.length; i++)
			value += p[i] * Math.pow(x, i);
		
		return value;
	}
	
	@Override
	public int setup(String arg, ImagePlus imp) {

		if (arg.equals("final")) {
			
			// set all positions relative to first position
			
			for (int selection = 0; selection < roiManager.getCount(); selection++) {
				
				for (int i = 1; i < fittedPeaks[selection]; i++) {
					xPositions[selection][i] -= xPositions[selection][0];
					yPositions[selection][i] -= yPositions[selection][0];
				}
				
				xPositions[selection][0] = 0;
				yPositions[selection][0] = 0;
				
			}
			
			// create arrays for fitting
			
			double[][] xFit = new double[roiManager.getCount() * imp.getNSlices()][1];
			double[] xPlot = new double[roiManager.getCount() * imp.getNSlices()];
			double[] xPositionsFit = new double[roiManager.getCount() * imp.getNSlices()];
			double[] yPositionsFit = new double[roiManager.getCount() * imp.getNSlices()];
			int n = 0;
			
			for (int selection = 0; selection < roiManager.getCount(); selection++) {
				
				for (int i = 0; i < fittedPeaks[selection]; i++) {
					
					xFit[n][0] = x[selection][i];
					xPlot[n] = x[selection][i];
					xPositionsFit[n] = xPositions[selection][i];
					yPositionsFit[n] = yPositions[selection][i];
					n++;
				}
				
			}

			// fit all the found x and y positions with polynomial
			
			LevenbergMarquardt lm = new LevenbergMarquardt() {
				
				@Override
				public double getValue(double[] x, double[] p) {
					return polynomial(x[0], p);
				}
				
				@Override
				public void getGradient(double[] x, double[] p, double[] dyda) {
					
					for (int i = 0; i < p.length; i++)
						dyda[i] = Math.pow(x[0], i);
				}


			};
			
			for (int i = 0; i < degreeOfPolynomial; i++) {
				xParameters[i] = 0.1 * Math.pow(0.1, i);
				yParameters[i] = 0.1 * Math.pow(0.1, i);
			}
			
			lm.solve(xFit, xPositionsFit, null, n, xParameters, null, xError, 0.001);
			lm.solve(xFit, yPositionsFit, null, n, yParameters, null, yError, 0.001);
			
			// create plot
			int size = (toSlice - fromSlice) + 1;
			double[] xFittedPlot = new double[size];
			double[] xFittedPositions = new double[size];
			double[] yFittedPositions = new double[size];
			
			for (int i = 0; i < size; i++) {
				xFittedPlot[i] = fromSlice + i;
				xFittedPositions[i] = polynomial(fromSlice + i, xParameters);
				yFittedPositions[i] = polynomial(fromSlice + i, yParameters);
			}
			
			Plot plot = new Plot();
			plot.addScatterPlot(xPlot, xPositionsFit, Color.RED, 1.0f);
			plot.addScatterPlot(xPlot, yPositionsFit, Color.BLUE, 1.0f);
			plot.addLinePlot(xFittedPlot, xFittedPositions, Color.RED, 2.0f);
			plot.addLinePlot(xFittedPlot, yFittedPositions, Color.BLUE, 2.0f);
			plot.setxAxisLabel("Slice");
			plot.setyAxisLabel("Drift (pixels)");
			
			JPanel panel = new JPanel();

			panel.add(correctResultsButton);
			panel.add(correctImageButton);
			
			correctResultsButton.addActionListener(this);
			correctImageButton.addActionListener(this);
			
			JFrame frame = new JFrame("Drift Correction");
			Container contentPane = frame.getContentPane();
			contentPane.setLayout(new BorderLayout());
			contentPane.add(plot, BorderLayout.CENTER);
			contentPane.add(panel, BorderLayout.SOUTH);
			frame.setSize(800, 600);
			frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			frame.setVisible(true);
			
			return DONE;
		}
		
		roiManager = RoiManager.getInstance();
		
		if (roiManager == null) {
			IJ.showMessage("This plugin needs selections in the roi manager. Select the peaks for correcting on drift first and add them to the roi manager!");
			return DONE;
		}
		
		fromSlice = 1;
		toSlice = imp.getNSlices();
		
		GenericDialog dialog = new GenericDialog("Drift Correction");
		
		dialog.addNumericField("From_slice", fromSlice, 0);
		dialog.addNumericField("To_slice", toSlice, 0);
		
		dialog.addNumericField("Max_error_baseline", maxError[0], 2);
		dialog.addNumericField("Max_error_height", maxError[1], 2);
		dialog.addNumericField("Max_error_x", maxError[2], 2);
		dialog.addNumericField("Max_error_y", maxError[3], 2);
		dialog.addNumericField("Max_error_sigma_x", maxError[4], 2);
		dialog.addNumericField("Max_error_sigma_y", maxError[5], 2);
		
		dialog.addNumericField("Degree_of_polynomial", degreeOfPolynomial, 0);
		
		dialog.showDialog();
		
		if (dialog.wasCanceled())
			return DONE;
		
		
		fromSlice = (int)dialog.getNextNumber();
		toSlice = (int)dialog.getNextNumber();
		
		if (fromSlice < 1)
			fromSlice = 1;
		
		if (toSlice > imp.getNSlices())
			toSlice = imp.getNSlices();
		
		for (int i = 0; i < maxError.length; i++)
			maxError[i] = dialog.getNextNumber();
		
		degreeOfPolynomial = (int)dialog.getNextNumber() + 1;
		
		int selections = roiManager.getCount();
		int slices = imp.getNSlices();
		
		x = new double[selections][slices];
		xPositions = new double[selections][slices];
		yPositions = new double[selections][slices];
		fittedPeaks = new int[selections];
		
		xParameters = new double[degreeOfPolynomial];
		yParameters = new double[degreeOfPolynomial];
		xError = new double[degreeOfPolynomial];
		yError = new double[degreeOfPolynomial];
		
		return flags;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		
		if (e.getSource() == correctResultsButton) {
			
			ResultsTable table = Analyzer.getResultsTable();
			
			if (table == null) {
				IJ.showMessage("No results table!");
				return;
			}
			
			for (int i = 0; i < table.getCounter(); i++) {
				double slice = table.getValue("slice", i);
				double x = table.getValue("x", i) - polynomial(slice - 1, xParameters);
				double y = table.getValue("y", i) - polynomial(slice - 1, yParameters);
				
				table.setValue("x", i, x);
				table.setValue("y", i, y);
			}
			
			table.show("Results");
			
		}
		else if (e.getSource() == correctImageButton) {
			
			ImagePlus imp = IJ.getImage();
			ImageStack stack = imp.getStack();
			
			for (int i = 0; i < stack.getSize(); i++) {
				
				
				ImageProcessor ip = stack.getProcessor(i + 1);
				ip.setInterpolationMethod(ImageProcessor.BILINEAR);
				double dx = -polynomial(i, xParameters);
				double dy = -polynomial(i, yParameters);
				
				ip.translate(dx, dy);
			}
			
			imp.show();
		}
		
	}

}
