//$Id$
package com.zoho.wms.asyncweb.server;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.logging.Level;

import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;

public class RequestHeartBeatMonitor extends TimeOutListener
{
	private static final AsyncLogger LOGGER = new AsyncLogger(RequestHeartBeatMonitor.class.getName());
        private static final AsyncLogger MTLOGGER = new AsyncLogger("messagetracker");//No I18n

        public static final RequestHeartBeatMonitor TRACKER = new RequestHeartBeatMonitor();

	private RequestHeartBeatMonitor()
	{
		super("RequestHeartBeatMonitor",ConfManager.getRequestTrackerInterval());//No I18N
	}

	public boolean isExpired(Object obj)
	{
		return (System.currentTimeMillis() >= (((AsyncWebClient)obj).getMaxAllowedHeartBeatTime()));
	}

	public boolean isInvalidEntry(Object obj, long time)
	{
		return (((AsyncWebClient)obj).isInvalidHeartBeatTimeoutEntry(time));
	}

	public void handleExpired(ArrayList list)
	{
		LOGGER.log(Level.INFO,"Expired heartbeat list : "+list.size(),RequestHeartBeatMonitor.class.getName(),AWSLogMethodConstants.HANDLE_EXPIRED);
		long readexpired = 0l;
		for(Iterator it = list.iterator(); it.hasNext();)
		{
			AsyncWebClient client = (AsyncWebClient) it.next();
			try
			{
				readexpired++;
				client.close(AWSConstants.EXPIRED_HEARTBEAT);
			}
			catch(Exception e)
			{
				LOGGER.log(Level.WARNING,"Exception during close "+e.getMessage(),RequestHeartBeatMonitor.class.getName(),AWSLogMethodConstants.HANDLE_EXPIRED);//No I18N
			}
		}
		if(readexpired>0)
		{
			MTLOGGER.log(Level.INFO,"Failed Heart Beat "+readexpired,RequestHeartBeatMonitor.class.getName(),AWSLogMethodConstants.HANDLE_EXPIRED);//No I18N
		}

	}
}
