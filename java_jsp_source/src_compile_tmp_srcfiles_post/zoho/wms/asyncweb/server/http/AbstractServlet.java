//$Id$
package com.zoho.wms.asyncweb.server.http;

import java.io.File;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.logging.Level;
import java.io.IOException;

import com.adventnet.wms.common.CommonUtil;

import com.zoho.wms.asyncweb.server.exception.AWSException;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;

public abstract class AbstractServlet
{
	public static AsyncLogger logger = new AsyncLogger(AbstractServlet.class.getName());

	public Map<String,String> APPINFO;
	public HashSet<String> staticFileList;
	public HashSet<String> ignoreFileList;

	public final void doInit(Map appinfo)
	{
		this.APPINFO = appinfo;
		this.staticFileList = new HashSet<String>(CommonUtil.getList(APPINFO.get("app.allowedstaticfiles")));
		this.ignoreFileList = new HashSet<String>(CommonUtil.getList(APPINFO.get("ignorefilelist")));
	}

	/**
	 * To check if a static file is a valid one
	 * @param filename - static filename with path from doc root
	 */

	public boolean isAllowedStaticFile(String filename)
	{	
		File file = new File(APPINFO.get("app.docroot"),filename);
		//ArrayList staticFileList = CommonUtil.getList(APPINFO.get("app.allowedstaticfiles"));
		//ArrayList ignoreFileList = CommonUtil.getList(APPINFO.get("ignorefilelist"));
		return staticFileList.contains(file.getPath())||ignoreFileList.contains(filename) ;
	}

	public boolean isAllowedStaticFile(String filePath, String filename)
	{
		return staticFileList.contains(filePath)||ignoreFileList.contains(filename);
	}

	/** 
	 * To check if a static file is a valid one
	 * @param file - static file with docroot path
	 */

	public boolean isAllowedStaticFile(File file)
	{
		return isAllowedStaticFile(file.getName());
	}

	public abstract void onHeaderCompletion(HttpRequest req, HttpResponse res) throws Exception;

	public abstract void service(HttpRequest req, HttpResponse res) throws Exception;

	public abstract void onData(HttpRequest req,HttpResponse res) throws Exception;

	public abstract void onOutputBufferRefill(HttpRequest req, HttpResponse res) throws Exception;

	public abstract void onWriteFailure(HttpRequest req, HttpResponse res) throws Exception;

	public abstract void onWriteComplete(HttpRequest req, HttpResponse res) throws Exception;

	public void close(int errorcode,String message,HttpResponse res) throws IOException, AWSException 
	{
		try
		{
			res.sendError(errorcode,message);
		}
		catch(Exception ex)
		{
			logger.log(Level.INFO, " Exception ",AbstractServlet.class.getName(),AWSLogMethodConstants.CLOSE, ex);
		}
		res.close();	
	}
}
