/*
 */

package com.inc.util;

import java.util.Vector;

import javax.swing.table.DefaultTableModel;

/**
 * PausedEmailModel - a table model for displaying 3 columns of information about an email
 * 							on resend, the emailId, will be used to retrieve the filelist 
 * 							(email info already on server)
 * 						
 *
 */
public class PausedEmailModel extends DefaultTableModel
{
	public boolean isCellEditable(int row, int col) { return false; }

	public Object getValueAt(int row, int column) {
		/*
		if (column == 2) {
			return "date";
		} else {
		*/
			return ((Vector)dataVector.elementAt(row)).elementAt(column);
		//}

	}

    public int getColumnCount() { 
		return 3; 
	}

    public String getColumnName(int column) {
		if (column == 0)
            return FileUploaderApplet.i18n("emailviewer recipient header");
		else if (column == 1)
            return FileUploaderApplet.i18n("emailviewer subject header");
		else if (column == 2)
            return FileUploaderApplet.i18n("emailviewer date header");
		else
            return super.getColumnName(column);
    }

    public Class<?> getColumnClass(int column) {
		return String.class;
    }
}
