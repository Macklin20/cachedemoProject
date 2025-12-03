package com.zoho.data;

public class Content
{
	private String fileName;
	private byte[] data;

	public Content(String fileName , byte[] data)
	{
		this.fileName = fileName;
		this.data = data;
	}

	public String getFileName()
	{
		return fileName;
	}

	public void setFileName(String fileName)
	{
		this.fileName = fileName;
	}

	public byte[] getData()
	{
		return data;
	}

	public void setData(byte[] data)
	{
		this.data = data;
	}

}
