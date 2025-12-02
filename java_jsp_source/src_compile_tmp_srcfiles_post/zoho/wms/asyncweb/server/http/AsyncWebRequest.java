//$Id$
package com.zoho.wms.asyncweb.server.http;

public class AsyncWebRequest
{
	private HttpRequest req;
	private HttpResponse res;

	public AsyncWebRequest(HttpRequest req,HttpResponse res)
	{
		this.req = req;
		this.res = res;
	}	

	public HttpRequest getRequest()
	{
		return req;
	}

	public HttpResponse getResponse()
	{
		return res;
	}

	public int getState()
	{
		return req.getState();
	}
}

