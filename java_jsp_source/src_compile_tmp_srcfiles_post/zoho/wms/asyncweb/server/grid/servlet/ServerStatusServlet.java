//$Id$
package com.zoho.wms.asyncweb.server.grid.servlet;

//aws imports
import com.zoho.wms.asyncweb.server.http.HttpRequest;
import com.zoho.wms.asyncweb.server.http.HttpServlet;
import com.zoho.wms.asyncweb.server.http.HttpResponse;

import java.util.logging.Logger;

//servercommon imports
import com.adventnet.wms.servercommon.dc.DC;

public class ServerStatusServlet extends HttpServlet
{
	private static Logger logger = Logger.getLogger(ServerStatusServlet.class.getName());

	public void service(HttpRequest req, HttpResponse res) throws Exception 
	{
		if(DC.getServerStartStatus())
		{
			logger.info("VS --> ServerStatusServlet Server is up");
			res.commitChunkedTransfer(200, "wmstrue");	//No I18n
			res.write("wmstrue");	//No I18n
		}
		else
		{
			res.commitChunkedTransfer(200, "wmsfalse");	//No I18n
			res.write("wmsfalse");	//No I18n
		}
		res.close();
	}
}
