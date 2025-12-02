//$Id$
package com.zoho.wms.asyncweb.server.grid.servlet;

// Java import
import java.io.*;
import java.util.Hashtable;
import java.util.logging.Logger;

// Common import
import com.adventnet.wms.common.HttpDataWraper;

// Server common import
import com.adventnet.wms.servercommon.dc.DC;
import com.adventnet.wms.servercommon.dc.DCConstants;
import com.adventnet.wms.servercommon.runtime.RuntimeAdmin;

// Wms import
import com.zoho.wms.asyncweb.server.http.*;
import com.zoho.wms.asyncweb.server.AWSConstants;
import com.zoho.wms.asyncweb.server.exception.AWSException;

public class RuntimeServlet extends HttpServlet
{
	private static Logger logger = Logger.getLogger(RuntimeServlet.class.getName());

	public void service(HttpRequest req, HttpResponse res) throws IOException, AWSException
	{
		String gacKey = req.getHeader(DCConstants.GAC_KEY);
		if(!DC.verifyGridAccessKey(gacKey))
		{
			logger.severe("Invalid GridAccessKey inside RuntimeServlet ");
			res.commitChunkedTransfer(401, "Unauthorised");	//No I18n
			res.close();
			return;
		}

		String opr = req.getParameter(AWSConstants.OPR);//No I18N
		res.commitChunkedTransfer();
		if(opr.equals("getinfo"))//No I18N
		{
			Hashtable details = (Hashtable) HttpDataWraper.getObject(req.getParameter("details"));//No I18N
			res.write(HttpDataWraper.getString(RuntimeAdmin.getInfo(details)));
		}

		res.close();
	}
}