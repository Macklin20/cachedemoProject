package com.zoho.services;

import java.io.RandomAccessFile;
import java.io.IOException;

import com.zoho.interfaces.CopyFromDisk;
import com.zoho.interfaces.Reading;
import com.zoho.framework.CopyFromDiskToInMemoryCache;

public class ReadingFromDisk implements Reading
{
	private String fileName;

	public ReadingFromDisk(String fileName)
	{
		this.fileName = fileName;
	}

	@Override
	public byte[] readingContent()
	{
		
		String filePath = System.getProperty("server.home") + "/src/files/" + fileName + ".txt";

		try(RandomAccessFile file = new RandomAccessFile(filePath , "r"))
		{

			byte[] data = new byte[(int)file.length()];

			file.readFully(data);

			CopyFromDisk copyFromDiskToInMemoryCache = new CopyFromDiskToInMemoryCache();
			
			copyFromDiskToInMemoryCache.copyFromDiskToExternal(fileName ,  data);
				
			System.out.println("Responded from Disk");
		
			return data;
		}
		catch(IOException e)
		{
			System.err.println(e.getMessage());
		}	
		
		return new byte[0];
	}
}

