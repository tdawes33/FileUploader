/*
 */

package com.inc.util;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/** 
 * QueryString, appends urlencoded http queries in UTF-8 
 * 
 * @author 
 * @version 
 */
public class QueryString {
	private StringBuilder query = new StringBuilder( );

	public QueryString(String name, String value) { 
		encode(name, value);
	}

	public synchronized void add(String name, String value) { 
    	query.append('&');
    	encode(name, value);
	}

	/** 
	 * encode, encode/append the key/value pair
	 * 
	 * @param name : key
	 * @param value : value
	 */
	private synchronized void encode(String name, String value) {
		try { 
			query.append(URLEncoder.encode(name, "UTF-8")); 
			query.append('='); 
			query.append(URLEncoder.encode(value, "UTF-8"));
		} catch (UnsupportedEncodingException ex) { 
			throw new RuntimeException("Broken VM does not support UTF-8"); 
		}
	}
	
	public String getQuery() { 
		return query.toString(); 
	
	}

	public String toString() { 
		return getQuery(); 
	}
}
