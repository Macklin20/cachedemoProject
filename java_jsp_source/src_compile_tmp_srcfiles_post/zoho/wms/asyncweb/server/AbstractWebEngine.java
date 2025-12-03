//$Id$
package com.zoho.wms.asyncweb.server;

import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.Enumeration;
import java.util.ArrayList;
import java.util.logging.Level;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.io.FileOutputStream;

import com.zoho.wms.asyncweb.server.util.Util;
import com.zoho.wms.asyncweb.server.util.StateConstants;
import com.zoho.wms.asyncweb.server.util.HttpResponseCode;
import com.zoho.wms.asyncweb.server.http.AbstractServlet;
import com.zoho.wms.asyncweb.server.http.HttpResponse;
import com.zoho.wms.asyncweb.server.http.HttpRequest;
import com.zoho.wms.asyncweb.server.http.HttpStream;
import com.zoho.wms.asyncweb.server.http.AsyncWebRequest;
import com.zoho.wms.asyncweb.server.http.DefaultServlet;
import com.zoho.wms.asyncweb.server.http.DownloadRefillListener;
import com.zoho.wms.asyncweb.server.http.WebSocket;
import com.zoho.wms.asyncweb.server.http.HttpStreamServlet;
import com.zoho.wms.asyncweb.server.exception.AsyncServletException;
import com.zoho.wms.asyncweb.server.exception.AWSException;
import com.zoho.wms.asyncweb.server.exception.AsyncAuthException;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogConstants;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;
import com.zoho.wms.asyncweb.server.stats.AWSInfluxStats;

import com.adventnet.wms.common.CommonUtil;
import com.adventnet.wms.common.exception.WMSException;

import com.adventnet.wms.servercommon.components.executor.WMSTPExecutorFactory;
import com.adventnet.wms.servercommon.components.executor.WmsTask;

public abstract class AbstractWebEngine
{
	private final AsyncLogger logger = new AsyncLogger(AbstractWebEngine.class.getName());
	private static AsyncLogger msgtracelogger = new AsyncLogger("msgtracelogger");//No I18n
	private static AsyncLogger msgfilterlogger = new AsyncLogger("msgfilterlogger");//No I18n
	private int corethreadcount;
	private int maxthreadcount;
	protected String appname;
	public Map<String,String> appinfo = new HashMap();
	private final HashMap<String, AbstractServlet> servletMap = new HashMap();
	private ArrayList keepAliveUrls = new ArrayList();
	protected AbstractServlet baseServlet ;
	private DownloadRefillListener refilllistener;
	
	private String SERVLET_PROPFILE;
	private String APP_PROPFILE;
	private String tpeName;
	private boolean initialized = false;
	private boolean wstpeInitialized = false;
	private String wsmsgThreadName;
	private String invalidRequestTpeName;
	private boolean isInvalidRequestTpeInitialized = false;
	private int engineMaxThreadCreationLimit = -1;

	private int readlimit = -1;
	private int writelimit = -1;
	private int keyactiveselectiontimeout = -1;
	private int httpreadselectorcnt = -1;
	private int httpsreadselectorcnt = -1;
	private ArrayList supportedgetmethods = null;
	private ArrayList supportedpostmethods = null;

	/**
	 * To initialize an webengine
	 * @param appname - name of the webengine
	 * @param corethreadcount - initial size of thread pool allocated for processing request for this engine
	 * @param maxthreadcount - maximum size of the thread pool
	 */
	
	public final void doInit(String appname,int corethreadcount,int maxthreadcount, long kaTime, int tpexecutor,int queueSize,boolean rejectionHandler) throws Exception
	{
		this.appname = appname;
		APP_PROPFILE = ConfManager.getServerHome()+File.separator+"webengine"+File.separator+appname+File.separator+"conf"+File.separator+"app.properties";
		SERVLET_PROPFILE = ConfManager.getServerHome()+File.separator+"webengine"+File.separator+appname+File.separator+"conf"+File.separator+"servlet.properties";

		tpeName = AWSConstants.WEBENGINE+appname;
		initAppProperties(); 
		manageThreadPoolExecutor(appname, corethreadcount, maxthreadcount, kaTime, tpexecutor, queueSize, rejectionHandler);
		initWSMsgThreadPool();
		loadServlets();
		initDownloadRefillListener();
		logger.log(Level.INFO,"APP NAME "+appname+" [PROPS] "+appinfo,AbstractWebEngine.class.getName(),AWSLogMethodConstants.DOINIT);//No I18N

		if(appinfo.get("readlimit") != null)
		{
			readlimit = Integer.parseInt(appinfo.get("readlimit"));
		}
		if(appinfo.get("writelimit") != null)
		{
			writelimit = Integer.parseInt(appinfo.get("writelimit"));
		}
		if(appinfo.get("httpreadselector") != null)
		{
			httpreadselectorcnt = Integer.parseInt(appinfo.get("httpreadselector"));
		}
		if(appinfo.get("httpsreadselector") != null)
		{
			httpsreadselectorcnt = Integer.parseInt(appinfo.get("httpsreadselector"));
		}
		if(appinfo.get("supportedgetmethods") != null)
		{
			supportedgetmethods = new ArrayList();
			supportedgetmethods = CommonUtil.getList(appinfo.get("supportedgetmethods"));
		}
		if(appinfo.get("supportedpostmethods") != null)
		{
			supportedpostmethods = new ArrayList();
			supportedpostmethods = CommonUtil.getList(appinfo.get("supportedpostmethods"));
		}
		if(appinfo.get("keyactiveselecttimeout") != null)
		{
			keyactiveselectiontimeout = Integer.parseInt(appinfo.get("keyactiveselecttimeout"));
		}

		try
		{
			baseServlet = (AbstractServlet)Class.forName(ConfManager.getDefaultServletURL()).newInstance();
			baseServlet.doInit(Collections.unmodifiableMap(appinfo));
		}
		catch(Exception ex)
		{
			logger.log(Level.INFO, " Exception in base servlet init : "+appname,AbstractWebEngine.class.getName(),AWSLogMethodConstants.DOINIT, ex);
		}
		
		loadTpeForInvalidRequest();
	}

	private void loadTpeForInvalidRequest()
	{
		try
		{
			boolean isInvalidRequestTpeEnabled = Boolean.parseBoolean(appinfo.getOrDefault("invalidrequesttpe", "false"));
			if(isInvalidRequestTpeEnabled)
			{

				invalidRequestTpeName = tpeName+AWSConstants.THREAD_NAME_SEPARATOR+"invalidrequesttpe";
				int minThreadCount = Integer.parseInt(appinfo.getOrDefault("invalidrequestthreadcount", "5"));
				int maxThreadCount = Integer.parseInt(appinfo.getOrDefault("maxinvalidrequestthreadcount", "10"));
				long kaTime = Long.parseLong(appinfo.getOrDefault("invalidrequestkatime", "100"));
				int executorCount = Integer.parseInt(appinfo.getOrDefault("invalidrequestexecutorcount", "1"));
				int queueSize = Integer.parseInt(appinfo.getOrDefault("invalidrequestqueuesize", "-1"));
				boolean rejectionHandler = Boolean.parseBoolean(appinfo.getOrDefault("invalidrequestrejectionhandler", "false"));
				int maxThreadCreationLimit = Integer.parseInt(appinfo.getOrDefault("invalidrequestmaxthreadcreationlimit", "-1"));
				maxThreadCount = Util.getMaxThreadCount(minThreadCount, maxThreadCount);
				boolean status = false;
				if(!isInvalidRequestTpeInitialized)
				{
					maxThreadCreationLimit = Util.getMaxThreadCreationLimit(maxThreadCreationLimit, maxThreadCount);
					status = WMSTPExecutorFactory.createNewExecutor(invalidRequestTpeName, minThreadCount, maxThreadCount, (int)kaTime, new Dispatcher(), maxThreadCreationLimit);
					
				}
				else
				{
					maxThreadCreationLimit = Util.getMaxThreadCreationLimit(maxThreadCreationLimit, maxThreadCount);
					status = WMSTPExecutorFactory.updateExecutor(invalidRequestTpeName, minThreadCount, maxThreadCount, (int)kaTime, maxThreadCreationLimit);
				}
				isInvalidRequestTpeInitialized = true;
				logger.log(Level.INFO, "Separate TPE for invalidRequest init successfully :: status:"+status,AbstractWebEngine.class.getName(),AWSLogMethodConstants.LOAD_TPE_FOR_INVALID_REQUEST);
			}
			else if(isInvalidRequestTpeInitialized)
			{
				isInvalidRequestTpeInitialized = false;
				WMSTPExecutorFactory.shutdown(invalidRequestTpeName);
				logger.log(Level.INFO, "invalidrequest TPE shutdown successfully",AbstractWebEngine.class.getName(),AWSLogMethodConstants.LOAD_TPE_FOR_INVALID_REQUEST);
			}
		}
		catch(Exception e)
		{
			logger.log(Level.SEVERE, "Exception while loading TPE for invalidRequest",AbstractWebEngine.class.getName(),AWSLogMethodConstants.LOAD_TPE_FOR_INVALID_REQUEST, e);
		}
	}

	private void initWSMsgThreadPool()
	{
		if(!ConfManager.isWebEngineWSMsgDispatcherEnabled())
		{
			return;
		}
		try
		{
			int wsmsgthreadcount = 10;
			try
			{
				wsmsgthreadcount = Integer.parseInt((String)appinfo.get("wsmsgthreadcount"));
			}
			catch(Exception exp)
			{
			}

			int wsmsgmaxthreadcount = 10;
			try
			{
				wsmsgmaxthreadcount = Integer.parseInt((String)appinfo.get("wsmsgmaxthreadcount"));
			}
			catch(Exception exp)
			{
			}

			long wsmsgkatime = 100L;
			try
			{
				wsmsgkatime = Long.parseLong((String)appinfo.get("wsmsgkatime"));
			}
			catch(Exception exp)
			{
			}

			int wsmsgexecutorcount = 1;
			try
			{
				wsmsgexecutorcount = Integer.parseInt((String)appinfo.get("wsmsgexecutorcount"));
			}
			catch(Exception exp)
			{
			}

			int wsmsgqueuesize = -1;
			try
			{
				wsmsgqueuesize = Integer.parseInt((String)appinfo.get("wsmsgqueuesize"));
			}
			catch(Exception exp)
			{
			}

			boolean wsmsgrejectionhandler = false;
			try
			{
				wsmsgrejectionhandler = Boolean.parseBoolean((String)appinfo.get("wsmsgrejectionhandler"));
			}
			catch(Exception exp)
			{
			}

			int wsMsgMaxThreadCreationLimit = -1;
			try
			{
				wsMsgMaxThreadCreationLimit = Integer.parseInt((String)appinfo.get("wsmsgmaxthreadcreationlimit"));
			}
			catch(Exception e)
			{
			}

			wsmsgThreadName = tpeName+AWSConstants.THREAD_NAME_SEPARATOR+AWSConstants.WSMSGTHREAD;
			int maxWSMSGThreadCount = Util.getMaxThreadCount(wsmsgmaxthreadcount, wsmsgthreadcount);
			int maxThreadCreationLimit = Util.getMaxThreadCreationLimit(wsMsgMaxThreadCreationLimit, maxWSMSGThreadCount);
			if(wstpeInitialized)
			{
				WMSTPExecutorFactory.updateExecutor(wsmsgThreadName, wsmsgthreadcount, maxWSMSGThreadCount, (int)wsmsgkatime, maxThreadCreationLimit);
			}
			else
			{
				WMSTPExecutorFactory.createNewExecutor(wsmsgThreadName, wsmsgthreadcount, maxWSMSGThreadCount, (int)wsmsgkatime, new WebsocketMsgDispatcher(), maxThreadCreationLimit);
			}
			wstpeInitialized = true;
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE, "Exception in initWSMsgThreadPool : "+appname,AbstractWebEngine.class.getName(),AWSLogMethodConstants.INIT_WS_MSG_THREADPOOL, ex);//No I18n
		}
	}

	private boolean manageThreadPoolExecutor(String appname, int corethreadcount, int maxthreadcount, long kaTime, int tpexecutor,int queueSize,boolean rejectionHandler)
	{
		try
		{
			int maxWSMSGThreadCount = Util.getMaxThreadCount(maxthreadcount, corethreadcount);
			if(!initialized)
			{
				int maxThreadCreationLimit = Util.getMaxThreadCreationLimit(engineMaxThreadCreationLimit, maxWSMSGThreadCount);
				WMSTPExecutorFactory.createNewExecutor(tpeName, corethreadcount, maxWSMSGThreadCount, (int)kaTime, new Dispatcher(), maxThreadCreationLimit);
				initialized = true;
				this.corethreadcount = corethreadcount;
				this.maxthreadcount = maxWSMSGThreadCount;
			}
			else if((this.corethreadcount != corethreadcount && corethreadcount >=0) || (this.maxthreadcount != maxWSMSGThreadCount))
			{
				int maxThreadCreationLimit = Util.getMaxThreadCreationLimit(engineMaxThreadCreationLimit, maxWSMSGThreadCount);
				WMSTPExecutorFactory.updateExecutor(tpeName, corethreadcount, maxWSMSGThreadCount, (int)kaTime, maxThreadCreationLimit);
				this.corethreadcount = corethreadcount;
				this.maxthreadcount = maxWSMSGThreadCount;
			}
			return true;
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE, "Exception in ThreadPool init/update [APPNAME] "+appname+" [INIT] "+initialized,AbstractWebEngine.class.getName(),AWSLogMethodConstants.MANAGE_THREADPOOL_EXECUTOR,ex);//No I18n
			return false;
		}
	}

	private void initAppProperties()
	{
		String appHome = ConfManager.getServerHome()+File.separator+"webengine"+File.separator+appname;
		String docroot = ConfManager.getServerHome()+File.separator+"webengine"+File.separator+appname+File.separator+"webapps"+File.separator+"ROOT"+File.separator;
		appinfo.put("app.name",appname);
		appinfo.put("app.home",appHome);
		appinfo.put("app.docroot",docroot);	
		appinfo.put(AWSConstants.CACHE, ""+ConfManager.isCacheEnabled());
		appinfo.put("ignorefilelist","favicon.ico,_app/health");
		appinfo.put("tkpticketcookie","x-tkp-token");
		appinfo.put("tkpdomaincookie","x-tkp-domain");
		initializeStaticFileList();
		loadAppProperties();
		loadAppConfs();
	}

	private void loadAppProperties()
	{
		Properties appprops = Util.getProperties(APP_PROPFILE);
		if(appprops != null)
		{
			Enumeration en = appprops.propertyNames();
			while(en.hasMoreElements())
			{
				String key = (String)en.nextElement();
				if(appinfo.get(key)==null || !key.startsWith("app."))
				{
					String value = appprops.getProperty(key);
					appinfo.put(key,value);
				}
			}	
		}
	}

	private void loadAppConfs()
	{
		try
		{
			engineMaxThreadCreationLimit = Integer.parseInt(appinfo.getOrDefault("maxthreadcreationlimit", ""+engineMaxThreadCreationLimit));
		}
		catch(Exception e)
		{
			logger.log(Level.SEVERE, "Exception while loading AppConfs : ",AbstractWebEngine.class.getName(),AWSLogMethodConstants.LOAD_APPCONF, e);
		}
	}

	private void initDownloadRefillListener()
	{
		try
		{
			String refillprops = ConfManager.getServerHome()+File.separator+"webengine"+File.separator+appname+File.separator+"conf"+File.separator+"downloadrefill.properties";//No I18N
			Properties refillpropsobj = Util.getProperties(refillprops);
			refilllistener = (DownloadRefillListener)(Class.forName(refillpropsobj.getProperty("listener")).newInstance());
		}
		catch(Exception ex)
		{
		}
	}

	public DownloadRefillListener getDownloadRefillListener()
	{
		return refilllistener;
	}

	
	/**
         * This method is used to set keepalive status of the engine
	 * @param status - true, if enabled
	 *                 false, if disabled
	 */	

	public void setKeepAlive(boolean status)
	{
		appinfo.put(AWSConstants.KEEPALIVE,""+status);
	}

	/** 
	 * To load base servlet of the engine
	 */
	
	public void reinitBaseServlet()
	{
		try
		{
			baseServlet.doInit(Collections.unmodifiableMap(appinfo));
		}
		catch(Exception ex)
		{
			logger.log(Level.INFO, " Exception in reinitBaseServlet : "+appname,AbstractWebEngine.class.getName(),AWSLogMethodConstants.REINIT_BASE_SERVLET, ex);//No I18n
		}
	}

	/**
	 * To load servlet associated with the webengine
	 */
	
	public void loadServlets()
	{
		Properties servletprops = Util.getProperties(SERVLET_PROPFILE);
		if(appname.equals(AWSConstants.GRID_APPNAME))
		{
			Properties gridServletProp = Util.getProperties(ConfManager.getServerHome()+File.separator+"webengine"+File.separator+appname+File.separator+"conf"+File.separator+"servlet-grid.properties");
			if(gridServletProp!=null)
			{
				servletprops.putAll(gridServletProp);
			}
		}
		Enumeration en = servletprops.propertyNames();
		while(en.hasMoreElements())
		{
			String servletName = (String) en.nextElement();
			String servletClass = (String) servletprops.getProperty(servletName).split(",")[0];
			try
			{
				AbstractServlet servlet = (AbstractServlet) Class.forName(servletClass).newInstance();
				servlet.doInit(Collections.unmodifiableMap(appinfo));
				servletMap.put(servletName,servlet);
			}
			catch(Exception exp)
			{
				logger.log(Level.INFO, "Unable to load servlet - "+servletName+" class - "+servletClass+" - ",AbstractWebEngine.class.getName(),AWSLogMethodConstants.LOAD_SERVLETS, exp);
			}

			try
			{
				if(servletprops.getProperty(servletName).split(",")[1].equals(AWSConstants.TRUE))
				{
					keepAliveUrls.add(servletName);
				}
			}
			catch(Exception exp)
			{
			}
		}
		logger.log(Level.INFO, "AWS -> Keepalive enabled urls : "+keepAliveUrls,AbstractWebEngine.class.getName(),AWSLogMethodConstants.LOAD_SERVLETS);//No I18n
	}

	public void refreshStaticFileList()
	{
		initializeStaticFileList();
		reinitBaseServlet();
	}

	public void initializeStaticFileList()
	{
		String staticFiles = CommonUtil.getString(accumulateStaticFileList(appinfo.get("app.docroot")));
		appinfo.put("app.allowedstaticfiles",staticFiles);
	}

	private ArrayList accumulateStaticFileList(String path)
	{
		ArrayList fileList = new ArrayList();
		accumulateStaticFiles(path,fileList);
		return fileList;
	}

	private void accumulateStaticFiles(String path,ArrayList fileList)
	{
		File[] fileArray = new File(path).listFiles();

		for (File file : fileArray)
		{
			if (file.isFile())
			{
				fileList.add(file.getPath());
			}
			else if (file.isDirectory())
			{
				accumulateStaticFiles(file.getPath(),fileList);
			}
		}
	}

	/** 
	 * To get the name of the engine
	 * @return - engine name
	 */

	public String getAppName()
	{	
		return this.appname;
	}
	
	/**
	 * To get default token pair ticket cookie name
	 */
 
	public String getTKPTicketCookie()
	{
		return (String)appinfo.get("tkpticketcookie");
	}

	/** 
	 * To get default token pair domain cookie name
	 */

	public String getTKPDomainCookie()
	{
		return (String)appinfo.get("tkpdomaincookie");
	}
	
	/**
	 * To check if dos blocking is enabled
	 * @return - true-enabled, false-not enabled
	 */
	
	public boolean isBlockEnabled()
	{
		if(appinfo!=null && appinfo.get("dosblock")!=null)
		{
			return Boolean.valueOf((String)appinfo.get("dosblock"));
		}
		return false;
	}

	/**
	 * To check if default ws read type requested
	 * @return - true-enabled, false-not enabled
	 */
	
	public boolean isDefaultWSRead()
	{
		if(appinfo!=null && appinfo.get("defaultwsread")!=null)
		{
			return Boolean.valueOf((String)appinfo.get("defaultwsread"));
		}
		return false;
	}
	
	/**
	 * To check if dos monitoring is enabled
	 * @return - true-enabled, false-not enabled
	 */
	
	public boolean isMonitorEnabled()
	{
		if(appinfo!=null && appinfo.get("dosmonitor")!=null)
                {
		        return Boolean.valueOf((String)appinfo.get("dosmonitor"));
                }
                return false;
	}

	/**
	 * To get property file under a webengine conf
	 * @param file - filename
	 * @return - property file
	 */

	public Properties getProperties(String file)
	{
		if(!file.endsWith(".properties"))
		{
			file = file+".properties";
		}
		
		try
		{
			Properties props = new Properties();
			props.load(new FileInputStream(ConfManager.getServerHome()+File.separator+"webengine"+File.separator+appname+File.separator+"conf"+File.separator+file));
			return props;
		}
		catch(Exception ex)
		{
			logger.addExceptionLog(Level.INFO, AWSLogConstants.EXCEPTION,AbstractWebEngine.class.getName(),AWSLogMethodConstants.GET_PROPERTIES, ex);
			return null;
		}
		
	}


	/**
	 * This function will be called at the time of webengine initialization
	 */
	
	public abstract void initializeEngine() throws Exception;

	private AbstractServlet getServlet(String reqUrl) throws Exception
	{
		AbstractServlet servlet = servletMap.get(reqUrl);
		if(servlet == null)
		{
			return baseServlet;
		}
		return (servlet instanceof WebSocket)?servlet.getClass().newInstance():servlet;
	}

	/**
	 * To get the servlet name associated with the url - can be ussed incase of dynamic URI
	 * @return - servlet name
	 */

	public String getServletURI(String reqUrl)
	{
		return reqUrl;
	}

	public boolean isStreamServlet(String reqUrl)
	{
		if(servletMap.get(reqUrl) instanceof HttpStreamServlet)
		{
			return true;
		}
		return false;
	}

	/**
	 * To get the internal servlet associated with the url
	 * @param reqUrl - url for the servlet
	 * @return - internal servlet name
	 */

	public String getInternalServletURI(String reqUrl)
	{
		return reqUrl;
	}

	/**
	 * To get the default servlet for processing unconfigured url 
	 * @return - default servlet
	 */
	
	public DefaultServlet getBaseServlet()
	{
		return ((DefaultServlet)baseServlet);
	}
	
	/**
	 * To check if security filter is enabled for the engine
	 * @return - true , if enabled
	 * 	     false, if not
	 */
	 
	 public boolean isSecurityFilterEnabled()
	 {
		if(appinfo.get(AWSConstants.SECURITYFILTER)!=null && appinfo.get(AWSConstants.SECURITYFILTER).equals(AWSConstants.TRUE))
		{
			 return true;
		}

		return false;
	 }

	/** 
	 * To set security filter for the engine
	 */

	public void setSecurityFilter(boolean status)
	{
		appinfo.put(AWSConstants.SECURITYFILTER,""+status);
	}

	/**
         * To check if Async Frame Processing is enabled for the engine
         * @return - true , if enabled
         *           false, if not
         */

	public boolean isAsyncFrameProcEnabled()
	{
		if(appinfo.get(AWSConstants.ASYNCFRAMEPROC)!=null && appinfo.get(AWSConstants.ASYNCFRAMEPROC).equals(AWSConstants.TRUE))
		{
			return true;
		}
		return false;
	}

	/**
         * To set Async Frame Processing for the engine
         */

	public void setAsyncFrameProc(boolean status)
	{
		appinfo.put(AWSConstants.ASYNCFRAMEPROC,""+status);
	}

	public boolean doSecurityValidation(HttpRequest request) throws Exception
	{
		return doSecurityValidation(request, null);
	}

	public boolean doSecurityValidation(HttpRequest request, HttpResponse response) throws Exception
	{
		if(ConfManager.isSecurityFilterEnabled() && isSecurityFilterEnabled())
		{
			return AWSSecurityFilter.verifyRequest(request, response, appname);
		}
		return true;
	}

	/*private boolean isURLEncodedContentType(HttpRequest request)
	{
		String contentType = request.getHeader(AWSConstants.CONTENT_TYPE);
		if(contentType == null)
		{
			return false;
		}
		return contentType.contains(AWSConstants.APPLICATION_X_WWW_URLENCODED);
	}*/

	private final boolean doHttpFilter(HttpRequest request,HttpResponse response, int req_state) throws Exception
	{
		if(req_state == StateConstants.ON_HEADER_COMPLETION || req_state == StateConstants.ON_COMPLETION || req_state == StateConstants.ON_DATA)
		{
			return doFilter(request, response , req_state);
		}
		return true;
	}

	/**
	 * This function will be called at time of filtering request and incase of overriding this function can be used to get the state of the request
	 * @param request - request to be filtered
	 * @param response - response for the current request
	 * @param req_state - can be used to get the state of the request
	 * @return - true-proceed, false-dont proceed further
	 * the request state might not be correct when req.getState() in http2 is called due to high frequency of callbacks
	 */

	public boolean doFilter(HttpRequest request,HttpResponse response, int req_state) throws Exception
	{
		return doFilter(request,response);
	}

	/**
	 * This function will be called at time of filtering request
	 * @param request - request to be filtered
	 * @param response - response for the current request
	 * @return - true-proceed, false-dont proceed further
	 */

	public boolean doFilter(HttpRequest request,HttpResponse response) throws Exception
	{
		return doSecurityValidation(request);	
	}

	public void doCleanUp()
	{
		if(ConfManager.isSecurityFilterEnabled() && isSecurityFilterEnabled())
		{
			AWSSecurityFilter.cleanUp();
		}
	}

	/**
	 * This function will be called to filter web socket request 
	 * @param request - request to be filtered
	 * @param response - response for the current request
	 * @param message - websocket payload 
	 * @return - true-proceed, false-dont proceed further
	 */
	
	public boolean doFilterWSMessage(HttpRequest request, HttpResponse response, String message)
	{
		return true;
	}

	/**
	 * This function will be called to filter web socket request 
	 * @param request - request to be filtered
	 * @param response - response for the current request
	 * @param message - websocket payload 
	 * @return - true-proceed, false-dont proceed further
	 */
	
	public boolean doFilterWSMessage(HttpRequest request, HttpResponse response, byte[] message)
	{
		return true;
	}

	/**
	 * This method will be called to dispatch request
	 * @param request - request from client
	 * @param response -response for the client
	 */
	
	public final void dispatchRequest(HttpRequest request,HttpResponse response) throws Exception
	{

		if(ConfManager.isKeepAliveEnabled() && (request.getHeader(AWSConstants.CONNECTION)!=null && request.getHeader(AWSConstants.CONNECTION).equalsIgnoreCase(AWSConstants.KEEP_ALIVE)) && ((appinfo.get(AWSConstants.KEEPALIVE)!=null && appinfo.get(AWSConstants.KEEPALIVE).equals(AWSConstants.TRUE)) || keepAliveUrls.contains(response.getStatsRequestURL())) && !response.isKeepAliveEnabled())
		{
			response.enableKeepAlive();
			if(appinfo.get(AWSConstants.KEEPALIVETIMEOUT)!=null)
			{
				response.setKeepAliveTimeout(Long.parseLong(appinfo.get(AWSConstants.KEEPALIVETIMEOUT)));
			}
		}
	
		try
		{
			if(isInvalidRequestTpeInitialized && !isURLPresent(response.getStatsRequestURL()))
			{
				WMSTPExecutorFactory.execute(invalidRequestTpeName, new DispatcherEvent(new AsyncWebRequest(request,response),request.getState()));
			}
			else
			{
				WMSTPExecutorFactory.execute(tpeName, new DispatcherEvent(new AsyncWebRequest(request,response),request.getState()));
			}
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE," Exception in DispatchRequest : ",AbstractWebEngine.class.getName(),AWSLogMethodConstants.DISPATCH_REQUESTS,ex);
		}

	}

	public void dispatchHttp2Request(HttpRequest request, HttpResponse response, int state)
	{
		WMSTPExecutorFactory.execute(tpeName, new DispatcherEvent(new AsyncWebRequest(request,response),state));
	}

	public boolean isURLPresent(String url)
	{
		try
		{
			return servletMap.containsKey(url) || isAllowedStaticFile(url);
		}
		catch(Exception ex)
		{
			return false;
		}
	}

	private boolean isAllowedStaticFile(String filename) throws Exception
	{
		File file = new File(appinfo.get("app.docroot"),filename);
		ArrayList staticFileList = CommonUtil.getList(appinfo.get("app.allowedstaticfiles"));//No I18n
		ArrayList ignoreFileList = CommonUtil.getList(appinfo.get("ignorefilelist"));//No I18n
		return staticFileList.contains(file.getPath())||ignoreFileList.contains(filename) ;
	}

	/**
	 * To get message queue for a session from a previous Rserver
	 * @param wnet - previours rserver domain or ip
	 * @param sid - session id
	 */
	
	public void getMessageQueueFromPrevServer(String wnet, String sid) throws WMSException
	{
	}
	
	/**
	 * To rebuild Rserver
	 */
	
	public void rebuild() throws Exception
	{
	}

	/**
	 * To get total no of session in use
	 * @return - session count
	 */
	
	public int getSessionCount()
	{
		return 0;
	} 

	/**
	 * To update websocket servlet associated with the request
	 * @param req - request from the client
	 * @param wsservlet - servlet
	 */
	
	public void updateWSListenerFactory(HttpRequest req, WebSocket wsservlet)
	{
		if(!ConfManager.isWSPrdListenerFactoryEnabled())
		{
			return;
		}
		String prd = req.getWSPrd();
		int type = req.getWSType();
		String ip = req.getWnetAddress();
		//WSListenerFactory.add(prd+"_"+ip,type,wsservlet);
		String ukey = prd+"_"+ip;
		WSListenerFactory.add(ukey,type,wsservlet);
	}

	/**
	 * To check if Websocket Threadsafe is enabled for the engine
	 */

	public boolean isWSServletThreadSafe()
	{
		if(appinfo.get("wsservletthreadsafe")!=null && appinfo.get("wsservletthreadsafe").equals(AWSConstants.FALSE))
		{
			return false;
		}
		return true;
	}

	private static class DispatcherEvent implements Serializable
	{
		private AsyncWebRequest request = null;
		private long intime;
		private int state;

		private DispatcherEvent(AsyncWebRequest request, int state)
		{
			this.request = request;
			this.intime = System.currentTimeMillis();
			this.state = state;
		}

		private AsyncWebRequest getRequest()
		{
			return this.request;
		}

		private long getIntime()
		{
			return this.intime;
		}

		private int getState()
		{
			return this.state;
		}
	}

	private class Dispatcher implements WmsTask
	{
		@Override
		public void handle(Object obj)
		{
			try
			{
				DispatcherEvent event = (DispatcherEvent) obj;
				handleServletDispatcher(event.getRequest(), event.getIntime(),event.getState());
			}
			catch(Exception e)
			{
				logger.log(Level.SEVERE, "Exception in Dispatcher : ",AbstractWebEngine.class.getName(),AWSLogMethodConstants.HANDLE, e);
			}
		}
	}

	private void handleServletDispatcher(AsyncWebRequest req, long intime, int state) throws Exception
	{
		long servlettime = 0;
		int servletcount = 0;
		long ondatatime = 0l;
		int ondatacount = 0;
		long start = System.currentTimeMillis();
		long taskQueuedTime = start - intime;
		req.getResponse().updateServletDispatcherTime(taskQueuedTime);
		String threadid = Thread.currentThread().getName();
		AWSLogClientThreadLocal.setLoggingProperties(req.getResponse().getReqId());
		try
		{
			req.getRequest().getSyncLock().lock();
			if(req.getResponse().isSecurityFilterDisabled() || doHttpFilter(req.getRequest(),req.getResponse(), state))
			{
				String url = req.getResponse().getStatsRequestURL();
				if(req.getRequest().isWebSocket())
				{
					if(state==StateConstants.ON_COMPLETION)
					{
						AbstractServlet wsservlet = getServlet(url);
						req.getResponse().setWebSocket((WebSocket)wsservlet, req.getRequest().isCompressionEnabled(),req.getRequest().getCompressionDetails());
						updateWSListenerFactory(req.getRequest(),(WebSocket)wsservlet);
						req.getResponse().startInstrumentation(req.getRequest());
						long servletIntime = System.currentTimeMillis();
						try
						{
							if(ConfManager.isWSOffloaderEnabled())
							{
								((WebSocket)wsservlet).verifyToUpgrade(req.getRequest(),req.getResponse());
							}
							else
							{
								wsservlet.service(req.getRequest(),req.getResponse());
							}
						}
						catch(Exception ex)
						{
						}
						finally
						{
							req.getResponse().addServletTimeTaken(System.currentTimeMillis()-servletIntime);
							req.getResponse().finishInstrumentation(req.getRequest());
						}
					}
					return;
				}

				if(req.getRequest().getRequestType().toLowerCase().equals(AWSConstants.OPTIONS))
				{
					if(state == StateConstants.ON_COMPLETION)
					{
						req.getResponse().sendOptionsResponse(req.getRequest().getHeader(AWSConstants.ORGIN),req.getRequest().getHeader(AWSConstants.ACCESS_CONTROL_REQUEST_HEADERS));
						req.getResponse().close();
					}
					return;
				}
				switch(state)
				{
					case StateConstants.ON_HEADER_COMPLETION:
					{
						long starttime = System.currentTimeMillis();
						long timeinterval = System.currentTimeMillis()-starttime;
						getServlet(url).onHeaderCompletion(req.getRequest(),req.getResponse());
						msgTracePrint(req.getRequest(),"ON HEADER COMPLETION",starttime,timeinterval);//No I18N
						HttpStream httpstream = req.getRequest().getHttpStream();
						if(httpstream != null)
						{
							httpstream.addToNotificationTime(starttime-httpstream.resetHeaderThreadStartTime());
							httpstream.addToNotificationProcTime(timeinterval);
						}
					}
					break;
					case StateConstants.ON_DATA:
					{
						long starttime = System.currentTimeMillis();
						getServlet(url).onData(req.getRequest(),req.getResponse());
						ondatatime = System.currentTimeMillis()-starttime;
						req.getResponse().addServletTimeTaken(ondatatime);
						ondatacount = 1;
						msgTracePrint(req.getRequest(),"ON DATA",starttime,ondatatime);//No I18N
						HttpStream httpstream = req.getRequest().getHttpStream();
						httpstream.addToNotificationTime(starttime-httpstream.resetDataThreadStartTime());
						httpstream.addToNotificationProcTime(ondatatime);
						try
						{
							if(httpstream.isFinished())
							{
								msgtracelogger.log(Level.INFO,"STREAM "+httpstream.getRequestURL()+" DATA SIZE "+httpstream.getTotalDataSize()+" STATS "+httpstream.getCompleteStats(),AbstractWebEngine.class.getName(),AWSLogMethodConstants.HANDLE_SERVLET_DISPATHER);
							}
							else
							{
								httpstream.resume();
								if(httpstream.isAvailable())
								{
									req.getResponse().invokeNotification(StateConstants.ON_DATA);
								}
							}
						}
						catch(Exception ex)
						{
						}
					}
					break;
					case StateConstants.ON_OUTPUTBUFFERREFILL:
					{
						long starttime = System.currentTimeMillis();
						if(getDownloadRefillListener() != null)
						{
							getDownloadRefillListener().onOutputBufferRefill(req.getRequest(), req.getResponse());
						}
						else
						{
							getServlet(url).onOutputBufferRefill(req.getRequest(),req.getResponse());
						}
						msgTracePrint(req.getRequest(),"ON OUTPUT BUFFERRE FILL",starttime,System.currentTimeMillis()-starttime);//No I18N
					}	
					break;
					case StateConstants.ON_WRITEFAILURE:
					{
						long starttime = System.currentTimeMillis();
						getServlet(url).onWriteFailure(req.getRequest(),req.getResponse());
						msgTracePrint(req.getRequest(),"ON WRITE FAILURE",starttime,System.currentTimeMillis()-starttime);//No I18N
					}	
					break;
					case StateConstants.ON_WRITECOMPLETE:
					{
						long starttime = System.currentTimeMillis();
						getServlet(url).onWriteComplete(req.getRequest(),req.getResponse());
						msgTracePrint(req.getRequest(),"ON WRITE COMPLETE",starttime,System.currentTimeMillis()-starttime);//No I18N
					}
					break;
					default:
					{
						req.getResponse().startInstrumentation(req.getRequest());
						req.getResponse().startTimeLine(AWSConstants.TL_SERVLET_TIME);
						long starttime = System.currentTimeMillis();
						getServlet(url).service(req.getRequest(),req.getResponse());
						servlettime = System.currentTimeMillis()-starttime;
						req.getResponse().addServletTimeTaken(servlettime);
						req.getResponse().printEndAWSAccessLog();
						servletcount = 1;
						req.getResponse().endTimeLine(AWSConstants.TL_SERVLET_TIME);
						msgTracePrint(req.getRequest(),"ON SERVICE",starttime,servlettime);//No I18N
						AWSInfluxStats.addHttpRequestStats(req.getRequest().getRequestType(), req.getRequest().getScheme(), req.getRequest().getVersion());	
					}	
					break;
				}
			}
			else
			{
				logger.addDebugLog(Level.INFO, AWSLogConstants.DO_FILTER_FALSE, AbstractWebEngine.class.getName(),AWSLogMethodConstants.HANDLE_SERVLET_DISPATHER, new Object[]{req.getRequest()});
				req.getResponse().sendError(HttpResponseCode.INTERNAL_SERVER_ERROR,HttpResponseCode.INTERNAL_SERVER_ERROR_MSG);
				req.getResponse().close();
			}
				
		}
		catch (NullPointerException npe)   
		{
			logger.log(Level.SEVERE, "Exception - NPE --> req : " + req.getRequest(),AbstractWebEngine.class.getName(),AWSLogMethodConstants.HANDLE_SERVLET_DISPATHER, npe);

			try
			{
				req.getResponse().sendError(HttpResponseCode.INTERNAL_SERVER_ERROR,HttpResponseCode.INTERNAL_SERVER_ERROR_MSG);
				req.getResponse().close();
			}
			catch(Exception exp)
			{
			}
		}
		catch(AWSException awsex)
		{
			logger.log(Level.SEVERE, "Exception --> req : " + req.getRequest(),AbstractWebEngine.class.getName(),AWSLogMethodConstants.HANDLE_SERVLET_DISPATHER, awsex);
			try
			{
				req.getResponse().sendError(HttpResponseCode.INTERNAL_SERVER_ERROR,awsex.getMessage());
				req.getResponse().close();
			}
			catch(Exception exp)
			{
			}
		}
		catch(Exception ex)
		{
			logger.log(Level.WARNING, "[GENERALERROR] "+req.getRequest(),AbstractWebEngine.class.getName(),AWSLogMethodConstants.HANDLE_SERVLET_DISPATHER, ex);
			try
			{
				req.getResponse().sendError(HttpResponseCode.INTERNAL_SERVER_ERROR,HttpResponseCode.INTERNAL_SERVER_ERROR_MSG); 
				req.getResponse().close();
			}
			catch(Exception exp)
			{
			}
		}
		finally
		{
			try
			{
				req.getRequest().getSyncLock().unlock();
				req.getResponse().finishInstrumentation(req.getRequest());
			}
			catch(Exception ex)
			{
			}
			addEngineQOSStats(req, taskQueuedTime, System.currentTimeMillis() - start, servlettime, ondatatime, servletcount, ondatacount);
			try
			{
				doCleanUp();
			}
			catch(Exception ex)
			{
			}
			Thread.currentThread().setName(threadid);
		}
	}

	protected void addEngineQOSStats(AsyncWebRequest req, Long taskQueuedTime, Long processingTime, Long servlettime, Long ondatatime, Integer servletcount, Integer ondatacount)
	{
		try
		{
			if(ConfManager.isQOSEnabled())
			{
				AWSInfluxStats.addAWSRequestStats(req.getResponse().getStatsRequestURL(), req.getResponse().getResponseCode(), appname, AWSConstants.WEBENGINE_TASKQUEUE, taskQueuedTime, 1);//No I18n
				AWSInfluxStats.addAWSRequestStats(req.getResponse().getStatsRequestURL(), req.getResponse().getResponseCode(), appname, AWSConstants.WEBENGINE_PROCESSING, processingTime, 1);//No I18n
				AWSInfluxStats.addAWSRequestStats(req.getResponse().getStatsRequestURL(), req.getResponse().getResponseCode(), appname, AWSConstants.WEBENGINE_TOTAL, taskQueuedTime+processingTime, 1);//No I18n
				if(servletcount > 0)
				{
					AWSInfluxStats.addAWSRequestStats(req.getResponse().getStatsRequestURL(), req.getResponse().getResponseCode(), appname, AWSConstants.WEBENGINE_SERVLET, servlettime, servletcount);//No I18n
				}
				if(ondatacount > 0)
				{
					AWSInfluxStats.addAWSRequestStats(req.getResponse().getStatsRequestURL(), req.getResponse().getResponseCode(), appname, AWSConstants.WEBENGINE_ONDATA, ondatatime, ondatacount);//No I18n
				}
			}
		}
		catch(Exception ex)
		{
		}
	}

	public final void dispatchWSData(AsyncWebClient client, int state) throws Exception
        {
                try
                {
			/*if(client.isWSClosed())
                        {
                                client.close();
                                return;
                        }*/

                        if(!wstpeInitialized)
                        {
                                initWSMsgThreadPool();
                        }
                        WMSTPExecutorFactory.execute(wsmsgThreadName, new WSMsgEvent(client, state));
                }
                catch(Exception ex)
                {
                        logger.log(Level.SEVERE," Exception in dispatchWSData : ",AbstractWebEngine.class.getName(),AWSLogMethodConstants.DISPATCH_WS_DATA,ex);
                }               
        }

	private class WSMsgEvent implements Serializable
	{
		private AsyncWebClient client = null;
		private int state;
		private long intime;

		private WSMsgEvent(AsyncWebClient client, int state)
		{
			this.client = client;
			this.state = state;
			this.intime = System.currentTimeMillis();
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
	}

	private class WebsocketMsgDispatcher implements WmsTask
	{
		private long intime;
		private AsyncWebClient client;
		private int state;

		@Override
		public void handle(Object obj)
		{
			try
			{
				WSMsgEvent event = (WSMsgEvent) obj;
				handleWSMsgDispatcher(event.getClient(), event.getState(), event.getIntime());
			}
			catch(Exception e)
			{
				logger.log(Level.SEVERE, "Exception in WebsocketMsgDispatcher : ",WebsocketMsgDispatcher.class.getName(),AWSLogMethodConstants.HANDLE, e);
			}
		}
	}

	private void handleWSMsgDispatcher(AsyncWebClient client, int state, long intime) throws Exception
	{
		long start = System.currentTimeMillis();
		long taskQueuedTime = start - intime;
		AWSLogClientThreadLocal.setLoggingProperties(client.getReqId());
		client.setReqQueueInsertTime(intime);
                client.updateReqProcTime(taskQueuedTime);
		if(client.isWriteInitiated() && state == StateConstants.ON_WRITEFAILURE)
                {
                        long starttime = System.currentTimeMillis();
                        client.handleWSWriteFailure();
                        msgTracePrint(client.getHttpRequest(state),"ON WRITE FAILURE-WS",starttime,System.currentTimeMillis()-starttime);
                }
		else
		{
			dispatchWSMessage(client, state);
		}
		addQOSStats(client, taskQueuedTime, System.currentTimeMillis() - start);
	}

	private void addQOSStats(AsyncWebClient client, long taskQueuedTime, Long processingTime)
	{
		try
		{
			if(ConfManager.isQOSEnabled())
			{
				AWSInfluxStats.addAWSRequestStats(client.getStatsRequestURL(), client.getResponseCode(), appname+AWSConstants.HYPHEN_WSMSG, AWSConstants.WEBENGINE_TASKQUEUE, taskQueuedTime, 1);//No I18n
				AWSInfluxStats.addAWSRequestStats(client.getStatsRequestURL(), client.getResponseCode(), appname+AWSConstants.HYPHEN_WSMSG, AWSConstants.WEBENGINE_PROCESSING, processingTime, 1);//No I18n
				AWSInfluxStats.addAWSRequestStats(client.getStatsRequestURL(), client.getResponseCode(), appname+AWSConstants.HYPHEN_WSMSG, AWSConstants.WEBENGINE_TOTAL, taskQueuedTime + processingTime, 1);//No I18n
			}
		}
		catch(Exception ex)
		{
		}
	}

	public void dispatchWSMessage(AsyncWebClient client, int state)
	{
		if(client.isWSServletThreadSafe())//Will prevent unecessary new obj creation in case of WCP which will be huge
		{
			try
			{
				if(client.acquireWSProcessorLock())
				{
                                	dispatch(client, state);
				}
			}
			catch(Exception e)
			{
			}
			finally
			{
				if(client.isWSProcessorLocked())
				{
					client.releaseWSProcessorLock();
				}
			}
		}
		else
		{
			dispatch(client, state);
		}
	}

	private void dispatch(AsyncWebClient client, int state)
	{
		long starttime = System.currentTimeMillis();
		long lockAcquiredTime = 0l;
		long msgProcessingTime = 0l;
		int msgCount = 0;
		try
		{
			lockAcquiredTime = System.currentTimeMillis() - starttime;
			if(client.isWSClosed())
			{
				client.close(AWSConstants.CONNECTION_ALREADY_CLOSED);
				return;
			}
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
			HttpRequest request = client.getHttpRequest(state);
			Object data = null;
			while((data = client.pollWSPayload()) != null)
			{
				if(data instanceof byte[])
				{
					try
					{
						if(ConfManager.isBinaryWSDataEnabled() || isDefaultWSRead())
						{
							if(doFilterWSMessage(request,client.getHttpResponse(),(byte[])data))
							{
								long msgStartTime = System.currentTimeMillis();
								client.handleWSRead((byte[])data);
								msgProcessingTime += System.currentTimeMillis() - msgStartTime;
								msgCount++;
							}
							else
							{
								msgfilterlogger.addDebugLog(Level.WARNING, AWSLogConstants.RESTRICTED_CONTENTS_IN_MESSAGE, AbstractWebEngine.class.getName(),AWSLogMethodConstants.DISPATCH,new Object[]{client.getIPAddress(), client.isActive()});
							}
						}
						else
						{
							String sdata = new String((byte[])data);
							if(doFilterWSMessage(request,client.getHttpResponse(),sdata))
							{
								long msgStartTime = System.currentTimeMillis();
								client.handleWSRead(sdata);
								msgProcessingTime += System.currentTimeMillis() - msgStartTime;
								msgCount++;
							}
							else
							{
								msgfilterlogger.addDebugLog(Level.WARNING, AWSLogConstants.RESTRICTED_CONTENTS_IN_MESSAGE, AbstractWebEngine.class.getName(),AWSLogMethodConstants.DISPATCH,new Object[]{client.getIPAddress(), client.isActive()});
							}
						}
					}
					catch(Exception ex)
					{
						msgfilterlogger.addExceptionLog(Level.WARNING,"Restricted contents in message received from "+client.getIPAddress()+" . Connection active : "+client.isActive()+", Exception ",AbstractWebEngine.class.getName(),AWSLogMethodConstants.DISPATCH,ex);
					}
				}
				else if(data instanceof String)
				{
					try
					{
						if(ConfManager.isBinaryWSDataEnabled() && !isDefaultWSRead())
						{
							byte[] sdata = ((String)data).getBytes();
							if(doFilterWSMessage(request,client.getHttpResponse(),sdata))
							{
								long msgStartTime = System.currentTimeMillis();
								client.handleWSRead(sdata);
								msgProcessingTime = System.currentTimeMillis() - msgStartTime;
								msgCount++;
							}
							else
							{
								msgfilterlogger.addDebugLog(Level.WARNING, AWSLogConstants.RESTRICTED_CONTENTS_IN_MESSAGE,AbstractWebEngine.class.getName(),AWSLogMethodConstants.DISPATCH, new Object[]{client.getIPAddress(), client.isActive()});
							}
						}
						else
						{
							if(doFilterWSMessage(request,client.getHttpResponse(),(String)data))
							{
								long msgStartTime = System.currentTimeMillis();
								client.handleWSRead((String)data);
								msgProcessingTime = System.currentTimeMillis() - msgStartTime;
								msgCount++;
							}
							else
							{
								msgfilterlogger.addDebugLog(Level.WARNING, AWSLogConstants.RESTRICTED_CONTENTS_IN_MESSAGE,AbstractWebEngine.class.getName(),AWSLogMethodConstants.DISPATCH, new Object[]{client.getIPAddress(), client.isActive()});
							}
						}
					}
					catch(Exception ex)
					{
						msgfilterlogger.addDebugLog(Level.WARNING,"Restricted contents in message received from "+client.getIPAddress()+" . Connection active : "+client.isActive()+", Exception ",AbstractWebEngine.class.getName(),AWSLogMethodConstants.DISPATCH,ex);
					}
				}
			}
		}
		catch(Exception ex)
		{
			logger.addExceptionLog(Level.SEVERE, "Exception in WebSocket data dispatch : ", AbstractWebEngine.class.getName(),AWSLogMethodConstants.DISPATCH,ex);//No I18n
		}
		finally
		{
			addWSMsgStats(client, lockAcquiredTime, msgProcessingTime, System.currentTimeMillis() - starttime, msgCount);
		}
	}

	protected void addWSMsgStats(AsyncWebClient client, Long lockAcquiredTime, Long msgProcessingTime, Long processingTime, Integer msgCount)
	{
		try
		{
			if(ConfManager.isQOSEnabled())
			{
				AWSInfluxStats.addAWSRequestStats(client.getStatsRequestURL(), client.getResponseCode(), appname, "wsmsg_lockAcquire", lockAcquiredTime, 1);//No I18n
				AWSInfluxStats.addAWSRequestStats(client.getStatsRequestURL(), client.getResponseCode(), appname, "wsmsg_onMessageProcessing", msgProcessingTime, msgCount);//No I18n
				AWSInfluxStats.addAWSRequestStats(client.getStatsRequestURL(), client.getResponseCode(), appname, "wsmsg_totalProcessing", processingTime, 1);//No I18n
			}
		}
		catch(Exception ex)
		{
		}
	}

	public void msgTracePrint(HttpRequest req, String data, long starttime, long timeinterval)
	{
		if(req != null  && req.getRemoteAddr() != null && req.getRequestURL() != null)
		{
			if(timeinterval>ConfManager.getNetEventThreshold())
			{
				msgtracelogger.log(Level.SEVERE, AWSLogConstants.WEBENGINE_MSGTRACE_DELAY,AbstractWebEngine.class.getName(),AWSLogMethodConstants.MSG_TRACE_PRINT, new Object[]{data, req.getRemoteAddr(), req.getRequestURL(), starttime, timeinterval});
			}	
			else if(ConfManager.isNetEventTraceEnabled(req.getRemoteAddr()))
			{
				msgtracelogger.log(Level.INFO, AWSLogConstants.WEBENGINE_MSGTRACE_NODELAY,AbstractWebEngine.class.getName(),AWSLogMethodConstants.MSG_TRACE_PRINT, new Object[]{data, req.getRemoteAddr(), req.getRequestURL(), starttime, timeinterval});
			}
		}
	}

	public int getActivePoolSize() throws Exception
	{
		return WMSTPExecutorFactory.getActivePoolSize(tpeName);
	}

	public int getQueueSize() throws Exception
	{
		return WMSTPExecutorFactory.getQueueSize(tpeName);
	}

	public int getWSMsgTpeActivePoolSize() throws Exception
	{
		return WMSTPExecutorFactory.getActivePoolSize(wsmsgThreadName);
	}

	public int getWSMsgTpeQueueSize() throws Exception
	{
		return WMSTPExecutorFactory.getQueueSize(wsmsgThreadName);
	}

	public int getReadLimit()
	{
		return readlimit;
	}

	public int getWriteLimit()
	{
		return writelimit;
	}

	public int getKeyActiveSelectionTimeout()
	{
		return keyactiveselectiontimeout;
	}

	public int getReadSelectorCount(int port)
	{
		if(ConfManager.isHttpsPort(port))
		{
			return httpsreadselectorcnt;
                }
                else
		{
			return httpreadselectorcnt;
                }
	}

	public boolean isSupportedGetMethod(String type)
	{
		if(getSupportedGetMethods() != null)
		{
			return supportedgetmethods.contains(type);
		}
		else
		{
			return false;
		}
	}

	public boolean isSupportedPostMethod(String type)
	{
		if(getSupportedPostMethods() != null)
		{
			return supportedpostmethods.contains(type);
		}
		else
		{
			return false;
		}
	}

	public ArrayList getSupportedGetMethods()
	{
		return supportedgetmethods;
	}

	public ArrayList getSupportedPostMethods()
	{
		return supportedpostmethods;
	}

	public boolean isSupportedRequestType(String type)
	{
		return (isSupportedGetMethod(type) || isSupportedPostMethod(type));
	}

}
