/*
 */

package com.inc.util;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

/** 
 * Context Menu class
 *
 * */
class ContextMenu extends MouseAdapter implements ActionListener {
	private JPopupMenu menu;
	private FileUploaderApplet container;

	public void mousePressed(MouseEvent e) { 
		if (!container.isEmailing())
			maybeShowPopup(e); 
	}

	public void mouseReleased(MouseEvent e) { 
		if (!container.isEmailing())
			maybeShowPopup(e); 
	}

	ContextMenu(FileUploaderApplet container) {
		this.container = container;
	}

	private void maybeShowPopup(MouseEvent event) {
		
		if (event.isPopupTrigger()) {
			boolean isSelected = (container.getTable().getSelectedRows().length > 0);
			boolean isEmpty = (container.getTable().getModel().getRowCount() == 0);

			menu = new JPopupMenu();
			JMenuItem menuItem;

			menuItem = new JMenuItem(FileUploaderApplet.i18n("paste menuitem"));
			menuItem.addActionListener(this);
			menu.add(menuItem);

			if (isSelected) {
				menuItem = new JMenuItem(FileUploaderApplet.i18n("delete menuitem"));
				menuItem.addActionListener(this);
				menu.add(menuItem);
			}

			if (!isEmpty) {
				menu.add(new JPopupMenu.Separator());
				menuItem = new JMenuItem(FileUploaderApplet.i18n("select all menuitem"));
				menuItem.addActionListener(this);
				menu.add(menuItem);
			}

			menu.show(event.getComponent(), event.getX(), event.getY());
		}
	}
	
	/**
	 * Context Menu request handler
	 *
	 */
	public void actionPerformed(ActionEvent event) { 

		if (event.getActionCommand() == FileUploaderApplet.i18n("paste menuitem")) {
			FileWorker worker = new FileWorker(container, (List<File>)(new ClipboardFile().getClipboardContents()));
			worker.execute();
		} else if (event.getActionCommand() == FileUploaderApplet.i18n("delete menuitem")) {
			//NOTE: deleting while processing must be blocked by client javascript (check applet.isProcessing())
			if (container.isProcessing()) {
				container.errorPopup(FileUploaderApplet.i18n("no changes during processing"));
			} else {
				while (container.getTable().getSelectedRows().length > 0) {
					int rows[] = container.getTable().getSelectedRows();
					File file = (File)container.getTable().getModel().getValueAt(rows[0], 0);
					try {
						//find temporary archive files
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
		} else if (event.getActionCommand() == FileUploaderApplet.i18n("select all menuitem")) {
			container.getTable().selectAll();
		}
	}
}
