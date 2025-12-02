package com.zoho.wms.asyncweb.server.grid.servlet;

import com.zoho.wms.wcp.common.servlet.WCPRequest;
import com.zoho.wms.wcp.common.servlet.WCPResponse;
import com.zoho.wms.wcp.common.servlet.WCPServlet;
import com.adventnet.wms.servercommon.grid.ar.ARConstants;
import com.adventnet.wms.servercommon.grid.ar.ARManager;
import com.adventnet.wms.servercommon.rocksdb.RocksDBBackupManager;
import com.adventnet.wms.servercommon.rocksdb.RocksDBConstants;
import com.adventnet.wms.servercommon.rocksdb.RocksDBManager;

import java.util.logging.Level;
import java.util.logging.Logger;

public class RocksDBInitServlet implements WCPServlet
{
	private static final Logger LOGGER = Logger.getLogger(RocksDBInitServlet.class.getName());
	@Override
	public void wcpService(WCPRequest req, WCPResponse res) throws Exception
	{

		LOGGER.log(Level.INFO, "SNA-- Rocksdb init request received");
		try
		{
			res.setResponseData(RocksDBConstants.SUCCESS);
			if(ARManager.getServerMode() == ARConstants.SERVERMODE_NEW)
			{
				if(!RocksDBBackupManager.initialize())
				{
					LOGGER.log(Level.WARNING, "SNA-- RocksDB - restore init - backupmanager failed to initialize");
					res.setResponseData(RocksDBConstants.FAIL);
				}

				if(!RocksDBBackupManager.executeRestore())
				{
					LOGGER.log(Level.WARNING, "SNA-- RocksDB - restore init - Execute Restore failed");
					res.setResponseData(RocksDBConstants.FAIL);
				}
				LOGGER.log(Level.INFO, "SNA-- initializing rocksdb");
				if(!RocksDBManager.initialize(true))
				{
					LOGGER.log(Level.WARNING, "SNA-- RocksDB - restore init - RocksDB initialize failed");
					res.setResponseData(RocksDBConstants.FAIL);
				}
			}
		}
		catch (Exception e)
		{
			LOGGER.log(Level.SEVERE, "SNA-- Untar failed");
			res.setResponseData((RocksDBConstants.FAIL));

		}

		res.sendResponse();
	}
}
