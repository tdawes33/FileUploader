/*
 */

package com.inc.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.StringTokenizer;

import javax.net.ssl.HttpsURLConnection;

public class HTTPComm {
	public static String SUCCESS_VALUE = "success";
	public static String FAILURE_VALUE = "failure";

	private FileUploaderApplet container;
	private String server;
	private String service;
    private int readTimeout = FileUploaderApplet.NET_TIMEOUT;

	HTTPComm(FileUploaderApplet container, String server, String service) { 
		this.container = container; 
		this.server = server; 
		this.service = service; 
	}
	HTTPComm(FileUploaderApplet container) {
		this.container = container; 
		this.server = container.getServer(); 
		this.service = container.getService(); 
	}

	public void setReadTimeout(int readTimeout) { this.readTimeout = readTimeout; }

	/** 
	 * post query string, return response
	 * 
	 * @param query 
	 * @return string response from server, or empty string on failure
	 */
	public String postData(String query) {
		URL url;
		HttpURLConnection connection;
		OutputStreamWriter out;
		InputStream rawResponse;
		int responseCode, responseSize, count;

		try {
			if (FileUploaderApplet.canEncrypt())
				url = new URL("https", server, service);
			else
				url = new URL("http", server, service);
		} catch (MalformedURLException e) {
			FileUploaderApplet.log(e);
			return "";
		}

		try {
			if (FileUploaderApplet.canEncrypt())
				connection = (HttpsURLConnection)url.openConnection();
			else
				connection = (HttpURLConnection)url.openConnection();
		} catch (IOException e) {
			FileUploaderApplet.log(e);
			return "";
		}


		connection.setDoOutput(true);
		connection.setAllowUserInteraction(false);

		if (container.getCookie().length() > 0)
			connection.setRequestProperty("Cookie", container.getCookie());
		else
			FileUploaderApplet.log("no cookie to send");

		try {
			connection.setReadTimeout(readTimeout);
		} catch (IllegalArgumentException e) {
			FileUploaderApplet.log(e); //ignore
		}

		try {
			out = new OutputStreamWriter(connection.getOutputStream());
		} catch (IOException e) {
			FileUploaderApplet.log(e);
			return "";
		}

		//content-length/type (application/x-www-form-urlencoded) handled by urlconnection
		try {
			out.write(query);
			out.write("\r\n");
			out.close();
		} catch (IOException e) {
			FileUploaderApplet.log(e);
		}

		try {
			responseCode = connection.getResponseCode();
			if (responseCode != HttpURLConnection.HTTP_OK) {
				FileUploaderApplet.log("post response code: " + String.valueOf(responseCode));
				return "";
			}
		} catch (IOException e) {
			FileUploaderApplet.log("getting response code in httpcom: " + e.toString());
		}

		//we handle only 1 cookie from our server
		String cookie = connection.getHeaderField(FileUploaderApplet.COOKIE_KEY);
		if (cookie != null) {
			StringTokenizer cookieParts = new StringTokenizer(cookie, ";");
			while (cookieParts.hasMoreTokens()) {
				String cookiePart = cookieParts.nextToken();
				String[] keyValue = cookiePart.split("=");
				if (keyValue[0].equals(FileUploaderApplet.COOKIE_NAME)) {
					if (keyValue.length > 1 && keyValue[1].length() > 0)
						container.setCookie(cookiePart);
					else
						container.setCookie("");
					break;
				}
			}
		} else {
			container.setCookie("");
		}

		try {
			rawResponse = connection.getInputStream();
		} catch (IOException e) {
			FileUploaderApplet.log("getting post input stream: " + e.toString());
			return "";
		}

		responseSize = connection.getContentLength();
		StringBuilder response =  new StringBuilder();
		try {
            int bufferSize = Math.min(FileUploaderApplet.BUF_SIZE, responseSize);
			byte[] incoming = new byte[bufferSize];

			while (response.length() < responseSize && (count = rawResponse.read(incoming, 0, bufferSize)) != -1)
				response.append(new String(incoming, 0, count, "UTF-8"));
			rawResponse.close();

            FileUploaderApplet.log(response.toString());
			if (response.length() > responseSize) {
				FileUploaderApplet.log("post response length: " + response.length() + " and header was: " + responseSize);
				return response.toString().substring(0, responseSize);
			} else {
				return response.toString();
			}
		} catch (IOException e) {
			FileUploaderApplet.log("reading post response: " + e.toString());
			return response.toString();
		}

	}


}
