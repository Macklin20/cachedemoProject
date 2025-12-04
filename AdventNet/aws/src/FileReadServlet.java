package com.webengine.servlet;

import com.webengine.LRUCache;

import com.zoho.wms.asyncweb.server.http.HttpResponse;
import com.zoho.wms.asyncweb.server.http.HttpRequest;
import com.zoho.wms.asyncweb.server.http.HttpServlet;
import com.zoho.wms.asyncweb.server.exception.AWSException;

import java.io.IOException;
import java.io.RandomAccessFile;

public class FileReadServlet extends HttpServlet
{

	private boolean isConnected;
	private ReadAndWriteFile readWriteFile;

	private HttpRequest req;
	private HttpResponse res;

	public void service(HttpRequest req, HttpResponse res) throws IOException , AWSException
	{
		try
		{
			System.out.println("Received request " + req.getRemoteAddr());
			
			this.req = req;
			this.res = res;

			isConnected = true;

			setReadWriteFile();

			System.out.println();
			res.close();
		}
		catch(NullPointerException e)
		{
			throw new NullPointerException("Request or Response is null");
		}
	}

	public void setReadWriteFile()
	{
		this.readWriteFile = new ReadAndWriteFile(req , res);

                readParameters();
	}


	public boolean isServerAcceptedClient()
	{
		return isConnected;
	}


	public void readParameters()
	{
		readWriteFile.getParameterFromRequest();

		readWriteFile.writeFile();
	}
}
