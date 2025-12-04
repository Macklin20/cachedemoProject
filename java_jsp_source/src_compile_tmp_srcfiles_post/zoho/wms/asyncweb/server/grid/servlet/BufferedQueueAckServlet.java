//$Id$
package com.zoho.wms.asyncweb.server.grid.servlet;

//java imports
import java.io.IOException;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

//servercommon imports
import com.adventnet.wms.common.HttpDataWraper;
import com.adventnet.wms.servercommon.components.queue.BQInventory;
import com.zoho.wms.asyncweb.server.http.WebSocket;
import com.zoho.wms.asyncweb.server.http.HttpRequest;
import com.zoho.wms.asyncweb.server.http.HttpResponse;

public class BufferedQueueAckServlet extends WebSocket
{
	private static final Logger LOGGER = Logger.getLogger(BufferedQueueAckServlet.class.getName());

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
		try
		{
			@SuppressWarnings("unchecked") //NO I18N
			Hashtable<String, String> status = (Hashtable<String, String>) HttpDataWraper.getObject(new String(data,"UTF-8")); //NO I18N
			LOGGER.info("HB--> headers="+status); //NO I18N
			BQInventory.updateBQAck(status);
		}
		catch (Exception e)
		{
			LOGGER.log(Level.SEVERE, "HB--> Error while receiving bqAck", e);
		}
	}

	public void onClose() throws IOException
	{
		LOGGER.info("NS--> Ack Connection closed successfully. remoteip="+request.getRemoteAddr());
	}

	public void onPingMessage(byte[] data) throws IOException
	{

	}

	public void onPongMessage(byte[] data) throws IOException
	{

	}
}
