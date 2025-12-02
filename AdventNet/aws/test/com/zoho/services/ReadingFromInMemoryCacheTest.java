package com.zoho.services.test;

import com.zoho.services.ReadingFromInMemoryCache;
import com.zoho.services.ReadingFromDisk;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReadingFromInMemoryCacheTest
{
	@Test
	public void testReadingFromCache()
	{

		System.setProperty("server.home", "/Users/macklin-ts506/cachedemo/AdventNet/aws");

		ReadingFromDisk readingFromDisk = new ReadingFromDisk("file1");
		ReadingFromInMemoryCache readFromCache = new ReadingFromInMemoryCache("file1");    

		byte[] content = readingFromDisk.readingContent();


		byte[] cacheContent = readFromCache.readingContent();

		assertTrue(readFromCache.isRespondedFromCache());
	}	

}
