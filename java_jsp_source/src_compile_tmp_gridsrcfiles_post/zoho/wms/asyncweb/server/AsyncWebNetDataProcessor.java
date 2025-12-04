//$Id$
package com.zoho.wms.asyncweb.server;

import java.util.logging.Level;
import java.io.IOException;
import java.io.Serializable;
import java.nio.channels.SocketChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.CancelledKeyException;
import javax.net.ssl.SSLHandshakeException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Enumeration;
import java.util.Map;
import java.util.HashMap;
import java.io.File;

import com.zoho.wms.asyncweb.server.exception.AWSException;
import com.zoho.wms.asyncweb.server.exception.IllegalReqException;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogConstants;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;
import com.zoho.wms.asyncweb.server.stats.AWSInfluxStats;
import com.zoho.wms.asyncweb.server.util.Util;
import com.zoho.wms.asyncweb.server.util.StateConstants;
import com.adventnet.wms.servercommon.components.executor.WMSTPExecutorFactory;
import com.adventnet.wms.servercommon.components.executor.WmsTask;

public class AsyncWebNetDataProcessor
{
	private static final AsyncLogger LOGGER = new AsyncLogger(AsyncWebNetDataProcessor.class.getName());
	private static boolean isWebEngineNetDataEnabled = false;
	
	static
	{
		initialize();
	}

	private static void initialize()
	{
		if(ConfManager.isWebEngineNetDataEnabled())
                {
                        initWebEngineNetDataProcessor();
                }
		else
		{
			try
			{
				int maxThreadPoolSize = Util.getMaxThreadCount(ConfManager.getMaxNetDataProcessorCount(), ConfManager.getNetDataProcessorCount());
                        	int maxThreadCreationLimit = Util.getMaxThreadCreationLimit(ConfManager.getNetDataMaxThreadCreationLimit(), maxThreadPoolSize);
				WMSTPExecutorFactory.createNewExecutor(AWSConstants.NETDATA_PROCESSOR, ConfManager.getNetDataProcessorCount(), maxThreadPoolSize, (int)ConfManager.getNetDataProcessorKeepaliveTime(), new Processor(),  maxThreadCreationLimit);
				LOGGER.log(Level.INFO,"NetdataProcessor Initialized");
			}
			catch(Exception ex)
			{
				LOGGER.log(Level.SEVERE, "Exception in NetdataProcessor Initialization :: ", ex);//No I18n
			}
		}
	}

	private static void initWebEngineNetDataProcessor()
	{
		try
		{
			Properties props = Util.getProperties(ConfManager.getEngineMap());
                	Enumeration propenum = props.propertyNames();
                	while(propenum.hasMoreElements())
                	{
				String engineName = (String)propenum.nextElement();
		       		String app_propfile = ConfManager.getServerHome()+File.separator+"webengine"+File.separator+engineName+File.separator+"conf"+File.separator+"app.properties";
		       		HashMap<String,String> appinfo = loadAppProperties(app_propfile);

		       		int corepoolsize = Util.getMinWebEngineNetData(appinfo);
		       		int maxpoolsize = Util.getMaxWebEngineNetData(corepoolsize, appinfo);
		       		int maxThreadCreationLimit = Util.getWebEngineNetDataMaxThreadCreationLimit(maxpoolsize,appinfo);
		       		int keepalivetime = Util.getWebEngineNetDataKATime(appinfo);

                                WMSTPExecutorFactory.createNewExecutor(AWSConstants.WEBENGINE_NETDATA_PROCESSOR+engineName, corepoolsize, maxpoolsize, keepalivetime, new Processor(),  maxThreadCreationLimit);
				LOGGER.log(Level.INFO,"WebEngine-NetdataProcessor Initialized for Engine :: "+engineName);

			}
			isWebEngineNetDataEnabled = true;
		}
		catch(Exception ex)
		{
			LOGGER.log(Level.SEVERE, "Exception in WebEngine-NetdataProcessor Initialisation  :: ", ex);
		}
	}

	private static HashMap loadAppProperties(String app_propfile)
	{
		HashMap<String,String> appinfo = new HashMap();
		Properties appprops = Util.getProperties(app_propfile);
                if(appprops != null)
                {
                        Enumeration en = appprops.propertyNames();
                        while(en.hasMoreElements())
                        {
                                String key = (String)en.nextElement();
                                String value = appprops.getProperty(key);
                                appinfo.put(key,value);
                        }
                }
		return appinfo;
	}

	static boolean reinit()
	{
		try
		{
			if(isWebEngineNetDataEnabled)
			{
				Properties props = Util.getProperties(ConfManager.getEngineMap());
                		Enumeration propenum = props.propertyNames();
                		while(propenum.hasMoreElements())
                		{	
                       			String engineName = (String)propenum.nextElement();
                       			String app_propfile = ConfManager.getServerHome()+File.separator+"webengine"+File.separator+engineName+File.separator+"conf"+File.separator+"app.properties";
                       			HashMap<String,String> appinfo = loadAppProperties(app_propfile);

                       			int corepoolsize = Util.getMinWebEngineNetData(appinfo);
                       			int maxpoolsize = Util.getMaxWebEngineNetData(corepoolsize, appinfo);
                       			int maxThreadCreationLimit = Util.getWebEngineNetDataMaxThreadCreationLimit(maxpoolsize,appinfo);
                       			int keepalivetime = Util.getWebEngineNetDataKATime(appinfo);

                       			WMSTPExecutorFactory.updateExecutor(AWSConstants.WEBENGINE_NETDATA_PROCESSOR+engineName, corepoolsize, maxpoolsize, keepalivetime, maxThreadCreationLimit);
				}
			}
			else
			{
				int maxThreadCount = Util.getMaxThreadCount(ConfManager.getMaxNetDataProcessorCount(), ConfManager.getNetDataProcessorCount());
                                int maxThreadCreationLimit = Util.getMaxThreadCreationLimit(ConfManager.getNetDataMaxThreadCreationLimit(), maxThreadCount);
                                WMSTPExecutorFactory.updateExecutor(AWSConstants.NETDATA_PROCESSOR, ConfManager.getNetDataProcessorCount(), maxThreadCount, (int)ConfManager.getNetDataProcessorKeepaliveTime(), maxThreadCreationLimit);
			}

			return true;
		}
		catch(Exception ex)
		{
			LOGGER.log(Level.SEVERE, "Exception in netdata processor reinit : ",AsyncWebNetDataProcessor.class.getName(),AWSLogMethodConstants.REINIT, ex);//No I18n
			return false;
		}
	}

	public static void process(SelectionKey key) throws CancelledKeyException
	{
		int readyops = 0;
		AsyncWebClient client = (AsyncWebClient) key.attachment();
		String engineName = client.getWebEngineName();
		if(!key.isValid() || !client.isActive() || !((SocketChannel)key.channel()).isOpen())
		{
			key.cancel();
			readyops = -1;
		}
		else
		{
			readyops = key.readyOps();
			if(readyops == 0)
			{
				return;
			}
			client.setZeroOps();
		}
		try
		{
			if(isWebEngineNetDataEnabled)
			{
				if(engineName!=null)
                                {
                                        WMSTPExecutorFactory.execute(AWSConstants.WEBENGINE_NETDATA_PROCESSOR+engineName, new Event(key, readyops));
				}
				else
				{
					WMSTPExecutorFactory.execute(AWSConstants.WEBENGINE_NETDATA_PROCESSOR+AWSConstants.DEFAULT, new Event(key, readyops));
				}
                        }     
			else
			{
				WMSTPExecutorFactory.execute(AWSConstants.NETDATA_PROCESSOR, new Event(key, readyops));
			}
		}
		catch(Exception ex)
		{
			LOGGER.log(Level.SEVERE, "Exception in ThreadPoolExecutor :: ",AsyncWebNetDataProcessor.class.getName(),AWSLogMethodConstants.PROCESS, ex);//No I18n
		}
	}

	private static class Event implements Serializable
	{
		private SelectionKey key = null;
		private int readyops;
		private long intime;

		private Event(SelectionKey key, int readyOps)
		{
			this.key = key;
			this.readyops = readyOps;
			this.intime = System.currentTimeMillis();
		}

		private SelectionKey getKey()
		{
			return this.key;
		}

		private int getReadyOps()
		{
			return this.readyops;
		}

		private long getIntime()
		{
			return this.intime;
		}
	}

	static class Processor implements WmsTask
	{
		@Override
		public void handle(Object obj)
		{
			try
			{
				Event event = (Event) obj;
				handleProcess(event.getKey(), event.getReadyOps(), event.getIntime());
			}
			catch(Exception e)
			{
				LOGGER.log(Level.SEVERE, "Exception in Processor : ",Processor.class.getName(),AWSLogMethodConstants.HANDLE, e);
			}
		}
	}

	private static void handleProcess(SelectionKey key, int readyops, long intime) throws Exception
	{
		long starttime = System.currentTimeMillis();
		long taskQueuedTime = starttime - intime;
		long readtime = 0l;
		long writetime = 0l;
		int readCount = 0;
		int writeCount = 0;
		AsyncWebClient client = (AsyncWebClient) key.attachment();
		AWSLogClientThreadLocal.setLoggingProperties(client.getReqId());
		try
		{
			if(readyops == -1 || !key.isValid() || !client.isActive() || !((SocketChannel)key.channel()).isOpen())
			{
				LOGGER.addDebugLog(Level.FINE, AWSLogConstants.KEY_NOT_VALID_FOR_CLIENT,AsyncWebNetDataProcessor.class.getName(),AWSLogMethodConstants.HANDLE_PROCESS,  new Object[]{client});
				closeClient(client);
				return;
			}
			if((readyops & SelectionKey.OP_READ) == SelectionKey.OP_READ)
			{
				long start = System.currentTimeMillis();
				try
				{
					client.readData(key);
				}
				catch(CancelledKeyException cke)
				{
					closeClient(client);
				}
				catch(AWSException awsexp)
				{
					LOGGER.addExceptionLog(Level.WARNING, "AWSException in isreadable for client "+client+" "+client.getIPAddress()+" , Details ",AsyncWebNetDataProcessor.class.getName(),AWSLogMethodConstants.HANDLE_PROCESS, awsexp);
					closeClient(client);
				}
				catch(IOException ex)
				{
					LOGGER.addExceptionLog(Level.FINE, "IOException in isreadable for client "+client+" "+client.getIPAddress()+" , Details ",AsyncWebNetDataProcessor.class.getName(),AWSLogMethodConstants.HANDLE_PROCESS, ex);//No I18N
					closeClient(client);
				}
				catch(IllegalReqException ire)
				{
					if(ConfManager.isIllegalReqExceptionEnabled())
					{
						LOGGER.log(Level.INFO, "Exception --> requrl:"+client.getRequestURL()+", ipaddr:"+client.getIPAddress(),AsyncWebNetDataProcessor.class.getName(),AWSLogMethodConstants.HANDLE_PROCESS, ire);
					}
					closeClient(client);
				}
				catch(Exception ex)
				{
					LOGGER.addExceptionLog(Level.INFO, "Exception in isreadable for client "+client+" "+client.getIPAddress()+" , Details ",AsyncWebNetDataProcessor.class.getName(),AWSLogMethodConstants.HANDLE_PROCESS, ex);//No I18N
					closeClient(client);
				}
				finally
				{
					readtime = System.currentTimeMillis() - start;
					readCount = 1;
				}
			}
			else if((readyops & SelectionKey.OP_WRITE) == SelectionKey.OP_WRITE)
			{
				long start = System.currentTimeMillis();
				try
				{
					client.handleWrite(key);
				}
				catch(CancelledKeyException cex)
				{
					LOGGER.addExceptionLog(Level.FINE,"CancelledKeyException in isWritable for client "+client+" "+client.getIPAddress()+" , Details ", AsyncWebNetDataProcessor.class.getName(),AWSLogMethodConstants.HANDLE_PROCESS,cex);//No I18N
					closeClient(client);
				}
				catch(SSLHandshakeException cex)
				{
					LOGGER.addExceptionLog(Level.FINE,"SSLHandshakeException in isWritable for client "+client+" "+client.getIPAddress()+" , Details ", AsyncWebNetDataProcessor.class.getName(),AWSLogMethodConstants.HANDLE_PROCESS,cex);//No I18N
					closeClient(client);
				}
				catch(AWSException awsexp)
				{
					LOGGER.addExceptionLog(Level.WARNING,"AWSException in isWritable for client "+client+" "+client.getIPAddress()+" , Details ", AsyncWebNetDataProcessor.class.getName(),AWSLogMethodConstants.HANDLE_PROCESS,awsexp);//No I18N
					closeClient(client);
				}
				catch(IOException ex)
				{
					LOGGER.addExceptionLog(Level.FINE,"IOException in isWritable for client "+client+" "+client.getIPAddress()+" , Details ",AsyncWebNetDataProcessor.class.getName(),AWSLogMethodConstants.HANDLE_PROCESS, ex);//No I18N
					closeClient(client);
				}
				catch(Exception ex)
				{
					LOGGER.addExceptionLog(Level.INFO,"Exception in isWritable case for client "+client+" "+client.getIPAddress()+" , Details ", AsyncWebNetDataProcessor.class.getName(),AWSLogMethodConstants.HANDLE_PROCESS,ex);//No I18N
					closeClient(client);
				}
				finally
				{
					writetime = System.currentTimeMillis() - start;
					writeCount = 1;
				}
			}
		}
		catch(CancelledKeyException cke)
		{
			LOGGER.addExceptionLog(Level.FINE,"CancelledKeyException for client "+client+" "+client.getIPAddress()+" , Details ",AsyncWebNetDataProcessor.class.getName(),AWSLogMethodConstants.HANDLE_PROCESS, cke);//No I18N
			closeClient(client);
		}
		catch(Exception ex)
		{
			LOGGER.addExceptionLog(Level.FINE,"Exception for client "+client+" "+client.getIPAddress()+" , Details ", AsyncWebNetDataProcessor.class.getName(),AWSLogMethodConstants.HANDLE_PROCESS,ex);//No I18N
			closeClient(client);
		}
		finally
		{
			addQOSStats(client, taskQueuedTime, System.currentTimeMillis() - starttime, readtime, writetime, readCount, writeCount);
		}
	}

	private static void addQOSStats(AsyncWebClient client, Long taskQueuedTime, Long processingTime, Long readtime, Long writetime, Integer readCount, Integer writeCount)
	{
		try
		{
			if (ConfManager.isQOSEnabled())
			{
				String uri = client.getStatsRequestURL();
				String appname = client.getWebEngineName();
				int responseCode = client.getResponseCode();

				if(uri.equals(AWSConstants.NA) && client.isSSL())
				{
					client.addSSLStats(taskQueuedTime, processingTime, readtime, writetime, readCount, writeCount, 1);
				}
				else
				{
					AWSInfluxStats.addAWSRequestStats(uri, responseCode, appname, AWSConstants.NETDATA_TASKQUEUE, taskQueuedTime, 1);
					AWSInfluxStats.addAWSRequestStats(uri, responseCode, appname, AWSConstants.NETDATA_PROCESSING, processingTime, 1);
					if(readCount > 0)
					{
						AWSInfluxStats.addAWSRequestStats(uri, responseCode, appname, AWSConstants.NETDATA_READDATA, readtime, readCount);
					}
					if(writeCount > 0)
					{
						AWSInfluxStats.addAWSRequestStats(uri, responseCode, appname, AWSConstants.NETDATA_WRITEDATA, writetime, writeCount);
					}
					AWSInfluxStats.addAWSRequestStats(uri, responseCode, appname, AWSConstants.NETDATA_TOTALTIME, taskQueuedTime + processingTime, 1);
				}
			}
			if(client.isHttp2())
			{
				client.setSocketTimeTakenStats(readtime, writetime, readCount, writeCount);
			}
		}
		catch(Exception ex)
		{
		}
	}

	private static void closeClient(AsyncWebClient client)
	{
		try
		{
			if(client == null)
			{
				return;
			}
			if(client.isWebSocket() && client.isWriteInitiated() && !client.isWriteComplete())
			{
				client.notifyWSWriteFailure();
				client.close(AWSConstants.WS_WRITE_FAILURE);
				return;
			}
			else
			{
				client.close(AWSConstants.EXCEPTION_INTEREST_OPS);
			}
		}
		catch(Exception ex)
		{
			LOGGER.addExceptionLog(Level.FINE, "Exception during client close "+client, AsyncWebNetDataProcessor.class.getName(),AWSLogMethodConstants.CLOSE_CLIENT,ex);//No I18N
		}
	}

	public static int getActivePoolSize() throws Exception
	{
		return WMSTPExecutorFactory.getActivePoolSize(AWSConstants.NETDATA_PROCESSOR);
	}

	public static int getQueueSize() throws Exception
	{
		return WMSTPExecutorFactory.getQueueSize(AWSConstants.NETDATA_PROCESSOR);
	}
}
