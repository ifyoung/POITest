package com.magicair.webpj.AFurui;


import com.alibaba.fastjson2.JSONObject;
import com.magicair.webpj.AFurui.model.BoxRules;
import com.magicair.webpj.AFurui.model.DepotRes;
import com.magicair.webpj.AFurui.model.ShopDetails;
import com.magicair.webpj.AFurui.model.wrap.WrapListWithMsg;
import com.magicair.webpj.core.Result;
import com.magicair.webpj.core.ResultCode;
import com.magicair.webpj.core.ResultGenerator;
import com.magicair.webpj.utils.CommonUtils;
import com.magicair.webpj.utils.Lg;
import com.magicair.webpj.utils.StringUtils;
import org.apache.commons.io.FileUtils;
import org.apache.poi.ss.formula.FormulaParseException;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.*;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.magicair.webpj.AFurui.ConstantFu.*;
import static com.magicair.webpj.AFurui.PoiUtiles.floatFormat;
import static com.magicair.webpj.AFurui.PoiUtiles.getBoxRulesList;


@Service
@Transactional
public class FuruiStockServiceImpl {

    private static List<TreeMap> dataMapsCache;
    public static boolean isOutNameUnique = true;//输出名是否带时间戳

    //key>当前上传进程唯一标识 List<InputStream> 收到的文件流
    //>>>>MultipartFile无法缓存？第二个开始汇报500？断流
//    public static TreeMap<String, Map<String, List<InputStream>>> G_inUploadService = new TreeMap<>();
    public static Map<String, byte[]> G_inUploadService = new HashMap<>();
    //TO DO 并发？？
    public static Map<String, String> G_inUploadServiceNames = new HashMap<>();
    //MultipartFile、InputStream 等流都无法有效静态缓存
//    public static List<InputStream> G_MultipartFiles = new ArrayList<>();

    @Value("${web.excel-path}")
    private String realPathExcel;


    public Result excelActionMergeAllFile(String outFileName, File inputBoxesRules, List<File> inputs) {

        long t1 = System.currentTimeMillis();
        dataMapsCache = new ArrayList<>();
        WrapListWithMsg<BoxRules> msgBoxes = null;//sheet表名 US-HMT，不包含颜色
        try {
            msgBoxes = getBoxRulesList(null, null, inputBoxesRules);
            //[]
            if (msgBoxes != null && msgBoxes.getErrMsg() != null && msgBoxes.getErrMsg().length() > 2) {//格式校验有误
                return ResultGenerator.genFailResult("解析箱规文件失败", msgBoxes.getErrMsg());
            }
        } catch (IOException e) {
            e.printStackTrace();
            return ResultGenerator.genFailResult("解析箱规文件失败", e);
        }

        Map boxes = msgBoxes.getListData().stream().collect(Collectors.groupingBy(BoxRules::getSKU));


        final String[] warning = {null};
        List<Runnable> tasks = new ArrayList<>();
        List<Result> resultsErr = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            int finalI = i;
            Runnable runnable = () -> {
                Result resultMap = inputConvertToMap(inputs.get(finalI), boxes);
                if (resultMap.getCode() == ResultCode.SUCCESS.code) {
//                        return resultMap;
                    synchronized (dataMapsCache) {
                        dataMapsCache.add((TreeMap) resultMap.getData());
                    }
                    if (!Objects.equals(resultMap.getMessage(), "SUCCESS")) {
                        warning[0] = resultMap.getMessage();
                    }
                } else {
                    resultsErr.add(resultMap);
                }

            };
            tasks.add(runnable);
        }

        CommonUtils.runTaskAwait(tasks, inputs.size());

        if (resultsErr.size() > 0) {
//            return resultsErr.get(0);//暂返回第一个错误
            Result resultAll = ResultGenerator.genFailResult("错误集合");
            resultsErr.forEach(result -> {
                List<Object> obs = new ArrayList<>();
                obs.add(result.getData());

                resultAll.setData(obs);
            });
            return resultAll;
        }


        //US-HMT-004-White-10_5-1
        //US-HMT-001-White05_5
//        Set<String> strings = sortedResDepotMap.keySet();
        String path = null;
        try {
            path = generateExcelAll(dataMapsCache, outFileName);
        } catch (IOException e) {
            e.printStackTrace();
            return ResultGenerator.genFailResult("结果生成失败", e.getMessage());
        }

        long t2 = System.currentTimeMillis() - t1;
        JSONObject jsonObject = new JSONObject();
        if (path != null) {
            if (path.contains("#")) {//有警示信息
                String[] res = path.split("#");
                jsonObject.put("path", res[0].toString());
                jsonObject.put("msg", res[1]);
            } else {
                jsonObject.put("path", path);
            }
            if (warning[0] != null) {
                if (jsonObject.containsKey("msg")) {
                    String msgOld = (String) jsonObject.get("msg");
                    jsonObject.put("msg", warning[0] + msgOld);
                } else {
                    jsonObject.put("msg", warning[0]);
                }
            }

        } else {
            jsonObject.put("path", path);
        }
        jsonObject.put("time_c", t2);
        boolean t = true;
        return ResultGenerator.genSuccessResult(jsonObject);

    }

    //M-解析货件-file模式
    public Result inputConvertToMap(File inputFile, Map boxes) {
        WrapListWithMsg<ShopDetails> msgDetails = null;//解析的sheet表名包含颜色
        try {
            msgDetails = getShopDetailsFast(inputFile);
            if (msgDetails != null && msgDetails.getErrMsg() != null && msgDetails.getErrMsg().length() > 2) {//格式校验有误
                return ResultGenerator.genFailResult("解析货件文件失败", msgDetails.getErrMsg());
            }
        } catch (IOException e) {
            e.printStackTrace();
            return ResultGenerator.genFailResult("解析货件文件失败", e);
        }

        //生成Excel 要按包含颜色的表名分组?>暂时不要，留行！
        List<DepotRes> resList = new ArrayList<>();

        List<String> resMsg = new ArrayList<>();

        msgDetails.getListData().forEach(new Consumer<ShopDetails>() {
            @Override
            public void accept(ShopDetails shopDetails) {

                List<BoxRules> boxRules = (List<BoxRules>) boxes.get(shopDetails.getSKU());

//                System.out.println("Excel->excelActionMerge>" + boxRules.size());
                if (boxRules == null) {//>>箱规咩有包含的
                    boolean t = true;
                    resMsg.add("箱规未包含：" + shopDetails.getSheetName() + ">" + shopDetails.getSKU());
                }

                if (boxRules != null && boxRules.size() > 0) {
                    BoxRules boxRules1 = boxRules.get(0);
                    DepotRes depotRes = new DepotRes();
                    depotRes.setSKU(shopDetails.getSKU());
                    depotRes.setFNSKU(boxRules1.getFNSKU());
                    depotRes.setfSize(boxRules1.getfSize());
                    depotRes.setAMOUNT_shoes_fast(PoiUtiles.intFormat(shopDetails.getAMOUNT_shoes_fast()));
                    depotRes.setAMOUNT_shoes_slow(PoiUtiles.intFormat(shopDetails.getAMOUNT_shoes_slow()));
                    depotRes.setPerBox(boxRules1.getPerBox());
//                    depotRes.setGroup(shopDetails.getSheetName());
                    depotRes.setGroup(inputFile.getName() + "#" + shopDetails.getSheetName());
                    //设置完，再计算
//                    depotRes.actionCompute(); //后面写入会再计算？但其实不影响？
                    resList.add(depotRes);
                }
            }
        });

        // 使用Stream流排序，按SKU升序排序
        //   resList = resList.stream().sorted(Comparator.comparing(DepotRes::getSKU)).collect(Collectors.toList());
        //不用再排序，自动按箱标里的sku的顺序
        Map resDepotMap = resList.stream().collect(Collectors.groupingBy(DepotRes::getGroup));

        // 使用TreeMap对resDepotMap按key值升序排序
        TreeMap<String, List<DepotRes>> sortedResDepotMap = new TreeMap<>(resDepotMap);
        if (resMsg.size() > 0) {
            return ResultGenerator.genSuccessResult(sortedResDepotMap, resMsg.toString());
        } else {
            return ResultGenerator.genSuccessResult(sortedResDepotMap);
        }

    }

    //生成，箱规放列表第一个?还是单独列出
    //箱规单独列出管理!>>>for test poi 解析法 内存坑
    public Result excelActionMergeAll(String outFileName, InputStream inputBoxesRules, List<InputStream> inputs) {

        long t1 = System.currentTimeMillis();
        dataMapsCache = new ArrayList<>();
        WrapListWithMsg<BoxRules> msgBoxes = null;//sheet表名 US-HMT，不包含颜色
        try {
            msgBoxes = getBoxRulesList(null, inputBoxesRules, null);
            //[]
            if (msgBoxes != null && msgBoxes.getErrMsg() != null && msgBoxes.getErrMsg().length() > 2) {//格式校验有误
                inputBoxesRules.close();
                return ResultGenerator.genFailResult("解析箱规文件失败", msgBoxes.getErrMsg());
            }
            inputBoxesRules.close();
        } catch (IOException e) {
            e.printStackTrace();
            return ResultGenerator.genFailResult("解析箱规文件失败", e);
        }

        Map boxes = msgBoxes.getListData().stream().collect(Collectors.groupingBy(BoxRules::getSKU));

//        String pathBoxesRules = realPathExcel + "Furuian-test/初始数据/US-HMT 2023.9.4 发货计划.xlsx"; //US-TPS shop

        List<Runnable> tasks = new ArrayList<>();
        List<Result> resultsErr = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            int finalI = i;
            Runnable runnable = () -> {
                Result resultMap = PoiUtiles.inputConvertToMap(inputs.get(finalI), boxes);
                if (resultMap.getCode() == ResultCode.SUCCESS.code) {
//                        return resultMap;
                    synchronized (dataMapsCache) {
                        dataMapsCache.add((TreeMap) resultMap.getData());
                    }
                } else {
                    resultsErr.add(resultMap);
                }

            };
            tasks.add(runnable);
        }

        CommonUtils.runTaskAwait(tasks, inputs.size());

        if (resultsErr.size() > 0) {
//            return resultsErr.get(0);//暂返回第一个错误
            Result resultAll = ResultGenerator.genFailResult("错误集合");
            resultsErr.forEach(new Consumer<Result>() {
                @Override
                public void accept(Result result) {
                    List<Object> obs = new ArrayList<>();
                    obs.add(result.getData());

                    resultAll.setData(obs);
                }
            });
            return resultAll;
        }


        //US-HMT-004-White-10_5-1
        //US-HMT-001-White05_5
//        Set<String> strings = sortedResDepotMap.keySet();
        String path = null;
        try {
//            path = generateExcel(sortedResDepotMap, outFileName);

            path = generateExcelAll(dataMapsCache, outFileName);
        } catch (IOException e) {
            e.printStackTrace();
            return ResultGenerator.genFailResult("结果生成失败", e.getMessage());
        }

        long t2 = System.currentTimeMillis() - t1;
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("path", path);
        jsonObject.put("time_c", t2);
        boolean t = true;
        return ResultGenerator.genSuccessResult(jsonObject);

    }


    private static int NULL_Name = 0;

    //M-根据解析数据生成最终Excel
    public String generateExcelAll(List<TreeMap> dataMaps, String outFileName) throws IOException {

        NULL_Name = 0;
        System.out.println("开始合成");

        List<String> nameRepeats = new ArrayList<>();
        // 创建一个工作簿对象
        //SXSSFWorkbook 基于流，更省内存，但不支持公式求值！！！
        XSSFWorkbook workbook = new XSSFWorkbook();
        dataMaps.forEach(treeMap -> {
            //构造sheet>>>非线程安全workbook
            if (treeMap.size() > 0) {
                Lg.i("建表名》》>>>", "", treeMap.keySet());
                Object ob = generateSheetBeiHuo(workbook, treeMap);
                if (ob instanceof String) {//有重复表名出错了
                    nameRepeats.add((String) ob);
                }
                NULL_Name = NULL_Name + 1;
            }


        });

        File fileDir = new File((realPathExcel + C_ResultExcelFloder));
        if (!fileDir.exists()) {
            fileDir.mkdirs();
            Lg.i("saveToAssets", "文件的路径不存在，创建:", fileDir.getPath());

        }
        String trueFileName = System.currentTimeMillis() + "-" + outFileName + ".xlsx";
        if (!isOutNameUnique) {
            trueFileName = outFileName + ".xlsx";
        }

        String path = fileDir.getPath() + '/' + trueFileName;

        String pathBack = C_ResultExcelFloder + "/" + trueFileName;
        // 将工作簿对象写入到文件中
        FileOutputStream out = new FileOutputStream(path);

        workbook.write(out);
        out.close();
        workbook.close();
        System.out.println("合成完成，文件位置：" + path);
        if (nameRepeats.size() > 0) {
            return pathBack + "#表名重复，略过" + nameRepeats;
        } else {
            return pathBack;
        }

    }

    //M-构造表
    public Object generateSheetBeiHuo(XSSFWorkbook workbook, TreeMap<String, List<DepotRes>> dataMap) {
        Set<String> tabStrs = dataMap.keySet();

        Object[] array = tabStrs.toArray();
        //The workbook already contains a sheet named '表名构造失败'>>不能重名
        String sheetName = "表名构造失败" + NULL_Name;
        String repeatName = null;
        if (array.length == 0) {
            boolean t = true;
        } else {
            String firstStr = (String) array[0]; // 取出第一个元素
//            String[] tags = firstStr.split("#");//取货件文件名(店铺名)
            // 找到～的位置
            int start = firstStr.indexOf("～");
// 找到第一个空格的位置
            int end = firstStr.indexOf(" ");
// 从start+1到end-1的位置提取子字符串
            String result = firstStr.substring(start + 1, end);

            String[] tags = result.split("-");//取货件文件名(店铺名)
            if (tags.length > 1) {
                sheetName = tags[0] + "-" + tags[1];
            }

            repeatName = firstStr;
        }


        // 创建一个工作簿对象
//        XSSFWorkbook workbook = new XSSFWorkbook();
        //TO DO DE-FRA 重复内表名情况？？
        XSSFSheet sheet = null;
        try {
            sheet = workbook.createSheet(sheetName);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            // 找到～的位置
            int start = repeatName.indexOf("～");
// 找到第一个空格的位置
            int end = repeatName.indexOf(".x");
// 从start+1到end-1的位置提取子字符串
            String result = repeatName.substring(start + 1, end);
            return result;
        }


        // 创建一个单元格样式对象
//        CellStyle style = workbook.createCellStyle();
        XSSFCellStyle style = workbook.createCellStyle();
        // 设置水平居中
        style.setAlignment(HorizontalAlignment.CENTER);
        // 设置垂直居中
        style.setVerticalAlignment(VerticalAlignment.CENTER);


        final int[] lastMax = {0};
        final int[] lastLen = {0};//缓存一组列表长度，便于计算总计

        List<String> mTitles = sheetName.contains("US") ? C_titleM : C_titleKh;
        XSSFSheet finalSheet = sheet;
        tabStrs.forEach(s -> {

            List<DepotRes> depotRes = dataMap.get(s);
            int len = depotRes.size();

            for (int i = lastMax[0]; i <= len + lastMax[0]; i++) {

                //US-HMT-004-White-10_5-1
                //US-HMT-001-White05_5
                if (i == lastMax[0]) {//title

                    XSSFCellStyle styleIn = workbook.createCellStyle();//XSSFCellStyle才可自由设置颜色
                    styleIn.cloneStyleFrom(style);
                    XSSFColor myColor = new XSSFColor();
                    byte[] byteColor = new byte[]{(byte) 252, (byte) 197, (byte) 201};
//                        myColor.setARGBHex("fffcc5c9");
                    myColor.setRGB(byteColor);

//                        styleIn.setFillForegroundColor(IndexedColors.TEAL.getIndex());
                    styleIn.setFillForegroundColor(myColor);
                    styleIn.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                    if (i == 0) {
                        // 创建一个行对象，表示第一行
                        XSSFRow row = finalSheet.createRow(i);
                        row.setHeight((short) (25 * 20)); // 25 像素 = 25 * 20 缇
                        mTitles.forEach(new Consumer<String>() {
                            @Override
                            public void accept(String s) {
                                XSSFCell cell0 = row.createCell(mTitles.indexOf(s));
                                cell0.setCellValue(s);
                                cell0.setCellStyle(styleIn);

                            }
                        });
                    } else {
                        //总计
                        XSSFRow rowSum = finalSheet.createRow(i - 2);
//                            rowSum.setHeight((short) (25 * 20)); // 25 像素 = 25 * 20 缇
                        XSSFFormulaEvaluator formulaEvaluator =
                                workbook.getCreationHelper().createFormulaEvaluator();
                        //4 5 6 7 8
                        String[] sumKeys = new String[]{"E", "F", "G", "H", "I"};
                        for (int j = 0; j < sumKeys.length; j++) {

                            // E、F、G、H、I
                            XSSFCell cell0 = rowSum.createCell(j + 4);
//                                        cell0.getAddress()
                            //"SUM(F2:F4)"
//                                String start = sumKeys[j] + 2;
                            String start = sumKeys[j] + (i - lastLen[0] - 1);
                            String end = sumKeys[j] + (i - 2);
                            cell0.setCellFormula("SUM(" + start + ":" + end + ")");
                            formulaEvaluator.evaluate(cell0);
                            cell0.setCellStyle(style);
//                                System.out.println("Excel->merge>SUM" + i + "lastLen>" + lastLen[0] + "》len>" + len + "start>" + start + "end>" + end);

                        }

                        //下移
//                            sheet.shiftRows(i+1, sheet.getLastRowNum()+1, 1);
                        // 创建一个行对象，tricky! 不是 i+1?? 组记录也不是+1是+2？？
                        XSSFRow row = finalSheet.createRow(i);
                        row.setHeight((short) (25 * 20)); // 25 像素 = 25 * 20 缇
                        mTitles.forEach(s1 -> {
                            XSSFCell cell0 = row.createCell(mTitles.indexOf(s1));
                            cell0.setCellValue(s1);
                            cell0.setCellStyle(styleIn);

                        });
//                            System.out.println("Excel->merge>title" + i + "》" + lastMax[0]);

                    }


                } else {
                    XSSFRow row = finalSheet.createRow(i);
                    DepotRes depotRes1 = depotRes.get(i - lastMax[0] - 1);
//                        Class<?> objClass = depotRes1.getClass();
//                        Field[] fields = objClass.getDeclaredFields();

//                        JSONObject json = (JSONObject) JSON.toJSON(depotRes1); // 转换为JSONObject对象
//                        Set<String> fields = json.keySet();//遍历赋值顺序不好控制 ？？

//                        System.out.println("Excel->merge>" + depotRes1.getSKU() + "》" + i + "》" + lastMax);

                    XSSFCell cell0 = row.createCell(0);
                    cell0.setCellValue(depotRes1.getSKU());
                    cell0.setCellStyle(style);
                    XSSFCell cell1 = row.createCell(1);
                    cell1.setCellValue(depotRes1.getfSize());
                    cell1.setCellStyle(style);
                    XSSFCell cell2 = row.createCell(2);
                    cell2.setCellValue(depotRes1.getFNSKU());
                    cell2.setCellStyle(style);
                    XSSFCell cell3 = row.createCell(3);
                    cell3.setCellValue(depotRes1.getPerBox());
                    cell3.setCellStyle(style);
                    XSSFCell cell4 = row.createCell(4);
                    int fastBoxes = depotRes1.getAMOUNT_boxes_fast();
                    cell4.setCellValue(fastBoxes);

                    XSSFCellStyle styleIn = workbook.createCellStyle();//XSSFCellStyle才可自由设置颜色
                    styleIn.cloneStyleFrom(style);
                    XSSFColor myColor = new XSSFColor();
//                    byte[] byteColor = new byte[]{(byte) 252, (byte) 197, (byte) 201};
//                        myColor.setARGBHex("fffcc5c9");
                    myColor.setIndexed(IndexedColors.LIGHT_GREEN.getIndex());
//                        styleIn.setFillForegroundColor(IndexedColors.TEAL.getIndex());
                    styleIn.setFillForegroundColor(myColor);
                    styleIn.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                    boolean isIntDivide = fastBoxes * depotRes1.getPerBox() == depotRes1.getAMOUNT_shoes_fast();
                    cell4.setCellStyle(isIntDivide ? style : styleIn);

                    XSSFCell cell5 = row.createCell(5);
                    int slowBoxes = depotRes1.getAMOUNT_boxes_slow();
                    cell5.setCellValue(slowBoxes);
                    boolean isIntDivideS = slowBoxes * depotRes1.getPerBox() == depotRes1.getAMOUNT_shoes_slow();
                    cell5.setCellStyle(isIntDivideS ? style : styleIn);

                    XSSFCell cell6 = row.createCell(6);
                    cell6.setCellValue(depotRes1.getAMOUNT_shoes_fast());
                    cell6.setCellStyle(style);
                    XSSFCell cell7 = row.createCell(7);
                    cell7.setCellValue(depotRes1.getAMOUNT_shoes_slow());
                    cell7.setCellStyle(style);
                    XSSFCell cell8 = row.createCell(8);
                    cell8.setCellValue(depotRes1.getAMOUNT_boxes_all());
                    cell8.setCellStyle(style);
                }


            }

            lastLen[0] = len;
            lastMax[0] = len + lastMax[0] + 3;//加一空行,再加上总计
        });

        //最后一组
        int lastRow = sheet.getLastRowNum();//前面map大小为0，空表？？
        if (lastRow < 1) {
            return sheet;
        }
        XSSFRow rowSum = sheet.createRow(lastRow + 1);
        XSSFFormulaEvaluator formulaEvaluator =
                workbook.getCreationHelper().createFormulaEvaluator();
        //4 5 6 7 8
        String[] sumKeys = new String[]{"E", "F", "G", "H", "I"};
        for (int j = 0; j < sumKeys.length; j++) {
            // E、F、G、H、I
            XSSFCell cell0 = rowSum.createCell(j + 4);
            //"SUM(F2:F4)"
            String start = sumKeys[j] + (lastRow - lastLen[0] + 2);
            String end = sumKeys[j] + (lastRow + 1);

            try {
                cell0.setCellFormula("SUM(" + start + ":" + end + ")");
            } catch (FormulaParseException | IllegalStateException e) {
                e.printStackTrace();
                Lg.e("总计构建失败>>>>", sheet.getSheetName());
            }
            formulaEvaluator.evaluate(cell0);
            cell0.setCellStyle(style);

//            System.out.println("Excel->merge>SUM" + lastRow + "lastLen>" + lastLen[0] + "start>" + start + "end>" + end);

        }

        PoiUtiles.adjustAutoWidth(sheet, mTitles.size());
        dataMap.clear();//清缓存
        return sheet;
    }


    //M-获取最新箱规文件>>>或上传文件
    public File[] getLatestBoxRules(String folderPath) {
        String path = realPathExcel + C_BaseBoxFloder;
        if (!StringUtils.isNullOrEmpty(folderPath)) {
            path = realPathExcel + folderPath;
        }
        File fileDir = new File(path);
        if (!fileDir.exists()) {
            fileDir.mkdirs();
            Lg.i("saveToAssets", "文件的路径不存在，创建:", fileDir.getPath());

        }

        //创建File对象
//        File file = new File("D:\\test");
        //使用listFiles()方法过滤文件
        File[] files = fileDir.listFiles((dir, name) -> (name.endsWith(".xls") || name.endsWith(".xlsx")));
        if (files == null || files.length == 0) {
            return null;
        }
        //使用Arrays类中的sort方法按照文件名排序
//        Arrays.sort(files, (f1, f2) -> f1.getName().compareTo(f2.getName())); //升序
        Arrays.sort(files, (f1, f2) -> f2.getName().compareTo(f1.getName())); //降序
        //遍历输出文件名
//        for (File f : files) {
//            System.out.println(f.getName());
//        }
        return files;//列出所有
    }

    //M-保存上传文件到本地
    public String saveToAssets(String folder, InputStream fileInputStream, String fileName) {
//        String floder = "/Results";

//        File fileDir = new File((realPathExcel + C_BaseBoxFloder));
        File fileDir = new File((realPathExcel + folder));
        if (!fileDir.exists()) {
            fileDir.mkdirs();
            Lg.i("saveToAssets", "文件的路径不存在，创建:", fileDir.getPath());

        }

        //不要用#号，浏览器路径会混淆，好像可选的不多，就用～
        String trueFileName = System.currentTimeMillis() + "～" + fileName;

        String path = fileDir.getPath() + '/' + trueFileName;
        Lg.i("saveToAssets", "存放箱规等资料的路径:", path);

        try {
            FileUtils.copyInputStreamToFile(fileInputStream, new File(path));
//                file.transferTo(new File(path));//jetty 报错
            fileInputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
            Lg.i("uploadFile", "文件存储失败");
            return null;
        }

        return path;
    }

    //M-读取货件>>> fast
    public WrapListWithMsg<ShopDetails> getShopDetailsFast(File fileIn) throws IOException {

        ReadableWorkbook wb = new ReadableWorkbook(fileIn);

        WrapListWithMsg<ShopDetails> msgBoxes = new WrapListWithMsg<>();
        List<ShopDetails> shopDetails = new ArrayList<>();
        List<String> errStrs = new ArrayList<>();
        msgBoxes.setListData(shopDetails);//放前面放后面一样？地址指向？ List<String> 不行？


        Stream<org.dhatim.fastexcel.reader.Sheet> sheets = wb.getSheets(); //获取Workbook中sheet的个数

        sheets.forEach(sheet -> {
            String name = sheet.getName(); //获取每个sheet的名称
            boolean isOneWay = name.toUpperCase().contains("JP-");
            if (name.contains("不发") || name.contains("数据源")) {//过滤表

            } else {
                org.dhatim.fastexcel.reader.SheetVisibility sheetVisibility = sheet.getVisibility(); //获取每个sheet的可见性
                if (sheetVisibility == org.dhatim.fastexcel.reader.SheetVisibility.VISIBLE) {
                    try { // Get a stream of rows from the sheet
                        List<org.dhatim.fastexcel.reader.Row> rr = sheet.read();
                        if (!rr.isEmpty()) {
                            int size = rr.size();

                            //首行取关键列
                            org.dhatim.fastexcel.reader.Row row0 = rr.get(0);
                            List<String> keysNeed = new ArrayList<>();//地址 A、B...

                            row0.stream().forEach(cell -> {
                                if (cell == null) {
                                    return;
                                }
                                Object cellData = cell.getValue();
                                if (cellData instanceof String) {
                                    String colKey = (String) cellData;
                                    staticKeys.forEach(s -> {
                                        boolean isHasKey = isOneWay ? colKey.endsWith(s) : colKey.contains(s);
                                        if (isHasKey) {
                                            String address = String.valueOf(cell.getAddress());
                                            String firstChar = String.valueOf(address.charAt(0));
                                            if (!keysNeed.contains(firstChar)) {
                                                keysNeed.add(firstChar);
                                            }
                                            staticKeysTmp.remove(s);
                                        }

                                    });
                                }
                            });
                            String fName = fileIn.getName();
                            String[] fNames = fName.split("～");
                            if (fNames.length == 2) {
                                fName = fNames[1];
                            }
                            if (keysNeed.size() == 0) {


                                errStrs.add(fName + ">" + name + ">未检测到关键列:" + staticKeysTmp.toString() + ",无法解析");
                                msgBoxes.setErrMsg(errStrs.toString());//放前面放后面一样？地址指向？
                            } else {

                                //美国才有两渠道，日本、欧洲一个渠道(都预置两渠道，一个的默认快的)
                                if (isOneWay) {//日本单列出
                                    if (keysNeed.size() != 1) {//只有两个关键列
                                        errStrs.add(fName + ">" + name + ">未检测到关键列,无法解析：" + staticKeysTmp.toString());
                                        msgBoxes.setErrMsg(errStrs.toString());//放前面放后面一样？地址指向？
                                        return;
                                    }
                                } else {
                                    if (keysNeed.size() < 2) {//只有两个关键列
                                        errStrs.add(fName + ">" + name + ">未检测到关键列,无法解析：" + staticKeysTmp.toString());
                                        msgBoxes.setErrMsg(errStrs.toString());//放前面放后面一样？地址指向？
                                        return;
                                    }
                                }


//                                Lg.i("解析关键字>>>>" + name, ">>", keysNeed);
                                Pattern pattern = Pattern.compile("A\\d+");
                                Pattern patternP = Pattern.compile(keysNeed.get(0) + "\\d+");
                                Pattern patternR = isOneWay ? patternP : Pattern.compile(keysNeed.get(1) + "\\d+");
                                //排除首行
                                for (int i = 1; i < size; i++) {
                                    org.dhatim.fastexcel.reader.Row row = rr.get(i);
                                    ShopDetails details = new ShopDetails();
                                    details.setSheetName(name);
                                    //底部空一大片又突然出现一小格的情况,或者隐藏行
                                    if (row == null || row.getCellCount() < 2) {
                                        boolean tt = true;
                                        continue;
                                    }
                                    Optional<org.dhatim.fastexcel.reader.Cell> fCell = row.getOptionalCell(0);
//                                    org.dhatim.fastexcel.reader.Cell fCell = row.getFirstNonEmptyCell().get();
                                    if (!fCell.isPresent() || fCell.get().getText().isEmpty() || StringUtils.hasChinese(fCell.get().getText())) {
                                        continue;
                                    }

                                    String finalFName = fName;
                                    row.stream().forEach(cell -> {
                                        if (cell == null) {
                                            return;
                                        }
                                        Object cellData = cell.getValue();
                                        String address = String.valueOf(cell.getAddress());

                                        //排除"AA" "BB"等情况 >>>更不能按长度,eg,A212
//                                            if (address.contains("A") && !address.startsWith("A", 1)) {//一行开始》》不严谨
                                        if (address.startsWith("A")) {//一行开始
                                            if (pattern.matcher(address).matches()) {
                                                if (cellData instanceof String) {
                                                    String sku = (String) cellData;
                                                    details.setSKU(sku);

                                                } else {
//                                System.out.println("sku类型错误>>" + address + ">>" + cellData);
                                                    errStrs.add(finalFName + ">" + name + ">" + address + ":类型错误");
                                                }
                                            }

                                        } else if (address.contains("B")) {//可能是美码列？
                                            String size1 = String.valueOf(cellData);

                                        } //第二个字符也判断一下,排除AA等情况
                                        else if (patternP.matcher(address).matches()) {//默认美森在前,可能有AA BB这种情况,严格判断

                                            if (cellData == null) {
                                                errStrs.add(finalFName + ">" + name + ">" + address + ":格式错误");
                                            } else {
                                                String fastNum = String.valueOf(cellData);
//                            rules.setbWidth(Float.parseFloat(width));

                                                Float rFast = floatFormat(fastNum);
                                                if (rFast != null) {
                                                    int rFastInt = Math.round(rFast);
                                                    details.setAMOUNT_shoes_fast(String.valueOf(rFastInt));
                                                } else {
                                                    errStrs.add(finalFName + ">" + name + ">" + address + ":格式错误");
                                                }
                                            }


                                        } else if (patternR.matcher(address).matches()) {//慢

                                            if (cellData == null) {
                                                errStrs.add(finalFName + ">" + name + ">" + address + ":格式错误");
                                            } else {
                                                String slowNum = String.valueOf(cellData);

                                                Float rSlow = floatFormat(slowNum);
                                                if (rSlow != null) {
                                                    int rSlowInt = Math.round(rSlow);
                                                    details.setAMOUNT_shoes_slow(String.valueOf(rSlowInt));
                                                } else {
                                                    errStrs.add(finalFName + ">" + name + ">" + address + ":格式错误");
                                                }
                                            }


                                        }
                                        if (!shopDetails.contains(details)) {
                                            shopDetails.add(details);
                                        }

                                    });

                                }

                            }


                        }


                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            }

        });
        msgBoxes.setErrMsg(errStrs.toString());//放前面放后面一样？地址指向？
        if (wb != null) {
            wb.close();
        }
        return msgBoxes;
    }


}
