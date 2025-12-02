package com.zoho.wms.asyncweb.server.runtime;

import java.text.DecimalFormat;
import java.util.Hashtable;
import java.util.logging.Level;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.math.RoundingMode;

import com.adventnet.wms.servercommon.runtime.WmsRuntime;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.AWSConstants;
import com.zoho.wms.asyncweb.server.AsyncWebStatsManager;

public class CPUInfo extends WmsRuntime
{
	private Hashtable<String, String> info = new Hashtable();
        private static AsyncLogger logger = new AsyncLogger("logger");//No I18N
	private static long lastCpuTime = 0;

        public Hashtable getInfo(Hashtable params)
        {
                return info;
        }

        protected void periodicCollector(long timeElapsed)
        {
		try
		{
			OperatingSystemMXBean osBean = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
                	ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
                	int availableProcessors = osBean.getAvailableProcessors();
                        
                	DecimalFormat df = new DecimalFormat("#.###");
                	df.setRoundingMode(RoundingMode.HALF_UP);
                	try
                	{
                        	double loadAverage = osBean.getSystemLoadAverage();
                        	String loadPercentage = df.format((loadAverage * 100) / availableProcessors);
                        	int threadCount = threadBean.getThreadCount();                      
                        	String systemCpuLoad = df.format(osBean.getSystemCpuLoad() * 100);  
                        	String processCpuLoad = df.format(osBean.getProcessCpuLoad() * 100);
                        	long processCpuTime = osBean.getProcessCpuTime() / 1000000;
                                
                        	String cpuTime = df.format(processCpuTime - lastCpuTime);
                        	lastCpuTime = processCpuTime;
                                
				info.put(AWSConstants.LOADAVG, df.format(loadAverage));
				info.put(AWSConstants.LOADPERCENT, loadPercentage);
				info.put(AWSConstants.SYSTEMCPULOAD, systemCpuLoad);
				info.put(AWSConstants.PROCESSCPULOAD, processCpuLoad);
				info.put(AWSConstants.CPUTIME, cpuTime);
				info.put(AWSConstants.THREADCOUNT, (threadCount+""));
				info.put(AWSConstants.PROCESSORS, availableProcessors+"");
			}
			catch(Exception e)
			{
			}
			AsyncWebStatsManager.onCPUStats(info);
                }
                catch(Exception e)
                {
                }

        }

}

