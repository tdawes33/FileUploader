/*
 */

package com.inc.util;

import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.UIManager;

class FileDialog implements Runnable
{
	private FileUploaderApplet container;
	private JFileChooser fileDialog;

	FileDialog (FileUploaderApplet container) {
		this.container = container;
	}


	public void run() {
		if (fileDialog == null)
			createFileChooser();

		if (fileDialog.showOpenDialog(container) == JFileChooser.APPROVE_OPTION) {
			FileUploaderApplet.log("got dialog");
		}

	}

	private void createFileChooser() {
		UIManager.put("FileChooser.readOnly", true);

		UIManager.put("FileChooser.openDialogTitleText", FileUploaderApplet.i18n("file dialog title"));
		UIManager.put("FileChooser.openDialogTitleText", FileUploaderApplet.i18n("file dialog title"));
		UIManager.put("FileChooser.lookInLabelText", FileUploaderApplet.i18n("file dialog location"));
		UIManager.put("FileChooser.upFolderToolTipText", FileUploaderApplet.i18n("file dialog up folder"));
		UIManager.put("FileChooser.newFolderToolTipText", FileUploaderApplet.i18n("file dialog new folder"));
		UIManager.put("FileChooser.listViewButtonToolTipText", FileUploaderApplet.i18n("file dialog view icons"));
		UIManager.put("FileChooser.detailsViewButtonToolTipText", FileUploaderApplet.i18n("file dialog view details"));
		UIManager.put("FileChooser.fileNameHeaderText", FileUploaderApplet.i18n("file dialog details name"));
		UIManager.put("FileChooser.fileSizeHeaderText", FileUploaderApplet.i18n("file dialog details size"));
		UIManager.put("FileChooser.fileTypeHeaderText", FileUploaderApplet.i18n("file dialog details type"));
		UIManager.put("FileChooser.fileDateHeaderText", FileUploaderApplet.i18n("file dialog details date"));
		UIManager.put("FileChooser.fileAttrHeaderText", FileUploaderApplet.i18n("file dialog details attributes"));
		UIManager.put("FileChooser.fileNameLabelText", FileUploaderApplet.i18n("file dialog filename label"));
		UIManager.put("FileChooser.filesOfTypeLabelText", FileUploaderApplet.i18n("file dialog type of label"));
		UIManager.put("FileChooser.openButtonText", FileUploaderApplet.i18n("file dialog open"));
		UIManager.put("FileChooser.openButtonToolTipText", FileUploaderApplet.i18n("file open tip"));
		UIManager.put("FileChooser.cancelButtonText", FileUploaderApplet.i18n("file dialog cancel"));
		UIManager.put("FileChooser.cancelButtonToolTipText", FileUploaderApplet.i18n("file cancel tip"));
		UIManager.put("FileChooser.acceptAllFileFilterText", FileUploaderApplet.i18n("file dialog accept all"));

		try { 
			fileDialog = new JFileChooser(); 
		} catch (Exception e) { 
			FileUploaderApplet.log(e.toString());
		}

		fileDialog.setCurrentDirectory(new File(System.getProperty("user.home")));
		fileDialog.setApproveButtonMnemonic('A');	//TODO i18n
		fileDialog.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
		fileDialog.setMultiSelectionEnabled(true);
		fileDialog.setFileHidingEnabled(true);
	}


}

