package com.zoho.wms.asyncweb.server.grid.servlet;

// java imports
import java.io.IOException;
import java.util.logging.Logger;

// aws imports
import com.zoho.wms.asyncweb.server.http.WebSocket;
import com.zoho.wms.asyncweb.server.http.HttpRequest;
import com.zoho.wms.asyncweb.server.http.HttpResponse;

// wms servercommon import
import com.adventnet.wms.servercommon.components.queue.BQInventory;

public class BufferedQueueDTServlet extends WebSocket
{
	private static final Logger LOGGER = Logger.getLogger(BufferedQueueDTServlet.class.getName());

	public void onConnect(HttpRequest req, HttpResponse res) throws IOException
	{
		LOGGER.info("NS--> Connected made successfully. remoteip="+request.getRemoteAddr());
		res.setWriteLimit(-1);
	}

	public void onMessage(String data) throws IOException
	{
	}

	public void onMessage(byte[] data) throws IOException
	{
		BQInventory.addBQPacket(data);
	}

	public void onClose() throws IOException
	{
		LOGGER.info("NS--> Connected closed successfully. remoteip="+request.getRemoteAddr());

	}

	public void onPingMessage(byte[] data) throws IOException
	{

	}

	public void onPongMessage(byte[] data) throws IOException
	{

	}
}
