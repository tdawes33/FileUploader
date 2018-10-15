/*
 */

package com.inc.util;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.Enumeration;
import java.util.Vector;

import javax.swing.table.DefaultTableModel;

/**
 * FileArchiveModel - a table model for displaying 3 columns of information about a file to upload
 * 						
 *
 */
public class FileArchiveModel extends DefaultTableModel
{
	public static final String ZIP_EXTENSION = ".zip";
	public static final long ONE_KB = 1024;
    public static final long ONE_MB = ONE_KB * ONE_KB;
    public static final long ONE_GB = ONE_KB * ONE_MB;

	/**
	 * originFile is not null when a file archive was created, or a file was compressed
	 */
	public void addRow(File file) { //, File originFile) {
		try {
			super.addRow(new Object[] { file, humanReadable(file.length()), file.getParentFile().getCanonicalPath() } );
		} catch (Exception e) {
			FileUploaderApplet.logWarning(e.toString());
		}
	}

	/** 
	 * add to the model, and create temporary zip file for processing later
	 * 
	 * @param file 
	 * @return true if able to create file
	 */
	public boolean addRowCompress(File file) {
		String ofilename = System.getProperty("java.io.tmpdir") + File.separator + file.getName() + ".zip";
		File f = new File(ofilename);
		try {
			f.createNewFile();
			addRow(f);
		} catch (IOException e) {
			FileUploaderApplet.logWarning(e.toString());
			return false;
		}
		return true;
	}

	/** 
	 * add to the model, and create temporary archive file for processing later
	 * 
	 * @param fileDir 
	 * @return true if able to create file
	 */
	public boolean addArchiveRow(File fileDir) {
		String ofilename = System.getProperty("java.io.tmpdir") + File.separator + fileDir.getName() + ".zip";
		File f = new File(ofilename);
		try {
			f.createNewFile();
			addRow(f);
		} catch (IOException e) {
			FileUploaderApplet.logWarning(e.toString());
			return false;
		}
		return true;
	}

	/**
	 * walk the directory adding files to our list
	 */
	public void addDirRows(File fileDir, boolean compress) {
		for (File file : fileDir.listFiles()) {
			if (file.isDirectory())
				addDirRows(file, compress);
			else if (compress)
				addRowCompress(file);
			else
				addRow(file);
		}
	}

	public Object getValueAt(int row, int column) {
		Vector rowVector;

		try {
			rowVector = (Vector)dataVector.elementAt(row);
		} catch (Exception e) {
			FileUploaderApplet.log("out of sync model: " + e);
			return null;
		}

		if (column == 1) {
			//File file = (File)((Vector)dataVector.elementAt(row)).elementAt(0);
			File file = (File)rowVector.elementAt(0);
			return humanReadable(file.length());
		} else {
			//return ((Vector)dataVector.elementAt(row)).elementAt(column);
			return rowVector.elementAt(column);
		}

	}

    public int getColumnCount() { 
		return 3; 
	}

	public boolean isCellEditable(int row, int col) { return false; }

    public String getColumnName(int column) {
		if (column == 0)
            return FileUploaderApplet.i18n("filename header");
		else if (column == 1)
            return FileUploaderApplet.i18n("size header");
		else if (column == 2)
            return FileUploaderApplet.i18n("path header");
		else
            return super.getColumnName(column);
    }


    public Class<?> getColumnClass(int column) {
		return String.class;
    }

	protected String totalSize() {
		return humanReadable(totalSizeBytes());
	}

	protected long totalSizeBytes() {
		return totalSizeBytes(0, getRowCount() - 1);
	}

	/** 
	 * 
	 * 
	 * @param startRow 
	 * @param endRow inclusive
	 * @return 
	 */
	protected long totalSizeBytes(int startRow, int endRow) {
		long totalSize = 0;

		if (getRowCount() > 0) {
			if (endRow >= getRowCount())
				endRow = getRowCount() - 1;

			for (int i = startRow; i <= endRow; i++)
				totalSize += ((File)((Vector)dataVector.elementAt(i)).elementAt(0)).length();
		}
			
		return totalSize;
	}

	protected void removeFile(String file) {
		removeFile(new File(file));
	}

	protected void removeFile(File file) {
		int i = 0;

		for (Enumeration e = dataVector.elements(); e.hasMoreElements(); i++) {
			File fileTest = (File)((Vector)e.nextElement()).elementAt(0);
			if (fileTest.getPath().equals(file.getPath())) {
				FileUploaderApplet.log("removing " + file.getPath());
				removeRow(i);
				break;
			}
		}
	}

	/** 
	 * delete temp files, and remove from list 
	 */
	protected void removeAll() {

		for (Enumeration e = dataVector.elements(); e.hasMoreElements(); ) {
			File file = (File)((Vector)e.nextElement()).elementAt(0);
			try {
                //delete temp files
				if (file.getParentFile().getCanonicalPath().equals(new File(System.getProperty("java.io.tmpdir")).getCanonicalPath()))
					file.delete(); 
			} catch (IOException ex) {
				FileUploaderApplet.log("cleanup of model: " + ex.toString());
			}
		}

		setRowCount(0);
	}


	/** 
	 * based on server routine 
	 * 
	 * @param number 
	 * @return 
	 */
	static public long numberToKilo(long number) {
		return ((number % ONE_KB) == 0) ? number/ONE_KB : (number/ONE_KB) + 1;
	}

	/** 
	 * 
	 * @param size 
	 * @return 
	 */
	static public String humanReadable(long size) {
		String readable = String.valueOf(0);
		NumberFormat formatter = NumberFormat.getNumberInstance();
		formatter.setMaximumFractionDigits(2);

		if (size > 0) {
			if (size/ONE_GB > 0)
				readable = formatter.format(size/(float)ONE_GB) + " GB";
			else if (size/ONE_MB > 0)
				readable = formatter.format(size/(float)ONE_MB) + " MB";
			else if (size/ONE_KB > 0)
				readable = formatter.format(size/(float)ONE_KB) + " KB";
			else
				readable = String.valueOf(size) + " bytes";
		}

		return readable;
	}


}
