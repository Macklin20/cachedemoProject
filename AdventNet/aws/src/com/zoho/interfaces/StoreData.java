package com.zoho.interfaces;

import com.zoho.data.Content;

public interface StoreData
{
	void addContent(Content content);
	byte[] getData(String key);
	void setCapacity(int capacity);
	int getCapacity();
}
