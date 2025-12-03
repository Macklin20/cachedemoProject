//$Id$
package com.zoho.wms.asyncweb.server.http;

//Java import
import java.io.*;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

import com.zoho.wms.asyncweb.server.TrustedDomains;
import com.zoho.wms.asyncweb.server.exception.AWSException;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;
import com.zoho.wms.asyncweb.server.stats.AWSInfluxStats;
import com.zoho.wms.asyncweb.server.util.HttpResponseCode;
import com.zoho.wms.asyncweb.server.AWSConstants;

public class DefaultServlet extends HttpServlet
{
	private static AsyncLogger logger = new AsyncLogger(DefaultServlet.class.getName());

	private HashMap cache = new HashMap();

	public final void service(HttpRequest req, HttpResponse res) throws IOException, AWSException
	{
		try
		{
            System.out.println("This is default servlet");
			FileDesc fd = getDescriptor(req, req.getRequestURL(),res);
                        if(!TrustedDomains.isAllowedReferer(req))
                        {
                                res.restrictXFrame();
                        }
			if(fd.lastModified <= req.getModifiedSince())
			{
				res.sendError(HttpResponseCode.NOT_MODIFIED, HttpResponseCode.NOT_MODIFIED_MSG);
			}
			else
			{
				addCustomHeaders(req, res, fd);
				sendFileContent(req, res, fd);
			}

		}
		catch(IOException exp)
		{
			logger.log(Level.WARNING,"IOException while writing file "+req.getRequestURL()+" ENGINE "+req.getEngineName()+" HOST "+req.getHost()+" PORT "+req.getLocalPort()+" : ",DefaultServlet.class.getName(),AWSLogMethodConstants.SERVICE,exp);//No I18N

			if(exp.getMessage().equals(AWSConstants.UNKNOWN_FILE))
			{
				res.sendError(HttpResponseCode.FORBIDDEN, HttpResponseCode.FORBIDDEN_MSG);
			}
		}
		catch(Exception exp)
		{
			res.sendError(HttpResponseCode.NOT_FOUND, HttpResponseCode.NOT_FOUND_MSG);
		}
		finally
		{
				res.close();
		}
	}

	protected void addCustomHeaders(HttpRequest req, HttpResponse res, FileDesc fd)
	{
		return;
	}

	private FileDesc getDescriptor(HttpRequest req, String url,HttpResponse res) throws IOException 
	{
		return (cache.get(url)!=null && (APPINFO.get(AWSConstants.CACHE).equals(AWSConstants.TRUE)))? ( (FileDesc) cache.get(url)): cacheResource(res, url);
	}

	public void clearCache(String url)
	{
		cache.remove(url);
	}

	public void clearAllCache()
	{
		cache.clear();
	}

	protected void sendFileContent(HttpRequest req, HttpResponse res, FileDesc fd) throws IOException, AWSException
	{
		res.setContentType(fd.contentType);
		ByteBuffer filecontent;
		if(isRawContent(fd.contentType))
		{
			filecontent = ByteBuffer.wrap(fd.rawdata);
		}
		else
		{
			filecontent = TrustedDomains.replaceVariables(fd.data,req);	
		}
		res.setLastModified(fd.lastModified);
		res.setContentLength(filecontent.limit());
		res.sendFile(filecontent);
	}

	private FileDesc cacheResource(HttpResponse res, String url) throws IOException 
	{
		try
		{
			String filePath = getFilePath(url, res);
			if(filePath == null)
			{
				throw new IOException(AWSConstants.UNKNOWN_FILE);
			}
			File f = new File(filePath);
			if (! f.exists()) return null;
			FileDesc fd = new FileDesc();	
			fd.lastModified = f.lastModified();
			fd.contentType = getMimeType(url);
			FileChannel from = new FileInputStream(f).getChannel();
			ByteBuffer bb = from.map(FileChannel.MapMode.READ_ONLY, 0, from.size());
			byte[] data = new byte[bb.remaining()];
			bb.get(data, 0, data.length);
			from.close();
			if(isRawContent(fd.contentType))
			{
				fd.rawdata = data;
				fd.size = data.length;
			}
			else
			{
				fd.data = new String(data);
				fd.size = f.length();
			}

			String cacheprop = APPINFO.get(AWSConstants.CACHE);
			if(cacheprop != null && cacheprop.equals(AWSConstants.TRUE))
			{
				updateFileContent(url, fd);
				cache.put(url, fd);
			}
			return fd;
		}
		catch(Exception exp)
		{
			if(exp.getMessage().equals(AWSConstants.UNKNOWN_FILE))
			{
				throw exp;
			}
		}
		return null;
	}

	/**
	 * To update File content in the File Descriptor
	 * @param url - Request url
	 * @param fd - FileDescriptor
	 */
	protected void updateFileContent(String url, FileDesc fd)
	{
		return;
	}

	protected String getFilePath(String url,HttpResponse res)
	{
		File file = new File(APPINFO.get("app.docroot"),url);
		if(!isAllowedStaticFile(file.getPath(), url))
		{
			AWSInfluxStats.addHackAttemptStats(AWSConstants.INVALID_REQ_URL);
			new AsyncLogger(AWSConstants.HACKLOG).log(Level.SEVERE,"[HACKLOGGER - Hacking Attempt][ip:"+res.getIPAddress()+" url:"+url+"]",DefaultServlet.class.getName(),"getFilePath");//No I18N
			return null;
		}
		return file.getAbsolutePath();
	}

	private String getMimeType(String url)
	{
		int index = url.lastIndexOf('.');
		String mimeType = (index++ > 0)	?(String) mimeMap.get(url.substring(index)) : AWSConstants.TEXT_OR_HTML;
		return (mimeType!=null) ? mimeType : AWSConstants.TEXT_OR_HTML;
	}

	private boolean isRawContent(String contenttype)
	{
		if(contenttype.contains("image/jpeg") || contenttype.contains("image/jpg") || contenttype.contains("image/png") || contenttype.contains("image/gif"))//No I18N
		{
			return true;
		}
		return false;
	}

	protected class FileDesc
	{
		public long size;
		public String contentType;
		public long lastModified;
		public String data;
		public byte[] rawdata;
	}

	private static HashMap mimeMap = new HashMap();
	static {
		mimeMap.put("", "content/unknown");
		mimeMap.put("uu", "application/octet-stream");
		mimeMap.put("exe", "application/octet-stream");
		mimeMap.put("ps", "application/postscript");
		mimeMap.put("zip", "application/zip");
		mimeMap.put("sh", "application/x-shar");
		mimeMap.put("tar", "application/x-tar");
		mimeMap.put("snd", "audio/basic");
		mimeMap.put("au", "audio/basic");
		mimeMap.put("wav", "audio/x-wav");
		mimeMap.put("gif", "image/gif");
		mimeMap.put("jpg", "image/jpeg");
		mimeMap.put("png", "image/png");
		mimeMap.put("jpeg", "image/jpeg");
		mimeMap.put("htm", AWSConstants.TEXT_OR_HTML);
		mimeMap.put("html", AWSConstants.TEXT_OR_HTML);
		mimeMap.put("text", AWSConstants.TEXT_OR_PLAIN);
		mimeMap.put("c", AWSConstants.TEXT_OR_PLAIN);
		mimeMap.put("cc", AWSConstants.TEXT_OR_PLAIN);
		mimeMap.put("c++", AWSConstants.TEXT_OR_PLAIN);
		mimeMap.put(AWSConstants.H, AWSConstants.TEXT_OR_PLAIN);
		mimeMap.put("pl", AWSConstants.TEXT_OR_PLAIN);
		mimeMap.put("txt", AWSConstants.TEXT_OR_PLAIN);
		mimeMap.put("java", AWSConstants.TEXT_OR_PLAIN);
		mimeMap.put("css", "text/css");
		mimeMap.put("xml", "text/xml");
	};
}
