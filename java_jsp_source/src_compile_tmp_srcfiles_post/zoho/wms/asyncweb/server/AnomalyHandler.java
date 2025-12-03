//$Id$

package com.zoho.wms.asyncweb.server;

import java.util.logging.Level;
import java.io.Serializable;
import java.util.Hashtable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;
import com.zoho.wms.asyncweb.server.util.Util;
import com.adventnet.wms.servercommon.components.executor.WMSTPExecutorFactory;
import com.adventnet.wms.servercommon.components.executor.WmsTask;

public class AnomalyHandler
{
	private static AsyncLogger logger = new AsyncLogger(AnomalyHandler.class.getName());

	private static boolean initialized = false;

	public static void init() throws Exception
	{
		if(initialized)
		{
			logger.log(Level.INFO, "AnomalyHandler already initialized...",AnomalyHandler.class.getName(),AWSLogMethodConstants.INIT);
			return;
		}
		try
		{
			int maxThreadCount = Util.getMaxThreadCount(ConfManager.getAnomalyMaxProcessorCount(), ConfManager.getAnomalyProcessorCount());
			int maxThreadCreationLimit = Util.getMaxThreadCreationLimit(ConfManager.getAnomalyProcessorMaxThreadCreationLimit(), maxThreadCount);
			WMSTPExecutorFactory.createNewExecutor(AWSConstants.ANOMALY_HANDLER, ConfManager.getAnomalyProcessorCount(), maxThreadCount, 100, new Dispatcher(), maxThreadCreationLimit);
			logger.log(Level.INFO, "AWS Anomaly Handler Init status : {0}",AnomalyHandler.class.getName(),AWSLogMethodConstants.INIT, new Object[]{initialized});//No I18n
			initialized = true;
		}
		catch(Exception ex)
		{
			logger.log(Level.INFO, "Exception during AnomalyHandler init ",AnomalyHandler.class.getName(),AWSLogMethodConstants.INIT, ex);
			throw ex;
		}
	}

	public static void handleAnomaly(String anomalyType , Hashtable anomalyinfo)
	{
		if(!initialized)
		{
			try
			{
				init();
			}
			catch(Exception ex)
			{
				logger.log(Level.SEVERE, "Exception in init anomaly handler via handleAnomaly : "+anomalyType,AnomalyHandler.class.getName(),AWSLogMethodConstants.HANDLE_ANOMALY, ex);// No I18n
				return;
			}
		}
		try
		{
			WMSTPExecutorFactory.execute(AWSConstants.ANOMALY_HANDLER, new Event(anomalyType, anomalyinfo));
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE, "Exception in dispatch anomaly info : anomalyType : "+anomalyType,AnomalyHandler.class.getName(),AWSLogMethodConstants.HANDLE_ANOMALY, ex);//No I18n
		}

	}

	private static class Event implements Serializable
	{
		private String anomalyType = null;
		private Hashtable anomalyinfo;

		private Event(String anomalyType, Hashtable anomalyinfo)
		{
			this.anomalyType = anomalyType;
			this.anomalyinfo = anomalyinfo;
		}

		private String getAnomalyType()
		{
			return this.anomalyType;
		}

		private Hashtable getAnomalyInfo()
		{
			return anomalyinfo;
		}
	}

	private static class Dispatcher implements WmsTask
	{
		@Override
		public void handle(Object obj)
		{
			try
			{
				Event event = (Event) obj;
				handleProcess(event.getAnomalyType(), event.getAnomalyInfo(), ConfManager.getAnomalyHandlerObject());
			}
			catch(Exception e)
			{
				logger.log(Level.SEVERE, "Exception in Dispatcher : ",Dispatcher.class.getName(),AWSLogMethodConstants.HANDLE, e);
			}
		}
	}
	
	private static void handleProcess(String anomalyType, Hashtable anomalyinfo, AnomalyHandlerImpl handlerImpl) throws Exception
	{
		if(anomalyType == null || handlerImpl == null)
		{
			logger.log(Level.INFO, "AnomalyHandler Dispatcher : anomalyType / AnomalyHandlerObject is null.",AnomalyHandler.class.getName(),AWSLogMethodConstants.HANDLE_PROCESS);//No I18n
			return;
		}

		if(anomalyType.equals(AWSConstants.ANOMALY_MONITOR))
		{
			handlerImpl.handleAnomalyMonitor(anomalyType , anomalyinfo);
		}
		else if(anomalyType.equals(AWSConstants.ANOMALY_MONITOR_DATA_RECORD))
		{
			handlerImpl.handleAnomalyDataRecord(anomalyType , anomalyinfo);
		}
		else if(anomalyType.equals(AWSConstants.DOS_MONITOR))
		{
			handlerImpl.handleDOSMonitor(anomalyType , anomalyinfo);
		}
		else if(anomalyType.equals(AWSConstants.SERVER_PORT_MONITOR))
		{
			handlerImpl.handleServerPortDown(anomalyType, anomalyinfo);
		}
		else if(anomalyType.equals(AWSConstants.DEADLOCK_MONITOR))
		{
			handlerImpl.handleDeadlockAnomaly(anomalyType , anomalyinfo);
		}
		else
		{
			logger.log(Level.WARNING, "Unknown Anomaly type : {0}, values : {1}",AnomalyHandler.class.getName(),AWSLogMethodConstants.HANDLE_PROCESS, new Object[]{anomalyType, anomalyinfo});
		}
	}
}
