/*
 * TODO get style from markup params
 */

package com.inc.util;

import java.awt.Color;
import java.awt.Component;
import java.io.File;

import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.filechooser.FileSystemView;
import javax.swing.table.DefaultTableCellRenderer;

/** 
 * Per cell display for text, style, icon  
 * 
 * @version 
 */
public class CellRenderer extends DefaultTableCellRenderer 
{
	private Color whiteColor = new Color(254, 254, 254);
	private Color blackColor = new Color(0, 0, 0);
	private Color alternateColor = Color.decode("#ffefdf");
	private Color selectedColor = Color.decode("#2b1209");
	private Color selectedForeground = Color.decode("#ffffff");

	/**
	 * Customization of cell rendering
	 *
	 * @param table display object
	 * @param value value as returned by data model
	 * @param isSelected selected status
	 * @param hasFocus focus status
	 * @param row cell row 
	 * @param column cell column
	 */
    public Component getTableCellRendererComponent(JTable table, Object value, 
					boolean isSelected, boolean hasFocus, int row, int column) {
		int hAlign = SwingConstants.LEFT;
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

		if (value == null) {
			setIcon(null);
			return this;
		}

		if (isSelected) {
			setBackground(selectedColor);
			setForeground(selectedForeground);
        } else {
			setBackground((row % 2 == 0 ? alternateColor : whiteColor));
			setForeground(blackColor);
        }

		//each column will display a file detail
		if (row > -1) {
			if (column == 0) {
				FileSystemView fs = FileSystemView.getFileSystemView();
				File file = (File)value;
				if (file.exists()) {
					setIcon(fs.getSystemIcon(file));
					setText(file.getName());
					hAlign = SwingConstants.LEFT;
				} else {
					setIcon(null);
					return this;
				}
			} else if (column == 1)  {
				setIcon(null);
				hAlign = SwingConstants.RIGHT;
			} else if (column == 2) {
				setIcon(null);
				hAlign = SwingConstants.LEFT;
			}
			setHorizontalAlignment(hAlign);
		}

        return this;
    }
} 
