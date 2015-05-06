package process;

import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;

public class CorrectBackground implements PlugInFilter {

	private int flags = DOES_32 | DOES_STACKS | PARALLELIZE_STACKS;
	private double electronicOffset = 1400;
	private double maximumPixelValue;
	private ImageProcessor backgroundIp;
	
	@Override
	public void run(ImageProcessor ip) {
		
		// subtract electronic offset and
		// devide by background
		for (int y = 0; y < ip.getHeight(); y++) {
			for (int x = 0; x < ip.getWidth(); x++) {
				
				double backgroundValue = (backgroundIp.getf(x, y) - electronicOffset) / (maximumPixelValue - electronicOffset);
				double value = ip.getf(x, y) - electronicOffset;
				
				ip.setf(x, y, (float)Math.abs(value / backgroundValue));
			}
		}
		
	}

	@Override
	public int setup(String arg0, ImagePlus imp) {
		
		int numberOfImages = WindowManager.getImageCount();
		String[] images = new String[numberOfImages];
		
		for (int i = 0; i < numberOfImages; i++)
			images[i] = WindowManager.getImage(i + 1).getTitle();
		
		GenericDialog dialog = new GenericDialog("Correct Background");
		dialog.addNumericField("Electronic_offset", electronicOffset, 2);
		dialog.addMessage("Correct " + imp.getTitle() + " with");
		dialog.addChoice("Background_image", images, images[0]);
		dialog.showDialog();
		
		
		if (dialog.wasCanceled())
			return DONE;
		
		electronicOffset = dialog.getNextNumber();
		ImagePlus backgroundImp = WindowManager.getImage(dialog.getNextChoice());
		backgroundIp = backgroundImp.getProcessor();
		
		
		// determine maximum pixel value
		maximumPixelValue = backgroundIp.getf(0, 0);
		
		for (int y = 0; y < backgroundIp.getHeight(); y++) {
			for (int x = 0; x < backgroundIp.getWidth(); x++) {
				
				double value = backgroundIp.getf(x, y);
				
				if (value > maximumPixelValue)
					maximumPixelValue = value;
				
			}
		}
		
		return flags;
	}

}
