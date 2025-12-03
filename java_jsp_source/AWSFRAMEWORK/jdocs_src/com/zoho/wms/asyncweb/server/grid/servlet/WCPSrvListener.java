package com.zoho.wms.asyncweb.server.grid.servlet;

// java imports
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

// wms common import
import com.adventnet.wms.common.WMSTypes;
import com.adventnet.wms.common.CommonUtil;

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

// aws imports
import com.zoho.wms.asyncweb.server.ConfManager;
import com.zoho.wms.asyncweb.server.http.WebSocket;
import com.zoho.wms.asyncweb.server.http.HttpRequest;
import com.zoho.wms.asyncweb.server.http.HttpResponse;
import com.zoho.wms.asyncweb.server.exception.AWSException;
import com.zoho.wms.asyncweb.server.grid.wcp.connection.WCPDefaultServerConnection;

import com.zoho.wms.wcp.common.wcputil.WCPConstants;

public class WCPSrvListener extends WebSocket
{
	private static Logger logger = Logger.getLogger(WCPSrvListener.class.getName());
  
	private String serverSessionId;

	private static Object lockObject = new Object();
	private static WCPIsolationHandler wcpIsoHandler = null;
	private WCPDefaultServerConnection servConn = null;
	private long servletInitTime = 0;

	public void onConnect(HttpRequest req, HttpResponse res) throws IOException
	{
		try
		{
			if(!req.isWebSocket())
			{
				logger.log(Level.SEVERE, "WCPSrvListener -  Not a WebSocketConnection onConnect - req={0}", new Object[] {req.getRemoteAddr()});
				res.sendError(400, "Bad Request");
				res.close();
				return;
			}
		}
		catch (Exception e)
		{
			logger.log(Level.SEVERE, "WCPSrvListener - Exception while onConnect validation - ", e);
		}

		updateWSPingIntervalinMilliSeconds(30*1000);
		res.enablePingResponse();
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
				logger.log(Level.SEVERE, "WCPSrvListener - Exception while onConnect - ", e);
			}

			return false;
		}

		if(WCPSessionManager.isPaused(WCPConstants.WCP_SERVER_URI))
		{
			res.addHeader("x-datastream-paused", "true"); //No I18N
		}

		String remoteIp = null;
		String source = null;
		String poolName = null;
		String context = null;
		String clientSessionId = null;
		String remoteServerType = null;
		String sourceCluster = null;
		String accessToken = null;
		boolean isNewDecryptionEnabled = false;
		long connOriginTime = 0l;
		try
		{
			res.setWriteLimit(-1);

			remoteIp = req.getRemoteAddr();
			source = req.getParameter("source");
			poolName = req.getParameter("pname");
			context = req.getParameter("context");
			clientSessionId = req.getParameter("sid");
			remoteServerType = req.getParameter("rst");
			sourceCluster = req.getParameter("sourcecluster");
			accessToken = req.getParameter(WCPConstants.Security.HeadersKeys.ACCESS_TOKEN);
			connOriginTime = WCPUtil.parseLong(req.getParameter("connect-origin-time"), -1);
			
			logger.log(Level.INFO," MK --> WCPSrvListener-verifyToUpgrade --> poolName={0} remoteIp={1} source={2} clientSessionId{3} serverSessionId={4} connOriginTime={5} reqHeaders={6} ", new Object[] {poolName,remoteIp,source,clientSessionId,serverSessionId,connOriginTime,req.getHeaders()});

		    if(Boolean.parseBoolean((req.getHeader(WCPConstants.NEW_ENCRYPTION))))
		    {
		    	isNewDecryptionEnabled=true;
		    	logger.log(Level.INFO,"MK --> WCPSrvListener New Decryption Header Found --> poolName={0} remoteIp={1} source={2} clientSessionId={3}  serverSessionId={4}",new Object[]{poolName,remoteIp,source,clientSessionId,serverSessionId});
		    }

			if(CommonUtil.isEmpty(accessToken))
			{
				accessToken=req.getHeader(WCPConstants.Security.HeadersKeys.ACCESS_TOKEN);
			}

			this.serverSessionId = WCPUtil.getServerSessionId(req.getRemoteAddr()+"_"+clientSessionId);

			 if(!validateRequest(res, accessToken, remoteServerType, remoteIp, sourceCluster, context, poolName, clientSessionId, isNewDecryptionEnabled, connOriginTime))
			{
				return false;
			}

			try
			{
				source = sourceCluster;
				servConn = new WCPDefaultServerConnection(this, remoteServerType, remoteIp, serverSessionId, sourceCluster, poolName, context, connOriginTime);
			}
			catch(Exception ex)
			{
				logger.log(Level.SEVERE,"[WCP][WCPSrvListener - onConnect] sourceCluster="+source+" remoteip="+remoteIp+" poolName="+poolName+" context="+context+" csid="+clientSessionId+" ssid="+serverSessionId, ex);
				return false;
			}

			logger.info("[WCP][WCPSrvListener initialized!!]["+source+"]["+sourceCluster+"]["+poolName+"]["+context+"]["+remoteIp+"]["+serverSessionId+"][CSID="+clientSessionId+"]");
			if(!ConfManager.isWCPServletThreadSafe())
			{
				res.setWSServletThreadSafe(false);
			}
			servConn.onConnect();
			servletInitTime = System.currentTimeMillis();

			return true;
		}
		catch(Exception e)
		{
			logger.log(Level.SEVERE, "WCPERR--> Error while inside verifyToUpgrade inside WCPSrvListener. sourceCluster="+source+" remoteip="+remoteIp+" poolName="+poolName+" csid="+clientSessionId+" ssid"+serverSessionId, e);
		}

		return false;
	}

	private boolean validateRequest(HttpResponse res, String accessToken, String remoteServerType, String remoteIp, String sourceCluster, String context, String poolName, String clientSessionId, boolean isNewDecryptionEnabled, long connOriginTime)
	{
		if(checkIsEmptyForParams(sourceCluster, poolName, clientSessionId))
		{
			return false;
		}

		boolean isWCPSecurityEnabled = WCPConfManager.isWCPSecurityEnabled();

		if(connOriginTime>0 && WCPSessionManager.getConnection(serverSessionId) != null)
		{
			if(WCPSessionManager.getConnection(serverSessionId).getConnectionOriginTime() > connOriginTime)
			{
				logger.log(Level.WARNING,"SBTEST-->[WCPERR][WCPSrvListener] Valid Session is already present. sourceCluster={0} context={1} poolName={2} remoteIp={3} clientSessionId={4} serverSessionId={5}", new Object[]{sourceCluster, context, poolName, remoteIp, clientSessionId, serverSessionId});

				return false;
			}
		}

		//security level 1 - allowedclusters
		if(!WCPConfManager.isAllowedCluster(sourceCluster))
		{
			logger.log(Level.WARNING,"[WCPERR][WCPSrvListener] isAllowedCluster failed. sourceCluster={0} isAllowedCluster={1} context={2} poolName={3} remoteIp={4} clientSessionId={5}", new Object[]{sourceCluster, WCPConfManager.isAllowedCluster(sourceCluster), context, poolName, remoteIp, clientSessionId});

			if(isWCPSecurityEnabled)
			{
				res.addHeader(WCPConstants.Security.HeadersKeys.FAILED_STATUS, String.valueOf(WCPConstants.Security.ConnectionFailedStatusCode.CLUSTER_NOT_ALLOWED));
				WCPStats.addWCPSecurityFailure(WCPConstants.WCP_SERVER_URI, sourceCluster, poolName, context, remoteIp, "AllowedCluster Failed"); //No I18N

				return false;
			}
		}

		//security level 2 - allowedIps check
		if(!WCPConfManager.isAllowedIp(sourceCluster, remoteIp))
		{
			logger.log(Level.WARNING,"[WCPERR][WCPSrvListener] isAllowedIPS failed. remoteIp is not present in the allowedIps list. remoteIp={0} sourceCluster={1} context={2} poolName={3} clientSessionId={4}", new Object[]{remoteIp, sourceCluster, context, poolName, clientSessionId});

			if(isWCPSecurityEnabled)
			{
				res.addHeader(WCPConstants.Security.HeadersKeys.FAILED_STATUS, String.valueOf(WCPConstants.Security.ConnectionFailedStatusCode.IP_NOT_ALLOWED));
				WCPStats.addWCPSecurityFailure(WCPConstants.WCP_SERVER_URI, sourceCluster, poolName, context, remoteIp, "AllowedIPs Failed"); //No I18N

				return false;
			}
		}
	
		//security level 3 - accessToken Verification
		if(!CommonUtil.isEmpty(accessToken))
		{
			int accessTokenStatus = WCPSecurityUtil.verifyServerAccessToken(accessToken, WCPUtil.getServerType(sourceCluster), remoteIp, isNewDecryptionEnabled);
			switch(accessTokenStatus)
			{
			case WCPConstants.Security.AccessTokenStatus.SUCCESS:
				logger.log(Level.INFO,"[WCP][WCPSrvListener] verifyServerAccessToken SUCCESS. accessToken={0} remoteServerType={1} remoteIp={2} sourceCluster={3} context={4} poolName={5} clientSessionId={6}",new Object[]{accessToken,remoteServerType,remoteIp,sourceCluster,context,poolName,clientSessionId});

				break;

			case WCPConstants.Security.AccessTokenStatus.FAILED:

				logger.log(Level.WARNING,"[WCPERR][WCPSrvListener] verifyServerAccessToken FAILED. accessToken={0} remoteServerType={1} remoteIp={2} sourceCluster={3} context={4} poolName={5} clientSessionId={6}",new Object[]{accessToken,remoteServerType,remoteIp,sourceCluster,context,poolName,clientSessionId});
				WCPStats.addWCPSecurityFailure(WCPConstants.WCP_SERVER_URI, sourceCluster, poolName, context, remoteIp, "Verify AccessToken Failed"); //No I18N

				if(isWCPSecurityEnabled)
				{
					res.addHeader(WCPConstants.Security.HeadersKeys.ACCESS_TOKEN_VERIFICATION_STATUS, String.valueOf(accessTokenStatus));
					res.addHeader(WCPConstants.Security.HeadersKeys.FAILED_STATUS, String.valueOf(WCPConstants.Security.ConnectionFailedStatusCode.ACCESS_TOKEN_VERIFICATION_FAILED));

					return false;
				}

				break;

			case WCPConstants.Security.AccessTokenStatus.EXPIRED:

				logger.log(Level.WARNING,"[WCPERR][WCPSrvListener] verifyServerAccessToken EXPIRED. accessToken={0} remoteServerType={1} remoteIp={2} sourceCluster={3} context={4} poolName={5} clientSessionId={6}",new Object[]{accessToken,remoteServerType,remoteIp,sourceCluster,context,poolName,clientSessionId});
				WCPStats.addWCPSecurityFailure(WCPConstants.WCP_SERVER_URI, sourceCluster, poolName, context, remoteIp, "AccessToken Expired"); //No I18N

				if(isWCPSecurityEnabled)
				{
					res.addHeader(WCPConstants.Security.HeadersKeys.ACCESS_TOKEN_VERIFICATION_STATUS, String.valueOf(accessTokenStatus));
					res.addHeader(WCPConstants.Security.HeadersKeys.FAILED_STATUS, String.valueOf(WCPConstants.Security.ConnectionFailedStatusCode.ACCESS_TOKEN_EXPIRED));

					return false;
				}

				break;

			default :

				logger.log(Level.WARNING,"[WCPERR][WCPSrvListener] Invalid accessTokenStatus. accessToken={0} remoteServerType={1} remoteIp={2} sourceCluster={3} context={4} poolName={5} clientSessionId={6}",new Object[]{accessToken,remoteServerType,remoteIp,sourceCluster,context,poolName,clientSessionId});
				WCPStats.addWCPSecurityFailure(WCPConstants.WCP_SERVER_URI, sourceCluster, poolName, context, remoteIp, "Invalid AccessToken"); //No I18N

				if(isWCPSecurityEnabled)
				{
					res.addHeader(WCPConstants.Security.HeadersKeys.ACCESS_TOKEN_VERIFICATION_STATUS, String.valueOf(accessTokenStatus));
					res.addHeader(WCPConstants.Security.HeadersKeys.FAILED_STATUS, String.valueOf(WCPConstants.Security.ConnectionFailedStatusCode.ACCESS_TOKEN_VERIFICATION_FAILED));

					return false;
				}

				break;
			}
		}
		else
		{
			logger.log(Level.WARNING,"[WCPERR][WCPSrvListener] accessToken is null or empty. accessToken={0} remoteServerType={1} remoteIp={2} sourceCluster={3} context={4} poolName={5} clientSessionId={6}",new Object[]{accessToken,remoteServerType,remoteIp,sourceCluster,context,poolName,clientSessionId});
			WCPStats.addWCPSecurityFailure(WCPConstants.WCP_SERVER_URI, sourceCluster, poolName, context, remoteIp, "AccessToken is null"); //No I18N

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

	private boolean checkIsEmptyForParams(String sourceCluster, String poolName, String clientSessionId)
	{
		if(CommonUtil.isEmpty(poolName))
		{
			logger.log(Level.WARNING,"[WCPERR][WCPSrvListener] poolName is null or empty. sourceCluster={0} clientSessionId={1} ssid={2}", new Object[]{sourceCluster, clientSessionId, serverSessionId});

			return true;
		}

		if(CommonUtil.isEmpty(sourceCluster))
		{
			logger.log(Level.WARNING,"[WCPERR][WCPSrvListener] sourceCluster is null or empty. poolName={0} clientSessionId={1} ssid={2}", new Object[]{poolName, clientSessionId, serverSessionId});

			return true;
		}

		if(CommonUtil.isEmpty(clientSessionId))
		{
			logger.log(Level.WARNING,"[WCPERR][WCPSrvListener] clientSessionId is null or empty. sourceCluster={0} poolName={1} ssid={2}", new Object[]{sourceCluster, poolName, serverSessionId});

			return true;
		}

		return false;
	}


	public boolean writeData(byte[] data)throws IOException, AWSException
	{
		if(response==null || !response.isActive())
		{
			StatsDB.recordError(ComponentConstants.WCP_ENUM.getModuleCode(), ComponentConstants.WCP_ENUM.WRITE_INVOKED_AFTER_RESPONSE_STREAM_CLOSED_SRV.getErrorCode(), 1);

			// this +  WCPSessionManager.getConnection(serverSessionId).getRequest() + serverSessionId + http request object
			StringBuilder debugLogs = new StringBuilder();
			debugLogs.append(" WCP Error - WRITE_INVOKED_AFTER_RESPONSE_STREAM_CLOSED_SRV ")	//No I18n
			.append(" serverSessionId = "+serverSessionId)//No I18n
			.append(" WebSocket Request Object = "+request)//No I18n
			.append(" Current Class Instance = "+this);//No I18n
			try
			{
				debugLogs.append(" Reference Class Insatnce inside WCP = "+((WCPDefaultServerConnection)WCPSessionManager.getConnection(serverSessionId)).getRequest());//No I18n
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
					wcpIsoHandler = new WCPIsolationHandler("srv_req_proc","srv", WCPConfManager.getServerReqProcessingCorePool(), WCPConfManager.getServerReqProcessingMaxPool(), WCPConfManager.getServerReqProcessingIsoMaxPool(), WCPConfManager.getServerReqProcessingQueueSize(), WCPConfManager.getServerReqProcessingIsolationThreshold(), WCPConfManager.getServerReqProcessingMaxIsolationLimit(), WCPConfManager.isIsoEnabledInServerReqProc()); //NO I18n
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
			logger.log(Level.INFO, "WCP-> WCPSrvListener, Connection trying to remove is already removed sid={0}, onClose-conn={1}, alive connection={2} servletInTime={3}", new Object[]{serverSessionId, servConn, WCPSessionManager.getConnection(serverSessionId), servletInitTime});
			WCPStats.addWCPErrorStats("WCP_SRV_LISTENER_FALSE_CLOSE", "[sid="+serverSessionId+"][srv_startTime="+servletInitTime+"]");
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
