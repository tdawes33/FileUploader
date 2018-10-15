/*
 */

package com.inc.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.apache.tools.zip.ZipEntry;
import org.apache.tools.zip.ZipOutputStream;
import org.jdesktop.swingworker.SwingWorker;

/* FileWorker process(threaded) list of files from dnd, file dialog, context menu, and shortcut
 * policy: 
 * 			limited to FileUploaderApplet.FILECOUNT_MAX
 * 			ignores empty top-level directories
 * effected by archive and compress settings
 *
 */
public class FileWorker extends SwingWorker<Boolean, Void> {
	private FileUploaderApplet container;
	private FileArchiveModel model;
	private List<File> fileList;
	private Boolean archiving, compressing;

	FileWorker(FileUploaderApplet container, List<File> fileList) { 
		this.container = container;
		this.model = (FileArchiveModel)container.getTable().getModel();
		this.fileList = fileList;
		archiving = FileUploaderApplet.canArchive();	//in case user changes settings during processing
		compressing = FileUploaderApplet.canCompress();

		(Thread.currentThread()).setPriority(FileUploaderApplet.UPLOAD_PRIORITY); 
		(Thread.currentThread()).setName(this.getClass().toString());
		container.addFileWorker(this);
	}

	protected void process() { FileUploaderApplet.log("file worker process method"); }

	protected boolean contains(File file) { return fileList.contains(file); }


	/** 
	 * process the provided file directory creating list of non-empty files
	 *
	 * @param dir root directory 
	 * @return list of non-empty files
	 */
	private List<File> flattenList(File dir) {
		ArrayList<File> fileList = new ArrayList<File>();

		for (File file : dir.listFiles()) {
			if (file.isDirectory())
				fileList.addAll(flattenList(file));
			else if (file.length() > 0)
				fileList.addAll(Arrays.asList(file));
			else
				FileUploaderApplet.logWarning(file.getPath() + FileUploaderApplet.i18n("empty removal"));
		}

		return fileList;
	}

	/** 
	 * process our filelist - removing empty top level files, and finding duplicates
	 * 
	 * @return true on success
	 */
	public Boolean doInBackground() {

		//ensure no updates while emailing
		if (container.isEmailing())
			return false;

		if (!archiving) { //flatten the list, noting empty files, and removing them
			ArrayList<File> tempList = new ArrayList<File>();

			for (File file : fileList) {
				if (file.isDirectory())
					tempList.addAll(flattenList(file));
				else if (file.length() > 0)
					tempList.addAll(Arrays.asList(file));
				else
					FileUploaderApplet.logWarning(file.getPath() + FileUploaderApplet.i18n("empty removal"));
			}
            fileList = tempList;	//use the new list
		} else {
			//remove empty files/dirs from top level (NOTE: a directory may consist only of an empty directory)
			ArrayList<File> tempList = new ArrayList<File>();

			for (File file : fileList) {
				if ((file.isDirectory() && file.list().length == 0) || (!file.isDirectory() && file.length() == 0))
					FileUploaderApplet.logWarning(file.getPath() + FileUploaderApplet.i18n("empty removal"));
				else
					tempList.addAll(Arrays.asList(file));
			}
            fileList = tempList;	//use the new list
		}

		//check for duplicate filenames in new list & already loaded list (continue adding after removal though)
		ArrayList<File> duplicateList = getDuplicateList();

		if (duplicateList.size() > 0) {
			for (File dup : duplicateList)
				FileUploaderApplet.log("dup " + dup.getName());

			//build up display
			StringBuilder displayList = new StringBuilder("\n\n");
			int i = 0;
			for (File file : duplicateList) {
				try {
					displayList.append(file.getName() + ": " + file.getParentFile().getCanonicalPath() + "\n");
				} catch (Exception e) {
					FileUploaderApplet.log(e);
				}

				if (i++ > FileUploaderApplet.LIST_DISPLAY_LIMIT) {
					displayList.append("...");
					break;
				}

			}
			JOptionPane.showMessageDialog(container, FileUploaderApplet.i18n("duplicate attachment name") + displayList,
					 "MegaMail", JOptionPane.ERROR_MESSAGE);


			//now remove duplicates from fileList for processing
			for (File file : duplicateList) {
				FileUploaderApplet.log("filelist removing " + file.getPath());
				fileList.remove(file);
			}

		 }

		//limit attachment count (fill up though)
		int fileCount = container.getTable().getRowCount();
		int newFileCount = fileList.size();	//filelist is flattend already
		if (fileCount + newFileCount > FileUploaderApplet.FILECOUNT_MAX) {
			 String amount = String.valueOf((fileCount + newFileCount) - FileUploaderApplet.FILECOUNT_MAX);
			 JOptionPane.showMessageDialog(container, FileUploaderApplet.i18n("over attachment limit by") + amount,
					 "MegaMail", JOptionPane.ERROR_MESSAGE);

			 //remove excess 
			 fileList = fileList.subList(0, FileUploaderApplet.FILECOUNT_MAX - fileCount);
		}

		//Add to model for display before processing (archive/compress),
		for (File file : fileList) {
            boolean zipped = !file.isDirectory() && file.getName().substring(file.getName().length() - 
                    FileArchiveModel.ZIP_EXTENSION.length()).equals(FileArchiveModel.ZIP_EXTENSION);

			if (file.isDirectory()) {
                if (!archiving)
				    model.addDirRows(file, compressing); //walk directory adding files
                else
				    model.addArchiveRow(file);
            } else if (compressing && !zipped) {
				model.addRowCompress(file);
            } else {
				model.addRow(file);
            }

		}
		SwingUtilities.invokeLater(new Runnable() { public void run() { container.getTable().repaint(); } });
		container.sendStatsUpdate(model.totalSize());

		//Process the file list for archiving &/or compression
		//NOTE: if compressing, empty temp files have been created, and must be written here
		for (File file : fileList) {
            boolean zipped = !file.isDirectory() && file.getName().substring(file.getName().length() - 
                    FileArchiveModel.ZIP_EXTENSION.length()).equals(FileArchiveModel.ZIP_EXTENSION);

			if (isCancelled())
				break;

			if (container.getTable().getRowCount() > FileUploaderApplet.FILECOUNT_MAX)
				break;

			if (file.isDirectory() && archiving) {
				String ofilename = System.getProperty("java.io.tmpdir") + File.separator + file.getName() + ".zip";	// a temp file
				ZipOutputStream zout;

				try {
					FileOutputStream fout = new FileOutputStream(ofilename);
					zout = new ZipOutputStream(fout);
                    zout.setEncoding("UTF8");
					if (compressing)
						zout.setLevel(7);
					else
						zout.setLevel(0);

					if (!zip(zout, file, "")) {	// a temp zip file created
						if (!isCancelled())
							model.removeFile(ofilename);
					}

					zout.close();
				} catch (Exception e) {
					FileUploaderApplet.logWarning(e.toString());
				}
			} else if (file.isDirectory() && compressing) {  
				if (!zipFiles(file) && isCancelled())
					break;
            } else if (compressing && !zipped) {
				if (!zipFile(file) && isCancelled())
					break;
			} else {
				;//top level file with no compression requires no processing (or temp file)
				;//top level dir with no compression & no archiving requires no processing (or temp file)
			}
		}

		if (isCancelled())
			cleanUp();

		return true;  
	}

	private boolean zipFile(File file) {
		String ofilename = System.getProperty("java.io.tmpdir") + File.separator + file.getName() + ".zip";
		ZipOutputStream zout;

		try {
			FileOutputStream fout = new FileOutputStream(ofilename);
			zout = new ZipOutputStream(fout);
            zout.setEncoding("UTF8");
			zout.setLevel(7);

			ZipEntry ze = new ZipEntry(file.getName());
			FileInputStream fin = new FileInputStream(file.getPath());
            int bufferSize = (int)Math.min((long)FileUploaderApplet.ZIP_BUF_SIZE, file.length());
			byte buffer[] = new byte[bufferSize];
			try { 
				zout.putNextEntry(ze);
				int c = 0;
				long update = 0;
				while ((c = fin.read(buffer)) > 0) {
					if (isCancelled()) {
						zout.close();
						try {
							fin.close();
						} catch (IOException e2) {
							FileUploaderApplet.log(e2);
						}
						return false;
					}
					zout.write(buffer, 0, c);

					if (container.getFileWorkers().indexOf(this) == 0 && update++ % 10 == 0)
						SwingUtilities.invokeLater(new Runnable() { public void run() { container.getTable().repaint(); } });
				}
				zout.close();
			} catch (IOException e) {
				FileUploaderApplet.log(e);
				try {
					fin.close();
				} catch (IOException e2) {
					FileUploaderApplet.log(e2);
				}
				FileUploaderApplet.logWarning("Error writing temporary archive: " + ofilename);
				model.removeFile(ofilename);
				return false;
			} finally {
				try {
					fin.close();
				} catch (IOException e2) {
					FileUploaderApplet.log(e2);
				}
			}
		} catch (FileNotFoundException fe) {
			FileUploaderApplet.logWarning("Error writing temporary archive: " + ofilename);
			FileUploaderApplet.log(fe.toString());
			model.removeFile(ofilename);
			return false;
		}

		return true;
	}

	protected void done() { 
		container.sendStatsUpdate(model.totalSize());
		container.removeFileWorker(this);
	}

	/*
	* return a list of duplicate files, 
	* must be called after processing for empty files, and flattening directories
	* scenarios:
	* archive/compress off (compare filenames, recurse directories)
	* archive on (compare filenames, add .zip to directories compare filenames)
	* compress on (add .zip compare filenames, recurse directories)
	* archive on compress on (add .zip compare filenames, add .zip to directories compare filenames)
	*
	* NOTE: duplicate filename in subdirs are not allowed if archiving is off
	*
	* @return duplicateList ArrayList<File> of files attempting to be added (recursive if not archiving)
	*/
	private ArrayList<File> getDuplicateList() {
		HashSet<File> duplicateSet = new HashSet<File>();
		String fileExt = (compressing) ? ".zip" : "";	//always append to file test
		String dirExt = (archiving) ? ".zip" : "";	//always append to dir test

		for (File file: fileList) {
			String fileName;
           
            //a filename already ending with zip extension will not be recompressed
            if (file.isDirectory())
                fileName = file.getName() + dirExt;
            else if (!file.getName().substring(file.getName().length() - 
                            FileArchiveModel.ZIP_EXTENSION.length()).equals(FileArchiveModel.ZIP_EXTENSION))
                fileName = file.getName() + fileExt;
            else
                fileName = file.getName();

			//check against loaded list
			for (int i = 0; i < container.getTable().getRowCount(); i++) {
				File attached = (File)container.getTable().getModel().getValueAt(i, 0);
				if (attached.getName().equals(fileName))// && !duplicateList.contains(file))
					duplicateSet.add(file);
			}

			//check against clipboard itself with final filenames (duplicate filenames after from subdirs)
			for (File fileTest : fileList) {
				String fileName2 = fileTest.getName() + ((fileTest.isDirectory()) ? dirExt : fileExt);
				if (file != fileTest && fileName.equals(fileName2) && 
						fileList.indexOf(fileTest) > fileList.indexOf(file))
					duplicateSet.add(fileTest);
			}
		}

		ArrayList<File> duplicates = new ArrayList<File>();
		duplicates.addAll(duplicateSet);
		return duplicates;
	}
	
	/*
	 * getFileCount recursively add non-empty files to count
	 * policy: topleve empty files/dirs have been removed already
	 */
	private int getFileCount(List<File> fileList) {
		int count = 0;

		for (File file : fileList) {
			if (file.isDirectory())
				count += getFileCount(new ArrayList<File>(Arrays.asList(file.listFiles())));	//count dirs files
			else if (file.length() > 0)	
				count++; 
		}
		return count;
	}

	/** 
	 * all files must have been closed already, use list to remove from temp location
	 *
	 */
	private void cleanUp() {

		//NOTE fileList has been processed already, so that recursion is unnecessary
		for (File file : fileList) {

			//cleanup temp files only
			if ((file.isDirectory() && archiving) || compressing) {
				file = new File(System.getProperty("java.io.tmpdir") + File.separator + file.getName() + ".zip");	//a temp file
				model.removeFile(file);
				file.delete(); 
			} else {
				model.removeFile(file);
			}
		}
		container.sendStatsUpdate(model.totalSize());
	}


	/** 
	 * recursive archive function
	 * <p>
	 * must check for cancel during individual archive 
	 * 
	 * @param zout 
	 * @param root 
	 * @param ancestors 
	 * @return 
	 * @throws IOException 
	 */
	boolean zip(ZipOutputStream zout, File root, String ancestors) throws IOException {

		for (File file : root.listFiles()) {
			if (file.isDirectory()) {
				if (!zip(zout, file, ancestors + root.getName() + "/"))
					return false;
			} else {
				try {
					ZipEntry ze = new ZipEntry(ancestors + root.getName() + "/" + file.getName());
					FileInputStream fin = new FileInputStream(file.getPath());
                    int bufferSize = (int)Math.min((long)FileUploaderApplet.ZIP_BUF_SIZE, file.length());
					byte buffer[] = new byte[bufferSize];

					try { 
						zout.putNextEntry(ze);
						int c = 0;
						while ((c = fin.read(buffer)) > 0) {
							if (isCancelled())
								return false;
							zout.write(buffer, 0, c);
						}
						SwingUtilities.invokeLater(new Runnable() { public void run() { container.getTable().repaint(); } });

					} catch (Exception e) {
						FileUploaderApplet.log(e.toString());
						return false;
					} finally {
						fin.close();
					}
				} catch (FileNotFoundException fe) {
					FileUploaderApplet.log(fe.toString());
					return false;
				}
			}
		}
		return true;
	}

	private boolean zipFiles(File root) {
		for (File file : root.listFiles()) {
			if (file.isDirectory())
				zipFiles(file);
			else if (!zipFile(file) && isCancelled())
				return false;
		}
		return true;
	}

}
