package out;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doNothing;
import org.mockito.Mockito;


import com.zoho.wms.asyncweb.server.http.HttpRequest;
import com.zoho.wms.asyncweb.server.http.HttpResponse;
import com.zoho.wms.asyncweb.server.exception.AWSException;

import com.webengine.servlet.FileReadServlet;
import com.webengine.servlet.ReadAndWriteFile;
import com.webengine.LRUCache;

import java.io.IOException;

public class FileReadServletTest
{

	private static HttpRequest request;
	private static HttpResponse response;
	private static FileReadServlet servlet;
	private static LRUCache lrucache;
	private static ReadAndWriteFile readWriteFile;
	private static String fileName;

	@BeforeAll
	public static void setUp()
	{
		request = Mockito.mock(HttpRequest.class);
		response = Mockito.mock(HttpResponse.class);


		System.setProperty("server.home" ,"/Users/macklin-ts506/democache/AdventNet/aws");


		servlet = spy(new FileReadServlet());
		readWriteFile = spy(new ReadAndWriteFile(request , response));



		Mockito.when(request.getParameter("filename")).thenReturn("file1");
		Mockito.when(request.getRemoteAddr()).thenReturn("127.0.0.1");

		readWriteFile.getParameterFromRequest();
		fileName = readWriteFile.getFileName();
		readWriteFile.setLRUCache(2);
		lrucache = readWriteFile.getLRUCache();

		try
		{
			doNothing().when(response).write(Mockito.any(byte[].class));
			doNothing().when(response).close();

		}
		catch(AWSException e)
		{
			e.printStackTrace();
		}        
		catch(IOException e)
		{
			e.printStackTrace();
		}	
	}


	@Test
	public void testServerAcceptedClient()
	{
		assertTrue(servlet.isServerAcceptedClient() , "Server did not accept the client");
	}


	@Test
	public void servletThrowNullWhenPassNullRequest()
	{
		assertThrows(NullPointerException.class , () -> {servlet.service(null , response ); } );
	}

	@Test
	public void servletThrowNullWhenPassNullResponse()
	{
		assertThrows(NullPointerException.class , () -> {servlet.service(request , null ); } );
	}

	@Test
	public void testCacheCreated()
	{
		assertTrue(lrucache != null , "Object not created");
		System.out.println(lrucache);
	}

	@Test
	public void testParameterReceived()
	{
		readWriteFile.getParameterFromRequest();
		assertEquals("file1" , readWriteFile.getFileName());
		System.out.println(readWriteFile.getFileName());
	}
	

	@Test
	public void testFileRetreivedFromDisk()
	{
		System.out.println(fileName);
		assertTrue(readWriteFile.isRespondedFromDisk() , "File is not taken from disk");	
	}

	@Test
	public void testDiskThrowNullWhenGivingFileNameNull()
	{
		assertThrows(NullPointerException.class , () -> {readWriteFile.writeFromFile(null); });
	}

	@Test
	public void testDataAddedToCache()
	{
		readWriteFile.writeFromFile(fileName);
		assertTrue(lrucache.isAddedToCache() , "Data not added to Cache");
	}	


	@Test
	public void testFileRetreivedFromCache()
	{
		readWriteFile.writeFromFile(fileName);
		readWriteFile.writeFromCache(fileName);
		assertTrue(readWriteFile.isRespondedFromCache() , "File is not taken from cache");
	}

	@Test
	public void testAddingNullInCacheThrowException()
	{
		assertThrows(NullPointerException.class , () -> {readWriteFile.addingDataToCache(null , null); });
        }

	@Test
	public void testSameDataWrittenOnCache()
	{
		byte[] bytearr = {10,20,30};
		lrucache.put("array" , bytearr);
		
		assertTrue(lrucache.checkData(bytearr , "array") , "Cache data is not same");
		lrucache.viewCacheList();
		System.out.println(lrucache.getCache());
	} 
}
