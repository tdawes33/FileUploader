/*
 */

package com.inc.util;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;

import javax.swing.JComponent;
import javax.swing.TransferHandler;


/**
 * handle drops to scrollpane, and table only allow files
 *
 */
public class DropHandler extends TransferHandler {
	private FileUploaderApplet container;

	/**
	 * DropHandler will process data objects dropped on to the scrollpane and
	 * the treetable
	 *
	 * @param j treetable since we may be handling drops to the scrollpane as well
	 * @param w javascript handle for web ui
	 */
	DropHandler(FileUploaderApplet container) {
		this.container = container;
	}

	/**
	 * Test if not currently emailing & dropped content is a File
	 *
	 * @param comp the drop target component
	 * @param flavors array of DataFlavor for source data
	 * @return true if the dropped content is a File (including directories)
	 */
	public boolean canImport(JComponent comp, DataFlavor[] flavors) {
		return (!container.isEmailing() && FileList.hasAnyFileFlavor(flavors));
	}

	/**
	 * After approval from canImport, 
	 *
	 * @param comp
	 * @param t
	 * @return always true
	 */
	public boolean importData(JComponent comp, Transferable transfer) {

		FileList fileList = new FileList(transfer);
		FileWorker worker = new FileWorker(container, fileList.getList());
		worker.execute();

		return true;
	 }

}
