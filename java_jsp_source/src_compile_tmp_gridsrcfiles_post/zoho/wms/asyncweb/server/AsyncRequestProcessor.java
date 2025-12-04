//$Id$
package com.zoho.wms.asyncweb.server;

import java.io.Serializable;
import java.util.logging.Level;

import com.zoho.wms.asyncweb.server.http.HttpRequest;
import com.zoho.wms.asyncweb.server.http.HttpResponse;
import com.zoho.wms.asyncweb.server.util.Util;
import com.zoho.wms.asyncweb.server.util.StateConstants;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogConstants;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;
import com.zoho.wms.asyncweb.server.stats.AWSInfluxStats;

import com.adventnet.wms.servercommon.components.executor.WmsTask;
import com.adventnet.wms.servercommon.components.executor.WMSTPExecutorFactory;

public class AsyncRequestProcessor
{
	private static AsyncLogger logger = new AsyncLogger(AsyncRequestProcessor.class.getName());

	private static int corethreadcount;
        private static int maxthreadcount;

	static
	{
		initialize();
	}

	private static void initialize()
	{
		try
		{
                        corethreadcount = ConfManager.getRequestProcessorCount();
                        maxthreadcount = Util.getMaxThreadCount(ConfManager.getMaxRequestProcessorCount(), ConfManager.getRequestProcessorCount());
                        int maxThreadCreationLimit = Util.getMaxThreadCreationLimit(ConfManager.getRequestProcessorMaxThreadCreationLimit(), maxthreadcount);
			WMSTPExecutorFactory.createNewExecutor(AWSConstants.REQUEST_PROCESSOR, ConfManager.getRequestProcessorCount(), maxthreadcount, (int)ConfManager.getRequestProcessorKeepaliveTime(), new WSDispatcher(),  maxThreadCreationLimit);
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE, "Exception in WMSTPExecutorFactory Initialisation :: ",AsyncRequestProcessor.class.getName(),AWSLogMethodConstants.INITIALIZE, ex);//No I18n
		}
	}

	static boolean reinit()
	{
		try
		{
			if((corethreadcount != ConfManager.getRequestProcessorCount() && ConfManager.getRequestProcessorCount() >= 0) || (maxthreadcount != ConfManager.getMaxRequestProcessorCount()))
			{
				corethreadcount = ConfManager.getRequestProcessorCount();
				maxthreadcount = Util.getMaxThreadCount(ConfManager.getMaxRequestProcessorCount(), ConfManager.getRequestProcessorCount());
				int maxThreadCreationLimit = Util.getMaxThreadCreationLimit(ConfManager.getRequestProcessorMaxThreadCreationLimit(), maxthreadcount);
				WMSTPExecutorFactory.updateExecutor(AWSConstants.REQUEST_PROCESSOR, ConfManager.getRequestProcessorCount(), maxthreadcount, (int)ConfManager.getRequestProcessorKeepaliveTime(), maxThreadCreationLimit);
			}

			return true;
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE, "Exception in request processor reinit. ",AsyncRequestProcessor.class.getName(),AWSLogMethodConstants.REINIT, ex);//No I18n
			return false;
		}
	}

	private static class Event implements Serializable
	{
		private AsyncWebClient client = null;
		private int state;
		private long index;
		private long intime;
		private String type;

		private Event(AsyncWebClient client, int state, String type)
		{
			this.client = client;
			this.state = state;
			this.intime = System.currentTimeMillis();
			this.type = type;
		}

		private Event(AsyncWebClient client, long index, String type)
		{
			this.client = client;
			this.index = index;
			this.intime = System.currentTimeMillis();
			this.type = type;
		}

		private AsyncWebClient getClient()
		{
			return this.client;
		}

		private int getState()
		{
			return this.state;
		}

		private long getIntime()
		{
			return this.intime;
		}

		private long getIndex()
		{
			return this.index;
		}

		private String getType()
		{
			return this.type;
		}
	}

	public static void process(AsyncWebClient client, int state) throws Exception
	{
		try
		{
			if(client.isWebSocket())
                        {
				if(ConfManager.isWebEngineWSMsgDispatcherEnabled())
				{
					AbstractWebEngine engine = WebEngine.getEngineByAppName(client.getWebEngineName());
					engine.dispatchWSData(client, state);
					return;
				}
				else
				{
					WMSTPExecutorFactory.execute(AWSConstants.REQUEST_PROCESSOR, new Event(client, state ,AWSConstants.WRITE));
				}
                        }
                        else
                        {
                                handleHttpDomainDispatcher(client,state);
                        }
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE, "Exception in ThreadPoolExecutor : ",AsyncRequestProcessor.class.getName(),AWSLogMethodConstants.PROCESS, ex);
		}
	}	

	public static void processWSWriteAck(AsyncWebClient client, long index) throws Exception
	{
		try
		{
                        WMSTPExecutorFactory.execute(AWSConstants.REQUEST_PROCESSOR, new Event(client, index, AWSConstants.ACK));
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE, "Exception in ThreadPoolExecutor : ",AsyncRequestProcessor.class.getName(),AWSLogMethodConstants.PROCESS_WS_WRITE_ACK, ex);
		}
	}

	public static void dispatchHttp2Request(String webEngineName, HttpRequest req, HttpResponse res) throws Exception
	{
		AbstractWebEngine engine = WebEngine.getEngineByAppName(webEngineName);
		engine.dispatchHttp2Request(req, res , req.getState());
	}

	private static void handleHttpDomainDispatcher(AsyncWebClient client, int state) throws Exception
        {
                try
                {
			client.setReqQueueInsertTime(System.currentTimeMillis());
			client.updateReqProcTime(0);
			AbstractWebEngine engine = WebEngine.getEngineByAppName(client.getWebEngineName());
			HttpRequest httpreq = client.getHttpRequest(state);
			HttpResponse httpresponse = new HttpResponse(client,client.getSelectionKey(),httpreq.getRemoteAddr(), client.getClientId());

                        if(ConfManager.isDOSEnabled())
                        {
                                DOSMonitor doss = DOSManager.getDOSByEngineName(client.getWebEngineName());
                                if(doss!=null && state == StateConstants.ON_COMPLETION)
                                {
                                        if(doss.isIPBlocked(httpreq.getRemoteAddr()))
                                        {
                                                new AsyncLogger("doslogger").finer("Ignoring request from blocked ip "+httpreq.getRemoteAddr(),AsyncRequestProcessor.class.getName(),AWSLogMethodConstants.HANDLE_HTTP_DOMAIN_DISPATCHER);//No I18n
                                                client.close(AWSConstants.IP_BLOCKED);
                                                return;
                                        }
                                        else
                                        {
                                                if(doss.isSuspectedIP(httpreq.getRemoteAddr()))
                                                {
                                                        doss.doURLHit(httpreq.getRemoteAddr(),httpreq.getRequestURL());
                                                }
                                        }
                                }
                        }
                        client.updateExternalHit(httpreq.getHost());
                        engine.dispatchRequest(httpreq,httpresponse);

                }
                catch(Exception ex)
                {
                        logger.log(Level.INFO, "EXCEPTION CASE "+client.getRequestType()+","+client.getRequestURL()+","+client.getRequestVersion()+","+client.getIPAddress()+","+client.getHeaderMap()+","+client.getParamMap()+","+client.getRawBodyContent(),AsyncRequestProcessor.class.getName(),AWSLogMethodConstants.HANDLE_HTTP_DOMAIN_DISPATCHER, ex);//No I18N
                        try
                        {
                                client.close(AWSConstants.EXCEPTION_DISPATCH_REQUEST_TO_ENGINE);
                        }
                        catch(Exception exp1)
                        {
                                logger.addExceptionLog(Level.INFO, AWSLogConstants.EXCEPTION, AsyncRequestProcessor.class.getName(),AWSLogMethodConstants.HANDLE_HTTP_DOMAIN_DISPATCHER, exp1);
                        }
                }
        }

	static class WSDispatcher implements WmsTask
	{
		@Override
		public void handle(Object obj)
		{
			try
			{
				Event event = (Event) obj;
				if(event.getType() == AWSConstants.ACK)
				{
					handleWSWriteAckDispatcher(event.getClient(), event.getIndex(),event.getIntime());
				}
				else
				{
					handleWSDispatcher(event.getClient(), event.getState(),event.getIntime());
				}
			}
			catch(Exception e)
			{
				logger.log(Level.SEVERE, "Exception in DomainDispatcher : ",WSDispatcher.class.getName(),AWSLogMethodConstants.HANDLE, e);
			}
		}
	}

	private static void handleWSWriteAckDispatcher(AsyncWebClient client, long index, long starttime) throws Exception
        {
                client.updateReqProcTime(System.currentTimeMillis()-starttime);
                try
                {
                        AWSLogClientThreadLocal.setLoggingProperties(client.getReqId());
                        client.handleWSWriteAck(index);
                }
                catch(Exception ex)
                {
                        logger.log(Level.SEVERE, "[WS WRITE ACK FAILURE] "+client.getRequestType()+","+client.getRequestURL()+","+client.getParamMap()+","+client.getRequestVersion()+","+client.getIPAddress(), AsyncRequestProcessor.class.getName(),AWSLogMethodConstants.HANDLE_WS_WRITE_ACK_DISPATCHER, ex);//No I18N
                        try
                        {
                                client.close(AWSConstants.WS_WRITE_ACK_FAILURE);
                        }
                        catch(Exception exp1)
                        {
                                logger.addExceptionLog(Level.INFO, AWSLogConstants.EXCEPTION, AsyncRequestProcessor.class.getName(),AWSLogMethodConstants.HANDLE_WS_WRITE_ACK_DISPATCHER, exp1);
                        }
                }
        }

	private static void handleWSDispatcher(AsyncWebClient client, int state,long intime) throws Exception
	{
		long starttime = System.currentTimeMillis();
                long taskQueuedTime = starttime-intime;
                client.setReqQueueInsertTime(intime);
                client.updateReqProcTime(taskQueuedTime);
                try
                {
			AWSLogClientThreadLocal.setLoggingProperties(client.getReqId());
                        if(client.isWSClosed())
                        {
                                client.close(AWSConstants.CONNECTION_ALREADY_CLOSED);
                                return;
                        }

                        AbstractWebEngine engine = WebEngine.getEngineByAppName(client.getWebEngineName());
                        if(state == StateConstants.ON_PING)
                        {
                                client.handlePing();
                                return;
                        }
                        if(state == StateConstants.ON_PONG)
                        {
                                client.handlePong();
                                return;
                        }
                        engine.dispatchWSMessage(client, state);
                        return;
			/*AbstractWebEngine engine = WebEngine.getEngineByAppName(client.getWebEngineName());
			HttpRequest httpreq = client.getHttpRequest(state);
			HttpResponse httpresponse = new HttpResponse(client,client.getSelectionKey(),httpreq.getRemoteAddr(), client.getClientId());

			if(ConfManager.isDOSEnabled())
			{
				DOSMonitor doss = DOSManager.getDOSByEngineName(client.getWebEngineName());
				if(doss!=null && state == StateConstants.ON_COMPLETION)
				{
					if(doss.isIPBlocked(httpreq.getRemoteAddr()))
					{
						new AsyncLogger("doslogger").sr("Ignoring request from blocked ip "+httpreq.getRemoteAddr());//No I18n
						client.close();
						return;
					}
					else
					{
						if(doss.isSuspectedIP(httpreq.getRemoteAddr()))
						{
							doss.doURLHit(httpreq.getRemoteAddr(),httpreq.getRequestURL());
						}
					}
				}
				client.updateExternalHit(httpreq.getHost());
				engine.dispatchRequest(httpreq,httpresponse);
			}*/
		}
		catch(Exception ex)
		{
			logger.log(Level.INFO, "EXCEPTION CASE "+client.getRequestType()+","+client.getRequestURL()+","+client.getRequestVersion()+","+client.getIPAddress()+","+client.getHeaderMap()+","+client.getParamMap()+","+client.getRawBodyContent(), AsyncRequestProcessor.class.getName(),AWSLogMethodConstants.HANDLE_WS_DISPATCHER, ex);//No I18N
			try
			{
				client.close(AWSConstants.EXCEPTION_DISPATCH_WS_DATA_TO_ENGINE);
			}
			catch(Exception exp1)
			{
				logger.addExceptionLog(Level.INFO, AWSLogConstants.EXCEPTION, AsyncRequestProcessor.class.getName(),AWSLogMethodConstants.HANDLE_WS_DISPATCHER, exp1);
			}
		}
		finally
		{
			addQOSStats(client, taskQueuedTime, System.currentTimeMillis() - starttime);
		}
	}

	private static void addQOSStats(AsyncWebClient client, Long taskQueuedTime, Long processingTime)
	{
		try
		{
			if(ConfManager.isQOSEnabled())
			{
				String appname = client.getWebEngineName();
				int responseCode = client.getResponseCode();
				String uri = client.getStatsRequestURL();

				AWSInfluxStats.addAWSRequestStats(uri, responseCode, appname, "reqproc_taskqueue", taskQueuedTime, 1);//No I18n
				AWSInfluxStats.addAWSRequestStats(uri, responseCode, appname, "reqproc_processing", processingTime, 1);//No I18n
				AWSInfluxStats.addAWSRequestStats(uri, responseCode, appname, "reqproc_total", taskQueuedTime + processingTime, 1);//No I18n
			}
		}
		catch(Exception ex)
		{
		}
	}

	public static int getActivePoolSize() throws Exception
	{
		return WMSTPExecutorFactory.getActivePoolSize(AWSConstants.REQUEST_PROCESSOR);
	}

	public static int getQueueSize() throws Exception
	{
		return WMSTPExecutorFactory.getQueueSize(AWSConstants.REQUEST_PROCESSOR);
	}
}
