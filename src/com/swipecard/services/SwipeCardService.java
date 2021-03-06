package com.swipecard.services;

import java.awt.Color;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.swing.JOptionPane;

import org.apache.ibatis.exceptions.ExceptionFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.session.SqlSession;
import org.apache.log4j.Logger;

import com.swipecard.SwipeCardNoDB;
import com.swipecard.model.EmpShiftInfos;
import com.swipecard.model.Employee;
import com.swipecard.model.RCLine;
import com.swipecard.model.RawRecord;
import com.swipecard.model.SwingBase;
import com.swipecard.model.SwipeCardTimeInfos;
import com.swipecard.model.WorkedOneWeek;
import com.swipecard.util.FormatDateUtil;

public class SwipeCardService {
	/* *
	 * Date:2017/11/13
	 * Button Ong
	 * 將刷卡邏輯集合在此Service中，降低SwipeCard的耦合力
	 * */
	private static Logger logger=Logger.getLogger(SwipeCardService.class);
	public WorkedOneWeek isUserContinuesWorkedOneWeek(SqlSession session, Employee eif, String cardID, String WorkshopNo,
			Date swipeCardTime) {
		WorkedOneWeek workedOneWeek=new WorkedOneWeek();
		try {
			// HashMap<String,Object>
			// workDays=session.selectOne("getContinuesWorker",CardID);
			// System.out.println("員工工作日:"+(long)workDays.get("work_count_week"));
			// if((long)workDays.get("work_count_week")<6)

			// 今天之前五天的记录，即:大于(new Date()-6 ) 且小于new Date()
			// int workDays=session.selectOne("getContinuesWorker",CardID);
			String Id = eif.getId();
			int workDays = 0;
			EmpShiftInfos yesShiftUser = new EmpShiftInfos();
			yesShiftUser.setId(Id);
			yesShiftUser.setShiftDay(1);

			EmpShiftInfos curShiftUser = new EmpShiftInfos();
			curShiftUser.setId(Id);
			curShiftUser.setShiftDay(0);

			EmpShiftInfos sixDayWorkerUser = new EmpShiftInfos();
			sixDayWorkerUser.setId(Id);
			sixDayWorkerUser.setShiftDay(7);

			workDays = session.selectOne("getOneWeekWorkDays", sixDayWorkerUser);

			int empYesShiftCount = session.selectOne("getShiftCount", yesShiftUser);
			int empCurShiftCount = session.selectOne("getShiftCount", curShiftUser);

			if (empYesShiftCount > 0) {

				EmpShiftInfos empYesUSer = (EmpShiftInfos) session.selectOne("getShiftByEmpId", yesShiftUser);
				String empYesShift = empYesUSer.getShift();
				if (empYesShift.equals("N")) {
					Date SwipeCardTime2 = swipeCardTime;
					SwipeCardTimeInfos userNSwipe = new SwipeCardTimeInfos();
					userNSwipe.setSwipeCardTime2(SwipeCardTime2);
					userNSwipe.setEMP_ID(Id);
					userNSwipe.setShift(empYesShift);
					userNSwipe.setWorkshopNo(WorkshopNo);
					int goWorkNCardCount = session.selectOne("selectGoWorkNByCardID", userNSwipe);// 昨日夜班上刷记录

					int yesterdaygoWorkCardCount = session.selectOne("selectCountNByCardID", userNSwipe);// 昨日夜班下刷记录

					Timestamp yesClassStart = empYesUSer.getClass_start();
					Timestamp yesClassEnd = empYesUSer.getClass_end();
					Timestamp goWorkSwipeTime = new Timestamp(new Date().getTime());

					Calendar outWorkc = Calendar.getInstance();
					outWorkc.setTime(yesClassEnd);
					outWorkc.set(Calendar.HOUR_OF_DAY, outWorkc.get(Calendar.HOUR_OF_DAY) + 3);
					outWorkc.set(Calendar.MINUTE, outWorkc.get(Calendar.MINUTE) + 30);
					Date dt = outWorkc.getTime();
					Timestamp afterClassEnd = new Timestamp(dt.getTime());

					if (goWorkNCardCount > 0) {
						// 昨日夜班已存在上刷
						if (yesterdaygoWorkCardCount == 0) { // 夜班下刷刷卡記錄不存在

							if (goWorkSwipeTime.before(afterClassEnd)) {
								// 刷卡在夜班下班3.5小時之內,記為昨日夜班下刷
								workDays = workDays - 1;
							}

						} else {

							int isOutWorkSwipeDuplicate = session.selectOne("isOutWorkSwipeDuplicate", userNSwipe);
							if (isOutWorkSwipeDuplicate > 0) {
								workedOneWeek.setSwingBase(outWorkSwipeDuplicate(session, eif, swipeCardTime, empYesShift));
								workDays = -1;
							}
						}
					} else { // 昨日夜班不存在上刷
						if (empCurShiftCount > 0) {
							EmpShiftInfos curYesUSer = (EmpShiftInfos) session.selectOne("getShiftByEmpId",
									curShiftUser);
							String empCurShift = curYesUSer.getShift();
							if (empCurShift.equals("N")) {
								if (goWorkSwipeTime.getHours() <= 12) {

									// 刷卡在12點前的,記為昨日夜班下刷
									int twoDayBeforworkDays = session.selectOne("getTwoDayBeforWorkDays",sixDayWorkerUser);
									if (twoDayBeforworkDays < 6) {
										int outWorkNCardCount = session.selectOne("selectOutWorkByCardID", userNSwipe);// 夜班昨天无上刷，今天有下刷

										/*
										 * if (outWorkNCardCount > 0) { workDays
										 * = workDays + 1; }
										 */
										int isOutWorkSwipeDuplicate = session.selectOne("isOutWorkSwipeDuplicate",
												userNSwipe);
										if (isOutWorkSwipeDuplicate > 0) {
											workedOneWeek.setSwingBase(outWorkSwipeDuplicate(session, eif, swipeCardTime, empYesShift));
											workDays = -1;
										}
									} else {
										workDays = twoDayBeforworkDays;
									}
								}
							}
						}
					}
				}
			}
			System.out.println("員工工作日:" + workDays);
			if (workDays < 6)
				workedOneWeek.setWorkedOneWeek(false);
			else
				workedOneWeek.setWorkedOneWeek(true);
		} catch (Exception ex) {
			SwipeCardNoDB d = new SwipeCardNoDB(WorkshopNo);
			ex.printStackTrace();
		}
		return workedOneWeek;
	}

	public SwingBase outWorkSwipeDuplicate(SqlSession session, Employee eif, Date swipeCardTime, String curShift) {
		String name = eif.getName();
		String Id = eif.getId();
		String CardId = eif.getCardID();	
		SwingBase fieldSetting=new SwingBase();
		fieldSetting.setFieldColor(Color.WHITE);
		fieldSetting.setFieldContent("ID: " + Id + " Name: " + name + "\n" + "下班重複刷卡！\n\n");
		RawRecord swipeRecord=new RawRecord();
		swipeRecord.setCardID(CardId);
		swipeRecord.setId(Id);
		swipeRecord.setSwipeCardTime(swipeCardTime);
		swipeRecord.setRecord_Status("5");
		session.update("updateRawRecordStatus",swipeRecord);
		session.commit();		
		
		return fieldSetting;
	}
	
	/*當員工刷卡時，立即記錄一筆刷卡資料至raw_record table中*/
	public void addRawSwipeRecord(SqlSession session, Employee eif, String CardID,Date SwipeCardTime,String WorkshopNo) {
		String Id=null;
		try {
			if(eif!=null)
				Id=eif.getId();
			if(Id==null){
				Id="";
			}
			RawRecord swipeRecord=new RawRecord();
			swipeRecord.setCardID(CardID);
			swipeRecord.setId(Id);
			swipeRecord.setSwipeCardTime(SwipeCardTime);
			session.insert("addRawSwipeRecord", swipeRecord);
			session.commit();
		}
		catch(Exception ex) {
			SwipeCardNoDB d = new SwipeCardNoDB(WorkshopNo);
			ex.printStackTrace();
		}
	}
	
	public String[] showIDDialog(){
		String[] aArray = new String[2];
		String inputID = JOptionPane.showInputDialog("Please input a Id");
		String inputName = null;
		aArray[0] = inputID;

		if (inputID == null) {

			return null;
		} else if (inputID.isEmpty()) {
			showIDDialog();
		} else {
			inputName = showNameDialog();
			aArray[1] = inputName;
		}
		// return aArray;
		return aArray;
	}
	
	public String showNameDialog() {
		String inputName = JOptionPane.showInputDialog("Please input a Name");
		if (inputName == null) {
			return null;
		}
		if (inputName.isEmpty()) {
			showNameDialog();
		}
		return inputName;
	}
	
	/*回傳生產指示單號*/
	public List<String> getRcLine(SqlSession session){
		List<RCLine> rclines;
		List<String> RCNOs=null;
		try {
			rclines = session.selectList("selectRCNo");
			if(rclines.size()>0) {
				RCNOs=new ArrayList<String>();
				Iterator<RCLine> iterator=rclines.iterator();
				while(iterator.hasNext()) {
					RCLine rcline=iterator.next();
					RCNOs.add(rcline.getRC_NO());
				}
			}
		}
		catch(Exception ex) {
			System.out.println("Error opening session");
			SwipeCardNoDB d = new SwipeCardNoDB(null);
			throw ExceptionFactory.wrapException("Error opening session.  Cause: " + ex, ex);
		}
		finally {
			ErrorContext.instance().reset();
			if (session != null) {
				session.close();
			}
		}
		return RCNOs;
	}
	
	/*原outWorkSwipeDuplicate*/
	public SwingBase offDutySwipeDuplicate(SqlSession session, Employee employee, Date swipeCardTime, String curShift) {
		SwingBase fieldSetting=new SwingBase();
		try {
			fieldSetting.setFieldColor(Color.WHITE);
			fieldSetting.setFieldContent("ID: " + employee.getId() + " Name: " + employee.getName() + "\n" + "下班重複刷卡！\n\n");
			RawRecord swipeRecord=new RawRecord();
			swipeRecord.setCardID(employee.getCardID());
			swipeRecord.setId(employee.getId());
			swipeRecord.setSwipeCardTime(swipeCardTime);
			swipeRecord.setRecord_Status("5");
			session.update("updateRawRecordStatus",swipeRecord);
			session.commit();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		return fieldSetting;
	}
	
	/*原goWorkSwipeDuplicate*/
	public SwingBase onDutySwipeDuplicate(SqlSession session,Employee employee, Date swipeCardTime, String curShift) {
		SwingBase fieldSetting=new SwingBase();
		try {
			fieldSetting.setFieldColor(Color.WHITE);
			fieldSetting.setFieldContent("ID: " + employee.getId() + " Name: " + employee.getName() + "\n" + "上班重複刷卡！\n\n");
			RawRecord swipeRecord=new RawRecord();
			swipeRecord.setCardID(employee.getCardID());
			swipeRecord.setId(employee.getId());
			swipeRecord.setSwipeCardTime(swipeCardTime);
			swipeRecord.setRecord_Status("5");
			session.update("updateRawRecordStatus",swipeRecord);
			session.commit();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		return fieldSetting;
	}
	
	/*原outWorkNSwipeCard*/
	public SwingBase offDutyNightShiftSwipeCard(SqlSession session,String RCNO,String PrimaryItemNo,String workShop,Employee employee, Date swipeCardTime, EmpShiftInfos empYesShift) {
		SwipeCardTimeInfos userNSwipe = new SwipeCardTimeInfos();
		Date SwipeCardTime2 = swipeCardTime;
		SwingBase fieldSetting=new SwingBase();
		try {
			userNSwipe.setEMP_ID(employee.getId());
			userNSwipe.setSWIPE_DATE(FormatDateUtil.getYesterdayDate());
			userNSwipe.setSwipeCardTime2(SwipeCardTime2);
			userNSwipe.setRC_NO(RCNO);
			userNSwipe.setPRIMARY_ITEM_NO(PrimaryItemNo);
			userNSwipe.setShift(empYesShift.getShift());
			userNSwipe.setCLASS_NO(empYesShift.getClass_no());
			userNSwipe.setWorkshopNo(workShop);
			
			int yesterdatOnDutyCardCount=session.selectOne("selectCountNByCardID",userNSwipe);
			
			int isOffDutySwipeDuplicate=session.selectOne("isOutWorkSwipeDuplicate", userNSwipe);
			
			if(yesterdatOnDutyCardCount>0) {
				//已有上/下刷紀錄
				if(isOffDutySwipeDuplicate>0) {
					fieldSetting=offDutySwipeDuplicate(session, employee, swipeCardTime,empYesShift.getShift());
				}
				else{
					fieldSetting.setFieldColor(Color.RED);
					fieldSetting.setFieldContent("ID: " + employee.getId() + " Name: "
						+ employee.getName() + "\n" + "今日上下班卡已刷，此次刷卡無效！\n\n");
					RawRecord swipeRecord=new RawRecord();
					swipeRecord.setCardID(employee.getCardID());
					swipeRecord.setId(employee.getId());
					swipeRecord.setSwipeCardTime(swipeCardTime);
					swipeRecord.setRecord_Status("6");
					session.update("updateRawRecordStatus",swipeRecord);
					session.commit();
				}
			}
			else {
				//昨日上班卡有刷，今日下班卡沒刷 or 昨日上班卡沒刷，今日下班卡也沒刷
				int onDutyNightShiftCardCount= session.selectOne("selectGoWorkNByCardID", userNSwipe);//取得該員工昨日到今日有上刷的筆數(有上刷)
				if (onDutyNightShiftCardCount==0) {
					//昨天無上刷
					if(isOffDutySwipeDuplicate >0) {
						//Has a off duty swipe record between 10 mins before and now,hadnle in swipe duplicate.
						fieldSetting=offDutySwipeDuplicate(session, employee, swipeCardTime,empYesShift.getShift());
					}
					else {
						//No off duty swipe record between 10 mins before and now
						int offDutyNightCardCount=session
								.selectOne("selectOutWorkByCardID", userNSwipe);//從今天至明天該員工的刷卡記錄（無上刷，有下刷）
						if(offDutyNightCardCount==0) {
							//無上刷也無下刷
							fieldSetting.setFieldColor(Color.WHITE);
							fieldSetting.setFieldContent("下班刷卡\n" + "ID: " + employee.getId()
								+ "\nName: " + employee.getName() + "\n刷卡時間： "
								+ FormatDateUtil.changeTimeToStr(swipeCardTime) + "\n"
								+"\n昨日班別為:"+empYesShift.getClass_desc()
								+ "\n員工下班刷卡成功！\n------------\n");
						}
						else {
							fieldSetting.setFieldColor(Color.RED);
							fieldSetting.setFieldContent("ID: " + employee.getId() + " Name: "
									+ employee.getName() + "\n"
									+ "今日上下班卡已刷，此次刷卡無效！\n\n");
							
							RawRecord swipeRecord=new RawRecord();
							swipeRecord.setCardID(employee.getCardID());
							swipeRecord.setId(employee.getId());
							swipeRecord.setSwipeCardTime(swipeCardTime);
							swipeRecord.setRecord_Status("6");
							session.update("updateRawRecordStatus",swipeRecord);
							session.commit();
						}
					}
				}
				else {
					//昨天有上刷
					fieldSetting.setFieldColor(Color.WHITE);
					fieldSetting.setFieldContent("下班刷卡\n" + "ID: " + employee.getId() + "\nName: "
								+ employee.getName() + "\n刷卡時間： " + FormatDateUtil.changeTimeToStr(swipeCardTime)
								+"\n昨日班別為:"+empYesShift.getClass_desc()
								+ "\n" + "員工下班刷卡成功！\n------------\n");
					session.update("updateOutWorkNSwipeTime",userNSwipe);
					session.commit();
				}
			}
		}
		catch(Exception ex) {
			ex.printStackTrace();
		}
		return fieldSetting;
	}

	/*原outWorkSwipeCard*/
	public SwingBase offDutyDaySwipeCard(SqlSession session, Employee employee,String RCNO,String PrimaryItemNo,String workShop, Date swipeCardTime,EmpShiftInfos empCurShift) {
		SwingBase fieldSetting=new SwingBase(); 
		try {
			String curDate=FormatDateUtil.getCurDate();
			String swipeCardTimeStr = FormatDateUtil.changeTimeToStr(swipeCardTime);
			Timestamp swipeTime = new java.sql.Timestamp(swipeCardTime.getTime());
			
			long diffMinutes=(empCurShift.getClass_start().getTime() - swipeTime.getTime())/(60*1000);
			
			if(diffMinutes>14) {
				//超過15分鐘
				fieldSetting.setFieldColor(Color.RED);
				fieldSetting.setFieldContent("上班刷卡\n" + "ID: " + employee.getId() + "\nName: " + employee.getName() + "\n班別： " 
				+ empCurShift.getClass_desc()
						+ "\n刷卡時間： " + swipeCardTimeStr + "\n" + "超出上班刷卡時間限制，請於上班前15分鐘刷卡！\n------------\n");
				
				RawRecord swipeRecord=new RawRecord();
				swipeRecord.setCardID(employee.getCardID());
				swipeRecord.setId(employee.getId());
				swipeRecord.setSwipeCardTime(swipeCardTime);
				swipeRecord.setRecord_Status("3");
				session.update("updateRawRecordStatus",swipeRecord);
				session.commit();
			}
			else {
				fieldSetting.setFieldColor(Color.WHITE);
				fieldSetting.setFieldContent("上班刷卡\n" + "ID: " + employee.getId() + "\nName: " + employee.getName() + "\n班別： " + empCurShift.getClass_desc()
						+ "\n刷卡時間： " + swipeCardTimeStr + "\n" + "員工上班刷卡成功！\n------------\n");
				
				SwipeCardTimeInfos userSwipe = new SwipeCardTimeInfos();
				userSwipe.setEMP_ID(employee.getId());
				userSwipe.setSWIPE_DATE(curDate);
				userSwipe.setSwipeCardTime(swipeCardTime);
				userSwipe.setRC_NO(RCNO);
				userSwipe.setPRIMARY_ITEM_NO(PrimaryItemNo);
				userSwipe.setWorkshopNo(workShop);
				userSwipe.setShift(empCurShift.getShift());
				userSwipe.setCLASS_NO(empCurShift.getClass_no());
				session.insert("insertUserByOnDNShift", userSwipe);
				session.commit();
			}
		}
		catch(Exception ex) {
			ex.printStackTrace();
			logger.error("offDutyDaySwipeCard exception",ex);
		}
		return fieldSetting;
	}

	/*原goWorkSwipeCard*/
	public SwingBase onDutyDaySwipeCard(SqlSession session, Employee employee, Date swipeCardTime,EmpShiftInfos empCurShift,String RCNO,String PrimaryItemNo,String workShopNo) {
		SwingBase fieldSetting=new SwingBase();
		try {
			String curDate=FormatDateUtil.getCurDate();
			String swipeCardTimeStr = FormatDateUtil.changeTimeToStr(swipeCardTime);
			Timestamp swipeTime = new java.sql.Timestamp(swipeCardTime.getTime());
			long diffMinutes=(empCurShift.getClass_start().getTime() - swipeTime.getTime())/(60*1000);
			
			if(diffMinutes>14) {
				fieldSetting.setFieldColor(Color.RED);
				fieldSetting.setFieldContent("上班刷卡\n" + "ID: " + employee.getId() + "\nName: " + employee.getName() + "\n班別： " 
				+ empCurShift.getClass_desc()
						+ "\n刷卡時間： " + swipeCardTimeStr + "\n" + "超出上班刷卡時間限制，請於上班前15分鐘刷卡！\n------------\n");
				
				RawRecord swipeRecord=new RawRecord();
				swipeRecord.setCardID(employee.getCardID());
				swipeRecord.setId(employee.getId());
				swipeRecord.setSwipeCardTime(swipeCardTime);
				swipeRecord.setRecord_Status("3");
				session.update("updateRawRecordStatus",swipeRecord);
				session.commit();
			}
			else {
				//上刷時間介於班別15分鐘至班別起始時間，則進行記錄
				fieldSetting.setFieldColor(Color.WHITE);
				fieldSetting.setFieldContent("上班刷卡\n" + "ID: " + employee.getId() + "\nName: " + employee.getName() + "\n班別： " 
				+ empCurShift.getClass_desc()
						+ "\n刷卡時間： " + swipeCardTimeStr + "\n" + "員工上班刷卡成功！\n------------\n");
				
				SwipeCardTimeInfos userSwipe = new SwipeCardTimeInfos();
				userSwipe.setEMP_ID(employee.getId());
				userSwipe.setSWIPE_DATE(curDate);
				userSwipe.setSwipeCardTime(swipeCardTime);
				userSwipe.setRC_NO(RCNO);
				userSwipe.setPRIMARY_ITEM_NO(PrimaryItemNo);
				userSwipe.setWorkshopNo(workShopNo);
				userSwipe.setShift(empCurShift.getShift());
				userSwipe.setCLASS_NO(empCurShift.getClass_no());
				session.insert("insertUserByOnDNShift", userSwipe);
				session.commit();
			}
		}
		catch(Exception ex) {
			ex.printStackTrace();
			logger.error("onDutyDaySwipeCard exception",ex);
		}
		return fieldSetting;
	}

	/*goOrOutWorkSwipeRecord*/
	public SwingBase onDutyOrOffDutySwipeRecord(SqlSession session, Employee employee, Date swipeCardTime,
			EmpShiftInfos empCurShift,String workShopNo,String RCNO,String PrimaryItemNo) {
		SwipeCardTimeInfos userSwipe = new SwipeCardTimeInfos();	
		SwingBase fieldSetting=new SwingBase();
		try {
			userSwipe.setEMP_ID(employee.getId());
			userSwipe.setSWIPE_DATE(FormatDateUtil.getCurDate());
			userSwipe.setSwipeCardTime(swipeCardTime);
			userSwipe.setShift(empCurShift.getShift());
			userSwipe.setWorkshopNo(workShopNo);
			
			int curOnDutyCardCount =  session.selectOne("selectCountAByCardID", userSwipe);
			// 無刷卡記錄
			if (curOnDutyCardCount == 0) {
					
				fieldSetting=onDutyDaySwipeCard(session, employee, swipeCardTime, empCurShift,RCNO,PrimaryItemNo,workShopNo);

			} else if (curOnDutyCardCount > 0) {

				int isGoWorkSwipeDuplicate = session.selectOne("isGoWorkSwipeDuplicate", userSwipe);
				if (isGoWorkSwipeDuplicate > 0) {
					fieldSetting=onDutySwipeDuplicate(session, employee, swipeCardTime, empCurShift.getShift());
				} else {
					// 下班刷卡
					fieldSetting=offDutyDaySwipeCard(session, employee,RCNO,PrimaryItemNo,workShopNo, swipeCardTime,empCurShift);
				}
			}
			
		}
		catch(Exception ex) {
			ex.printStackTrace();
			logger.error("onDutyOrOffDutySwipeRecord Exception",ex);
		}
		return fieldSetting;
	}
	
	public SwingBase swipeCardRecord(SqlSession session, Employee employee, Date swipeCardTime,String RCNO,String PrimaryItemNo,String workShopNo) {
		SwingBase fieldSetting=new SwingBase(); 
		EmpShiftInfos curShiftUser = new EmpShiftInfos();
		try {
			curShiftUser.setId(employee.getId());
			curShiftUser.setShiftDay(0);
			int empCurShiftCount =  session.selectOne("getShiftCount", curShiftUser);
			if(empCurShiftCount==0) {
				fieldSetting.setFieldColor(Color.RED);
				fieldSetting.setFieldContent("ID: " + employee.getId() + " Name: " + employee.getName() + "\n班別有誤，請聯繫助理核對班別信息!\n\n ");
				RawRecord swipeRecord=new RawRecord();
				swipeRecord.setCardID(employee.getCardID());
				swipeRecord.setId(employee.getId());
				swipeRecord.setSwipeCardTime(swipeCardTime);
				swipeRecord.setRecord_Status("2");
				session.update("updateRawRecordStatus",swipeRecord);
				session.commit();
			}
			else {
				Timestamp goWorkSwipeTime = new Timestamp(new Date().getTime());
				EmpShiftInfos empCurShift = (EmpShiftInfos) session.selectOne("getShiftByEmpId", curShiftUser);
				Calendar goWorkc = Calendar.getInstance();
				goWorkc.setTime(empCurShift.getClass_start());
				goWorkc.set(Calendar.HOUR_OF_DAY, goWorkc.get(Calendar.HOUR_OF_DAY) - 1);
				Date dt = goWorkc.getTime();
				Timestamp oneHBeforClassStart = new Timestamp(dt.getTime());
				
				SwipeCardTimeInfos userSwipe = new SwipeCardTimeInfos();
				Date SwipeCardTime2 = swipeCardTime;
				userSwipe.setEMP_ID(employee.getId());
				userSwipe.setSWIPE_DATE(FormatDateUtil.getCurDate());
				userSwipe.setSwipeCardTime(swipeCardTime);
				userSwipe.setSwipeCardTime2(SwipeCardTime2);
				userSwipe.setShift(empCurShift.getShift());
				userSwipe.setCLASS_NO(empCurShift.getClass_no());
				userSwipe.setWorkshopNo(workShopNo);
				userSwipe.setRC_NO(RCNO);
				userSwipe.setPRIMARY_ITEM_NO(PrimaryItemNo);
				
				if(goWorkSwipeTime.after(oneHBeforClassStart) && goWorkSwipeTime.before(empCurShift.getClass_start())) {
					int isOnDutySwipeDuplicate =  session.selectOne("isGoWorkSwipeDuplicate", userSwipe);
					if(isOnDutySwipeDuplicate>0) {
						fieldSetting=this.onDutySwipeDuplicate(session, employee, SwipeCardTime2, empCurShift.getShift());
					}
					else {
						fieldSetting=this.onDutyOrOffDutySwipeRecord(session, employee, SwipeCardTime2, empCurShift, workShopNo, RCNO, PrimaryItemNo);
					}
				}
				else {
					switch(empCurShift.getShift()) {
						case "D":
							if(goWorkSwipeTime.after(empCurShift.getClass_end())) {
								String name=employee.getName();
								int curDayGoWorkCardCount =  session.selectOne("selectCountAByCardID", userSwipe);
								if(curDayGoWorkCardCount==0) {
									int isOutWoakSwipeDuplicate =  session.selectOne("isOutWorkSwipeDuplicate", userSwipe);
									if(isOutWoakSwipeDuplicate>0) {
										fieldSetting=this.offDutySwipeDuplicate(session, employee, SwipeCardTime2, empCurShift.getShift());
									}
									else {
										int outWorkCardCount =  session.selectOne("selectOutWorkByCardID", userSwipe);
										if(outWorkCardCount==0) {
											fieldSetting.setFieldColor(Color.WHITE);
											fieldSetting.setFieldContent("下班刷卡\n" + "ID: " + employee.getId() + "\nName: " + employee.getName()
											+ "\n刷卡時間： " + FormatDateUtil.changeTimeToStr(swipeCardTime) + "\n今日班別為："+empCurShift.getClass_desc()+ "\n" + "員工下班刷卡成功！\n------------\n");
											session.insert("insertOutWorkSwipeTime",userSwipe);
											session.commit();
										}
										else {
											fieldSetting.setFieldColor(Color.RED);
											fieldSetting.setFieldContent("ID: " + employee.getId() + " Name: " + employee.getName() + "\n"
											+ "今日上下班卡已刷，此次刷卡無效！\n\n");
											
											RawRecord swipeRecord=new RawRecord();
											swipeRecord.setCardID(employee.getCardID());
											swipeRecord.setId(employee.getId());
											swipeRecord.setSwipeCardTime(swipeCardTime);
											swipeRecord.setRecord_Status("6");
											session.update("updateRawRecordStatus",swipeRecord);
											session.commit();
										}
									}
								}
								else {
									fieldSetting=this.offDutyDaySwipeCard(session, employee, RCNO, PrimaryItemNo, workShopNo, SwipeCardTime2, empCurShift);
								}
							}
							else {
								fieldSetting=this.onDutyOrOffDutySwipeRecord(session, employee, SwipeCardTime2, empCurShift, workShopNo, RCNO, PrimaryItemNo);
							}
							break;
						case "N":
							if(goWorkSwipeTime.getHours() > 12) {
								fieldSetting=this.onDutyOrOffDutySwipeRecord(session, employee, SwipeCardTime2, empCurShift, workShopNo, RCNO, PrimaryItemNo);
							}
							else if(goWorkSwipeTime.getHours()<=12) {
								fieldSetting.setFieldColor(Color.RED);
								fieldSetting.setFieldContent("ID: " + employee.getId() + " Name: " + employee.getName() + "\n班別： " + empCurShift.getClass_desc()
										+ "\n刷卡時間： " + swipeCardTime + "\n昨日班別非夜班，今日班別為夜班，請在夜班上班前刷上班卡！\n");
								RawRecord swipeRecord=new RawRecord();
								swipeRecord.setCardID(employee.getCardID());
								swipeRecord.setId(employee.getId());
								swipeRecord.setSwipeCardTime(swipeCardTime);
								swipeRecord.setRecord_Status("3");
								session.update("updateRawRecordStatus",swipeRecord);
								session.commit();
							}
							break;
						default:
							fieldSetting=null;
							break;
					}
				}
			}
		}
		catch(Exception ex) {
			ex.printStackTrace();
			logger.error("swipeCardRecord exception",ex);
		}
		return fieldSetting;
	}

}
