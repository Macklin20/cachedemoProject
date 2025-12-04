package com.zoho.wms.asyncweb.server.stats;

import java.util.Hashtable;

public interface RuntimeStatsListener
{
	public void updateMemoryStats(Hashtable data);
	public void updateHitsStats(Hashtable data);
	public void updateBandwidthStats(Hashtable data);
	public void updateGCStats(Hashtable data);
	public void updateCPUStats(Hashtable data);
	public void updateDiskUsageStats(Hashtable data);
}

