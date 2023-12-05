package com.magicair.webpj.AFurui;


import com.alibaba.fastjson2.JSONObject;
import com.magicair.webpj.AFurui.model.AMZoutBox;
import com.magicair.webpj.AFurui.model.AMZoutFeeInfo;
import com.magicair.webpj.AFurui.model.BoxRules;
import com.magicair.webpj.AFurui.model.wrap.WrapListWithMsg;
import com.magicair.webpj.core.Result;
import com.magicair.webpj.core.ResultCode;
import com.magicair.webpj.core.ResultGenerator;
import com.magicair.webpj.utils.CommonUtils;
import com.magicair.webpj.utils.Lg;
import com.magicair.webpj.utils.StringUtils;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.RFC4180Parser;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.magicair.webpj.AFurui.ConstantFu.AMZ_title_FeeList;
import static com.magicair.webpj.AFurui.ConstantFu.C_ResultExcelFloder;
import static com.magicair.webpj.AFurui.PoiUtiles.findItemInNames;

/* 计费重
 *功能描述
 * @author lch
 * @date 2023/10/24
 * @param  * @param null
 * @return
 */
@Service
@Transactional
public class FuruiAMZcsvFeeServiceImpl {

    @Value("${web.excel-path}")
    private String realPathExcel;


    //    private static List<TreeMap> dataMapsCache;
//    private  List<AMZout> allRes = new ArrayList<>();

    private String outDateFile = "";


    //M-获取最新箱规文件
    public File[] getUploads(String folderPath) {
        String path = realPathExcel + folderPath;
        File fileDir = new File(path);
        if (!fileDir.exists()) {
            fileDir.mkdirs();
            Lg.i("saveToAssets", "文件的路径不存在，创建:", fileDir.getPath());

        }

        //创建File对象
//        File file = new File("D:\\test");
        //使用listFiles()方法过滤文件
        File[] files = fileDir.listFiles((dir, name) -> (name.endsWith(".csv")));
        if (files == null || files.length == 0) {
            return null;
        }
        //使用Arrays类中的sort方法按照文件名排序
//        Arrays.sort(files, (f1, f2) -> f1.getName().compareTo(f2.getName())); //升序
        Arrays.sort(files, (f1, f2) -> f2.getName().compareTo(f1.getName())); //降序
        //遍历输出文件名
        for (File f : files) {
            System.out.println(f.getName());
        }
        return files;//列出所有
    }

    public Result excelActionMergeAllCSVFile(String outFileName, List<File> inputs, File boxFile) {

        long t1 = System.currentTimeMillis();


        WrapListWithMsg<BoxRules> boxRulesWrap = null;
        try {
            boxRulesWrap = PoiUtiles.getBoxRulesList(null, null, boxFile);
            if (boxRulesWrap != null && boxRulesWrap.getErrMsg() != null && boxRulesWrap.getErrMsg().length() > 2) {//格式校验有误
                return ResultGenerator.genFailResult("解析箱规文件失败", boxRulesWrap.getErrMsg());
            }
        } catch (IOException e) {
            e.printStackTrace();
            return ResultGenerator.genFailResult("解析箱规文件失败", e);
        }

        //箱规分组！
        Map<String, List<BoxRules>> boxRulesGroup = boxRulesWrap.getListData().stream().collect(Collectors.groupingBy(BoxRules::getSKU));


        List<AMZoutFeeInfo> allRes = new ArrayList<>();//不能用全局的，多次结果会缓存？对多线程也有影响？？

        String[] warning = {null};
        List<Runnable> tasks = new ArrayList<>();
        List<Result> resultsErr = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            int finalI = i;
            Runnable runnable = () -> {
                Result result = inputConvertAllList(inputs.get(finalI));
                if (result.getCode() == ResultCode.SUCCESS.code) {
//                        return resultMap;
                    // 使用synchronized保护allRes，防止多线程下数据丢失等异常
                    synchronized (allRes) {
                        allRes.addAll((List<AMZoutFeeInfo>) result.getData());
                    }
//                    allRes.addAll((List<AMZout>) result.getData());
                } else {
                    resultsErr.add(result);
                }

            };
            tasks.add(runnable);
        }

        //用ForkJoinPool会导致解析数据混乱？？？！！>>>>非synchronized下allRes被多线程同时修改的原因！
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
        // //用ForkJoinPool 多线程导致的？？
        //333>6874
        //324>6450
        //326>6560
        //337>6930 *
        Lg.i(">>>", "初始所有项" + allRes.size());

        String path = null;
        try {
            path = generateExcelAll(allRes, outFileName, boxRulesGroup);

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
                    jsonObject.put("msg", Arrays.toString(warning) + msgOld);
                } else {
                    jsonObject.put("msg", Arrays.toString(warning));
                }
            }

        } else {
            jsonObject.put("path", path);
        }
        jsonObject.put("time_c", t2);
        boolean t = true;
        return ResultGenerator.genSuccessResult(jsonObject);

    }


    public Result inputConvertAllList(File inputFile) {

        List<String> resMsg = new ArrayList<>();//异常信息

        List<AMZoutFeeInfo> treeParser = new ArrayList<>();
//        Path filePath = Paths.get(path);

        String fileName = inputFile.getName();

        String filterName = fileName.substring(fileName.indexOf("～") + 1);
//        String[] tags = fileName.split("-");

        //第一部分：MDW2
        //第三部分：2023.9.20
        //第五部分：美森
        //第七部分：US-BS
        List<String> tagList = findItemInNames(filterName);

        if (tagList.size() < 3) {
            resMsg.add("文件:" + filterName + "名称格式错误");
            return ResultGenerator.genFailResult("文件名格式错误", resMsg);
        }

        String stock = tagList.get(0);
        //仓库
//        stock = stock.substring(stock.indexOf("～") + 1);//保存服务端后有拼接!!是中文的～！！！！

        //渠道
        String obWay = tagList.size() < 3 ? null : tagList.get(2);

        String outDate = null;
        //船期
        if (outDate == null) {
            String regex = "\\d{4}\\.\\d{1,2}\\.\\d{1,2}";
            // Create a Pattern object
            Pattern p = Pattern.compile(regex);
            // Create a Matcher object
            Matcher m = p.matcher(fileName);
            // Loop to find the matching substrings
            while (m.find()) {
                // Print the matching results
                outDate = m.group();
                System.out.println("匹配日期：" + m.group());
            }
        }
        if (!StringUtils.isNullOrEmpty(outDate)) {
            if (StringUtils.isBlank(outDateFile)) {
                outDateFile = outDateFile + outDate;
            } else {
                outDateFile = outDateFile + "-" + outDate;
            }

        }

        String fileCharSet = "UTF-8";//默认
        try {
            fileCharSet = CommonUtils.getCharsetName(inputFile);
            Lg.i("文件编码-fff》》》", fileCharSet);
        } catch (IOException e) {
            e.printStackTrace();
        }

        try (FileReader fr = new FileReader(inputFile, Charset.forName(fileCharSet))) {
            // 使用CsvToBeanBuilder解析csv文件
//            Lg.i("文件编码》》》", fr.getEncoding());

            CSVReader reader = new CSVReaderBuilder(fr).withCSVParser(new RFC4180Parser()).build();
            AMZoutFeeInfo tmpTop = new AMZoutFeeInfo();
            AMZoutFeeInfo zoutFeeInfo = null;

//            List<AMZoutBox> amZoutBoxesTmp = new ArrayList<>();
            List<AMZoutBox> amZoutBoxesTmp = null; // 初始化一个容量为 10 的 List
            boolean isCm = false;//单位不同体积重计算不同，cm/6000 ,in/167
            int isPJ = -1;
            for (Iterator<String[]> it = reader.iterator(); it.hasNext(); ) {
                String[] nextLine = it.next();

                String joined = String.join(",", nextLine); // 用逗号连接数组元素
                boolean isBlank = StringUtils.isBlank(joined);
                if (isBlank) {
                    continue;
                }

//                String toStr = Arrays.toString(nextLine).trim();
                String toStr = String.join(",", nextLine);
                // nextLine[] is an array of values from the line
                if (nextLine.length > 0) {
                    if (toStr.contains("货件编号")) {//FBA编号
                        tmpTop.setObNumber(nextLine[1]);
                        continue;
                    } else if (toStr.contains("货件名称")) {
                        tmpTop.setObName(nextLine[1]);
                        continue;
                    } else if (toStr.contains("配送地址")) {
                        tmpTop.setAddress(nextLine[1]);
                        continue;
                    } else if (toStr.contains("箱子数量")) {
                        int num = Integer.parseInt(nextLine[1]);
                        tmpTop.setBoxes(num);
//                        amZoutBoxesTmp = new ArrayList<>(num);
//                        amZoutBoxesTmp = new ArrayList<>(Collections.nCopies(num, new AMZoutBox()));

                        // 创建一个包含 10 个 AMZoutBox 对象的流
                        Stream<AMZoutBox> stream = Stream.generate(AMZoutBox::new).limit(num);
                        // 收集流中的元素到一个列表中
                        amZoutBoxesTmp = stream.collect(Collectors.toList());
                        // 现在你可以对 list 进行增删改操作了
                        continue;
                    } else if (toStr.contains("SKU 数量")) {
                        tmpTop.setSkuNum(Integer.parseInt(nextLine[1]));
                        continue;
                    } else if (toStr.startsWith("商品数量")) {//拼箱后面有一行还有"商品数量"字样
                        tmpTop.setTotalAll(Integer.parseInt(nextLine[1]));
                        continue;
                    }
                }

                //商品开始
                if (toStr.contains("FNSKU") && toStr.contains("商品名称")) {

                    if (!toStr.contains("箱号")) {
                        isPJ = 1;//拼箱
                    } else {
                        isPJ = 0; //整箱
                    }
                    continue;
                }
                //拼接
                if (isPJ == 1 && nextLine.length > 9) {//开始读取具体商品了

                    if (!nextLine[0].isEmpty()) {


                        zoutFeeInfo = new AMZoutFeeInfo();
                        zoutFeeInfo.setSku(nextLine[0]);
                        zoutFeeInfo.setName(nextLine[1]);
                        zoutFeeInfo.setASIN(nextLine[2]);
                        zoutFeeInfo.setFNSKU(nextLine[3]);
                        zoutFeeInfo.setState(nextLine[4]);
                        zoutFeeInfo.setTotal(Integer.parseInt(nextLine[8]));

                        zoutFeeInfo.setObName(tmpTop.getObName());
                        zoutFeeInfo.setObNumber(tmpTop.getObNumber());
                        zoutFeeInfo.setAddress(tmpTop.getAddress());
                        zoutFeeInfo.setBoxes(tmpTop.getBoxes());
                        zoutFeeInfo.setStock(stock);
                        zoutFeeInfo.setObWays(obWay);
                        zoutFeeInfo.setObDate(outDate);

                        Lg.i("挨着读取>拼接>>>", ">", nextLine);


                        //第8列箱子标题（箱号    第9列具体数据
                        //箱子名称
                        //包装箱重量（磅）：
                        //包装箱长度（英寸）：
                        //包装箱宽度（英寸）：
                        //包装箱高度（英寸）：）

                        List<AMZoutBox> amZoutBoxes = new ArrayList<>();
                        for (int i = 9; i < nextLine.length; i++) {
                            if (StringUtils.isNumber(nextLine[i])) {//读取箱子里的数据与箱子位置
                                AMZoutBox amZoutBox = new AMZoutBox();
                                amZoutBox.setBoxPos(i);
                                amZoutBox.setBoxShoes(Integer.parseInt(nextLine[i]));
                                amZoutBoxes.add(amZoutBox);
                            }
                        }
                        zoutFeeInfo.setAmZoutBoxes(amZoutBoxes);
                        treeParser.add(zoutFeeInfo);
                    } else if (!nextLine[8].isEmpty() && amZoutBoxesTmp != null) {

                        String boxItem = nextLine[8];
                        if (boxItem.contains("箱号")) {

                            for (int i = 0; i < amZoutBoxesTmp.size(); i++) {
                                amZoutBoxesTmp.get(i).setBoxPos(i + 9);
                                amZoutBoxesTmp.get(i).setBoxNum(nextLine[i + 9]);

                            }
                            boolean t = true;
                        } else if (boxItem.contains("箱子名称")) {

                            for (int i = 0; i < amZoutBoxesTmp.size(); i++) {
                                amZoutBoxesTmp.get(i).setBoxName(nextLine[i + 9]);
                            }

                        } else if (boxItem.contains("包装箱重量")) {

                            for (int i = 0; i < amZoutBoxesTmp.size(); i++) {
                                amZoutBoxesTmp.get(i).setBoxWeight(Double.parseDouble(nextLine[i + 9]));
                            }

                        } else if (boxItem.contains("包装箱长度")) {
                            if (boxItem.contains("英寸")) {
                                isCm = false;
                            } else if (boxItem.contains("厘米")) {
                                isCm = true;
                            }

                            for (int i = 0; i < amZoutBoxesTmp.size(); i++) {
                                amZoutBoxesTmp.get(i).setBoxLength(Double.parseDouble(nextLine[i + 9]));
                            }

                        } else if (boxItem.contains("包装箱宽度")) {


                            for (int i = 0; i < amZoutBoxesTmp.size(); i++) {
                                amZoutBoxesTmp.get(i).setBoxWidth(Double.parseDouble(nextLine[i + 9]));
                            }

                        } else if (boxItem.contains("包装箱高度")) {

                            for (int i = 0; i < amZoutBoxesTmp.size(); i++) {
                                amZoutBoxesTmp.get(i).setBoxHeight(Double.parseDouble(nextLine[i + 9]));
                            }

                        }

                    }


                }
                //整箱
                if (isPJ == 0 && !nextLine[0].isEmpty() && nextLine.length > 9) {//开始读取具体商品了
                    zoutFeeInfo = new AMZoutFeeInfo();
                    zoutFeeInfo.setSku(nextLine[0]);
                    zoutFeeInfo.setName(nextLine[1]);
                    zoutFeeInfo.setASIN(nextLine[2]);
                    zoutFeeInfo.setFNSKU(nextLine[3]);
                    zoutFeeInfo.setState(nextLine[4]);
                    try {
                        zoutFeeInfo.setTotal(Integer.parseInt(nextLine[15]));
                    } catch (NumberFormatException e) {
//                        e.printStackTrace();
                        Lg.e("NumberFormatException>>>" + fileName, e.getMessage());
                    }

                    zoutFeeInfo.setObName(tmpTop.getObName());
                    zoutFeeInfo.setObNumber(tmpTop.getObNumber());
                    zoutFeeInfo.setAddress(tmpTop.getAddress());
                    zoutFeeInfo.setBoxes(tmpTop.getBoxes());
                    zoutFeeInfo.setStock(stock);
                    zoutFeeInfo.setObWays(obWay);
                    zoutFeeInfo.setObDate(outDate);


                    // 9(重量) 10 11 12

                    double rWeight = Double.parseDouble(nextLine[9]);

                    double rate = isCm ? 6000 : 167;
                    //体积重
                    double tW = (Double.parseDouble(nextLine[10]) * Double.parseDouble(nextLine[11]) * Double.parseDouble(nextLine[12])) / rate;
                    //计费重
                    zoutFeeInfo.setSku_weight_all(Math.max(rWeight, tW));
                    zoutFeeInfo.setStock(stock);
                    treeParser.add(zoutFeeInfo);
                }


                Lg.i("挨着读取>>>>", ">", nextLine);

            }
            List<AMZoutBox> finalAmZoutBoxesTmp = amZoutBoxesTmp;
            if (isPJ == 1) {//拼箱

                boolean finalIsCm = isCm;
                treeParser.forEach(new Consumer<AMZoutFeeInfo>() {
                    @Override
                    public void accept(AMZoutFeeInfo amZoutFeeInfo) {
                        amZoutFeeInfo.getAmZoutBoxes().forEach(new Consumer<AMZoutBox>() {
                            @Override
                            public void accept(AMZoutBox amZoutBox) {
                                finalAmZoutBoxesTmp.forEach(new Consumer<AMZoutBox>() {
                                    @Override
                                    public void accept(AMZoutBox amZoutBoxIn) {
                                        if (amZoutBoxIn.getBoxPos() == amZoutBox.getBoxPos()) {

                                            amZoutBox.setBoxWeight(amZoutBoxIn.getBoxWeight());
                                            amZoutBox.setBoxHeight(amZoutBoxIn.getBoxHeight());
                                            amZoutBox.setBoxWidth(amZoutBoxIn.getBoxWidth());
                                            amZoutBox.setBoxLength(amZoutBoxIn.getBoxLength());
                                            amZoutBox.setBoxNum(amZoutBoxIn.getBoxNum());
                                            amZoutBox.setBoxName(amZoutBoxIn.getBoxName());

                                            //箱内总鞋数
                                            amZoutBoxIn.setBoxShoes(amZoutBoxIn.getBoxShoes() + amZoutBox.getBoxShoes());

                                            double rate = finalIsCm ? 6000 : 167;
                                            //体积重
                                            amZoutBoxIn.setT_weight(amZoutBoxIn.getBoxHeight() * amZoutBoxIn.getBoxWidth() * amZoutBoxIn.getBoxLength() / rate);
                                            amZoutBoxIn.setF_weight(Math.max(amZoutBoxIn.getBoxWeight(), amZoutBoxIn.getT_weight()));
                                            if (amZoutBox.getBoxShoesTmp() == null) {
                                                List<Integer> boxShoesTmp = new ArrayList<>();
                                                boxShoesTmp.add(amZoutBox.getBoxShoes());
                                                amZoutBox.setBoxShoesTmp(boxShoesTmp);
                                            } else {
                                                amZoutBox.getBoxShoesTmp().add(amZoutBox.getBoxShoes());
                                            }

                                            //覆盖到最后都是最大的
//                                        amZoutBox.setBoxShoesTmp(amZoutBoxIn.getBoxShoes());
                                            amZoutBox.setT_weight(amZoutBoxIn.getT_weight());
                                            amZoutBox.setF_weight(amZoutBoxIn.getF_weight());
                                        }

                                    }
                                });

                            }
                        });


                    }
                });
            }
            final double[] allFeeWeight = {0};
            int finalIsPJ = isPJ;
            treeParser.forEach(new Consumer<AMZoutFeeInfo>() {
                @Override
                public void accept(AMZoutFeeInfo amZoutFeeInfo) {
                    if (finalIsPJ == 1) {
                        amZoutFeeInfo.getAmZoutBoxes().forEach(new Consumer<AMZoutBox>() {
                            @Override
                            public void accept(AMZoutBox amZoutBox) {

                                finalAmZoutBoxesTmp.forEach(new Consumer<AMZoutBox>() {
                                    @Override
                                    public void accept(AMZoutBox amZoutBoxTmp) {
                                        if (amZoutBoxTmp.getBoxPos() == amZoutBox.getBoxPos()) {
                                            amZoutBox.setBoxShoesAll(amZoutBoxTmp.getBoxShoes());
                                        }
                                    }
                                });
                                //单个sku计费重
                                double sSkuWeight = (double) amZoutBox.getBoxShoes() / (double) amZoutBox.getBoxShoesAll() * amZoutBox.getF_weight();
                                amZoutBox.setSku_weight(sSkuWeight);
                                //所有箱子单个sku计费重和
                                amZoutFeeInfo.setSku_weight_all(amZoutBox.getSku_weight() + amZoutFeeInfo.getSku_weight_all());


                            }
                        });
                    }

                    allFeeWeight[0] = allFeeWeight[0] + amZoutFeeInfo.getSku_weight_all();
                }
            });

            treeParser.forEach(new Consumer<AMZoutFeeInfo>() {
                @Override
                public void accept(AMZoutFeeInfo amZoutFeeInfo) {
                    amZoutFeeInfo.setAllFeeWeight(allFeeWeight[0]);
                }
            });


            boolean tt = true;
            // 不需要调用close()方法，Java会自动关闭FileReader对象
        } catch (IOException e) {
            // 处理异常
        }


//        Map resDepotMap = treeParser.stream().collect(Collectors.groupingBy(AMZout::getStock));

        // 使用TreeMap对resDepotMap按key值升序排序
//        TreeMap<String, List<AMZout>> sortedResMap = new TreeMap<>(resDepotMap);
        if (resMsg.size() > 0) {
            return ResultGenerator.genSuccessResult(treeParser, resMsg.toString());
        } else {
            return ResultGenerator.genSuccessResult(treeParser);
        }

    }

    private static int NULL_Name = 0;

    //M-根据解析数据生成最终Excel
    public String generateExcelAll(List<AMZoutFeeInfo> amZoutFeeInfoList, String outFileName, Map<String, List<BoxRules>> boxRulesGroup) throws IOException {

        NULL_Name = 0;
        System.out.println("开始合成");

        List<String> nameRepeats = new ArrayList<>();
        XSSFWorkbook workbook = new XSSFWorkbook();

        Object ob = generateSheetWays(workbook, amZoutFeeInfoList, "计费重", boxRulesGroup);
        if (ob instanceof String) {//有重复表名出错了
            nameRepeats.add((String) ob);
        }
        NULL_Name = NULL_Name + 1;

        File fileDir = new File((realPathExcel + C_ResultExcelFloder));
        if (!fileDir.exists()) {
            fileDir.mkdirs();
            Lg.i("saveToAssets", "文件的路径不存在，创建:", fileDir.getPath());

        }

//        String outFileName = "仓库数据";

//        String trueFileNameStep1 = outDate + "-" + outFileName + "step1" + ".xlsx";

        String[] dates = outDateFile.split("-"); // 按照连字符分割字符串
        Arrays.sort(dates); // 对字符串数组进行排序
        String firstDate = dates[0]; // 取出第一个日期
        String lastDate = dates[dates.length - 1]; //

        String trueFileName = firstDate + "-" + lastDate + "-" + outFileName + ".xlsx";


//        String pathOutStep1 = fileDir.getPath() + '/' + trueFileNameStep1;

        String pathOut = fileDir.getPath() + '/' + trueFileName;

        String pathBack = C_ResultExcelFloder + "/" + trueFileName;
        // 将工作簿对象写入到文件中
        FileOutputStream out = new FileOutputStream(pathOut);
//        FileOutputStream out = new FileOutputStream("example仓库数据.xlsx");

        workbook.write(out);
        out.close();
        workbook.close();
        System.out.println("合成完成，文件位置：" + pathOut);


//        XSSFWorkbook workbookCopy = new XSSFWorkbook();


        if (nameRepeats.size() > 0) {
            return pathBack + "#表名重复，略过" + nameRepeats;
        } else {
            return pathBack;
        }

    }

    //M-单个默认fast，渠道1
    public org.apache.poi.ss.usermodel.Sheet generateSheetWays(XSSFWorkbook workbook,
                                                               List<AMZoutFeeInfo> amZoutFeeInfoList,
                                                               String name, Map<String, List<BoxRules>> boxRulesGroup) {
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

//        List<String> mTitles = sheetName.contains("US") ? C_titleM : C_titleKh;
        List<String> mTitles = AMZ_title_FeeList;


        int len = amZoutFeeInfoList.size();

        amZoutFeeInfoList.forEach(new Consumer<AMZoutFeeInfo>() {
            @Override
            public void accept(AMZoutFeeInfo amZoutFeeInfo) {
                List<BoxRules> rules = boxRulesGroup.get(amZoutFeeInfo.getSku());
                if (rules != null && rules.size() > 0) {
                    amZoutFeeInfo.setARTICLE(rules.get(0).getARTICLE());
                    amZoutFeeInfo.setErpCode(rules.get(0).getErpCode());
                    amZoutFeeInfo.setHS_CODE(rules.get(0).getHS_CODE());
                    amZoutFeeInfo.setErpSize(rules.get(0).getfSize());
                } else {

                }
            }
        });


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
                        XSSFCell cell0 = row.createCell(mTitles.indexOf(s));
                        cell0.setCellValue(title);
                        cell0.setCellStyle(styleIn);
                    }
                });
            } else {
                AMZoutFeeInfo deZoutFeeInfo = amZoutFeeInfoList.get(i - 1);
                XSSFRow row = sheet.createRow(i);

                mTitles.forEach(new Consumer<String>() {
                    @Override
                    public void accept(String s) {
                        int index = mTitles.indexOf(s);
                        XSSFCell cell0 = row.createCell(index);
                        cell0.setCellStyle(style);
//                        "渠道,计费重,仓库名,船期,票名,店铺名,货件名称,FBA编码,SKU,FNSKU,ASIN,件数,计费重,总计费重";
                        //末尾增加ERP款号 ERP尺码
                        switch (index) {
                            case 0://渠道
                                cell0.setCellValue(deZoutFeeInfo.getObWays());
                                break;
                            case 1://仓库名
                                cell0.setCellValue(deZoutFeeInfo.getStock());
                                break;
                            case 2://船期
                                cell0.setCellValue(deZoutFeeInfo.getObDate());
                                break;
                            case 3://票名 = 渠道+仓库+船期
                                cell0.setCellValue(deZoutFeeInfo.getObWays() + "-" + deZoutFeeInfo.getStock() + "-" + deZoutFeeInfo.getObDate());
                                break;
                            case 4://店铺名
//                                String[] nameTags = deZoutFeeInfo.getObName().split("-");

                                List<String> nameTags = findItemInNames(deZoutFeeInfo.getObName());

                                if (nameTags.size() > 3) {
                                    cell0.setCellValue(nameTags.get(3));
                                }

                                break;
                            case 5://货件名称

                                cell0.setCellValue(deZoutFeeInfo.getObName());
                                break;
                            case 6://FBA编码
                                cell0.setCellValue(deZoutFeeInfo.getObNumber());
                                break;
                            case 7://SKU
                                cell0.setCellValue(deZoutFeeInfo.getSku());
                                break;
                            case 8://FNSKU
                                cell0.setCellValue(deZoutFeeInfo.getFNSKU());
                                break;
                            case 9://ASIN
                                cell0.setCellValue(deZoutFeeInfo.getASIN());
                                break;
                            case 10://件数
                                cell0.setCellValue(deZoutFeeInfo.getTotal());
                                break;
                            case 11://计费重
                                cell0.setCellValue(deZoutFeeInfo.getSku_weight_all());
                                break;
                            case 12://总计费重
                                cell0.setCellValue(deZoutFeeInfo.getAllFeeWeight());
                                break;


                            case 13://erp code
                                if (deZoutFeeInfo.getErpCode() != null) {
                                    if (deZoutFeeInfo.getErpCode().contains(".")) {
                                        //.需要转义！！！！
                                        String[] er = deZoutFeeInfo.getErpCode().split("\\.");
                                        cell0.setCellValue(er[0]);
                                    } else {
                                        cell0.setCellValue(deZoutFeeInfo.getErpCode());
                                    }

                                }

                                break;
                            case 14://erp size

                                if (StringUtils.isNumber(deZoutFeeInfo.getErpSize())) {
                                    float sizeERP = Float.parseFloat(deZoutFeeInfo.getErpSize());
                                    String format = sizeERP % 1 == 0 ? "%.0f" : "%.1f";
                                    // 使用String.format()方法来保留整数或一位小数
                                    String formatted = String.format(format, sizeERP);

                                    cell0.setCellValue(formatted);
                                }


                                break;
                        }


                    }
                });


            }
        }

        // 整箱的不用合并？
        mergeSameCellValue(sheet, 12);

        PoiUtiles.adjustAutoWidth(sheet, mTitles.size());
//        dataMap.clear();//清缓存
        return sheet;
    }

    // 定义一个方法，判断两个单元格的值是否相等
    public static boolean isSameCellValue(Cell cell1, Cell cell2) {
        if (cell1 == null || cell2 == null) {
            return false;
        }
        if (cell1.getCellType() != cell2.getCellType()) {
            return false;
        }
        switch (cell1.getCellType()) {
            case STRING:
                return cell1.getStringCellValue().equals(cell2.getStringCellValue());
            case NUMERIC:
                return cell1.getNumericCellValue() == cell2.getNumericCellValue();
            case BOOLEAN:
                return cell1.getBooleanCellValue() == cell2.getBooleanCellValue();
            case FORMULA:
                return cell1.getCellFormula().equals(cell2.getCellFormula());
            default:
                return false;
        }
    }

    // 定义一个方法，根据指定的列索引，合并相同值的单元格-bing一次成功
    public static void mergeSameCellValue(Sheet sheet, int columnIndex) {
        // 获取最后一行的行号
        int lastRowNum = sheet.getLastRowNum();
        // 定义一个变量，记录合并开始的行号
        int startRowNum = -1;
        // 遍历所有的行
        for (int i = 0; i <= lastRowNum; i++) {
            // 获取当前行
            Row row = sheet.getRow(i);
            // 如果当前行为空，跳过
            if (row == null) {
                continue;
            }
            // 获取当前行指定列的单元格
            Cell cell = row.getCell(columnIndex);
            // 如果当前单元格为空，跳过
            if (cell == null) {
                continue;
            }
            // 如果合并开始的行号为-1，表示还没有开始合并，赋值为当前行号
            if (startRowNum == -1) {
                startRowNum = i;
                continue;
            }
            // 获取上一行指定列的单元格
            Cell prevCell = sheet.getRow(i - 1).getCell(columnIndex);
            // 如果当前单元格和上一单元格的值相等，继续遍历下一行
            if (isSameCellValue(cell, prevCell)) {
                continue;
            }
            // 如果当前单元格和上一单元格的值不相等，且合并开始的行号和当前行号不相等，表示有需要合并的单元格
            if (startRowNum != i - 1) {
                // 创建一个合并区域对象，指定起始行号，结束行号，起始列号，结束列号
                CellRangeAddress region = new CellRangeAddress(startRowNum, i - 1, columnIndex, columnIndex);
                // 在工作表上添加合并区域
                sheet.addMergedRegion(region);
            }
            // 更新合并开始的行号为当前行号
            startRowNum = i;
        }
        // 遍历结束后，如果合并开始的行号不等于最后一行的行号，表示还有需要合并的单元格
        if (startRowNum != lastRowNum) {
            // 创建一个合并区域对象，指定起始行号，结束行号，起始列号，结束列号
            CellRangeAddress region = new CellRangeAddress(startRowNum, lastRowNum, columnIndex, columnIndex);
            // 在工作表上添加合并区域
            sheet.addMergedRegion(region);
        }
    }


}
