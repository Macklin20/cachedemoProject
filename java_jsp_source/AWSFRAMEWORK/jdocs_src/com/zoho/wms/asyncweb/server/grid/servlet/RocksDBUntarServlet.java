package com.zoho.wms.asyncweb.server.grid.servlet;

import com.zoho.wms.wcp.common.servlet.WCPRequest;
import com.zoho.wms.wcp.common.servlet.WCPResponse;
import com.zoho.wms.wcp.common.servlet.WCPServlet;
import com.adventnet.wms.servercommon.rocksdb.RocksDBBackupManager;
import com.adventnet.wms.servercommon.rocksdb.RocksDBConstants;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RocksDBUntarServlet implements WCPServlet
{
	private static final Logger LOGGER = Logger.getLogger(RocksDBUntarServlet.class.getName());

	@Override
	public void wcpService(WCPRequest req, WCPResponse res) throws Exception
	{
		LOGGER.log(Level.INFO, "SNA-- Untar request received");
		try
		{
			//clear dir
			RocksDBBackupManager.clearDir(new File(RocksDBBackupManager.rocksDBBackupDirPath));

			//untar
			if(!RocksDBBackupManager.unTar(req.getData()))
			{
				LOGGER.log(Level.WARNING, "SNA-- RocksDB - restore init - untar failed");
				res.setResponseData(RocksDBConstants.FAIL);
			}
			res.setResponseData(RocksDBConstants.SUCCESS);
		}
		catch (Exception e)
		{
			LOGGER.log(Level.SEVERE, "SNA-- Untar failed");
			res.setResponseData((RocksDBConstants.FAIL));

		}

		res.sendResponse();
	}
}
