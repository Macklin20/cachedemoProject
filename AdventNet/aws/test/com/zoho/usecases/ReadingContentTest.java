package com.zoho.usecases.test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doNothing;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.zoho.interfaces.StoreData;
import com.zoho.interfaces.Reading;
import com.zoho.services.ReadingFromDisk;
import com.zoho.services.ReadingFromInMemoryCache;
import com.zoho.services.StoreDataInMemoryCacheList;
import com.zoho.entities.URLFile;
import com.zoho.usecases.ReadingContent;

public class ReadingContentTest {

	private ReadingContent readingContent;
	private MockedStatic<URLFile> mocked;

	@BeforeEach
	public void setUp() 
	{
		readingContent = new ReadingContent();
		System.setProperty("server.home" ,"/Users/macklin-ts506/cachedemo/AdventNet/aws");

		mocked = Mockito.mockStatic(URLFile.class);
		mocked.when(URLFile::getFileName).thenReturn("file1");
	}

	@AfterEach
	public void tearDown()
	{
		mocked.close();
	}

	@Test
	public void testIsResponding()
	{
		byte[] output = readingContent.readingFileContent();
		assertNotNull(output, "Data should not be null");
		assertTrue(readingContent.checkResponded(), "isRespond should be true");
	}

	@Test
	public void testIsRespondingFromCache()
	{
		byte[] output1 = readingContent.readingFileContent();
		assertTrue(readingContent.checkResponded(), "isRespond should be true");
	}
}

