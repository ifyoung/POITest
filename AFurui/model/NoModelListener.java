package com.magicair.webpj.AFurui.model;

//
//import com.alibaba.excel.context.AnalysisContext;
//import com.alibaba.excel.event.AnalysisEventListener;
//import com.alibaba.excel.read.listener.ReadListener;
//import com.alibaba.excel.read.metadata.holder.ReadRowHolder;
//import com.alibaba.excel.read.metadata.holder.ReadSheetHolder;
//import com.magicair.webpj.utils.Lg;
//
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Map;
//
////无模型读取
//public class NoModelListener extends AnalysisEventListener<Map<Integer, String>> {
////    private static final Logger LOGGER = LoggerFactory.getLogger(NoModleDataListener.class);
//    /**
//     * 每隔5条存储数据库，实际使用中可以3000条，然后清理list ，方便内存回收
//     */
////    private static final int BATCH_COUNT = 5;
//    List<Map<Integer, String>> list = new ArrayList<Map<Integer, String>>();
//
//
//
//
//    @Override
//    public void invoke(Map<Integer, String> data, AnalysisContext context) {
//
//
//        list.add(data);
//
//        Lg.i("NoModleDataListener>>", "invoke>", data);
//    }
//
//
//    @Override
//    public void doAfterAllAnalysed(AnalysisContext context) {
//        Lg.i("NoModleDataListener>>", "doAfterAllAnalysed>", context);
//        saveData();
//    }
//
//    /**
//     * 加上存储数据库
//     */
//    private void saveData() {
//        ReadSheetHolder readSheetHolder = new ReadSheetHolder();
//
//    }
//
//}