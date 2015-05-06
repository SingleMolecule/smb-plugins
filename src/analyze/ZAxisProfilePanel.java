package analyze;

import java.awt.Container;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTextField;

import process.DiscoidalAveragingFilter;
import analyze.PeakFinder;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

public class ZAxisProfilePanel implements PlugIn, ActionListener {

	JFrame frame = new JFrame("Z-Axis Profile Panel");
	JButton alignButton = new JButton("align");
	JButton findPeaksButton = new JButton("find peaks");
	JButton getZAxisProfilesButton = new JButton("get z-axis profiles");
	
	JTextField dxTextField = new JTextField("0.0", 5);
	JTextField dyTextField = new JTextField("0.0", 5);
	JTextField sdxTextField = new JTextField("0.0", 5);
	JTextField sdyTextField = new JTextField("0.0", 5);
	
	JCheckBox useDiscoidalAveragingFilterCheckBox = new JCheckBox("Use Discoidal Averaging Filter", true);
	JTextField innerRadiusTextField = new JTextField("1", 5);
	JTextField outerRadiusTextField = new JTextField("3", 5);
	JTextField thresholdTextField = new JTextField("6.0", 5);
	JTextField thresholdValueTextField = new JTextField("0", 5);
	
	JTextField maximumPeakIntesityTextField = new JTextField("NaN", 5);
	
	JTextField selectionRadiusTextField = new JTextField("3", 5);
	JTextField minimumDistanceBetweenPeaksTextField = new JTextField("5", 5);
	JTextField selectionRadius = new JTextField("4", 0);
	
	JTextField timeIntervalTextField = new JTextField("100", 5);
	
	@Override
	public void run(String arg0) {
		frame.setSize(250, 600);
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		
		alignButton.addActionListener(this);
		findPeaksButton.addActionListener(this);
		getZAxisProfilesButton.addActionListener(this);
		
		Container contentPane = frame.getContentPane();
		contentPane.setLayout(new GridLayout(0, 1));
		contentPane.add(alignButton);
		
		JPanel panel = new JPanel();
		panel.add(new JLabel("dx"));
		panel.add(dxTextField);
		panel.add(new JLabel("dy"));
		panel.add(dyTextField);
		
		contentPane.add(panel);
		
		panel = new JPanel();
		panel.add(new JLabel("SD dx"));
		panel.add(sdxTextField);
		panel.add(new JLabel("SD dy"));
		panel.add(sdyTextField);
		
		contentPane.add(panel);
		
		contentPane.add(new JSeparator());
		
		
		contentPane.add(useDiscoidalAveragingFilterCheckBox);
		contentPane.add(new JLabel("inner radius of peaks (in pixels)"));
		contentPane.add(innerRadiusTextField);
		contentPane.add(new JLabel("outer radius of peaks (in pixels)"));
		contentPane.add(outerRadiusTextField);
		
		contentPane.add(new JLabel("threshold (mean + n * std. dev.)"));
		contentPane.add(thresholdTextField);
		contentPane.add(new JLabel("fixed threshold (0 = ignore)"));
		contentPane.add(thresholdValueTextField);
		contentPane.add(new JLabel("maximum peak intensity"));
		contentPane.add(maximumPeakIntesityTextField);
		
		contentPane.add(new JLabel("selection radius (4 = 9 x 9 pixels selection box)"));
		contentPane.add(selectionRadiusTextField);
		
		contentPane.add(new JLabel("minimum distance between peaks (in pixels)"));
		contentPane.add(minimumDistanceBetweenPeaksTextField);
		
		contentPane.add(findPeaksButton);
		contentPane.add(new JSeparator());
		
		panel = new JPanel();
		panel.add(new JLabel("time interval"));
		panel.add(timeIntervalTextField);
		
		contentPane.add(panel);
		contentPane.add(getZAxisProfilesButton);
		frame.pack();
		frame.setVisible(true);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		ImagePlus imp = IJ.getImage();
		ImageProcessor ip = imp.getProcessor();
		Roi roi = imp.getRoi();
		
		if (e.getSource() == alignButton) {
			
			if (roi == null || roi.getType() != Roi.POINT) {
				IJ.showMessage("no point selections specified");
				return;
			}
			
			double[] dxdy = Colocalization.align(ip, (PointRoi)roi);
			
			dxTextField.setText(String.format("%.3f", dxdy[0]));
			dyTextField.setText(String.format("%.3f", dxdy[1]));
			sdxTextField.setText(String.format("%.3f", dxdy[2]));
			sdyTextField.setText(String.format("%.3f", dxdy[3]));
		}
		else if (e.getSource() == findPeaksButton) {
			
			if (roi == null || roi.getType() != Roi.RECTANGLE) {
				IJ.showMessage("no rectangular selection specified");
				return;
			}
			
			boolean useDiscoidalAveragingFilter = useDiscoidalAveragingFilterCheckBox.isSelected();
			int innerRadius = Integer.parseInt(innerRadiusTextField.getText());
			int outerRadius = Integer.parseInt(outerRadiusTextField.getText());
			double threshold = Double.parseDouble(thresholdTextField.getText());
			double thresholdValue = Double.parseDouble(thresholdValueTextField.getText());
			double maximumPeakIntesity = Double.parseDouble(maximumPeakIntesityTextField.getText());
			int minimumDistanceBetweenPeaks = Integer.parseInt(minimumDistanceBetweenPeaksTextField.getText());
			int selectionRadius = Integer.parseInt(selectionRadiusTextField.getText());
			
			DiscoidalAveragingFilter filter = new DiscoidalAveragingFilter(ip.getWidth(), innerRadius, outerRadius);
			PeakFinder peakFinder = new PeakFinder(useDiscoidalAveragingFilter, filter, threshold, thresholdValue, minimumDistanceBetweenPeaks, selectionRadius);
			peakFinder.run(ip);
			
			RoiManager roiManager = RoiManager.getInstance();
			
			if (roiManager == null)
				roiManager = new RoiManager();

			Roi[] rois = roiManager.getRoisAsArray();
			
			ArrayList<Roi> filteredRois = new ArrayList<Roi>();
			
			for (Roi r: rois) {
				Rectangle bounds = r.getBounds();
				
				int x = (int)bounds.getCenterX();
				int y = (int)bounds.getCenterY();
				
				if (Double.isNaN(maximumPeakIntesity) || ip.getPixel(x, y) < maximumPeakIntesity)
					filteredRois.add(r);
			}
			
			roiManager.runCommand("reset");
			
			int halfwidth = imp.getWidth() / 2;
			double dx = Double.parseDouble(dxTextField.getText());
			double dy = Double.parseDouble(dyTextField.getText());
			
			for (Roi r: filteredRois) {
				roiManager.addRoi(r);
				Rectangle bounds = r.getBounds();
				
				if (bounds.x < halfwidth) {
					Roi r2 = new Roi((int)Math.round(bounds.x + halfwidth - dx), (int)Math.round(bounds.y - dy), bounds.width, bounds.height);
					roiManager.addRoi(r2);
				}
				else {
					Roi r2 = new Roi((int)Math.round(bounds.x - halfwidth + dx), (int)Math.round(bounds.y + dy), bounds.width, bounds.height);
					roiManager.addRoi(r2);
				}
			}
			
			roiManager.runCommand("Show All");
		}
		else if (e.getSource() == getZAxisProfilesButton) {
			double timeInterval = Double.parseDouble(timeIntervalTextField.getText());
			//IJ.run("Batch Z-axis Profile", String.format("time=%f", timeInterval));
			
			ImageStack stack = imp.getStack();
			RoiManager manager = RoiManager.getInstance();
			ResultsTable table = Analyzer.getResultsTable();
			
			for (int slice = 1; slice <= stack.getSize(); slice++) {
				
				ImageProcessor ip2 = stack.getProcessor(slice);
				table.incrementCounter();
				table.addValue("slice", slice);
				table.addValue("time", (slice - 1) * timeInterval);
				
				for (Roi roi2: manager.getRoisAsArray()) {
					
					ip2.setRoi(roi2);
					ImageStatistics statistics = ip2.getStatistics();
					table.addValue(roi2.getName(), statistics.mean);
					
				}
				
			}
			
			table.show("Results");
			
		}
		
	}
	
	

}
