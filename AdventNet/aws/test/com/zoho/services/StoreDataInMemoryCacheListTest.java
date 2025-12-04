package com.zoho.services.test;

import com.zoho.data.Content;
import com.zoho.services.StoreDataInMemoryCacheList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class StoreDataInMemoryCacheListTest {

	private StoreDataInMemoryCacheList cacheList;
	@BeforeEach
	public void setUp() 
	{
		cacheList = new StoreDataInMemoryCacheList();
		
		cacheList.setCapacity(2);
	}

	@Test
	public void testAddContentAndCheckAdded() 
	{
		Content content1 = new Content("file1", "data1".getBytes());
		cacheList.addContent(content1);

		assertTrue(cacheList.checkInMemoryCacheListContainsFile("file1"));
		assertTrue(cacheList.isDataAddedCheck());
		assertArrayEquals("data1".getBytes(), cacheList.getData("file1"));
	}

	@Test
	public void testGetDataReturnsEmptyForNonExistingFile() 
	{
		byte[] data = cacheList.getData("file");

		assertNotNull(data);
		assertEquals(0, data.length);
	}

	@Test
	public void testCacheCapacityRemovesLastWhenFull()
	{
		Content content1 = new Content("file1", "data1".getBytes());
		Content content2 = new Content("file2", "data2".getBytes());
		Content content3 = new Content("file3", "data3".getBytes());

		cacheList.addContent(content1);
		cacheList.addContent(content2);

	
		assertTrue(cacheList.checkInMemoryCacheListContainsFile("file1"));
		assertTrue(cacheList.checkInMemoryCacheListContainsFile("file2"));
		cacheList.addContent(content3);

		assertFalse(cacheList.checkInMemoryCacheListContainsFile("file1"));
		assertTrue(cacheList.checkInMemoryCacheListContainsFile("file2"));
		assertTrue(cacheList.checkInMemoryCacheListContainsFile("file3"));
	}

	@Test
	public void testNullContentThrowsException()
	 {
		assertThrows(NullPointerException.class, () -> cacheList.addContent(null));
	}

	@Test
	public void testNullKeyThrowsException()
	{
		assertThrows(NullPointerException.class, () -> cacheList.getData(null));
	}
}

