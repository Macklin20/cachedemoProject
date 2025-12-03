package com.zoho.usecases;

import com.zoho.interfaces.StoreData;
import com.zoho.interfaces.Reading;
import com.zoho.services.ReadingFromDisk;
import com.zoho.services.ReadingFromInMemoryCache;
import com.zoho.services.StoreDataInMemoryCacheList;
import com.zoho.entities.URLFile;

public class ReadingContent
{
	private StoreData cacheList;
	private Reading readDisk;
	private Reading readCache;
	
	private byte[] data;
	
	private boolean isRespond;
	

	public void  initialize(String fileName)
	{
		try
		{
			cacheList = new StoreDataInMemoryCacheList();
			readDisk = new ReadingFromDisk(fileName);
			readCache = new ReadingFromInMemoryCache(fileName);
		}
		catch(NullPointerException e)
		{
			throw new NullPointerException("Filename cannot be null");
		}
	}
	

	public byte[] readingFileContent()
	{
		String fileName = URLFile.getFileName();
		
		initialize(fileName);
	
		if(((StoreDataInMemoryCacheList)cacheList).checkInMemoryCacheListContainsFile(fileName))
		{
			System.out.println("Writing from cache");
			data =  readCache.readingContent();
		}
		else
		{
			System.out.println("Writing from disk");
			data = readDisk.readingContent();
		}
	
		if(data != null )
		{
			isRespond = true;
		}
	
		return data;
	}

	public boolean checkResponded()
	{
		return isRespond;
	}
}

