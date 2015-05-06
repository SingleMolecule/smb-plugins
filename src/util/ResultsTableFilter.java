package util;

import java.awt.AWTEvent;
import java.awt.TextField;
import java.util.Vector;

import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.filter.Analyzer;

public class ResultsTableFilter implements PlugIn, DialogListener {
	
	private ResultsTable table;
	
	private String[] columns;
	private String column;
	private int columnIndex = 0;
	private double min = 0;
	private double max = 1;
	
	public ResultsTableFilter() {
		table = Analyzer.getResultsTable();
	}
	
	public ResultsTableFilter(ResultsTable table) {
		this.table = table;
	}
	
	@Override
	public void run(String arg0) {
		
		if (table == null)
			return;
		
		columns = table.getColumnHeadings().split(",|\\t+");
		columnIndex =  table.getColumnIndex(columns[0]);
		
		GenericDialog dialog = new GenericDialog("results filter");
		dialog.addChoice("column", columns, columns[0]);
		dialog.addNumericField("min", min, 2);
		dialog.addNumericField("max", max, 2);
		dialog.addDialogListener(this);
		dialog.showDialog();
		
		if (dialog.wasCanceled() || columnIndex == ResultsTable.COLUMN_NOT_FOUND)
			return;
		
		
		min = dialog.getNextNumber();
		max = dialog.getNextNumber();
		
		for (int i = table.getCounter() - 1; i >= 0; i--) {
			double value = table.getValue(column, i);
			
			if (value < min || value > max || Double.isNaN(value))
				table.deleteRow(i);
		}
		
		table.updateResults();
	}

	@Override
	public boolean dialogItemChanged(GenericDialog dialog, AWTEvent arg1) {
		
		column = dialog.getNextChoice();
		int newColumnIndex = table.getColumnIndex(column);
		min = dialog.getNextNumber();
		max = dialog.getNextNumber();
		
		if (newColumnIndex != ResultsTable.COLUMN_NOT_FOUND && newColumnIndex != columnIndex) {
			
			double[] values = table.getColumnAsDoubles(newColumnIndex);
			min = max = values[0];
			
			for (double value: values) {
				if (value < min)
					min = value;
				else if (value > max)
					max = value;
			}
			
			@SuppressWarnings("unchecked")
			Vector<TextField> stringFields = (Vector<TextField>)dialog.getNumericFields();
			stringFields.get(0).setText(Double.toString(min));
			stringFields.get(1).setText(Double.toString(max));
			
			columnIndex = newColumnIndex;
		}
		
		return min <= max;
	}

}
