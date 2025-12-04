package com.zoho.services;

import com.zoho.data.Content;
import com.zoho.interfaces.StoreData;

import java.util.LinkedList;

public class StoreDataInMemoryCacheList implements StoreData
{
	private static LinkedList<Content> linkedList = new LinkedList<>();	
	private int capacity = 2;
	private Content content;

	private boolean isDataAdded;


	@Override	
	public void setCapacity(int capacity)
	{
		this.capacity = capacity;
	}

	@Override
	public int getCapacity()
	{
		return capacity;
	}

	@Override
	public void addContent(Content content)
	{

		if(content == null)
		{
			throw new NullPointerException("Content cannot be null");
		}

		if(linkedList.size() >= capacity )
		{
			linkedList.removeLast();
		}

		linkedList.addFirst(content);
		viewInMemoryCacheList();	
		isDataAdded = true;

	}

	@Override
	public byte[] getData(String key)
	{
		if(key == null)
		{
			throw new NullPointerException("Key cannot be null");
		}

		if(!checkInMemoryCacheListContainsFile(key))
		{
			System.out.println("File not found");
			return new byte[0];
		}

		System.out.println("Responded from Cache");

		if(!linkedList.getFirst().getFileName().equals(key))
		{	
			linkedList.removeLast();
			linkedList.addFirst(content);
		}	
		viewInMemoryCacheList();
		return content.getData();
	}

	public boolean checkInMemoryCacheListContainsFile(String key)
	{
		this.content = linkedList.stream()
			.filter(c -> c.getFileName().equals(key))
			.findFirst()
			.orElse(null);


		return content != null;
	}

	public void viewInMemoryCacheList()
	{
		System.out.print("Cache list: " );
		linkedList.stream().filter(s -> s.getFileName().startsWith("f")).forEach(s -> System.out.print(s.getFileName() +" " ));
		System.out.println();
	}

	public boolean isDataAddedCheck()
	{
		return isDataAdded;
	}
}	
