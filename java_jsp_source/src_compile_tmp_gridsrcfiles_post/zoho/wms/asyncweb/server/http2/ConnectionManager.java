package com.zoho.wms.asyncweb.server.http2;

// java import
import java.util.concurrent.ConcurrentHashMap;

// aws import
import com.zoho.wms.asyncweb.server.AsyncWebClient;
import com.zoho.wms.asyncweb.server.ClientManager;
import com.zoho.wms.asyncweb.server.WmsSessionIdGenerator;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;


/**
 *
 * @author durai - 11882
 *
 */


public class ConnectionManager
{
    private static AsyncLogger logger = new AsyncLogger(Http2Connection.class.getName());

    private static ConcurrentHashMap<String, Http2Connection> connectionMap = new ConcurrentHashMap(10);

    public static String registerConnection(AsyncWebClient client) throws Exception
    {
        String clientID = ClientManager.addClient(client);

        String http2ConnID = "H2C_" + WmsSessionIdGenerator.getUniqueId();
        Http2Connection http2Conn = new Http2Connection(http2ConnID, clientID);
        connectionMap.put(http2ConnID, http2Conn);

        return http2ConnID;
    }

    public static void unRegisterConnection(String http2ConnID)
    {
        Http2Connection http2Conn = removeConnection(http2ConnID);
        if(http2Conn != null && !http2Conn.isConnectionClosed())
        {
            http2Conn.closeConnection(true);
        }
    }

    public static Http2Connection removeConnection(String http2ConnID)
    {
        return connectionMap.remove(http2ConnID);
    }

    public static Http2Connection getConnection(String http2ConnID)
    {
        if(http2ConnID != null)
        {
            return connectionMap.get(http2ConnID);
        }
        return null;
    }

    public static Http2Stream getStream(String http2ConnID, int streamID)
    {
        if(http2ConnID != null && streamID >= 0)
        {
            try
            {
                return  getConnection(http2ConnID).getStream(streamID);
            }
            catch(Exception e)
            {
                return null;
            }
        }

        return null;
    }

    public static void removeStream(String http2ConnID, int streamID)
    {
        Http2Connection http2Conn = getConnection(http2ConnID);
        if(http2Conn != null)
        {
            http2Conn.removeStream(streamID);
        }
    }

    public static ConcurrentHashMap<String, Http2Connection> getConnectionMap()
    {
        return connectionMap;
    }

    public static long getActiveHttp2ConnectionCount()
    {
        return connectionMap.size();
    }

}
