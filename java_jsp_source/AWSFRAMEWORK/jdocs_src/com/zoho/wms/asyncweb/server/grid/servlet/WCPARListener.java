package com.zoho.wms.asyncweb.server.grid.servlet;

// java imports
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

// wms common import
import com.adventnet.wms.common.CommonUtil;
import com.adventnet.wms.common.WMSTypes;

// wms servercommon import
import com.adventnet.wms.servercommon.components.net.stats.WCPStats;
import com.adventnet.wms.servercommon.grid.ar.ARConstants;
import com.adventnet.wms.servercommon.stats.influx.StatsDB;
import com.adventnet.wms.servercommon.components.net.util.WCPUtil;
import com.adventnet.wms.servercommon.components.net.WCPSessionManager;
import com.adventnet.wms.servercommon.components.constants.ComponentConstants;

// aws imports
import com.zoho.wms.asyncweb.server.ConfManager;
import com.zoho.wms.asyncweb.server.http.WebSocket;
import com.zoho.wms.asyncweb.server.http.HttpRequest;
import com.zoho.wms.asyncweb.server.http.HttpResponse;
import com.zoho.wms.asyncweb.server.exception.AWSException;
import com.zoho.wms.asyncweb.server.grid.wcp.connection.WCPActiveReplacementConnection;

public class WCPARListener extends WebSocket
{
	private static Logger logger = Logger.getLogger(WCPARListener.class.getName());

	private String source;
	private String clientSessionId;
	private String serverSessionId;

	private String poolName;
	private String sourceCluster;
	private long servletInitTime = 0;
	private WCPActiveReplacementConnection servConn= null;

	public void onConnect(HttpRequest req, HttpResponse res) throws IOException
	{
		try
		{
			if(!req.isWebSocket())
			{
				logger.log(Level.SEVERE, "WCPARListener -  Not a WebSocketConnection onConnect - req={0}", new Object[] {req.getRemoteAddr()});
				res.sendError(400, "Bad Request");
				res.close();
				return;
			}
		}
		catch (Exception e)
		{
			logger.log(Level.SEVERE, "WCPARListener - Exception while onConnect validation - ", e);
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
				logger.log(Level.SEVERE, "WCPARListener - Exception while verifyToUpgrade - ", e);
			}
			return false;
		}

		if(WCPSessionManager.isPaused(ARConstants.WCP.AR_URI))
		{
			res.addHeader("x-datastream-paused", "true"); //No I18N
		}

		try
		{
			res.setWriteLimit(-1);

			this.clientSessionId = req.getParameter("sid");
			this.source = req.getParameter("source");
			this.serverSessionId = WCPUtil.getServerSessionId(req.getRemoteAddr()+"_"+clientSessionId);

			String context = req.getParameter("context");
			String remoteServerType = req.getParameter("rst");

			this.sourceCluster = req.getParameter("sourcecluster");
			this.poolName = req.getParameter("pname");

			if(checkIsEmptyForParams(sourceCluster, source, poolName, clientSessionId))
			{
				return false;
			}

			try
			{
				source = sourceCluster;
				servConn = new WCPActiveReplacementConnection(this, remoteServerType, req.getRemoteAddr(), serverSessionId, sourceCluster, poolName, context);
			}
			catch(Exception ex)
			{
				logger.log(Level.SEVERE,"[WCP][WCPARListener - onConnect] sourceCluster="+source+" remoteip="+req.getRemoteAddr()+" poolName="+poolName+" context="+context+" csid="+clientSessionId+" ssid="+serverSessionId, ex);
				return false;
			}

			logger.info("[WCP][WCPARListener initialized!!]["+source+"]["+sourceCluster+"]["+poolName+"]["+context+"]["+req.getRemoteAddr()+"]["+serverSessionId+"]");
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
			logger.log(Level.SEVERE, "WCPERR--> Error while inside verifyToUpgrade WCPARListener sourceCluster="+source+" remoteip="+req.getRemoteAddr()+" csid="+clientSessionId+" ssid="+serverSessionId, e);
		}

		return false;
	}

	public boolean writeData(byte[] data)throws IOException, AWSException
	{
		if(response==null || !response.isActive())
		{
			StatsDB.recordError(ComponentConstants.WCP_ENUM.getModuleCode(), ComponentConstants.WCP_ENUM.WRITE_INVOKED_AFTER_RESPONSE_STREAM_CLOSED_AR.getErrorCode(), 1);
//			close();
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

		WCPSessionManager.getConnection(serverSessionId).onMessage(data);
	}

	public void onClose() throws IOException
	{
		if(WCPSessionManager.getConnection(serverSessionId) == servConn)
		{
			WCPSessionManager.getConnection(serverSessionId).onClose();
		}
		else
		{
			logger.log(Level.INFO, "WCP-> WCPARListener, Connection trying to remove is already removed sid={0}, onClose-conn={1}, alive connection={2} lifetime={3}", new Object[]{serverSessionId, WCPSessionManager.getConnection(serverSessionId), servConn, System.currentTimeMillis() - servletInitTime});
			WCPStats.addWCPErrorStats("WCP_AR_LISTENER_FALSE_CLOSE", "[sid="+serverSessionId+"][srv_startTime="+servletInitTime+"]");
		}
	}

	private boolean checkIsEmptyForParams(String sourceCluster, String source, String poolName, String clientSessionId)
	{
		if(CommonUtil.isEmpty(poolName))
		{
			logger.log(Level.WARNING,"[WCPERR][WCPSrvListener] poolName is null or empty. sourceCluster={0} clientSessionId={1} ssid={2}", new Object[]{sourceCluster, clientSessionId, serverSessionId});

			return true;
		}

		if(CommonUtil.isEmpty(sourceCluster) && CommonUtil.isEmpty(source))
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
