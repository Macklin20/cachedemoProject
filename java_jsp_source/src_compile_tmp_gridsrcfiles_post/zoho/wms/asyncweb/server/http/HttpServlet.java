//$Id$
package com.zoho.wms.asyncweb.server.http;

import com.zoho.wms.asyncweb.server.exception.AWSException;

public abstract class HttpServlet extends AbstractServlet
{
	public void onHeaderCompletion(HttpRequest req, HttpResponse res) throws Exception
	{
	}

	public abstract void service(HttpRequest req, HttpResponse res) throws Exception;

	public final void onData(HttpRequest req,HttpResponse res) throws Exception
	{
		throw new AWSException("StreamMode invoked for HTTP Servlet, Please use HttpStream servlet");
	}

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
