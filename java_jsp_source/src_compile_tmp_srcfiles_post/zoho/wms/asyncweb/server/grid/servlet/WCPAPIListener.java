package com.zoho.wms.asyncweb.server.grid.servlet;

// java imports
import java.io.IOException;
import java.net.URLDecoder;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

// wms common import
import com.adventnet.wms.common.WMSTypes;
import com.adventnet.wms.common.CommonUtil;
import com.adventnet.wms.common.WMSConstants;

// aws importss
import com.zoho.wms.asyncweb.server.http.HttpRequest;
import com.zoho.wms.asyncweb.server.http.HttpResponse;
import com.zoho.wms.asyncweb.server.grid.wcp.connection.WCPAPIConnection;

// wms servercommon import
import com.adventnet.wms.servercommon.stats.influx.StatsDB;
import com.adventnet.wms.servercommon.components.net.WCPBQObject;
import com.adventnet.wms.servercommon.components.net.util.WCPUtil;
import com.adventnet.wms.servercommon.components.net.WCPConfManager;
import com.adventnet.wms.servercommon.components.net.stats.WCPStats;
import com.adventnet.wms.servercommon.components.net.WCPSessionManager;
import com.adventnet.wms.servercommon.components.net.WCPIsolationHandler;
import com.adventnet.wms.servercommon.components.net.util.WCPSecurityUtil;
import com.adventnet.wms.servercommon.components.constants.ComponentConstants;

import com.zoho.wms.wcp.common.wcputil.WCPConstants;

// aws imports
import com.zoho.wms.asyncweb.server.http.WebSocket;
import com.zoho.wms.asyncweb.server.exception.AWSException;

public class WCPAPIListener extends WebSocket
{
	private static Logger logger = Logger.getLogger(WCPAPIListener.class.getName());

	private String serverSessionId;	//ServerSessionId	- ssid

	private static Object lockObject = new Object();
	private static WCPIsolationHandler wcpIsoHandler = null;
	private String iamServiceName = null;
	private WCPAPIConnection servConn = null;
	private long servletInitTime = 0;

	public void onConnect(HttpRequest req, HttpResponse res) throws IOException
	{
		try
		{
			if(!req.isWebSocket())
			{
				logger.log(Level.SEVERE, "WCPAPIListener -  Not a WebSocketConnection onConnect - req={0}", new Object[] {req.getRemoteAddr()});
				res.sendError(400, "Bad Request");
				res.close();
				return;
			}
		}
		catch (Exception e)
		{
			logger.log(Level.SEVERE, "WCPAPIListener - Exception while onConnect validation - ", e);
		}
	}

	public boolean verifyToUpgrade(HttpRequest req, HttpResponse res) throws IOException
	{
		if(!WCPSessionManager.isInitialised())
		{
			try
			{
				res.sendError(406, "WCPSessionManager Not Initialised");
				res.close();
			}
			catch (Exception e)
			{
				logger.log(Level.SEVERE, "WCPAPIListener - Exception while verifyToUpgrade - ", e);
			}

			return false;
		}

		if(WCPSessionManager.isPaused(WCPConstants.WCP_API_URI))
		{
			logger.warning("WCPAPI--> DataStream paused for WCP API URI remoteip="+req.getRemoteAddr());
			return false;
		}

		String prd = null;
		String setup = null;
		String apiName = null;
		String context = null;
		String remoteIp = null;
		String poolName = null;
		String servingMode = null;
		String accessToken = null;
		String clientSessionId = null;
		long connOriginTime = 0l;
		boolean isNewDecryptionEnabled = false;
		try
		{
			res.setWriteLimit(-1);

			prd = req.getParameter("prd");
			remoteIp = req.getRemoteAddr();
			setup = req.getParameter("setup");
			poolName = req.getParameter("pname");
			apiName = req.getParameter("source"); // wmsapi-CT    <apiname>-<prd>
			context = req.getParameter("context");
			clientSessionId = req.getParameter("sid");
			servingMode = req.getParameter(WMSConstants.RequestHeader.REQ_ORIGIN_SERVING_MODE);
			accessToken = req.getParameter(WCPConstants.Security.HeadersKeys.ACCESS_TOKEN_FOR_API);

			connOriginTime = WCPUtil.parseLong(req.getParameter("connect-origin-time"), -1);

			this.serverSessionId = WCPUtil.getServerSessionId(req.getRemoteAddr()+"_"+clientSessionId);

			logger.log(Level.INFO," MK --> WCPAPIListener-verifyToUpgrade --> poolName={0} remoteIp={1} apiName={2} clientSessionId{3} serverSessionId={4} reqHeaders={5} ", new Object[] {poolName,remoteIp,apiName,clientSessionId,serverSessionId,req.getHeaders()});
			
			if(Boolean.parseBoolean(req.getHeader(WCPConstants.NEW_ENCRYPTION)))
			{
				isNewDecryptionEnabled=true;
				logger.log(Level.INFO," MK --> WCPAPIListener New Decryption Header Found --> poolName={0} remoteIp={1} apiName={2} clientSessionId={3}  serverSessionId={4} ",new Object[]{poolName,remoteIp,apiName,clientSessionId,serverSessionId});
			}
			
			if(CommonUtil.isEmpty(accessToken))
			{
				accessToken=URLDecoder.decode(req.getHeader(WCPConstants.Security.HeadersKeys.ACCESS_TOKEN_FOR_API), "UTF-8");
				logger.log(Level.INFO," MK --> WCPAPIListener Received accessToken ={0}",accessToken);
			}

			if(CommonUtil.isEmpty(setup))
			{
				setup = WCPConstants.WCP_DEFAULT_SETUP;
			}

			if(!validateRequest(res, accessToken, apiName.toLowerCase(), remoteIp, prd, context, poolName, clientSessionId, isNewDecryptionEnabled, connOriginTime))
			{
				return false;
			}

			if(serverSessionId == null || apiName == null)
			{
				return false;
			}

			try
			{
				servConn = new WCPAPIConnection(this, prd, req.getRemoteAddr(), serverSessionId, apiName, setup, poolName, context, servingMode, iamServiceName, connOriginTime);
			}
			catch(Exception ex)
			{
				logger.log(Level.SEVERE,"[WCP][WCPAPIListener - onConnect] prd="+prd+" apiName="+apiName+" setup="+setup+" remoteip="+remoteIp+" context="+context+" csid="+clientSessionId+" ssid="+serverSessionId, ex);
				return false;
			}

			logger.info("[WCP][WCPAPIListener initialized!!] prd="+prd+" apiName="+apiName+" setup="+setup+" remoteip="+remoteIp+" context="+context+" csid="+clientSessionId+" iamServiceName="+iamServiceName+" ssid="+serverSessionId);
			servConn.onConnect();
			servletInitTime = System.currentTimeMillis();

			return true;
		}
		catch(Exception e)
		{
			logger.log(Level.SEVERE, "WCPERR--> Error while inside verifyToUpgrade WCPAPIListener prd="+prd+" apiName="+apiName+" setup="+setup+" remoteip="+remoteIp+" csid="+clientSessionId+" ssid="+serverSessionId, e);
		}

		return false;
	}

	private boolean validateRequest(HttpResponse res, String accessToken, String apiName, String remoteIp, String prd, String context, String poolName, String clientSessionId, boolean isNewDecryptionEnabled, long connOriginTime)
	{
		if(checkIsEmptyForParams(apiName, poolName, clientSessionId))
		{
			return false;
		}


		String apiname = apiName.split("-")[0];
		boolean isWCPSecurityEnabled = WCPConfManager.isWCPSecurityEnabledForAPI();

		if(connOriginTime>0 && WCPSessionManager.getConnection(serverSessionId) != null)
		{
			if(WCPSessionManager.getConnection(serverSessionId).getConnectionOriginTime() > connOriginTime)
			{
				logger.log(Level.WARNING,"WCP-->[WCPERR][WCPApiListener] Valid Session is already present. apiName={0} prd={1} context={2} poolName={3} remoteIp={4} clientSessionId={5} serverSessionId={6}", new Object[]{apiName, prd, context, poolName, remoteIp, clientSessionId, serverSessionId});

				return false;
			}
		}

		//security level 1 - allowedapis
		if(!WCPConfManager.isAllowedAPI(apiname))
		{
			logger.log(Level.WARNING,"[WCPERR][WCPAPIListener] isAllowedAPI failed. apiName={0} isAllowedAPI={1} context={2} poolName={3} remoteIp={4} clientSessionId={5}", new Object[]{apiName, WCPConfManager.isAllowedAPI(apiname), context, poolName, remoteIp, clientSessionId});

			if(isWCPSecurityEnabled)
			{
				res.addHeader(WCPConstants.Security.HeadersKeys.FAILED_STATUS, String.valueOf(WCPConstants.Security.ConnectionFailedStatusCode.API_NOT_ALLOWED));
				WCPStats.addWCPSecurityFailure(WCPConstants.WCP_API_URI, apiName, poolName, context, remoteIp, "AllowedAPI Failed");

				return false;
			}
		}

		//security level 2 - accessToken Verification
		if(!CommonUtil.isEmpty(accessToken))
		{
			StringBuffer iamServiceNameSB = new StringBuffer();
			int accessTokenStatus = WCPSecurityUtil.verifyAPIAccessToken(accessToken, apiname, prd, remoteIp, iamServiceNameSB, isNewDecryptionEnabled);
			iamServiceName = WCPUtil.isNull(iamServiceNameSB.toString())?null:iamServiceNameSB.toString();
			switch(accessTokenStatus)
			{
			case WCPConstants.Security.AccessTokenStatus.SUCCESS:
				logger.log(Level.INFO,"[WCP][WCPAPIListener] verifyAPIAccessToken SUCCESS. accessToken={0} apiName={1} remoteIp={2} prd={3} context={4} poolName={5} clientSessionId={6}",new Object[]{accessToken,apiName,remoteIp,prd,context,poolName,clientSessionId});

				break;

			case WCPConstants.Security.AccessTokenStatus.FAILED:

				logger.log(Level.WARNING,"[WCPERR][WCPAPIListener] verifyAPIAccessToken FAILED. accessToken={0} apiName={1} remoteIp={2} prd={3} context={4} poolName={5} clientSessionId={6}",new Object[]{accessToken,apiName,remoteIp,prd,context,poolName,clientSessionId});
				WCPStats.addWCPSecurityFailure(WCPConstants.WCP_API_URI, apiName, poolName, context, remoteIp, "Verify AccessToken Failed");

				if(isWCPSecurityEnabled)
				{
					res.addHeader(WCPConstants.Security.HeadersKeys.FAILED_STATUS, String.valueOf(WCPConstants.Security.ConnectionFailedStatusCode.ACCESS_TOKEN_VERIFICATION_FAILED));
					res.addHeader(WCPConstants.Security.HeadersKeys.ACCESS_TOKEN_VERIFICATION_STATUS, String.valueOf(accessTokenStatus));

					return false;
				}

				break;

			case WCPConstants.Security.AccessTokenStatus.EXPIRED:

				logger.log(Level.WARNING,"[WCPERR][WCPAPIListener] verifyAPIAccessToken EXPIRED. accessToken={0} apiName={1} remoteIp={2} prd={3} context={4} poolName={5} clientSessionId={6}",new Object[]{accessToken,apiName,remoteIp,prd,context,poolName,clientSessionId});
				WCPStats.addWCPSecurityFailure(WCPConstants.WCP_API_URI, apiName, poolName, context, remoteIp, "AccessToken Expired");

				if(isWCPSecurityEnabled)
				{
					res.addHeader(WCPConstants.Security.HeadersKeys.FAILED_STATUS, String.valueOf(WCPConstants.Security.ConnectionFailedStatusCode.ACCESS_TOKEN_EXPIRED));
					res.addHeader(WCPConstants.Security.HeadersKeys.ACCESS_TOKEN_VERIFICATION_STATUS, String.valueOf(accessTokenStatus));

					return false;
				}

				break;

			default :

				logger.log(Level.WARNING,"[WCPERR][WCPAPIListener] Invalid accessTokenStatus. accessToken={0} apiName={1} remoteIp={2} prd={3} context={4} poolName={5} clientSessionId={6}",new Object[]{accessToken,apiName,remoteIp,prd,context,poolName,clientSessionId});
				WCPStats.addWCPSecurityFailure(WCPConstants.WCP_API_URI, apiName, poolName, context, remoteIp, "Invalid AccessToken");

				if(isWCPSecurityEnabled)
				{
					res.addHeader(WCPConstants.Security.HeadersKeys.FAILED_STATUS, String.valueOf(WCPConstants.Security.ConnectionFailedStatusCode.ACCESS_TOKEN_VERIFICATION_FAILED));
					res.addHeader(WCPConstants.Security.HeadersKeys.ACCESS_TOKEN_VERIFICATION_STATUS, String.valueOf(accessTokenStatus));

					return false;
				}

				break;
			}
		}
		else
		{
			logger.log(Level.WARNING,"[WCPERR][WCPAPIListener] accessToken is null or empty. accessToken={0} apiName={1} remoteIp={2} prd={3} context={4} poolName={5} clientSessionId={6}",new Object[]{accessToken,apiName,remoteIp,prd,context,poolName,clientSessionId});
			WCPStats.addWCPSecurityFailure(WCPConstants.WCP_API_URI, apiName, poolName, context, remoteIp, "AccessToken is null");

			if(isWCPSecurityEnabled)
			{
				// TODO: change it to WCPConstants in next update.
				res.addHeader(WCPConstants.Security.HeadersKeys.FAILED_STATUS, "506"); //String.valueOf(WCPConstants.Security.ConnectionFailedStatusCode.ACCESS_TOKEN_NULL_OR_EMPTY));
				res.addHeader(WCPConstants.Security.HeadersKeys.ACCESS_TOKEN_VERIFICATION_STATUS, "3");

				return false;
			}
		}
		res.addHeader(WCPConstants.Security.HeadersKeys.ACCESS_TOKEN_VERIFICATION_STATUS, String.valueOf(WCPConstants.Security.AccessTokenStatus.SUCCESS));

		return true;
	}

	private boolean checkIsEmptyForParams(String apiName, String poolName, String clientSessionId)
	{
		if(CommonUtil.isEmpty(poolName))
		{
			logger.log(Level.WARNING,"[WCPERR][WCPSrvListener] poolName is null or empty. sourceCluster={0} clientSessionId={1} ssid={2}", new Object[]{apiName, clientSessionId, serverSessionId});

			return true;
		}

		if(CommonUtil.isEmpty(apiName))
		{
			logger.log(Level.WARNING,"[WCPERR][WCPSrvListener] apiName is null or empty. poolName={0} clientSessionId={1} ssid={2}", new Object[]{poolName, clientSessionId, serverSessionId});

			return true;
		}

		if(CommonUtil.isEmpty(clientSessionId))
		{
			logger.log(Level.WARNING,"[WCPERR][WCPSrvListener] clientSessionId is null or empty. sourceCluster={0} poolName={1} ssid={2}", new Object[]{apiName, poolName, serverSessionId});

			return true;
		}

		return false;
	}

	public boolean writeData(byte[] data)throws IOException, AWSException
	{
		if(response==null || !response.isActive())
		{
			StatsDB.recordError(ComponentConstants.WCP_ENUM.getModuleCode(), ComponentConstants.WCP_ENUM.WRITE_INVOKED_AFTER_RESPONSE_STREAM_CLOSED_API.getErrorCode(), 1);

			// this +  WCPSessionManager.getConnection(serverSessionId).getRequest() + serverSessionId + http request object
			StringBuilder debugLogs = new StringBuilder();
			debugLogs.append(" WCP Error - WRITE_INVOKED_AFTER_RESPONSE_STREAM_CLOSED_API ") //No I18n
			.append(" serverSessionId = "+serverSessionId) //No I18n
			.append(" WebSocket Request Object = "+request) //No I18n
			.append(" Current Class Instance = "+this); //No I18n
			try
			{
				debugLogs.append(" Reference Class Insatnce inside WCP = "+((WCPAPIConnection)WCPSessionManager.getConnection(serverSessionId)).getRequest()); //No I18n
			}
			catch(Exception ignored)
			{
			}

			logger.severe(debugLogs.toString());

			return false;
		}

		response.write(data);

		return true;
	}

	public void onMessage(String data) throws IOException
	{
	}

	public void onMessage(byte[] data) throws IOException
	{
		if(data.length < 5 && (new String(data)).equals(WMSTypes.NOP))
		{
			response.ping();
			return;
		}

		if(wcpIsoHandler==null && WCPConfManager.isReqProcessingBQEnabled())
		{
			synchronized (lockObject)
			{
				if(wcpIsoHandler==null && WCPConfManager.isReqProcessingBQEnabled())
				{
					wcpIsoHandler = new WCPIsolationHandler("api_req_proc","api", WCPConfManager.getApiReqProcessingCorePool(), WCPConfManager.getApiReqProcessingMaxPool(), WCPConfManager.getApiReqProcessingIsoMaxPool(), WCPConfManager.getApiReqProcessingQueueSize(), WCPConfManager.getApiReqProcessingIsolationThreshold(), WCPConfManager.getApiReqProcessingMaxIsolationLimit(), WCPConfManager.isIsoEnabledInApiReqProc());	//NO I18n
				}
			}
		}

		if(WCPConfManager.isReqProcessingBQEnabled())
		{
			wcpIsoHandler.handle(new WCPBQObject(serverSessionId, data));
		}
		else
		{
			WCPSessionManager.getConnection(serverSessionId).onMessage(data);
		}
	}

	public void onClose() throws IOException
	{
		if(WCPSessionManager.getConnection(serverSessionId) == servConn)
		{
			WCPSessionManager.getConnection(serverSessionId).onClose();
		}
		else
		{
			logger.log(Level.INFO, "WCP-> WCPAPIListener, Connection trying to remove is already removed sid={0}, onClose-conn={1}, alive connection={2} lifetime={3}", new Object[]{serverSessionId, WCPSessionManager.getConnection(serverSessionId), servConn, System.currentTimeMillis() - servletInitTime});
			WCPStats.addWCPErrorStats("WCP_API_LISTENER_FALSE_CLOSE", "[sid="+serverSessionId+"][srv_startTime="+servletInitTime+"]");
		}
	}

	public void close()
	{
		try
		{
			response.close();
		}
		catch(Exception ex)
		{
		}
	}

	public void onPingMessage(byte[] data) throws IOException
	{

	}

	public void onPongMessage(byte[] data) throws IOException
	{

	}

	public boolean isActive()
	{
		return (response!=null && response.isActive());
	}
}