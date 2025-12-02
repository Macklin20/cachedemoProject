package com.zoho.controllers.test;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doNothing;
import org.mockito.Mockito;

import com.zoho.wms.asyncweb.server.http.HttpRequest;
import com.zoho.wms.asyncweb.server.http.HttpResponse;
import com.zoho.wms.asyncweb.server.exception.AWSException;

import com.zoho.controllers.FileReadServlet;

import java.io.IOException;

public class FileReadServletTest
{

	private static HttpRequest req;
	private static HttpResponse res;
	private static FileReadServlet servlet;

	@BeforeAll
	public static void setUp()
	{
		req = Mockito.mock(HttpRequest.class);
		res = Mockito.mock(HttpResponse.class);

		servlet = spy(new FileReadServlet());

		Mockito.when(req.getRemoteAddr()).thenReturn("127.0.0.1");
		
		try
                {
                        doNothing().when(res).write(Mockito.any(byte[].class));
                        doNothing().when(res).close();

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
	public void testServletConnectClient()
	{
		try
		{
			servlet.service(req , res);
			assertTrue(servlet.isServerAcceptedClient() , "Servlet does not connected to client");
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
	public void testServletThrowNullWhenRequestIsNull()
	{
			assertThrows(NullPointerException.class , () -> { servlet.service(null , res); });
	}

	@Test
	public void testServletThrowNullWhenResponseIsNull()
	{
		assertThrows(NullPointerException.class , () -> { servlet.service(req , null); });
	}

	@Test
	public void testWritingResponseToClient()
	{
		servlet.writingResponse();
		assertTrue(servlet.isServerRespondToClient() , "Server does not respond to client");
	}
}
	

	
	
