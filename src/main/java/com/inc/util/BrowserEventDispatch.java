/*
 */

package com.inc.util;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import javax.swing.JFileChooser;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;

import org.json.simple.JSONArray;
import org.json.simple.JSONValue;

/**
 * BrowserEventDispatchThread handle function calls from the browser by polling flags
 *
 * */
class BrowserEventDispatch implements Runnable
{
	private FileUploaderApplet container;
	private JFileChooser fileDialog = null;
	private EmailWorker email;

	BrowserEventDispatch(FileUploaderApplet container) {
		this.container = container;
		(Thread.currentThread()).setPriority(FileUploaderApplet.BROWSER_DISPATCHER_PRIORITY); 
		(Thread.currentThread()).setName("Browser Polling");
	}

	/**
	 * run forever, polling container vars
	 */
	public void run() { 
		if (fileDialog == null)
			createFC();

		while (true) {
			try { (Thread.currentThread()).sleep(200); } catch (Exception e) { ; }

			if (container.getOpenFileDialog() && fileDialog != null) {
				long size;
				String displaySize;
				if (fileDialog.showOpenDialog(container) == JFileChooser.APPROVE_OPTION) {
					FileWorker worker = new FileWorker(container, Arrays.asList(fileDialog.getSelectedFiles()));
					worker.execute();
				}

				container.setOpenFileDialog(false);

			} else if (container.getRemoveSelection()) {
				if (container.isProcessing()) {
					container.errorPopup(FileUploaderApplet.i18n("no changes during processing"));
				} else {

					while (container.getTable().getSelectedRows().length > 0) {
						int rows[] = container.getTable().getSelectedRows();
						File file = (File)container.getTable().getModel().getValueAt(rows[0], 0);
						try {
							if (file.getParentFile().getCanonicalPath() == (new File(System.getProperty("java.io.tmpdir")).getCanonicalPath()))
								file.delete();
						} catch (IOException e) {
							FileUploaderApplet.log(e.toString());
						}
						((DefaultTableModel)container.getTable().getModel()).removeRow(rows[0]);	
					}

					try{
						SwingUtilities.invokeLater(new Runnable() {
							public void run() { 
								container.repaint();
								((JTable)container.getTable()).repaint(); 
							} 
						});
					} catch (Exception e) { ; }
					container.sendStatsUpdate(((FileArchiveModel)container.getTable().getModel()).totalSize());	
				}

				container.setRemoveSelection(false);

			} else if (container.getSendEmail()) {

				if (email == null || email.isDone()) {
					if (container.getTable().getRowCount() == 0) {
						container.sendNoAttachmentError();
					} else {
						email = new EmailWorker(container);
						container.setEmail(email);
						email.execute();
					}
				}

				container.setSendEmail(false);

			} else if (container.getFinishEmail()) {
				if (email != null && !email.isDone()) {
					email.cancel(true);
				} else {
					container.sendStatsUpdate("0"); 
					((FileArchiveModel)container.getTable().getModel()).removeAll(); 
				}

				container.setFinishEmail(false);

			} else if (container.getSavePreferences()) {
				container.writePreferences();
				container.setSavePreferences(false);

			} else if (container.getViewIncompleteEmail()) {
				HTTPComm comm = new HTTPComm(container, container.getServer(), container.getService()); 
				QueryString query = new QueryString("cmd", "get_paused_list");

				String response = comm.postData(query.toString());
				JSONArray pausedEmails = null;
				try {
					pausedEmails = (JSONArray)JSONValue.parse(response);
				} catch (Exception e) {
					FileUploaderApplet.log("getting paused list: " + e);
				}


				if (pausedEmails == null)
					pausedEmails = new JSONArray();

				container.displayIncompleteEmailViewer(pausedEmails);
				container.setViewIncompleteEmail(false);

			} else if (container.getUpdateCredentials()) {
				container.login();
				container.setUpdateCredentials(false); 
			}
		}
	}

	/** 
	 * 
	 * 
	 * @return true if jfilechooser is created
	 */
	private boolean createFC() {
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
			container.log(e.toString());
			return false;
		}

		fileDialog.setCurrentDirectory(new File(System.getProperty("user.home")));
		fileDialog.setApproveButtonMnemonic('A');	//TODO i18n?
		fileDialog.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
		fileDialog.setMultiSelectionEnabled(true);
		fileDialog.setFileHidingEnabled(true);

		return true;
	}


}
