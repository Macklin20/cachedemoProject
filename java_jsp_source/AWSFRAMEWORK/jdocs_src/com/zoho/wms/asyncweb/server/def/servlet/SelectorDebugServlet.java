//$Id:$

package com.zoho.wms.asyncweb.server.def.servlet;

// Java import
import java.io.*;

// Wms import
import com.zoho.wms.asyncweb.server.*;
import com.zoho.wms.asyncweb.server.http.*;
import com.zoho.wms.asyncweb.server.exception.AWSException;

public class SelectorDebugServlet extends HttpServlet
{
	public void service(HttpRequest req, HttpResponse res) throws IOException, AWSException
	{		
		res.commitChunkedTransfer();
		if(req.getParameter(AWSConstants.PORT) == null || req.getParameter(AWSConstants.PORT).trim().equals(""))
		{
			res.write("port param missing");//No I18N
			res.close();
			return;
		}
		int port = Integer.parseInt(req.getParameter(AWSConstants.PORT));	
		boolean debug = false;
		if(req.getParameter("debug") != null && !req.getParameter("debug").trim().equals(""))
		{
			debug = Boolean.parseBoolean(req.getParameter("debug"));
		}
		SelectorPool spool = SelectorPoolFactory.getSelectorPool(port);
		String debugInfo = spool.getDebugInfo(debug);
		res.write(debugInfo);
                res.close();
	}
}

