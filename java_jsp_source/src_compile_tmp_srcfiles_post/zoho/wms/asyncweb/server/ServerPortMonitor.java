//$Id$
package com.zoho.wms.asyncweb.server;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.Hashtable;
import java.util.logging.Level;

import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.stats.AWSInfluxStats;
import com.zoho.wms.asyncweb.server.util.Util;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;

import com.adventnet.wms.servercommon.util.CustomThreadFactory;

public class ServerPortMonitor extends Thread
{
	private ArrayList portList = ConfManager.getAllPorts();
	private static final AsyncLogger LOGGER = new AsyncLogger(ServerPortMonitor.class.getName());
	private static boolean status = false;
	private static ServerStatusCheckHandler statusCheckHandler = null;
	private boolean printPortList = true;
	private ExecutorService executor = Executors.newSingleThreadScheduledExecutor(new CustomThreadFactory(AWSConstants.AWS_THREAD_PREFIX+"ServerPortRequestMonitorThread"));

	public ServerPortMonitor()
	{
		super(AWSConstants.AWS_THREAD_PREFIX+"ServerMonitorThread");//No I18N
	}

	public void run()
	{
		status = true;

		while(true)
		{
			if(!ConfManager.isServerPortMonitoringEnabled())
			{
				break;
			}

			int serverPortMonitorInterval = ConfManager.getServerPortMonitorInterval();
				
			if(serverPortMonitorInterval==0)
			{
				return;
			}

			try{Thread.sleep(serverPortMonitorInterval*1000);}catch(Exception ex){}

			if(!ConfManager.getServerStartStatus()) { continue; }

			portList = ConfManager.getAllPorts();
			Iterator ie = portList.iterator();
			if(printPortList)
			{
				LOGGER.log(Level.INFO, "Serverportmonitor port list --> {0}",ServerPortMonitor.class.getName(),AWSLogMethodConstants.RUN, new Object[]{portList});
				printPortList = false;
			}
			while(ie.hasNext())
			{
				String key = (String)ie.next();
				int port = Integer.parseInt(key);
				//LOGGER.log(Level.INFO,"Testing PORT "+port);//No I18N
				int count = 1;
				while(count<=3)
				{
					if(getPortStatus(port))
					{
						//LOGGER.log(Level.INFO,"Testing PORT. "+port+" Running");//No I18N
						break;
					}
					else
					{
						LOGGER.log(Level.INFO,"Testing PORT. "+port+" is NOT Running. Trying "+count+" more time(s)");//No I18N
						if(count == 3)
						{
							AWSInfluxStats.addServerPortMonitorStats(""+port , count, 1);
						}
						else
						{
							AWSInfluxStats.addServerPortMonitorStats(""+port , count, 0);
						}
						try{Thread.sleep(serverPortMonitorInterval*1000);}catch(Exception ex){}
					}
					if(count == 2 && statusCheckHandler != null)
					{
						statusCheckHandler.serverStatusCheckHandle(port,count);
					}
					count++;
				}
				if(count>3)
				{
					handlePortFailure(port);
				}
			}
		}

		LOGGER.log(Level.INFO,"Server port monitoring disabled...",ServerPortMonitor.class.getName(),AWSLogMethodConstants.RUN);
		status = false;
	}

	private boolean getPortStatus(int port)
	{
		try
		{
			Future<Object> future = executor.submit(new Callable<Object>()
			{
				public Object call() throws Exception
				{
					return ConfManager.isRunning(port);
				}
			});
			return (boolean)future.get(ConfManager.getServerPortRequestMonitorInterval(), TimeUnit.SECONDS);
		}
		catch (TimeoutException ex)
		{
			LOGGER.log(Level.SEVERE, "[Exception][serverportrequestmonitor] :: {0} Port is down and thread is blocked",ServerPortMonitor.class.getName(),AWSLogMethodConstants.GET_PORT_STATUS, new Object[]{""+port});
			AWSInfluxStats.addServerPortMonitorStats(""+port , -1, 1);
			handlePortFailure(port);
		}
		catch (Exception e)
		{
			LOGGER.log(Level.INFO, "Exception --> in serverportrequestmonitor",ServerPortMonitor.class.getName(),AWSLogMethodConstants.GET_PORT_STATUS, e);
		}
		return false;
	}

	public static boolean isThreadAlive()
	{
		return status;
	}

	private void handlePortFailure(int port)
	{
		LOGGER.log(Level.INFO, "[SERVER PORT MONITOR - PORT DOWN][{0}] :: PORT {1} is DOWN.",ServerPortMonitor.class.getName(),AWSLogMethodConstants.HANDLE_PORT_FAILURE, new Object[]{ConfManager.getIPAddress(), ""+port});
		ConfManager.setServerCheckStatus(false);
		if(ConfManager.isAnomalyHandlerEnabled()) 
		{
			sendAlert(ConfManager.getServiceName(), ConfManager.getIPAddress(), port);
		}
	}

	private void sendAlert(String servicename, String ip, int port)
	{
		try
		{
			Hashtable<String, String> anomalyinfo = new Hashtable<>();
			anomalyinfo.put("anomaly", "SERVER PORT DOWN");
			anomalyinfo.put(AWSConstants.NAME, servicename);
			anomalyinfo.put("ip", ip);
			anomalyinfo.put(AWSConstants.PORT, ""+port);
			AnomalyHandler.handleAnomaly(AWSConstants.SERVER_PORT_MONITOR , anomalyinfo);
		}
		catch(Exception ex)
		{
			LOGGER.log(Level.SEVERE, "Exception in serverport down alert : ip : "+ip+", port : "+port,ServerPortMonitor.class.getName(),AWSLogMethodConstants.SEND_ALERT, ex);//No I18n
		}
	}

	public static void registerServerStatusCheckHandler(ServerStatusCheckHandler obj)
	{
		try
		{
			statusCheckHandler = obj;
			LOGGER.info("ServerStatusCheck Handler initialized.");
		}
		catch(Exception e)
		{
			LOGGER.log(Level.SEVERE,"Error while registering ServerStatusCheck Handler. \n",e);
		}
	}

	public void stopThread()
	{
		status = false;
	}

	public void refreshConfValues()
	{
		portList = ConfManager.getAllPorts();
	}

}
