package com.webengine;

import java.util.HashMap;
import java.util.LinkedList;

public class LRUCache
{
	private int capacity;
	private HashMap<String, byte[]> cache;
	private LinkedList<String> list;

	private boolean addCache;
	private boolean isRespond;

	public LRUCache(int capacity)
	{
		this.capacity = capacity;
		this.cache = new HashMap<>();
		this.list= new LinkedList<>();
	}

	public void put(String key , byte[] value)
	{
		if (key == null || value == null) 
		{
			throw new NullPointerException("Key or value cannot be null");
		}


		if(cache.size() >= capacity )
		{
			String filename = list.removeLast();

			cache.remove(filename);

		}

		cache.put(key , value);

		list.addFirst(key);

		addCache = true;


	}

	public byte[] getData(String key)
	{
		byte[] data = null;

		if(key == null)
		{
			throw new NullPointerException("Key cannot be null");
		}


		if(cache.containsKey(key))
		{
			data = cache.get(key);
			list.remove(key);

			list.addFirst(key);
			isRespond = true;
		}

	//	
		return data;
	}

	public boolean checkFilePresent(String filename)
	{
		return cache.containsKey(filename);
	}	


	public void viewCacheList()
	{
		System.out.println(list);
	}

	public boolean checkData(byte[] data , String key)
	{
		return data == cache.get(key);
	}

	public boolean isResponseToClient()
	{
		return isRespond;
	}

	public boolean isAddedToCache()
	{
		return addCache;
	}
	
	public HashMap getCache()
	{
		return cache;
	}
}
