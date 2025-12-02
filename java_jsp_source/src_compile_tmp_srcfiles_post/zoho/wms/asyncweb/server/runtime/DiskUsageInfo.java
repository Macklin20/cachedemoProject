//$Id$
package com.zoho.wms.asyncweb.server.runtime;

// Java import
import java.util.Hashtable;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.File;
import java.io.IOException;

// Server common import
import com.adventnet.wms.servercommon.runtime.WmsRuntime;
import com.adventnet.wms.servercommon.util.WMSUtil;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;

import com.zoho.wms.asyncweb.server.AsyncWebStatsManager;
import com.zoho.wms.asyncweb.server.AWSConstants;

public class DiskUsageInfo extends WmsRuntime
{
        private Hashtable<String, String> info = new Hashtable();
        private static AsyncLogger logger = new AsyncLogger("logger");//No I18N
        
        private static FileStore homeFileStore = null;
        private static int fileStoreTime = 0;

        public Hashtable getInfo(Hashtable params)
        {
                return info;
        }

        protected void periodicCollector(long timeElapsed)
        {
                try
                {
                        if (homeFileStore == null || fileStoreTime++ > 60)
                        {
                                initFileStorage();
                                fileStoreTime = 0;
                        }
                        
                        long totalSize = homeFileStore.getTotalSpace();
                        long freeSize = homeFileStore.getUsableSpace();
                        long usedSize = homeFileStore.getTotalSpace() - homeFileStore.getUnallocatedSpace();
                        
                        String usedPercentage = String.format("%.2f", (usedSize * 100.0) / totalSize);
                        String freePercentage = String.format("%.2f", (freeSize * 100.0) / totalSize);
                        
                        info.put(AWSConstants.TOTAL_SIZE, totalSize+"");
                        info.put(AWSConstants.USED_SIZE, usedSize+"");
                        info.put(AWSConstants.FREE_SIZE, freeSize+"");
                        info.put(AWSConstants.USED_PERCENTAGE, usedPercentage);
                        info.put(AWSConstants.FREE_PERCENTAGE, freePercentage);
                        
                        AsyncWebStatsManager.onDiskUsageStats(info);
                        
                }
                catch (Exception e)
                {
                }
        
        }

	private void initFileStorage()
        {
                try
                {
                        homeFileStore = Files.getFileStore(Paths.get(File.separator + "home"));
                }
                catch (IOException e)
                {
                }
        }


}


