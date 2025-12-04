//$Id$
package com.zoho.wms.asyncweb.server.asyncprocessor;

import java.io.Serializable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import com.zoho.wms.asyncweb.server.http.HttpRequest;
import com.zoho.wms.asyncweb.server.http.HttpResponse;
import com.zoho.wms.asyncweb.server.util.Util;
import com.zoho.wms.asyncweb.server.util.StateConstants;
import com.zoho.wms.asyncweb.server.runtime.WmsRuntimeCounters;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogConstants;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;
import com.zoho.wms.asyncweb.server.exception.AsyncFilterException;
import com.zoho.wms.asyncweb.server.stats.AWSInfluxStats;
import com.zoho.wms.asyncweb.server.ConfManager;
import com.zoho.wms.asyncweb.server.AWSConstants;
import com.zoho.wms.asyncweb.server.AsyncWebClient;

import com.adventnet.wms.common.constants.AWSEngines;

import com.adventnet.wms.servercommon.components.executor.WMSTPExecutorFactory;
import com.adventnet.wms.servercommon.components.executor.WmsTask;

public class AsyncFrameProcessor
{
	private static AsyncLogger logger = new AsyncLogger(AsyncFrameProcessor.class.getName());

	static
	{
		initialize();
	}

	private static void initialize()
	{
		try
		{
			int maxThreadCount = Util.getMaxThreadCount(ConfManager.getMaxExternalFPCount(), ConfManager.getExternalFPCount());
			int maxThreadCreationLimit = Util.getMaxThreadCreationLimit(ConfManager.getExternalFPMaxThreadCreationLimit(), maxThreadCount);
			WMSTPExecutorFactory.createNewExecutor(AWSConstants.TP_FRAME_EXTERNAL, ConfManager.getExternalFPCount(), maxThreadCount, (int)ConfManager.getExternalFPKATime(), new FrameProcessor(), maxThreadCreationLimit);
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE, "Exception in WMSTPExecutorFactory Initialisation :: ",AsyncFrameProcessor.class.getName(),AWSLogMethodConstants.INITIALIZE, ex);//No I18n
		}
	}

	public static void process(AsyncWebClient client)
	{
		try
		{
			WMSTPExecutorFactory.execute(AWSConstants.TP_FRAME_EXTERNAL, new Event(client));
		}       
		catch(Exception ex)
		{
			logger.log(Level.SEVERE, "Exception in WMSThreadPoolExecutor process: ",AsyncFrameProcessor.class.getName(),AWSLogMethodConstants.PROCESS, ex);
		}
	}

	private static class Event implements Serializable
	{
		private AsyncWebClient client = null;

		private Event(AsyncWebClient client)
		{
			this.client = client;
		}

		private AsyncWebClient getClient()
		{
			return this.client;
		}
	}

	static class FrameProcessor implements WmsTask
	{
		@Override
		public void handle(Object obj)
		{
			try
			{
				Event event = (Event) obj;
				handleProcess(event.getClient());
			}
			catch(Exception e)
			{
				logger.log(Level.SEVERE, "Exception in FrameProcessor : ",FrameProcessor.class.getName(),AWSLogMethodConstants.HANDLE, e); 
			}
		}
	}

	private static void handleProcess(AsyncWebClient client)
	{
		try
		{
			client.doFrameProcess();
			//client.notifyFrameProcessor();
		}       
		catch(Exception ex)
		{
		}
	}       

}
