package com.zoho.entities;

public class URLFile
{
	private static String fileName;

	public URLFile(String fileName)
	{		
		if(fileName == null)
		{
			throw new NullPointerException("Filename cannot be null");
		}
		this.fileName = fileName;

	}

	public static String getFileName()
	{
		return fileName;
	}
}
