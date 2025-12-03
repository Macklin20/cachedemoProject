package com.zoho.externalservices;

import com.zoho.interfaces.CopyFromDisk;
import com.zoho.interfaces.StoreData;
import com.zoho.services.StoreDataInMemoryCacheList;
import com.zoho.data.Content;

public class CopyFromDiskToInMemoryCache implements CopyFromDisk
{
	private String fileName;
	private byte[] data;

	private boolean isAdded;

	@Override
	public void copyFromDiskToExternal(String fileName , byte[] data)
	{
		this.fileName = fileName;
		this.data = data;


		if(fileName == null || data == null )
		{
			throw new NullPointerException("Filename or data cannot be null");
		}

		addContentToInMemoryCacheList();
	}

	public void addContentToInMemoryCacheList()
	{
		StoreData cacheList = new StoreDataInMemoryCacheList();

		Content content = new Content(fileName , data);

		cacheList.addContent(content);

		isAdded = true;

		System.out.println("Added from Disk to In Memory Cache");
	}

	public boolean isAddedToCacheList()
	{
		return isAdded;
	}
}
