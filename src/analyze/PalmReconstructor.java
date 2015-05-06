package analyze;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.filter.Analyzer;
import ij.process.ImageProcessor;

public class PalmReconstructor implements PlugIn {

	public enum Type {
		GAUSSIAN,
		PIXEL;
		
		public static String[] getNames() {
			
			Type[] types = values();
			String[] names = new String[types.length];
			
			for (int i = 0; i < names.length; i++)
				names[i] = types[i].toString();
			
			return names;
		}
		
	};
	
	private ResultsTable table;
	
	private double magnification = 4;
	private Type type;
	
	@Override
	public void run(String arg0) {

		table = Analyzer.getResultsTable();
		
		if (table == null || table.getCounter() == 0) {
			IJ.error("This plugin requires a results table!");
			return;
		}
		
		double xMin = table.getValue("x", 0);
		double yMin = table.getValue("y", 0);
		double xMax = xMin;
		double yMax = yMin;
		
		// get minimum and maximum x and y values from the results table
		for (int row = 1; row < table.getCounter(); row++) {
			
			double x = table.getValue("x", row);
			double y = table.getValue("y", row);
			
			if (x < xMin) xMin = x;
			if (y < yMin) yMin = y;
			if (x > xMax) xMax = x;
			if (y > yMax) yMax = y;
		}
		
		xMin = Math.floor(xMin);
		yMin = Math.floor(yMin);
		xMax = Math.ceil(xMax);
		yMax = Math.ceil(yMax);
		
		GenericDialog dialog = new GenericDialog("PALM Reconstructor");
		
		dialog.addNumericField("x_min", xMin, 2);
		dialog.addNumericField("y_min", yMin, 2);
		dialog.addNumericField("x_max", xMax, 2);
		dialog.addNumericField("y_max", yMax, 2);
		dialog.addNumericField("magnification", magnification, 2);
		dialog.addChoice("type", Type.getNames(), Type.getNames()[0]);
		
		dialog.showDialog();
		
		if (dialog.wasCanceled())
			return;
		
		xMin = dialog.getNextNumber();
		yMin = dialog.getNextNumber();
		xMax = dialog.getNextNumber();
		yMax = dialog.getNextNumber();
		
		magnification = dialog.getNextNumber();
		
		type = Type.valueOf(Type.class, dialog.getNextChoice());
		
		int width = (int)((xMax - xMin) * magnification);
		int height = (int)((yMax - yMin) * magnification);
		
		// create image stack
		ImagePlus imp = IJ.createImage("reconstruction", "32-bit", width, height, 1);
		ImageProcessor ip = imp.getProcessor();
		
		for (int row = 0; row < table.getCounter(); row++) {
			
			double x = table.getValue("x", row);
			double y = table.getValue("y", row);
			double errorX = table.getValue("error_x", row) * magnification;
			double errorY = table.getValue("error_y", row) * magnification;
			
			int x0 = (int)((x - xMin) * magnification);
			int y0 = (int)((y - yMin) * magnification);
			
			switch (type) {
			case GAUSSIAN:
				for (int y1 = -10; y1 <= 10; y1++) {
					for (int x1 = -10; x1 <= 10; x1++) {
						
						double value = normalDistribution(x1, y1, errorX, errorY);
						ip.putPixelValue(x0 + x1, y0 + y1, value + ip.getPixelValue(x0 + x1, y0 + y1));
						
					}
				}
				
				break;
			case PIXEL:
				
				ip.putPixelValue(x0, y0, 255);
				
				break;
			}
			
			
			IJ.showProgress(row, table.getCounter());
			
			
		}
		
		Calibration c = imp.getCalibration();
		c.xOrigin = -xMin * magnification;
		c.yOrigin = -yMin * magnification;
		c.pixelWidth = 1 / magnification;
		c.pixelHeight = 1 / magnification;
		
		imp.show();
		IJ.run("Red Hot");
	}
	
	public static double normalDistribution(double x, double y, double sigmaX, double sigmaY) {
		return Math.exp(-((x * x) / (2 * sigmaX * sigmaX) + (y * y) / (2 * sigmaY * sigmaY))) / (2 * Math.PI * sigmaX * sigmaY);
	}
		
	public static void main(String[] args) {
		
		double sum = 0;
		
		for (double x = -20; x <= 20; x += 0.1) {
			for (double y = -20; y <= 20; y += 0.1) {
				sum += normalDistribution(x, y, 2, 5) * 0.01;
			}
		}

		System.out.printf("%f", sum);
		
	}

}

