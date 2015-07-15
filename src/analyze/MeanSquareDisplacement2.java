package analyze;

import java.awt.Color;
import java.util.ArrayList;

import util.ResultsTableSorter;
import ij.IJ;
import ij.gui.GenericDialog;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.filter.Analyzer;

public class MeanSquareDisplacement2 implements PlugIn {

	private double timeInterval = 0.1;
	private double pixelSize = 0.160;
	private int minimumNumberOfPoints = 1;
	private double maxFitTime = 0;
	private String dimensionality = "2D";
	private boolean averageTrajectories = false;
	private int dimensionalityFit = 4;	// 2D
	private boolean showFitPlots = false;
	
	@Override
	public void run(String arg0) {

		ResultsTable table = Analyzer.getResultsTable();
		
		if (table == null) {
			IJ.error("This plugin requires a results table!");
			return;
		}
		
		GenericDialog dialog = new GenericDialog("Mean Square Displacement");
		dialog.addNumericField("Time_interval", timeInterval, 6, 10, "s");
		dialog.addNumericField("Pixel_size", pixelSize, 6, 10, " µm");
		dialog.addNumericField("Minimum_number_of_points", minimumNumberOfPoints, 0);
		dialog.addNumericField("Fit_until (0 = all)", maxFitTime, 6, 10, "s");
		dialog.addChoice("Diffusion_dimensionality", new String[]{"1D", "2D"}, dimensionality);
		dialog.addCheckbox("Average_all_trajectories", averageTrajectories);
		dialog.addCheckbox("Show_fit_plots", showFitPlots);
		
		dialog.showDialog();
		
		if (dialog.wasCanceled())
			return;
		
		timeInterval = dialog.getNextNumber();
		pixelSize = dialog.getNextNumber();
		minimumNumberOfPoints = (int)dialog.getNextNumber();
		maxFitTime = dialog.getNextNumber();
		dimensionality = dialog.getNextChoice();
		averageTrajectories = dialog.getNextBoolean();
		showFitPlots = dialog.getNextBoolean();
		
		if (dimensionality.equals("1D"))
			dimensionalityFit = 2;
		
		if (maxFitTime == 0)
			maxFitTime = Double.MAX_VALUE;
		
		ResultsTable sdTable = new ResultsTable();
		sdTable.setPrecision(Analyzer.getPrecision());
		
		for (int row1 = 0; row1 < table.getCounter(); row1++) {
			
			double x1 = table.getValue("x", row1);
			double y1 = table.getValue("y", row1);
			int slice1 = (int)table.getValue("slice", row1);
			int trajectory1 = (int)table.getValue("trajectory", row1);
			
			for (int row2 = row1 + 1; row2 < table.getCounter(); row2++) {
				
				double x2 = table.getValue("x", row2);
				double y2 = table.getValue("y", row2);
				int slice2 = (int)table.getValue("slice", row2);
				int trajectory2 = (int)table.getValue("trajectory", row2);
				
				if (trajectory1 != trajectory2)
					break;
				
				double dx = (x2 - x1) * pixelSize;
				double dy = (y2 - y1) * pixelSize;
				double dt = (slice2 - slice1) * timeInterval;	// delta t
				double sd = dx * dx + dy * dy;					// square displacement
				
				sdTable.incrementCounter();
				sdTable.addValue("trajectory", averageTrajectories ? -1 : trajectory1);
				sdTable.addValue("dt", dt);
				sdTable.addValue("sd", sd);
				
			}
			
		}
		
		ResultsTableSorter.sort(sdTable, true, "trajectory", "dt");
		sdTable.show("square displacements");
		
		
		// find the start and end of each group (same dt and trajectory) that we need to average
		// we need to calculate the mean square displacement for each dt, trajectory individually
		ArrayList<Integer> offsets = new ArrayList<Integer>();
		offsets.add(0);
		
		for (int row = 0; row < sdTable.getCounter(); row++) {
			
			double dt = sdTable.getValue("dt", row);
			int trajectory = (int)sdTable.getValue("trajectory", row);
			
			if (row + 1 >= sdTable.getCounter() || dt != sdTable.getValue("dt", row + 1) || trajectory != sdTable.getValue("trajectory", row + 1)) {
				offsets.add(row + 1);
			}
			
		}
		
		// calculate for each group the mean square displacement and standard deviation
		ResultsTable msdTable = new ResultsTable();
		msdTable.setPrecision(Analyzer.getPrecision());
		
		for (int i = 0; i < offsets.size() - 1; i++) {
			
			int from = offsets.get(i);
			int to = offsets.get(i + 1);
			
			double mean = 0.0;
			
			for (int row = from; row < to; row++)
				mean += sdTable.getValue("sd", row);
			
			mean /= (to - from);
			
			double stdDev = 0.0;
			
			for (int row = from; row < to; row++) {
				double d = sdTable.getValue("sd", row) - mean;
				stdDev += d * d;
			}
			
			stdDev = Math.sqrt(stdDev / (to - from));
			
			if (to - from > minimumNumberOfPoints) {
				msdTable.incrementCounter();
			
				msdTable.addValue("trajectory", sdTable.getValue("trajectory", from));
				msdTable.addValue("dt", sdTable.getValue("dt", from));
				msdTable.addValue("msd", mean);
				msdTable.addValue("stdDev", stdDev);
				msdTable.addValue("points", to - from);
			}
				
		}
		
		
		msdTable.show("mean square displacements");
		
		
		// fit the mean square displacements for each trajectory individually
		ResultsTable dTable = new ResultsTable();
		dTable.setPrecision(9);
		
		ArrayList<Double> dts = new ArrayList<Double>();
		ArrayList<Double> msds = new ArrayList<Double>();
		ArrayList<Double> stdDevs = new ArrayList<Double>();
		
		for (int row = 0; row < msdTable.getCounter(); row++) {
			
			int trajectory = (int) msdTable.getValue("trajectory", row);
			double dt = msdTable.getValue("dt", row);
			double msd = msdTable.getValue("msd", row);
			double stdDev = msdTable.getValue("stdDev", row);
			
			dts.add(dt);
			msds.add(msd);
			stdDevs.add(stdDev);
			
			if (row + 1 >= msdTable.getCounter() || trajectory != msdTable.getValue("trajectory", row + 1)) {
				
				if (dts.size() > 0) {
					
					double[] x = new double[dts.size()];
					double[] y = new double[msds.size()];
					double[] error = new double[stdDevs.size()];
					
					double[] yfit = new double[x.length];
					double[][] xfit = new double[x.length][1];
					int n = 0;
					
					for (int i = 0; i < x.length; i++) {
						
						x[i] = dts.get(i);
						y[i] = msds.get(i);
						error[i] = stdDevs.get(i);
						
						if (x[i] <= maxFitTime) {
							yfit[n] = y[i];
							xfit[n][0] = x[i];
							n++;
						}
						
					}
					
					if (n == 0) continue;
					
					// fit data
					LevenbergMarquardt lm = new LevenbergMarquardt() {
						
						@Override
						public double getValue(double[] x, double[] p) {
							return dimensionalityFit * p[0] * x[0];
						}
						
						@Override
						public void getGradient(double[] x, double[] p, double[] dyda) {
							dyda[0] = dimensionalityFit * x[0];
						}
						
					};
					
					double estimatedMsd = yfit[n - 1];
					double t = xfit[n - 1][0];
					
					double[] p = new double[]{estimatedMsd / (dimensionalityFit * t)};
					double[] e = new double[1];
					
					lm.solve(xfit, yfit, error, n, p, null, e, 0.001);
					
					double[] fx = new double[2];
					double[] fy = new double[2];
					
					fx[0] = 0;
					fy[0] = 0;
					
					fx[1] = t;
					fy[1] = dimensionalityFit * p[0] * t;
										
					if (!Double.isNaN(fy[1])) {
						
						if (showFitPlots) {
							Plot plot = new Plot();
							plot.addErrorBars(x, y, error, Color.GRAY, 1f);
							plot.addScatterPlot(x, y, Color.BLACK, 1f);
							plot.addLinePlot(fx, fy, Color.RED, 1f);
							plot.setCaption(" D = " + p[0] + " µm^2/s  fitting error = " + e[0] + " Trajectory = " + trajectory);
							plot.setxAxisLabel("Time Step (s)");
							plot.setyAxisLabel("Mean Square Displacement (µm)");
							plot.showPlot("Mean Square Displacement");
						}
						
						dTable.incrementCounter();
						dTable.addValue("trajectory", trajectory);
						dTable.addValue("D", p[0]);
						dTable.addValue("D_error", e[0]);
						dTable.addValue("R^2", lm.rSquared);
						
					}
					
				}
				
				dts.clear();
				msds.clear();
				stdDevs.clear();

			}
			
		}
		
		dTable.show("Diffusion Coefficients");
	}

}
