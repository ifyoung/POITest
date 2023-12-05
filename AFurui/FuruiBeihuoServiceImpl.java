package com.magicair.webpj.AFurui;

import com.alibaba.fastjson2.JSONObject;
import com.magicair.webpj.AFurui.model.BoxRules;
import com.magicair.webpj.AFurui.model.DepotRes;
import com.magicair.webpj.AFurui.model.wrap.WrapListWithMsg;
import com.magicair.webpj.core.Result;
import com.magicair.webpj.core.ResultGenerator;
import com.magicair.webpj.utils.CommonUtils;
import com.magicair.webpj.utils.Lg;
import com.magicair.webpj.utils.StringUtils;
import com.magicair.webpj.utils.ZipUtils;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.formula.FormulaParseException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Sheet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.magicair.webpj.AFurui.ConstantFu.*;
import static com.magicair.webpj.AFurui.PoiUtiles.floatFormat;


@Service
@Transactional
public class FuruiBeihuoServiceImpl {

    private static List<TreeMap> dataMapsCache;

    public static Map<String, String> G_inUploadServiceNames = new HashMap<>();

    @Value("${web.excel-path}")
    private String realPathExcel;

//    @Resource
//    FuruiStockServiceImpl furuiStockService;

    public Result excelActionMergeBehuo(File fileBeihuo, String folderToken, boolean isCM, Map<String, List<DepotRes>> lastInfos) {
        staticKeysTmp = new ArrayList<>(Arrays.asList("计划箱数", "备货数量"));//重置一下
        WrapListWithMsg<DepotRes> msgDepots = null;
        try {
            msgDepots = getFromBeiHuoFast(fileBeihuo);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (msgDepots == null || msgDepots.getErrMsg() != null && msgDepots.getErrMsg().length() > 2) {//格式校验有误
            return ResultGenerator.genFailResult("解析仓库备货文件失败", msgDepots.getErrMsg());
        }

        //所有箱规
        File[] fileRules = getLatestBoxRules(null);
//        PoiUtiles.getBoxRulesList(pathBoxesRules, null, null);
//        WrapListWithMsg<BoxRules> msgBoxes2 = getBoxRulesListFast(fileRules[0]);//比poi严苛
        WrapListWithMsg<BoxRules> msgBoxes = null;
        try {
            msgBoxes = PoiUtiles.getBoxRulesList(null, null, fileRules[0]);
        } catch (IOException e) {
            e.printStackTrace();
        }

//        boolean tr = JSON.toJSONString(msgBoxes2).equals(JSON.toJSONString(msgBoxes));

        if (msgBoxes == null || msgBoxes.getErrMsg() != null && msgBoxes.getErrMsg().length() > 2) {//格式校验有误
            return ResultGenerator.genFailResult("解析箱规文件失败", msgBoxes.getErrMsg());
        }


        Map boxes = msgBoxes.getListData().stream().collect(Collectors.groupingBy(BoxRules::getSKU));

        boolean t = true;

        List<String> resMsg = new ArrayList<>();

        msgDepots.getListData().forEach(new Consumer<DepotRes>() {
            @Override
            public void accept(DepotRes depotRes) {

                List<BoxRules> boxRules = (List<BoxRules>) boxes.get(depotRes.getSKU());
                if (boxRules == null) {//>>箱规咩有包含的
                    boolean t = true;
                    resMsg.add("箱规未包含：" + depotRes.getSheetName() + ">" + depotRes.getSKU());
                }
                if (boxRules != null && boxRules.size() > 0) {
                    BoxRules boxRules1 = boxRules.get(0);

                    depotRes.setfSize(boxRules1.getfSize());
                    depotRes.setPerBox(boxRules1.getPerBox());

                    depotRes.setWeight_kg(boxRules1.getbWeight());
                    depotRes.setWidth_cm(boxRules1.getbWidth());
                    depotRes.setLength_cm(boxRules1.getbLength());
                    depotRes.setHeight_cm(boxRules1.getbHeight());

//                    depotRes.setWeight_lb(boxRules1.getbWeight());
//                    depotRes.setWidth_in(boxRules1.getbWidth());
//                    depotRes.set(boxRules1.getbLength());
//                    depotRes.setHeight_cm(boxRules1.getbHeight());

                    depotRes.actionComputeActual();
//                    depotRes.getWeight_lb();
//                    depotRes.getWidth_in();
//                    depotRes.getLength_in();
//                    depotRes.getHeight_in();
//
//                    depotRes.getAMOUNT_actual_fast();
//                    depotRes.getAMOUNT_actual_slow();
                }
            }
        });
        Map resDepotMap = msgDepots.getListData().stream().collect(Collectors.groupingBy(DepotRes::getSheetName));
        // 使用TreeMap对resDepotMap按key值升序排序
        TreeMap<String, List<DepotRes>> sortedResDepotMap = new TreeMap<>(resDepotMap);

        String date = CommonUtils.getStringMonthAndDay();
        folderToken = date + "上传亚马逊-" + folderToken;

        for (int i = 0; i < 2; i++) {
            try {
                generateExcelSingleWay(sortedResDepotMap, folderToken, i == 0);
            } catch (IOException e) {
                e.printStackTrace();
                return ResultGenerator.genFailResult("快/慢渠道分类生成失败");
            }
        }

        Set<String> tabStrs = sortedResDepotMap.keySet();
        boolean tt = true;
        List<String> resPaths = new ArrayList<>();
        String finalFolderToken = folderToken;
        tabStrs.forEach(new Consumer<String>() {
            @Override
            public void accept(String s) {
                try {
                    for (int i = 0; i < 2; i++) {
                        String path = writeExcelToTemplate(i == 0, isCM, sortedResDepotMap, s, finalFolderToken);
                        resPaths.add(path);
                    }
                } catch (IOException | InvalidFormatException e) {
                    e.printStackTrace();
                }

            }
        });

        //复制赋予多少备货逻辑
        try {
            String res = generateExcelMoreOrLess(fileBeihuo, folderToken, lastInfos);
        } catch (IOException e) {
            e.printStackTrace();
        }

        String pSrc = realPathExcel + C_ResultExcelFloder + "/" + folderToken;
        String targetPath = realPathExcel + C_ResultExcelFloder + "/" + folderToken + ".zip";

        try {
            ZipUtils.toZip(pSrc, targetPath, true);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return ResultGenerator.genFailResult("压缩文件生成失败");
        }

        String backPath = C_ResultExcelFloder + "/" + folderToken + ".zip";

        JSONObject jsonObject = new JSONObject();

        jsonObject.put("path", backPath);

        jsonObject.put("msg", resMsg.toArray());

        return ResultGenerator.genSuccessResult(jsonObject);
    }


    public String generateExcelMoreOrLess(File fileBeihuo, String folderToken, Map<String, List<DepotRes>> lastInfos) throws IOException {

        XSSFWorkbook workbook = null;
        try {
            workbook = new XSSFWorkbook(fileBeihuo);
        } catch (InvalidFormatException e) {
            e.printStackTrace();
        }

        XSSFCellStyle style = workbook.createCellStyle();
        // 设置水平居中
        style.setAlignment(HorizontalAlignment.CENTER);
        // 设置垂直居中
        style.setVerticalAlignment(VerticalAlignment.CENTER);

        XSSFFormulaEvaluator formulaEvaluator =
                workbook.getCreationHelper().createFormulaEvaluator();

        int sheets = workbook.getNumberOfSheets();
        for (int i = 0; i < sheets; i++) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(i); // 获取工作表

            int rows = sheet.getLastRowNum();
            int startRow = 0;
            for (int j = 0; j < rows + 1; j++) {

                Row inRow = sheet.getRow(j);
                if (inRow != null) {
                    Cell inCell = inRow.getCell(0);
                    if (inCell != null) {
                        //'M' 'N'
                        if (inCell.getStringCellValue().contains("SKU")) {
                            Cell cellMore = inRow.createCell('L' - 'A');
                            Cell cellLess = inRow.createCell('M' - 'A');
                            cellMore.setCellValue("多准备箱数");
                            cellLess.setCellValue("缺少箱数");
                            startRow = j;
                        } else {

                            Cell inCellPlan = inRow.getCell(8);

                            String inCellSKU = inRow.getCell(0).getStringCellValue();

//                            if (inCellPlan != null && inCellPlan.getStringCellValue().compareTo("0") > 0) {
                            if (inCellPlan != null && inCellPlan.getNumericCellValue() > 0) {
                                Cell cellMore = inRow.createCell('L' - 'A');
                                Cell cellLess = inRow.createCell('M' - 'A');

//                                淡蓝色：#ADD8E6，（173,216,230）
//                                淡红色：#F08080，（240,128,128）
//
                                int indexRow = j + 1;
                                try {
                                    String formula = "IF(K" + indexRow + "-SUM(E" + indexRow + "+F" + indexRow + ")<0,0,K" + indexRow + "-SUM(E" + indexRow + "+F" + indexRow + "))";
                                    cellMore.setCellFormula(formula);

                                    double more = (int) formulaEvaluator.evaluate(cellMore).getNumberValue();
                                    if (more > 0) {

                                        XSSFCellStyle styleIn = workbook.createCellStyle();//XSSFCellStyle才可自由设置颜色
                                        styleIn.cloneStyleFrom(style);
                                        XSSFColor myColor_err = new XSSFColor();
                                        byte[] byteColor_err = new byte[]{(byte) 173, (byte) 216, (byte) 230};

//                        myColor.setARGBHex("fffcc5c9");
                                        myColor_err.setRGB(byteColor_err);

//                        styleIn.setFillForegroundColor(IndexedColors.TEAL.getIndex());
                                        styleIn.setFillForegroundColor(myColor_err);
                                        styleIn.setFillPattern(FillPatternType.SOLID_FOREGROUND);

                                        cellMore.setCellStyle(styleIn);
                                    } else {
                                        cellMore.setCellStyle(style);
                                    }

                                } catch (FormulaParseException | IllegalStateException e) {
                                    e.printStackTrace();
                                }


                                try {
                                    String formula = "IF(K" + indexRow + "-SUM(E" + indexRow + "+F" + indexRow + ")<0,-K" + indexRow + "+SUM(E" + indexRow + "+F" + indexRow + "),0)";
                                    cellLess.setCellFormula(formula);

                                    double less = (int) formulaEvaluator.evaluate(cellLess).getNumberValue();
                                    if (less > 0) {
                                        XSSFCellStyle styleIn = workbook.createCellStyle();//XSSFCellStyle才可自由设置颜色
                                        styleIn.cloneStyleFrom(style);
                                        XSSFColor myColor_err = new XSSFColor();
                                        byte[] byteColor_err = new byte[]{(byte) 240, (byte) 128, (byte) 128};

                                        //US-GG-763-BLACK-47
                                        // US-HMT-CB501-BK-09_5


                                        if (lastInfos != null && lastInfos.get(inCellSKU) != null && lastInfos.get(inCellSKU).get(0) != null) {
                                            if (lastInfos.get(inCellSKU).get(0).getDelt() < 0) {
                                                byteColor_err = ColorDeeper(byteColor_err);
//                                                byteColor_err = new byte[]{(byte) 128, (byte) 128, (byte) 128};
//                                                byteColor_err = new byte[]{(byte) 128, (byte) 128, (byte) 128};

                                            }

                                        }


//                        myColor.setARGBHex("fffcc5c9");
                                        myColor_err.setRGB(byteColor_err);
                                        styleIn.setFillForegroundColor(myColor_err);
                                        styleIn.setFillPattern(FillPatternType.SOLID_FOREGROUND);

                                        cellLess.setCellStyle(styleIn);
                                    } else {
                                        cellLess.setCellStyle(style);
                                    }

//                                    formulaEvaluator.evaluate(cellLess);
                                } catch (FormulaParseException | IllegalStateException e) {
                                    e.printStackTrace();
                                }

                            }


                        }

                    } else { //总计

                        //挨着 L M
                        Cell inCellAll = inRow.getCell(4);
                        if (inCellAll != null) {
                            Cell cellMoreAll = inRow.createCell('L' - 'A');
                            Cell cellLessAll = inRow.createCell('M' - 'A');

                            int indexRow = j;
                            try {
                                String formula = "SUM(L" + (startRow + 2) + ":L" + indexRow + ")";
                                cellMoreAll.setCellFormula(formula);
                                double more = (int) formulaEvaluator.evaluate(cellMoreAll).getNumberValue();
                            } catch (FormulaParseException | IllegalStateException e) {
                                e.printStackTrace();
                            }

                            try {
                                String formula = "SUM(M" + (startRow + 2) + ":M" + indexRow + ")";
                                cellLessAll.setCellFormula(formula);
                                double less = (int) formulaEvaluator.evaluate(cellLessAll).getNumberValue();

                                if (less > 0) {
                                    XSSFCellStyle styleIn = workbook.createCellStyle();//XSSFCellStyle才可自由设置颜色
                                    styleIn.cloneStyleFrom(style);
                                    XSSFColor myColor_err = new XSSFColor();
                                    byte[] byteColor_err = new byte[]{(byte) 240, (byte) 128, (byte) 128};
//                        myColor.setARGBHex("fffcc5c9");
                                    myColor_err.setRGB(byteColor_err);

//                        styleIn.setFillForegroundColor(IndexedColors.TEAL.getIndex());
                                    styleIn.setFillForegroundColor(myColor_err);
                                    styleIn.setFillPattern(FillPatternType.SOLID_FOREGROUND);

                                    cellLessAll.setCellStyle(styleIn);
                                } else {
                                    cellLessAll.setCellStyle(style);
                                }


                            } catch (FormulaParseException | IllegalStateException e) {
                                e.printStackTrace();
                            }


                        }

                    }


                }


            }
        }

        formulaEvaluator.evaluateAll();

        File fileDir = new File((realPathExcel + C_ResultExcelFloder + "/" + folderToken));
        if (!fileDir.exists()) {
            fileDir.mkdirs();
            Lg.i("saveToAssets", "文件的路径不存在，创建:", fileDir.getPath());

        }

        String timeCreated = "-" + CommonUtils.getStringMonthAndDay();

        String pathBack = fileDir.getPath() + "/" + "多备货-少备货比对" + timeCreated + ".xlsx";
        FileOutputStream out = new FileOutputStream(pathBack);
        workbook.write(out);
        out.close();
        workbook.close();
        return pathBack;
    }

    public byte[] ColorDeeper(byte[] by) {

        float decrease = 0.2f;

        // 获取原始颜色的分量值
        int red = by[0] & 0xFF;
        int green = by[1] & 0xFF;
        int blue = by[2] & 0xFF;
        // 计算新的颜色分量值
        int newRed = (int) (red * (1 - decrease));
        int newGreen = (int) (green * (1 - decrease));
        int newBlue = (int) (blue * (1 - decrease));
        // 创建一个新的颜色对象
        //(newRed, newGreen, newBlue)
        return new byte[]{(byte) newRed, (byte) newGreen, (byte) newBlue};
    }


    private static int NULL_Name = 0;

    //M-仓库备货表 > 快慢渠道
    public String generateExcelSingleWay(TreeMap dataMap, String folderToken, boolean isFast) throws IOException {

        NULL_Name = 0;
        System.out.println("开始合成");
        // 创建一个工作簿对象
        //SXSSFWorkbook 基于流，更省内存，但不支持公式求值！！！
        XSSFWorkbook workbook = new XSSFWorkbook();
        Set<String> tabStrs = dataMap.keySet();
        boolean tt = true;
        tabStrs.forEach(new Consumer<String>() {
            @Override
            public void accept(String s) {

                generateSheetWays(workbook, dataMap, s, isFast);
                NULL_Name = NULL_Name + 1;

            }
        });


        File fileDir = new File((realPathExcel + C_ResultExcelFloder + "/" + folderToken));
        if (!fileDir.exists()) {
            fileDir.mkdirs();
            Lg.i("saveToAssets", "文件的路径不存在，创建:", fileDir.getPath());

        }

        String outFileName = isFast ? "各个电铺发美森数量" : "各个电铺发慢船数量";

        String trueFileName = CommonUtils.getStringMonth() + "-" + outFileName + ".xlsx";

        String path = fileDir.getPath() + '/' + trueFileName;

        String pathBack = C_ResultExcelFloder + "/" + trueFileName;
        // 将工作簿对象写入到文件中
        FileOutputStream out = new FileOutputStream(path);

        workbook.write(out);
        out.close();
        workbook.close();
        System.out.println("合成完成，文件位置：" + path);
        return pathBack;
    }


    //M-单个默认fast，渠道1
    public org.apache.poi.ss.usermodel.Sheet generateSheetWays(XSSFWorkbook workbook,
                                                               TreeMap<String, List<DepotRes>> dataMap,
                                                               String name, boolean isFast) {
//        Set<String> tabStrs = dataMap.keySet();

        //The workbook already contains a sheet named '表名构造失败'>>不能重名
        String sheetName = "表名构造失败" + NULL_Name;
        if (name != null) {
            sheetName = name;
        }
        XSSFSheet sheet = workbook.createSheet(sheetName);
        XSSFCellStyle style = workbook.createCellStyle();
        // 设置水平居中
        style.setAlignment(HorizontalAlignment.CENTER);
        // 设置垂直居中
        style.setVerticalAlignment(VerticalAlignment.CENTER);


//        final int[] lastMax = {0};
        final int[] lastLen = {0};//缓存一组列表长度，便于计算总计

//        List<String> mTitles = sheetName.contains("US") ? C_titleM : C_titleKh;
        List<String> mTitles = C_titleMS;

        List<DepotRes> depotRes = dataMap.get(name).stream().filter(new Predicate<DepotRes>() {
            @Override
            public boolean test(DepotRes depotRes) {

                if (isFast) {
                    if (depotRes.getAMOUNT_actual_fast() == 0) {
                        return false;
                    }
                } else {
                    if (depotRes.getAMOUNT_actual_slow() == 0) {
                        return false;
                    }
                }

                return true;
            }
        }).collect(Collectors.toList());

        int len = depotRes.size();

        for (int i = 0; i <= len; i++) {

            //US-HMT-004-White-10_5-1
            //US-HMT-001-White05_5
            if (i == 0) {//title

                XSSFCellStyle styleIn = workbook.createCellStyle();//XSSFCellStyle才可自由设置颜色
                styleIn.cloneStyleFrom(style);
                XSSFColor myColor = new XSSFColor();
                byte[] byteColor = new byte[]{(byte) 252, (byte) 197, (byte) 201};
//                        myColor.setARGBHex("fffcc5c9");
                myColor.setRGB(byteColor);

//                        styleIn.setFillForegroundColor(IndexedColors.TEAL.getIndex());
                styleIn.setFillForegroundColor(myColor);
                styleIn.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                // 创建一个行对象，表示第一行
                XSSFRow row = sheet.createRow(i);
                row.setHeight((short) (25 * 20)); // 25 像素 = 25 * 20 缇
                mTitles.forEach(new Consumer<String>() {
                    @Override
                    public void accept(String s) {
                        String title = s;
                        //美森箱数/慢船箱数
                        if (s.contains("美森箱数")) {
                            if (!isFast) {
                                title = "慢船箱数";
                            }
                        }

                        XSSFCell cell0 = row.createCell(mTitles.indexOf(s));
                        cell0.setCellValue(title);
                        cell0.setCellStyle(styleIn);

                    }
                });


            } else {
                DepotRes depotRes1 = depotRes.get(i - 1);
                if (isFast) {
                    if (depotRes1.getAMOUNT_actual_fast() == 0) {
                        continue;
                    }
                } else {
                    if (depotRes1.getAMOUNT_actual_slow() == 0) {
                        continue;
                    }
                }

                XSSFRow row = sheet.createRow(i);


                XSSFCell cell0 = row.createCell(0);
                cell0.setCellValue(depotRes1.getSKU());
                cell0.setCellStyle(style);
                XSSFCell cell1 = row.createCell(1);
                cell1.setCellValue(depotRes1.getfSize());
                cell1.setCellStyle(style);
                XSSFCell cell2 = row.createCell(2);
                cell2.setCellValue(depotRes1.getPerBox());
                cell2.setCellStyle(style);
                if (isFast) {
                    XSSFCell cell3 = row.createCell(3);
                    cell3.setCellValue(depotRes1.getAMOUNT_actual_fast());//fast实际
                    cell3.setCellStyle(style);
                    XSSFCell cell4 = row.createCell(4);
//                    cell4.setCellValue(depotRes1.getAMOUNT_shoes_fast());//总数量 只能直取！！>>不能直取！！
                    cell4.setCellValue(depotRes1.getAMOUNT_actual_fast() * depotRes1.getPerBox());//总数量
                    cell4.setCellStyle(style);

                } else {
                    XSSFCell cell3 = row.createCell(3);
                    cell3.setCellValue(depotRes1.getAMOUNT_actual_slow());//fast实际
                    cell3.setCellStyle(style);
                    XSSFCell cell4 = row.createCell(4);
//                    cell4.setCellValue(depotRes1.getAMOUNT_shoes_slow());//总数量
                    cell4.setCellValue(depotRes1.getAMOUNT_actual_slow() * depotRes1.getPerBox());//总数量
                    cell4.setCellStyle(style);
                }

                XSSFCell cell5 = row.createCell(5);
                cell5.setCellValue(depotRes1.getWeight_kg());
                cell5.setCellStyle(style);
                XSSFCell cell6 = row.createCell(6);
                cell6.setCellValue(depotRes1.getWidth_cm());
                cell6.setCellStyle(style);
                XSSFCell cell7 = row.createCell(7);
                cell7.setCellValue(depotRes1.getLength_cm());
                cell7.setCellStyle(style);
                XSSFCell cell8 = row.createCell(8);
                cell8.setCellValue(depotRes1.getHeight_cm());
                cell8.setCellStyle(style);

                //lb

                XSSFCell cell9 = row.createCell(9);
                cell9.setCellValue(depotRes1.getWeight_lb());
                cell9.setCellStyle(style);
                XSSFCell cell10 = row.createCell(10);
                cell10.setCellValue(depotRes1.getWidth_in());
                cell10.setCellStyle(style);
                XSSFCell cell11 = row.createCell(11);
                cell11.setCellValue(depotRes1.getLength_in());
                cell11.setCellStyle(style);
                XSSFCell cell12 = row.createCell(12);
                cell12.setCellValue(depotRes1.getHeight_in());
                cell12.setCellStyle(style);

            }
        }

        PoiUtiles.adjustAutoWidth(sheet, mTitles.size());
//        dataMap.clear();//清缓存
        return sheet;
    }


    public String writeExcelToTemplate(boolean isFast, boolean isCM, TreeMap<String, List<DepotRes>> dataMap, String shopName, String folderToken) throws IOException, InvalidFormatException {
//        Set<String> strings = dataMap.keySet();

        String srcTemplate = "excel/templateAZ_kg.xlsx";
        if (!isCM) {
            srcTemplate = "excel/templateAZ_lb.xlsx";
        }
        org.springframework.core.io.Resource resource = new ClassPathResource(srcTemplate);
//        File file = resource.getFile();
        InputStream fileInputStreamIN = resource.getInputStream();

        XSSFWorkbook workbook = new XSSFWorkbook(fileInputStreamIN);
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(2); // 获取第二个工作表
//        Row newRow = sheet.createRow(8); // 在第一行创建一个新的行对象

//        String shopName = (String) strings.toArray()[0];

        List<DepotRes> resList = dataMap.get(shopName).stream().filter(new Predicate<DepotRes>() {
            @Override
            public boolean test(DepotRes depotRes) {

                if (isFast) {
                    if (depotRes.getAMOUNT_actual_fast() == 0) {
                        return false;
                    }
                } else {
                    if (depotRes.getAMOUNT_actual_slow() == 0) {
                        return false;
                    }
                }

                return true;
            }
        }).collect(Collectors.toList());

        int lenRows = resList.size();

        for (int i = 0; i < lenRows; i++) {

//            if (isFast) {
//                if (resList.get(i).getAMOUNT_actual_fast() == 0) {
//                    continue;
//                }
//            } else {
//                if (resList.get(i).getAMOUNT_actual_slow() == 0) {
//                    continue;
//                }
//            }

            Row newRow = sheet.createRow(8 + i);

            for (int j = 0; j < 10; j++) {
                newRow.createCell(j);
                if (j == 0) {
                    newRow.getCell(j).setCellValue(resList.get(i).getSKU());
                }
                if (j == 1) {
                    if (isFast) {
//                        newRow.getCell(j).setCellValue(resList.get(i).getAMOUNT_shoes_fast());//快的实际鞋数>>>数量只能直取！>>不能直取！！！
                        newRow.getCell(j).setCellValue(resList.get(i).getAMOUNT_actual_fast() * resList.get(i).getPerBox());//快的实际鞋数
                    } else {
                        newRow.getCell(j).setCellValue(resList.get(i).getAMOUNT_actual_slow() * resList.get(i).getPerBox());
                    }

                }
                if (j == 2 || j == 3) {
                    newRow.getCell(j).setCellValue("Seller");
                }
                if (j == 4) {
                    newRow.getCell(j).setCellValue(resList.get(i).getPerBox());
                }
                if (j == 5) {
                    if (isFast) {
                        newRow.getCell(j).setCellValue(resList.get(i).getAMOUNT_actual_fast());
                    } else {
                        newRow.getCell(j).setCellValue(resList.get(i).getAMOUNT_actual_slow());
                    }

                }
                if (isCM) {
                    //6789 长宽高重
                    if (j == 6) {
                        newRow.getCell(j).setCellValue(resList.get(i).getLength_cm());
                    }
                    if (j == 7) {
                        newRow.getCell(j).setCellValue(resList.get(i).getWidth_cm());
                    }
                    if (j == 8) {
                        newRow.getCell(j).setCellValue(resList.get(i).getHeight_cm());
                    }
                    if (j == 9) {
                        newRow.getCell(j).setCellValue(resList.get(i).getWeight_kg());
                    }
                } else {
                    //6789 长宽高重
                    if (j == 6) {
                        newRow.getCell(j).setCellValue(resList.get(i).getLength_in());
                    }
                    if (j == 7) {
                        newRow.getCell(j).setCellValue(resList.get(i).getWidth_in());
                    }
                    if (j == 8) {
                        newRow.getCell(j).setCellValue(resList.get(i).getHeight_in());
                    }
                    if (j == 9) {
                        newRow.getCell(j).setCellValue(resList.get(i).getWeight_lb());
                    }
                }


            }
        }

        String folder = "美森";
        if (isFast) {
            shopName = "美森-" + shopName;
            folder = "美森";
        } else {

            shopName = "慢船-" + shopName;
            folder = "慢船";
        }
        File fileDir = new File((realPathExcel + C_ResultExcelFloder + "/" + folderToken + "/" + folder));
        if (!fileDir.exists()) {
            fileDir.mkdirs();
            Lg.i("saveToAssets", "文件的路径不存在，创建:", fileDir.getPath());

        }

        String timeCreated = "-" + CommonUtils.getStringMonthAndDay();

        String pathBack = fileDir.getPath() + "/" + shopName + timeCreated + ".xlsx";
        FileOutputStream out = new FileOutputStream(pathBack);
        workbook.write(out);
        out.close();
        workbook.close();
        if (fileInputStreamIN != null) {
            fileInputStreamIN.close();
        }

        return pathBack;
    }

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


    //发美森计划箱数 发慢船计划箱数
    private static List<String> staticKeys = new ArrayList<>(Arrays.asList("计划箱数", "备货数量"));
    private static List<String> staticKeysTmp = new ArrayList<>(Arrays.asList("计划箱数", "备货数量"));


    public WrapListWithMsg<DepotRes> getFromBeiHuoFast(File fileIn) throws IOException {

        return getFromBeiHuoFastFilter(fileIn, true);
    }


    public WrapListWithMsg<DepotRes> getFromBeiHuoFastFilter(File fileIn, boolean isWithPrepare) throws IOException {

        ReadableWorkbook wb = new ReadableWorkbook(fileIn);

        WrapListWithMsg<DepotRes> msgBoxes = new WrapListWithMsg<>();
        List<DepotRes> stockDetails = new ArrayList<>();
        List<String> errStrs = new ArrayList<>();
        msgBoxes.setListData(stockDetails);//放前面放后面一样？地址指向？ List<String> 不行？

        Stream<Sheet> sheets = wb.getSheets(); //获取Workbook中sheet的个数


        sheets.forEach(sheet -> {
            String name = sheet.getName(); //获取每个sheet的名称
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


                            // 计划发 美森/慢船 的鞋数，只能直取表中数据，不能用箱数和装箱率来计算！！！>>>不能直取！！
                            List<String> keysNeedShoes = new ArrayList<>();//地址 A、B...

                            row0.stream().forEach(cell -> {
                                if (cell == null) {
                                    return;
                                }
                                Object cellData = cell.getValue();
                                if (cellData instanceof String) {
                                    String colKey = (String) cellData;
                                    staticKeys.forEach(s -> {
                                        if (colKey.contains(s)) {
                                            String address = String.valueOf(cell.getAddress());
                                            String firstChar = String.valueOf(address.charAt(0));
                                            if (!keysNeed.contains(firstChar)) {
                                                keysNeed.add(firstChar);
                                            }
                                            if (staticKeysTmp.contains(s)) {
                                                staticKeysTmp.remove(s);
                                            }

                                        }

                                    });
                                    //发美森数量 发慢船数量
                                    //发美森数量 （美森计划数量）
                                    if (colKey.contains("美森") && colKey.contains("数量") && !colKey.contains("箱")) {
                                        String address = String.valueOf(cell.getAddress());
                                        String firstChar = String.valueOf(address.charAt(0));
                                        if (!keysNeedShoes.contains(firstChar)) {
                                            keysNeedShoes.add(firstChar);
                                        }
                                    }
                                    if (colKey.contains("慢船") && colKey.contains("数量") && !colKey.contains("箱")) {
                                        String address = String.valueOf(cell.getAddress());
                                        String firstChar = String.valueOf(address.charAt(0));
                                        if (!keysNeedShoes.contains(firstChar)) {
                                            keysNeedShoes.add(firstChar);
                                        }
                                    }


                                }
                            });

                            //美国才有两渠道，日本、欧洲一个渠道
//                            if (keysNeed.size() != 2) {//只有两个关键列
//                                errStrs.add(name + ">未检测到关键列：" + staticKeysTmp.toString());
//                                msgBoxes.setErrMsg(errStrs.toString());//放前面放后面一样？地址指向？
//                            }
                            if (staticKeysTmp.size() > 0) {//缺失关键列
                                errStrs.add(name + ">未检测到关键列：" + staticKeysTmp.toString());
                                msgBoxes.setErrMsg(errStrs.toString());//放前面放后面一样？地址指向？
                            }

                            Pattern pattern = Pattern.compile("A\\d+");
                            //排除首行
                            for (int i = 1; i < size; i++) {
                                org.dhatim.fastexcel.reader.Row row = rr.get(i);
                                DepotRes details = new DepotRes();
                                details.setSheetName(name);
                                //底部空一大片又突然出现一小格的情况,或者隐藏行
                                if (row == null || row.getCellCount() < 2) {
                                    boolean tt = true;
                                    continue;
                                }
                                Optional<org.dhatim.fastexcel.reader.Cell> fCell = row.getOptionalCell(0);
//                                    org.dhatim.fastexcel.reader.Cell fCell = row.getFirstNonEmptyCell().get();
                                if (!fCell.isPresent()
                                        || fCell.get().getText().isEmpty()
                                        || fCell.get().getText().toUpperCase().contains("SKU")) {//中间可能还有标题
                                    continue;
                                }
//                                final boolean[] isActualEmpty = {false};//要有实际备货的才输出
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
                                                errStrs.add(name + ">" + address + ":类型错误");
                                            }
                                        }

                                    } else if (address.startsWith("B")) {//size
//                                        String size1 = String.valueOf(cellData);
                                        if (cellData == null) {
                                            errStrs.add(name + ">" + address + ":格式错误");
                                        } else {
                                            String rFast = String.valueOf(cellData);
//                            rules.setbWidth(Float.parseFloat(width));

//                                            Float rFast = floatFormat(fastNum);
                                            if (rFast != null) {
//                                                 rFastInt = Math.round(rFast);
                                                details.setfSize(rFast);
                                            } else {
                                                errStrs.add(name + ">" + address + ":格式错误");
                                            }
                                        }


                                    } else {//默认美森在前,可能有AA BB这种情况,严格判断
                                        if (address.contains("K128")) {

                                            boolean t = true;
                                        }

                                        keysNeed.forEach(new Consumer<String>() {
                                            @Override
                                            public void accept(String s) {
                                                Pattern patternP = Pattern.compile(s + "\\d+");
                                                if (patternP.matcher(address).matches()) {
                                                    if (cellData == null) {
//                                                        errStrs.add(name + ">" + address + ":数据为空");
//                                                        isActualEmpty[0] = true;
                                                    } else {
                                                        String fastNum = String.valueOf(cellData);
                                                        Float rFast = floatFormat(fastNum);
                                                        if (rFast != null) {
                                                            int rFastInt = Math.round(rFast);//箱数

                                                            if (keysNeed.indexOf(s) == 0) {//第一个，肯定有
                                                                details.setAMOUNT_boxes_fast(rFastInt);
                                                            } else if (keysNeed.indexOf(s) == keysNeed.size() - 1) {//最后一个肯定有，实际
                                                                details.setAMOUNT_actual(rFastInt);
                                                                Lg.i("实际备货>in", s + ">" + address, rFastInt);
                                                            } else {
                                                                details.setAMOUNT_boxes_slow(rFastInt);
                                                            }

                                                        } else {
                                                            errStrs.add(name + ">" + address + ":格式错误");
                                                        }
                                                    }
                                                }

                                            }
                                        });

                                        keysNeedShoes.forEach(new Consumer<String>() {
                                            @Override
                                            public void accept(String s) {
                                                Pattern patternP = Pattern.compile(s + "\\d+");
                                                if (patternP.matcher(address).matches()) {
                                                    if (cellData == null) {
//                                                        errStrs.add(name + ">" + address + ":数据为空");
//                                                        isActualEmpty[0] = true;
                                                    } else {
                                                        String fastNum = String.valueOf(cellData);
                                                        Float rFastShoes = floatFormat(fastNum);
                                                        if (rFastShoes != null) {
                                                            int rFastInt = Math.round(rFastShoes);//箱数

                                                            if (keysNeedShoes.indexOf(s) == 0) {//第一个，美森
                                                                details.setAMOUNT_shoes_fast(rFastInt);
                                                            } else {
                                                                details.setAMOUNT_shoes_slow(rFastInt);
                                                            }

                                                        } else {
                                                            errStrs.add(name + ">" + address + ":格式错误");
                                                        }
                                                    }
                                                }
                                            }
                                        });


                                    }
                                    if (isWithPrepare) {//有备货的才输出
                                        if (!stockDetails.contains(details) && details.getAMOUNT_actual() >= 0 && !(details.getAMOUNT_actual() == 0 && details.getAMOUNT_boxes_all() == 0)) {
                                            stockDetails.add(details);
                                            Lg.i("实际备货", String.valueOf(details.getAMOUNT_actual()), stockDetails.size());
                                        }
                                    } else {//比对要所有？
                                        if (!stockDetails.contains(details) && !(details.getAMOUNT_actual() == 0 && details.getAMOUNT_boxes_all() == 0)) {
                                            stockDetails.add(details);
                                            Lg.i("实际备货-all", String.valueOf(details.getAMOUNT_actual()), stockDetails.size());
                                        }
                                    }
                                });

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

    //上一周期的备货信息
    //
    public Map getLocalJson() {
        JSONObject ob = CommonUtils.readJsonFromFile(realPathExcel + C_BaseConfigFloder + '/' + C_BaseConfig_name);

        WrapListWithMsg<DepotRes> msgDepots = null;
        if (ob == null) {
            //信息路径不存在
            return null;
        }
        String path = ob.getString(CONFIG_KEY.KEY_beihuo_path.getValue());
        try {
            msgDepots = getFromBeiHuoFastFilter(new File(path), false);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (msgDepots == null) {
            return null;
        }

        List<DepotRes> deltDepots = msgDepots.getListData().stream().filter(new Predicate<DepotRes>() {
            @Override
            public boolean test(DepotRes depotRes) {
                //总计划发货直接读取！！！
//                int delt = depotRes.getAMOUNT_actual() - (depotRes.getAMOUNT_boxes_fast() + depotRes.getAMOUNT_boxes_slow());
                int delt = depotRes.getAMOUNT_actual() - depotRes.getAMOUNT_boxes_all();
                depotRes.setDelt(delt);
                if (delt != 0) {
                    return true;
                }
                return false;
            }
        }).collect(Collectors.toList());
        Map map = deltDepots.stream().collect(Collectors.groupingBy(DepotRes::getSKU));

        return map;
    }

    public boolean saveLastBeihuo(String path) {

        //保存上一个周期的记录，来比较差值
        File fileDir = new File((realPathExcel + C_BaseConfigFloder));
        if (!fileDir.exists()) {
            fileDir.mkdirs();
            Lg.i("C_BaseConfigFloder", "文件的路径不存在，创建:", fileDir.getPath());

        }
        String configPath = fileDir.getPath() + '/' + C_BaseConfig_name;
        com.alibaba.fastjson2.JSONObject obConfig = new com.alibaba.fastjson2.JSONObject();
        obConfig.put(CONFIG_KEY.KEY_beihuo_path.getValue(), path);
        return CommonUtils.writeJsonToFile(obConfig, configPath);

    }


}
