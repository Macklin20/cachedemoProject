//$Id$
package com.zoho.wms.asyncweb.server.grid.servlet;

//java imports
import java.io.File;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.atomic.AtomicLong;

//aws imports
import com.zoho.wms.asyncweb.server.http.HttpRequest;
import com.zoho.wms.asyncweb.server.http.HttpResponse;
import com.zoho.wms.asyncweb.server.http.HttpStreamServlet;

//servercommon imports
import com.adventnet.wms.servercommon.components.file.AbsFileHandler;

public class ARFileTransferServlet extends HttpStreamServlet
{
	private static final Logger LOGGER = Logger.getLogger(ARFileTransferServlet.class.getName());

	private static final Hashtable<String, AbsFileHandler> FILEHANDLERMAP = new Hashtable<>();
	private static final AtomicLong REQUEST_ID = new AtomicLong();

	@Override
	public void onHeaderCompletion(HttpRequest req, HttpResponse res) throws Exception
	{
		try
		{
			LOGGER.fine("VS_AWS --> ARFileTransferServlet --> InSide onHeaderCompletion"); //No I18N 


			String reqId = Long.toString(REQUEST_ID.incrementAndGet());
			LOGGER.info("VS_AWS --> onHeaderCompletion --> RequestId --> " + reqId);        //No I18N    

			req.addHeader("_reqid", reqId);            //No I18N
			res.setInputDataSize(req.getContentLength());
		}
		catch(Exception e)
		{
			LOGGER.log(Level.SEVERE,"VS --> Exception in onHeaderCompletion ",e);
		}
	}

	public void onData(HttpRequest request, HttpResponse response) throws Exception
	{
		String reqId = request.getHeader("_reqid");

		AbsFileHandler fileHandler = null;
		if (FILEHANDLERMAP.containsKey(reqId))
		{
			fileHandler = FILEHANDLERMAP.get(reqId);
		}
		else
		{
			String componentName = request.getHeader("componentname");
			if(AbsFileHandler.isRegistered(componentName))
			{
				fileHandler = AbsFileHandler.getInstance(componentName);
				FILEHANDLERMAP.put(reqId, fileHandler);
			}
			else
			{
				response.sendError(400, "invalid componentname");//No I18N
				response.close();
			}
		}

		while (request.getHttpStream().isAvailable())
		{
			byte[] data = request.getHttpStream().read();

			String filePath = request.getHeader("filepath");

			if (request.getHttpStream().isFinished())
			{
				fileHandler.handleData(new File(filePath), data, true);
				FILEHANDLERMAP.remove(reqId);
				response.commitChunkedTransfer();
				response.close();
				return;
			}

			try
			{
				fileHandler.handleData(new File(filePath), data, false);
			}
			catch(Exception e)
			{
				FILEHANDLERMAP.remove(reqId);
				response.sendError(500, "internal server error");//No I18N
				response.close();
			}
		}
	}
}