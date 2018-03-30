import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Vector;

import org.apache.log4j.Logger;

import com.matrixone.apps.common.InboxTask;
import com.matrixone.apps.common.Person;
import com.matrixone.apps.common.Route;
import com.matrixone.apps.domain.DomainObject;
import com.matrixone.apps.domain.DomainRelationship;
import com.matrixone.apps.domain.util.ContextUtil;
import com.matrixone.apps.domain.util.FrameworkException;
import com.matrixone.apps.domain.util.FrameworkProperties;
import com.matrixone.apps.domain.util.MapList;
import com.matrixone.apps.domain.util.PropertyUtil;
import com.matrixone.apps.domain.util.eMatrixDateFormat;
import com.matrixone.apps.engineering.EngineeringUtil;
import com.matrixone.apps.framework.ui.UIUtil;

import matrix.db.BusinessObject;
import matrix.db.Context;
import matrix.db.JPO;
import matrix.util.StringList;

public class DBCChange_mxJPO extends emxChangeBase_mxJPO{
	private Logger m_logger = Logger.getLogger(DBCChange_mxJPO.class);

	public DBCChange_mxJPO(Context context,String[] args) throws Exception
	{
		super(context, args);
	}
	
	//add by ryan 
	public void DBCAutoStartRouteFromDBCCAPPSync(Context context,String[] args) throws Exception
	{
		System.out.println("-----------execute DBCAutoStartRouteFromDBCCAPPSync------------");
		String objectId = args[0];  //object Id;
		if(objectId.equalsIgnoreCase(null) || objectId.equalsIgnoreCase(""))
		{
			String errorMsg ="Get the error ObjectId from the Trigger!";
			throw new Exception(errorMsg);
		}
		
		DomainObject  domObj = DomainObject.newInstance(context,objectId);
		String strDBCCAPPSync = (String)domObj.getAttributeValue(context, "DBC CAPP Sync");
		if((strDBCCAPPSync == null) || (!strDBCCAPPSync.equalsIgnoreCase("yes")))
		{
			DBCAutoStartRoute(context, args);
		}
	}
	//add end
	
	/**           
	 * autostartRoute	
	 * @param context
	 * @param args
	 * @return
	 * @throws Exception
	 */
		public int DBCAutoStartRoute(Context context,String[] args) throws Exception{
			String docId = args[0];  //object Id;
			String routeBlockDocPolicyState = args[1]; //state_Review
			if(docId.equalsIgnoreCase(null) || docId.equalsIgnoreCase(""))
			{
				String errorMsg ="Get the error ObjectId from the Trigger!";
				throw new Exception(errorMsg);
			}
			if(routeBlockDocPolicyState.equalsIgnoreCase(null) || routeBlockDocPolicyState.equalsIgnoreCase(""))
			{
				String errorMsg ="Get the error State from the Trigger!";
				throw new Exception(errorMsg);
			}
			DomainObject  objDoc = DomainObject.newInstance(context,docId);
			StringList busList = new StringList();
			busList.add("id");
			StringList relList = new StringList();
			relList.add("id[connection]");
			relList.add("attribute[Route Base State]");
			MapList routeInfo = objDoc.getRelatedObjects(
					context,
					"Object Route",
					"Route",
					busList,
					relList,
					false,
					true,
					(short)1,
					null,
					null);
			Map getRouteInfo  = null;
			for(Iterator itRouteInfo = routeInfo.iterator();itRouteInfo.hasNext();)
			{
				getRouteInfo = (Map) itRouteInfo.next();
				String routeId = (String) getRouteInfo.get("id");
				String routeRelId = (String) getRouteInfo.get("id[connection]");
				String relAttrState = (String) getRouteInfo.get("attribute[Route Base State]");
				if((relAttrState.equals(routeBlockDocPolicyState)))
				{
					DomainObject objRoute = DomainObject.newInstance(context,routeId);
					String routeStatus = (String)objRoute.getAttributeValue(context, "Route Status");
					if(routeStatus.equalsIgnoreCase("Not Started"))
					{
						startRoute(context,routeId);
					}else if(routeStatus.equals("Stopped"))
					{
						Route routeIns = new Route(routeId);
						routeIns.resume(context);
		                routeIns.startTasksOnCurrentLevel(context);
		                InboxTask.setTaskTitle(context, routeId);
					}
				}else
				{
					continue;
				}
			}
			return 0;
		}
		
		/**
		 *                              
		 * @param context
		 * @param args
		 * @return
		 * @throws Exception
		 */
		public int DBCCheckSelfRouteNeedFinishedForDemote(Context context,String[] args) throws Exception
		{
			String strId = args[0];
			String strState = args[1];
			DomainObject strObject = new DomainObject(strId);
			String strPolicy = strObject.getInfo(context, DomainObject.SELECT_POLICY);
			StringList busList = new StringList();
			busList.add(DomainObject.SELECT_ID);
			StringList relList = new StringList();
			relList.add("id[connection]");
			MapList routeList = strObject.getRelatedObjects(
					context,
					"Object Route",
					"*",
					busList,
					relList,
					false,
					true,
					(short)1,
					null,null);
			for(Iterator itRoute = routeList.iterator(); itRoute.hasNext();)
			{
				Map routeMap = (Map) itRoute.next();
				String routeId = (String)routeMap.get(DomainObject.SELECT_ID);
				DomainObject routeIns = new DomainObject(routeId);
				String attrState = routeIns.getAttributeValue(context, "Route Status");
				String relId = (String)routeMap.get("id[connection]");
				DomainRelationship relIns = new DomainRelationship(relId);
				String baseState = relIns.getAttributeValue(context, "Route Base State");
				if(attrState.equalsIgnoreCase("Started") && baseState.equals(strState))
				{
					emxContextUtil_mxJPO.mqlNotice(context,
							EngineeringUtil.i18nStringNow("DBC.emxEngineeringCentral.DBCChange.CannotDemote",
									context.getSession().getLanguage()));
					return 1;
				}else{
					continue;
				}
			}
			return 0;
		}
		
		/**
		 *                                     
		 * @param context
		 * @param args
		 * @return
		 * @throws Exception
		 */
		public int DBCCheckRelatedObjectsRouteNeedFineshedForDemote(Context context,String[] args) throws Exception
		{
			String strObjectId = args[0];
			String strObjectState = args[1];
			DomainObject strObject = new DomainObject(strObjectId);
			String current = strObject.getInfo(context, DomainObject.SELECT_CURRENT);
			String strPolicy = strObject.getInfo(context, DomainObject.SELECT_POLICY);
			StringList busList = new StringList();
			busList.add(DomainObject.SELECT_ID);
			StringList relList = new StringList();
			relList.add("id[connection]");
			MapList  billsList = strObject.getRelatedObjects(
					context,
					"Affected Item",
					"*",
					busList,
					relList,
					true,
					false,
					(short)1,
					null,null);
			for(Iterator itBills = billsList.iterator(); itBills.hasNext();)
			{
				Map billsMap = (Map) itBills.next();
				String billsId =(String) billsMap.get(DomainObject.SELECT_ID);
				DomainObject billsIns = new DomainObject(billsId);
				MapList routeList = billsIns.getRelatedObjects(
						context,
						"Object Route",
						"*",
						busList,
						relList,
						false,
						true,
						(short)1,
						null,null);
				for(Iterator itRoute = routeList.iterator(); itRoute.hasNext();)
				{
					Map routeMap = (Map)itRoute.next();
					String routeId = (String) routeMap.get(DomainObject.SELECT_ID);
					DomainObject routeIns = new DomainObject(routeId);
					String attrValue = routeIns.getAttributeValue(context, "Route Status");
					String relId = (String)routeMap.get("id[connection]"); 
					DomainRelationship relIns = new DomainRelationship(relId);
					String baseState = relIns.getAttributeValue(context, "Route Base State");
					if(attrValue.equalsIgnoreCase("Started") && baseState.equals(strObjectState))
					{
						emxContextUtil_mxJPO.mqlNotice(context,
								EngineeringUtil.i18nStringNow("DBC.emxEngineeringCentral.DBCChange.CannotDemote",
										context.getSession().getLanguage()));
						return 1;
					}else{
						continue;
					}
				}
			}
			return 0;
	
		}
		
/**
 * checkRoute		
 * @param context
 * @param args
 * @return
 * @throws Exception
 */
		public int checkRouteExist(Context context,String[] args)throws Exception{
			int isNotExist = 1;
			String objId = args[0];
			String objPolicyState = args[1];
			if(objId.equalsIgnoreCase(null) || objId.equalsIgnoreCase(""))
			{
				String errorMsg ="Get the error ObjectId from the Trigger!";
				throw new Exception(errorMsg);
			}
			if(objPolicyState.equalsIgnoreCase(null) || objPolicyState.equalsIgnoreCase(""))
			{
				String errorMsg ="Get the error state from the Trigger!";
				throw new Exception(errorMsg);
			}
			DomainObject objInstance = new DomainObject(objId);
			
			StringList busSelect = new StringList();
			busSelect.add("id");
			StringList relSelect = new StringList();
			relSelect.add("id[connection]");
			MapList routeInfo = objInstance.getRelatedObjects(
					context,
					"Object Route",
					"Route",
					busSelect,
					relSelect,
					false,
					true,
					(short)1,
					null,
					null);
			for(Iterator itRouteInfo = routeInfo.iterator();itRouteInfo.hasNext();)
			{
				Map getRouteInfo = (Map)itRouteInfo.next();
				String routeId = (String)getRouteInfo.get("id");
				String relId  = (String) getRouteInfo.get("id[connection]");
				DomainObject routeInstance = new DomainObject(routeId);
				DomainRelationship relInstance = new DomainRelationship(relId);
				String routeStatus = routeInstance.getAttributeValue(context, "Route Status");
				String routeBlockstates = relInstance.getAttributeValue(context, "Route Base State");
				if(routeBlockstates.equalsIgnoreCase(objPolicyState) && (!routeStatus.equals("Finished") || !routeStatus.equals("Stopped")))
				{
					return 0;
				}
			}
			String strType = objInstance.getType(context);
			
			if(objPolicyState.equals("state_Review"))
			{
				emxContextUtil_mxJPO.mqlNotice(context,
						EngineeringUtil.i18nStringNow("DBC.emxEngineeringCentral.ENVCheckRouteExistAffectedItemsStateReviewWarning",
								context.getSession().getLanguage()));
			}else if(objPolicyState.equals("state_Approve"))
			{
				emxContextUtil_mxJPO.mqlNotice(context,
						EngineeringUtil.i18nStringNow("DBC.emxEngineeringCentral.ENVCheckRouteExistAffectedItemsStateReleaseWarning",
								context.getSession().getLanguage()));
			}
			
			return isNotExist;
		}
				
/**
 * check User		
 * @param context
 * @param args
 * @return
 * @throws Exception
 */
	public int checkAnonymousUsers(Context context,String[] args) throws Exception{
		int isNotExist = 0;
		String objId = args[0];//${OBJECTID}
		String policyState = args[1]; //policy_Review
		String anonymousUser = args[2]; // Nobody
		if(objId.equalsIgnoreCase(null) || objId.equalsIgnoreCase(""))
		{
			String errorMsg ="Get the error ObjectId from the Trigger!";
			throw new Exception(errorMsg);
		}
		if(policyState.equalsIgnoreCase(null) || policyState.equalsIgnoreCase(""))
		{
			String errorMsg ="Get the error State from the Trigger!";
			throw new Exception(errorMsg);
		}
		if(anonymousUser.equalsIgnoreCase(null)|| anonymousUser.equalsIgnoreCase(""))
		{
			String errorMsg ="Get the error anonymous User from the Trigger!";
			throw new Exception(errorMsg);
		}
		StringList busSelect = new StringList();
		busSelect.add("id");
		StringList relSelect = new StringList();
		relSelect.add("id[connection]");
		DomainObject objIns = new DomainObject(objId);
		String strPolicy = objIns.getInfo(context, DomainObject.SELECT_POLICY);
		MapList routeInfo = objIns.getRelatedObjects(
				context,
				"Object Route",
				"Route",
				busSelect,
				relSelect,
				false,
				true,
				(short)1,
				null,null);
		for(Iterator itRouteInfo = routeInfo.iterator();itRouteInfo.hasNext();)
		{
			Map getRouteInfo = (Map)itRouteInfo.next();
			String routeId = (String)getRouteInfo.get("id");
			String relId = (String)getRouteInfo.get("id[connection]");
			DomainObject routeIns = new DomainObject(routeId);
			DomainRelationship relIns = new DomainRelationship(relId);
			String states = relIns.getAttributeValue(context, "Route Base State");
			StringList busList = new StringList();
			busList.add(DomainObject.SELECT_ID);
			if(states.equals(policyState))
			{
				MapList personInfo = routeIns.getRelatedObjects(
						context,
						"Route Node",
						"*",
						busList,
						relSelect,
						false,
						true,
						(short)1,
						null,null);
				Iterator i = personInfo.iterator();
				while(i.hasNext())
				{
					Map map = (Map) i.next();
					String personId = (String) map.get(DomainObject.SELECT_ID);
					DomainObject person = new DomainObject(personId);
					String strType = person.getType(context);
					String relationshipId = (String)map.get("id[connection]");
					DomainRelationship relationshipIns = new DomainRelationship(relationshipId);
					String attrValue = relationshipIns.getAttributeString(context, "Route Task User");
					String strState = PropertyUtil.getSchemaProperty(context,"policy", strPolicy,states);
					//System.out.println(!strType.equals("Person") && attrValue.equals(""));
					if(strType.equals("Person"))
					{
						continue;
					}else{
						if(attrValue.equals("")){
							
							emxContextUtil_mxJPO.mqlNotice(context,
								"At State:"	+strState+ ",  "  + EngineeringUtil.i18nStringNow("DBC.emxEngineeringCentral.ENVCheckUserAffectedItemsWarning",
										context.getSession().getLanguage()));
							return 1;
						}
					}
					
				}
			}
		}
		return isNotExist;
	}	
		
	/**
	 *                                         
	 * @param context
	 * @param args
	 * @return
	 * @throws Exception
	 */
			public int checkRouteExistAndCheckUser(Context context,String[] args) throws Exception{
				String strObjId = args[0];
				String strState = args[1];
				String anonymousUser = args[2];
				if(strObjId.equalsIgnoreCase(null) || strObjId.equalsIgnoreCase(""))
				{
					String errorMsg ="Get the error ObjectId from the Trigger!";
					throw new Exception(errorMsg);
				}
				if(strObjId.equalsIgnoreCase(null) || strObjId.equalsIgnoreCase(""))
				{
					String errorMsg ="Get the error State from the Trigger!";
					throw new Exception(errorMsg);
				}
				if(anonymousUser.equalsIgnoreCase(null)|| anonymousUser.equalsIgnoreCase(""))
				{
					String errorMsg ="Get the error anonymous User from the Trigger!";
					throw new Exception(errorMsg);
				}
				String[] checkRouteExistParam = {strObjId,strState};
				int i = checkRouteExist(context,checkRouteExistParam);
				String[] checkAnonymousUsersParam ={strObjId,strState,anonymousUser};
				int j = checkAnonymousUsers(context,checkAnonymousUsersParam);
				if(i == 0 && j==0 ){
					return 0;
				}
				return 1;
			}
			
	/**
	 * 		ECO,ERO                 
	 */
	 public MapList getAffectedItems(Context context,String[] args)throws Exception
	 {
		 HashMap hashMap = (HashMap)JPO.unpackArgs(args);
		 String objectId = (String)hashMap.get("objectId");
		 DomainObject strObject = new DomainObject(objectId);
		 StringList busList = new StringList();
		 busList.add(DomainObject.SELECT_ID);
		 StringList relList = new StringList();
		 relList.add(DomainRelationship.SELECT_ID);
		 MapList affectedMapList = strObject.getRelatedObjects(context, "Affected Item", "*", busList, relList, false, true, (short)1, null, null);
		 
		 return affectedMapList;
		 
	 }
	 
	 /**
	  * ECO,ERO              ree: structure fuction
	  * @param context
	  * @param args
	  * @return
	  * @throws Exception
	  */
	 public MapList getAffectedItemsForTree(Context context,String[] args)throws Exception
	 {
		 HashMap hashMap = (HashMap)JPO.unpackArgs(args);
		 HashMap paramMap = (HashMap) hashMap.get("paramMap");
		 String objectId = (String)paramMap.get("objectId");
		 DomainObject strObject = new DomainObject(objectId);
		 StringList busList = new StringList();
		 busList.add(DomainObject.SELECT_ID);
		 StringList relList = new StringList();
		 relList.add(DomainRelationship.SELECT_ID);
		 MapList affectedMapList = strObject.getRelatedObjects(context, "Affected Item", "*", busList, relList, false, true, (short)1, null, null);
		 
		 return affectedMapList;
		 
	 }
	 
	 /**
	  * 
	  * @param context
	  * @param args
	  * @return
	  * @throws Exception
	  */
	 public MapList DBCGetEBOMStructure(Context context,String[] args)throws Exception
	 {
		 HashMap hashMap = (HashMap)JPO.unpackArgs(args);
		 HashMap paramMap = (HashMap) hashMap.get("paramMap");
		 String objectId = (String)paramMap.get("objectId");
		 DomainObject strObject = new DomainObject(objectId);
		 StringList busList = new StringList();
		 busList.add(DomainObject.SELECT_ID);
		 StringList relList = new StringList();
		 relList.add(DomainRelationship.SELECT_ID);
		 MapList affectedMapList = strObject.getRelatedObjects(context, "EBOM", "*", busList, relList, false, true, (short)1, null, null);
		 
		 return affectedMapList;
		 
	 }
	 
	 /**
	  * ECO,ERO                ----       
	  * @param context
	  * @param args
	  * @return
	  * @throws Exception
	  */
	 public MapList getPartAffectedItems(Context context,String[] args)throws Exception
	 {
		 HashMap hashMap = (HashMap)JPO.unpackArgs(args);
		 String objectId = (String)hashMap.get("objectId");
		 DomainObject strObject = new DomainObject(objectId);
		 String strPartType = "DBC Top Assembly,DBC Standard Part,DBC General Part,DBC Non-standard Part,Part";
		 StringList busList = new StringList();
		 busList.add(DomainObject.SELECT_ID);
		 StringList relList = new StringList();
		 relList.add(DomainRelationship.SELECT_ID);
		 MapList affectedMapList = strObject.getRelatedObjects(context,"Affected Item", strPartType , busList, relList, false, true, (short)1, null, null);
		 
		 return affectedMapList;
		 
	 }
	 
	 /**
	  * ECO,ERO                ----       
	  * @param context
	  * @param args
	  * @return
	  * @throws Exception
	  */
	 public MapList getSpecAffectedItems(Context context,String[] args)throws Exception
	 {
		 HashMap hashMap = (HashMap)JPO.unpackArgs(args);
		 String objectId = (String)hashMap.get("objectId");
		 DomainObject strObject = new DomainObject(objectId);
		 String strSpecType = "DOCUMENTS";
		 
		 StringList busList = new StringList();
		 busList.add(DomainObject.SELECT_ID);
		 StringList relList = new StringList();
		 relList.add(DomainRelationship.SELECT_ID);
		 MapList affectedMapList = strObject.getRelatedObjects(context,"Affected Item", strSpecType , busList, relList, false, true, (short)1, null, null);
		 return affectedMapList;
	 }
	 
	 /**
	  * 
	  * @param context
	  * @param args
	  * @return
	  * @throws Exception
	  */
	 public MapList getExpandObjects(Context context,String[] args) throws Exception
	 {
		 HashMap hashMap = (HashMap)JPO.unpackArgs(args);
		 String objectId = (String)hashMap.get("objectId");
		 DomainObject strObject = new DomainObject(objectId);
		 StringList busList = new StringList();
		 busList.add(DomainObject.SELECT_ID);
		 StringList relList = new StringList();
		 relList.add(DomainRelationship.SELECT_ID);
		 MapList affectedMapList = strObject.getRelatedObjects(context, "EBOM", "*", busList, relList, false, true, (short)1, null, null);
		 MapList mapList = new MapList();
		 return mapList; 
	 }
	 
	 /**
	  *                      'Review'     
	  * @param context
	  * @param args
	  * @return
	  * @throws Exception
	  */
	 public int DBCCheckAllAffectedItemsIsStateReview(Context context,String[] args) throws Exception
	 {
		 String objectId = args[0];
		 DomainObject strObject = new DomainObject(objectId);
		 StringList busList = new StringList();
		 busList.add(DomainObject.SELECT_ID);
		 StringList relList = new StringList();
		 relList.add(DomainRelationship.SELECT_ID);
		 MapList affectedItemsMapList = strObject.getRelatedObjects(context,"Affected Item","*",busList,relList,false,true,(short)1,null,null);
		 Iterator it = affectedItemsMapList.iterator();
		 while(it.hasNext())
		 {
			 Map affectedItemMap = (Map)it.next();
			 String affectedItemId = (String)affectedItemMap.get(DomainObject.SELECT_ID);
			 DomainObject strAffectedItem = new DomainObject(affectedItemId);
			 String strName = strAffectedItem.getName(context);
			 String current = strAffectedItem.getInfo(context, DomainObject.SELECT_CURRENT);
			 if(!current.equalsIgnoreCase("Review"))
			 {
				 emxContextUtil_mxJPO.mqlNotice(context,
						 strName + EngineeringUtil.i18nStringNow("DBC.emxEngineeringCentral.DBCCheckAllAffectedItemsIsStateReview",context.getSession().getLanguage()));
				 return 1;
			 }
		 }
		 return 0;
	 }
	 
	 /**
	  *                         
	  * @param context
	  * @param args
	  * @return
	  * @throws Exception
	  */
	 public int DBCSetDesignDate(Context context,String[]  args) throws Exception
	 {
		 String objectId = args[0];
		 DomainObject strObject = new DomainObject(objectId);
		 Date date = new Date(); 
		 SimpleDateFormat formatterTest   = new SimpleDateFormat (eMatrixDateFormat.getInputDateFormat(),Locale.US);
		 String dateString = formatterTest.format(date);
		 strObject.setAttributeValue(context, "DBC Design Date",dateString);
		 return 0;
	 }
	 
	 /**
	  *               able   webform      
	  * @param context
	  * @param args
	  * @return
	  * @throws Exception
	  */
	 public Object DBCGetEBOMquote(Context context,String[] args) throws Exception
	 {
		// Vector  number = new Vector();
		 HashMap programMap = (HashMap) JPO.unpackArgs(args);
		 MapList objectList = (MapList)programMap.get("objectList");
		 HashMap paramMap = (HashMap)programMap.get("paramMap");  
		 String objectId ="";
		 if(objectList == null)
		 {
			 objectId = (String)paramMap.get("objectId");
			 StringList busList = new StringList();
			 busList.add(DomainObject.SELECT_ID);
			 DomainObject strObject = new DomainObject(objectId);
			 String strType = strObject.getType(context);
			 String value = "";
			 StringList typeList = new StringList();
			 typeList.add("DBC Top Assembly");
			 typeList.add("DBC Standard Part");
			 typeList.add("DBC General Part");
			 typeList.add("DBC Non-standard Part");
			 if(typeList.contains(strType)){
				 MapList mapList = strObject.getRelatedObjects(context, "EBOM", "*", busList, null, true, false, (short)1, null, null);
				 int size = mapList.size();
				 String strUrl = "../common/emxTree.jsp?objectId="+objectId+"&DefaultCategory=LCPartWhereUsedTreeCategory";
				 value = " <a href=\"javascript:emxTableColumnLinkClick('"+strUrl+"','600','600','false','popup')\">"+size+"</a>";
			 }else {
				 MapList CADDraiwngList = strObject.getRelatedObjects(context, "Part Specification", "*", busList, null, true, false, (short)1, null, null);
				 int draiwngsize = CADDraiwngList.size();
				 String strUrl = "../common/emxTree.jsp?objectId="+objectId+"&DefaultCategory=ENCSpecRelatedParts";
				 value = " <a href=\"javascript:emxTableColumnLinkClick('"+strUrl+"','600','600','false','popup')\">"+draiwngsize+"</a>";
			 }
			 return value;
		 }else{
			 Vector returnvalue = new Vector();
	  		 Iterator it = objectList.iterator();
	  		 StringList busList = new StringList();
	  		 busList.add(DomainObject.SELECT_ID);
	  		 while(it.hasNext()){
	  			 Map map = (Map)it.next();
	  			 objectId = (String)map.get("id");
	  			 DomainObject strObject = new DomainObject(objectId);
	  			 String strType = strObject.getType(context);
	  			 String value = "";
	  			 StringList typeList = new StringList();
	  			 typeList.add("DBC Top Assembly");
	  			 typeList.add("DBC Standard Part");
	  			 typeList.add("DBC General Part");
	  			 typeList.add("DBC Non-standard Part");
	  			 if(typeList.contains(strType)){
	  				 MapList mapList = strObject.getRelatedObjects(context, "EBOM", "*", busList, null, true, false, (short)1, null, null);
	  				 int size = mapList.size();
	  				 String strUrl = "../common/emxTree.jsp?objectId="+objectId+"&amp;DefaultCategory=LCPartWhereUsedTreeCategory";
	  				value = " <a href=\"javascript:emxTableColumnLinkClick('"+strUrl+"','600','600','false','popup')\">"+size+"</a>";
	  				 returnvalue.add(value);
	  			 }else {
	  				 MapList CADDraiwngList = strObject.getRelatedObjects(context, "Part Specification", "*", busList, null, true, false, (short)1, null, null);
	  				 int draiwngsize = CADDraiwngList.size();
	  				 String strUrl = "../common/emxTree.jsp?objectId="+objectId+"&amp;DefaultCategory=ENCSpecRelatedParts";
	  				 value = " <a href=\"javascript:emxTableColumnLinkClick('"+strUrl+"','600','600','false','popup')\">"+draiwngsize+"</a>";
	  				 returnvalue.add(value);
	  			 }
	  		 }
	  		 return returnvalue;
		 }	
		
	 }
	 
	 /**
	  * 
	  * @param context
	  * @param args
	  * @return
	  * @throws Exception
	  */
	 public int DBCCheckDrawingIsGetReview(Context context,String[] args) throws Exception
	 {
		 String objectId = args[0];
		 
		 DomainObject strObject = new DomainObject(objectId);
		 String strType = strObject.getType(context);
		 StringList busList = new StringList();
		 busList.add(DomainObject.SELECT_ID);
		 StringList relList = new StringList();
		 relList.add(DomainRelationship.SELECT_ID);
		 MapList affectedMapList = strObject.getRelatedObjects(context,"Affected Item", "ACAD Drawing,ProE Drawing" , busList, relList, false, true, (short)1, null, null);
		 Iterator i = affectedMapList.iterator();
		 while(i.hasNext())
		 {
			 Map map = (Map)i.next();
			 String drawingId = (String) map.get(DomainObject.SELECT_ID);
			 String strRelId = (String) map.get(DomainRelationship.SELECT_ID);
			 DomainObject drawingObj = new DomainObject(drawingId);
			 String current = drawingObj.getInfo(context, DomainObject.SELECT_CURRENT);
			 String strName = drawingObj.getName(context);
			 
			 DomainRelationship strRel = new DomainRelationship(strRelId);
			 String strAttr = strRel.getAttributeValue(context, "Requested Change");
			 if(strAttr.equals("For Release") && !current.equals("Review"))
			 {
					 emxContextUtil_mxJPO.mqlNotice(context,
							 strName + EngineeringUtil.i18nStringNow("DBC.emxEngineeringCentral.DBCCheckDrawingIsGetReview",context.getSession().getLanguage()));
					 return 1;
				 
			 }
		 }
		 return 0;
	 }
	 
	 /**
	  *                       
	  * @return
	  */
	 public int promoteDBCProcessDoc(Context context,String[] args) throws Exception
	 {
		 String objectId = args[0];
		 DomainObject strObj = new DomainObject(objectId);
		 StringList busList = new StringList();
		 busList.add(DomainObject.SELECT_ID);
		 StringList relList = new StringList();
		 relList.add(DomainRelationship.SELECT_ID);
		 MapList mapList = strObj.getRelatedObjects(context, "Affected Item", "*", busList, relList, false, true, (short)1, null, null);
		 Iterator i = mapList.iterator();
		 while(i.hasNext())
		 {
			 Map map = (Map)i.next();
			 String strChildId = (String) map.get(DomainObject.SELECT_ID);
			 DomainObject strChild = new DomainObject(strChildId);
			 ContextUtil.pushContext(context);
			 strChild.gotoState(context, "Release");
			 ContextUtil.popContext(context);
		 }
		 return 0;
	 }
	 
	 /**
	  *   CR     ubmit               Evaluate;  CR     eview               Plan ECO
	  * @param context
	  * @param args
	  * @return
	  * @throws Exception
	  */
	 public int promoteECRState(Context context ,String[] args) throws Exception
	 {
		 String objectId = args[0];
		 DomainObject strObj = new DomainObject(objectId);
		 String strCurrent = strObj.getInfo(context, DomainObject.SELECT_CURRENT);
		 if(strCurrent.equals("Submit"))
		 {
			 strObj.gotoState(context, "Evaluate");
		 }else if(strCurrent.equals("Review")){
			 strObj.gotoState(context, "Plan ECO");
		 }
		 return 0;
	 }
	 
	 /**
	  * ECO tree                 
	  * @param context
	  * @param args
	  * @return
	  * @throws Exception
	  */
	 public String DBCShowTreeName(Context context,String[] args) throws Exception
     {
		 HashMap programMap = (HashMap) JPO.unpackArgs(args);
		 HashMap paramMap=(HashMap)programMap.get("paramMap");
		 MapList objectList = (MapList)programMap.get("objectList");
		 String objectId = (String)paramMap.get("objectId"); 
		 DomainObject strObj = new DomainObject(objectId);
		 String strName = strObj.getName(context);
		 String strType  = strObj.getType(context);
		 String rev = strObj.getRevision(context);
		 StringList typeList = new StringList();
		 typeList.add("DBC Top Assembly");
		 typeList.add("DBC Standard Part");
		 typeList.add("DBC General Part");
		 typeList.add("DBC Non-standard Part");
		 typeList.add("Part");
		 if(typeList.contains(strType))
		 {
			 String partDrawingNumber = strObj.getAttributeValue(context, "DBC Drawing Number");
			 String PartCNName = strObj.getAttributeValue(context, "DBC CN Part Name");
			 String partRev = strObj.getRevision(context);
			 if(partDrawingNumber.equals(""))
			 {
				 return PartCNName +" "+ partRev;
			 }
			 return partDrawingNumber +" "+ PartCNName+" " + partRev;
		 }
		 
		 return strName+" "+ rev ;
     }
	 
	 /**
	  *                           
	  * @param context
	  * @param args
	  * @return
	  * @throws Exception
	  */
	 public int DBCCheckChange(Context context,String[] args)throws Exception
	 {
		 String fromObjId = args[0];
		 String toObjId = args[1];
		 DomainObject toObj = new DomainObject(toObjId);
		 DomainObject fromObj = new DomainObject(fromObjId);
		 String strFromCurrent = fromObj.getInfo(context, DomainObject.SELECT_CURRENT);
		 String strFromName = fromObj.getName(context);
		 if(strFromCurrent.equals("Create") || strFromCurrent.equals("Define Components"))
		 {
			 StringList busList = new StringList();
			 busList.add(DomainObject.SELECT_ID);
			 String strToName = toObj.getName(context);
			 MapList mapList = toObj.getRelatedObjects(context,"Affected Item","*",busList,null,true,false,(short)1,null,null);
			 Iterator i = mapList.iterator();
			 StringList stateList = new StringList();
			 stateList.add("Release");
			 stateList.add("Implemented");
			 stateList.add("Cancelled");
			 stateList.add("Plan ECO");
			 stateList.add("Complete");
			 if(mapList.size()>0){
				 while(i.hasNext()){
					 Map map =  (Map)i.next();
					 String changeId = (String)map.get(DomainObject.SELECT_ID);
					 DomainObject changeObj = new DomainObject(changeId);
					 String strChangeCurrent =  changeObj.getInfo(context, DomainObject.SELECT_CURRENT);
					 if(!stateList.contains(strChangeCurrent))
					 {
						 //modify ryan 2013-11-17
						 String strNotice = strToName + " " + EngineeringUtil.i18nStringNow("DBC.emxEngineeringCentral.DBCCheckChange.Part1",context.getSession().getLanguage()) + 
								 " " + changeObj.getName(context) + EngineeringUtil.i18nStringNow("DBC.emxEngineeringCentral.DBCCheckChange.Part2",context.getSession().getLanguage());
						 emxContextUtil_mxJPO.mqlNotice(context, strNotice);
						//	 ${CLASS:emxContextUtil}.mqlNotice(context,
					//			 EngineeringUtil.i18nStringNow("DBC.emxEngineeringCentral.DBCCheckChange.Part",context.getSession().getLanguage()));
						 //modify end
						 return 1;
					 }
				 }
			 }
		 }else{
			 emxContextUtil_mxJPO.mqlNotice(context,
					   EngineeringUtil.i18nStringNow("DBC.emxEngineeringCentral.DBCCheckChange.Change",context.getSession().getLanguage()));
			 return 1;
		 }
		 return 0;
	 }
	 
	 /**
	  * EC Part          rigger                                
	  * @param context
	  * @param args
	  * @return
	  * @throws Exception
	  */
	 public int checkJianBenTuPart(Context context,String[] args) throws Exception
	 {
		 String objectId = args[0];
		 DomainObject srtObj = new DomainObject(objectId);
		 String strAttrValue = srtObj.getAttributeValue(context, "DBC Drawing Number");
		 StringList busList = new StringList();
		 busList.add(DomainObject.SELECT_ID);
		 MapList mapList = srtObj.getRelatedObjects(context, "EBOM", "*", busList, null, false, true, (short)1, null, null);
		 Iterator i = mapList.iterator();
		 while(i.hasNext())
		 {
			 Map map  = (Map) i.next();
			 String strChildId = (String) map.get(DomainObject.SELECT_ID);
			 DomainObject strChildObj = new DomainObject(strChildId);
			 String strChildAttrValue = strChildObj.getAttributeValue(context, "DBC Drawing Number");
			 String strChildDrawingType = strChildObj.getAttributeValue(context, "DBC Drawing Type");
			 if(strChildDrawingType.equals("JianBenTu") && strAttrValue.length()>0)
			 {
				 if(!strAttrValue.equals(strChildAttrValue))
				 {
						String strName = strChildObj.getName(context);
						 emxContextUtil_mxJPO.mqlNotice(context,
								strName + EngineeringUtil.i18nStringNow("DBC.emxEngineeringCentral.checkJianBenTuPart",context.getSession().getLanguage()));
						return 1;
				 }
			 }
		 }
		 return 0;
	 }
	 /**
	  *                        
	  * @param context
	  * @param args
	  * @return
	  * @throws Exception
	  */
	 
	  public Vector getNameOfMyView(Context context,String[] args) throws Exception
	 {
		 Vector vector = new Vector();
		 HashMap programMap = (HashMap) JPO.unpackArgs(args);
		 MapList objectList = (MapList)programMap.get("objectList");
		 Iterator it = objectList.iterator();
		 while(it.hasNext())
		 {
			Map map = (Map) it.next();
			String objectId = (String)map.get("id");
			DomainObject strObj = new DomainObject(objectId);
			String strName = strObj.getName(context);
			String strUrl = "../common/emxTree.jsp?objectId="+objectId+"&amp;mode=insert";
			String	value = " <a href=\"javascript:emxTableColumnLinkClick('"+strUrl+"','600','600','false','popup')\">"+strName+"</a>";
			vector.add(strName);
			
		 }
		 return vector;
	 }
	  
	  /**
	   *                ----            
	   * @param context
	   * @param args
	   * @return
	   * @throws Exception
	   */
	  public MapList DBCGetToPrintPart(Context context,String[] args)throws Exception
		 {
			 HashMap hashMap = (HashMap)JPO.unpackArgs(args);
			 String objectId = (String)hashMap.get("objectId");
			 DomainObject strObject = new DomainObject(objectId);
			 String strPartType = "DBC Top Assembly,DBC Standard Part,DBC General Part,DBC Non-standard Part,Part";
			 StringList busList = new StringList();
			 busList.add(DomainObject.SELECT_ID);
			 StringList relList = new StringList();
			 relList.add(DomainRelationship.SELECT_ID);
			 MapList affectedMapList = strObject.getRelatedObjects(context,"DBC To Print Part", strPartType , busList, relList, false, true, (short)1, null, null);
			 return affectedMapList;
		 }
		 
		 /**
		  *                ----            
		  * @param context
		  * @param args
		  * @return
		  * @throws Exception
		  */
		 public MapList DBCGetToPrintSpec(Context context,String[] args)throws Exception
		 {
			 
			 HashMap hashMap = (HashMap)JPO.unpackArgs(args);
			 String objectId = (String)hashMap.get("objectId");
			 DomainObject strObject = new DomainObject(objectId);
			 String strSpecType = "DOCUMENTS";
			 StringList busList = new StringList();
			 busList.add(DomainObject.SELECT_ID);
			 StringList relList = new StringList();
			 relList.add(DomainRelationship.SELECT_ID);
			 MapList affectedMapList = strObject.getRelatedObjects(context,"DBC To Print Drawing", strSpecType , busList, relList, false, true, (short)1, null, null);
			 return affectedMapList;
			 
		 }
		 
		 /**
		  *   CO               ECO     CR     '   ECO'             ECR  '   '     
		  * @param context
		  * @param args
		  * @return
		  * @throws Exception
		  */
		 public int DBCAutoPromoteRelatedECR(Context context,String[] args)throws Exception
		 {
			 String objectId = args[0];
			 DomainObject strObj = new DomainObject(objectId);
			 StringList busList = new StringList();
			 busList.add(DomainObject.SELECT_ID);
			 MapList mapList  = strObj.getRelatedObjects(context,"ECO Change Request Input","*",busList,null,false,true,(short)1,null,null);
			 Iterator i = mapList.iterator();
			 while(i.hasNext())
			 {
				 Map map = (Map) i.next();
				 String strECRId = (String) map.get(DomainObject.SELECT_ID);
				 DomainObject strECRObj = new DomainObject(strECRId);
				 String currentState = strECRObj.getInfo(context, DomainObject.SELECT_CURRENT);
				 if(currentState.equals("Plan ECO")){
					 ContextUtil.pushContext(context);
					 strECRObj.gotoState(context, "Complete");
					 ContextUtil.popContext(context);	 
				 }else{
					 continue;
				 }
			 }
			 return 0;
		 }
		 
		 /**
		  *                                              
		  * @param context
		  * @param args
		  * @return
		  * @throws Exception
		  */
		 public String DBCDBCDepartment(Context context,String[] args) throws Exception
		 {
			String strDepartmentName ="";
			String strUserName = context.getUser();
			StringList busList = new StringList();
			busList.add(DomainObject.SELECT_ID);
			MapList personList = DomainObject.findObjects(context, "Person", strUserName, "*","*", "*", "", true, busList);
			Iterator it = personList.iterator();
			while(it.hasNext())
			{
				Map personMap = (Map)it.next();
				String personId = (String) personMap.get(DomainObject.SELECT_ID);
				DomainObject strPersonObj = new DomainObject(personId);
				MapList departmentList = strPersonObj.getRelatedObjects(context, "Member", "Business Unit", busList, null, true, false, (short)1, null, null);
				Iterator i = departmentList.iterator();
				while(i.hasNext())
				{
					Map departmentMap = (Map)i.next();
					String departmentId = (String)departmentMap.get(DomainObject.SELECT_ID);
					DomainObject strDepartmentObj = new DomainObject(departmentId);
					strDepartmentName = strDepartmentObj.getName(context);
					if(strDepartmentName != "" || strDepartmentName != null)
					{
						break;
					}
				}
			}
			return strDepartmentName;
		 }
		 
	  public MapList getEBOM(Context context,String[] args)throws Exception
	 {
		 HashMap hashMap = (HashMap)JPO.unpackArgs(args);
		 String objectId = (String)hashMap.get("objectId");
		 DomainObject strObject = new DomainObject(objectId);
		 StringList busList = new StringList();
		 busList.add(DomainObject.SELECT_ID);
		 StringList relList = new StringList();
		 relList.add(DomainRelationship.SELECT_ID);
		 MapList affectedMapList = strObject.getRelatedObjects(context, "EBOM", "*", busList, relList, false, true, (short)1, null, null);
		 
		 return affectedMapList;
		 
	 }
	 
	 
	 public String getProjectNumber (Context context,String[] args) throws Exception
	 {
		 StringBuffer strBuffer = new StringBuffer();
		 try{
			 
			 String languagestr  = context.getSession().getLanguage();
			 HashMap programMap = (HashMap)JPO.unpackArgs(args);
			 HashMap paramMap = (HashMap)programMap.get("paramMap");
			 HashMap requestMap = (HashMap)programMap.get("requestMap");
			 String objectId = (String)paramMap.get("objectId");
			 
			 strBuffer.append(
					 "<input type=\"text\" id=\"DBCProductNumber\" name=\"DBCProductNumber\" value=\"\" readonly=\"true\" onclick=\"javascript:showSelectProductNumber()\" ></input>" 
							 + " <input type=\"button\" name=\"selectProdcutNumber\" value=\"...\" onclick=\"javascript:showSelectProductNumber()\"></input>");
			 strBuffer.append("<div id=\"materialResult\"></div>");
			 strBuffer.append("<link rel=\"stylesheet\" type=\"text/css\" href=\"../engineeringcentral/DBCSelectMaterial.css\"></link>");
		 }catch(Exception e){
				System.out.println("get the eco product div exception -------------------------");
				e.printStackTrace();
		}
		 return strBuffer.toString();
	 }
		 
	public void updateProductNo(Context context,String[]args)throws Exception
	{
		try{
			
			HashMap programMap = (HashMap) JPO.unpackArgs(args);
			HashMap paramMap = (HashMap) programMap.get("paramMap");
			String objectId = (String) paramMap.get("objectId");
			DomainObject partObj = DomainObject.newInstance(context,objectId);
			String newValue = (String) paramMap.get("New Value");
			if(newValue != null && !newValue.trim().equals("") && !newValue.trim().equals("null"))
			{
				//partObj.setAttributeValue(context, "", newValue);
			}
		}catch(Exception e){
			System.out.println("update eco Product No exception-----------------------------");
			 e.printStackTrace();
		}
	}
	
	public String getPartNumber(Context context,String[]args)throws Exception
	{
		StringBuffer strBuffer = new StringBuffer();
		try{
			
			String languagestr  = context.getSession().getLanguage();
			HashMap programMap = (HashMap)JPO.unpackArgs(args);
			HashMap paramMap = (HashMap)programMap.get("paramMap");
			HashMap requestMap = (HashMap)programMap.get("requestMap");
			String objectId = (String)paramMap.get("objectId");
			
			strBuffer.append(
					"<input type=\"text\" id=\"DBCPartNumberForChange\" name=\"DBCPartNumberForChange\" value=\"\" readonly=\"true\" onclick=\"javascript:showSelectPartNumber()\" ></input>" 
							+ " <input type=\"button\" name=\"selectPartNumber\" value=\"...\" onclick=\"javascript:showSelectPartNumber()\"></input>");
			strBuffer.append("<div id=\"materialResult\"></div>");
			strBuffer.append("<link rel=\"stylesheet\" type=\"text/css\" href=\"../engineeringcentral/DBCSelectMaterial.css\"></link>");
		}catch(Exception e)
		{
			System.out.println("get the part div exception -------------------------");
			e.printStackTrace();
		}
		 return strBuffer.toString();
	}
	
	public void updatePartNo(Context context,String[]args)throws Exception
	{
		try{
			
			HashMap programMap = (HashMap) JPO.unpackArgs(args);
			HashMap paramMap = (HashMap) programMap.get("paramMap");
			String objectId = (String) paramMap.get("objectId");
			DomainObject partObj = DomainObject.newInstance(context,objectId);
			String newValue = (String) paramMap.get("New Value");
			if(newValue != null && !newValue.trim().equals("") && !newValue.trim().equals("null"))
			{
				//partObj.setAttributeValue(context, "Bypass Plants", newValue);
			}
		}catch(Exception e){
			System.out.println("updatePartNo exception-----------------------------");
			 e.printStackTrace();
		}
	}
	
	/**
	 * For ECR Create webform;
	 * @param context
	 * @param args
	 * @return
	 * @throws Exception
	 */
	public String getDBCECDocNo(Context context,String[] args)throws Exception
	{
		StringBuffer sb = new StringBuffer();
		sb.append("<input name='DBC_EC_DocNo' size='20' readonly='true'/>");
        String textbox = sb.toString();
        return textbox;
	}
	public void updateDBCECDocNo(Context context,String[]args)throws Exception
	{
		try{
			
			HashMap programMap = (HashMap) JPO.unpackArgs(args);
			HashMap paramMap = (HashMap) programMap.get("paramMap");
			String objectId = (String) paramMap.get("objectId");
			DomainObject changeObj = DomainObject.newInstance(context,objectId);
			String newValue = (String) paramMap.get("New Value");
			if(newValue != null && !newValue.trim().equals("") && !newValue.trim().equals("null"))
			{
				changeObj.setAttributeValue(context, "DBC EC DocNo", newValue);
			}
		}catch(Exception e){
			System.out.println("update updateDBCECDocNo  exception-----------------------------");
			 e.printStackTrace();
		}
	}
	public String getDBCECDocName(Context context,String[] args)throws Exception
	{
		StringBuffer sb = new StringBuffer();
        sb.append("<input name='DBC_EC_DocName' size='20'/>");
        String textbox = sb.toString();
        return textbox;
	}
	public void updateDBCECDocName(Context context,String[]args)throws Exception
	{
		try{
			
			HashMap programMap = (HashMap) JPO.unpackArgs(args);
			HashMap paramMap = (HashMap) programMap.get("paramMap");
			String objectId = (String) paramMap.get("objectId");
			DomainObject changeObj = DomainObject.newInstance(context,objectId);
			String newValue = (String) paramMap.get("New Value");
			if(newValue != null && !newValue.trim().equals("") && !newValue.trim().equals("null"))
			{
				changeObj.setAttributeValue(context, "DBC EC DocName", newValue);
			}
		}catch(Exception e){
			System.out.println("update updateDBCECDocName  exception-----------------------------");
			 e.printStackTrace();
		}
	}
	public String getDBCECDesign(Context context,String[] args)throws Exception
	{
		StringBuffer sb = new StringBuffer();
        sb.append("<input name='DBC_EC_Design' size='20' readonly='true'/>");
        String textbox = sb.toString();
        return textbox;
	}
	public void updateDBCECDesign(Context context,String[]args)throws Exception
	{
		try{
			
			HashMap programMap = (HashMap) JPO.unpackArgs(args);
			HashMap paramMap = (HashMap) programMap.get("paramMap");
			String objectId = (String) paramMap.get("objectId");
			DomainObject changeObj = DomainObject.newInstance(context,objectId);
			String newValue = (String) paramMap.get("New Value");
			if(newValue != null && !newValue.trim().equals("") && !newValue.trim().equals("null"))
			{
				changeObj.setAttributeValue(context, "DBC EC Design", newValue);
			}
		}catch(Exception e){
			System.out.println("update updateDBCECDesign  exception-----------------------------");
			 e.printStackTrace();
		}
	}
	public String getDBCECCheck(Context context,String[] args)throws Exception
	{
		StringBuffer sb = new StringBuffer();
        sb.append("<input name='DBC_EC_Check' size='20' readonly='true'/>");
        String textbox = sb.toString();
        return textbox;
	}
	public void updateDBCECCheck(Context context,String[]args)throws Exception
	{
		try{
			
			HashMap programMap = (HashMap) JPO.unpackArgs(args);
			HashMap paramMap = (HashMap) programMap.get("paramMap");
			String objectId = (String) paramMap.get("objectId");
			DomainObject changeObj = DomainObject.newInstance(context,objectId);
			String newValue = (String) paramMap.get("New Value");
			if(newValue != null && !newValue.trim().equals("") && !newValue.trim().equals("null"))
			{
				changeObj.setAttributeValue(context, "DBC EC Check", newValue);
			}
		}catch(Exception e){
			System.out.println("update updateDBCECCheck  exception-----------------------------");
			 e.printStackTrace();
		}
	}
	public String getDBCRelatedProject(Context context,String[] args)throws Exception
	{
		StringBuffer sb = new StringBuffer();
        sb.append("<input name='DBCRelatedProject' size='20'/>");
        String textbox = sb.toString();
        return textbox;
	}
	public void updateDBCRelatedProject(Context context,String[]args)throws Exception
	{
		try{
			
			HashMap programMap = (HashMap) JPO.unpackArgs(args);
			HashMap paramMap = (HashMap) programMap.get("paramMap");
			String objectId = (String) paramMap.get("objectId");
			DomainObject changeObj = DomainObject.newInstance(context,objectId);
			String newValue = (String) paramMap.get("New Value");
			if(newValue != null && !newValue.trim().equals("") && !newValue.trim().equals("null"))
			{
				changeObj.setAttributeValue(context, "DBC Related Project", newValue);
			}
		}catch(Exception e){
			System.out.println("update updateDBCRelatedProject  exception-----------------------------");
			 e.printStackTrace();
		}
	}
	
	
	  /**
	   * 
	   * @param For ECO;
	   * @param args no args needed for this method
	   * @returns String
	   * @throws Exception if the operation fails
	   * @since EC X3
	   */
	  public String  displayRelatedDBCECDocNo (Context context, String[] args) throws Exception 
	  {
	       StringBuffer strBuilder = new StringBuffer(3);
	       try{
	            HashMap programMap = (HashMap) JPO.unpackArgs(args);
	            HashMap requestMap = (HashMap) programMap.get("requestMap");
	            String strObjId = (String) requestMap.get("objectId");
	            if (strObjId != null && strObjId.length() > 0)
	            {
		            setId(strObjId);
		            DomainObject dPart = DomainObject.newInstance(context, strObjId);
				    String sPartName =  dPart.getAttributeValue(context, "DBC EC DocNo");
				    strBuilder.append("<input name='DBCECDocNo' size='20' readonly='true' value='"+sPartName+"'/>");
				}else{
					 strBuilder.append("<input name='DBCECDocNo' size='20' readonly='true'/>");
				 }
		    }catch(Exception ex){
	            throw  new FrameworkException((String)ex.getMessage());
	        }
	        return strBuilder.toString();
	    }
	  
	  public String  displayRelatedDBCECDocName (Context context, String[] args) throws Exception 
	  {
	       StringBuffer strBuilder = new StringBuffer(3);
	       try{
	            HashMap programMap = (HashMap) JPO.unpackArgs(args);
	            HashMap requestMap = (HashMap) programMap.get("requestMap");
	            String strObjId = (String) requestMap.get("objectId");
	            if (strObjId != null && strObjId.length() > 0)
	            {
		            setId(strObjId);
		            DomainObject dPart = DomainObject.newInstance(context, strObjId);
				    String sPartName =  dPart.getAttributeValue(context, "DBC EC DocName");
				    strBuilder.append("<input name='DBCECDocName' size='20' value='"+sPartName+"'/>");
				}else{
					 strBuilder.append("<input name='DBCECDocName' size='20' />");
				 }
		    }catch(Exception ex){
	            throw  new FrameworkException((String)ex.getMessage());
	        }
	        return strBuilder.toString();
	    }
	  
	  public String  displayRelatedDBCECDesign(Context context, String[] args) throws Exception 
	  {
	       StringBuffer strBuilder = new StringBuffer(3);
	       try{
	            HashMap programMap = (HashMap) JPO.unpackArgs(args);
	            HashMap requestMap = (HashMap) programMap.get("requestMap");
	            String strObjId = (String) requestMap.get("objectId");
	            if (strObjId != null && strObjId.length() > 0)
	            {
		            setId(strObjId);
		            DomainObject dPart = DomainObject.newInstance(context, strObjId);
				    String sPartName =  dPart.getAttributeValue(context, "DBC EC Design");
				    strBuilder.append("<input name='DBCECDesign' size='20' readonly='true' value='"+sPartName+"'/>");
				}else{
					 strBuilder.append("<input name='DBCECDesign' size='20' readonly='true'/>");
				 }
		    }catch(Exception ex){
	            throw  new FrameworkException((String)ex.getMessage());
	        }
	        return strBuilder.toString();
	    }
	  
	  public String  displayRelatedDBCECCheck(Context context, String[] args) throws Exception 
	  {
	       StringBuffer strBuilder = new StringBuffer(3);
	       try{
	            HashMap programMap = (HashMap) JPO.unpackArgs(args);
	            HashMap requestMap = (HashMap) programMap.get("requestMap");
	            String strObjId = (String) requestMap.get("objectId");
	            if (strObjId != null && strObjId.length() > 0)
	            {
		            setId(strObjId);
		            DomainObject dPart = DomainObject.newInstance(context, strObjId);
				    String sPartName =  dPart.getAttributeValue(context, "DBC EC Check");
				    strBuilder.append("<input name='DBCECCheck' size='20' readonly='true' value='"+sPartName+"'/>");
				 }else{
					 strBuilder.append("<input name='DBCECCheck' size='20' readonly='true'/>");
				 }
		    }catch(Exception ex){
	            throw  new FrameworkException((String)ex.getMessage());
	        }
	        return strBuilder.toString();
	    }
	  
	  public String  displayDBCRelatedProduct(Context context, String[] args) throws Exception 
	  {
	       StringBuffer strBuilder = new StringBuffer(3);
	       try{
	            HashMap programMap = (HashMap) JPO.unpackArgs(args);
	            HashMap requestMap = (HashMap) programMap.get("requestMap");
	            String strObjId = (String) requestMap.get("objectId");
	            if (strObjId != null && strObjId.length() > 0)
	            {
		            setId(strObjId);
		            DomainObject dPart = DomainObject.newInstance(context, strObjId);
				    String sPartName =  dPart.getAttributeValue(context, "DBC Related Project");
				    strBuilder.append("<input name='DBCRelatedProject' size='20' value='"+sPartName+"'/>");
				}else{
					 strBuilder.append("<input name='DBCRelatedProject' size='20' />");
				 }
		    }catch(Exception ex){
	            throw  new FrameworkException((String)ex.getMessage());
	        }
	        return strBuilder.toString();
	    }
	  
	  
	public void updateDBCECNumber(Context context,String[]args)throws Exception
	{
		
		try{
			HashMap programMap = (HashMap) JPO.unpackArgs(args);
			HashMap paramMap = (HashMap) programMap.get("paramMap");
			String objectId = (String) paramMap.get("objectId");
			DomainObject changeObj = DomainObject.newInstance(context,objectId);
			String newValue = (String) paramMap.get("New Value");
			if(newValue != null && !newValue.trim().equals("") && !newValue.trim().equals("null"))
			{
				changeObj.setAttributeValue(context, "DBC EC Number", newValue);
			}
		}catch(Exception e){
			System.out.println("update updateDBCECNumber  exception-----------------------------");
			 e.printStackTrace();
		}
	}
	
	public String getDBCECNumber(Context context,String[] args)throws Exception
	{
		StringBuffer strBuilder = new StringBuffer();
	       try{
	            HashMap programMap = (HashMap) JPO.unpackArgs(args);
	            HashMap requestMap = (HashMap) programMap.get("requestMap");
	            String strObjId = (String) requestMap.get("objectId");
	            StringList busList = new StringList(DomainObject.SELECT_ID);
	            if (strObjId != null && strObjId.length() > 0)
	            {
		            //setId(strObjId);
		            DomainObject dPart = DomainObject.newInstance(context, strObjId);
		            MapList strPartList = dPart.getRelatedObjects(context, "Reported Against Change", "*", busList, null, false, true, (short)1, "", null);
		            DomainObject strPartObj = new DomainObject();
		            for(Iterator itPart = strPartList.iterator(); itPart.hasNext();)
		            {
		            	Map partMap = (Map) itPart.next();
		            	String strPartId = (String) partMap.get(DomainObject.SELECT_ID);
		            	strPartObj.setId(strPartId);
		            }
		            
				    String sPartName =  strPartObj.getAttributeValue(context, "DBC Drawing Number");
				    String[] objectName = sPartName.split("-");
				    String strRelatedProduct = dPart.getAttributeValue(context, "DBC Related Project");
					String where = "attribute[DBC Related Project] == '"+strRelatedProduct+"'";
					MapList ECOList = DomainObject.findObjects(context, "ECO", "*", "*","*", "*", where, true, busList);
					StringList valueList = new StringList();
					String newValue ="";
					if(ECOList != null && ECOList.size()>0){
						Iterator itECO = ECOList.iterator();
						while(itECO.hasNext())
						{
							Map ecoMap = (Map) itECO.next();
							String ecoId = (String)ecoMap.get(DomainObject.SELECT_ID);
							DomainObject strChangeObj = new DomainObject(ecoId);
							String attrValue = strChangeObj.getAttributeValue(context, "DBC EC Number");
							if(attrValue != null && attrValue.length()>0){
								
								attrValue = attrValue.substring(attrValue.length()-3,attrValue.length());
								valueList.add(attrValue);
							}else{
								valueList.add("-1");
							}
						}
						for(int i=0;i<valueList.size();i++)
						{  
				            for(int j=0;j<valueList.size()-i-1;j++)
				            {  
				            	int value1 = Integer.parseInt((String)valueList.get(j));
				            	int value2 = Integer.parseInt((String)valueList.get(j+1));
				                if(value1>value2){   
				                    int tmp=value1;  
				                    value1=value2;  
				                    value2=tmp;  
				                }  
				            }  
					    } 
						int mxValue = Integer.parseInt((String)valueList.get(valueList.size()-1))+1;
						if(mxValue>0 && mxValue<10){
							
							newValue = objectName[0]+"gP00"+Integer.toString(mxValue);
						}else if(mxValue>10 && mxValue<100){
							newValue = objectName[0]+"gP0"+Integer.toString(mxValue);
						}else{
							newValue = objectName[0]+"gP001";
						}
					}else{
						newValue = objectName[0]+"gP001";
					}
					strBuilder.append("<input name='DBCECNumber' size='20' readonly='true' value='"+newValue+"'/>");
				}else{
					strBuilder.append("<input name='DBCECNumber' size='20' readonly='true'/>");
				}
		    }catch(Exception ex){
	            throw  new FrameworkException((String)ex.getMessage());
	        }
	        return strBuilder.toString();
	}
	
	
	public int checkDBCECNumberOnly(Context context,String[] args)throws Exception
	{	
		String objectValue = args[1];
		StringList busList = new StringList(DomainObject.SELECT_ID);
		busList.add(DomainObject.SELECT_NAME);
		String where = "attribute[DBC EC Number] == '"+objectValue+"'";
		MapList ECOList = DomainObject.findObjects(context, "ECO", "*", "*","*", "*", where, true, busList);
		if(ECOList != null && ECOList.size()!=1)
		{
			emxContextUtil_mxJPO.mqlNotice(context,"\u66f4\u6539\u5355\u53f7\u5df2\u5b58\u5728\uff0c\u8bf7\u91cd\u65b0\u521b\u5efa\u66f4\u6539\u5355\u3002");
			return 1;
		}
		return 0;
		
	}
	
	
	public void setReleaseTime(Context context,String[] args)throws Exception
	{
		Date date = new Date();
        java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat(eMatrixDateFormat.getEMatrixDateFormat(), Locale.US);
        String strDate = formatter.format(date);
        String objectId = args[0];
        DomainObject strObject = DomainObject.newInstance(context,objectId);
		if(strObject.isKindOf(context,"ProE Drawing") || strObject.isKindOf(context, "ACAD Drawing")
				|| strObject.isKindOf(context, "DBC Non-standard Part") || strObject.isKindOf(context, "DBC Standard Part")
				|| strObject.isKindOf(context, "DBC General Part") || strObject.isKindOf(context, "DBC Top Assembly"))
		{
			strObject.setAttributeValue(context, "DBC Release Date", strDate);
		}
	}
	
	
	public String getReportedAgainst(Context context,String[] args)throws Exception
	{
		String strName = "";
		try{
			HashMap programMap = (HashMap) JPO.unpackArgs(args);
            HashMap requestMap = (HashMap) programMap.get("requestMap");
            StringList busList = new StringList();
            busList.add(DomainObject.SELECT_ID);
            String strObjId = (String) requestMap.get("objectId");
            DomainObject strObj = new DomainObject(strObjId);
            MapList mapList = strObj.getRelatedObjects(context, "Reported Against Change", "*", busList, null, false, true, (short)1, null, null);
            if(mapList !=null && mapList.size()>0){
            	
            	Iterator it  = mapList.iterator();
            	while(it.hasNext())
            	{
            		Map map = (Map)it.next();
            		String strObjectId = (String)map.get(DomainObject.SELECT_ID);
            		DomainObject strObject = new DomainObject(strObjectId);
            		strName = strObject.getName(context);
            	}
            }
            
		}catch(Exception e){
			e.printStackTrace();
		}
		return strName;
	}
	
	
	
	
	public Vector getSpecificDescriptionofChange(Context context,String[] args)throws Exception
	{
		Vector vector = new Vector();
		try{
			HashMap programMap = (HashMap) JPO.unpackArgs(args);
            MapList objectList = (MapList)programMap.get("objectList");
            StringList busList = new StringList();
            busList.add(DomainObject.SELECT_ID);
            StringList relList = new StringList();
            relList.add(DomainRelationship.SELECT_ID);
            Iterator i = objectList.iterator();
            while(i.hasNext()){
            	Map idMap = (Map)i.next();
	            String strObjId = (String) idMap.get("id[connection]");
	            if(strObjId !=null && strObjId !=""){
	            	DomainRelationship strRel = new DomainRelationship(strObjId);
	            	String value = strRel.getAttributeValue(context, "Specific Description of Change");
	            	vector.add(value);
	            }else{
	            	vector.add("");
	            }
            }
		}catch(Exception e){
			
			e.printStackTrace();
			
		}
		return vector;
	}
	
	
	public void updateSpecificDescriptionofChange(Context context,String[]args)throws Exception
	{
		try{
			
			HashMap programMap = (HashMap) JPO.unpackArgs(args);
			HashMap paramMap = (HashMap) programMap.get("paramMap");
			String objectId = (String) paramMap.get("relId");
			DomainRelationship relObj = DomainRelationship.newInstance(context,objectId);
			String newValue = (String) paramMap.get("New Value");
			if(newValue != null && !newValue.trim().equals("") && !newValue.trim().equals("null"))
			{
				relObj.setAttributeValue(context, "Specific Description of Change", newValue);
			}
		}catch(Exception e){
			System.out.println("updateSpecificDescriptionofChange  exception-----------------------------");
			 e.printStackTrace();
		}
	}
	
	//add by ryan 2014-04-15 for check reported against part
	public int checkReportedAgainstPart(Context context,String[]args)throws Exception
	{
		try
		{
			String strObjectId = args[0];
			DomainObject ecoObj = DomainObject.newInstance(context, strObjectId);
			StringList objSelect = new StringList();
			objSelect.addElement("id");
			MapList reportedAgainstItemList = ecoObj.getRelatedObjects(context, // context.
					"Reported Against Change", // relationship pattern
					"Part", // type filter.
					objSelect, // business object selectables.
					null, // relationship selectables.
					false, // expand to direction.
					true, // expand from direction.
					(short) 1, // level
					"", // object where clause
					null, 0);
			
			if(reportedAgainstItemList.size() <= 0)
			{
				
		
				throw new Exception(ecoObj.getName(context) + "\u88AB\u9519\u8BEF\u521B\u5EFA\uFF0C\u521B\u5EFA\u65F6\u6CA1\u6709\u6307\u5B9A\u53D1\u5E03(\u6C38\u6539)\u96F6\u90E8\u4EF6\uFF0C\u8BF7\u91CD\u65B0\u521B\u5EFA");
			}
			return 0;
		} catch(Exception e){
			emxContextUtil_mxJPO.mqlError(context, e.getMessage());
			return 1;
		}
	}
	//add end 2014-04-15
	
	
	
	
	
	
	 public Vector DBCGetPartFromChange(Context context,String[] args) throws Exception
	    {
	    	try {
	    		Vector vecResult = new Vector();
	           
	            // Get object list information from packed arguments
	    		HashMap programMap = (HashMap) JPO.unpackArgs(args);
	            MapList objectList = (MapList)programMap.get("objectList");
	            Map paramList = (Map)programMap.get("paramList");

	            DomainObject dmoObject = DomainObject.newInstance(context);

	            StringList objectSelects = new StringList();
	            objectSelects.add(DomainObject.SELECT_ID);
	            objectSelects.add(DomainObject.SELECT_DESCRIPTION);

	            MapList mapList = new MapList();
	            Map mapObjectInfo = null;
	            String strObjId = null;
	            boolean GET_TO = true;
	            boolean GET_FROM = false;
	            String strObjType = "";

	            for (Iterator itrObjects = objectList.iterator(); itrObjects.hasNext();) 
	            {
	                mapObjectInfo = (Map) itrObjects.next();
	               	strObjId = (String)mapObjectInfo.get("id");
	               	DomainObject strChangeObj = new DomainObject(strObjId);
	               	String strType = strChangeObj.getType(context);
	                String retDescription = "";
	                if(strObjId != null && !strObjId.trim().equals("") && !strObjId.equals("null"))
	                {
		                 dmoObject.setId(strObjId);
		                 
	                	 if(strType.equals("ECO"))
	                	 {
	                		MapList partList = strChangeObj.getRelatedObjects(context, "Reported Against Change", "*", objectSelects, null, false, true, (short)1, null, null);
	                		 if(partList != null && partList.size()>0)
		                	 {
		                		 Iterator it = partList.iterator();
		                		 while(it.hasNext())
		                		 {
		                			 Map partMap =(Map) it.next();
		                			 String strPartId = (String)partMap.get(DomainObject.SELECT_ID);
		                			 DomainObject strPart = new DomainObject(strPartId);
	                				 String partName = strPart.getName(context);
		                			 vecResult.add(partName);
		                			 break;
		                		 }
		                	 }else{
		                		 vecResult.add(retDescription);
		                	 }
	                	 }else if(strType.equals("DBC ERO")){
	                		MapList partEROList = strChangeObj.getRelatedObjects(context, "Reported Against Change", "*", objectSelects, null, false, true, (short)1, null, null);
	                		if(partEROList != null && partEROList.size()>0)
		                	 {
		                		 Iterator i = partEROList.iterator();
		                		 while(i.hasNext())
		                		 {
		                			 Map partMap =(Map) i.next();
		                			 String strPartId = (String)partMap.get(DomainObject.SELECT_ID);
		                			 DomainObject strPart = new DomainObject(strPartId);
                					 String partName = strPart.getName(context);
		                			 vecResult.add(partName);
		                			 break;
		                		 }
		                	 }else{
		                		 vecResult.add(retDescription);
		                	 }
	                	 }else if(strType.equals("DBC MCO") || strType.equals("DBC MRO")){
	                		 String value = strChangeObj.getAttributeValue(context, "DBC Assembly Drawing No");
	                		 vecResult.add(value);
	                	 }else{
	                		 vecResult.add(retDescription);
	                	 }
		                	 
		                	
	                }     
	                
	            }//for

	            return vecResult;
	        }
	        catch(Exception exp) {
	            exp.printStackTrace();
	            throw exp;
	        }
	    }
	 
	 
	 public String  displayRelatedDBCChangeRevision(Context context, String[] args) throws Exception 
	  {
	       StringBuffer strBuilder = new StringBuffer(3);
	       try{
	            HashMap programMap = (HashMap) JPO.unpackArgs(args);
	            HashMap requestMap = (HashMap) programMap.get("requestMap");
	            String strObjId = (String) requestMap.get("objectId");
	            if (strObjId != null && strObjId.length() > 0)
	            {
		            setId(strObjId);
		            DomainObject dPart = DomainObject.newInstance(context, strObjId);
				    /*String sPartName =  dPart.getAttributeValue(context, "DBC EC DocNo");*/
		            String strValue = "";
		            String partCurrent = dPart.getInfo(context, DomainObject.SELECT_CURRENT);
		            if(partCurrent.equals("Preliminary"))
		            {
		            	strValue = "NO";
		            }else if(partCurrent.equals("Release")){
		            	strValue = "YES"; 
		            }
		            String strNextValue = "";
		            if(strValue.equals("NO"))
		            {
		            	strNextValue = "YES";
		            }else{
		            	strNextValue = "NO";
		            }
		           /* System.out.println("strValue========="+strValue);*/
				    //strBuilder.append("<select name='DBCChangeRevision' ><option value='"+strValue+"' selected='true'>"+strValue+"</option><option value='"+strNextValue+"'>"+strNextValue+"</option></select>");
				}else{
				    strBuilder.append("<select name='DBCChangeRevision' id='DBCChangeRevision' ><option value='YES'>\u662f</option><option value='NO'>\u5426</option></select>");
				 }
		    }catch(Exception ex){
	            throw  new FrameworkException((String)ex.getMessage());
	        }
	        return strBuilder.toString();
	    }
	 
	 public void updateDBCChangeRevision(Context context,String[]args)throws Exception
	 {
			try{
				
				HashMap programMap = (HashMap) JPO.unpackArgs(args);
				HashMap paramMap = (HashMap) programMap.get("paramMap");
				String objectId = (String) paramMap.get("objectId");
				DomainObject changeObj = DomainObject.newInstance(context,objectId);
				String newValue = (String) paramMap.get("New Value");
				if(newValue.equals("\u5426"))
				{
					newValue = "NO";
				}else if(newValue.equals("\u662f")){
					newValue = "YES";
				}
				if(newValue != null && !newValue.trim().equals("") && !newValue.trim().equals("null"))
				{
					changeObj.setAttributeValue(context, "DBC Change Revision", newValue);
				}
			}catch(Exception e){
				System.out.println("update DBC Change Revision  exception-----------------------------");
				 e.printStackTrace();
			}
	 }
	 
	 
	 public int checkPartDrawingNumber(Context context,String[] args) throws Exception
	 {
		 String objectId = args[0];
		 DomainObject strObj = new DomainObject(objectId);
		 StringList busList = new StringList(DomainObject.SELECT_ID);
		 MapList mapList = strObj.getRelatedObjects(context, "Affected Item", "Part", busList, null, false, true, (short)1, null, null);
		 Iterator it = mapList.iterator();
		 String si18NWuTu = FrameworkProperties.getProperty("DBC.emxFrameworkDBC.ValidateCraetePart.WuTu");
		 while(it.hasNext())
		 {
			 Map map = (Map)it.next();
			 String partId = (String)map.get(DomainObject.SELECT_ID);
			 DomainObject partObj = new DomainObject(partId);
			 String strType = partObj.getType(context);
			 String strName = partObj.getName(context);
			 String strDrawingNumber = partObj.getAttributeValue(context, "DBC Drawing Number").trim();
			 String strDrawingType = partObj.getAttributeValue(context, "DBC Drawing Type");
			 if(strType.equals("DBC Top Assembly"))
			 {
				 continue;
			 }
			 if(si18NWuTu.equals(strDrawingNumber))
			 {
				if(!"WuTu".equals(strDrawingType) || !strType.trim().equalsIgnoreCase("DBC Non-standard Part"))
				{
					
					emxContextUtil_mxJPO.mqlNotice(context,
							strName + EngineeringUtil.i18nStringNow("DBC.emxEngineeringCentral.ValidateCreatePart.NoStandardDrawingNumber",
									context.getSession().getLanguage()));
					return 1;
				}
			 }
			
			if("WuTu".equals(strDrawingType))
			{
				if(!si18NWuTu.equals(strDrawingNumber) || !strType.trim().equalsIgnoreCase("DBC Non-standard Part"))
				{
					
					emxContextUtil_mxJPO.mqlNotice(context,
						strName + EngineeringUtil.i18nStringNow("DBC.emxEngineeringCentral.ValidateCreatePart.NoStandardDrawingType",
								context.getSession().getLanguage()));
					return 1;
				}
			}
			
			if(strDrawingNumber.startsWith("T") && !strType.trim().equalsIgnoreCase("DBC General Part"))
			{
				emxContextUtil_mxJPO.mqlNotice(context,
						strName + EngineeringUtil.i18nStringNow("DBC.emxEngineeringCentral.ValidateCreatePart.StartsWithT",
								context.getSession().getLanguage()));
				return 1;
			}
			
			if((strDrawingNumber.startsWith("DG") 
				|| strDrawingNumber.startsWith("GB")
				|| strDrawingNumber.startsWith("ASME")
				|| strDrawingNumber.startsWith("HG")) 
				&& !strType.trim().equalsIgnoreCase("DBC Standard Part"))
			{
				
				emxContextUtil_mxJPO.mqlNotice(context,
						strName + EngineeringUtil.i18nStringNow("DBC.emxEngineeringCentral.ValidateCreatePart.StartsWithDGGB",
								context.getSession().getLanguage()));
				return 1;
			}
		 }
		 return 0;
	 }

	public Map getCategoryofChangeRange(Context context, String[] args) throws Exception 
	{
		Map map = new HashMap();
		try 
		{
			Map argMap = (Map) JPO.unpackArgs(args);
			Map requestMap = (Map)argMap.get("requestMap");
			String strObjectId = (String)requestMap.get("objectId");
			String strPropertyKey = "MCO";
			if(UIUtil.isNotNullAndNotEmpty(strObjectId))
			{
				DomainObject dobj = DomainObject.newInstance(context, strObjectId);
				if(dobj.isKindOf(context, "ECO"))
				{
					strPropertyKey = "ECO";
				}
			}
			else
			{
				String strCreateType = (String)requestMap.get("type");
				if(strCreateType.equals("type_ECO"))
				{
					strPropertyKey = "ECO";
				}
			}
			strPropertyKey = "DBC." + strPropertyKey + ".CategoryofChange.Range";
			String strRangeValues = getValueByLSProperty(context, strPropertyKey);
			String[] strRangeValueArray = strRangeValues.split(",");
			StringList rangeList = new StringList(strRangeValueArray);
			map.put("field_choices", new StringList(rangeList));
			map.put("field_display_choices", new StringList(rangeList));
		} catch (Exception ex) 
		{
			// TODO: handle exception
			m_logger.error(ex.getMessage(), ex);
			throw ex;
		}
		return map;
	}
	
	public String getValueByLSProperty(Context context, String strIndex1)
			throws Exception {
		String strValue = "";
		try {
			StringList busSelects = new StringList("attribute[LS Attribute1]");
			String strWhereClause = "attribute[LS Index Key1] == \""
					+ strIndex1 + "\"";
			MapList propertyList = DomainObject.findObjects(context,
					"LS Property Key", "*", strWhereClause, busSelects);
			if (propertyList.size() > 0) {
				if (propertyList.size() > 1) {
					m_logger.warn("key \"" + strIndex1 + "\" is defined multi!");
				}
				strValue = (String) ((Map) propertyList.get(0))
						.get("attribute[LS Attribute1]");
			} else {
				m_logger.warn("key \"" + strIndex1 + "\" is not defined!");
			}
		} catch (Exception ex) {
			m_logger.error(ex.getMessage(), ex);
			throw ex;
		}

		return strValue;
	}
	 
}
