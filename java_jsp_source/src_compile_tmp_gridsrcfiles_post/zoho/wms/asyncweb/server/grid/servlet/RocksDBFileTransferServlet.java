package com.zoho.wms.asyncweb.server.grid.servlet;

import com.adventnet.wms.servercommon.util.InflaterUtil;
import com.zoho.wms.asyncweb.server.exception.AWSException;
import com.zoho.wms.asyncweb.server.http.HttpRequest;
import com.zoho.wms.asyncweb.server.http.HttpResponse;
import com.zoho.wms.asyncweb.server.http.HttpStreamServlet;
import com.zoho.wms.asyncweb.server.util.StateConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RocksDBFileTransferServlet extends HttpStreamServlet
{
	private static final Logger LOGGER = Logger.getLogger(RocksDBFileTransferServlet.class.getName());

	private static ConcurrentHashMap<String, OutputStream> outputStreamMap = new ConcurrentHashMap<>();
	private static ConcurrentHashMap<String, InflaterUtil> inflaterMap = new ConcurrentHashMap<>();
	private static ConcurrentHashMap<String, Long> startTimeMap = new ConcurrentHashMap<>();
	private static ConcurrentHashMap<String, Boolean> isCompressedFlagMap = new ConcurrentHashMap<>();

	@SuppressWarnings("unchecked")	//No I18n
	public void onHeaderCompletion(HttpRequest req, HttpResponse res) throws Exception
	{
		LOGGER.log(Level.INFO, "SNA-- RocksDBFileTransferServlet onHeaderCompletion");
		HashMap<String, String> headerMap = req.getHeaders();
		if(!headerMap.containsKey("random-id"))
		{
		    String destPath = null;
		    if(req.getHeader("destpath")!=null)
		    {
		        destPath = URLDecoder.decode(req.getHeader("destpath"), "UTF-8");
		    }
			LOGGER.log(Level.INFO, "SNA-- RocksDBFileTransferServlet onHeaderCompletion - does not contain random id destPath : {0}", destPath);
			//create a new random id, add to header
			// required for identifying request in onData method
			String randomID = UUID.randomUUID().toString();
			headerMap.put("random-id", randomID);
			req.setHeaders(headerMap);

			res.setRequestState(StateConstants.REQUEST_ACKNOWLEDGED);

			if(destPath != null)
			{
				LOGGER.log(Level.INFO, "SNA-- RocksDBFileTransferServlet onHeaderCompletion - creating output stream destPath :{0}", destPath);
				File destFile = new File(destPath);
				destFile.getParentFile().mkdirs();
				outputStreamMap.put(randomID,new FileOutputStream(destFile));
				isCompressedFlagMap.put(randomID, Boolean.parseBoolean(req.getHeader("iscompressed")));
				if(isCompressedFlagMap.get(randomID))
				{
					inflaterMap.put(randomID, new InflaterUtil());
				}
				startTimeMap.put(randomID, Long.parseLong(req.getHeader("start-time")));
			}
			else
			{
				LOGGER.log(Level.WARNING, "SNA-- RocksDBFileTransferServlet onHeaderCompletion - filename not available");
				sendError(res, 400, "File name not available"); //No I18N
			}
		}
		else
		{
			LOGGER.log(Level.WARNING, "SNA-- RocksDBFileTransferServlet onHeaderCompletion - random id already exists");
			sendError(res, 400, "random id already present"); //No I18N
		}

		res.setInputDataSize(-1); // requirement for onData method to be called

	}

	@SuppressWarnings("unchecked")	//No I18n
	@Override
	public void onData(HttpRequest req, HttpResponse res) throws Exception
	{
		OutputStream os = null;
		String randomID = null;
		try 
		{
			LOGGER.log(Level.WARNING, "SNA-- RocksDBFileTransferServlet onData");

			HashMap<String,String> headerMap = req.getHeaders();
			if(headerMap.containsKey("random-id"))
			{
				LOGGER.log(Level.WARNING, "SNA-- RocksDBFileTransferServlet onData - getting output stream");
				randomID = req.getHeader("random-id");

				os = outputStreamMap.get(randomID);
			}
            String destPath = null;
		    if(req.getHeader("destpath")!=null)
		    {
		        destPath = URLDecoder.decode(req.getHeader("destpath"), "UTF-8");
		    }
			if (os == null) 
			{
				sendError(res, 400, "output stream is null"); //No I18N
				return;
			}

			if(req.getHttpStream().isAvailable())
			{
				LOGGER.log(Level.WARNING, "SNA-- RocksDBFileTransferServlet onData - writing to output stream destPath :{0}", destPath);
				if(isCompressedFlagMap.get(randomID))
				{
					os.write(inflaterMap.get(randomID).inflate(req.getHttpStream().read()));
				}
				else
				{
					os.write(req.getHttpStream().read());
				}
			}
			else
			{
				LOGGER.log(Level.WARNING, "SNA-- RocksDBFileTransferServlet onData - stream not available");
			}

			if (req.getHttpStream().isFinished())
			{
				LOGGER.log(Level.INFO, "SNA-- RocksDB - RocksDBFileTransferServlet - Stream is finished destPath :{0}", destPath);
				LOGGER.log(Level.INFO, "SNA-- RocksDB - RocksDBFileTransferServlet - Time take for transfer - destPath :{0}, timetaken :{1} " ,new Object[]{destPath, (System.currentTimeMillis() - startTimeMap.get(randomID))});

				outputStreamMap.remove(randomID).close();
				if(isCompressedFlagMap.get(randomID))
				{
					inflaterMap.remove(randomID).close();
				}
				res.commitChunkedTransfer();
				res.write("ok"); //No I18N
				res.close();
			}
		} 
		catch (Exception e)
		{
			LOGGER.log(Level.WARNING, "SNA--> RocksDBFileTransferServlet Exception during onData", e);
			sendError(res, 500, "Error During Write"); //No I18N
			//todo close file on exception
		}
	}

	public void sendError(HttpResponse response, int status, String message) throws IOException, AWSException
	{
		response.sendError(status, message);
		response.close();
	}
}