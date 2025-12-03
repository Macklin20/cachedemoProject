package com.zoho.wms.asyncweb.server;

// Java import
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;


public class ClientManager
{
	private static ConcurrentHashMap<String, AsyncWebClient> clientMap = new ConcurrentHashMap(100);

	public static AsyncWebClient createClient(SelectionKey key,int port,long stime) throws IOException
	{
		AsyncWebClient newClient = new AsyncWebClient(key, port, stime);
		clientMap.put(newClient.toString(), newClient);
		return newClient;
	}

	public static String addClient(AsyncWebClient client)
	{
		if(client != null)
		{
			clientMap.put(client.toString(), client);
		}

		return client.toString();
	}

	public static AsyncWebClient getClient(String clientId)
	{
		if(clientId != null)
		{
			return clientMap.get(clientId);
		}
		return null;
	}

	public static AsyncWebClient removeClient(String clientId)
	{
		if(clientId != null )
		{
			return clientMap.remove(clientId);
		}
		return null;
	}

	public static AsyncWebClient removeClient(AsyncWebClient client)
	{
		if(client != null)
		{
			return removeClient(client.toString());
		}
		return null;
	}

	public static String getIPAddress(String clientId)
	{
		AsyncWebClient client = getClient(clientId);
		if(client != null)
		{
			return client.getIPAddress();
		}
		return null;
	}

	public static long closeAllClients()
	{
		long clientCount = clientMap.size();

		Iterator itr = clientMap.values().iterator();
		while(itr.hasNext())
		{
			AsyncWebClient client = (AsyncWebClient) itr.next();
			client.close(AWSConstants.CLOSE_ALL_CLIENTS);
		}

		return clientCount;
	}
}
