package com.zoho.services;

import com.zoho.interfaces.StoreData;
import com.zoho.interfaces.Reading;
import com.zoho.services.StoreDataInMemoryCacheList;

public class ReadingFromInMemoryCache implements Reading
{
	private String fileName;
	private boolean isRespond;

	public ReadingFromInMemoryCache(String fileName)
	{
		this.fileName = fileName;
	}

	@Override
	public byte[] readingContent()
	{
		
		try
		{
			StoreData inMemoryCacheList = new StoreDataInMemoryCacheList();
			byte[] data =  inMemoryCacheList.getData(fileName);

			if(data != null)
			{
				isRespond = true;
			}

			return data;
		}
		catch(NullPointerException e)
		{
			throw new NullPointerException("file name cannot be null");
		}
	}

	public boolean isRespondedFromCache()
	{
		return isRespond;
	}
}
