//$Id$
package com.zoho.wms.asyncweb.server.grid.servlet;

//java imports
import java.util.Map;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletResponse;

//wms imports
import com.adventnet.wms.common.CommonUtil;
import com.adventnet.wms.servercommon.dc.DC;
import com.adventnet.wms.common.HttpDataWraper;

// aws imports
import com.zoho.wms.asyncweb.server.http.HttpServlet;
import com.zoho.wms.asyncweb.server.http.HttpRequest;
import com.adventnet.wms.servercommon.dc.DCConstants;
import com.zoho.wms.asyncweb.server.http.HttpResponse;

// wms servercommon import
import com.adventnet.wms.servercommon.grid.AbsAutoReBalancer;

public class ARBServlet  extends HttpServlet
{
	private static final Logger LOGGER = Logger.getLogger(ARBServlet.class.getName());

	@SuppressWarnings("unchecked") //No I18N
	public void service(HttpRequest request, HttpResponse response) throws Exception 
	{
		if(!DC.verifyGridAccessKey(request.getHeader(DCConstants.GAC_KEY)))
		{
			LOGGER.info("ARB-->Invalid GAC key from server : "+request.getRemoteAddr());
			response.close();
			return;
		}

		String opr = request.getParameter("opr");
		String data = request.getParameter("data");
		String context = request.getParameter("context");
		String balancetype = request.getParameter("balancetype");

		try
		{
			LOGGER.info("ARB--> opr : "+opr+ " context : "+context+ " data : "+data);

			if(!"get".equals(opr) && !"rebalance".equals(opr))
			{
				response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid opr");	//No I18N
			}

			if(opr.equals("get"))
			{
				if(CommonUtil.isEmpty(context))
				{
					response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Context should not be empty");	//No I18N
				}
				else
				{
					if(balancetype.equals("hits"))
					{
						response.write(HttpDataWraper.getString(AbsAutoReBalancer.getHitsInfo(CommonUtil.getList(context))).getBytes("UTF-8")); //NO I18N
					}
					else
					{
						response.write(HttpDataWraper.getString(AbsAutoReBalancer.getLoadInfo(CommonUtil.getList(context))).getBytes("UTF-8")); //NO I18N
					}
				}
			}
			else if(opr.equals("rebalance"))
			{
				if(CommonUtil.isEmpty(data))
				{
					response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Data should not be empty for Rebalancing"); //No I18N
				}
				else
				{
					response.write(HttpDataWraper.getString(AbsAutoReBalancer.rebalance((Map<String, Map<String,Object>>)HttpDataWraper.getObject(data))).getBytes("UTF-8")); //NO I18N
				}
			}
		}
		catch(Exception e)
		{
			LOGGER.log(Level.SEVERE, "Exception in ARBServlet", e);
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Server Internal Error");	//No I18N
		}
		finally
		{
			response.close();
		}
	}
}
