package analyze;

import ij.IJ;
import ij.gui.GenericDialog;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.filter.Analyzer;

import java.awt.Color;
import java.util.LinkedList;

import javax.swing.JFrame;

public class StepFitter implements PlugIn {

	private double[] data;
	
	private double sigma;
	private LinkedList<Step> steps = new LinkedList<Step>();
	private LinkedList<Step> counterSteps = new LinkedList<Step>();
	private double totalChiSquared;
	private double counterChiSquared;
	private class Split {
		public Step left;
		public Step right;
		private double chiSquared;
		public Split(Step left, Step right) {
			this.left = left;
			this.right = right;
			chiSquared = left.chiSquared + right.chiSquared;
		}
	}
	
	/**
	 * The Class Step.
	 */
	private class Step {
		public int from;
		public int to;
		public double mean;
		public double chiSquared;
		
		/**
		 * Instantiates a new step.
		 *
		 * @param from the from
		 * @param to the to
		 */
		public Step(int from, int to) {
			this.from = from;
			this.to = to;
			
			// calculate mean
			mean = 0.0;
			for (int i = from; i < to; i++)
				mean += data[i];
			mean /= (to - from);
			
			// calculate chi squared
			chiSquared = 0.0;
			for (int i = from; i < to; i++) {
				double residual = (data[i] - mean) / sigma;
				chiSquared += residual * residual;
			}
		}
		
		/**
		 * Gets the split point.
		 *
		 * @return the split point
		 */
		public Split getSplitPoint() {
			double minChiSquared = chiSquared;
			Split bestSplit = null;
			
			for (int i = from + 1; i < to; i++) {
				Step left = new Step(from, i);
				Step right = new Step(i, to);
				Split split = new Split(left, right);
				
				if (split.chiSquared < minChiSquared) {
					minChiSquared = split.chiSquared;
					bestSplit = split;					
				}
			}
			
			return bestSplit;
		}
	}
	
	public StepFitter() {}
	
	/**
	 * Instantiates a new step fitter.
	 *
	 * @param data the data
	 * @param sigma the sigma
	 */
	public StepFitter(double[] data, double sigma) {
		this.data = data;
		this.sigma = sigma;
		
		clear();
	}
	
	/**
	 * Clear.
	 */
	public void clear() {
		steps.clear();
		
		Step step = new Step(0, data.length);
		totalChiSquared = step.chiSquared;
		
		steps.add(step);
		
		setCounterSteps();
	}
	
	/**
	 * Gets the steps x.
	 *
	 * @return the steps x
	 */
	public double[] getStepsX() {
		double[] x = new double[steps.size() * 2];
		
		int i = 0;
		for (Step step: steps) {
			x[i++] = step.from;
			x[i++] = step.to;
		}
		
		return x;
	}
	
	/**
	 * Gets the steps y.
	 *
	 * @return the steps y
	 */
	public double[] getStepsY() {
		double[] y = new double[steps.size() * 2];
		
		int i = 0;
		for (Step step: steps) {
			y[i++] = step.mean;
			y[i++] = step.mean;
		}
		
		return y;
	}
	
	/**
	 * Gets the counter steps x.
	 *
	 * @return the counter steps x
	 */
	public double[] getCounterStepsX() {
		double[] x = new double[counterSteps.size() * 2];
		
		int i = 0;
		for (Step step: counterSteps) {
			x[i++] = step.from;
			x[i++] = step.to;
		}
		
		return x;
	}
	
	/**
	 * Gets the counter steps y.
	 *
	 * @return the counter steps y
	 */
	public double[] getCounterStepsY() {
		double[] y = new double[counterSteps.size() * 2];
		
		int i = 0;
		for (Step step: counterSteps) {
			y[i++] = step.mean;
			y[i++] = step.mean;
		}
		
		return y;
	}
	
	/**
	 * Adds the step.
	 */
	public void addStep() {
		double minChiSquared = totalChiSquared;
		Split bestSplit = null;
		int index = -1;
		
		for (int i = 0; i < steps.size(); i++) {
			Step step = steps.get(i);
			
			if (totalChiSquared - step.chiSquared > minChiSquared || step.to - step.from < 2)
				continue;
			
			Split split = step.getSplitPoint();
			double chiSquared = (totalChiSquared - step.chiSquared) + split.chiSquared;
			
			if (chiSquared < minChiSquared) {
				minChiSquared = chiSquared;
				bestSplit = split;
				index = i;
			}
		}
		
		steps.remove(index);
		steps.add(index, bestSplit.right);
		steps.add(index, bestSplit.left);
		
		setCounterSteps();
		
		totalChiSquared = minChiSquared;
	}
	
	/**
	 * Sets the counter steps.
	 */
	private void setCounterSteps() {
		counterChiSquared = 0;
		counterSteps.clear();
		int last = 0;
		
		for (int i = 0; i < steps.size(); i++) {
			
			if (steps.get(i).to - steps.get(i).from == 1)
				continue;
			
			Split split = steps.get(i).getSplitPoint();
			Step step = new Step(last, split.left.to);
			counterChiSquared += step.chiSquared;
			counterSteps.add(step);
			last = split.right.from;
		}
		
		Step step = new Step(last, data.length);
		counterSteps.add(step);
		counterChiSquared += step.chiSquared;
	}
	
	/**
	 * Gets the chi squared.
	 *
	 * @return the chi squared
	 */
	public double getChiSquared() {
		return totalChiSquared;
	}
	
	/**
	 * Gets the counter chi squared.
	 *
	 * @return the counter chi squared
	 */
	public double getCounterChiSquared() {
		return counterChiSquared;
	}
	
	/**
	 * To string.
	 *
	 * @return the string
	 */
	@Override
	public String toString() {
		String str = Integer.toString(steps.getFirst().from);
		
		for (Step step: steps)
			str += String.format(", %d", step.to);
		
		return str;
	}
	
	@Override
	public void run(String arg0) {

		ResultsTable table = Analyzer.getResultsTable();
		
		if (table == null) {
			IJ.showMessage("PlugIn requires a results table");
			return;
		}
		
		String[] headings = table.getHeadings();
		
		if (headings.length == 0) {
			IJ.showMessage("No columns present in results table");
			return;
		}
		
		GenericDialog dialog = new GenericDialog("Step Fitter");
		dialog.addChoice("Column_x", headings, headings[0]);
		dialog.addChoice("Column_y", headings, headings[0]);
		dialog.addCheckbox("No_x_column", true);
		dialog.addNumericField("Number_of_steps (0=auto detect)", 0, 0);
		dialog.addCheckbox("Plot_steps", true);
		dialog.showDialog();
		
		String xColumn = dialog.getNextChoice();
		String yColumn = dialog.getNextChoice();
		boolean noXColumn = dialog.getNextBoolean();
		int steps = (int)dialog.getNextNumber();
		boolean plotSteps = dialog.getNextBoolean();
		
		
		double[] x = new double[table.getCounter()];
		double[] y = new double[table.getCounter()];
		
		for (int row = 0; row < table.getCounter(); row++) {
			x[row] = noXColumn ? row : table.getValue(xColumn, row);
			y[row] = table.getValue(yColumn, row);
		}
		
		StepFitter fitter = new StepFitter(y, 1);
		
		double previousRatio = 0;
		double ratio = Double.MAX_VALUE;
		
		if (steps == 0) {
			
			do {
				steps++;
				fitter.addStep();
				previousRatio = ratio;
				ratio = fitter.getChiSquared() / fitter.getCounterChiSquared();
			
			} while (ratio < previousRatio);
			
		}
		else {
			for (int step = 0; step < steps; step++)
				fitter.addStep();
		}
		
		table.reset();
		
		
		double[] xSteps = fitter.getStepsX();
		double[] ySteps = fitter.getStepsX();
		double[] counterXSteps = fitter.getCounterStepsX();
		double[] counterYSteps = fitter.getCounterStepsY();
		
		for (int i = 0; i < xSteps.length; i += 2) {
			
			int from = (int)xSteps[i];
			int to = (int)xSteps[i + 1];
			
			if (to >= x.length)
				to = x.length - 1;
			
			table.incrementCounter();
			table.addValue("from", x[from]);
			table.addValue("to", x[to]);
			table.addValue("signal", ySteps[i]);

			if (i < counterXSteps.length) {
				int from1 = (int)counterXSteps[i];
				int to1 = (int)counterXSteps[i + 1];
				
				table.addValue("counter_from", x[from1]);
				table.addValue("counter_to", x[to1]);
				table.addValue("counter_to", counterYSteps[i]);
			}

		}
		
		table.updateResults();
		table.show("Results");
		
		
		if (plotSteps) {
			Plot plot = new Plot();
			plot.addLinePlot(x, y, Color.BLACK, 1);
			
			double[] x1 = fitter.getCounterStepsX();
			double[] x2 = fitter.getStepsX();
			
			for (int i = 0; i < x1.length; i++)
				x1[i] = (x1[i] < x.length) ? x[(int)x1[i]] : x[x.length - 1];
			for (int i = 0; i < x2.length; i++)
				x2[i] = (x2[i] < x.length) ? x[(int)x2[i]] : x[x.length - 1];

			plot.addLinePlot(x1, fitter.getCounterStepsY(), Color.BLUE, 2);
			plot.addLinePlot(x2, fitter.getStepsY(), Color.RED, 2);
			
			JFrame frame = new JFrame("Step Fitter");
			frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			frame.setSize(500, 500);
			frame.getContentPane().add(plot);
			frame.setVisible(true);
		}
	}
}
