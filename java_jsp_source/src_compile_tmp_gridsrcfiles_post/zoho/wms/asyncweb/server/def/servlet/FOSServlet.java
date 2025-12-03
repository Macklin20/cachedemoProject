//$Id$

package com.zoho.wms.asyncweb.server.def.servlet;

// Java import
import java.io.*;

// Wms import
import com.zoho.wms.asyncweb.server.http.*;
import com.zoho.wms.asyncweb.server.util.HttpResponseCode;
import com.zoho.wms.asyncweb.server.ConfManager;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogConstants;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;
import com.zoho.wms.asyncweb.server.exception.AWSException;
import com.zoho.wms.asyncweb.server.AWSConstants;
import java.util.logging.Level;

public class FOSServlet extends HttpServlet
{
	private static AsyncLogger logger = new AsyncLogger(FOSServlet.class.getName());
	
	public void service(HttpRequest req, HttpResponse res) throws IOException, AWSException
	{
		try
		{
			if(ConfManager.getServerCheckStatus())
			{
				res.addHeader(AWSConstants.HDR_CONTENT_LENGTH, AWSConstants.SAS_TRUE_LENGTH);
				if(ConfManager.isReadOnlyMode())
				{
					res.commitChunkedTransfer(HttpResponseCode.MULTIPLE_CHOICES, AWSConstants.RO);
				}
				res.write(AWSConstants.SAS_TRUE);
			}
			else
			{
				res.addHeader(AWSConstants.HDR_CONTENT_LENGTH, AWSConstants.SAS_FALSE_LENGTH);
				res.commitChunkedTransfer(HttpResponseCode.SERVICE_UNAVAILABLE, AWSConstants.SERVICE_UNAVAILABLE);
				res.write(AWSConstants.SAS_FALSE);
			}
		}
		catch(Exception e)
		{
			logger.addExceptionLog(Level.SEVERE, AWSLogConstants.EXCEPTION_IN_SERVERCHECK, FOSServlet.class.getName(),AWSLogMethodConstants.SERVICE,e);
		}
		finally
		{
			res.close();
		}
	}
}