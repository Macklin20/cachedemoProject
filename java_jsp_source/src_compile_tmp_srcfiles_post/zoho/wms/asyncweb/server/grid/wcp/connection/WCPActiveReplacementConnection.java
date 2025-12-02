//$Id$
package com.zoho.wms.asyncweb.server.grid.wcp.connection;

// java imports
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.atomic.AtomicLong;

//wms common imports
import com.adventnet.wms.common.exception.WMSCommunicationException;

// wms servercommon imports
import com.adventnet.wms.servercommon.dc.DC;
import com.adventnet.wms.servercommon.stats.influx.StatsDB;
import com.adventnet.wms.servercommon.components.net.stats.WCPStats;
import com.adventnet.wms.servercommon.components.net.WCPSessionManager;
import com.adventnet.wms.servercommon.components.constants.ComponentConstants;

// aws import
import com.zoho.wms.asyncweb.server.grid.servlet.WCPARListener;

//wcp import
import com.zoho.wms.wcp.common.WCPSessionInfo;
import com.zoho.wms.wcp.common.WCPThreadLocal;
import com.zoho.wms.wcp.common.connection.WCPConnection;
import com.zoho.wms.wcp.common.wcputil.WCPConstants;
import com.zoho.wms.wcp.common.wcputil.WCPPacket;
import com.zoho.wms.wcp.common.wcputil.WCPPacketTypes;
import com.zoho.wms.wcp.common.wcputil.WCPPacketizer;

public class WCPActiveReplacementConnection extends WCPConnection
{
	private Logger logger = Logger.getLogger(WCPActiveReplacementConnection.class.getName());

	private WCPARListener request;

	private AtomicLong msg_rcv_time = new AtomicLong(0);
	private AtomicLong msg_rcv_count = new AtomicLong(0);
	private String ownClusterName = DC.getCluster();

	// Context here is for active replacement
	private String context = "default_ar";//No I18N

	private String reqSource = null;

	private String poolName = null;

	private String uri = "ar";//No I18N

	WCPSessionInfo sessionInfo = null;

	public WCPActiveReplacementConnection(WCPARListener request, String remoteServerType, String hostIp, String sid, String source, String poolName, String context)	throws Exception
	{
		super(DC.getServertype(), remoteServerType, source, hostIp, 0l, sid, null);
		this.request = request;
		if(context != null)
		{
			this.context = context;
		}

		reqSource = uri+"|"+source+"|"+hostIp+"|"+poolName;
		this.poolName = poolName;
		boolean status = WCPSessionManager.registerStream(reqSource, uri, source, hostIp, poolName, context, sid, this);
		if(!status)
		{
			reqSource = null;
			throw new Exception("Exception while registerStream");
		}

		sessionInfo = new WCPSessionInfo();
		sessionInfo.setServerSessionInfo(WCPConstants.ClientType.SERVER, remoteServerType, remoteClusterName, poolName, context, remoteIp);
	}

	public void onConnect()
	{
		addConnStats(WCPConstants.STATS_CONNECTION_ONCONNECT,"ar-onconnect");//No I18N
		logger.info("[WCP-Server][New Server Connection Established]["+remoteClusterName+"]["+context+"]["+remoteIp+"]["+sid+"]");
	}

	public void onMessage(byte[] data)
	{
		try
		{
			if(data != null)
			{
				WCPPacket packet = WCPPacketizer.getDataFromPacket(data);
				if(packet == null)
				{
					return;
				}

				WCPThreadLocal.setSessionInfo(sessionInfo);

				if(packet.getPacketType() == WCPPacketTypes.DATA)
				{
					updateMsgRcvTime(""+(packet.getHeaderAsTable().get("time")));//No I18N
					WCPStats.addWCPHits();

					WCPSessionManager.processDataPackets(context, remoteIp+":"+sid, packet.getPayLoadType(), new String(packet.getPayLoadData(),"UTF-8"));//No I18N
					WCPStats.addWCPServerData(uri, serverType, remoteServerType, ownClusterName, remoteClusterName, context, remoteIp, WCPConstants.PacketType.DATA,WCPConstants.CommunicationType.MSG_RECEIVED);

					WCPPacket ack_packet = WCPPacketizer.getPacketFromData(packet.getPacketId(), WCPPacketTypes.ACK, WCPPacketTypes.DATA);
					request.writeData(ack_packet.getCompletePacketData());

					WCPStats.addWCPServerData(uri, serverType, remoteServerType, ownClusterName, remoteClusterName, context, remoteIp, WCPConstants.PacketType.DATA, WCPConstants.CommunicationType.ACK_SENT);
				}
				else if(packet.getPacketType() == WCPPacketTypes.ACK)
				{
					int payLoadType = packet.getPayLoadType();
					switch(payLoadType)
					{
					case WCPPacketTypes.NOTIFY:
					case WCPPacketTypes.RESPONSE:
					case WCPPacketTypes.META:
					{
						WCPStats.addWCPServerData(uri, serverType, remoteServerType, ownClusterName, remoteClusterName, context, remoteIp, WCPConstants.PacketType.getPacketTypeString(payLoadType), WCPConstants.CommunicationType.ACK_RECEIVED);
						//WCPStats.addWCPServerRTT(serverType, remoteServerType, context, remoteIp, WCPConstants.PacketType.getPacketTypeString(payLoadType), (System.currentTimeMillis() - rttMap.remove(packet.getPacketId())) );
						break;
					}
					default :
					{
						StatsDB.recordError(ComponentConstants.WCP_ENUM.getModuleCode(), ComponentConstants.WCP_ENUM.INVALID_ACK_PACKET_TYPE_RECEIVED_IN_SERVER.getErrorCode(), 1);
						WCPStats.addWCPErrorStats("INVALID_ACK_PACKET_TYPE_RECEIVED_IN_SERVER", "[packetType : " + packet.getPacketType() + "][payloadType : " + payLoadType + "]");//No I18N
						logger.info("WCPERR--> Invalid ack_packetType received in server. ack_packetType="+(WCPPacketTypes.isValidPacketType(payLoadType)?WCPConstants.PacketType.getPacketTypeString(payLoadType):payLoadType));

						return;
					}
					}
					//WCPStats.addWCPServerRttSize(context, WCPConstants.WCP_AR_URI, serverType, remoteServerType, remoteClusterName, remoteIp, packet.getPacketType(), rttMap.size(), WCPConstants.RTT_OUT);
				}
				else if(packet.getPacketType() == WCPPacketTypes.FORWARD)
				{
					updateMsgRcvTime(""+(packet.getHeaderAsTable().get("time")));//No I18N
					WCPStats.addWCPHits();

					WCPSessionManager.handleForward(context, remoteIp, packet.getPayLoadType(), packet.getHeaderAsTable(), packet.getPayLoadData());
					WCPStats.addWCPServerData(uri, serverType, remoteServerType, ownClusterName, remoteClusterName, context, remoteIp, WCPConstants.PacketType.FORWARD, WCPConstants.CommunicationType.MSG_RECEIVED);

					WCPPacket ack_packet = WCPPacketizer.getPacketFromData(packet.getPacketId(), WCPPacketTypes.ACK, WCPPacketTypes.FORWARD);
					request.writeData(ack_packet.getCompletePacketData());

					WCPStats.addWCPServerData(uri, serverType, remoteServerType, ownClusterName, remoteClusterName, context, remoteIp, WCPConstants.PacketType.FORWARD, WCPConstants.CommunicationType.ACK_SENT);
				}
				else if(packet.getPacketType() == WCPPacketTypes.REQUEST)
				{
					updateMsgRcvTime(""+(packet.getHeaderAsTable().get("time")));//No I18N
					WCPStats.addWCPHits();

					if(reqSource!=null)
					{
						WCPSessionManager.handleWCPRequest(uri, reqSource, remoteServerType, remoteIp, packet.getHeaderAsTable(), sid, packet.getPayLoadData());
					}
					else
					{
						WCPSessionManager.handleWCPRequest(remoteServerType, remoteIp, packet.getHeaderAsTable(), sid, packet.getPayLoadData(), WCPConstants.WCP_SERVER_URI);
					}
					WCPStats.addWCPServerData(uri, serverType, remoteServerType, remoteIp, ownClusterName, remoteClusterName, context, WCPConstants.PacketType.REQUEST, WCPConstants.CommunicationType.MSG_RECEIVED);

					WCPPacket ack_packet = WCPPacketizer.getPacketFromData(packet.getPacketId(), WCPPacketTypes.ACK, WCPPacketTypes.REQUEST);
					request.writeData(ack_packet.getCompletePacketData());

					WCPStats.addWCPServerData(uri, serverType, remoteServerType, ownClusterName, remoteClusterName, context, remoteIp, WCPConstants.PacketType.REQUEST, WCPConstants.CommunicationType.ACK_SENT);
				}
				else
				{
					StatsDB.recordError(ComponentConstants.WCP_ENUM.getModuleCode(), ComponentConstants.WCP_ENUM.INVALID_PACKET_TYPE_RECEIVED_IN_SERVER.getErrorCode(), 1);
					WCPStats.addWCPErrorStats("INVALID_PACKET_TYPE_RECEIVED_IN_SERVER", "[packetType : " + packet.getPacketType() + "]");//No I18N
					logger.info("WCPERR--> Invalid packetType received in server. packetType="+(WCPPacketTypes.isValidPacketType(packet.getPacketType())?WCPConstants.PacketType.getPacketTypeString(packet.getPacketType()):packet.getPacketType()));

					return;
				}
			}
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE,"[WCPActiveReplacementConnection - onMessage]["+context+"]["+remoteIp+"]", ex);
		}
		finally
		{
			WCPThreadLocal.clear();
			WCPSessionManager.clearCVMismatch();
		}
	}

	public boolean writeData(WCPPacket packet)throws Exception
	{
		try
		{
			long pcktId = packetId.incrementAndGet();
			packet.setPacketId(pcktId);
			int packetType = packet.getPacketType();

			switch(packetType)
			{
			case WCPPacketTypes.NOTIFY:
			case WCPPacketTypes.RESPONSE:
			case WCPPacketTypes.META:
			case WCPPacketTypes.CONTROL:
			{
				WCPStats.addWCPServerData(uri, serverType, remoteServerType, ownClusterName, remoteClusterName, context, remoteIp, WCPConstants.PacketType.getPacketTypeString(packetType), WCPConstants.CommunicationType.MSG_SENT);
				break;
			}
			default :
			{
				StatsDB.recordError(ComponentConstants.WCP_ENUM.getModuleCode(), ComponentConstants.WCP_ENUM.INVALID_PACKET_TYPE_SENT_FROM_SERVER.getErrorCode(), 1);
				WCPStats.addWCPErrorStats("INVALID_PACKET_TYPE_SENT_FROM_SERVER", "[packetType : " + packet.getPacketType() + "]");//No I18N
				logger.info("WCPERR--> Invalid packetType sent in server. packetType="+(WCPPacketTypes.isValidPacketType(packetType)?WCPConstants.PacketType.getPacketTypeString(packetType):packetType));

				return false;
			}
			}
			//WCPStats.addWCPServerRttSize(context, WCPConstants.WCP_AR_URI, serverType, remoteServerType, remoteClusterName, remoteIp, packet.getPacketType(), rttMap.size(), WCPConstants.RTT_IN);

			return request.writeData(packet.getCompletePacketData());
		}
		catch(WMSCommunicationException ex)
		{
			throw new Exception(ex.getMessage());
		}
	}

	public void updateMsgRcvTime(String tm)
	{
		try
		{
			long time = Long.parseLong(tm);
			msg_rcv_time.addAndGet(System.currentTimeMillis() - time);
			msg_rcv_count.incrementAndGet();
		}
		catch(Exception ex)
		{
		}
	}

	public long getAvgMsgRecieveTime()
	{
		return (msg_rcv_time.get()/msg_rcv_count.get());
	}

	public void close()throws Exception
	{
		request.close();
	}

	public void onClose()
	{
		try
		{
			WCPSessionManager.unRegisterStream(reqSource, uri, remoteClusterName, remoteIp, poolName, context, sid);

			sessionInfo.clear();
			sessionInfo = null;
			WCPThreadLocal.clear();
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE,"[WCPActiveReplacementConnection - onClose]["+context+"]["+remoteIp+"]["+sid+"]", ex);
		}
	}

	public boolean isAlive()
	{
		return request.isActive();
	}
}