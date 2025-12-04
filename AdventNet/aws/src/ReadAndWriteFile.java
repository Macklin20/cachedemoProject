package com.webengine.servlet;

import com.zoho.wms.asyncweb.server.http.HttpResponse;
import com.zoho.wms.asyncweb.server.http.HttpRequest;
import com.zoho.wms.asyncweb.server.exception.AWSException;

import com.webengine.LRUCache;

import java.io.IOException;
import java.io.RandomAccessFile;

public class ReadAndWriteFile
{
	private HttpRequest req;
	private HttpResponse res;
	private static LRUCache lrucache;
	private String fileName;

	private boolean isRespond;
	private boolean isRespondFromCache;
	private boolean isRespondFromDisk;

	static
	{
		lrucache = new LRUCache(2);
	}

	public ReadAndWriteFile(HttpRequest req, HttpResponse res)
	{
		this.req = req;
		this.res = res;	

		setHome();
	}

	public void setHome()
	{
		try
		{
			String str = "<a href=\"http://wms.localzoho.com:9999/mycustomservlet\">Home</a><br>";
			res.write(str.getBytes());
		}
		catch(AWSException e)
		{
			e.printStackTrace();
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
	}


	public void getParameterFromRequest()
	{
		String value = req.getParameter("filename");
		fileName = value;
	}

	public void writeFile()
	{

		String filename = fileName;


		if(lrucache.checkFilePresent(filename))
		{
			System.out.println("Inside cache block");
			writeFromCache(filename);
			System.out.println("response written fromm cache");
		}
		else
		{
			System.out.println("Inside disk block");
			writeFromFile(filename);
			System.out.println("Responded to client from disk");
		}

	}

	public void writeFromFile(String fileName)
	{
		try
		{
			System.out.println("Writing from file");
			String filePath = System.getProperty("server.home") + "/src/" + fileName;

			if(!fileName.contains(".txt"))
			{
				filePath = System.getProperty("server.home") + "/src/" + fileName + ".txt";
			}

			System.out.println(filePath);

			RandomAccessFile file = new RandomAccessFile(filePath , "r");
			
				
			System.out.println(file);
			byte[] data = new byte[(int)file.length()];
			file.readFully(data);


			writeResponseToClient(data);

			isRespondFromDisk = true;

			addingDataToCache(fileName, data);
		}
		catch(NullPointerException e)
		{
			throw new NullPointerException("filename is null");
		}
		catch(IOException e)
		{
			System.err.println(e.getMessage());
		}

	}


	public void writeFromCache(String filename)
	{
		try
		{
			System.out.println("Writing " + filename + "  from cache");

			byte[] responseData = lrucache.getData(filename);

			if(responseData == null)
			{
				return;
			}

			writeResponseToClient(responseData);
			
			isRespondFromCache = true;

			lrucache.viewCacheList();
		}
		catch(NullPointerException e)
		{
			throw new NullPointerException("filename is null");
		}
	}


	public void writeResponseToClient(byte[] data)
	{
		try
		{
			res.write(data);
			isRespond = true;
			System.out.println("Responded to Client");
		}
		catch(NullPointerException e)
		{
			throw new NullPointerException("Data is null");
		}
		catch(AWSException e)
		{
			System.err.println(e.getMessage());
		}
		catch(IOException e)
		{
			System.err.println(e.getMessage());
		}
	}

	public void addingDataToCache(String filename , byte[] data)
	{
		try
		{
			lrucache.put(filename , data);
			System.out.println("Data added to Cache");
		}
		catch(NullPointerException e)
		{
			throw new NullPointerException("Given parameter is null");
		}
	}

	public boolean isServerRespond()
	{
		return isRespond;
	}

	public void setLRUCache(int capacity)
	{
		lrucache = new LRUCache(capacity);
	}

	public LRUCache getLRUCache()
	{
		return lrucache;
	}

	public boolean isRespondedFromDisk()
	{
		return isRespondFromDisk;
	}

	public boolean isRespondedFromCache()
	{
		return isRespondFromCache;
	}

	public String getFileName()
	{
		return fileName;
	}
}
