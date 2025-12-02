//$Id$
package com.zoho.wms.asyncweb.server.http;

public abstract class HttpStreamServlet extends AbstractServlet 
{
	public void onHeaderCompletion(HttpRequest req, HttpResponse res) throws Exception
	{
	}

	public void service(HttpRequest req, HttpResponse res) throws Exception
	{
	}

	public abstract void onData(HttpRequest req,HttpResponse res) throws Exception;

	public void onOutputBufferRefill(HttpRequest req, HttpResponse res) throws Exception
	{
	}

	public void onWriteFailure(HttpRequest req, HttpResponse res) throws Exception
	{
	}

	public void onWriteComplete(HttpRequest req, HttpResponse res) throws Exception
	{
	}
}
