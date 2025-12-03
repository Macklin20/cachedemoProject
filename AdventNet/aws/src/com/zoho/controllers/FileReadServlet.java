package com.zoho.controllers;

import com.zoho.wms.asyncweb.server.http.HttpResponse;
import com.zoho.wms.asyncweb.server.http.HttpRequest;
import com.zoho.wms.asyncweb.server.http.HttpServlet;
import com.zoho.wms.asyncweb.server.exception.AWSException;

import java.io.IOException;

import com.zoho.entities.URLFile;
import com.zoho.usecases.ReadingContent;


public class FileReadServlet extends HttpServlet
{
	private HttpRequest req;
	private HttpResponse res;

	private boolean isConnect;
	private boolean isRespond;	

	public void service(HttpRequest req, HttpResponse res) throws IOException , AWSException
	{
		try
		{
			System.out.println("Received request " + req.getRemoteAddr());

			this.req = req;
			this.res = res;

			isConnect = true;

			String str = "<a href=\"http://wms.localzoho.com:9999/mycustomservlet\">Home</a><br>";
			res.write(str.getBytes());
			getParameterFromURL();

			res.close();
		}
		catch(AWSException e)
		{
			e.printStackTrace();
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
		catch(NullPointerException e)
		{
			throw new NullPointerException("Request or Response is null");
		}
	}

	public void getParameterFromURL()
	{
		String fileName = req.getParameter("filename");


		if(fileName != null)
		{
			System.out.println("Request for " + fileName);
			new URLFile(fileName);
			writingResponse();
		}
		else	
		{
			try
			{
				res.write("Enter valid query string and filename");
			}
			catch(AWSException | IOException e)
			{
				e.printStackTrace();
			}
		}
	}	

	public void writingResponse()
	{
		try
		{
			ReadingContent readingContent = new ReadingContent();
			res.write(readingContent.readingFileContent());
			isRespond = true;
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

	public boolean isServerAcceptedClient()
	{
		return isConnect;
	}

	public boolean isServerRespondToClient()
	{
		return isRespond;
	}

}
