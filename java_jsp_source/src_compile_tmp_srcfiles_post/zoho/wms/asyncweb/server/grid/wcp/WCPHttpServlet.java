//$Id$
package com.zoho.wms.asyncweb.server.grid.wcp;

// aws imports
import com.zoho.wms.asyncweb.server.http.HttpRequest;
import com.zoho.wms.asyncweb.server.http.HttpServlet;
import com.zoho.wms.asyncweb.server.http.HttpResponse;

// wcp common imports
import com.zoho.wms.wcp.common.servlet.WCPRequest;
import com.zoho.wms.wcp.common.servlet.WCPResponse;
import com.zoho.wms.wcp.common.servlet.WCPServlet;

public abstract class WCPHttpServlet extends HttpServlet implements WCPServlet
{
	public void onHeaderCompletion(HttpRequest req, HttpResponse res) throws Exception
	{
	}

	public abstract void service(HttpRequest req, HttpResponse res) throws Exception;

	public void onOutputBufferRefill(HttpRequest req, HttpResponse res) throws Exception
	{
	}

	public void onWriteFailure(HttpRequest req, HttpResponse res) throws Exception
	{
	}

	public void onWriteComplete(HttpRequest req, HttpResponse res) throws Exception
	{
	}

	public void wcpService(WCPRequest req, WCPResponse res) throws Exception
	{
	}
}
