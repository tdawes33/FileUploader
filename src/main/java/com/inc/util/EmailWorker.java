/*
 */

package com.inc.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;
import javax.swing.table.DefaultTableModel;

import org.jdesktop.swingworker.SwingWorker;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

/** 
 * Start new/resume incomplete email's in a lower priority thread
 * <p>
 * Boolean for doInBackground, (not useful here), would be returned by call to get
 * Void for publish method (not useful here)
 * <p>
 * Do not call this.get()
 * 
 * @version 
 */
public class EmailWorker extends SwingWorker<Boolean, Void> {
	private final String TRAFFIC_TYPE = "type";
	private final String TRAFFIC_TYPE_GROUP = "group";
	private final String TRAFFIC_TYPE_PRIVATE = "private";

	private final String TRAFFIC_ALLOW_USER_OVERRIDE = "allowUserOverride";
	private final String TRAFFIC_ALLOW_GROUP_OVERRIDE = "allowGroupOverride";

	private final String TRAFFIC_AT_ONCE_MAX = "atOnceMaxBytes";
	private final String TRAFFIC_MONTHLY_MAX_AVAILABLE = "monthlyMaxAvailable";
	private final String TRAFFIC_GROUP_MONTHLY_MAX_AVAILABLE = "groupMonthlyMaxAvailable";
	private final String TRAFFIC_AT_ONCE_LIMIT = "atOnceLimit";
	private final String TRAFFIC_MONTHLY_AVAILABLE = "monthlyAvailable";
	private final String TRAFFIC_GROUP_MONTHLY_AVAILABLE = "groupMonthlyAvailable";

	private final String HEX_ENCODED_FILENAME_HEADER = "FILENAME";

	private final static int PASSWORD_PROTECT_MAX = 10;
	private final static int PASSWORD_PROTECT_MIN = 6;

	private FileUploaderApplet container;
	private String server, service;
	private long sizeWhole, countWhole, resumePosition, startTime, totalTime, rate, newRate;
	private float kbitCount;
	private int resumeRow, percWhole, newPercWhole;
	private boolean unable = false;


	EmailWorker(FileUploaderApplet container) {
		this.container = container; 
		this.server = container.getServer(); 
		this.service = container.getService(); 
		(Thread.currentThread()).setPriority(FileUploaderApplet.UPLOAD_PRIORITY); 
		(Thread.currentThread()).setName("Email Worker");
	}

	/**
	 * Update progress in ui, handle size checks, send email, upload files, send email end. 
	 * <p>start method called on execute() in our own thread
	 *
	 * @return true if all successful
	 */
	public Boolean doInBackground() {
		long uploadSize, trafficSize, trafficSizeKB, freeSpace; 

		percWhole = newPercWhole = 0;
		rate = newRate = 0;

		if (!container.isLoggedIn() && !login()) {
			container.errorPopup(FileUploaderApplet.i18n("login failure"));
			unable = true;
			return false;
		}

		container.getTable().clearSelection();	//disable selection during process
		container.sendProgressStart();

		resumeRow = container.getResumeRow();	//-1 indicates new email
		resumePosition = container.getResumePosition();

		sizeWhole = ((FileArchiveModel)container.getTable().getModel()).totalSizeBytes();
		//get totalcount based on resume too (if resumeRow >=0 resumeposition will be also)
		if (resumeRow >= 0)
			countWhole = ((FileArchiveModel)container.getTable().getModel()).totalSizeBytes(0, resumeRow - 1) + resumePosition;
		else
			countWhole = 0;

		uploadSize = sizeWhole - countWhole;
			
		//this is freespace for group (we may allow private users)
		if ((freeSpace = getFreeSpace()) < 0) {
			FileUploaderApplet.log("getting free space error");
			container.errorPopup(FileUploaderApplet.i18n("server error"));
			unable = true;
			return false;
        }

        FileUploaderApplet.log("Upload size: " + FileArchiveModel.numberToKilo(uploadSize) + " KB");
        FileUploaderApplet.log("Disk space available: " + freeSpace);
		if (freeSpace < FileArchiveModel.numberToKilo(uploadSize)) {
			container.errorPopup(FileUploaderApplet.i18n("exceeds disk space"));
			unable = true;
			return false;
		}

		JSONObject trafficInfo;
		if ((trafficInfo = getTrafficInfo()) == null) {
			FileUploaderApplet.log("getting traffic space error");
			container.errorPopup(FileUploaderApplet.i18n("server error"));
			unable = true;
			return false;
		}

		trafficSize = (container.getRecipients().split(",").length * sizeWhole) + uploadSize;
		trafficSizeKB = FileArchiveModel.numberToKilo(trafficSize);
        FileUploaderApplet.log("Traffic size: " + trafficSizeKB + " KB");
		FileUploaderApplet.log("At once limit: " + trafficInfo.get(TRAFFIC_AT_ONCE_MAX) + " Bytes");

        //traffic at once tests against current upload size
		if (uploadSize > (Long)trafficInfo.get(TRAFFIC_AT_ONCE_MAX)) {	//in bytes (hard coded to 4GB)
			container.errorPopup(FileUploaderApplet.i18n("exceeds traffic width"));
			unable = true;
			return false;
		}

		//based on MegaMail 1.0 server policy
		if (((String)trafficInfo.get(TRAFFIC_TYPE)).equalsIgnoreCase(TRAFFIC_TYPE_GROUP)) {
            FileUploaderApplet.log("Group account");
			
			if (trafficSizeKB > (Long)trafficInfo.get(TRAFFIC_MONTHLY_MAX_AVAILABLE)) {
                FileUploaderApplet.log("exceeds TRAFFIC_MONTHLY_MAX_AVAILABLE");
				container.errorPopup(FileUploaderApplet.i18n("exceeds traffic width"));
				unable = true;
				return false;
            } else if (trafficSizeKB > (Long)trafficInfo.get(TRAFFIC_GROUP_MONTHLY_MAX_AVAILABLE)) { //max less group usage
                FileUploaderApplet.log("exceeds TRAFFIC_GROUP_MONTHLY_MAX_AVAILABLE");
				container.errorPopup(FileUploaderApplet.i18n("exceeds traffic width"));
				unable = true;
				return false;
            } else if (FileArchiveModel.numberToKilo(uploadSize) > (Long)trafficInfo.get(TRAFFIC_AT_ONCE_LIMIT)) { //user ini setting
                FileUploaderApplet.log("exceeds TRAFFIC_AT_ONCE_LIMIT");
				container.errorPopup(FileUploaderApplet.i18n("exceeds traffic width"));
				unable = true;
				return false;
			} else if (trafficSizeKB > (Long)trafficInfo.get(TRAFFIC_MONTHLY_AVAILABLE)) {	//user ini setting

				if (!(Boolean)trafficInfo.get(TRAFFIC_ALLOW_USER_OVERRIDE) ||
						!container.confirmPopup(FileUploaderApplet.i18n("confirm title"), FileUploaderApplet.i18n("traffic width override"))) {
					unable = true;
					return false;
				}

			} else if (trafficSizeKB > (Long)trafficInfo.get(TRAFFIC_GROUP_MONTHLY_AVAILABLE)) {	//user ini setting

				if (!(Boolean)trafficInfo.get(TRAFFIC_ALLOW_GROUP_OVERRIDE) ||
						!container.confirmPopup(FileUploaderApplet.i18n("confirm title"), FileUploaderApplet.i18n("traffic width override"))) {

					unable = true;
					return false;

				}
			}

		} else {

			if (trafficSizeKB > (Long)trafficInfo.get(TRAFFIC_MONTHLY_MAX_AVAILABLE) ||
					trafficSizeKB > (Long)trafficInfo.get(TRAFFIC_AT_ONCE_LIMIT)) {	//user ini setting

				container.errorPopup(FileUploaderApplet.i18n("exceeds traffic width"));

				unable = true;
				return false;

			} else if (trafficSizeKB > (Long)trafficInfo.get(TRAFFIC_MONTHLY_AVAILABLE)) {	//user ini setting
				if (!(Boolean)trafficInfo.get(TRAFFIC_ALLOW_USER_OVERRIDE) ||
						!container.confirmPopup(FileUploaderApplet.i18n("confirm title"), FileUploaderApplet.i18n("traffic width override"))) {

					unable = true;
					return false;

				}
			}

		}

		if (resumeRow >= 0 || postEmail()) {
			if (resumeRow < 0)
				resumeRow = 0; //resumeRow now to be used as initial index (minimum 0)

			kbitCount = totalTime = 0;
			startTime = System.currentTimeMillis();
			for (int i = resumeRow; i < container.getTable().getRowCount(); i++) {
				File file = (File)container.getTable().getModel().getValueAt(i, 0);
				if (!putFile(file)) {
					FileUploaderApplet.logWarning("aborted sending: " + file.getPath());
					return false;
				}
				resumePosition = 0; //must reset in case of resume (otherwise putFile will seek)
			}

            container.sendDisableCancel();

			if (!isCancelled()) {
				//email settings sent twice, for writing cod file 
                //(TODO this won't work for resumes...server must return from mml)
				QueryString query = new QueryString("cmd", "email_end");
				query.add("notification", (FileUploaderApplet.canNotify()) ? "1" : "0");
				query.add("preservation", String.valueOf(FileUploaderApplet.preserveLength()));
				query.add("filecount", String.valueOf(container.getTable().getRowCount()));
				HTTPComm comm = new HTTPComm(container);
		        if (container.getPasswordProtect().length() > 0)
                    comm.setReadTimeout(FileUploaderApplet.PASSWORD_NET_TIMEOUT);

		        container.sendActivityUpdateAsRate(FileUploaderApplet.i18n("sending email"));
				if (!HTTPComm.SUCCESS_VALUE.equalsIgnoreCase(comm.postData(query.toString()))) {
					container.errorPopup(FileUploaderApplet.i18n("incompleted"));
                    unable = true;
					return false;
                }
			}
            container.sendActivityUpdateAsRate("");

            container.sendEnableCancel();

		} else {
			FileUploaderApplet.log("posting email error, resumerow: " + resumeRow);
			container.errorPopup(FileUploaderApplet.i18n("server error"));
			unable = true;
			return false;
		}
		container.sendProgressUpdate(new String[] { "", "100", "100" }); 

		return true;
	}

	/** 
	 * called from EDT
	 */
	protected void done() {

		FileUploaderApplet.log("email worker done");
		if (isCancelled()) {
			container.infoPopup(FileUploaderApplet.i18n("cancelled"));
			container.sendStatsUpdate("0"); 
			container.sendReset();
			((DefaultTableModel)container.getTable().getModel()).setRowCount(0); 
		}

		if (unable)
			container.sendProgressUnable();
		else
			container.sendProgressEnd();

		container.setEmail(null);
		container.resetResume(); //always reset in case of resume
	}

	/*
	 * send credentials, start cookie session 
	 *
	 */
	protected boolean login() {
		HTTPComm comm = new HTTPComm(container);
		QueryString query = new QueryString("cmd", "login");
		query.add("userId", container.getUsername());
		query.add("password", container.getPassword());

		FileUploaderApplet.log("relogin required");
		String response = comm.postData(query.toString());
		if (!HTTPComm.SUCCESS_VALUE.equalsIgnoreCase(response)) {
			container.errorPopup(FileUploaderApplet.i18n("login error"));
			container.setIsLoggedIn(false);
			return false;
		}

		container.setIsLoggedIn(true);
		return true;
	}


	/** 
	 * Assemble email message and post to server
	 * 
	 * @return true on success
	 */
	protected boolean postEmail() {

		QueryString query = new QueryString("cmd", "email");
		query.add("recipients", container.getRecipients());
		String subject = container.getSubject();
		if (subject.length() > 0)
			query.add("subject", subject);

		String password = container.getPasswordProtect();
		if (password.length() > 0) {
            if (password.length() > PASSWORD_PROTECT_MAX) {
                password =  password.substring(0, PASSWORD_PROTECT_MAX);
                FileUploaderApplet.logWarning("password truncated to: " + password);
            }
            query.add("password", password);
        }

		String message = container.getMessage();
		if (message.length() > 0)
			query.add("message", message);

		StringBuilder fileList = new StringBuilder();
		for (int i = 0; i<container.getTable().getRowCount(); i++) {
			try {
				if (fileList.length() > 0)
					fileList.append("?");
				fileList.append(((File)container.getTable().getModel().getValueAt(i, 0)).getCanonicalPath());
			} catch (Exception e) {
				FileUploaderApplet.log("getting filepaths: " + e);
				return false;
			}
		}

		query.add("files", fileList.toString());
		query.add("language", container.getLanguage());
		query.add("notification", (FileUploaderApplet.canNotify()) ? "1" : "0");	
		query.add("preservation", String.valueOf(FileUploaderApplet.preserveLength()));

		HTTPComm comm = new HTTPComm(container);
		String response = comm.postData(query.toString());
		if (!HTTPComm.SUCCESS_VALUE.equals(response)) {
			FileUploaderApplet.log("response was: " + response);
			return false;
		}

		return true;
	}
			
	/** 
	 * check available disk space on server for this user 
	 * 
	 * @return amount available in KB
	 */
	protected long getFreeSpace() {
		HTTPComm comm = new HTTPComm(container);
		QueryString query = new QueryString("cmd", "disk_quota");
		String response = comm.postData(query.toString());

		try {
			return Long.parseLong(response);
		} catch (NumberFormatException e) {
			FileUploaderApplet.log("getting disk quota: " + response + " " + e.toString());
			return -1;
		}
	}

	/** 
	 * 
	 * 
	 * @return map of traffic info
	 */
	protected JSONObject getTrafficInfo() {
		HTTPComm comm = new HTTPComm(container);
		QueryString query = new QueryString("cmd", "traffic_quota");
		String response = comm.postData(query.toString());

		try {
			return (JSONObject)JSONValue.parse(response);
		} catch (Exception e) {
			FileUploaderApplet.log("getting traffic quota: " + response + " " + e.toString());
			return null;
		}
	}
			
	/**
	 * server awaits these files. aborted uploads can be restarted based on email info on server
	 *
	 * @param file the file to upload
	 */
	public boolean putFile(File file) {
		URL url;
		HttpURLConnection connection;
		FileInputStream fis;
		BufferedOutputStream out;
		long sizePart, countPart;
		int blockCount, responseCode, responseSize, count, percPart, newPercPart, bufferSize;

		percPart = newPercPart = 0;

		try {
			fis = new FileInputStream(file);
		} catch (IOException e) {
			FileUploaderApplet.logWarning("Unable to open file: " + file.getName());
			FileUploaderApplet.log(e);
			return false;
		}
		sizePart = file.length();

        //if the files finished uploading, but an error occurred prior to email_end
        if (resumePosition == sizePart) {
            try {
                fis.close();
            } catch (IOException e) {
                FileUploaderApplet.log("closing filestream: " + e);
            }
            container.sendProgressUpdate(new String[] { file.getName(), "100", String.valueOf((int)((countWhole/(float)sizeWhole)*100)) });
            return true;
        }

		if (resumeRow >= 0 && resumePosition > 0) {
			FileUploaderApplet.log("skipping to position: " + resumePosition);
			countPart = resumePosition;
			try {
		        long skipped = fis.skip(resumePosition);
				if (skipped != resumePosition) {
					FileUploaderApplet.logWarning("unable to set file position for upload");
					FileUploaderApplet.log("skip requested " + String.valueOf(resumePosition) + " got " + String.valueOf(skipped));
					return false;
				}
			} catch (IOException e) {
				FileUploaderApplet.logWarning("Error resuming read on file: " + file.getName());
			}
		} else {
			countPart = 0;
		}

		try {
			if (FileUploaderApplet.canEncrypt())
				url = new URL("https", server, service);
			else
				url = new URL("http", server, service);
		} catch (MalformedURLException e) {
			FileUploaderApplet.log("creating url: " + e);
			return false; 
		}

		try {
			if (FileUploaderApplet.canEncrypt())
				connection = (HttpsURLConnection)url.openConnection();
			else
				connection = (HttpURLConnection)url.openConnection();
		} catch (IOException e) {
			FileUploaderApplet.log("opening connection: " + e);
			return false;
		}

		try {
			connection.setRequestMethod("PUT");
		} catch (ProtocolException e) {
			FileUploaderApplet.log("setting put: " + e);
			return false;
		}

        //hex-encoded filename header for 'put' processor
		connection.setRequestProperty(HEX_ENCODED_FILENAME_HEADER, encode2ByteToSafeText(file.getName()));

 		//authentication & redirection requests throws HttpRetryException, when using fixedlength
		connection.setFixedLengthStreamingMode((int)(sizePart - countPart));
		connection.setAllowUserInteraction(false);
		connection.setDoOutput(true);

		if (container.getCookie().length() > 0)
			connection.setRequestProperty("Cookie", container.getCookie());
		else
			FileUploaderApplet.log("no cookie!. this is an error");

		try {
            connection.setReadTimeout(FileUploaderApplet.NET_TIMEOUT);
		} catch (IllegalArgumentException e) {
			FileUploaderApplet.log(e); //ignore
		}

		try {
			out = new BufferedOutputStream(connection.getOutputStream());
		} catch (IOException e) {
			FileUploaderApplet.log("getting outputstream: " + e);
			return false;
		}
		
        bufferSize = Math.min(FileUploaderApplet.BUF_SIZE, (int)(sizePart - countPart));
		byte[] outgoing = new byte[bufferSize];

		//in case of resume, percentage should display immediately (safari ui update problem)
		container.sendProgressUpdate(new String[] { 
			file.getName(),
			String.valueOf((int)((countPart/(float)sizePart)*100)),
			String.valueOf((int)((countWhole/(float)sizeWhole)*100)) });

		blockCount = 0;
		while (true) {

			try {
				blockCount = fis.read(outgoing, 0, bufferSize);
			} catch (IOException e) {
				FileUploaderApplet.logWarning("error reading: " + file.getName());
				FileUploaderApplet.log(e);
				return false;
			}

			if (blockCount < 1)	//-1 eof
				break;

			countPart += blockCount;
			countWhole += blockCount;

			try {
				out.write(outgoing, 0, blockCount);
			} catch (IOException e_outer) {
				FileUploaderApplet.logWarning("error writing to server");
				FileUploaderApplet.log(e_outer);

				try {
					out.flush();	//close does not seem to flush as advertised
					out.close();	//flushes then closes underlying stream
				} catch (IOException e) { /* ignore exception due to cancelled fixed length stream */ }

				try {
					fis.close();
				} catch (IOException e) {
					FileUploaderApplet.log("closing filestream: " + e);
				}

				try {
					if ((responseCode = connection.getResponseCode()) != HttpURLConnection.HTTP_OK)
						FileUploaderApplet.log("error response code: " + String.valueOf(responseCode));
				} catch (IOException e) {
					FileUploaderApplet.log("getting response code after write error: " + e);
				}
				return false;
			}

			totalTime = (System.currentTimeMillis() - startTime) / 1000;
			kbitCount += (blockCount*8)/1000.0;

			if (totalTime > 0) {
				newRate = (long)(kbitCount/(totalTime*1.0));
				if (Math.abs(newRate - rate) > 1000)	//update on significant change
					container.sendRateUpdateKbps(newRate);
				rate = newRate;
			}

			newPercPart = (int)((countPart/(float)sizePart)*100);
			newPercWhole = (int)((countWhole/(float)sizeWhole)*100);
			if (newPercPart != percPart || newPercWhole != percWhole)	//display on any change
				container.sendProgressUpdate(new String[] { file.getName(), String.valueOf(newPercPart), String.valueOf(newPercWhole) });
			percPart = newPercPart;
			percWhole = newPercWhole;

			if (isCancelled()) {
				FileUploaderApplet.logWarning("cancelled email");
				
				try {
					out.flush();	//close does not seem to flush as advertised
					out.close();
				} catch (IOException e) { /* ignore exception due to cancelled fixed length stream */ }

				try {
					fis.close();
				} catch (IOException e) {
					FileUploaderApplet.log("closing filestream: " + e);
				}

				try {
					if ((responseCode = connection.getResponseCode()) != HttpURLConnection.HTTP_OK)
						FileUploaderApplet.log("error response code: " + String.valueOf(responseCode));
				} catch (IOException e) {
					FileUploaderApplet.log("getting response code after cancel: " + e);
				}

				return false;
			}
		}

		try {
			fis.close();	//close file input stream
		} catch (IOException e) {
			FileUploaderApplet.log("closing filestream: " + e);
		}

		try {
			out.flush();	//close does not seem to flush as advertised
			out.close();	//flushes then closes underlying stream
		} catch (IOException e) {
			FileUploaderApplet.log("closing error: " + e);
		}

        //need to wait for response!?!ddp
		try {
			if ((responseCode = connection.getResponseCode()) != HttpURLConnection.HTTP_ACCEPTED) {
				FileUploaderApplet.log("error response code: " + String.valueOf(responseCode));
			    container.errorPopup(FileUploaderApplet.i18n("server error"));
				return false;
			} else {
                FileUploaderApplet.log("upload accepted code received");
            }
		} catch (IOException e) {
			FileUploaderApplet.log("getting response code: " + e);
			return false;
		}
		
		InputStream rawResponse;
		try {
			rawResponse = connection.getInputStream();
		} catch (IOException e) {
			FileUploaderApplet.log("getting response stream: " + e);
			return false;
		}

		responseSize = connection.getContentLength();
        if (responseSize > 0) {
            StringBuilder response =  new StringBuilder();
            try {
                bufferSize = Math.min(FileUploaderApplet.BUF_SIZE, responseSize);
                byte[] incoming = new byte[bufferSize];

                while (response.length() < responseSize && (count = rawResponse.read(incoming, 0, bufferSize)) != -1)
                    response.append(new String(incoming, 0, count, "UTF-8"));
                rawResponse.close();

                if (response.length() > responseSize) {
                    FileUploaderApplet.log("post response length: " + response.length() + " and header was: " + responseSize);
                    FileUploaderApplet.log(response.toString().substring(0, responseSize));
                }

                FileUploaderApplet.log("response was " + response);
            } catch (IOException e) {
                FileUploaderApplet.log("reading response: " + e);
                return false;
            }
        } else {
            FileUploaderApplet.log("response is empty");
        }

		return true;
	}
    
    /**
     * Converts a unicode string to utf-8 hex string
     */
    public String encode2ByteToSafeText(String in) 
    {
        StringBuffer out = new StringBuffer();
        byte[] bytes;
        try {
            bytes = in.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            FileUploaderApplet.log(e);
            return "";
        }

        for (int i = 0; i < bytes.length; i++)
            out.append(String.format("%02x", bytes[i]));

        return out.toString();
    }

}
