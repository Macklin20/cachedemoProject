//$Id$
package com.zoho.wms.asyncweb.server.grid.wcp.connection;

// java imports
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

//wms common imports
import com.adventnet.wms.common.CommonUtil;
import com.adventnet.wms.common.exception.WMSCommunicationException;

// wms servercommon imports
import com.adventnet.wms.servercommon.dc.DC;
import com.adventnet.wms.servercommon.stats.influx.StatsDB;
import com.adventnet.wms.servercommon.grid.DistributionManager;
import com.adventnet.wms.servercommon.components.net.WCPARModule;
import com.adventnet.wms.servercommon.components.net.stats.WCPStats;
import com.adventnet.wms.servercommon.components.net.WCPSessionManager;
import com.adventnet.wms.servercommon.components.constants.ComponentConstants;

// aws import
import com.zoho.wms.asyncweb.server.grid.servlet.WCPSrvListener;

//wcp import
import com.zoho.wms.wcp.common.WCPSessionInfo;
import com.zoho.wms.wcp.common.WCPThreadLocal;
import com.zoho.wms.wcp.common.wcputil.WCPPacket;
import com.zoho.wms.wcp.common.wcputil.WCPConstants;
import com.zoho.wms.wcp.common.wcputil.WCPPacketizer;
import com.zoho.wms.wcp.common.wcputil.WCPPacketTypes;
import com.zoho.wms.wcp.common.connection.WCPConnection;

public class WCPDefaultServerConnection extends WCPConnection
{
	private static final Logger LOGGER = Logger.getLogger(WCPDefaultServerConnection.class.getName());

	private WCPSrvListener request;

	//private AtomicLong msg_rcv_time = new AtomicLong(0);
	//private AtomicLong msg_rcv_count = new AtomicLong(0);
	private String ownClusterName = DC.getCluster();

	private String context = "default";	//No I18N

	private String reqSource = null;

	private String uri = WCPConstants.WCP_SERVER_URI;

	private String poolName = null;

	WCPSessionInfo sessionInfo = null;

	public WCPDefaultServerConnection(WCPSrvListener request, String remoteServerType, String hostIp, String sid, String source, String poolName, String context, long connOriginTime) throws Exception
	{
		super(DC.getServertype(), remoteServerType, source, hostIp, 0l, sid, null);

		this.request = request;
		this.connectionOriginTime = connOriginTime;  //Change to super in next SC Channel with latest WCP.

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
		addConnStats(WCPConstants.STATS_CONNECTION_ONCONNECT,"server-onconnect");//No I18N
		LOGGER.info("[WCP-Server][New Server Connection Established]["+remoteClusterName+"]["+context+"]["+remoteIp+"]["+sid+"]");
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

				int ackStatusCode = getAckStatusCode(packet);

				if(packet.getPacketType() == WCPPacketTypes.DATA)
				{
					updateMsgRcvTime(packet);
					WCPStats.addWCPHits();

					String sourceIp = packet.getHeader(WCPConstants.IDB.SOURCE_IP);
					String idbEventId = packet.getHeader(WCPConstants.IDB.EVENT_ID);
					StringBuilder sourceDetails = new StringBuilder();
					sourceDetails.append(remoteIp);
					sourceDetails.append(":");
					sourceDetails.append(sid);

					if(sourceIp!=null)
					{
						sourceDetails.append(":");
						sourceDetails.append(sourceIp);
						if(!CommonUtil.isEmpty(idbEventId))
						{
							sourceDetails.append(":");
							sourceDetails.append(idbEventId);
						}
					}

					String rtcpSid = packet.getHeader(WCPConstants.RTCP.RTCP_IDB_SID);
					if(!CommonUtil.isEmpty(rtcpSid))
					{
						Hashtable<String,String> rtcpInfo = new Hashtable<String, String>();
						rtcpInfo.put(WCPConstants.RTCP.RTCP_IDB_SID, rtcpSid);
						rtcpInfo.put(WCPConstants.RTCP.RTCP_PRD, packet.getHeader(WCPConstants.RTCP.RTCP_PRD));
						rtcpInfo.put(WCPConstants.RTCP.RTCP_ZSOID, packet.getHeader(WCPConstants.RTCP.RTCP_ZSOID));
						WCPThreadLocal.setRtcpInfo(rtcpInfo);
					}

					request.writeData(getAckPacket(ackStatusCode, packet));
					WCPStats.addWCPServerData(uri, serverType, remoteServerType,  ownClusterName, remoteClusterName, context, remoteIp, WCPConstants.PacketType.DATA, WCPConstants.CommunicationType.ACK_SENT, packet.getHeaderAsTable());

					WCPSessionManager.processDataPackets(context, sourceDetails.toString(), packet.getPayLoadType(), packet.getPayLoadData(), packet.getHeaderAsTable());
					WCPStats.addWCPServerData(uri, serverType, remoteServerType, ownClusterName, remoteClusterName, context, remoteIp, WCPConstants.PacketType.DATA,WCPConstants.CommunicationType.MSG_RECEIVED, packet.getHeaderAsTable());
				}
				else if(packet.getPacketType() == WCPPacketTypes.ACK)
				{
					int payLoadType = packet.getPayLoadType();
					switch(payLoadType)
					{
					case WCPPacketTypes.NOTIFY:
					case WCPPacketTypes.RESPONSE:
					case WCPPacketTypes.META:
					case WCPPacketTypes.IDB.CB_NOTIFY:
					{
						WCPStats.addWCPServerData(uri, serverType, remoteServerType, ownClusterName, remoteClusterName, context, remoteIp, WCPConstants.PacketType.getPacketTypeString(payLoadType), WCPConstants.CommunicationType.ACK_RECEIVED);
						//WCPStats.addWCPServerRTT(serverType, remoteServerType, context, remoteIp, WCPConstants.PacketType.getPacketTypeString(payLoadType), (System.currentTimeMillis() - rttMap.remove(packet.getPacketId())) );
						break;
					}
					case WCPPacketTypes.CONTROL:
					{
						WCPStats.addWCPServerData(uri, serverType, remoteServerType, ownClusterName, remoteClusterName, context, remoteIp, WCPConstants.PacketType.CONTROL, WCPConstants.CommunicationType.ACK_RECEIVED);
						//WCPStats.addWCPServerRTT(serverType, remoteServerType, context, remoteIp, WCPConstants.PacketType.CONTROL, (System.currentTimeMillis() - rttMap.remove(packet.getPacketId())) );
						WCPARModule.removeControlAck(sid);
						LOGGER.info("HB--> CONTROL-ACK RECEIVED sid="+sid);
						break;
					}
					default :
					{
						StatsDB.recordError(ComponentConstants.WCP_ENUM.getModuleCode(), ComponentConstants.WCP_ENUM.INVALID_ACK_PACKET_TYPE_RECEIVED_IN_SERVER.getErrorCode(), 1);
						WCPStats.addWCPErrorStats("INVALID_ACK_PACKET_TYPE_RECEIVED_IN_SERVER", "[packetType : " + packet.getPacketType() + "][payloadType : " + payLoadType + "]");//No I18N
						LOGGER.info("WCPERR--> Invalid ack_packetType received in server. ack_packetType="+(WCPPacketTypes.isValidPacketType(payLoadType)?WCPConstants.PacketType.getPacketTypeString(payLoadType):payLoadType));

						return;
					}
					}
					//WCPStats.addWCPServerRttSize(context, WCPConstants.WCP_SERVER_URI, serverType, remoteServerType, remoteClusterName, remoteIp, packet.getPacketType(), rttMap.size(), WCPConstants.RTT_OUT);
				}
				else if(packet.getPacketType() == WCPPacketTypes.FORWARD)
				{
					updateMsgRcvTime(packet);
					WCPStats.addWCPHits();

					request.writeData(getAckPacket(ackStatusCode, packet));
					WCPStats.addWCPServerData(uri, serverType, remoteServerType, ownClusterName, remoteClusterName, context, remoteIp, WCPConstants.PacketType.FORWARD, WCPConstants.CommunicationType.ACK_SENT, packet.getHeaderAsTable());

					WCPSessionManager.handleForward(context, remoteIp, packet.getPayLoadType(), packet.getHeaderAsTable(), packet.getPayLoadData());
					WCPStats.addWCPServerData(uri, serverType, remoteServerType, ownClusterName, remoteClusterName, context, remoteIp, WCPConstants.PacketType.FORWARD, WCPConstants.CommunicationType.MSG_RECEIVED, packet.getHeaderAsTable());
				}
				else if(packet.getPacketType() == WCPPacketTypes.REQUEST)
				{
					updateMsgRcvTime(packet);
					WCPStats.addWCPHits();

					request.writeData(getAckPacket(ackStatusCode, packet));
					WCPStats.addWCPServerData(uri, serverType, remoteServerType, ownClusterName, remoteClusterName, context, remoteIp, WCPConstants.PacketType.REQUEST, WCPConstants.CommunicationType.ACK_SENT, packet.getHeaderAsTable());

					if(reqSource!=null)
					{
						WCPSessionManager.handleWCPRequest(uri, reqSource, remoteServerType, remoteIp, packet.getHeaderAsTable(), sid, packet.getPayLoadData());
					}
					else
					{
						WCPSessionManager.handleWCPRequest(remoteServerType, remoteIp, packet.getHeaderAsTable(), sid, packet.getPayLoadData(), WCPConstants.WCP_SERVER_URI);
					}
					WCPStats.addWCPServerData(uri, serverType, remoteServerType, ownClusterName, remoteClusterName, context, remoteIp, WCPConstants.PacketType.REQUEST, WCPConstants.CommunicationType.MSG_RECEIVED, packet.getHeaderAsTable());
				}
				else
				{
					StatsDB.recordError(ComponentConstants.WCP_ENUM.getModuleCode(), ComponentConstants.WCP_ENUM.INVALID_PACKET_TYPE_RECEIVED_IN_SERVER.getErrorCode(), 1);
					WCPStats.addWCPErrorStats("INVALID_PACKET_TYPE_RECEIVED_IN_SERVER", "[packetType : " + packet.getPacketType() + "]");//No I18N
					LOGGER.info("WCPERR--> Invalid packetType received in server. packetType="+(WCPPacketTypes.isValidPacketType(packet.getPacketType())?WCPConstants.PacketType.getPacketTypeString(packet.getPacketType()):packet.getPacketType()));
					return;
				}
			}
		}
		catch(Exception ex)
		{
			LOGGER.log(Level.SEVERE,"[WCPDefaultServerConnection - onMessage]["+context+"]["+remoteIp+"]", ex);
		}
		finally
		{
			WCPThreadLocal.clear();
			WCPSessionManager.clearCVMismatch();
		}
	}

	private byte[] getAckPacket(int ackStatusCode, WCPPacket packet)
	{
		try
		{
			WCPPacket ack_packet = WCPPacketizer.getPacketFromData(packet.getPacketId(), WCPPacketTypes.ACK, packet.getPacketType());
			ack_packet.setStatusCode(ackStatusCode);

			if(packet.getQTime() != -1)
			{
				ack_packet.setQTime(packet.getQTime());
			}

			if(packet.getNetInTime() != -1)
			{
				ack_packet.setNetInTime(packet.getNetInTime());
			}

			return ack_packet.getCompletePacketData();
		}
		catch (Exception e)
		{
			LOGGER.log(Level.SEVERE, "Exception while getting ack-packet");
			return null;
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
			case WCPPacketTypes.IDB.CB_NOTIFY:
			{
				WCPStats.addWCPServerData(serverType, remoteServerType, ownClusterName, remoteClusterName, context, remoteIp, WCPConstants.PacketType.getPacketTypeString(packetType), WCPConstants.CommunicationType.MSG_SENT);
				break;
			}
			default :
			{
				StatsDB.recordError(ComponentConstants.WCP_ENUM.getModuleCode(), ComponentConstants.WCP_ENUM.INVALID_PACKET_TYPE_SENT_FROM_SERVER.getErrorCode(), 1);
				WCPStats.addWCPErrorStats("INVALID_PACKET_TYPE_SENT_FROM_SERVER", "[packetType : " + packet.getPacketType() + "]"); //No I18N
				LOGGER.info("WCPERR--> Invalid packetType sent in server. packetType="+(WCPPacketTypes.isValidPacketType(packetType)?WCPConstants.PacketType.getPacketTypeString(packetType):packetType));
				return false;
			}
			}

			byte[] data = packet.getCompletePacketData();
			WCPStats.addWCPServerDataSize(uri, serverType, remoteServerType, ownClusterName, remoteClusterName, context, remoteIp, WCPConstants.PacketType.getPacketTypeString(packetType), data.length);

			return request.writeData(data);
		}
		catch(WMSCommunicationException ex)
		{
			throw new Exception(ex.getMessage());
		}
	}

	public void updateMsgRcvTime(WCPPacket packet)
	{
		try
		{
			if(packet!=null)
			{			
				packet.addHeader(WCPConstants.PacketHeader.SERVLET_IN_TIME, Long.toString(System.currentTimeMillis()));
			}
		}
		catch(Exception e)
		{
		}
	}

	private int getAckStatusCode(WCPPacket packet)
	{
		if(packet.needsCVCheck())
		{
			try
			{
				if(!DistributionManager.isSameClusterVersion(ownClusterName, packet.getClusterVersion()))
				{
					WCPStats.addWCPCVMismatch(ownClusterName, packet.getClusterVersion(), DistributionManager.getClusterVersion(ownClusterName));
					WCPSessionManager.setCVMismatch();
					DistributionManager.refreshClusterVersion(ownClusterName);
					return WCPConstants.StatusCode.CLUSTER_VERSION_MISMATCH;
				}
			}
			catch(Exception e)
			{
			}
		}

		return WCPConstants.StatusCode.SUCCESS;
	}

	public long getAvgMsgRecieveTime()
	{
		//return (msg_rcv_time.get()/msg_rcv_count.get());
		return 0l;
	}

	public void close() throws Exception
	{
		request.close();
	}

	public void onClose()
	{
		try
		{
			sessionInfo.clear();
			sessionInfo = null;
			WCPThreadLocal.clear();

			WCPSessionManager.unRegisterStream(reqSource, uri, remoteClusterName, remoteIp, poolName, context, sid);
		}
		catch(Exception ex)
		{
			LOGGER.log(Level.SEVERE,"[WCPDefaultServerConnection - onClose]["+context+"]["+remoteIp+"]["+sid+"]", ex);
		}
	}

	public boolean isAlive()
	{
		return request.isActive();
	}

	public WCPSrvListener getRequest()
	{
		return this.request;
	}


	public long getConnectionOriginTime()
	{
		return connectionOriginTime;
	}
}
