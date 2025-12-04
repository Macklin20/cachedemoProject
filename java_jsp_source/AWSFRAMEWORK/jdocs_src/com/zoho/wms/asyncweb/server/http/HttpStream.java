//$Id$
package com.zoho.wms.asyncweb.server.http;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Hashtable;
import java.security.MessageDigest;
import java.util.logging.Level;

import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;
import com.zoho.wms.asyncweb.server.http2.ConnectionManager;
import com.zoho.wms.asyncweb.server.http2.Http2Stream;

public class HttpStream
{
	private static AsyncLogger logger = new AsyncLogger(HttpStream.class.getName());

	private LinkedList dataList = null;
	private String reqUrl = null;
	private long totalDataSize = 0l;
	private long readSize = 0l;
	private long writeSize = 0l;

	private Object ioLock = new Object();
	MessageDigest md = null;
	byte[] digest = null;

	private long streaminittime;
	private long headerthreadstarttime;
	private long datathreadstarttime;
	private long notificationtime;
	private long notificationproctime;
	private long awsstreamingtime;

	private boolean chunked = false;
	private boolean eocf = false;
	private boolean throwException = false;

	private boolean pause = false;

	private String http2ConnID = null;
	private int streamID = -1;

	public HttpStream(long size, String reqUrl)
	{
		this.reqUrl = reqUrl;
		dataList = new LinkedList();
		totalDataSize = size;
		try
		{
			md = MessageDigest.getInstance("MD5");
		}
		catch(Exception ex)
		{
			logger.log(Level.INFO, " Exception ", HttpStream.class.getName(),AWSLogMethodConstants.HTTP_STREAM, ex);
		}
		this.streaminittime = System.currentTimeMillis();
	}

	public HttpStream(String reqUrl)
	{
		this.reqUrl = reqUrl;
		dataList = new LinkedList();
		chunked = true;
		totalDataSize = -1;
		try
		{
			md = MessageDigest.getInstance("MD5");
		}
		catch(Exception ex)
		{
			logger.log(Level.INFO, " Exception ", HttpStream.class.getName(),AWSLogMethodConstants.HTTP_STREAM, ex);
		}
		this.streaminittime = System.currentTimeMillis();
	}


	public void setThrowException()
	{
		synchronized(ioLock)
		{
			this.throwException = true;
		}
	}

	public boolean isExceptionSet()
	{
		return throwException;
	}

	public byte[] read() throws IOException
	{
		synchronized(ioLock)
		{
			if(dataList.size() == 0)
			{
				return null;
			}
			byte[] data = (byte[])dataList.remove(0);
			readSize += data.length;
			return data;
		}		
	}

	public void write(byte[] data)
	{
		synchronized(ioLock)
		{
			md.update(data);
			dataList.add(data);
			writeSize += data.length;
			if(writeSize == totalDataSize)
			{
				addToAWSStreamingTime(System.currentTimeMillis()-streaminittime);
			}
		}
	}

	public void setEOCF()
	{
		eocf = true;
		addToAWSStreamingTime(System.currentTimeMillis()-streaminittime);
	}

	public boolean isEOCF()
	{
		return eocf;
	}

	public boolean isFinished() throws IOException
	{
		synchronized(ioLock)
		{
			if(http2ConnID != null && streamID != -1)
			{
				Http2Stream  stream = ConnectionManager.getStream(http2ConnID,streamID);
				if(stream != null && stream.isReadCompleted() && dataList.isEmpty())
				{
					return true;
				}
			}
			else if(readSize == totalDataSize || (chunked && eocf && dataList.isEmpty()))
			{
				return true;
			}
			/*	if(throwException)
				{
				throw new IOException("Read Error");//No I18N
				}*/
		}
		return false;
	}

	public boolean isAvailable() throws IOException
	{
		synchronized(ioLock)
		{
			/*	if(throwException)
				{
				throw new IOException("Read Error");//No I18N
				}*/
			if(dataList.size() > 0)
			{
				return true;
			}
		}
		return false;
	}

	public boolean isPaused()
	{
		return pause;
	}

	public void resume()
	{
		pause = false;
	}

	public long getTotalDataSize()
	{
		return totalDataSize;
	}

	public void pause()
	{
		pause = true;
	}

	public long getStreamInitTime()
	{
		return this.streaminittime;
	}

	public void addToNotificationTime(long milliseconds)
	{
		this.notificationtime = this.notificationtime+milliseconds;
	}

	public void addToNotificationProcTime(long milliseconds)
	{
		this.notificationproctime = this.notificationproctime+milliseconds;
	}

	private void addToAWSStreamingTime(long milliseconds)
	{
		this.awsstreamingtime = this.awsstreamingtime+milliseconds;
	}

	public long resetHeaderThreadStartTime()
	{
		long lastinvoketime = this.headerthreadstarttime;
		this.headerthreadstarttime = 0l;
		return lastinvoketime;
	}

	public void setHeaderThreadStartTime()
	{
		if(this.headerthreadstarttime==0l)
		{
			this.headerthreadstarttime = System.currentTimeMillis();
			addToAWSStreamingTime(headerthreadstarttime-streaminittime);
		}
		else
		{
			logger.log(Level.INFO,"STREAM HEADER THREAD ALREADY RUNNING "+this.reqUrl+" DATA SIZE "+this.totalDataSize+" CHUNKED "+chunked, HttpStream.class.getName(),AWSLogMethodConstants.SET_HEADER_THREAD_START_TIME);
		}
	}

	public long resetDataThreadStartTime()
	{
		long lastinvoketime = this.datathreadstarttime;
		this.datathreadstarttime = 0l;
		return lastinvoketime;
	}

	public void setDataThreadStartTime()
	{
		if(this.datathreadstarttime==0l)
		{
			this.datathreadstarttime = System.currentTimeMillis();
		}
		else
		{
			logger.log(Level.INFO,"STREAM DATA THREAD ALREADY RUNNING "+this.reqUrl+" DATA SIZE "+this.totalDataSize, HttpStream.class.getName(),AWSLogMethodConstants.SET_DATA_THREAD_START_TIME);
		}
	}

	public Hashtable getCompleteStats()
	{
		Hashtable<String, String> completeStats= new Hashtable();
		try
		{
			completeStats.put("awsstreamingtime",""+awsstreamingtime);
			completeStats.put("awsnotificationtime",""+notificationtime);
			completeStats.put("notificationproctime",""+notificationproctime);
		}
		catch(Exception ex)
		{
		}
		return completeStats;
	}

	public String getRequestURL()
	{
		return reqUrl;
	}

	public String getDigest()
	{
		synchronized(ioLock)
		{
			digest = md.digest();

			StringBuffer sb = new StringBuffer("");
			for (int i = 0; i < digest.length; i++) {
				sb.append(Integer.toString((digest[i] & 0xff) + 0x100, 16).substring(1));
			}
			return sb.toString();
		}
	}

	public void setHttp2StreamID(int streamID)
	{
		this.streamID = streamID;
	}

	public void setHttp2ConnectionID(String http2ConnID)
	{
		this.http2ConnID = http2ConnID;
	}

}
