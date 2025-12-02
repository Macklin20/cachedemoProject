package com.zoho.wms.asyncweb.server.grid.servlet;

import com.zoho.wms.wcp.common.servlet.WCPRequest;
import com.zoho.wms.wcp.common.servlet.WCPResponse;
import com.zoho.wms.wcp.common.servlet.WCPServlet;
import com.adventnet.wms.servercommon.rocksdb.RocksDBManager;

public class RocksDBGetVerifyRecord implements WCPServlet
{

    @Override
    public void wcpService(WCPRequest req, WCPResponse res) throws Exception
    {
        res.setResponseData(""+ RocksDBManager.getVerificationRecord());
        res.sendResponse();
    }
}
