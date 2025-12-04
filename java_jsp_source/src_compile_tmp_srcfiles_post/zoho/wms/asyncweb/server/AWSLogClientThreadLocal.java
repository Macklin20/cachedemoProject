//$Id$
package com.zoho.wms.asyncweb.server;

import com.zoho.logs.logclient.LogClientThreadLocal;

import java.util.logging.Level;

import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;

public class AWSLogClientThreadLocal
{
	private static AsyncLogger logger = new AsyncLogger(AWSLogClientThreadLocal.class.getName());

	public static void setLoggingProperties(String reqid)
	{
		try
		{	
			LogClientThreadLocal.setRequestID(reqid);
			if(ConfManager.isSasLogFormatEnabled())
			{
				LogClientThreadLocal.addCustomField(AWSConstants.APPLICATION, AWSConstants.CUSTOM_FIELD_REQUEST_ID, reqid);
			}
			else
			{
				LogClientThreadLocal.addCustomField(AWSConstants.JAVAAPPLICATION, AWSConstants.CUSTOM_FIELD_REQUEST_ID, reqid);
			}
		}
		catch(Exception e)
		{
			logger.log(Level.INFO,"Exception when setting log id : "+reqid,AWSLogClientThreadLocal.class.getName(),AWSLogMethodConstants.SET_LOGGING_PROPERTIES, e);	
		}
	}

	public static void setAccesslogLoggingProperties(String reqid)
	{
		try
		{
			LogClientThreadLocal.addCustomField(AWSConstants.AWSACCESS, AWSConstants.CUSTOM_FIELD_REQUEST_ID, reqid);
		}
		catch(Exception e)
		{
			logger.log(Level.INFO,"Exception when setting log id in accesslog : "+reqid,AWSLogClientThreadLocal.class.getName(),AWSLogMethodConstants.SET_ACCESS_LOGGING_PROPERTIES, e);
		}
	}

	public static void clear()
	{
		try	
		{
			LogClientThreadLocal.setRequestID(null);
			if(ConfManager.isSasLogFormatEnabled())
			{
				LogClientThreadLocal.removeCustomFields(AWSConstants.APPLICATION, AWSConstants.CUSTOM_FIELD_REQUEST_ID);
			}
			else
			{
				LogClientThreadLocal.removeCustomFields(AWSConstants.JAVAAPPLICATION, AWSConstants.CUSTOM_FIELD_REQUEST_ID);
				LogClientThreadLocal.removeCustomFields(AWSConstants.AWSACCESS, AWSConstants.CUSTOM_FIELD_REQUEST_ID);
			}
			LogClientThreadLocal.clear();                                                                                                                         
		} 
		catch(Exception ex)
		{	
			logger.log(Level.INFO,"Exception when clearing log id.",AWSLogClientThreadLocal.class.getName(),AWSLogMethodConstants.CLEAR, ex);
		}
	}

	public static String getLoggingProperties()
	{
		return LogClientThreadLocal.getRequestID();
	}
}
