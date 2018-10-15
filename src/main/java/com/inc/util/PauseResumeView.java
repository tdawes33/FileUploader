/*
 */

package com.inc.util;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.net.URLDecoder;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

/** 
 * 
 * @version 
 */
public class PauseResumeView extends JDialog implements ActionListener
{
	private FileUploaderApplet container;
	private JTable table;
	private PausedEmailModel model;

	/** 
	 * 
	 */
	public PauseResumeView() {
		super((Frame) null, false);
	}

	/** 
	 * createRootPane is required for JDialog to respond to keys
	 * 
	 * @return 
	 */
	@Override
	protected JRootPane createRootPane() {
		JRootPane rootPane = new JRootPane();
		rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "ESCAPE");
		rootPane.getActionMap().put("ESCAPE", new AbstractAction() { 
			public void actionPerformed(ActionEvent e) { setVisible(false); } 
		});

		rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("DELETE"), "DELETE");
		rootPane.getActionMap().put("DELETE", new AbstractAction() { 
			public void actionPerformed(ActionEvent e) { 
				int row;
				if ((row = table.getSelectedRow()) != -1)
					deletePaused(row);
			}
		});

		return rootPane;
	}

	/** 
	 * PauseResumeView create the swing components to display a scrollable table 
	 * of emails which were cancelled/paused 
	 *
	 * @param container 
	 */
	PauseResumeView(FileUploaderApplet container) {
		super((Frame)SwingUtilities.getAncestorOfClass(Frame.class, container), 
				"MegaMail " +  FileUploaderApplet.i18n("emailviewer title"), false);
		this.container = container;
		
		setPreferredSize(new Dimension(700, 300));
		setSize(new Dimension(700, 300));
		setMinimumSize(new Dimension(400, 100));

		setLocationRelativeTo(container);
		setLayout(new BorderLayout());

		PausedEmailModel model = new PausedEmailModel();
		table = new JTable(model);
		table.getColumnModel().getColumn(0).setPreferredWidth(180);
		table.getColumnModel().getColumn(1).setPreferredWidth(180);
		table.getColumnModel().getColumn(2).setPreferredWidth(100);
		table.setRowHeight(18);
		table.setRowSelectionAllowed(true);
		table.setDragEnabled(false);	
		table.getTableHeader().setReorderingAllowed(false);
		table.setBackground(Color.white);
		table.setIntercellSpacing(new Dimension(1, 2));
		table.setShowHorizontalLines(true);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.addMouseListener( new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				int row;
				if (e.getClickCount() == 2 && (row = table.rowAtPoint(e.getPoint())) != -1)
					resume(row);
			}
		});

		PRCellRenderer renderer = new PRCellRenderer();
		try {
			table.setDefaultRenderer(Class.forName("java.lang.Object"), renderer);
		} catch( ClassNotFoundException ex ) { ; }

		JLabel info = new JLabel(FileUploaderApplet.i18n("emailviewer info"));
		info.setPreferredSize(new Dimension(0, 30));
		add(info, BorderLayout.NORTH);

		JScrollPane pane = new JScrollPane(table);
		pane.getViewport().setBackground(Color.white);
		pane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		add(pane, BorderLayout.CENTER);

		JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		add(panel, BorderLayout.PAGE_END);

		JButton resend = new JButton(FileUploaderApplet.i18n("emailviewer resend button"));
		resend.addActionListener(this);
		JButton delete = new JButton(FileUploaderApplet.i18n("emailviewer delete button"));
		delete.addActionListener(this);
		JButton close = new JButton(FileUploaderApplet.i18n("emailviewer close button"));
		close.addActionListener(this);

		panel.add(resend);
		panel.add(delete);
		panel.add(close);

	}

	protected void updateData(JSONArray pausedEmails) {

		int count = table.getRowCount();
		for (int i = 0; i < count; i++)
			((PausedEmailModel)table.getModel()).removeRow(0);

		for (Object emailObj : pausedEmails) {
			JSONObject paused = (JSONObject)emailObj;
			((PausedEmailModel)table.getModel()).addRow(new Object [] { paused.get("to").toString(),
												paused.get("subject").toString(), paused.get("emailId").toString() });
		}

	}

	private void resume(int row) {
		int i = 0, resumeRow = -1;
		long resumePosition = -1;
		boolean missing = false;

		//message + array of files+sizes, walk the list checking file exists+sizes (order of upload is maintained)
		HTTPComm comm = new HTTPComm(container);
		QueryString query = new QueryString("cmd", "email_resume");
		query.add("emailId", (String)table.getModel().getValueAt(row, 2));
		String response = comm.postData(query.toString());//use response to load up email

        //response is json
		if (response.length() == 0 || HTTPComm.FAILURE_VALUE.equals(response)) {
			FileUploaderApplet.logWarning("a server error has occured");
			return;
		}
		
		FileUploaderApplet.log(response);
		JSONObject json = null;
		try {
			json = (JSONObject)JSONValue.parse(response);
		} catch (Error e) {
			FileUploaderApplet.log(response);
			FileUploaderApplet.log("resuming error: " + e.toString());
		}

		JSONArray list = null;
		if ((json != null && json.toString().length() == 0) || (list  = (JSONArray)json.get("list")) == null) {
			FileUploaderApplet.logWarning("a server error has occured");
			return;
		}

		//NOTE: this process is dependent on the list of potentially incomplete uploads is IN ORDER
		for (Object fileInfo: list) {
			//add to list in order by uploaded, (existence check here) size mismatch (current), size 0

			String urlEncodedFilePathName = (String)((JSONObject)fileInfo).get("filepath");
			String filePathName = new String();
			try  {
				filePathName = URLDecoder.decode(urlEncodedFilePathName, "UTF-8");
			} catch (Exception e) {
				FileUploaderApplet.log(e);//ignore and hope for the best
			}

			File file = new File(filePathName);
			long uploaded = (Long)((JSONObject)fileInfo).get("filesize");
			if (file.exists() && file.isFile()) {
				if (resumePosition == -1) {
					//if all files are finished, set to the last
					if (file.length() > uploaded || (i+1) == list.size()) {
						resumeRow = i;
						resumePosition = (uploaded > 0) ? uploaded : 0;
					}
				}
			} else {
				missing = true;
				//TODO more informative message: list of all files/perc (x'es for missing)
				//Build up list for display
				if (JOptionPane.showConfirmDialog(this, FileUploaderApplet.i18n("missing attachments for resume"), 
						"MegaMail " + FileUploaderApplet.i18n("confirm title"), JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION)
					return;

				//else continue with NEW email (recipients/subject/message/list of available files only
				resumeRow = -1;
				resumePosition = 0;
			}
			i++;
		}

		//check for overwrite of current email
		if (container.getTable().getRowCount() > 0 || ((String)container.getRecipients()).length() > 0 ||
				((String)container.getSubject()).length() > 0 || 
					((String)container.getMessage()).length() > 0) {

			if (JOptionPane.showConfirmDialog(this, FileUploaderApplet.i18n("overwrite unsent email"), 
						"MegaMail " + FileUploaderApplet.i18n("confirm title"), 
													JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION)
				return;
		}

		container.setRecipients((String)table.getModel().getValueAt(row, 0));
		container.setSubject((String)table.getModel().getValueAt(row, 1));
		container.clearFileList();

		//Now load up the filelist
		for (Object fileInfo: list) {
			String urlEncodedFilePathName = (String)((JSONObject)fileInfo).get("filepath");
			String filePathName = new String();
			try {
				filePathName = URLDecoder.decode(urlEncodedFilePathName, "UTF-8");
			} catch (Exception e) {
				FileUploaderApplet.log(e);//ignore and hope for the best
			}

			File file = new File(filePathName);
			long uploaded = (Long)((JSONObject)fileInfo).get("filesize");
			if (file.exists() && file.isFile()) {
				((FileArchiveModel)container.getTable().getModel()).addRow(file);
			}
		}

		container.sendStatsUpdate(((FileArchiveModel)container.getTable().getModel()).totalSize());
		try {
			container.setMessage(URLDecoder.decode((String)json.get("message"), "UTF-8"));
		} catch (Exception e) {
			FileUploaderApplet.log(e);
		}

		//start resume immediately
		setVisible(false);
		if (!missing)
			container.setResumeEmail(resumeRow, resumePosition);	//only resume if all files exist

	}

	/** 
	 * 
	 * 
	 * @param event 
	 */
	public void actionPerformed(ActionEvent event) {
		int row;
		if (event.getActionCommand() == FileUploaderApplet.i18n("emailviewer close button")) {
			setVisible(false);
		} else if ((row = table.getSelectedRow()) != -1) {
			if (event.getActionCommand() == FileUploaderApplet.i18n("emailviewer resend button"))
				resume(row);
			else if (event.getActionCommand() == FileUploaderApplet.i18n("emailviewer delete button"))
				deletePaused(row);
		}
	}

	/** 
	 * 
	 * 
	 * @param row 
	 */
	private void deletePaused(int row) {

		if (JOptionPane.showConfirmDialog(this, FileUploaderApplet.i18n("confirm delete email"), 
							"MegaMail " + FileUploaderApplet.i18n("confirm title"), JOptionPane.OK_CANCEL_OPTION) 
				!= JOptionPane.OK_OPTION)
					return;

		HTTPComm comm = new HTTPComm(container);
		QueryString query = new QueryString("cmd", "delete_paused");

		query.add("emailId", (String)table.getModel().getValueAt(row, 2));
		if (!HTTPComm.SUCCESS_VALUE.equals(comm.postData(query.toString()))) {
			FileUploaderApplet.logWarning("error deleting email");
		} else {
			((DefaultTableModel)table.getModel()).removeRow(row);	
			if (table.getRowCount() > 0) {
				row -= (table.getRowCount() == row) ? 1 : 0;	//if last decrement
				table.getSelectionModel().setSelectionInterval(row, row);
			}

			//TODO delete paused in temp directory
			/*
			File file = (File)((Vector)e.nextElement()).elementAt(0);
			try {
				if (file.getParentFile().getCanonicalPath().equals(new File(System.getProperty("java.io.tmpdir")).getCanonicalPath()))
			} catch (Exception e) {
				;
			}
			*/
		}

	}


	/*
	 * TODO get style from params (or browser), use localtime for date
	 */
	class PRCellRenderer extends DefaultTableCellRenderer
	{
        private Color whiteColor = new Color(254, 254, 254);
        private Color blackColor = new Color(0, 0, 0);
        private Color alternateColor = Color.decode("#ffefdf");
        private Color selectedColor = Color.decode("#2b1209");
        private Color selectedForeground = Color.decode("#ffffff");

    	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, 
			boolean hasFocus, int row, int column) {
			int hAlign = SwingConstants.LEFT;
			super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

			if (isSelected) {
				setBackground(selectedColor);
                setForeground(selectedForeground);
            } else {
				setBackground((row % 2 == 0 ? alternateColor : whiteColor)); //alternating may cause flicker in mac
                setForeground(blackColor);
            }

			if (row > -1) {
				if (column == 0) {
					setIcon(container.getEmailIcon());
				} else if (column == 1)  {
					setIcon(null);
				} else if (column == 2) {
					String date = (String)value;
					StringBuilder formatted = new StringBuilder();
					formatted.append(date.substring(0, 4)).append("/").append(date.substring(4,6)).append("/").append(date.substring(6,8));
					formatted.append(" ").append(date.substring(8,10)).append(":").append(date.substring(10,12));
					formatted.append(":").append(date.substring(12,14));
					setText(formatted.toString());
					setIcon(null);
					hAlign = SwingConstants.RIGHT;
				}
				setHorizontalAlignment(hAlign);
			}
			return this;
		}
	} 
}
