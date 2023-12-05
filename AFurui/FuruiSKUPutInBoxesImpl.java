package com.magicair.webpj.AFurui;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.magicair.webpj.AFurui.model.AMZoutBox;
import com.magicair.webpj.AFurui.model.AMZoutFeeInfo;
import com.magicair.webpj.AFurui.model.wrap.WrapListWithMsg;
import com.magicair.webpj.core.Result;
import com.magicair.webpj.core.ResultCode;
import com.magicair.webpj.core.ResultGenerator;
import com.magicair.webpj.utils.CommonUtils;
import com.magicair.webpj.utils.Lg;
import com.magicair.webpj.utils.StringUtils;
import com.magicair.webpj.utils.ZipUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.dhatim.fastexcel.reader.CellAddress;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Sheet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.magicair.webpj.AFurui.ConstantFu.C_ResultExcelFloder;


@Service
@Transactional
public class FuruiSKUPutInBoxesImpl {


    @Value("${web.excel-path}")
    private String realPathExcel;


    public Result excelAction(String folderToken, List<File> inputs) {
        long t1 = System.currentTimeMillis();

        List<AMZoutFeeInfo> allRes = new ArrayList<>();//不能用全局的，多次结果会缓存？对多线程也有影响？？
        staticKeysTmp = new ArrayList<>(Arrays.asList("SKU", "装箱率", "箱量/箱数", "数量", "重量", "宽", "长", "高"));//每次重置一下？
        String[] warning = {null};
        List<Runnable> tasks = new ArrayList<>();
        List<Result> resultsErr = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            int finalI = i;
//            Runnable runnable = () -> {
//
//            };
//
//            tasks.add(runnable);


            WrapListWithMsg<AMZoutFeeInfo> resx = null;
            try {
                resx = getSKUWithBoxes(inputs.get(finalI));
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (resx.getErrMsg() != null && resx.getErrMsg().length() > 3) {

                Result result = new Result();
                result.setCode(ResultCode.FAIL);
                result.setData(resx.getErrMsg());
                resultsErr.add(result);
            }
            if (resx != null) {
                Map mapWays = resx.getListData().stream().collect(Collectors.groupingBy(AMZoutFeeInfo::getObWays));

                mapWays.values().forEach(new Consumer() {
                    @Override
                    public void accept(Object o) {
                        wrapWrite((List<AMZoutFeeInfo>) o, folderToken);
                    }
                });
            } else {
                Result result = new Result();
                result.setCode(ResultCode.FAIL);
                result.setData("读取初始文件失败");
                resultsErr.add(result);
            }
        }

        //用ForkJoinPool会导致解析数据混乱？？？！！>>>>非synchronized下allRes被多线程同时修改的原因！
//        CommonUtils.runTaskAwait(tasks, inputs.size());


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


        long t2 = System.currentTimeMillis() - t1;
        JSONObject jsonObject = new JSONObject();

        String timeCreated = "-" + CommonUtils.getStringMonthAndDay();
        String outName = "建货件" + timeCreated;

        String pSrc = realPathExcel + C_ResultExcelFloder + "/" + folderToken;

        try {
            ZipUtils.copyAndRenameFolder(pSrc, pSrc + "Copy", outName);
        } catch (IOException e) {
            e.printStackTrace();
            return ResultGenerator.genFailResult("压缩文件重命名失败");
        }

        String renameSrc = pSrc + "Copy" + "/" + outName;

        String targetPath = realPathExcel + C_ResultExcelFloder + "/" + outName + ".zip";

        try {
            ZipUtils.toZip(renameSrc, targetPath, true);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return ResultGenerator.genFailResult("压缩文件生成失败");
        }

        String backPath = C_ResultExcelFloder + "/" + outName + ".zip";

        jsonObject.put("path", backPath);
        jsonObject.put("time_c", t2);
        boolean t = true;
        return resultsErr.size() > 0 ? resultsErr.get(0) : ResultGenerator.genSuccessResult(jsonObject);


//        return resultsErr.size() > 0 ? resultsErr.get(0) : ResultGenerator.genSuccessResult();
    }


    private void wrapWrite(List<AMZoutFeeInfo> resInfo, String folderToken) {

//        List<AMZoutFeeInfo> slowWays = (List<AMZoutFeeInfo>) resMap.get("慢船");

        Map<List<String>, List<AMZoutFeeInfo>> mapBoxes = resInfo.stream()
                .collect(Collectors.groupingBy(
                        AMZoutFeeInfo -> AMZoutFeeInfo.getAmZoutBoxes().stream()
                                .map(AMZoutBox::getBoxNum).sorted().collect(Collectors.toList()),
                        Collectors.toList()
                ));

        //有冗余小数方便大小排序，按大小顺序确定位置
//        long sizesBoxes = mapBoxes.keySet().stream().mapToInt(items -> items.size()).sum();

        List<String> allElements = mapBoxes.keySet().stream().flatMap(items -> items.stream())
                .sorted(Comparator.comparingDouble(Double::parseDouble)).
                collect(Collectors.toList());

        Map<String, AMZoutBox> amZoutBoxesAll = new HashMap<>();
        resInfo.forEach(new Consumer<AMZoutFeeInfo>() {
            @Override
            public void accept(AMZoutFeeInfo amZoutFeeInfo) {

                List<AMZoutBox> amZoutBoxes = amZoutFeeInfo.getAmZoutBoxes();
                if (amZoutBoxes != null) {
                    amZoutBoxes.forEach(new Consumer<AMZoutBox>() {
                        @Override
                        public void accept(AMZoutBox amZoutBox) {
                            //位置
                            int pos = allElements.indexOf(amZoutBox.getBoxNum());
                            amZoutBox.setBoxPos(pos);

                            if (!amZoutBoxesAll.containsKey(amZoutBox.getBoxNum())) {
//                                amZoutBoxesAll.add(amZoutBox);
                                amZoutBoxesAll.put(amZoutBox.getBoxNum(), amZoutBox);
                            }

                        }
                    });
                }


            }
        });

        //sku按字符升序
        Map maps = resInfo.stream().collect(Collectors.groupingBy(AMZoutFeeInfo::getSku, TreeMap::new, Collectors.toList()));

        AMZoutFeeInfo firstIn = resInfo.get(0);//获取文件名

        //        Map treeMap = new TreeMap<>(map);
        try {
            writeToExcel(maps, allElements, amZoutBoxesAll, firstIn.getObName(), firstIn.getObWays(), folderToken);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void writeToExcel(Map<String, List<AMZoutFeeInfo>> resInfo,
                              List<String> allElements,
                              Map<String, AMZoutBox> amZoutBoxesAll, String fileName, String obWays, String folderToken) throws IOException {
        Resource resource = new ClassPathResource("excel/SkuINBoxes_template.xlsx");

        InputStream fileInputStreamIN = resource.getInputStream();

        XSSFWorkbook workbook = new XSSFWorkbook(fileInputStreamIN);
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(0); // 获取第二个工作表
        int lenRows = resInfo.size();
        Object[] keys = resInfo.keySet().toArray();
        int start = 5;
        //SXSSFWorkbook 下没有移动操作
        sheet.shiftRows(start, sheet.getLastRowNum(), lenRows);


        final int[] allShoes = {0};
        for (int i = 0; i < lenRows; i++) {
            int la = sheet.getLastRowNum();
            int lass = sheet.getPhysicalNumberOfRows();
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(start + i);

            org.apache.poi.ss.usermodel.Cell skuCell = row.createCell(0);


            //M3 包装箱总数：
            org.apache.poi.ss.usermodel.Cell numShoesCell_yj = row.createCell('J' - 'A');//预计数量
            org.apache.poi.ss.usermodel.Cell numShoesCell_sj = row.createCell('K' - 'A');//装箱数量


//            org.apache.poi.ss.usermodel.Cell sku = row.getCell(0);
            String skuInfo = (String) keys[i];
            List<AMZoutFeeInfo> cellsInfo = resInfo.get(skuInfo);


            if (skuInfo == null) {
                boolean tt = true;
            }

            skuCell.setCellValue(skuInfo);

//            numShoesCell_yj.setCellValue();
            final int[] rowTotal = {0};
            cellsInfo.forEach(new Consumer<AMZoutFeeInfo>() {
                @Override
                public void accept(AMZoutFeeInfo amZoutFeeInfo) {
                    allShoes[0] = allShoes[0] + amZoutFeeInfo.getTotal();
                    rowTotal[0] = rowTotal[0] + amZoutFeeInfo.getTotal();

                    List<AMZoutBox> amZoutBoxes = amZoutFeeInfo.getAmZoutBoxes();

                    if (amZoutBoxes != null) {
                        amZoutBoxes.forEach(new Consumer<AMZoutBox>() {
                            @Override
                            public void accept(AMZoutBox amZoutBox) {

                                int sBox = 'M' - 'A';

                                int fPos = sBox + amZoutBox.getBoxPos();
//                                System.out.println("箱子位置>>>>>" + fPos + "" + amZoutBox.getBoxPos() + ">" + amZoutBox.getBoxNum());
                                org.apache.poi.ss.usermodel.Cell boxNum = row.createCell(fPos);//装箱数量
                                boxNum.setCellValue(amZoutBox.getBoxShoes());

                            }
                        });


                    }


                }
            });
            numShoesCell_yj.setCellValue(rowTotal[0]);
            numShoesCell_sj.setCellValue(rowTotal[0]);


//            sku.setCellValue(resInfo.get(keys[i]));


        }


        //SKU 总数：17（592 件商品）
        sheet.getRow(2).getCell(0).setCellValue("SKU 总数：" + lenRows + "（" + allShoes[0] + "件商品）");
//        allElements 箱数
        sheet.getRow(2).getCell('M' - 'A').setCellValue(allElements.size());

        int footStart = 7;
        //箱子标题
        allElements.forEach(new Consumer<String>() {
            @Override
            public void accept(String s) {

                int index = allElements.indexOf(s);
                sheet.getRow(5 - 1).createCell('M' - 'A' + index).setCellValue("包装箱" + (index + 1) + "数量");

                //箱子名称 P1-B1...
                int fStart = footStart + lenRows;
                sheet.getRow(fStart - 1).createCell('M' - 'A' + index).setCellValue("P1-B" + (index + 1));

                AMZoutBox amZoutBox = amZoutBoxesAll.get(s);

                sheet.getRow(fStart + 1 - 1).createCell('M' - 'A' + index).setCellValue(amZoutBox.getBoxWeight());
                sheet.getRow(fStart + 2 - 1).createCell('M' - 'A' + index).setCellValue(amZoutBox.getBoxWidth());
                sheet.getRow(fStart + 3 - 1).createCell('M' - 'A' + index).setCellValue(amZoutBox.getBoxLength());
                sheet.getRow(fStart + 4 - 1).createCell('M' - 'A' + index).setCellValue(amZoutBox.getBoxHeight());


            }
        });
        //lb/in
        //25 重量 26 宽 27 长度 28 高度


//        String folder = "报关单";

//        String folderToken = "建货件";

        String folderPrefix = realPathExcel;
        File fileDir = new File((folderPrefix + C_ResultExcelFloder + "/" + folderToken));
        if (!fileDir.exists()) {
            fileDir.mkdirs();
            Lg.i("saveToAssets", "文件的路径不存在，创建:", fileDir.getPath());

        }

        String timeCreated = "-" + CommonUtils.getStringMonthAndDay();
        String fName = fileName.split("\\.xls")[0];//名称里可能日期带点
        String[] nameTags = fName.split("～");//中文的
        if (nameTags != null && nameTags.length > 0) {
            fName = nameTags[nameTags.length - 1];
        }

        String pathBack = fileDir.getPath() + "/" + "建货件" + "-" + fName + "-" + obWays + timeCreated + ".xlsx";
        FileOutputStream out = new FileOutputStream(pathBack);
        workbook.write(out);
        if (fileInputStreamIN != null) {
            fileInputStreamIN.close();
        }
        out.close();
        workbook.close();


    }


    private static List<String> staticKeys = new ArrayList<>(Arrays.asList("SKU", "装箱率", "箱量/箱数", "数量", "重量", "宽", "长", "高"));
    private static List<String> staticKeysTmp = new ArrayList<>(Arrays.asList("SKU", "装箱率", "箱量/箱数", "数量", "重量", "宽", "长", "高"));

    //M-建货件箱数据读取
    public static WrapListWithMsg<AMZoutFeeInfo> getSKUWithBoxes(File fileIn) throws IOException {

        ReadableWorkbook wb = new ReadableWorkbook(fileIn);

        WrapListWithMsg<AMZoutFeeInfo> msgBoxes = new WrapListWithMsg<>();
        List<AMZoutFeeInfo> boxDetails = new ArrayList<>();
        List<String> errStrs = new ArrayList<>();
        msgBoxes.setListData(boxDetails);//放前面放后面一样？地址指向？ List<String> 不行？
        Stream<Sheet> sheets = wb.getSheets(); //获取Workbook中sheet的个数

//        Worksheet worksheet = new Worksheet(wb,fileIn.getName());

        //到2050年时间戳都是13位
//        String uuid = "G" + System.currentTimeMillis();
        String uuid = String.valueOf(System.currentTimeMillis());
        boolean tt1 = true;
        sheets.forEach(sheet -> {
            String name = sheet.getName(); //获取每个sheet的名称
            if (name.contains("不发") || name.contains("数据源")) {//过滤表

            } else {
                org.dhatim.fastexcel.reader.SheetVisibility sheetVisibility = sheet.getVisibility(); //获取每个sheet的可见性
                if (sheetVisibility == org.dhatim.fastexcel.reader.SheetVisibility.VISIBLE && name != null && name.length() > 0) {
//                    if (!(name.contains("美森") || name.contains("慢船"))) {
//                        String tip = "不是美森或慢船建货件表格";
//                        if (!errStrs.contains(tip)) {
//                            errStrs.add("不是美森或慢船表格");
//                        }
//                        return;
//                    }

                    List<AMZoutFeeInfo> boxDetailsSheet = new ArrayList<>();
                    //内置，不然合并拼箱会污染

                    try { // Get a stream of rows from the sheet
                        List<org.dhatim.fastexcel.reader.Row> rr = sheet.read();

                        if (!rr.isEmpty()) {
                            int size = rr.size();

//                            org.dhatim.fastexcel.reader.Row rowTitle = rr.get(0);

                            org.dhatim.fastexcel.reader.Row row0 = rr.get(0);
                            Map<String, String> keysNeed = new HashMap<>();//地址 A、B...


                            row0.stream().forEach(cell -> {
                                if (cell == null) {
                                    return;
                                }
                                Object cellData = cell.getValue();
                                if (cellData instanceof String) {
                                    String colKey = (String) cellData;
                                    staticKeys.forEach(s -> {
                                        if (s.contains(colKey) || colKey.contains(s)) {
                                            String address = String.valueOf(cell.getAddress());
                                            String firstChar = String.valueOf(address.charAt(0));


                                            if (!keysNeed.containsKey(firstChar)) {
                                                keysNeed.put(s, firstChar);
//                                                keysNeed.put(firstChar, s);
                                            }
                                            if (staticKeysTmp.contains(s)) {

                                                staticKeysTmp.remove(s);
                                            }

                                        }

                                    });
                                }
                            });
                            if (staticKeysTmp.size() > 0) {//缺失关键列
                                errStrs.add(name + ">未检测到关键列：" + staticKeysTmp.toString());
                                msgBoxes.setErrMsg(errStrs.toString());//放前面放后面一样？地址指向？
                                return;
                            }


                            for (int i = 1; i < size; i++) {
                                org.dhatim.fastexcel.reader.Row row = rr.get(i);


//                                details.setSheetName(name);
                                //底部空一大片又突然出现一小格的情况,或者隐藏行
//                                System.out.println(">>>项数#>" + row.getCellCount());
                                if (row == null || row.getCellCount() <= 2) {//咩有SKU略过
                                    boolean tt = true;
                                    continue;
                                }

                                AMZoutFeeInfo amZoutFeeInfo = new AMZoutFeeInfo();
                                final AMZoutBox[] amZoutBox = {new AMZoutBox()};
                                amZoutFeeInfo.setObName(fileIn.getName());
//                                if (name.contains("慢船")) {
//                                    amZoutFeeInfo.setObWays("慢船");
//                                } else if (name.contains("美森")) {
//                                    amZoutFeeInfo.setObWays("美森");
//                                }

                                amZoutFeeInfo.setObWays(name);

                                amZoutFeeInfo.setAmZoutBoxes(new ArrayList<>());

                                //A~J

                                int finalI = i;
                                row.stream().forEach(cell -> {

                                    if (cell == null) {
                                        return;
                                    }
                                    Object cellData = cell.getValue();

                                    String address = String.valueOf(cell.getAddress());

                                    String item = String.valueOf(cellData);

                                    if (keysNeed.containsKey("SKU") && address.startsWith(keysNeed.get("SKU"))) {//SKU
                                        amZoutFeeInfo.setSku(item);

                                        boolean tt = amZoutFeeInfo.getSku().equals("null");
                                        if (StringUtils.isNullOrEmpty(amZoutFeeInfo.getSku()) || amZoutFeeInfo.getSku().equals("null")) {

                                            boolean ttt = true;
                                        }


                                    } else if (keysNeed.containsKey("装箱率") && address.startsWith(keysNeed.get("装箱率"))) {//装箱率，判断是否为一箱>>读取装箱率，因为箱数底下还有个总计。。
                                        if (StringUtils.isNumberL(item)) {//有值就确定为一箱，为空确定为合并拼箱


//                                                    amZoutBox[0].setBoxShoes(Integer.parseInt(item));//此箱装箱率
                                            amZoutBox[0].setBoxShoesAll(Integer.parseInt(item));//此箱装箱率
                                            amZoutBox[0].setBoxNum(uuid + finalI);
                                        } else {//为空，合并拼箱，取上一个有效值

                                            //用拷贝，不然会污染！！尤其是单独箱子的实际装箱
                                            amZoutBox[0] = SerializationUtils.clone(boxDetailsSheet.get(finalI - 2).getAmZoutBoxes().get(0));

                                        }


                                    } else if (keysNeed.containsKey("箱量/箱数") && address.startsWith(keysNeed.get("箱量/箱数"))) {//箱量,!!!按箱数分组！！！,判断是否为一箱>>读取装箱率，因为箱数底下还有个总计
                                        if (StringUtils.isNumberL(item)) {
                                            amZoutFeeInfo.setBoxes(Integer.parseInt(item));
                                        } else {//为空，表示是合并，取上一个非空的！
//                                                    Lg.e(address + ">箱数格式错误", item);
                                        }

                                    } else if (keysNeed.containsKey("数量") && address.startsWith(keysNeed.get("数量"))) {//数量
                                        if (StringUtils.isNumberL(item)) {
                                            int actual = Integer.parseInt(item);
                                            amZoutFeeInfo.setTotal(actual);

                                            if (actual == amZoutBox[0].getBoxShoesAll() * amZoutFeeInfo.getBoxes()) {//是整箱的话，拆箱，实际装箱变为装箱率的量


                                            } else {//拼箱的话就是实际装箱
                                                amZoutBox[0].setBoxShoes(actual);//实际装箱
                                            }

                                        } else {
//                                                    Lg.e(address + ">数量格式错误", item);
                                        }

                                    } else if (keysNeed.containsKey("重量") && address.startsWith(keysNeed.get("重量"))) {//重量>包装箱

                                        if (StringUtils.isNumberL(item)) {
//                                                    amZoutFeeInfo.setSku_weight_all(Float.parseFloat(item));
                                            //StringUtils.get2Numbers(String.valueOf(Double.parseDouble(nextLine[9]) * 0.454)
                                            amZoutBox[0].setBoxWeight(StringUtils.get2Numbers(String.valueOf(Double.parseDouble(item) * 2.204)));

                                        } else {
//                                                    Lg.e(address + ">重量格式错误", item);
                                        }

                                    } else if (keysNeed.containsKey("宽") && address.startsWith(keysNeed.get("宽"))) {//宽
                                        if (StringUtils.isNumberL(item)) {

                                            double inW = StringUtils.get2Numbers(String.valueOf(Double.parseDouble(item) * 0.3937));

                                            amZoutBox[0].setBoxWidth(inW);
                                        } else {
//                                                    Lg.e(address + ">重量格式错误", item);
                                        }


                                    } else if (keysNeed.containsKey("长") && address.startsWith(keysNeed.get("长"))) {//长
                                        if (StringUtils.isNumberL(item)) {
                                            double inW = StringUtils.get2Numbers(String.valueOf(Double.parseDouble(item) * 0.3937));
                                            amZoutBox[0].setBoxLength(inW);
                                        } else {
//                                                    Lg.e(address + ">重量格式错误", item);
                                        }

                                    } else if (keysNeed.containsKey("高") && address.startsWith(keysNeed.get("高"))) {//高
                                        if (StringUtils.isNumberL(item)) {
                                            double inW = StringUtils.get2Numbers(String.valueOf(Double.parseDouble(item) * 0.3937));
                                            amZoutBox[0].setBoxHeight(inW);
                                        } else {
//                                                    Lg.e(address + ">重量格式错误", item);
                                        }

                                    }

//                                            System.out.println(">>>#" + ((char) j) + ">ad>" + address + ">>" + item + ">merge>");


                                });

                                if (amZoutFeeInfo.getTotal() == amZoutBox[0].getBoxShoesAll() * amZoutFeeInfo.getBoxes()) {//是整箱的话，拆箱，实际装箱变为装箱率的量
                                    amZoutBox[0].setBoxShoes(amZoutBox[0].getBoxShoesAll());//实际装箱

                                    if (amZoutBox[0].getBoxNum() == null) {
//                                        boolean tt = true;
//                                        return;
                                        amZoutBox[0].setBoxNum("0");//源头不好截，从结果入手
                                    }
//                                    Double boxNo = Double.parseDouble(amZoutBox[0].getBoxNum());
                                    BigDecimal boxNo = new BigDecimal(amZoutBox[0].getBoxNum());
                                    for (int j = 0; j < amZoutFeeInfo.getBoxes(); j++) {

//                                        AMZoutBox amZoutBox1 = new AMZoutBox();
//                                        amZoutBox1 = amZoutBox[0];
//                                        amZoutBox1.setBoxNum(amZoutBox[0].getBoxNum());

                                        AMZoutBox cloneAMZoutBox = SerializationUtils.clone(amZoutBox[0]);


                                        //两位小数，模拟99箱
//                                        long boxNo = Long.valueOf((amZoutBox[0].getBoxNum()));
//                                        BigDecimal boxNoPj = BigDecimal.valueOf(boxNo + (j / 100.0f));
//                                        String noIN = boxNo.toString().split("//.")[0];
                                        System.out.println("BigDecimal>>>>" + boxNo.toPlainString());
                                        if (boxNo.toPlainString().contains(".")) {
                                            BigDecimal boxNoPj = new BigDecimal(boxNo.toPlainString() + j);
                                            cloneAMZoutBox.setBoxNum(boxNoPj.toPlainString());
//                                        System.out.println("箱子>>>>#" + amZoutFeeInfo.getBoxes() + ">>" + boxNoPj.toPlainString());
                                            amZoutFeeInfo.getAmZoutBoxes().add(cloneAMZoutBox);
                                        } else {
                                            BigDecimal boxNoPj = new BigDecimal(boxNo.toPlainString() + "." + j);
                                            cloneAMZoutBox.setBoxNum(boxNoPj.toPlainString());
//                                        System.out.println("箱子>>>>#" + amZoutFeeInfo.getBoxes() + ">>" + boxNoPj.toPlainString());
                                            amZoutFeeInfo.getAmZoutBoxes().add(cloneAMZoutBox);
                                        }


                                    }

                                } else {
                                    amZoutFeeInfo.getAmZoutBoxes().add(amZoutBox[0]);
                                }

                                boxDetailsSheet.add(amZoutFeeInfo);


                            }
                        }


                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    boxDetails.addAll(boxDetailsSheet);

                }
            }

        });
        //源头不好避免，从结果入手
        List<AMZoutFeeInfo> copyBox = boxDetails.stream().filter(new Predicate<AMZoutFeeInfo>() {
            @Override
            public boolean test(AMZoutFeeInfo amZoutFeeInfo) {
                return (!(StringUtils.isNullOrEmpty(amZoutFeeInfo.getSku())) && !amZoutFeeInfo.getSku().equals("null"));
            }
        }).collect(Collectors.toList());
        msgBoxes.setErrMsg(errStrs.toString());//放前面放后面一样？地址指向？
        msgBoxes.setListData(copyBox);

        wb.close();

        return msgBoxes;
    }


}
