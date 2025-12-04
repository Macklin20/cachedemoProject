package com.zoho.entities.test;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import com.zoho.entities.URLFile;

public class URLFileTest
{
	@Test
   	public void testConstructorSetsFileName() 
	{
		URLFile file = new URLFile("file1.txt");
		assertEquals("file1.txt", URLFile.getFileName());
	}

    	@Test
    	public void testStaticGetFileNameReturnsLatestValue() 
	{

		new URLFile("firstFile.txt");
		assertEquals("firstFile.txt", URLFile.getFileName());

		new URLFile("secondFile.txt");
		assertEquals("secondFile.txt", URLFile.getFileName());

		assertNotEquals("firstFile.txt", URLFile.getFileName());

	}

	@Test
	public void testURLFileNameThrowNull()
	{
		assertThrows(NullPointerException.class , () -> { new URLFile(null); });
	}
}

