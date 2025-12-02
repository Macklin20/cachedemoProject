//$Id$
package com.zoho.wms.asyncweb.server.runtime;

// Java import
import java.util.Hashtable;
import java.util.Properties;
import java.util.Enumeration;
import java.util.logging.*;
import java.io.FileOutputStream;
import java.io.File;

// Server common import 
import com.adventnet.wms.servercommon.runtime.WmsRuntime;
import com.zoho.wms.asyncweb.server.AWSConstants;
import com.zoho.wms.asyncweb.server.AsyncWebServerAdapter;
import com.zoho.wms.asyncweb.server.ConfManager;
import com.zoho.wms.asyncweb.server.WebEngine;
import com.zoho.wms.asyncweb.server.exception.AWSException;
import com.zoho.wms.asyncweb.server.ssl.SSLStartUpTypes;

public class UpdateConf extends WmsRuntime
{
	public static Logger logger = Logger.getLogger(UpdateConf.class.getName());

	public Hashtable getInfo(Hashtable params)
	{  
		Hashtable data = new Hashtable();
		try
		{
			logger.log(Level.INFO, "DEBUG --> Update AWS Conf : {0}", new Object[]{params});//No I18n
			String opr = (String) params.get(AWSConstants.OPR);
			switch(opr)
			{
				case "viewadapterconf":
					data = (Hashtable) ConfManager.getProperties(ConfManager.getAdapterConfFile());
					break;
				case "viewwebengineconf":
					data = (Hashtable) ConfManager.getWebEngineMap();
					break;
				case "viewportmap":
					data = (Hashtable) ConfManager.getProperties(ConfManager.getPortEngineMap());
					break;
				case "viewdomainmap":
					data = (Hashtable) ConfManager.getProperties(ConfManager.getDomainMap());
					break;
				case "editadapterconf":
					data = handleEditConf(ConfManager.getAdapterConfFile(), params);
					if((boolean)data.remove("success"))
					{
						if(!ConfManager.initialize(true))
						{
							throw new AWSException("ConfManager Re-Init failed.");//No I18n
						}
						data.put(opr, "conf updated.");
						logger.log(Level.INFO, "AWS Adapter Conf reinitialized Successfully.");//No I18n
					}
					break;
				case "removeadapterconf":
					data = handleRemoveConf(ConfManager.getAdapterConfFile(), params);
					if((boolean)data.remove("success"))
					{
						if(!ConfManager.initialize(true))
						{
							throw new AWSException("ConfManager Re-Init failed.");//No I18n
						}
						data.put(opr, "conf removed");
						logger.log(Level.INFO, "AWS Adapter Conf reinitialized Successfully.");//No I18n
					}
					break;
				default :
					data.put(" Unknown opr ", opr);
			}
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE, " Exception in update aws conf : "+params, ex);//No I18n
			data.put(" Exception ", ex);
		}
		return data;
	}

	private Hashtable handleEditConf(String propsfilepath, Hashtable params)
	{
		Hashtable data = new Hashtable();
		data.put("success", false);
		try
		{
			String key = (String)params.get("confkey");
			String value = (String)params.get("confvalue");
			String opr = (String) params.get(AWSConstants.OPR);
			Hashtable updatedconf = new Hashtable();
			updatedconf.put(key,value);
			Properties existingProps = ConfManager.getProperties(propsfilepath);
			Hashtable existingConf = (Hashtable)existingProps.clone();	
			int changehash = existingProps.hashCode();	
					
			if(updatedconf != null && existingConf != null)
			{
				existingConf.putAll(updatedconf);
				for(Enumeration e = existingConf.keys(); e.hasMoreElements();)
				{
					String propKey = (String)e.nextElement();
					propKey = propKey.replace("\u00A0", AWSConstants.EMPTY_STRING); //No I18N
					String propValue = (String)existingConf.get(propKey);
					propValue = propValue.replace("\u00A0", AWSConstants.EMPTY_STRING); //No I18N
					if(propKey.contains("NEWCONFKEY") || propValue.contains("NEWCONFVALUE"))
					{
						continue;
					}
					existingProps.setProperty(propKey.trim(),propValue.trim());
				}
				if( existingProps.hashCode() != changehash)
				{
					FileOutputStream fos = null;
					try
					{
						fos = new FileOutputStream(propsfilepath);
						existingProps.store(fos,"$Id$"); //No I18N
						data.put("success", true);
					}
					catch(Exception ex)
					{
						logger.log(Level.SEVERE, " Exception during update conf in file : "+opr, ex);
						data.put(opr," Problem updating the conf "+ex);
					}finally{try{ fos.close();}catch(Exception ex){}}
				}
				else
				{
					data.put(opr," Nothing changed ");
				}
			}
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE, " Exception in editconf : "+propsfilepath+", params : "+params, ex);//No I18n
			data.put(" Exception in Editconf ", ex);
		}
		return data;
	}

	private Hashtable handleRemoveConf(String propsfilepath, Hashtable params)
	{
		Hashtable data = new Hashtable();
		data.put("success", false);
		try
		{
			boolean isConfRemoved = false;
			String key = (String)params.get("confkey");
			String opr = (String) params.get(AWSConstants.OPR);
			Properties existingProps = ConfManager.getProperties(propsfilepath);
			if(existingProps.containsKey(key))
			{
				existingProps.remove(key);
				isConfRemoved = true;
			}
			else
			{
				data.put("removeconf on "+opr, "Key : "+key+" Not Found.");
			}
			if(isConfRemoved)
			{
				FileOutputStream fos = null;
				try
				{
					fos = new FileOutputStream(propsfilepath);
					existingProps.save(fos,"$Id$"); //No I18N
					data.put("success", true);
				}
				catch(Exception ex)
				{
					logger.log(Level.SEVERE, " Exception during conf removal : "+opr, ex);//No I18n
					data.put(opr," Problem updating the conf "+ex);
				}finally{try{ fos.close();}catch(Exception ex){}}
			}
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE, " Exception in removeconf : "+propsfilepath+", params : "+params, ex);//No I18n
			data.put(" Exception in removeconf ", ex);
		}
		return data;
	}

}
