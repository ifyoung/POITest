package com.magicair.webpj.AFurui;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.magicair.webpj.AFurui.model.*;
import com.magicair.webpj.AFurui.model.wrap.WrapListWithMsg;
import com.magicair.webpj.core.Result;
import com.magicair.webpj.core.ResultGenerator;
import com.magicair.webpj.utils.Lg;
import com.magicair.webpj.utils.StringUtils;
import org.apache.poi.hssf.usermodel.*;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.formula.FormulaParseException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.springframework.beans.factory.annotation.Value;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.magicair.webpj.AFurui.ConstantFu.staticKeys;
import static com.magicair.webpj.AFurui.ConstantFu.staticKeysTmp;

public class PoiUtiles {


    private static List<TreeMap> dataMapsCache;

    @Value("${web.excel-path}")
    static String realPathExcel;

    //构造，并返回结果路径 for test
    public static String generateExcel(TreeMap<String, List<DepotRes>> dataMap, String outFileName) throws IOException {
        Set<String> tabStrs = dataMap.keySet();

        Object[] array = tabStrs.toArray();
        String firstStr = (String) array[0]; // 取出第一个元素
        String[] tags = firstStr.split("-");
        String sheetName = "表名构造失败";
        if (tags.length > 1) {
            sheetName = tags[0] + "-" + tags[1];
        }

        String title = "SKU,Size,FNSKU,装箱率,发美森计划箱数,发慢船计划箱数,发美森数量,发慢船数量,总计划发货箱数,备注,仓库实际备货数量";
        List<String> title1 = Arrays.asList(title.split(","));
        // 创建一个工作簿对象
        XSSFWorkbook workbook = new XSSFWorkbook();

        XSSFSheet sheet = workbook.createSheet(sheetName);


        // 创建一个单元格样式对象
//        CellStyle style = workbook.createCellStyle();
        XSSFCellStyle style = workbook.createCellStyle();
        // 设置水平居中
        style.setAlignment(HorizontalAlignment.CENTER);
        // 设置垂直居中
        style.setVerticalAlignment(VerticalAlignment.CENTER);


        final int[] lastMax = {0};

        final int[] lastLen = {0};//缓存一组列表长度，便于计算总计
        List<Integer> lastMaxTmp = new ArrayList<>();//空行
        tabStrs.forEach(new Consumer<String>() {
            @Override
            public void accept(String s) {

                // 创建一个工作表对象，并命名为"Sheet1"
//                XSSFSheet sheet = workbook.createSheet(s);
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
                            XSSFRow row = sheet.createRow(i);
                            row.setHeight((short) (25 * 20)); // 25 像素 = 25 * 20 缇
                            title1.forEach(new Consumer<String>() {
                                @Override
                                public void accept(String s) {
                                    XSSFCell cell0 = row.createCell(title1.indexOf(s));
                                    cell0.setCellValue(s);
                                    cell0.setCellStyle(styleIn);

                                }
                            });
                            //style这儿改了会影响后面，导致所有行的背景都变
//                            style.setFillForegroundColor(IndexedColors.BROWN.getIndex());
//                            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
//                            row.setRowStyle(style);
                        } else {
//                            sheet.shiftRows(i - 1, sheet.getLastRowNum(), 1);
//                            XSSFRow newRow = sheet.createRow(i);//空行

                            //总计
                            XSSFRow rowSum = sheet.createRow(i - 2);
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
                            XSSFRow row = sheet.createRow(i);
                            row.setHeight((short) (25 * 20)); // 25 像素 = 25 * 20 缇
                            title1.forEach(new Consumer<String>() {
                                @Override
                                public void accept(String s) {
                                    XSSFCell cell0 = row.createCell(title1.indexOf(s));
                                    cell0.setCellValue(s);
                                    cell0.setCellStyle(styleIn);

                                }
                            });
//                            System.out.println("Excel->merge>title" + i + "》" + lastMax[0]);

                        }


                    } else {
                        XSSFRow row = sheet.createRow(i);
                        DepotRes depotRes1 = depotRes.get(i - lastMax[0] - 1);
//                        Class<?> objClass = depotRes1.getClass();
//                        Field[] fields = objClass.getDeclaredFields();
                        JSONObject json = (JSONObject) JSON.toJSON(depotRes1); // 转换为JSONObject对象
                        Set<String> fields = json.keySet();
                        List<String> fList = new ArrayList<String>(fields); // 将set转换为list
//                        System.out.println("Excel->merge>" + depotRes1.getSKU() + "》" + i + "》" + lastMax);
                        //顺序会乱
//                        for (int j = 0; j < fList.size(); j++) {
//
//                            if (fList.get(j).equals("id") || fList.get(j).equals("group")) {
//                                continue;
//                            }
//
//                            XSSFCell cell0 = row.createCell(j);
//                            cell0.setCellValue(json.getString(fList.get(j)));
//
//                        }

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
                        cell4.setCellValue(depotRes1.getAMOUNT_boxes_fast());
                        cell4.setCellStyle(style);
                        XSSFCell cell5 = row.createCell(5);
                        cell5.setCellValue(depotRes1.getAMOUNT_boxes_slow());
                        cell5.setCellStyle(style);
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
                lastMax[0] = len + lastMax[0] + 3;//加一空行,加上总计
//                lastMaxTmp.add(lastMax[0]);

            }
        });


        //最后一组
        int lastRow = sheet.getLastRowNum();
        XSSFRow rowSum = sheet.createRow(lastRow + 1);
//        rowSum.setHeight((short) (25 * 20)); // 25 像素 = 25 * 20 缇
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
            String start = sumKeys[j] + (lastRow - lastLen[0] + 2);
            String end = sumKeys[j] + (lastRow + 1);
            cell0.setCellFormula("SUM(" + start + ":" + end + ")");
            formulaEvaluator.evaluate(cell0);
            cell0.setCellStyle(style);

//            System.out.println("Excel->merge>SUM" + lastRow + "lastLen>" + lastLen[0] + "start>" + start + "end>" + end);

        }

        adjustAutoWidth(sheet, title1.size());

//        String resFileName = outFileName + ".xlsx";

        String floder = "/Results";

        File fileDir = new File((realPathExcel + floder));
        if (!fileDir.exists()) {
            fileDir.mkdirs();
            Lg.i("saveToAssets", "文件的路径不存在，创建:", fileDir.getPath());

        }
        String trueFileName = System.currentTimeMillis() + outFileName + ".xlsx";

        String path = fileDir.getPath() + '/' + trueFileName;

        String pathBack = floder + "/" + trueFileName;
        // 将工作簿对象写入到文件中
        FileOutputStream out = new FileOutputStream(path);

        workbook.write(out);
        out.close();
        workbook.close();

        return pathBack;
    }

    //计算+生成 > 单个 for test
    public static Result excelActionMerge(String outFileName, InputStream input) {

        long t1 = System.currentTimeMillis();

        String pathBoxesRules1 = realPathExcel + "Furuian-test/初始数据/箱规.xlsx"; //US-TPS shop
        WrapListWithMsg<BoxRules> msgBoxes = null;//sheet表名 US-HMT，不包含颜色
        try {
            msgBoxes = getBoxRulesList(pathBoxesRules1, null, null);

            //[]
            if (msgBoxes != null && msgBoxes.getErrMsg() != null && msgBoxes.getErrMsg().length() > 2) {//格式校验有误
                return ResultGenerator.genFailResult("解析箱规文件失败", msgBoxes.getErrMsg());
            }

        } catch (IOException e) {
            e.printStackTrace();
            return ResultGenerator.genFailResult("解析箱规文件失败", e);
        }

        Map boxes = msgBoxes.getListData().stream().collect(Collectors.groupingBy(BoxRules::getSKU));

        Result sortedResDepotMap = inputConvertToMap(input, boxes);

        //US-HMT-004-White-10_5-1
        //US-HMT-001-White05_5
//        Set<String> strings = sortedResDepotMap.keySet();
        String path = null;
        try {
            path = generateExcel((TreeMap<String, List<DepotRes>>) sortedResDepotMap.getData(), outFileName);
        } catch (IOException e) {
            e.printStackTrace();
            return ResultGenerator.genFailResult("结果生成失败", e);
        }

//        DepotRes r = resList.get(5);
//        int rr = r.getAMOUNT_boxes_all();
        long t2 = System.currentTimeMillis() - t1;
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("path", path);
        jsonObject.put("time_c", t2);
        boolean t = true;
        return ResultGenerator.genSuccessResult(jsonObject);

    }

    //M-解析货件
    public static Result inputConvertToMap(InputStream input, Map boxes) {
        WrapListWithMsg<ShopDetails> msgDetails = null;//解析的sheet表名包含颜色
        try {
            msgDetails = getShopDetails(null, input, null);
            if (msgDetails != null && msgDetails.getErrMsg() != null && msgDetails.getErrMsg().length() > 2) {//格式校验有误
                input.close();
                return ResultGenerator.genFailResult("解析货件文件失败", msgDetails.getErrMsg());
            }
            input.close();
        } catch (IOException e) {
            e.printStackTrace();
            return ResultGenerator.genFailResult("解析货件文件失败", e);
        }

        //生成Excel 要按包含颜色的表名分组?>暂时不要，留行！
        List<DepotRes> resList = new ArrayList<>();

        msgDetails.getListData().forEach(new Consumer<ShopDetails>() {
            @Override
            public void accept(ShopDetails shopDetails) {

                List<BoxRules> boxRules = (List<BoxRules>) boxes.get(shopDetails.getSKU());

//                System.out.println("Excel->excelActionMerge>" + boxRules.size());
                if (boxRules == null) {//>>箱规咩有包含的
                    boolean t = true;
                }

                if (boxRules != null && boxRules.size() > 0) {
                    BoxRules boxRules1 = boxRules.get(0);
                    DepotRes depotRes = new DepotRes();
                    depotRes.setSKU(shopDetails.getSKU());
                    depotRes.setFNSKU(boxRules1.getFNSKU());
                    depotRes.setfSize(boxRules1.getfSize());
                    depotRes.setAMOUNT_shoes_fast(Integer.parseInt(shopDetails.getAMOUNT_shoes_fast()));
                    depotRes.setAMOUNT_shoes_slow(Integer.parseInt(shopDetails.getAMOUNT_shoes_slow()));
                    depotRes.setPerBox(boxRules1.getPerBox());
                    depotRes.setGroup(shopDetails.getSheetName());
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

        return ResultGenerator.genSuccessResult(sortedResDepotMap);
    }

    //M-读取货件>>> Workbook workbook 读取excel耗内存大户
    public static WrapListWithMsg<ShopDetails> getShopDetails(String path, InputStream input, File fileIn) throws IOException {

        //关键项规则统一，"实际美森数量"、"发美森数量"、"发慢船数量">>"美森数量","慢船数量"
//        List<String> staticKeys = new ArrayList<>(Arrays.asList("实际美森数量", "发慢船数量"));
//        List<String> staticKeysTmp = new ArrayList<>(Arrays.asList("实际美森数量", "发慢船数量"));

        Workbook workbook = null;
        // 创建工作簿对象
//        ZipSecureFile.setMinInflateRatio(0);//默认可超限

        InputStream inputStream = null;
        if (path != null) {
            inputStream = new FileInputStream(path);
        }
        if (input != null) {
            inputStream = input;
        }
        if (inputStream != null) {
            workbook = new XSSFWorkbook(inputStream);
        }


        WrapListWithMsg<ShopDetails> msgBoxes = new WrapListWithMsg<>();
        List<ShopDetails> shopDetails = new ArrayList<>();
        List<String> errStrs = new ArrayList<>();
        msgBoxes.setListData(shopDetails);//放前面放后面一样？地址指向？ List<String> 不行？

        if (fileIn != null) {
            try {
                workbook = new XSSFWorkbook(fileIn);
            } catch (InvalidFormatException e) {
                e.printStackTrace();
                errStrs.add("整表读取错误:InvalidFormatException");
                return msgBoxes;
            }
        }

        if (workbook == null) {
            errStrs.add("整表读取错误");
            return msgBoxes;
        }
        int numberOfSheets = workbook.getNumberOfSheets(); //获取Workbook中sheet的个数
        //创建一个FormulaEvaluator对象，用于对公式进行求值
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

        for (int i = 0; i < numberOfSheets; i++) { //遍历所有的sheet索引
            String sheetName = workbook.getSheetName(i); //获取每个sheet的名称
//            System.out.println(sheetName); //打印sheet的名称

            //有个隐藏的sheet
            SheetVisibility sheetVisibility = workbook.getSheetVisibility(i); //获取第一个
            if (sheetVisibility == SheetVisibility.VISIBLE) {
                Sheet sheet = workbook.getSheetAt(i); // 获取工作表
                String name = sheet.getSheetName();

                if (name.contains("不发") || name.contains("数据源")) {//过滤表
                    continue;//不能用break
                }
                // 获取最后一行的索引,总行数
                int lastRowNum = sheet.getLastRowNum();


                //首行取关键列
                Row row0 = sheet.getRow(0);
                List<String> keysNeed = new ArrayList<>();//地址 A、B...
                for (Cell cell : row0) {
                    CellType rcCellType = cell.getCellType();
                    Object cellData = null;
                    switch (rcCellType) {
                        case NUMERIC: //如果结果是数值类型，获取数值
                            cellData = cell.getNumericCellValue();
                            break;
                        case STRING: //如果结果是字符串类型，获取字符串
                            cellData = cell.getStringCellValue();
                            break;
                        case BOOLEAN: //如果结果是布尔类型，获取布尔值
                            cellData = cell.getBooleanCellValue();
                            break;
                        case ERROR: //如果结果是错误类型，获取错误码
                            cellData = cell.getErrorCellValue();
                            break;
                        default: //其他情况，跳过
                            continue;
                    }

                    if (cellData instanceof String) {
                        String colKey = (String) cellData;

                        staticKeys.forEach(new Consumer<String>() {
                            @Override
                            public void accept(String s) {
                                if (colKey.contains(s)) {
                                    String address = String.valueOf(cell.getAddress());
                                    String firstChar = String.valueOf(address.charAt(0));
                                    if (!keysNeed.contains(firstChar)) {
                                        keysNeed.add(firstChar);
                                    }
                                    staticKeysTmp.remove(s);
                                }

                            }
                        });

//                        if (colKey.contains(staticKeys.get(0))) {
//                            String address = String.valueOf(cell.getAddress());
//                            String firstChar = String.valueOf(address.charAt(0));
//                            if (!keysNeed.contains(firstChar)) {
//                                keysNeed.add(firstChar);
//                            }
//                            staticKeysTmp.remove(staticKeys.get(0));
//                        } else if (colKey.contains(staticKeys.get(1))) {
//                            String address = String.valueOf(cell.getAddress());
//                            String firstChar = String.valueOf(address.charAt(0));
//                            if (!keysNeed.contains(firstChar)) {
//                                keysNeed.add(firstChar);
//                            }
//                            staticKeysTmp.remove(staticKeys.get(1));
//                        }

                    }
                }
                //美国才有两渠道，日本、欧洲一个渠道
                if (keysNeed.size() != 2) {//只有两个关键列
                    errStrs.add(name + ">未检测到关键列：" + staticKeysTmp.toString());
                    msgBoxes.setErrMsg(errStrs.toString());//放前面放后面一样？地址指向？
                    break;
                }


                //排除首行
                for (int j = 1; j <= lastRowNum; j++) {
                    Row row = sheet.getRow(j);
//                    BoxRules rules = new BoxRules();
//                    BoxRules rules = new BoxRules();
                    ShopDetails details = new ShopDetails();
                    details.setSheetName(name);
                    //列数校验
//                    int lenCell = row.getPhysicalNumberOfCells();
                    //列key值的顺序校验？
//                    rules.setColumn(lenCell);
                    //底部空一大片又突然出现一小格的情况,或者隐藏行
                    if (row == null) {

                        boolean tt = true;
                        continue;
                    }
                    //遍历行中的所有单元格
                    for (Cell cell : row) {

                        CellType rcCellType = cell.getCellType();
                        Object cellData = null;
                        String address = String.valueOf(cell.getAddress());

                        //判断单元格是否是公式类型
                        if (rcCellType == CellType.FORMULA) {
                            //对单元格进行求值，并获取求值结果的类型和值

                            CellValue cellValue = null;
                            try {
                                cellValue = evaluator.evaluate(cell);
                            } catch (FormulaParseException e) {
                                e.printStackTrace();
                                errStrs.add(name + ">" + address + ":公式错误");

                            }
                            if (cellValue == null) {
                                continue;
                            }
                            CellType cellType = cellValue.getCellType();

                            switch (cellType) {
                                case NUMERIC: //如果结果是数值类型，获取数值
                                    cellData = cellValue.getNumberValue();
                                    break;
                                case STRING: //如果结果是字符串类型，获取字符串
                                    cellData = cellValue.getStringValue();
                                    break;
                                case BOOLEAN: //如果结果是布尔类型，获取布尔值
                                    cellData = cellValue.getBooleanValue();
                                    break;
                                case ERROR: //如果结果是错误类型，获取错误码
                                    cellData = cellValue.getErrorValue();
                                    break;
                                default: //其他情况，跳过
                                    continue;
                            }
                            //打印单元格的坐标、公式和求值结果
//                            System.out.println("FORMULA>" + cell.getAddress() + ": " + cell.getCellFormula() + " = " + cellData);
                        } else {
//                            CellType cellType = cell.getCellType();
//                            Object cellData = null;
                            switch (rcCellType) {
                                case NUMERIC: //如果结果是数值类型，获取数值
                                    cellData = cell.getNumericCellValue();
                                    break;
                                case STRING: //如果结果是字符串类型，获取字符串
                                    cellData = cell.getStringCellValue();
                                    break;
                                case BOOLEAN: //如果结果是布尔类型，获取布尔值
                                    cellData = cell.getBooleanCellValue();
                                    break;
                                case ERROR: //如果结果是错误类型，获取错误码
                                    cellData = cell.getErrorCellValue();
                                    break;
                                default: //其他情况，跳过
                                    continue;
                            }
//                            System.out.println("FORMULA-out>" + cell.getAddress() + ">type" + rcCellType + ">: " + cellData);
                        }

//                        String address = cell.getAddress() + "";

                        //排除"AA" "BB"等情况
                        if (address.contains("A") && !address.startsWith("A", 1)) {//一行开始
                            if (cellData instanceof String) {
                                String sku = (String) cellData;
                                details.setSKU(sku);

//                                if (sku.contains(name)) { //实际上不一定包含。。。
//                                    rules.setSKU(sku);
//                                } else {
//                                    System.out.println("sku格式错误>>" + address + ">>" + sku);
//                                }
                            } else {
//                                System.out.println("sku类型错误>>" + address + ">>" + cellData);
                                errStrs.add(name + ">" + address + ":类型错误");
                            }

                        } else if (address.contains("B")) {//可能是美码列？
//                            String size = cellData + "";
                            String size = String.valueOf(cellData);


                        } //第二个字符也判断一下,排除AA等情况
                        else if (address.startsWith(keysNeed.get(0)) && !address.startsWith(keysNeed.get(0), 1)) {//默认美森在前,可能有AA BB这种情况,严格判断
                            String fastNum = String.valueOf(cellData);
//                            rules.setbWidth(Float.parseFloat(width));

                            Float rFast = floatFormat(fastNum);
                            if (rFast != null) {
                                int rFastInt = Math.round(rFast);
                                details.setAMOUNT_shoes_fast(String.valueOf(rFastInt));
                            } else {
                                errStrs.add(name + ">" + address + ":格式错误");
                            }

                        } else if (address.startsWith(keysNeed.get(1)) && !address.startsWith(keysNeed.get(1), 1)) {//慢
                            String slowNum = String.valueOf(cellData);

                            Float rSlow = floatFormat(slowNum);
                            if (rSlow != null) {
                                int rSlowInt = Math.round(rSlow);
                                details.setAMOUNT_shoes_slow(String.valueOf(rSlowInt));
                            } else {
                                errStrs.add(name + ">" + address + ":格式错误");
                            }
                        }
                        if (!shopDetails.contains(details)) {
                            shopDetails.add(details);
                        }

                    }
                }

//                System.out.println("Excel->list>" + shopDetails.size());

            } else {
                System.out.println(sheetName + ">The first sheet is hidden"); //如果是隐藏的，打印提示信息
            }


        }
        msgBoxes.setErrMsg(errStrs.toString());//放前面放后面一样？地址指向？
        workbook.close();// ?需要？
        return msgBoxes;
    }


    //M-读取箱规-poi
    public static WrapListWithMsg<BoxRules> getBoxRulesList(String path, InputStream input, File fileIn) throws IOException {

//        InputStream inputStream = new FileInputStream(path);

        InputStream inputStream = null;
        if (path != null) {
            inputStream = new FileInputStream(path);
        }
        if (input != null) {
            inputStream = input;
        }


        // 获取文件输入流
        // 创建工作簿对象
//        Workbook workbook = new XSSFWorkbook(inputStream);
        Workbook workbook = null;
        if (fileIn != null) {
            try {
                workbook = new XSSFWorkbook(fileIn);
            } catch (InvalidFormatException e) {
                e.printStackTrace();
            }
        } else {
            workbook = new XSSFWorkbook(inputStream);
        }


        WrapListWithMsg<BoxRules> msgBoxes = new WrapListWithMsg<>();
        List<BoxRules> boxRules = new ArrayList<>();
        List<String> errStrs = new ArrayList<>();
        msgBoxes.setListData(boxRules);//放前面放后面一样？地址指向？ List<String> 不行？
        if (workbook == null) {
            errStrs.add("整表读取错误");
            return msgBoxes;
        }
        int numberOfSheets = workbook.getNumberOfSheets(); //获取Workbook中sheet的个数
        //创建一个FormulaEvaluator对象，用于对公式进行求值
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

        for (int i = 0; i < numberOfSheets; i++) { //遍历所有的sheet索引
            String sheetName = workbook.getSheetName(i); //获取每个sheet的名称
//            System.out.println(sheetName); //打印sheet的名称

            //有个隐藏的sheet
            SheetVisibility sheetVisibility = workbook.getSheetVisibility(i); //获取第一个
            if (sheetVisibility == SheetVisibility.VISIBLE) {
                Sheet sheet = workbook.getSheetAt(i); // 获取工作表
                String name = sheet.getSheetName();
                // 获取最后一行的索引,总行数
                int lastRowNum = sheet.getLastRowNum();


                //获取第一个sheet对象
//        Sheet sheet = workbook.getSheetAt(0);
                //数据格式校验！
                //遍历sheet中的所有行
//                for (Row row : sheet) {
                //排除首行
                for (int j = 1; j <= lastRowNum; j++) {
                    Row row = sheet.getRow(j);
                    BoxRules rules = new BoxRules();
                    rules.setSheetName(name);
                    //列数校验
                    int lenCell = row.getPhysicalNumberOfCells();
                    //列key值的顺序校验？
//                    rules.setColumn(lenCell);
                    //遍历行中的所有单元格
                    for (Cell cell : row) {
                        CellType rcCellType = cell.getCellType();
                        Object cellData = null;
                        //判断单元格是否是公式类型
                        if (rcCellType == CellType.FORMULA) {
                            //对单元格进行求值，并获取求值结果的类型和值
                            CellValue cellValue = evaluator.evaluate(cell);
                            CellType cellType = cellValue.getCellType();

                            switch (cellType) {
                                case NUMERIC: //如果结果是数值类型，获取数值
                                    cellData = cellValue.getNumberValue();
                                    break;
                                case STRING: //如果结果是字符串类型，获取字符串
                                    cellData = cellValue.getStringValue();
                                    break;
                                case BOOLEAN: //如果结果是布尔类型，获取布尔值
                                    cellData = cellValue.getBooleanValue();
                                    break;
                                case ERROR: //如果结果是错误类型，获取错误码
                                    cellData = cellValue.getErrorValue();
                                    break;
                                default: //其他情况，跳过
                                    continue;
                            }
                            //打印单元格的坐标、公式和求值结果
//                            System.out.println("FORMULA>" + cell.getAddress() + ": " + cell.getCellFormula() + " = " + cellData);
                        } else {
//                            CellType cellType = cell.getCellType();
//                            Object cellData = null;
                            switch (rcCellType) {
                                case NUMERIC: //如果结果是数值类型，获取数值
                                    cellData = cell.getNumericCellValue();
                                    break;
                                case STRING: //如果结果是字符串类型，获取字符串
                                    cellData = cell.getStringCellValue();
                                    break;
                                case BOOLEAN: //如果结果是布尔类型，获取布尔值
                                    cellData = cell.getBooleanCellValue();
                                    break;
                                case ERROR: //如果结果是错误类型，获取错误码
                                    cellData = cell.getErrorCellValue();
                                    break;
                                default: //其他情况，跳过
                                    continue;
                            }
//                            System.out.println("FORMULA-out>" + cell.getAddress() + ">type" + rcCellType + ">: " + cellData);
                        }

//                        String address = cell.getAddress() + "";
                        String address = String.valueOf(cell.getAddress());
                        if (address.contains("A")) {//一行开始
                            if (cellData instanceof String) {
                                String sku = (String) cellData;
                                rules.setSKU(sku);

//                                if (sku.contains(name)) { //实际上不一定包含。。。
//                                    rules.setSKU(sku);
//                                } else {
//                                    System.out.println("sku格式错误>>" + address + ">>" + sku);
//                                }
                            } else {
//                                System.out.println("sku类型错误>>" + address + ">>" + cellData);
                                errStrs.add(address + ":类型错误");
                            }

                        } else if (address.contains("B")) {
//                            String size = cellData + "";
                            String rSize = String.valueOf(cellData);
//                            Float rSize = floatFormat(size); //可能有英文
                            if (rSize != null) {
                                rules.setfSize(rSize);
                            } else {
                                errStrs.add(address + ":格式错误");
                            }


                        } else if (address.contains("C")) {
                            if (cellData instanceof String) {
                                String fsku = (String) cellData;
//                                if (fsku.contains(name)) {
//                                    rules.setFNSKU(fsku);
//                                } else {
//                                    System.out.println("sku格式错误>>" + address);
//                                }
                                rules.setFNSKU(fsku);
                            } else {
//                                System.out.println("FNSKU类型错误>>" + address);
                                errStrs.add(address + ":类型错误");
                            }

                        } else if (address.contains("D")) {
                            //NUMERIC 基本都是float，带小数点
                            String perBox = String.valueOf(cellData);
                            Float rPerBox = floatFormat(perBox);
                            if (rPerBox != null) {
                                rules.setPerBox(rPerBox.intValue());
                            } else {
                                errStrs.add(address + ":格式错误");
                            }
//                            rules.setPerBox((int) Float.parseFloat(perBox));
                        } else if (address.contains("E")) {
                            String weight = String.valueOf(cellData);

                            Float rWeight = floatFormat(weight);
                            if (rWeight != null) {
                                rules.setbWeight(rWeight);
                            } else {
                                errStrs.add(address + ":格式错误");
                            }

//                            rules.setbWeight(Float.parseFloat(weight));
                        } else if (address.contains("F")) {//长
                            String len = String.valueOf(cellData);

                            Float rLen = floatFormat(len);
                            if (rLen != null) {
                                rules.setbLength(rLen);
                            } else {
                                errStrs.add(address + ":格式错误");
                            }

//                            rules.setbLength(Float.parseFloat(len));
                        } else if (address.contains("G")) {//宽
                            String width = String.valueOf(cellData);
//                            rules.setbWidth(Float.parseFloat(width));

                            Float rWidth = floatFormat(width);
                            if (rWidth != null) {
                                rules.setbWidth(rWidth);
                            } else {
                                errStrs.add(address + ":格式错误");
                            }

                        } else if (address.contains("H")) {//高
                            String height = String.valueOf(cellData);

                            Float rHeight = floatFormat(height);
                            if (rHeight != null) {
                                rules.setbHeight(rHeight);
                            } else {
                                errStrs.add(address + ":格式错误");
                            }
                        } else if (address.contains("I")) {//ERP款号
                            String erpCode = String.valueOf(cellData);
                            if (erpCode != null) {
                                rules.setErpCode(erpCode);
                            } else {
                                errStrs.add(name + ">" + address + "ERP款号缺失");
                            }

                        } else if (address.contains("J")) {//ARTICLE
                            String ARTICLE = String.valueOf(cellData);

                            if (ARTICLE != null) {
                                rules.setARTICLE(ARTICLE);
                            } else {
                                errStrs.add(name + ">" + address + "ARTICLE缺失");
                            }
//                            System.out.println("ARTICLE>>>" + ARTICLE + "地址：" + address);
                        } else if (address.contains("K")) {//HS CODE
                            String hsCode = String.valueOf(cellData);
                            if (rules.getARTICLE() == null) {//放最后一个来判别前面的
                                errStrs.add(name + ">" + address + "附近，ARTICLE缺失");
                            }
                            if (rules.getErpCode() == null) {//放最后一个来判别前面的
                                //过时或者不重要？
                                //[UK-KX>K170附近，ERP款号缺失, UK-KX>K171附近，ERP款号缺失, UK-KX>K192附近，ERP款号缺失, UK-KX>K193附近，ERP款号缺失]

//                                errStrs.add(name + ">" + address + "附近，ERP款号缺失"); //
                            }
                            if (hsCode != null) {
                                rules.setHS_CODE(hsCode);
                            } else {
                                errStrs.add(name + ">" + address + "HS CODE缺失");
                            }
//                            System.out.println("HS CODE>>>" + hsCode + "地址：" + address);
                        }
//                        if (!boxRules.contains(rules)) {
//                            boxRules.add(rules);
//                        }

                    }
                    //放里外结果一样？
                    if (!boxRules.contains(rules)) {
                        if (rules.getARTICLE() == null) {//放最后一个来判别前面的
                            String er = name + ">" + "里，ARTICLE缺失";
                            if (!errStrs.contains(er)) {
                                errStrs.add(er);
                            }

                        }
                        if (rules.getHS_CODE() == null) {//放最后一个来判别前面的
                            String er = name + ">" + "HS CODE缺失";
                            if (!errStrs.contains(er)) {
                                errStrs.add(er);
                            }
                        }
                        if (rules.getErpCode() == null) {//放最后一个来判别前面的
                            //过时或者不重要？
                            //[UK-KX>K170附近，ERP款号缺失, UK-KX>K171附近，ERP款号缺失, UK-KX>K192附近，ERP款号缺失, UK-KX>K193附近，ERP款号缺失]

//                                errStrs.add(name + ">" + address + "附近，ERP款号缺失"); //
                        }

                        boxRules.add(rules);
                    }
                }

//                System.out.println("Excel->list>" + boxRules.size());

            } else {
                System.out.println(sheetName + ">The first sheet is hidden"); //如果是隐藏的，打印提示信息
            }


        }
        msgBoxes.setErrMsg(errStrs.toString());//放前面放后面一样？地址指向？
        if (inputStream != null) {
            inputStream.close();
        }
        if (workbook != null) {
            workbook.close();
        }

        return msgBoxes;
    }

    //M-读取箱规-fast，有不确定问题？>>fastexcel不支持xls
    public static WrapListWithMsg<BoxRules> getBoxRulesListFast(File fileIn) throws IOException {

        ReadableWorkbook wb = new ReadableWorkbook(fileIn);

        WrapListWithMsg<BoxRules> msgBoxes = new WrapListWithMsg<>();
        List<BoxRules> boxDetails = new ArrayList<>();
        List<String> errStrs = new ArrayList<>();
        msgBoxes.setListData(boxDetails);//放前面放后面一样？地址指向？ List<String> 不行？
        Stream<org.dhatim.fastexcel.reader.Sheet> sheets = wb.getSheets(); //获取Workbook中sheet的个数

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
                            //排除首行
                            for (int i = 1; i < size; i++) {
                                org.dhatim.fastexcel.reader.Row row = rr.get(i);
                                BoxRules details = new BoxRules();
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

                                row.stream().forEach(cell -> {
                                    if (cell == null) {
                                        return;
                                    }
                                    Object cellData = cell.getValue();
                                    String address = String.valueOf(cell.getAddress());

                                    if (address.startsWith("A")) {//一行开始
                                        if (cellData instanceof String) {
                                            String sku = (String) cellData;
                                            details.setSKU(sku);
                                        } else {
                                            errStrs.add(name + ">" + address + ":类型错误");
                                        }

                                    } else if (address.startsWith("B")) {
                                        String rSize = String.valueOf(cellData);
//                                        Float rSize = floatFormat(sizeStr);
                                        if (rSize != null) {
                                            details.setfSize(rSize);
                                        } else {
                                            errStrs.add(name + ">" + address + ":格式错误");
                                        }

                                    } else if (address.startsWith("C")) {
                                        if (name.contains("DE-FRA") && address.contains("C144")) {
                                            boolean t = true;
                                        }
                                        if (cellData instanceof String) {
                                            String fsku = (String) cellData;
                                            details.setFNSKU(fsku);
                                        } else {
                                            errStrs.add(name + ">" + address + ":类型错误");
                                        }

                                    } else if (address.startsWith("D")) {
                                        //NUMERIC 基本都是float，带小数点
                                        String perBox = String.valueOf(cellData);
                                        Float rPerBox = floatFormat(perBox);
                                        if (rPerBox != null) {
                                            details.setPerBox(rPerBox.intValue());
                                        } else {
                                            errStrs.add(name + ">" + address + ":格式错误");
                                        }
                                    } else if (address.startsWith("E")) {
                                        String weight = String.valueOf(cellData);

                                        Float rWeight = floatFormat(weight);
                                        if (rWeight != null) {
                                            details.setbWeight(rWeight);
                                        } else {
                                            errStrs.add(name + ">" + address + ":格式错误");
                                        }

                                    } else if (address.startsWith("F")) {//长
                                        String len = String.valueOf(cellData);

                                        Float rLen = floatFormat(len);
                                        if (rLen != null) {
                                            details.setbLength(rLen);
                                        } else {
                                            errStrs.add(name + ">" + address + ":格式错误");
                                        }

                                    } else if (address.startsWith("G")) {//宽
                                        String width = String.valueOf(cellData);
                                        Float rWidth = floatFormat(width);
                                        if (rWidth != null) {
                                            details.setbWidth(rWidth);
                                        } else {
                                            errStrs.add(name + ">" + address + ":格式错误");
                                        }

                                    } else if (address.startsWith("H")) {//高
                                        String height = String.valueOf(cellData);

                                        Float rHeight = floatFormat(height);
                                        if (rHeight != null) {
                                            details.setbHeight(rHeight);
                                        } else {
                                            errStrs.add(name + ">" + address + ":格式错误");
                                        }
                                    }
                                    if (!boxDetails.contains(details)) {
                                        boxDetails.add(details);
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

        return msgBoxes;
    }

    //M-读取仓库信息
    public static WrapListWithMsg<StockInfos> getStockInfosListFast(File fileIn) throws IOException {

        ReadableWorkbook wb = new ReadableWorkbook(fileIn);

        WrapListWithMsg<StockInfos> msgBoxes = new WrapListWithMsg<>();
        List<StockInfos> boxDetails = new ArrayList<>();
        List<String> errStrs = new ArrayList<>();
        msgBoxes.setListData(boxDetails);//放前面放后面一样？地址指向？ List<String> 不行？
        Stream<org.dhatim.fastexcel.reader.Sheet> sheets = wb.getSheets(); //获取Workbook中sheet的个数

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
                            //排除首行
                            for (int i = 1; i < size; i++) {
                                org.dhatim.fastexcel.reader.Row row = rr.get(i);
                                StockInfos details = new StockInfos();
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
                                        || fCell.get().getText().toUpperCase().contains("仓库名")) {//中间可能还有标题
                                    continue;
                                }

                                row.stream().forEach(cell -> {
                                    if (cell == null) {
                                        return;
                                    }
                                    Object cellData = cell.getValue();
                                    String address = String.valueOf(cell.getAddress());

                                    if (address.startsWith("A")) {//一行开始
                                        if (cellData instanceof String) {
                                            String sku = (String) cellData;
                                            details.setStockName(sku);
                                        } else {
                                            errStrs.add(name + ">" + address + ":类型错误");
                                        }

                                    } else if (address.startsWith("B")) {
                                        if (cellData instanceof String) {
                                            String sku = (String) cellData;
                                            details.setFBA(sku);
                                        } else {
                                            errStrs.add(name + ">" + address + ":类型错误");
                                        }

                                    } else if (address.startsWith("C")) {
                                        if (cellData instanceof String) {
                                            String sku = (String) cellData;
                                            details.setCountry(sku);
                                        } else {
                                            errStrs.add(name + ">" + address + ":类型错误");
                                        }

                                    } else if (address.startsWith("D")) {
                                        if (cellData instanceof String) {
                                            String sku = (String) cellData;
                                            details.setProvince(sku);
                                        } else {
                                            errStrs.add(name + ">" + address + ":类型错误");
                                        }
                                    } else if (address.startsWith("E")) {
                                        if (cellData instanceof String) {
                                            String sku = (String) cellData;
                                            details.setCity(sku);
                                        } else {
                                            errStrs.add(name + ">" + address + ":类型错误");
                                        }

                                    } else if (address.startsWith("F")) {//邮编
                                        String sku = String.valueOf(cellData);
                                        details.setPostalCode(sku);

                                    } else if (address.startsWith("G")) {//宽
                                        if (cellData instanceof String) {
                                            String sku = (String) cellData;
                                            details.setAddress(sku);
                                        } else {
                                            errStrs.add(name + ">" + address + ":类型错误");
                                        }

                                    }
                                    if (!boxDetails.contains(details)) {
                                        boxDetails.add(details);
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

        return msgBoxes;
    }

    //M-仓库信息表读取
    public static WrapListWithMsg<CargoInfo> getCargoInfoListFast(File fileIn) throws IOException {

        ReadableWorkbook wb = new ReadableWorkbook(fileIn);

        WrapListWithMsg<CargoInfo> msgBoxes = new WrapListWithMsg<>();
        List<CargoInfo> boxDetails = new ArrayList<>();
        List<String> errStrs = new ArrayList<>();
        msgBoxes.setListData(boxDetails);//放前面放后面一样？地址指向？ List<String> 不行？
        Stream<org.dhatim.fastexcel.reader.Sheet> sheets = wb.getSheets(); //获取Workbook中sheet的个数

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
                            //排除首行
                            for (int i = 1; i < size; i++) {
                                org.dhatim.fastexcel.reader.Row row = rr.get(i);
                                CargoInfo details = new CargoInfo();
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
                                        || fCell.get().getText().toUpperCase().contains("仓库名")) {//中间可能还有标题
                                    continue;
                                }

                                row.stream().forEach(cell -> {
                                    if (cell == null) {
                                        return;
                                    }
                                    Object cellData = cell.getValue();
                                    String address = String.valueOf(cell.getAddress());

                                    if (address.startsWith("A")) {//一行开始
                                        if (cellData instanceof String) {
                                            String sku = (String) cellData;
                                            details.setStockName(sku);
                                        } else {
                                            errStrs.add(name + ">" + address + ":类型错误");
                                        }

                                    } else if (address.startsWith("B")) {
                                        if (cellData instanceof String) {
                                            String sku = (String) cellData;
                                            details.setCargo(sku);
                                        } else {
                                            errStrs.add(name + ">" + address + ":类型错误");
                                        }

                                    } else if (address.startsWith("C")) {
                                        if (cellData instanceof String) {
                                            String sku = (String) cellData;
                                            details.setFBA(sku);
                                        } else {
                                            errStrs.add(name + ">" + address + ":类型错误");
                                        }

                                    } else if (address.startsWith("D")) {
                                        if (cellData instanceof String) {
                                            String sku = (String) cellData;
                                            details.setCargoNum(sku);
                                        } else {
                                            errStrs.add(name + ">" + address + ":类型错误");
                                        }
                                    } else if (address.startsWith("E")) {
                                        String sku = String.valueOf(cellData);
                                        details.setBoxes(sku);

                                    } else if (address.startsWith("F")) {//商品数
                                        String sku = String.valueOf(cellData);
                                        details.setShoes(sku);

                                    } else if (address.startsWith("G")) {
                                        if (cellData instanceof String) {
                                            String sku = (String) cellData;
                                            details.setAddress(sku);
                                        } else {
                                            errStrs.add(name + ">" + address + ":类型错误");
                                        }

                                    }
                                    if (!boxDetails.contains(details)) {
                                        boxDetails.add(details);
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

        return msgBoxes;
    }

    //M-申报要素-poi
    public static WrapListWithMsg<ShenBaoInfo> getShenBaoInfoList(File fileIn) throws IOException {

        // 获取文件输入流
        // 创建工作簿对象
//        Workbook workbook = new XSSFWorkbook(inputStream);

        FileInputStream fileInputStream = new FileInputStream(fileIn);

        Workbook workbook = null;
        workbook = new HSSFWorkbook(fileInputStream);

        WrapListWithMsg<ShenBaoInfo> msgBoxes = new WrapListWithMsg<>();
        List<ShenBaoInfo> boxRules = new ArrayList<>();
        List<String> errStrs = new ArrayList<>();
        msgBoxes.setListData(boxRules);//放前面放后面一样？地址指向？ List<String> 不行？
        int numberOfSheets = workbook.getNumberOfSheets(); //获取Workbook中sheet的个数
        //创建一个FormulaEvaluator对象，用于对公式进行求值
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

        //略过第一个sheet
        for (int i = 1; i < numberOfSheets; i++) { //遍历所有的sheet索引
            String sheetName = workbook.getSheetName(i); //获取每个sheet的名称
//            System.out.println(sheetName); //打印sheet的名称

            //有个隐藏的sheet
            SheetVisibility sheetVisibility = workbook.getSheetVisibility(i); //获取第一个
            if (sheetVisibility == SheetVisibility.VISIBLE) {
                Sheet sheet = workbook.getSheetAt(i); // 获取工作表
                String name = sheet.getSheetName();
                // 获取最后一行的索引,总行数
                int lastRowNum = sheet.getLastRowNum();

//                List<PictureData> pictures = (List<PictureData>) workbook.getAllPictures();

                // 获取所有的绘图对象
//                List<HSSFPictureData> pictures = (List<HSSFPictureData>) workbook.getAllPictures();

                //都固定-15，15列
                Map<String, PictureData> maplist = getPictures1((HSSFSheet) sheet);

//                for (HSSFShape shape : (sheet.getDrawingPatriarch()).getChildren()) {
//                    // 如果是图片对象
//                    if (shape instanceof HSSFPicture) {
//                        HSSFPicture picture = (HSSFPicture) shape;
//                        // 获取图片锚点
//                        HSSFClientAnchor anchor = (HSSFClientAnchor) picture.getAnchor();
//                        // 获取图片数据
//                        PictureData pictureData = pictures.get(picture.getPictureIndex() - 1);
//                        // 获取图片位置，格式为行号-列号
//                        String key = anchor.getRow1() + "-" + anchor.getCol1();
//                        // 将图片和位置放入映射中
////                        pictureMap.put(key, pictureData);
//                        Lg.i("图图图》》》key", ">>>" + key);
//                    }
//                }

                // 获取所有图片数据
//                List<HSSFPictureData> pictures = workbook.getAllPictures();
                // 遍历图片数据

//                for (HSSFPictureData picture : pictures) {
//                    // 获取图片格式
//                    String ext = picture.suggestFileExtension();
//
//
//                    // 获取图片在sheet中的位置
//                    HSSFClientAnchor anchor = (HSSFClientAnchor) picture.getAnchor();
//                    int row = anchor.getRow1();
//                    int col = anchor.getCol1();
//                    // 创建一个输出流，保存图片到指定路径
//                    FileOutputStream out = new FileOutputStream("image_" + row + "_" + col + "." + ext);
//                    out.write(picture.getData());
//                    out.close();
//                }


                boolean tp = true;
                for (int j = 1; j <= lastRowNum; j++) {
                    Row row = sheet.getRow(j);
                    if (row == null || row.getCell(0) == null) {
                        continue;
                    }
                    ShenBaoInfo rules = new ShenBaoInfo();
                    rules.setSheetName(name);
                    //列数校验
                    int lenCell = row.getPhysicalNumberOfCells();
                    //列key值的顺序校验？
//                    rules.setColumn(lenCell);
                    //遍历行中的所有单元格
                    for (Cell cell : row) {
                        CellType rcCellType = cell.getCellType();
                        Object cellData = null;
                        //判断单元格是否是公式类型
                        if (rcCellType == CellType.FORMULA) {
                            //对单元格进行求值，并获取求值结果的类型和值
                            CellValue cellValue = evaluator.evaluate(cell);
                            CellType cellType = cellValue.getCellType();

                            switch (cellType) {
                                case NUMERIC: //如果结果是数值类型，获取数值
                                    cellData = cellValue.getNumberValue();
                                    break;
                                case STRING: //如果结果是字符串类型，获取字符串
                                    cellData = cellValue.getStringValue();
                                    break;
                                case BOOLEAN: //如果结果是布尔类型，获取布尔值
                                    cellData = cellValue.getBooleanValue();
                                    break;
                                case ERROR: //如果结果是错误类型，获取错误码
                                    cellData = cellValue.getErrorValue();
                                    break;
//                                default: //其他情况，跳过
//                                    continue;
                            }
                            //打印单元格的坐标、公式和求值结果
//                            System.out.println("FORMULA>" + cell.getAddress() + ": " + cell.getCellFormula() + " = " + cellData);
                        } else {
//                            CellType cellType = cell.getCellType();
//                            Object cellData = null;
                            switch (rcCellType) {
                                case NUMERIC: //如果结果是数值类型，获取数值
                                    cellData = cell.getNumericCellValue();
                                    break;
                                case STRING: //如果结果是字符串类型，获取字符串
                                    cellData = cell.getStringCellValue();
                                    break;
                                case BOOLEAN: //如果结果是布尔类型，获取布尔值
                                    cellData = cell.getBooleanCellValue();
                                    break;
                                case ERROR: //如果结果是错误类型，获取错误码
                                    cellData = cell.getErrorCellValue();
                                    break;
//                                default: //其他情况，跳过
//                                    Lg.i(">>>", rcCellType.name());
//                                    continue;
                            }
//                            System.out.println("FORMULA-out>" + cell.getAddress() + ">type" + rcCellType + ">: " + cellData);
                        }

//                        String address = cell.getAddress() + "";
                        String address = String.valueOf(cell.getAddress());
                        if (address.contains("A")) {//一行开始
                            if (cellData instanceof String) {
                                String sku = (String) cellData;
                                rules.setHsCode(sku);
                                //这儿插入图片，有sku就默认有图片

                                int ml = row.getRowNum();
                                PictureData pictureData = maplist.get(String.valueOf(ml));
                                rules.setPicData(pictureData);
                                Lg.i("图片图片>>>", ml + ">>>" + j);


                            } else {
//                                errStrs.add(name + ">" + address + ":类型错误");
                            }

                        } else if (address.contains("B")) {
//                            String size = cellData + "";
                            String rSize = String.valueOf(cellData);
                            if (rSize != null) {//申报要素
                                rules.setInfo(rSize);
                            } else {
//                                errStrs.add(name + ">" + address + ":格式错误");
                            }


                        } else if (address.contains("C")) {
                            String catalog = String.valueOf(cellData);
                            if (catalog != null) {//款号(型号)
                                //数字会被读成double
                                catalog = removeDotPart(catalog);
                                rules.setStyle(catalog.toUpperCase());//统一一下大小写！！！
                            } else {
                                errStrs.add(name + ">" + address + ":格式错误");
                            }

                        } else if (address.contains("D")) {
                            String rSize = String.valueOf(cellData);
                            if (rSize != null) {//英文品名
                                rules.setEnName(rSize);
                            } else {
                                errStrs.add(name + ">" + address + ":格式错误");
                            }
                        } else if (address.contains("E")) {
                            String rSize = String.valueOf(cellData);
                            if (rSize != null) {//分类
                                rules.setType(rSize);
                            } else {
                                errStrs.add(name + ">" + address + ":格式错误");
                            }
                        } else if (address.contains("F")) {
                            String rSize = String.valueOf(cellData);
                            if (rSize != null) {//工厂
                                rules.setFactory(rSize);
                            } else {
                                errStrs.add(name + ">" + address + ":格式错误");
                            }
                        } else if (address.contains("G")) {
                            String rSize = String.valueOf(cellData);
                            if (rSize != null) {//开票品名
                                rules.setTicketName(rSize);
                            } else {
//                                errStrs.add(name + ">" + address + ":格式错误");
                            }

                        } else if (address.contains("H")) {
                            String rSize = String.valueOf(cellData);
                            if (rSize != null) {
                                rules.setUsd(rSize);//最终报关单价/USD
                            } else {
//                                errStrs.add(name + ">" + address + ":格式错误");
                            }
                        } else if (address.contains("I")) {//最终报关单价/RMB
                            String erpCode = String.valueOf(cellData);
                            if (erpCode != null) {
                                rules.setRmb(erpCode);
                            } else {
//                                errStrs.add(name + ">" + address + "最终报关单价/RMB缺失");
                            }

                        } else if (address.contains("J")) {//报关单位
                            String ARTICLE = String.valueOf(cellData);
                            if (ARTICLE != null) {
                                rules.setUnit(ARTICLE);
                            } else {
                                errStrs.add(name + ">" + address + "报关单位缺失");
                            }
                        } else if (address.contains("K")) {//Description
                            String des = String.valueOf(cellData);
                            if (des != null) {
                                rules.setDescription(des);
                            } else {
//                                errStrs.add(name + ">" + address + "Description缺失");
                            }
                        } else if (address.contains("L")) {//单件毛重
                            String des = String.valueOf(cellData);
                            if (des != null) {
                                rules.setKgs(des);
                            } else {
//                                errStrs.add(name + ">" + address + "Description缺失");
                            }
                        } else if (address.contains("M")) {//品牌
                            String des = String.valueOf(cellData);
                            if (des != null) {
                                rules.setBrand(des);
                            } else {
//                                errStrs.add(name + ">" + address + "Description缺失");
                            }
                        } else if (address.contains("N")) {//材质
                            String des = String.valueOf(cellData);
                            if (des != null) {
                                rules.setMaterial(des);
                            } else {
//                                errStrs.add(name + ">" + address + "Description缺失");
                            }
                        } else if (address.contains("O")) {//用途
                            String des = String.valueOf(cellData);
                            if (des != null) {
                                rules.setPurpose(des);
                            } else {
//                                errStrs.add(name + ">" + address + "Description缺失");
                            }
                        } else if (address.contains("P")) {//图片
                            //这儿只有8项识别？？


                        }
//                        if (!boxRules.contains(rules)) {
//                            boxRules.add(rules);
//                        }

                    }
                    //放里外结果一样？
                    if (!boxRules.contains(rules)) {
                        boxRules.add(rules);
                    }
                }

//                System.out.println("Excel->list>" + boxRules.size());

            } else {
                System.out.println(sheetName + ">The first sheet is hidden"); //如果是隐藏的，打印提示信息
            }


        }
        msgBoxes.setErrMsg(errStrs.toString());//放前面放后面一样？地址指向？
        if (fileInputStream != null) {
            fileInputStream.close();
        }
        if (workbook != null) {
            workbook.close();
        }

        return msgBoxes;
    }

    public static String removeDotPart(String val) {
        if (val == null) {
            return "";
        }
        //如果元素包含小数点
        if (val.contains(".")) {
            //找到小数点的位置
            int index = val.indexOf(".");
            //截取小数点之前的部分
            val = val.substring(0, index);
        }

        return val;
    }

    public static Float floatFormat(String str) {
        Float f = 0.0f;
        boolean isNum = StringUtils.isNumber(str);
        if (isNum) {
            f = Float.parseFloat(str);
        } else {
            f = null;
        }
//        try {
//            f = Float.parseFloat(str);
//        } catch (NumberFormatException e) {
//            e.printStackTrace();
//            f = null;
//        }
        return f;
    }


    //小数转换会异常，这个只能整数!
    public static Integer intFormat(String str) {
        Integer f = 0;
        boolean isNum = StringUtils.isNumber(str);
        if (isNum) {
            f = Integer.parseInt(str);
        } else {
            f = 0;
        }
        return f;
    }

    public static void adjustAutoWidth(XSSFSheet sheet, int titleLength) {
        int maxColumnWidth = 30 * 256;
        for (int i = 0; i < titleLength; i++) {
            sheet.autoSizeColumn(i, true);
            int colW = sheet.getColumnWidth(i);
            if (colW > maxColumnWidth) {
                colW = maxColumnWidth;
            }
            //手动调整解决中文不能自适应问题
            sheet.setColumnWidth(i, colW * 12 / 10);
//            sheet.autoSizeColumn(i, true);
        }
    }

    public static void cellColor(XSSFCellStyle styleIn, XSSFCellStyle style, byte[] byteColor) {
//        XSSFCellStyle styleIn = workbook.createCellStyle();//XSSFCellStyle才可自由设置颜色
        styleIn.cloneStyleFrom(style);
        XSSFColor myColor = new XSSFColor();
        //淡粉色 238,180,192
//        byte[] byteColor = new byte[]{(byte) 238, (byte) 180, (byte) 192};
        myColor.setRGB(byteColor);
        styleIn.setFillForegroundColor(myColor);
        styleIn.setFillPattern(FillPatternType.SOLID_FOREGROUND);

//        return styleIn;
    }

    //IND4-2023.2.15-泓翔美森-US-HMT-502-503-11-60 提取
    public static List<String> findItemInNames(String name) {
        // 假设你的字符串是 str
//        String regex = "([A-Z]+)(-)(\\d+\\.\\d+)(-)([A-Z]+)(-)([A-Z]+)(-)(.*)";
        String regex = "((\\w+-)?\\w+)(-{1,2})(\\d{4}\\.\\d{1,2}\\.\\d{1,2})-(.+?)(-)(([A-Z]+)(-)([A-Z]+))(-)(.*)";
        Pattern p = Pattern.compile(regex); // 编译正则表达式
        Matcher m = p.matcher(name); // 创建匹配器
        List<String> tags = new ArrayList<>();
        while (m.find()) { // 循环查找匹配的部分
//            System.out.println("第一部分：" + m.group(1)); // 打印第一个捕获组
            tags.add(m.group(1));//仓库
//            System.out.println("第三部分：" + m.group(4)); // 打印第三个捕获组
            tags.add(m.group(4));//日期
//            System.out.println("第五部分：" + m.group(5)); // 打印第五个捕获组
            tags.add(m.group(5));//渠道
//            System.out.println("第七部分：" + m.group(7)); // 打印第七个捕获组
            tags.add(m.group(7));//店铺
        }
        return tags;
    }


    //US-LU-820-Black-13_5      13_5
    //US-LU-61601-39-1          39-1
    //US-HMT-004-White-08_5-1   08_5-1
    public static String getSizeInSKU(String skuStr) {
        //定义一个正则表达式，匹配末尾1～50内“数字－数字”或“数字_数字”样的字符
//        String regex = "(\\d+[\\-_]\\d+)$";

//        String regex = "\\d+[\\-_]\\d+(?:[\\-_]\\d+)?";
//        String regex = "$\\d+[\\-_]\\d+(?:[\\-_]\\d+)?";
        String regex = ".*\\d+[\\-_]\\d+(?:[\\-_]\\d+)?";


//编译正则表达式，创建一个Pattern对象
        Pattern pattern = Pattern.compile(regex);

//遍历字符串数组，对每个字符串进行匹配和提取操作
        //创建一个Matcher对象，传入要匹配的字符串
        Matcher matcher = pattern.matcher(skuStr);
        String result = null;
        //判断是否匹配成功
        if (matcher.find()) {
            //提取匹配到的子字符串，使用group方法
            result = matcher.group();
            //打印结果
            System.out.println(result);
            Lg.e("getSizeInSKU>>>", result);
        }
        return result;
    }

    //全尺码匹配
    public static String getSizeInSKUZ(String skuStr) {


        String[] strs = {"US-LU-820-Black-13_5",
                "US-LU-61601-39-1",
                "US-HMT-004-White-08_5-1", "US-HMT-CB805-BK-2", "US-HMT-CB805-BK-1", "US-HMT-CB806-BR-009",
                "US-HMT-CB806-BR-010",
                "US-RKS-EB003-BK-10.5",
                "US-RKS-EB003-BK-11",
                "US-RKS-EB003-BR-5",
                "US-RKS-EB003-BR-5.5",
                " US-HMT-CB806-BR-011"};

        //US-MY-BA201-Red-AA
        //US-MY-BA301-Black  //箱包没有尺寸！！

//        String regex = "-(\\d+_\\d+|\\d+)$";
//        String regex = "-(\\d+_\\d+|\\d+)\\b";
//        String regex = "-(\\d+_\\d+|\\d+)\\b$";

//        String regex = "-(([0]?[1-9]|[1-5][0-9])+(?:[\\-_]\\d+)?+(?:[\\-]\\d+)?)$";
        String regex = "-((([0]|[0][0])?[1-9]((.[5])?)|([0]?)[1-5][0-9]((.[5])?))+(?:[\\-_]([0]?[1-9]|[1-5][0-9]))?+(?:[\\-]\\d+)?)$";
        // 编译正则表达式为 Pattern 对象
        Pattern pattern = Pattern.compile(regex);
        // 遍历字符串数组，对每个字符串进行匹配和提取
        // 创建 Matcher 对象
        String res = null;
        Matcher matcher = pattern.matcher(skuStr);
        // 判断是否匹配
        if (matcher.find()) {
            // 提取匹配到的子串，去掉连字符
            String sub = matcher.group().substring(1);
            res = sub;
            // 输出结果
            System.out.println(skuStr + " -> " + sub);
        }

        //            res = res.split("-")[0];

        return res;

    }


    /**
     * 获取图片和位置 (xls)
     *
     * @param sheet
     * @return
     * @throws IOException
     */
    public static Map<String, PictureData> getPictures1(HSSFSheet sheet) throws IOException {
        Map<String, PictureData> map = new HashMap<String, PictureData>();
        HSSFPatriarch shapes = sheet.getDrawingPatriarch();
        if (shapes == null) {
            return map;
        }
        List<HSSFShape> list = shapes.getChildren();
        for (HSSFShape shape : list) {
            if (shape instanceof HSSFPicture) {
                HSSFPicture picture = (HSSFPicture) shape;
                HSSFClientAnchor cAnchor = (HSSFClientAnchor) picture.getAnchor();
                PictureData pdata = picture.getPictureData();
//                String key = cAnchor.getRow1() + "-" + cAnchor.getCol1(); // 行号-列号
                String key = cAnchor.getRow1() + "";//暂只要行号

                String proName = sheet.getRow(cAnchor.getRow1()).getCell(0).getStringCellValue();
                if (cAnchor.getCol1() == 1) {
                    key = proName += "01";
                } else if (cAnchor.getCol1() == 11) {
                    key = proName += "02";
                }
                map.put(key, pdata);
            }
        }
        return map;
    }
//
//————————————————
//    版权声明：本文为CSDN博主「csdnlzy」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
//    原文链接：https://blog.csdn.net/CSDNlzy/article/details/126136627


    //箱规里的erp款号 和 sku 提取出的款号 对应取申报要素，综合判断结果比较全面
    public static List<ShenBaoInfo> getSkuShenbaoAll(AMZout amZout, Map<String, List<ShenBaoInfo>> shenBaoInfoGroup) {
        String catalog = PoiUtiles.removeDotPart(amZout.getErpCode());//ERP code 比较对应
        //取出款号
        List<ShenBaoInfo> shenBaoInfos = shenBaoInfoGroup.get(catalog.toUpperCase());

        String inErpCode = "";
        if (shenBaoInfos == null || shenBaoInfos.isEmpty()) {
            String sku_no_sizeAndColor = getSkuModel(amZout.getSku());
            String[] parts = sku_no_sizeAndColor.split("-");
            inErpCode = parts[parts.length - 1];
            shenBaoInfos = shenBaoInfoGroup.get(inErpCode);
        }
        if (amZout.getSku().contains("616")) {

            boolean t = true;

        }
        return shenBaoInfos;

    }


    //取出sku里的款号
    public static String getSkuModel(String s) {
        // 首先检查字符串是否包含至少两个连字符
        if (s.indexOf("-") != -1 && s.indexOf("-", s.indexOf("-") + 1) != -1) {
            // 找到第一个连字符的位置
            String separator = "-";
            int sepPos = s.indexOf(separator); // 第一个分隔符的位置
            sepPos = s.indexOf(separator, sepPos + 1); // 第二个分隔符的位置
            sepPos = s.indexOf(separator, sepPos + 1); // 第三个分隔符的位置
            if (sepPos <= 0) {//可能没有第三个分割符号
                Lg.i("getSkuModelxx>>>", s + ">>>" + sepPos);
                return s.substring(0); // 返回结果
            } else {
                Lg.i("getSkuModel>>>", s + ">>>" + sepPos);
                return s.substring(0, sepPos); // 返回结果
            }

        } else {
            // 如果字符串不包含至少两个连字符，返回空字符串
            return "";
        }
    }


}
