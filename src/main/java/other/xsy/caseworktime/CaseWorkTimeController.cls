public without sharing class CaseWorkTimeController {
    @AuraEnabled
    public static PickClass GetCase(){
        Id recordId = UserInfo.getUserId();
        User u=[SELECT ID,Profile.Name FROM User WHERE ID=:recordId];
        // if(u.Profile.Name.contains('售后')){
            // Case.Status= '已关闭' 
            // Case.CaseNumber
            // Case.CreatedDate
            Datetime day = System.today();
            Integer year = day.year();
            Integer mon = day.month();
            System.debug(LoggingLevel.INFO, '*** year: ' + year);
            System.debug(LoggingLevel.INFO, '*** mon: ' + mon);
            
            //查出历史未关闭个案及当月关闭个案
            List<Case> casList = [SELECT Id,InOrderNum__c,StandWorkTime__c, ChangeWorkTime__c,CreatedDate,CaseNumber,ExpectFinishDate__c,ReadlyTime__c,ProductType__c,Status,TotalWorkTimeTota__c,
            (SELECT Id,ThisMon__c,WorkTime__c FROM CaseTime__r),
            (SELECT Id,WorkTime__c,ReadlyTime__c,Status,Subject,RecordType.Name,WorkOrderNumber FROM WorkOrders WHERE RecordTYpe.DeveloperName!='Staff')
            FROM Case 
            WHERE 
            ( 
                Status!='已关闭'
                OR ( Status ='已关闭' AND CALENDAR_YEAR(ClosedDate) =:year AND CALENDAR_MONTH(ClosedDate) = :mon)

            )
                AND InOrderNum__c>0
            ];
            
            PickClass pc = new PickClass();
            //未跨月个案
            pc.casMonTList = new List<InnerClass>();
            //跨月个案
            pc.casMonTList2 = new List<InnerClass>();
            if (!casList.isEmpty()) {
                Set<Id> caIdSet = new Set<Id>();
                Map<Id,Case> caIdMap = new Map<Id,Case>();
                for (Case ca : casList) {
                    caIdSet.add(ca.Id);
                    caIdMap.put(ca.Id, ca);
                }
                //查出已经报过的当月个案工时
                List<CaseMonTime__c> camTList = [SELECT Id,
                                                        Case__c,
                                                        Case__r.TotalWorkTimeTota__c,
                                                        Case__r.ReadlyTime__c,
                                                        Case__r.StandWorkTime__c,
                                                        Case__r.ChangeWorkTime__c,
                                                        Status__c,
                                                        CaseNumber__c,
                                                        ExpectFinishDate__c,
                                                        ReadlyTime__c,
                                                        CreatedDate__c,
                                                        ProductType__c,
                                                        ClosedDate__c,
                                                        TotalWorkTimeTota__c,
                                                        WorkTime__c,
                                                        CreatedDate,
                                                 (SELECT ID,WorkOrderNumber__c,Work_RecordType__c,WorkOrder__c,WorkOrder__r.WorkTime__c,WorkOrder__r.ReadlyTime__c,Alredy_Time__c,DealTime__c FROM Case_WorkOrder__r)
                                                 FROM CaseMonTime__c
                                                 WHERE Case__c IN:caIdSet
                                                 AND CALENDAR_YEAR(CreatedDate) = :year
                                                 AND CALENDAR_MONTH(CreatedDate) = :mon];
                Map<String,CaseMonTime__c> camTIdList = new Map<String,CaseMonTime__c>();
                Map<String,Case_WorkOrder__c> wokTIdList = new Map<String,Case_WorkOrder__c>();
                if (!camTList.isEmpty()) {
                    for (CaseMonTime__c cmt : camTList) {
                        camTIdList.put(cmt.Case__c, cmt);
                        if(!cmt.Case_WorkOrder__r.isEmpty()){
                            for(Case_WorkOrder__c cwo:cmt.Case_WorkOrder__r){
                                wokTIdList.put(cwo.WorkOrder__c,cwo);
                            }    
                        }
                        
                    }
                }

                for (Case ca : casList) {
                    InnerClass newInner=new InnerClass();
                    newInner.casMonTime=new CaseMonTime__c();
                    newInner.cwoMonTime=new List<Case_WorkOrder__c>();
                    newInner.inOrderNum=Integer.valueOf(ca.InOrderNum__c);
                    if (camTIdList.containsKey(ca.Id)) {
                        //已经报工过的个案，查出对应的个案工时。
                        CaseMonTime__c cti =camTIdList.get(ca.Id);
                        newInner.casMonTime=cti;
                        cti.TotalWorkTimeTota__c = cti.Case__r.StandWorkTime__c + (cti.Case__r.ChangeWorkTime__c==null?0:cti.Case__r.ChangeWorkTime__c);
                        cti.ReadlyTime__c = cti.Case__r.ReadlyTime__c;
                        Decimal workTime = cti.Case__r.StandWorkTime__c + (cti.Case__r.ChangeWorkTime__c==null?0:cti.Case__r.ChangeWorkTime__c) -(cti.Case__r.ReadlyTime__c==null?0:cti.Case__r.ReadlyTime__c);
                        cti.WorkTime__c= workTime;
                        Decimal totalTime=0;//合计工时
                        totalTime+=cti.WorkTime__c;
                        //比对个案工单工时
                        if(!ca.WorkOrders.isEmpty()){
                            for(WorkOrder wo:ca.WorkOrders){
                                if(wokTIdList.containsKey(wo.Id)){
                                    Case_WorkOrder__c cwo =wokTIdList.get(wo.Id);
                                    System.debug(LoggingLevel.INFO, '*** cwo: ' + cwo);
                                    cwo.TotalTime__c = cwo.WorkOrder__r.WorkTime__c==null?0:cwo.WorkOrder__r.WorkTime__c;
                                    cwo.ReadlyTime__c=cwo.WorkOrder__r.ReadlyTime__c==null?0:cwo.WorkOrder__r.ReadlyTime__c;
                                    cwo.DealTime__c=cwo.TotalTime__c-cwo.ReadlyTime__c;
                                    totalTime+=cwo.DealTime__c;
                                    newInner.cwoMonTime.add(cwo);
                                }else{
                                    Case_WorkOrder__c cwo =new Case_WorkOrder__c();
                                    cwo.WorkOrder__c=wo.Id;
                                    cwo.Status__c=wo.Status;
                                    cwo.TotalTime__c=wo.WorkTime__c==null?0:wo.WorkTime__c;
                                    cwo.DealTime__c=cwo.TotalTime__c-(cwo.WorkOrder__r.ReadlyTime__c==null?0:cwo.WorkOrder__r.ReadlyTime__c);
                                    cwo.ReadlyTime__c=cwo.WorkOrder__r.ReadlyTime__c==null?0:cwo.WorkOrder__r.ReadlyTime__c;
                                    cwo.CaseMonTime__c=cti.Id;
                                    cwo.WorkOrderNumber__c=wo.WorkOrderNumber;
                                    cwo.Work_RecordType__c=wo.RecordType.Name;
                                    cwo.Alredy_Time__c=0;
                                    totalTime+=cwo.DealTime__c;
                                    newInner.cwoMonTime.add(cwo);
                                }
                            }
                        }
                        if (cti.CreatedDate__c.month() == mon && cti.CreatedDate__c .year() == year&&totalTime!=0) {
                            
                            pc.casMonTList.add(newInner);

                        }
                        if (cti.CreatedDate__c.month() != mon&&totalTime!=0) {
                            pc.casMonTList2.add(newInner );
                        }
                    }else{
                        System.debug(LoggingLevel.INFO, '*** ca.TotalWorkTimeTota__c: ' + ca.TotalWorkTimeTota__c);
                        System.debug(LoggingLevel.INFO, '*** ca.ReadlyTime__c: ' + ca.ReadlyTime__c);
                        CaseMonTime__c ct = new CaseMonTime__c(Case__c = ca.Id,
                                                               Status__c=ca.Status,
                                                               CaseNumber__c = ca.CaseNumber,
                                                               ExpectFinishDate__c = ca.ExpectFinishDate__c,
                                                               CreatedDate__c = ca.CreatedDate,
                                                               ProductType__c = ca.ProductType__c,
                                                               WorkTime__c = ca.StandWorkTime__c + (ca.ChangeWorkTime__c==null?0:ca.ChangeWorkTime__c) -(ca.ReadlyTime__c==null?0:ca.ReadlyTime__c),
                                                               ReadlyTime__c = ca.ReadlyTime__c==null?0:ca.ReadlyTime__c,
                                                               TotalWorkTimeTota__c = ca.StandWorkTime__c + (ca.ChangeWorkTime__c==null?0:ca.ChangeWorkTime__c));
                        
                        newInner.casMonTime=ct;
                        Decimal totalTime=0;//合计工时
                        totalTime+=ct.WorkTime__c;
                        //比对个案工单工时
                        if(!ca.WorkOrders.isEmpty()){
                            for(WorkOrder wo:ca.WorkOrders){
                                Case_WorkOrder__c cwo =new Case_WorkOrder__c();
                                cwo.WorkOrder__c=wo.Id;
                                cwo.Status__c=wo.Status;
                                cwo.TotalTime__c=wo.WorkTime__c==null?0:wo.WorkTime__c;
                                cwo.DealTime__c=cwo.TotalTime__c-(wo.ReadlyTime__c==null?0:wo.ReadlyTime__c);
                                cwo.ReadlyTime__c=wo.ReadlyTime__c==null?0:wo.ReadlyTime__c;
                                cwo.WorkOrderNumber__c=wo.WorkOrderNumber;
                                cwo.Work_RecordType__c=wo.RecordType.Name;
                                cwo.Alredy_Time__c=0;
                                totalTime+=cwo.DealTime__c;
                                newInner.cwoMonTime.add(cwo);
                            }
                        }
                        System.debug(LoggingLevel.INFO, '*** ct: ' + ct);
                        if (ca.CreatedDate.month() == mon && ca.CreatedDate.year() == year&&totalTime!=0) {
                            pc.casMonTList.add(newInner);
                        }
                        if (ca.CreatedDate.month() != mon&&totalTime!=0) {
                            pc.casMonTList2.add(newInner);
                        }
                        
                    }
                }

            }
            
            return pc;
        // }else{
        //     return null;
        // }
        
        
    }

    @AuraEnabled
    public static String Save(List<InnerClass> TimeList,List<InnerClass>OverTimeList){
        String dd = '';
        List<CaseMonTime__c> saveList=new List<CaseMonTime__c>();
        List<Case_WorkOrder__c> saveList2=new List<Case_WorkOrder__c>();
        for (InnerClass camtt : TimeList) {
            
            saveList.add(camtt.casMonTime);
            
        }
        for (InnerClass camttt : OverTimeList) {
            
            saveList.add(camttt.casMonTime);
            
        }

        SavePoint sp = Database.setSavepoint();
        try{
            upsert saveList;
            for (InnerClass camtt : TimeList) {
                if(camtt.cwoMonTime.size()>0){
                    for(Case_WorkOrder__c cwo:camtt.cwoMonTime){
                        if(cwo.CaseMonTime__c==null){
                            cwo.CaseMonTime__c=camtt.casMonTime.Id;
                        }
                        saveList2.add(cwo);
                    }    
                }
                
            }
            for (InnerClass camttt : OverTimeList) {
                if(camttt.cwoMonTime.size()>0){
                    for(Case_WorkOrder__c cwo:camttt.cwoMonTime){
                    
                        if(cwo.CaseMonTime__c==null){
                            System.debug(LoggingLevel.INFO, '*** camttt.casMonTime.Id: ' + camttt.casMonTime.Id);
                            System.debug(LoggingLevel.INFO, '*** cwo: ' + cwo);        
                            cwo.CaseMonTime__c=camttt.casMonTime.Id;   
                        }
                        saveList2.add(cwo);
                    }    
                }
            }
            upsert saveList2;
            return dd;
        }catch(Exception e){
            Database.rollback(sp);
            throw new AuraHandledException(e.getMessage());
        }

    }
    public Class PickClass{
        @AuraEnabled
        public List<InnerClass> casMonTList { GET; SET; }
        @AuraEnabled
        public List<InnerClass> casMonTList2{GET;SET;}
    }


    public Class InnerClass{
        @AuraEnabled
        public CaseMonTime__c casMonTime{GET;SET;}
        @AuraEnabled
        public List<Case_WorkOrder__c> cwoMonTime {GET;SET;}
        @AuraEnabled
        public Integer inOrderNum {GET;SET;}
    }
}