package analyze;

import java.awt.Polygon;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;

public class Colocalization implements PlugIn {
	private static final int fittingRadius = 5;
	
	public static double[] align(ImageProcessor ip, PointRoi proi) {
		Polygon polygon = proi.getPolygon();
		
		double[] xpoints = new double[polygon.npoints];
		double[] ypoints = new double[polygon.npoints];
		
		// fit each selected point
		int fittingWidth = fittingRadius * 2 + 1;
		ResultsTable table = ResultsTable.getResultsTable();
		
		table = new ResultsTable();
		
		for (int i = 0; i < polygon.npoints; i++) {
			ip.setRoi(polygon.xpoints[i] - fittingRadius, polygon.ypoints[i] - fittingRadius, fittingWidth, fittingWidth);
			
			double[] p = new double[]{Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN};
			double[] e = new double[6];
			
			PeakFitter.fitPeak(ip, p, e);
			
			xpoints[i] = p[2];
			ypoints[i] = p[3];
			
			PeakFitter.addToResultsTable(table, p, e, ip.getSliceNumber());
		}
		
		table.show("Fitted points");
		
		// calculate mean dx and mean dy
		int halfwidth = ip.getWidth() / 2;
		
		int npoints = 0;
		
		double[] dxs = new double[xpoints.length / 2];
		double[] dys = new double[xpoints.length / 2];
		
		for (int i = 0; i < xpoints.length; i++) {
			
			double x = xpoints[i];
			double y = ypoints[i];
			
			double mindistance = Double.MAX_VALUE;
			double mindx = Double.MAX_VALUE;
			double mindy = Double.MAX_VALUE;
			
			for (int j = i + 1; j < xpoints.length; j++) {
				
				// find corresponding point
				if (x < halfwidth && xpoints[j] >= halfwidth) {
					double dx = (x + halfwidth) - xpoints[j];
					double dy = y - ypoints[j];
					double distance = Math.sqrt(dx * dx + dy * dy);
					
					if (distance < mindistance) {
						mindistance = distance;
						mindx = dx;
						mindy = dy;
					}
					
				}
				
			}
			
			if (mindistance < 50) {
				dxs[npoints] = mindx;
				dys[npoints] = mindy;
				npoints++;
			}
		}

		double meandx = 0;
		double meandy = 0;
		double stddevdx = 0;
		double stddevdy = 0;
		
		for (int i = 0; i < dxs.length; i++) {
			meandx += dxs[i];
			meandy += dys[i];
		}
		
		meandx /= npoints;
		meandy /= npoints;
		
		for (int i = 0; i < dxs.length; i++) {
			stddevdx += (dxs[i] - meandx) * (dxs[i] - meandx);
			stddevdy += (dys[i] - meandy) * (dys[i] - meandy);
		}
		
		stddevdx = Math.sqrt(stddevdx / npoints);
		stddevdy = Math.sqrt(stddevdy / npoints);
		
		return new double[]{meandx, meandy, stddevdx, stddevdy};
	}
	
	
	@Override
	public void run(String arg0) {
		ImagePlus imp = IJ.getImage();
		
		
		RoiManager roiManager = RoiManager.getInstance();
		
		if (roiManager == null) {
			IJ.showMessage("No ROI manager! Run Peak Finder first");
			return;
		}
		
		Roi[] rois = roiManager.getRoisAsArray();
		int[] x = new int[rois.length];
		int[] y = new int[rois.length];
		
		for (int i = 0; i < rois.length; i++) {
			
			x[i] = (int)rois[i].getBounds().getCenterX();
			y[i] = (int)rois[i].getBounds().getCenterY();
			
		}
		
		PointRoi points = new PointRoi(x, y, rois.length);				
		
		

		ImageProcessor ip = imp.getProcessor();
		double[] dxdy = align(ip, points);
		IJ.showMessage(String.format("dx: %f dy: %f", dxdy[0], dxdy[1]));
	}
}

