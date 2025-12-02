//$Id$

package com.zoho.wms.asyncweb.server.monitor;

import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.management.ManagementFactory;

import java.util.logging.Level;
import java.util.Hashtable;
import java.util.concurrent.atomic.AtomicLong;

import com.zoho.wms.asyncweb.server.ConfManager;
import com.zoho.wms.asyncweb.server.AnomalyHandler;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;
import com.zoho.wms.asyncweb.server.AWSConstants;

public class DeadlockMonitor extends Thread
{
	private AsyncLogger logger = new AsyncLogger(DeadlockMonitor.class.getName());
	private String mail_subject = "[AWS MONITOR][DEADLOCK ANOMALY]-["+ConfManager.getServiceName()+"]["+ConfManager.getWnetAddress()+"]"; // No I18N
	private boolean isDLMonitoringEnabled = false;
	private static boolean status = false;
	private static AtomicLong counter = new AtomicLong(0);

	public DeadlockMonitor()
	{
		super(AWSConstants.AWS_THREAD_PREFIX+"DeadLockMonitoringThread"+AWSConstants.THREAD_NAME_SEPARATOR+counter.getAndIncrement()); // No I18N
		isDLMonitoringEnabled = ConfManager.isDeadlockMonitoringEnabled();
	}

	public void run()
	{
		if(status)
		{
			logger.log(Level.INFO, "DeadLock Monitoring thread is already running...",DeadlockMonitor.class.getName(),AWSLogMethodConstants.RUN);//No I18n
			return;
		}
		status = true;
		while(status)
		{
			if(!isDLMonitoringEnabled)
			{
				logger.log(Level.INFO, "DeadLock Monitoring is not enabled..!!!",DeadlockMonitor.class.getName(),AWSLogMethodConstants.RUN);//No I18n
				break;
			}
			try
			{
				ThreadMXBean mxBean = ManagementFactory.getThreadMXBean();
                       		long[] lockedIds = mxBean.findDeadlockedThreads();
                       		if(lockedIds == null)
                       		{
                               		continue;
                       		}
				StringBuilder mailinfo = new StringBuilder();
				StringBuilder alertinfo = new StringBuilder();
				mailinfo.append("DeadLock Info : <br>");
                      		for(ThreadInfo tinfo : mxBean.getThreadInfo(lockedIds,true, true))
                        	{
                              		try
                               		{
						mailinfo.append(tinfo.toString()+"<br><br>");
						alertinfo.append(tinfo.toString());
					}
                               		catch(Exception e)
                                	{
                                   		logger.log(Level.INFO, "Exception : ", e);
                               		}
                  		}
				logger.log(Level.SEVERE, "DeadLock Occurred ::: \n{0}",DeadlockMonitor.class.getName(),AWSLogMethodConstants.RUN, new Object[]{alertinfo.toString()});
				if(ConfManager.isAnomalyHandlerEnabled()) 
				{
					sendAlert(alertinfo.toString());
				}
				try {Thread.sleep(ConfManager.getDLMonitoringWaitingInterval());} catch(Exception e){}
			}
			catch(Exception e)
			{
				logger.log(Level.INFO, "Error occured in deadlock anomaly -- ",DeadlockMonitor.class.getName(),AWSLogMethodConstants.RUN, e);
			}
		}
		logger.log(Level.INFO, "Deadlock Monitoring Disabled !!!",DeadlockMonitor.class.getName(),AWSLogMethodConstants.RUN);
		status = false;
	}

	private void sendAlert(String tinfo)
        {
                try
                {
                        Hashtable anomalyinfo = new Hashtable();
                        anomalyinfo.put("anomaly_type", AWSConstants.DEADLOCK_MONITOR);
                        anomalyinfo.put("info", tinfo);
			if(ConfManager.isAnomalyHandlerEnabled())
			{
				AnomalyHandler.handleAnomaly(AWSConstants.DEADLOCK_MONITOR, anomalyinfo);
			}
		}
                catch(Exception e)
                {
			logger.log(Level.INFO, "Exception during send alert ",DeadlockMonitor.class.getName(),AWSLogMethodConstants.SEND_ALERT, e);
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

	public void refreshConfValues()
	{
		mail_subject = "[AWS MONITOR][DEADLOCK ANOMALY]-["+ConfManager.getServiceName()+"]["+ConfManager.getWnetAddress()+"]"; // No I18N
		isDLMonitoringEnabled = ConfManager.isDeadlockMonitoringEnabled();
	}
}
