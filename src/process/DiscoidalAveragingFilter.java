package process;

import java.awt.AWTEvent;
import java.awt.Rectangle;
import java.util.ArrayList;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;

public class DiscoidalAveragingFilter implements ExtendedPlugInFilter, DialogListener {

	private int flags = DOES_8G | DOES_16 | DOES_32 | PARALLELIZE_STACKS;
	
	private int innerRadius = Prefs.getInt("DiscoidalAveragingFilter.innerRadius", 1);
	private int outerRadius = Prefs.getInt("DiscoidalAveragingFilter.outerRadius", 4);
	
	private ImagePlus imp;
	private int[] innerOffsets;
	private int[] outerOffsets;
	
	public DiscoidalAveragingFilter() {
		
	}
	
	public DiscoidalAveragingFilter(int width, int innerRadius, int outerRadius) {
		setCircleOffsets(width, innerRadius, outerRadius);
	}
	
	public void setCircleOffsets(int width, int innerRadius, int outerRadius) {
		ArrayList<Integer> innerOffsetList = new ArrayList<Integer>();
		ArrayList<Integer> outerOffsetList = new ArrayList<Integer>();
		
		for (int y = -outerRadius; y <= outerRadius; y++) {
			for (int x = -outerRadius; x <= outerRadius; x++) {
				double d = Math.round(Math.sqrt(x * x + y * y));
				int offset = x + y * width;

				if (d <= innerRadius)
					innerOffsetList.add(offset);
				
				if (d == outerRadius)
					outerOffsetList.add(offset);
				
			}
		}
		
		innerOffsets = new int[innerOffsetList.size()];
		outerOffsets = new int[outerOffsetList.size()];
		
		for (int i = 0; i < innerOffsets.length; i++)
			innerOffsets[i] = innerOffsetList.get(i);
		
		for (int i = 0; i < outerOffsets.length; i++)
			outerOffsets[i] = outerOffsetList.get(i);
	}
	
	@Override
	public void run(ImageProcessor ip) {
		
		ImageProcessor duplicate = ip.duplicate();
		
		double innerMean;
		double outerMean;
		int innerPixels;
		int outerPixels;
		
		int offset;
		int width = ip.getWidth();
		int count = ip.getPixelCount();
		
		Rectangle roi = ip.getRoi();
		
		for (int y = roi.y; y < roi.y + roi.height; y++) {
			
			offset = y * width + roi.x;
			
			for (int x = roi.x; x < roi.x + roi.width; x++) {
				
				innerMean = 0;
				outerMean = 0;
				innerPixels = 0;
				outerPixels = 0;
				
				for (int circleOffset: innerOffsets) {
					circleOffset += offset;
					
					if (circleOffset >= 0 && circleOffset < count) {
						innerMean += duplicate.getf(circleOffset);
						innerPixels++;
					}
				}
				
				for (int circleOffset: outerOffsets) {
					circleOffset += offset;
					
					if (circleOffset >= 0 && circleOffset < count) {
						outerMean += duplicate.getf(circleOffset);
						outerPixels++;
					}
				}
				
				innerMean /= innerPixels;
				outerMean /= outerPixels;
				innerMean -= outerMean;
				
				if (innerMean > 0)
					ip.setf(offset, (float)innerMean);
				else
					ip.setf(offset, 0);
				
				offset++;
			}
		}
		
	}

	@Override
	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		
		return flags;
	}

	@Override
	public void setNPasses(int arg0) {
	}

	@Override
	public int showDialog(ImagePlus imp, String arg, PlugInFilterRunner pfr) {
		
		GenericDialog dialog = new GenericDialog("Discoidal Averaging Filter");
		dialog.addNumericField("inner_radius", innerRadius, 0);
		dialog.addNumericField("outer_radius", outerRadius, 0);
		dialog.addPreviewCheckbox(pfr);
		dialog.addDialogListener(this);
		dialog.showDialog();
		
		if (dialog.wasCanceled())
			return DONE;
		
		return IJ.setupDialog(imp, flags);
	}

	@Override
	public boolean dialogItemChanged(GenericDialog dialog, AWTEvent arg1) {

		innerRadius = (int)dialog.getNextNumber();
		outerRadius = (int)dialog.getNextNumber();
		
		setCircleOffsets(imp.getWidth(), innerRadius, outerRadius);
		
		return innerRadius >= 0 && innerRadius < outerRadius;
	}

}
