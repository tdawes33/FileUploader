/*
 */

package com.inc.util;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
    

/* FileList
 *
 * policy: ignore empty directories, empty files
 *
 *
 */
public class FileList
{
	private static final String URI_LIST_MIME_TYPE = "text/uri-list;class=java.lang.String";
	private static DataFlavor uriListFlavor = getURIListFlavor();
	private Transferable transfer;

	FileList(Transferable transfer) {
		this.transfer = transfer;
	}

	private static DataFlavor getURIListFlavor() {
		DataFlavor flavor = null;
		try {
			flavor = new DataFlavor(URI_LIST_MIME_TYPE);
		} catch (Exception e) {
			FileUploaderApplet.log("Unable to create URI list");
			FileUploaderApplet.log(e.toString());
		}
		return flavor;
	}

	/**
	 * determine if at least one dataflavor is  represented in the 
	 *
	 * @param flavors array of current data flavors
	 * @return true if the flavor is in the array of flavors provided
	*/
	static protected boolean hasAnyFileFlavor(DataFlavor[] flavors) {
		for (DataFlavor flavor : flavors) {
			if (DataFlavor.javaFileListFlavor.equals(flavor) || uriListFlavor.equals(flavor))
				return true;
		}
    	return false;
	}

	static private boolean hasFileFlavor(DataFlavor[] flavors) {
		for (DataFlavor flavor : flavors) {
			if (DataFlavor.javaFileListFlavor.equals(flavor))
				return true;
		}
		return false;
	}

	static private boolean hasUnixFileFlavor(DataFlavor[] flavors) {
		for (DataFlavor flavor : flavors) {
			if (uriListFlavor.equals(flavor))
				return true;
		}
		return false;
	}

	protected List<File> getList() {
		List<File> list = null;

		if (hasFileFlavor(transfer.getTransferDataFlavors())) {
			try {
				list = (List)transfer.getTransferData(DataFlavor.javaFileListFlavor);
			} catch (Exception e) { 
				FileUploaderApplet.log(e.toString());
				return list; 
			}
		} else if (hasUnixFileFlavor(transfer.getTransferDataFlavors())) {
			try {
				list = textURIListToFileList((String)transfer.getTransferData(uriListFlavor));
			} catch (Exception e) { 
				FileUploaderApplet.log(e.toString());
				return list; 
			}
		}

		return list;
	}

	/**
	 * convert the EOF delimited URI string to a list of File for unix generally
	 *
	 * @param data EOF delimited URI string
	 * @return list of File from data
	 */
	private List textURIListToFileList(String data) {
		List<File> list = new ArrayList(1);

		for (StringTokenizer st = new StringTokenizer(data, "\r\n"); st.hasMoreTokens();) {
			String s = st.nextToken();
			if (s.startsWith("#"))
				continue;
			try {
				list.add(new File(new URI(s)));
			} catch (URISyntaxException e) { 
				FileUploaderApplet.log(e.toString());
			}
		}
		return list;
	}
}
