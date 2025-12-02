package com.zoho.services.test;

import com.zoho.services.ReadingFromDisk;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReadingFromDiskTest
{

	@Test
	public void testReadingFromDisk()
	{

		System.setProperty("server.home", "/Users/macklin-ts506/cachedemo/AdventNet/aws");

		ReadingFromDisk readingFromDisk = new ReadingFromDisk("file1");
		byte[] content = readingFromDisk.readingContent();

		assertTrue(content.length > 0, "File has no content");

		System.out.println("Read " + content.length + " bytes from disk");
	}
}

