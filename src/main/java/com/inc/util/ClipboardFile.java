/*
 */

package com.inc.util;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.util.List;

/** 
 * handle paste from clipboard 
 * 
 * @version 
 */
class ClipboardFile implements ClipboardOwner
{
	/**
	*  Empty implementation of the ClipboardOwner interface.
	**/
	public void lostOwnership( Clipboard aClipboard, Transferable aContents) { ; }

	/**
	* Get the String residing on the clipboard, see if it's a file
	*     
	* @return any text found on the Clipboard; if none found, return an empty String.
	**/
	public List<File> getClipboardContents() {
		List<File> list = null;
		Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
		Transferable contents = clipboard.getContents(null);

		if (contents != null && FileList.hasAnyFileFlavor(contents.getTransferDataFlavors())) {
			FileList fileList = new FileList(contents);
			list = fileList.getList();
		}

		return list;
	}

}
