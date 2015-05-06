package util;

import java.util.Arrays;
import java.util.Comparator;

import ij.IJ;
import ij.gui.GenericDialog;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.filter.Analyzer;

public class ResultsTableSorter implements PlugIn {
	
	private ResultsTable table;
	private String noGrouping = "no grouping";
	
	public ResultsTableSorter() {
		table = Analyzer.getResultsTable();
	}
	
	public ResultsTableSorter(ResultsTable table) {
		this.table = table;
	}
	
	@Override
	public void run(String arg0) {
		
		if (table == null) {
			IJ.showMessage("No results table!");
			return;
		}
		
		String[] headings = table.getHeadings();
		String[] headings2 = new String[headings.length + 1];
		
		headings2[0] = noGrouping;
		
		for (int i = 0; i < headings.length; i++)
			headings2[i + 1] = headings[i];
		
		GenericDialog dialog = new GenericDialog("Results Table Sorter");
		dialog.addChoice("column", headings, headings[0]);
		dialog.addChoice("group", headings2, headings2[0]);
		dialog.addCheckbox("ascending", true);
		dialog.showDialog();
		
		if (dialog.wasCanceled())
			return;
		
		String column = dialog.getNextChoice();
		String group = dialog.getNextChoice();
		boolean ascending = dialog.getNextBoolean();
		
		sort(table, ascending, group, column);
		
		table.show("Results");
	}
	
	public static void sort(final ResultsTable table, final boolean ascending, final String... columns) {
		
		
		Integer[] rowNumbers = new Integer[table.getCounter()];
		
		for (int i = 0; i < rowNumbers.length; i++)
			rowNumbers[i] = i;
		
		Arrays.sort(rowNumbers, new Comparator<Integer>() {

			@Override
			public int compare(Integer o1, Integer o2) {
				
				for (String column: columns) {
					
					if (table.getColumnIndex(column) != ResultsTable.COLUMN_NOT_FOUND) {
						double value1 = table.getValue(column, o1);
						double value2 = table.getValue(column, o2);
					
						int difference = Double.compare(value1, value2); 
					
						if (difference != 0)
							return ascending ? difference : -difference;
					}
					
				}
				
				return 0;
			}
			
		});
		
		
		// put all rows in the correct order (in-place)
		for (int i = 0; i < rowNumbers.length; i++) {
			int j = rowNumbers[i];
			
			if (i != j) {
				
				while (j < i)	// element at position j was already swapped with another element; find out which element that was
					j = rowNumbers[j];
				
				// swap rows
				for (int k = 0; k <= table.getLastColumn(); k++) {
					
					if (table.columnExists(k)) {
						double d = table.getValueAsDouble(k, i);
						table.setValue(k, i, table.getValueAsDouble(k, j));
						table.setValue(k, j, d);
					}
				}
				
			}
			
		}
		
		table.updateResults();
		
	}
	
	public static void main(String[] args) {
		
		
		System.out.println("creating results table...");
		
		ResultsTable rt = new ResultsTable();
		
		for (int i = 0; i < 1e6; i++) {
			
			rt.incrementCounter();
			rt.setValue("x", i, (int)(Math.random() * 1000));
			rt.setValue("y", i, (int)(Math.random() * 1000));
			
		}
		
		ResultsTableSorter.sort(rt, true, "x", "y");
		
		
		// test table
		
		System.out.println("testing results table...");
		System.out.println("number of rows : " + rt.getCounter());

		//System.out.println("x\ty");
		//System.out.println(rt.getValue("x", 0) + "\t" + rt.getValue("y", 0));
		
		for (int i = 1; i < rt.getCounter(); i++) {
			
			//System.out.println(rt.getValue("x", i) + "\t" + rt.getValue("y", i));
			
			int x1 = (int)rt.getValue("x", i - 1);
			int x2 = (int)rt.getValue("x", i);
			int y1 = (int)rt.getValue("y", i - 1);
			int y2 = (int)rt.getValue("y", i);
			
			if (x1 > x2) {
				System.out.println("not sorted");
				break;
			}
			
			if (x1 == x2 && y1 > y2) {
				System.out.println("group not sorted");
				break;
			}
			
		}
		
		System.out.println("done");
		
		
		
	}
	
	
	
	

}
