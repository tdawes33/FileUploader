/*
 */

package com.inc.util;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.event.MouseListener;
import java.io.File;
import java.net.CookieHandler;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.Icon;
import javax.swing.JApplet;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.filechooser.FileSystemView;
import javax.swing.table.DefaultTableModel;

import netscape.javascript.JSObject;

import org.json.simple.JSONArray;



/** 
 * main class.
 *
 * @version 
 */
public class FileUploaderApplet extends JApplet
{
	private static Logger logger;
	private static Preferences preferences; 
	private static ResourceBundle bundle;
	final protected static int FILECOUNT_MAX = 100;
	final protected static int BUF_SIZE = 128*1024;
	final protected static int ZIP_BUF_SIZE = 768*1024;
	final protected static int NET_TIMEOUT = 6000;
	final protected static int PASSWORD_NET_TIMEOUT = NET_TIMEOUT + 7000;
	final protected static int LIST_DISPLAY_LIMIT = 8;
	final protected static int BROWSER_DISPATCHER_PRIORITY = Thread.MIN_PRIORITY;
	final protected static int UPLOAD_PRIORITY = Thread.NORM_PRIORITY - 3;
	final protected static String COOKIE_KEY = "Set-Cookie";
	final protected static String COOKIE_NAME = "FILEUPLOADER_SESSION";

	final protected String OUR_NODE_NAME = "/com/inc/util/fileuploader";	
	final protected String MODULE_INFO = "FileUploader applet";
	final protected String[][] PARAM_INFO = { 
        {"font", "string", "preferred font"},
		{"select_bg", "string", "selection color"},
		{"locale", "string", "locale setting"}
    };
	final protected String FILENAME_HEADER_KEY = "filename header";
	final protected String FILESIZE_HEADER_KEY = "size header";
	final protected String FILEDIR_HEADER_KEY = "path header";

	private String cookie = "";
	private boolean isLoggedIn;

	//TODO move to a settings file
	final private boolean[] defaultsPrefs = { true, true, false, false };
	final private String[] preferenceKeys = { "archive", "compress", "ssl", "notice" };
	final private String ja_font = "MS Gothic";
	final private String en_font = "SansSerif";
	final private int defaultPreserveDays = 3;

	private JSObject win;

	private String os, server, service, username, password, token, session_id;
	private int resumeRow = -1;
	private long resumePosition = -1;
	private String language;
	private boolean debug = false;
	private boolean isEmailing = false;
	
	//private Thread browserThread;
	private EmailWorker email = null;
	private ArrayList<FileWorker> fileWorkers = new ArrayList<FileWorker>();
	private JScrollPane scrollPane;
	private JTable table;
	private FileArchiveModel model;
	private PauseResumeView prView;
	private Icon emailIcon;

	//Browser event vars, synchronization not required for non-critical polling (w/ boolean primitive)
	private volatile boolean openFileDialog = false, removeSelection = false, 
			sendEmail = false, finishEmail = false, updateCredentials = false,
			savePreferences = false, viewIncompleteEmail = false;

	public void init() {

		FileUploaderApplet.preferences = Preferences.userRoot().node(OUR_NODE_NAME);
		CookieHandler.setDefault(null);	//mac java embedding plugin (non-safari) is broken. handle cookies manually

		//anonymous Logger required for applet (must be from instance)
		FileUploaderApplet.logger = Logger.getAnonymousLogger();	//
		if (getParameter("debug") != null && Boolean.valueOf(getParameter("debug"))) {
			FileUploaderApplet.logger.setLevel(Level.FINER);
			FileUploaderApplet.logger.setUseParentHandlers(false);
			Handler handler = new ConsoleHandler();
			handler.setLevel(Level.FINER);
			FileUploaderApplet.logger.addHandler(handler);
			try {
				Properties props = new Properties();
				props.load(this.getClass().getClassLoader().getResourceAsStream("META-INF/application.properties"));
				log("******** starting: " + props.getProperty("application.name"));
				log("Build time: " + props.getProperty("application.build.time"));
                log("Version: " + this.getClass().getPackage().getImplementationVersion());
			} catch (Exception e) { 
                log(e); 
            }
		}

		os = System.getProperty("os.name");
		if (os.startsWith("Windows")) 
            os = "windows";	//eg. 'Windows XP'
		else if (os.startsWith("Mac")) 
            os = "mac";
		else 
            os = "unix";

		//TODO use a properties/settings file for defaults
		server = (getParameter("server") != null) ? 
			getParameter("server") : "localhost";
		service = (getParameter("service") != null) ? 
			getParameter("service") : "MMWebSrvc.dll";
        log("Service at: " + server + service);

		//if not provided, go english
		setLocale(new Locale((getParameter("locale") != null) ? getParameter("locale") : "en"));
		bundle = ResourceBundle.getBundle("FileUploaderStrings", getLocale());
		language = getLocale().getDisplayName(new Locale("en"));

		//require login credentials, presumably user is already logged in to a service
		username = getParameter("username");
		token = getParameter("token");
		session_id = getParameter("session_id");

        //only use fonts on non-mac
        if (!os.equalsIgnoreCase("mac")) {
            String fontName;

            String[] fontNames = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
            Arrays.sort(fontNames);
           
            if (getParameter(getLocale() + "_font") != null)
                fontName = getParameter(getLocale() + "_font"); 
            else
                fontName = ("ja".equalsIgnoreCase(getLocale().toString())) ? ja_font : en_font;

            if (Arrays.binarySearch(fontNames, fontName) < 0) {
                fontName = ("ja".equalsIgnoreCase(getLocale().toString())) ? ja_font : en_font;
                if (Arrays.binarySearch(fontNames, fontName) < 0)
                    fontName = "SansSerif";
            }

            // put this font in the defaults table for every ui font resource key
            Font font = new Font (fontName, Font.PLAIN, 12);
            Hashtable defaults = UIManager.getDefaults();
            Enumeration keys = defaults.keys();
            while (keys.hasMoreElements()) {
                Object key = keys.nextElement();
                if ((key instanceof String) && (((String) key).endsWith(".font")))
                    defaults.put (key, font);
            }
        }

		//the client uses the provided token to access server, and start session
		QueryString query = new QueryString("cmd", "access");
		query.add("userId", username);
		query.add("token", token);
		query.add("session_id", session_id);
		HTTPComm comm = new HTTPComm(this);
		String response = comm.postData(query.toString());
		if (HTTPComm.SUCCESS_VALUE.equals(response)) {
			setIsLoggedIn(true);
		} else {
			logWarning(i18n("login failure"));
			setIsLoggedIn(false);
		}

		//use local widgets style if possible
		try { 
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); 
		} catch (Exception e) { 
			FileUploaderApplet.log(e.toString()); 
		}

		String filenameHeader = i18n(FILENAME_HEADER_KEY);
		String sizeHeader = i18n(FILESIZE_HEADER_KEY);
		String pathHeader = i18n(FILEDIR_HEADER_KEY);

		//create model for table entries (files for upload)
		model = new FileArchiveModel();
		table = new JTable(model);

		table.getColumnModel().getColumn(0).setPreferredWidth(180);
		table.getColumnModel().getColumn(1).setPreferredWidth(90);
		table.getColumnModel().getColumn(2).setPreferredWidth(350);
		table.setRowHeight(16);
		table.setRowSelectionAllowed(true);
		table.setDragEnabled(false);	
		table.getTableHeader().setReorderingAllowed(false);
		table.setBackground(Color.white);
		table.setIntercellSpacing(new Dimension(1, 2));
		table.setShowHorizontalLines(false);
		table.setShowVerticalLines(true);
		table.setGridColor(Color.lightGray);

		scrollPane = new JScrollPane(table);
		scrollPane.getViewport().setBackground(Color.white);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

		//event handlers
		table.setTransferHandler(new DropHandler(this));
		scrollPane.setTransferHandler(new DropHandler(this));
		MouseListener popupListener = new ContextMenu(this);
		table.addMouseListener(popupListener);
		scrollPane.addMouseListener(popupListener);

		setLayout(new BorderLayout());
		this.getContentPane().add(scrollPane, java.awt.BorderLayout.CENTER);

		CellRenderer renderer = new CellRenderer();
		try {
			table.setDefaultRenderer(Class.forName("java.lang.Object"), renderer);
		} catch( ClassNotFoundException ex ) { ; }

		try {
			win = (JSObject)JSObject.getWindow(this);	//our browser window reference 
		} catch (Exception e) {
			FileUploaderApplet.log("Error getting browser handle" + e.toString());
		}
			
		(new Thread(new BrowserEventDispatch(this))).start(); //browser event handler (for privileged access)

		loadPreferences();
	}

	/** 
	 * send logout, cancel email thread, fileworkers, & clean up temp directory
	 *
	 */
	public void destroy() {

		QueryString query = new QueryString("cmd", "logout");
		HTTPComm comm = new HTTPComm(this);
		comm.postData(query.toString());

		if (email != null && !email.isDone())
			email.cancel(true);

		for (FileWorker worker : fileWorkers) {
			log("cancelling a fileworker");
			worker.cancel(true);
		}

		model.removeAll();
		
	}

	protected void setEmail(EmailWorker email) { this.email = email; }

	/** 
	 * keep a list of running processes for cleanup, and notification 
	 * 
	 * @param worker 
	 */
	protected void addFileWorker(FileWorker worker) { 
		if (fileWorkers.size() < 1)
			win.call("showProcessing", null);
		fileWorkers.add(worker); 
	}

	/** 
	 * manage the list, and notify user of processing completion 
	 * 
	 * @param worker 
	 */
	protected void removeFileWorker(FileWorker worker) { 

		if (fileWorkers.size() == 1) {
			if (win != null)	//test required in case of destroy
				win.call("doneProcessing", null);
			else
				log("done processing: win handle is null, no notification");
		}
		fileWorkers.remove(worker); 
	}

	public boolean isEmailing() { return (email != null && !email.isDone()); }
	/** 
	 * user convenience call for alerting user to wait 
	 * 
	 * @return true of file processing threads are running
	 */
	public boolean isProcessing() { 
		for (FileWorker worker : fileWorkers) {
			if (!worker.isDone())
				return true;
		}
		return false;
	}

	protected Icon getEmailIcon() {
		if (emailIcon == null) {
			String tempfilename = System.getProperty("java.io.tmpdir") + File.separator + "temp.eml";
			File file = new File(tempfilename);
			try {
				if (file.createNewFile()) {
					FileSystemView fs = FileSystemView.getFileSystemView();
					emailIcon = fs.getSystemIcon(file);
					file.delete();
				}
			} catch (Exception e) {
				log(e);
			}
		}

		return emailIcon;
	}

	protected JTable getTable() { return table; }
	protected String getServer() { return server; }
	protected String getService() { return service; }
	protected ArrayList<FileWorker> getFileWorkers() { return fileWorkers; }

	protected void infoPopup(String message) {
		JOptionPane.showMessageDialog(this, message, "MegaMail", JOptionPane.INFORMATION_MESSAGE);
	}

	protected void errorPopup(String message) {
		JOptionPane.showMessageDialog(this, message, "MegaMail", JOptionPane.ERROR_MESSAGE);
	}

	protected boolean confirmPopup(String message) {
		return confirmPopup("", message);
	}

	protected boolean confirmPopup(String title, String message) {
		return (JOptionPane.showConfirmDialog(this, message, "MegaMail " + title, 
					JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION);
	}

	protected void displayIncompleteEmailViewer(JSONArray pausedEmails) {
		if (prView == null)
			prView = new PauseResumeView(this);

		prView.updateData(pausedEmails);
		if (prView.isVisible())
			prView.show();
		else
			prView.setVisible(true);
	}

	protected void writePreferences() {
		Boolean toggle;
		String amount;

		for (int i=0; i<preferenceKeys.length; i++) {
			toggle = (Boolean)win.call("getCheckboxPref", 
					new String[] { preferenceKeys[i] });
			preferences.putBoolean(preferenceKeys[i], 
					toggle.booleanValue());
		}
		amount = (String)win.call("getSelectPref", new String[] { "preservation" });
		preferences.putInt("preservation", 
				Integer.valueOf(amount));
	}

	/**
	 * read from preferences and send to web ui
	 */
	private void loadPreferences() {

		for (int i=0; i<preferenceKeys.length; i++) {
			win.call("setCheckboxPref", new String[] { preferenceKeys[i], 
					String.valueOf(preferences.getBoolean(preferenceKeys[i],
					defaultsPrefs[i])) }); 
		}

		win.call("setSelectPref", new String[] { "preservation", 
				String.valueOf(preferences.getInt("preservation", defaultPreserveDays)) });
	}

	//routines called from javascript
	public String getAppletInfo() { return MODULE_INFO; }
	public String[][] getParameterInfo() { return PARAM_INFO; }
	public String getPlatform() { return os; }

	public void updateCredentials(String server, String username, String password) { 
		this.server = server;
		this.username = username;
		this.password = password;
		setUpdateCredentials(true); 
	}
	public void sendEmail() { setSendEmail(true); }
	public void openFileDialog() { setOpenFileDialog(true); }
	public void removeSelection() { setRemoveSelection(true); }
	public void savePreferences() { setSavePreferences(true); }
	public void viewIncompleteEmail() { setViewIncompleteEmail(true); }
	public void clearFileList() { ((DefaultTableModel)table.getModel()).setRowCount(0); }
	public int getFileCount() { return ((DefaultTableModel)table.getModel()).getRowCount(); }
	public void finishEmail() { setFinishEmail(true); }
	public void abortProcessing() {
		if (fileWorkers.size() == 0)
			win.call("doneProcessing", null);

		for (FileWorker worker : fileWorkers)
			worker.cancel(true);
	}

	//getters & setters.
	protected String getUsername() { return username; }
	protected String getPassword() { return password; }
	protected String getToken() { return token; }
	protected String getLanguage() { return language; }
	protected int getResumeRow() { return resumeRow; }
	protected long getResumePosition() { return resumePosition; }

	protected void setCookie(String cookie) { this.cookie = cookie; }
	protected String getCookie() { if (cookie == null) cookie = new String(); return cookie; }
	
	protected void setIsLoggedIn(boolean isLoggedIn) { this.isLoggedIn = isLoggedIn; }
	protected boolean isLoggedIn() { 
		if (getCookie().length() == 0) 
			isLoggedIn = false; 
		return isLoggedIn; 
	}

	protected boolean login() {
		HTTPComm comm = new HTTPComm(this);
		QueryString query = new QueryString("cmd", "login");
		query.add("userId", getUsername());
		query.add("password", getPassword());

		String response = comm.postData(query.toString());
		if (!HTTPComm.SUCCESS_VALUE.equalsIgnoreCase(response)) {
			errorPopup(FileUploaderApplet.i18n("login error"));
			setIsLoggedIn(false);
			return false;
		} else {
			setIsLoggedIn(true);
			return true;
		}
	}

	protected void setOpenFileDialog(boolean v) { openFileDialog = v; }
	protected boolean getOpenFileDialog() { return openFileDialog; }

	protected void setRemoveSelection(boolean v) { removeSelection = v; }
	protected boolean getRemoveSelection() { return removeSelection; }

	protected void setUpdateCredentials(boolean v) { updateCredentials = v; }
	protected boolean getUpdateCredentials() { return updateCredentials; }

	protected void setResumeEmail(int resumeRow, long resumePosition) { 
		this.resumeRow = resumeRow; 
		this.resumePosition = resumePosition; 
		log("settting resume position: " + resumePosition);
		sendEmail = true; 
	}

	protected void setSendEmail(boolean v) { sendEmail = v; }
	protected void resetResume() { resumeRow = -1; resumePosition = -1;}

	protected boolean getSendEmail() { return sendEmail; }

	protected void setSavePreferences(boolean v) { savePreferences = v; }
	protected boolean getSavePreferences() { return savePreferences; }
	
	protected void setFinishEmail(boolean v) { finishEmail = v; }
	protected boolean getFinishEmail() { return finishEmail; }

	protected void setViewIncompleteEmail(boolean v) { viewIncompleteEmail = v; }
	protected boolean getViewIncompleteEmail() { return viewIncompleteEmail; }

	//calls to web document
	protected void sendNoAttachmentError() { win.call("noAttachmentError", null); } 
	protected void sendConsoleMsg(String msg) { win.call("consoleMsg", new String[] { msg }); } 
	protected void sendAlertMsg(String msg) { win.call("alertMsg", new String[] { msg }); } 
	protected void sendStatsUpdate(String msg) { win.call("statsUpdate", new String[] { msg }); }
	protected void sendProgressStart() { win.call("progressStart", null); }
	protected void sendProgressEnd() { win.call("progressEnd", null); }
	protected void sendProgressUnable() { win.call("progressUnable", null); }
	protected void sendProgressUpdate(String[] perc) { win.call("progressUpdate", perc); }
	protected void sendRateUpdateKbps(long rate) { 
		String rateFormatted = (rate > 0) ? NumberFormat.getInstance(getLocale()).format(rate) + " kbps" : "";
		win.call("rateUpdate", new String [] { rateFormatted }); 
	}
	protected void sendActivityUpdateAsRate(String msg) { win.call("rateUpdate", new String [] { msg }); }
	protected void sendDisableCancel() { win.call("disableCancel", null); }
	protected void sendEnableCancel() { win.call("enableCancel", null); }

	protected void sendReset() { win.call("reset", new Boolean[] { true }); }
	protected void sendShowProcessing() { win.call("showProcessing", null); }

	protected String getRecipients() { return (String)win.call("getInput", new String[] { "recipients" }); }
	protected void setRecipients(String recipients) { win.call("setInput", new String[] { "recipients", recipients }); }
	protected String getPasswordProtect() { return (String)win.call("getInput", new String[] { "password" }); }
	protected void setPasswordProtect(String password) { win.call("setInput", new String[] { "password", password }); }
	protected String getSubject() { return (String)win.call("getInput", new String[] { "subject" }); }
	protected void setSubject(String subject) { win.call("setInput", new String[] { "subject", subject }); }
	protected String getMessage() { return (String)win.call("getInput", new String[] { "message" }); }
	protected void setMessage(String message) { win.call("setInput", new String[] { "message", message }); }

	//Class methods
	protected static void log(String msg) { logger.fine(msg); }
	protected static void log(Exception e) { logger.fine(e.toString()); }
	protected static void logWarning(String msg) { logger.warning(msg); }
	protected static void logEnter(String className, String method, Object[] params) { logger.entering(className, method, params); }
	protected static String i18n(String key) { return bundle.getString(key); }

	//TODO use a conf file
	protected static Boolean canArchive() { return preferences.getBoolean("archive", false); }
	protected static Boolean canCompress() { return preferences.getBoolean("compress", false); }
	protected static Boolean canEncrypt() { return preferences.getBoolean("ssl", false); }
	protected static Boolean canNotify() { return preferences.getBoolean("notice", false); }
	protected static Integer preserveLength() { return preferences.getInt("preservation", 3); }

	/*
	 * check if we have access to preferences
	 *
	 * @return boolean 
	 */
	protected boolean permitPreferences() {
		try { 
			System.getSecurityManager().checkPermission(new RuntimePermission("preferences"));
			//security.checkPermission(new RuntimePermission("preferences"));
		} catch (SecurityException s) { 
			return false; 
		}

		return true;
	}

}
