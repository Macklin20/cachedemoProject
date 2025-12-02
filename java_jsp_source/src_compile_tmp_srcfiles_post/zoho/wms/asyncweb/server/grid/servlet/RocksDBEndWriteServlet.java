package com.zoho.wms.asyncweb.server.grid.servlet;

import com.zoho.wms.wcp.common.servlet.WCPRequest;
import com.zoho.wms.wcp.common.servlet.WCPResponse;
import com.zoho.wms.wcp.common.servlet.WCPServlet;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RocksDBEndWriteServlet implements WCPServlet
{
	private static final Logger LOGGER = Logger.getLogger(RocksDBEndWriteServlet.class.getName());

	@Override
	public void wcpService(WCPRequest req, WCPResponse res) throws Exception
	{

		LOGGER.log(Level.INFO, "SNA- wcpEndWriteServlet request receieved");
		final String SUCCESS = "success";// NO I18N
		final String FAIL = "fail"; // NO I18N
		//call endWriting
		RocksDBDTServlet.rocksDBWriter.endWriting();
		try
		{
			if(RocksDBDTServlet.writeTask.get(30, TimeUnit.SECONDS))
			{
				res.setResponseData(SUCCESS);
			}
			else
			{
				res.setResponseData(FAIL);
			}
		}
		catch (InterruptedException e)
		{
			LOGGER.log(Level.WARNING, "SNA- Writer interrupted", e);
			res.setResponseData(FAIL);
		}
		catch (ExecutionException e)
		{
			LOGGER.log(Level.WARNING, "SNA- Exception in RocksDBWriter", e);
			res.setResponseData(FAIL);
		}
		catch (TimeoutException e)
		{
			LOGGER.log(Level.WARNING, "SNA- did not finish writing in 30 sec", e);
			res.setResponseData(FAIL);
		}

		res.sendResponse();

	}


}
