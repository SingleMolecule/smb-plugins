package process;

import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.plugin.filter.*;

// This filter averages each frame with the n - 1 previous frames
// http://en.wikipedia.org/wiki/Moving_average

public class MovingAverageFilter implements PlugInFilter, DialogListener {
	private int flags = DOES_16 | DOES_8G | DOES_STACKS;
	
	private int imagesToAverage = 5;

	private ImageProcessor[] imageBuffer;
	private int currentImage;
	private int[] sum;

	@Override
	public void run(ImageProcessor ip) {
		int divisor = ip.getSliceNumber();
		
		if (divisor > imageBuffer.length)
			divisor = imageBuffer.length;
		
		currentImage++;
		currentImage %= imageBuffer.length;
		
		imageBuffer[currentImage].setPixels(ip.getPixelsCopy());
		ImageProcessor previousIp = imageBuffer[(currentImage + 1) % imageBuffer.length];
		
		// update sum of last n images
		int pixelCount = ip.getPixelCount();
		
		for (int i = 0; i < pixelCount; i++) {
			sum[i] += ip.get(i);
			ip.set(i, (int)(sum[i] / divisor));
			sum[i] -= previousIp.get(i);
		}
	}

	@Override
	public int setup(String arg, ImagePlus imp) {
		
		if (arg != null && arg.equals("about"))
    	{
            IJ.showMessage(
                    "About Average Filter Plugin...",
                    "This is a sliding average filter. It replaces each frame with an average of the previous n frames");
    		return DONE;
    	}
		
		GenericDialog dialog = new GenericDialog("Average filter parameters");
		dialog.addNumericField("number of frames to average", imagesToAverage, 0);
		dialog.addDialogListener(this);
        dialog.showDialog();
        
        if (dialog.wasCanceled())
        	return DONE;
        
        imageBuffer = new ImageProcessor[imagesToAverage];
        ImageProcessor ip = imp.getProcessor();
        
        for (int i = 0; i < imagesToAverage; i++)
        	imageBuffer[i] = ip.createProcessor(imp.getWidth(), imp.getHeight());
        
        sum = new int[ip.getPixelCount()];
        
		return flags;
	}

	@Override
	public boolean dialogItemChanged(GenericDialog gd, java.awt.AWTEvent e)  {
		imagesToAverage = (int)gd.getNextNumber();
		
		return imagesToAverage > 1;
	}
}

