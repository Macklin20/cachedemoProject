//$Id$
package com.zoho.wms.asyncweb.server.grid.wcp.connection;

// java imports
import java.util.logging.Level;
import java.util.logging.Logger;
//import java.util.concurrent.atomic.AtomicLong;

// wms common imports
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
import com.zoho.wms.asyncweb.server.grid.servlet.WCPAPIListener;

//wcp import
import com.zoho.wms.wcp.common.WCPSessionInfo;
import com.zoho.wms.wcp.common.WCPThreadLocal;
import com.zoho.wms.wcp.common.connection.WCPConnection;
import com.zoho.wms.wcp.common.wcputil.*;

public class WCPAPIConnection extends WCPConnection
{
	private Logger logger = Logger.getLogger(WCPAPIConnection.class.getName());

	private static ThreadLocal<Boolean> isReadOnlyMode = new ThreadLocal<Boolean>();

	private WCPAPIListener request;

	//private AtomicLong msg_rcv_time = new AtomicLong(0);
	//private AtomicLong msg_rcv_count = new AtomicLong(0);

	private String ownClusterName = DC.getCluster();

	private String context = "default";//No I18N

	private String reqSource = null;

	private String uri = WCPConstants.WCP_API_URI;

	private String poolName = null;

	private String servingMode = null;
	private boolean readOnlyMode = false;

	WCPSessionInfo sessionInfo = null;

	public WCPAPIConnection(WCPAPIListener request, String remoteServerType, String hostIp, String sid, String source, String setup, String poolName, String context, String servingMode, String iamServiceName, long connOriginTime) throws Exception
	{
		super(DC.getServertype(), remoteServerType, source+"-"+setup, hostIp, 0l, sid, null);

		this.request = request;
		this.connectionOriginTime = connOriginTime;  //Change to super in next SC Channel with latest WCP.
		if(context != null)
		{
			this.context = context;
		}

		reqSource = uri+"|"+remoteClusterName+"|"+hostIp+"|"+poolName;
		this.poolName = poolName;

		if(!WCPUtil.isNull(servingMode))
		{
			this.servingMode = servingMode;
			if("ro".equalsIgnoreCase(servingMode)) //NO I18N
			{
				readOnlyMode = true;
			}
		}

		boolean status = WCPSessionManager.registerStream(reqSource, uri, remoteClusterName, hostIp, poolName, context, sid, this);
		if(!status)
		{
			reqSource = null;
			throw new Exception("Exception while registerStream");
		}

		sessionInfo = new WCPSessionInfo();
		sessionInfo.setAPISessionInfo(WCPConstants.ClientType.API, remoteServerType, source.split("-")[0], setup, poolName, context, remoteIp, iamServiceName);
	}

	public void onConnect()
	{
		addConnStats(WCPConstants.STATS_CONNECTION_ONCONNECT,"api-onconnect");//No I18N
		logger.info("[WCP-API][New Server Connection Established]["+remoteClusterName+"]["+context+"]["+remoteIp+"]["+sid+"]");
	}

	public void onMessage(byte[] data)
	{
		try
		{
			if(servingMode!=null)
			{
				setReadOnlyMode(readOnlyMode);
			}

			if(data != null)
			{
				WCPPacket packet = WCPPacketizer.getDataFromPacket(data);
				if(packet==null)
				{

					return;
				}

				int ackStatusCode = getAckStatusCode(packet);
				WCPThreadLocal.setSessionInfo(sessionInfo);

				if(packet.getPacketType() == WCPPacketTypes.DATA)
				{
					updateMsgRcvTime(packet);
					WCPStats.addWCPHits();

					WCPSessionManager.processDataPackets(context, remoteIp+":"+sid, packet.getPayLoadType(), packet.getPayLoadData(), packet.getHeaderAsTable());// No I18N
					WCPStats.addWCPServerAPIData(serverType, remoteServerType, ownClusterName, remoteClusterName, context, WCPConstants.PacketType.DATA,WCPConstants.CommunicationType.MSG_RECEIVED, remoteIp);

					WCPPacket ack_packet = WCPPacketizer.getPacketFromData(packet.getPacketId(), WCPPacketTypes.ACK, WCPPacketTypes.DATA);
					ack_packet.setStatusCode(ackStatusCode);
					request.writeData(ack_packet.getCompletePacketData());

					WCPStats.addWCPServerAPIData(serverType, remoteServerType, ownClusterName, remoteClusterName, context, WCPConstants.PacketType.DATA, WCPConstants.CommunicationType.ACK_SENT, remoteIp);
				}
				else if(packet.getPacketType() == WCPPacketTypes.ACK)
				{
					int payLoadType = packet.getPayLoadType();
					switch(payLoadType)
					{
					case WCPPacketTypes.NOTIFY:
					case WCPPacketTypes.RESPONSE:
					case WCPPacketTypes.META:
					case WCPPacketTypes.CB_REQUEST:
					{
						WCPStats.addWCPServerAPIData(serverType, remoteServerType, ownClusterName, remoteClusterName, context, WCPConstants.PacketType.getPacketTypeString(payLoadType), WCPConstants.CommunicationType.ACK_RECEIVED, remoteIp);
						//WCPStats.addWCPServerRTT(serverType, remoteServerType, context, remoteIp, WCPConstants.PacketType.getPacketTypeString(payLoadType), System.currentTimeMillis() - (rttMap.remove(packet.getPacketId())));
						break;
					}
					case WCPPacketTypes.CONTROL:
					{
						WCPStats.addWCPServerAPIData(serverType, remoteServerType, ownClusterName, remoteClusterName, context, WCPConstants.PacketType.getPacketTypeString(payLoadType), WCPConstants.CommunicationType.ACK_RECEIVED, remoteIp);
						//WCPStats.addWCPServerRTT(serverType, remoteServerType, context, remoteIp, WCPConstants.PacketType.CONTROL, (System.currentTimeMillis() - rttMap.remove(packet.getPacketId())) );

						WCPARModule.removeControlAck(sid);

						logger.info("HB--> CONTROL-ACK RECEIVED sid="+sid);

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
					//WCPStats.addWCPServerRttSize(context, WCPConstants.WCP_API_URI, serverType, remoteServerType, remoteClusterName, remoteIp, packet.getPacketType(), rttMap.size(), WCPConstants.RTT_OUT);
				}
				else if(packet.getPacketType() == WCPPacketTypes.FORWARD)
				{
					updateMsgRcvTime(packet);
					WCPStats.addWCPHits();

					WCPSessionManager.handleForward(context, remoteIp, packet.getPayLoadType(), packet.getHeaderAsTable(), packet.getPayLoadData());
					WCPStats.addWCPServerAPIData(serverType, remoteServerType, ownClusterName, remoteClusterName, context, WCPConstants.PacketType.FORWARD, WCPConstants.CommunicationType.MSG_RECEIVED, remoteIp);

					WCPPacket ack_packet = WCPPacketizer.getPacketFromData(packet.getPacketId(), WCPPacketTypes.ACK, WCPPacketTypes.FORWARD);
					ack_packet.setStatusCode(ackStatusCode);
					request.writeData(ack_packet.getCompletePacketData());

					WCPStats.addWCPServerAPIData(serverType, remoteServerType, ownClusterName, remoteClusterName, context, WCPConstants.PacketType.FORWARD, WCPConstants.CommunicationType.ACK_SENT, remoteIp);
				}
				else if(packet.getPacketType() == WCPPacketTypes.REQUEST)
				{
					updateMsgRcvTime(packet);
					WCPStats.addWCPHits();

					if(reqSource!=null)
					{
						WCPSessionManager.handleWCPRequest(uri, reqSource, remoteServerType, remoteIp, packet.getHeaderAsTable(), sid, packet.getPayLoadData());
					}
					else
					{
						WCPSessionManager.handleWCPRequest(remoteServerType, remoteIp, packet.getHeaderAsTable(), sid, packet.getPayLoadData(), WCPConstants.WCP_API_URI);
					}

					WCPStats.addWCPServerAPIData(serverType, remoteServerType, ownClusterName, remoteClusterName, context, WCPConstants.PacketType.REQUEST, WCPConstants.CommunicationType.MSG_RECEIVED, remoteIp, packet.getHeaderAsTable());

					WCPPacket ack_packet = WCPPacketizer.getPacketFromData(packet.getPacketId(), WCPPacketTypes.ACK, WCPPacketTypes.REQUEST);
					ack_packet.setStatusCode(ackStatusCode);
					request.writeData(ack_packet.getCompletePacketData());

					WCPStats.addWCPServerAPIData(serverType, remoteServerType, ownClusterName, remoteClusterName, context, WCPConstants.PacketType.REQUEST, WCPConstants.CommunicationType.ACK_SENT, remoteIp, packet.getHeaderAsTable());
				}
				else if(packet.getPacketType() == WCPPacketTypes.CB_RESPONSE)
				{
					WCPStats.addWCPHits();
					WCPStats.addWCPServerAPIData(serverType, remoteServerType, ownClusterName, remoteClusterName, context, WCPConstants.PacketType.CB_RESPONSE, WCPConstants.CommunicationType.MSG_RECEIVED, remoteIp);
					String id = packet.getHeaderAsTable().get("reqid"); //No I18N
					WCPSessionManager.receiveCBResponse(id,packet);

					//					logger.info("HB--> WCPCBResponse Received["+poolName+"]["+remoteIp+"]["+sid+"]["+id+"]");
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
			logger.log(Level.SEVERE,"[WCPAPIConnection - onMessage]["+context+"]["+remoteIp+"]", ex);
		}
		finally
		{
			WCPThreadLocal.clear();
			clearReadOnlyMode();
		}
	}

	public boolean writeData(WCPPacket packet)	throws Exception
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
			case WCPPacketTypes.CB_REQUEST:
			{
				WCPStats.addWCPServerAPIData(serverType, remoteServerType, ownClusterName, remoteClusterName, context, WCPConstants.PacketType.getPacketTypeString(packetType), WCPConstants.CommunicationType.MSG_SENT, remoteIp);
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
		/*
		try
		{
			long time = Long.parseLong(tm);
			msg_rcv_time.addAndGet(System.currentTimeMillis() - time);
			msg_rcv_count.incrementAndGet();
		}
		catch(Exception ex)
		{
		}
		 */
	}

	public long getAvgMsgRecieveTime()
	{
		return 0l;
		//return (msg_rcv_time.get()/msg_rcv_count.get());
	}

	public void close()throws Exception
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
			logger.log(Level.SEVERE,"[WCPApiConnection - onClose]["+context+"]["+remoteIp+"]["+sid+"]", ex);
		}
	}

	public static void setReadOnlyMode(Boolean readOnlyMode)
	{
		isReadOnlyMode.set(readOnlyMode);
	}

	public static boolean isReadOnlyMode()
	{
		return (isReadOnlyMode.get()!=null?isReadOnlyMode.get():false);
	}

	public static void clearReadOnlyMode()
	{
		isReadOnlyMode.remove();
	}

	public boolean isAlive()
	{
		return request.isActive();
	}

	public WCPAPIListener getRequest()
	{
		return this.request;
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

//					WCPSessionManager.setCVMismatch();  is needed ..?

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

	public long getConnectionOriginTime()
	{
		return connectionOriginTime;
	}
}