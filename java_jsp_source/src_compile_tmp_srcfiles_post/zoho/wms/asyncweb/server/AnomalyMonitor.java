//$Id$

package com.zoho.wms.asyncweb.server;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

//apache import
import java.util.logging.Level;

import com.zoho.wms.asyncweb.server.util.Util;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.adventnet.wms.common.HttpDataWraper;


public class AnomalyMonitor extends Thread
{
	private static AsyncLogger logger = new AsyncLogger(AnomalyMonitor.class.getName());
	private static boolean status = false;
	private static AtomicLong counter = new AtomicLong(0);

	public AnomalyMonitor()
	{
		super(AWSConstants.AWS_THREAD_PREFIX+"AnomalyMonitor"+AWSConstants.THREAD_NAME_SEPARATOR+counter.getAndIncrement()+AWSConstants.THREAD_NAME_SEPARATOR+"Thread");//No I18n
	}

	public void run()
	{
		if(isThreadAlive())
		{
			logger.log(Level.INFO, "AnomalyMonitor Thread is already running...",AnomalyMonitor.class.getName(),AWSLogMethodConstants.RUN);//No I18n
			return;
		}

		int anomalyMonitorInterval = ConfManager.getAnomalyMonitorInterval();
		int anomalyMailingInterval = ConfManager.getAnomalyScheduleInterval();
		int record = 0;
		boolean reset = false;
		status = true;
		int counter = 0;
		
		while(true)
		{
			try
			{
				Thread.sleep(anomalyMonitorInterval*60*1000);
			}
			catch(Exception e)
			{
			}

			if(!status)
			{
				logger.log(Level.INFO, "AnomalyMonitor Thread stopped..!!!",AnomalyMonitor.class.getName(),AWSLogMethodConstants.RUN);//No I18n
				break;
			}

			if(ConfManager.isAnomalyDetectionEnabled())
			{
				try
				{
					boolean sendmail = false;
					long serverstarttime = ConfManager.getServerStartTime();
					long curtime = System.currentTimeMillis();

					boolean behaviouranalysis = false;
					boolean anomalycapture = false;
					if(ConfManager.isAnomalySchedulingEnabled())
					{
						counter++;
						
						if(counter == (anomalyMailingInterval/anomalyMonitorInterval))
						{
							sendmail = true;
							counter = 0;
						}
					}
					else
					{
						sendmail = true;
					}
					behaviouranalysis = ((((curtime - serverstarttime) / 60000) % 60) <= ConfManager.getAnomalyBehaviourAnalysisPeriod());
					anomalycapture = !behaviouranalysis;
					if(behaviouranalysis && reset)
					{
						AnomalyTracker.resetCounters();
					}

					if(behaviouranalysis)
					{
						reset = false;
						AnomalyTracker.readBehaviour();
						record ++;
						if(record % ConfManager.getIterationPerAnomaly() == 0)
						{
							recordAnomalyData(AnomalyTracker.getHealthData());
							record = 0;
						}
					}
					if(anomalycapture)
					{
						reset = true;
						record ++;
						if(record % ConfManager.getIterationPerAnomaly() == 0)
						{
							recordAnomalyData(AnomalyTracker.getHealthData());
							record = 0;
						}
						Hashtable anomalydata = AnomalyTracker.detectAnomaly();
						if(ConfManager.isAnomalyHandlerEnabled() && anomalydata != null && anomalydata.size() > 0)
						{
							sendAlert(anomalydata);
						}
					}
	
				}
				catch(Exception ex)
				{
					AnomalyTracker.resetCounters();
				}

			}
			else
			{
				break;
			}
		}
		
		logger.log(Level.INFO,"Anomaly monitoring disabled...",AnomalyMonitor.class.getName(),AWSLogMethodConstants.RUN);
		status = false;

	}

	private void recordAnomalyData(Hashtable data)
	{
		if(ConfManager.isAnomalyDataRecordEnabled())
		{
			try
			{
				if(ConfManager.isAnomalyHandlerEnabled() && data!= null)
				{
					Hashtable anomalyinfo = new Hashtable();
					anomalyinfo.put("anomaly_type", AWSConstants.ANOMALY_MONITOR_DATA_RECORD);
					anomalyinfo.put("anomalydata", data);
					AnomalyHandler.handleAnomaly(AWSConstants.ANOMALY_MONITOR_DATA_RECORD, anomalyinfo);
				}
			}
			catch(Exception ex)
			{
				logger.log(Level.INFO, " Exception ",AnomalyMonitor.class.getName(),AWSLogMethodConstants.READ_ANOMALY_DATA, ex);
			}
		}
	}

	private void sendAlert(Hashtable anomalydata)
	{
		try
		{
			Hashtable anomalyinfo = new Hashtable();
			anomalyinfo.put("anomaly_type", AWSConstants.ANOMALY_MONITOR);
			anomalyinfo.put("anomalydata", anomalydata);
			AnomalyHandler.handleAnomaly(AWSConstants.ANOMALY_MONITOR , anomalyinfo);
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE, "Exception in send alert in anomaly monitor : ",AnomalyMonitor.class.getName(),AWSLogMethodConstants.SEND_ALERT, ex);//No I18n
		}
	}

	public static boolean isThreadAlive()
	{
		return status;
	}

	public void stopThread()
	{
		status = false;
	}
}
