package analyze;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;

import ij.IJ;
import ij.gui.GenericDialog;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.filter.Analyzer;

public class StepSizeDistribution implements PlugIn {

	private double timeInterval = 0.034;
	private double pixelSize = 0.1;
	private double binning = pixelSize / 10;
	private double minStepSize = 0; 
	
	@Override
	public void run(String arg0) {
		ResultsTable table = Analyzer.getResultsTable();
		
		if (table == null) {
			IJ.showMessage("Plugin requires a results table!");
			return;
		}
		
		GenericDialog dialog = new GenericDialog("Step Size Distribution");
		dialog.addNumericField("Time_interval", timeInterval, 6, 10, "s");
		dialog.addNumericField("Pixel_size", pixelSize, 6, 10, " µm");
		dialog.addNumericField("Binning", pixelSize / 100, 6, 10, " µm");
		dialog.addNumericField("Minimum_step_size", minStepSize, 6, 10, " µm");
		dialog.showDialog();
		
		if (dialog.wasCanceled())
			return;
		
		timeInterval = dialog.getNextNumber();
		pixelSize = dialog.getNextNumber();
		binning = dialog.getNextNumber();
		minStepSize = dialog.getNextNumber();
		
		// determine which rows belong to which trajectory
		ArrayList<Integer> offsets = new ArrayList<Integer>();
		offsets.add(0);
		
		for (int row = 1; row < table.getCounter(); row++) {
			int s0 = (int)table.getValue("trajectory", row - 1);
			int s1 = (int)table.getValue("trajectory", row);
			
			if (s0 != s1)
				offsets.add(row);
		}
		
		offsets.add(table.getCounter());
		
		ArrayList<Double> stepSizes = new ArrayList<Double>();
		double maxStepSize = 0;
		
		for (int i = 0; i < offsets.size() - 1; i++) {
			int from = offsets.get(i);
			int to = offsets.get(i + 1);
			int trajectory = (int)table.getValue("trajectory", from);
			
			if (trajectory >= 0) {
				
				for (int row = from; row < to - 1; row++) {
					int s1 = (int)table.getValue("slice", row);
					double x1 = table.getValue("x", row);
					double y1 = table.getValue("y", row);

					int s2 = (int)table.getValue("slice", row + 1);
					double x2 = table.getValue("x", row + 1);
					double y2 = table.getValue("y", row + 1);
					
					if (s2 - s1 == 1) {
						double dx = x2 - x1;
						double dy = y2 - y1;
						double stepSize = Math.sqrt(dx * dx + dy * dy) * pixelSize;
						
						if (stepSize > maxStepSize )
							maxStepSize = stepSize;
						
						stepSizes.add(stepSize);
					}
				}
				
			}
		}
		
		// create distribution
		int n = (int)(maxStepSize / binning) + 1;
		double[] probabilities = new double[n];
		double[] steps = new double[n];
		double[][] steps2 = new double[n][1];
		double msd = 0.0;
		
		for (double stepSize: stepSizes) {
			int bin = (int)(stepSize / binning);
			
			probabilities[bin]++;
			msd += stepSize * stepSize;
		}

		msd /= stepSizes.size();
		
		// normalize
		for (int i = 0; i < probabilities.length; i++) {
			probabilities[i] /= (stepSizes.size() * binning);
			steps[i] = (i + 0.5) * binning;
			steps2[i][0] = steps[i];
		}
		
		// remove all steps lower then the specified minimum step size
		for (int i = 0; i < steps2.length; i++) {
			
			if (steps2[i][0] >= minStepSize) {
				steps = Arrays.copyOfRange(steps, i, steps.length);
				steps2 = Arrays.copyOfRange(steps2, i, steps2.length);
				probabilities = Arrays.copyOfRange(probabilities, i, probabilities.length);
				break;				
			}
			
		}
		
		// fit step size distribution with function
		LevenbergMarquardt lm = new LevenbergMarquardt() {
			
			@Override
			public double getValue(double[] x, double[] p) {
				return func(x[0], p[0]);
			}
			
			@Override
			public void getGradient(double[] x, double[] p, double[] dyda) {
				double delta = 1e-6;
				dyda[0] = func(x[0], p[0] + delta);
				dyda[0] -= func(x[0], p[0] - delta);
				dyda[0] /= 2 * delta;
			}
		};
		
		double[] p = new double[]{msd};
		double[] e = new double[1];
		
		lm.solve(steps2, probabilities, null, steps2.length, p, null, e, 0.001);
		
		// calculate 2D diffusion coefficient
		double D = p[0] / (4 * timeInterval);
		
		double[] x2 = new double[1000];
		double[] y2 = new double[1000];
		
		for (int i = 0; i < 1000; i++) {
			x2[i] = maxStepSize * i / 1000.0;
			y2[i] = func(x2[i], p[0]);
		}

		// plot the result
		Plot plot = new Plot();
		plot.addScatterPlot(steps, probabilities, Color.BLACK, 2f);
		plot.addLinePlot(x2, y2, Color.RED, 1f);
		plot.showPlot("Step Size Distribution");
		plot.setCaption("D = " + D + " µm^2/s  fitting error = " + e[0] + " msd = " + msd);
		plot.setxAxisLabel("Step size (µm)");
		plot.setyAxisLabel("Probability");

	}
	
	public double func(double r, double msd) {
		// obtained from "A Wide-Field View at Single Molecules and Single Particles" by F. Lusitani (p. 43)
		return ((2 * r) / msd) * Math.exp(-((r * r) / msd));
	}

}
