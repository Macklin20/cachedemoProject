//$Id$

package com.zoho.wms.asyncweb.server;

// Wms import
import com.adventnet.wms.servercommon.WmsUIDGenerator;
import com.adventnet.wms.common.exception.WMSException;

public class WmsSessionIdGenerator extends WmsUIDGenerator
{
        private static long mask = 0x6L<<60;

        public static Long getUniqueId() throws WMSException
        {
                return (WmsUIDGenerator.getUniqueId()|mask);
        }
}
