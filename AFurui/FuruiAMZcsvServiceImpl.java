package com.magicair.webpj.AFurui;


import com.alibaba.fastjson2.JSONObject;
import com.magicair.webpj.AFurui.model.*;
import com.magicair.webpj.AFurui.model.wrap.WrapListWithMsg;
import com.magicair.webpj.core.Result;
import com.magicair.webpj.core.ResultCode;
import com.magicair.webpj.core.ResultGenerator;
import com.magicair.webpj.utils.CommonUtils;
import com.magicair.webpj.utils.Lg;
import com.magicair.webpj.utils.StringUtils;
import com.magicair.webpj.utils.ZipUtils;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.RFC4180Parser;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFFont;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.formula.FormulaParseException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellAddress;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.magicair.webpj.AFurui.ConstantFu.*;
import static com.magicair.webpj.AFurui.PoiUtiles.getSkuModel;
import static com.magicair.webpj.AFurui.PoiUtiles.getSkuShenbaoAll;

/* 仓库汇总
 *功能描述
 * @author lch
 * @date 2023/10/24
 * @param  * @param null
 * @return
 */
@Service
@Transactional
public class FuruiAMZcsvServiceImpl {

    @Value("${web.excel-path}")
    private String realPathExcel;


    //    private static List<TreeMap> dataMapsCache;
//    private  List<AMZout> allRes = new ArrayList<>();

    private String outDate;

    //M-获取最新箱规文件货上传文件
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
        //xlsx 货件信息表
        File[] files = fileDir.listFiles((dir, name) -> (name.endsWith(".csv") || name.endsWith(".xlsx")));
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

    public Result excelActionMergeAllCSVFile(String outFolder, List<File> inputs, File boxFile, File shenbaoFile, TEMPLATE_company isFurui, boolean isUS) {


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

        //申报要素
        WrapListWithMsg<ShenBaoInfo> shenBaoWrap = null;
        try {
            shenBaoWrap = PoiUtiles.getShenBaoInfoList(shenbaoFile);
            if (shenBaoWrap != null && shenBaoWrap.getErrMsg() != null && shenBaoWrap.getErrMsg().length() > 2) {//格式校验有误
                return ResultGenerator.genFailResult("解析申报要素文件失败", shenBaoWrap.getErrMsg());
            }
        } catch (IOException e) {
            e.printStackTrace();
            return ResultGenerator.genFailResult("解析申报要素文件失败", e);
        }

        // 使用toMap方法替代groupingBy方法，并指定HashMap作为结果容器
        //因为HashMap允许一个null键>>>备用
//        Map<String, List<ShenBaoInfo>> shenBaoGroupWithNUllkey = shenBaoWrap.getListData().stream()
//                .collect(Collectors.toMap(
//                        ShenBaoInfo::getStyle,
//                        p -> {
//                            List<ShenBaoInfo> list = new ArrayList<>();
//                            list.add(p);
//                            return list;
//                        },
//                        (left, right) -> {
//                            left.addAll(right);
//                            return left;
//                        },
//                        HashMap::new
//                ));
        //申报要素按照款号分组！
        Map<String, List<ShenBaoInfo>> shenBaoGroup = shenBaoWrap.getListData().stream().collect(Collectors.groupingBy(ShenBaoInfo::getStyle));


        long t1 = System.currentTimeMillis();
//        dataMapsCache = new ArrayList<>();
        List<AMZout> allRes = new ArrayList<>();//不能用全局的，多次结果会缓存？对多线程也有影响？？


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
                        WrapListWithMsg<AMZout> amZoutWrapListWithMsg = (WrapListWithMsg<AMZout>) result.getData();
                        allRes.addAll(amZoutWrapListWithMsg.getListData());
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
        allRes.sort(new Comparator<AMZout>() {
            @Override
            public int compare(AMZout o1, AMZout o2) {
                return o1.getSku().compareTo(o2.getSku());
            }
        });
        List<String> PjName = new ArrayList<>();


        List<AMZout> amZouts = allRes.stream().filter(new Predicate<AMZout>() {
            @Override
            public boolean test(AMZout amZout) {
                return amZout.getIsPJ() != null && amZout.getIsPJ().contains("true");
            }
        }).collect(Collectors.toList());


        Map mapBoxNums = allRes.stream().collect(Collectors.groupingBy(AMZout::getPjNo));


        boolean tt = true;


//        allRes.removeIf(new Predicate<AMZout>() {
//            @Override
//            public boolean test(AMZout amZout) {
//                boolean isPj = amZout.getIsPJ() != null && amZout.getIsPJ().contains("true");
//                if (isPj && !StringUtils.isNullOrEmpty(amZout.getObName())) {
//                    if (!PjName.contains(amZout.getObName())) {
//                        PjName.add(amZout.getObName());
//                    }
//
//                }
//                return isPj;
//            }
//        });


        if (PjName.size() > 0) {
//            PjName.add(0, "略过拼接箱：");

//            String[] first = new String[]{"略过拼接箱:"};
//            warning = PjName.toArray(new String[0]);
            warning[0] = "【1】" + "略过拼接箱:" + String.join(",", PjName);

        }
//        allRes.forEach(new Consumer<AMZout>() {
//            @Override
//            public void accept(AMZout amZout) {
//                //取出拼接的
//
//                amZout.getIsPJ().contains("true");
//            }
//        });

        Map sortedResMap = allRes.stream().collect(Collectors.groupingBy(AMZout::getStock));

//        使用TreeMap对resDepotMap按key值升序排序
//        TreeMap<String, List<AMZout>> sortedResMap = new TreeMap<>(resDepotMap);//这儿的key是sheet名了


        //US-HMT-004-White-10_5-1
        //US-HMT-001-White05_5
//        Set<String> strings = sortedResDepotMap.keySet();
        String path = null;
        String warnings = null;
        try {
            path = generateExcelAll(sortedResMap, outFolder, boxRulesGroup, shenBaoGroup, isUS);//仓库数据汇总
            List<String> path_PackingList = writePACKING_LIST_template(sortedResMap, "PACKING_LIST", outFolder, boxRulesGroup, isFurui);//PACKING_LIST
            List<String> path_INVOICE = writeINVOICE_Ttemplate(sortedResMap, "INVOICE", outFolder, boxRulesGroup, shenBaoGroup, isFurui, isUS);//PACKING_LIST
            List<String> baoGuan = writeBaoguandan_Ttemplate(sortedResMap, "报关单", outFolder, boxRulesGroup, shenBaoGroup, isFurui, isUS);
            List<String> piErrs = writePI_Ttemplate(sortedResMap, "PI", outFolder, boxRulesGroup, shenBaoGroup, isFurui, isUS);

            //错误/警示集合
            List<String> mergedList1 = Stream.concat(path_PackingList.stream(), path_INVOICE.stream())
                    .distinct()
                    .collect(Collectors.toList());

            List<String> mergedList2 = Stream.concat(baoGuan.stream(), piErrs.stream())
                    .distinct()
                    .collect(Collectors.toList());


            List<String> mergedList3 = Stream.concat(mergedList1.stream(), mergedList2.stream())
                    .distinct()
                    .collect(Collectors.toList());

            if (mergedList3.size() > 0) {
                warnings = String.join(",", mergedList3);
                warning[0] = warning[0] + "【2】" + warnings;
            }
        } catch (IOException | InvalidFormatException e) {
            e.printStackTrace();
            return ResultGenerator.genFailResult("结果生成失败", e.getMessage());
        }


        String outName = CommonUtils.getStringDate(true) + "-仓库数据+报关单";

        String pSrc = realPathExcel + C_ResultExcelFloder + "/" + outFolder;

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


        System.out.println(warnings);


        String backPath = C_ResultExcelFloder + "/" + outName + ".zip";


        long t2 = System.currentTimeMillis() - t1;
        JSONObject jsonObject = new JSONObject();
        if (path != null) {
            if (path.contains("#")) {//有警示信息
                String[] res = path.split("#");
                jsonObject.put("path", backPath);
                jsonObject.put("msg", res[1]);
            } else {
                jsonObject.put("path", backPath);
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

    public Result inputConvertAllList(File inputFile) {

        List<String> resMsg = new ArrayList<>();//异常信息

        WrapListWithMsg<AMZout> treeParser = new WrapListWithMsg<>();
//        Path filePath = Paths.get(path);
        String fileName = inputFile.getName();
        String stock = fileName.split("-")[0];
        stock = stock.substring(stock.indexOf("～") + 1);//保存服务端后有拼接!!是中文的～！！！！

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

        String fileCharSet = "UTF-8";//默认
        try {
            fileCharSet = CommonUtils.getCharsetName(inputFile);
            Lg.i("文件编码-fff》》》", fileCharSet);
        } catch (IOException e) {
            e.printStackTrace();
        }


        try (FileReader fr = new FileReader(inputFile, Charset.forName(fileCharSet))) {
            // 使用CsvToBeanBuilder解析csv文件

//            CSVReader reader1 = new CSVReader(fr);
//
////            reader1.skip(9);
//            // 创建两个PoundToKilogramFilter对象，分别传入两个CSVReader对象
//            PoundToKilogramFilter filter1 = new PoundToKilogramFilter(reader1);
//            treeParser = new CsvToBeanBuilder(reader1)
//                    .withType(AMZout.class)
////                    .withSkipLines(9)
//                    .withFilter(filter1)
//                    .withIgnoreLeadingWhiteSpace(true)
//                    .build().parse();

            treeParser = readCsvByLines(fr, stock);

            String finalStock = stock;
            treeParser.getListData().forEach(new Consumer<AMZout>() {
                @Override
                public void accept(AMZout amZout) {
                    amZout.setStock(finalStock);
                }
            });

            // 不需要调用close()方法，Java会自动关闭FileReader对象
        } catch (IOException e) {
            // 处理异常
        }
        //拆分假拼箱，不同双数的箱子，标出装箱率
        List<AMZout> tmpDivides = new ArrayList<>();


//        List<AMZout> judges = treeParser.stream().filter(new Predicate<AMZout>() {
//            @Override
//            public boolean test(AMZout amZout) {
//                return amZout.getIsPJ() != null && amZout.getIsPJ().contains("true");
//            }
//        }).collect(Collectors.toList());

        //是否真拼箱，解析时直接判断
        boolean isTPJ = treeParser.getErrMsg() != null;

        Map<String, AMZout> divMapPJ = new HashMap<>();
        treeParser.getListData().forEach(new Consumer<AMZout>() {
            @Override
            public void accept(AMZout amZout) {
                List<AMZoutBox> amZouts = amZout.getAmZoutBoxes();
//                Map<Integer, AMZout> divMap = new HashMap<>();


                List<AMZout> amZoutsDiv = new ArrayList<>();

                if (amZouts != null && amZouts.size() == 1 && !isTPJ) {//等于1是假拼箱
//                    amZout.getAmZoutBoxes().
                    int per = amZouts.get(0).getBoxShoes();
                    int num = amZouts.size();
                    if (per * num != amZout.getTotal()) {//相等就默认装箱率/规格等一致；不相等，则进行拆分

                        amZouts.forEach(new Consumer<AMZoutBox>() {
                            @Override
                            public void accept(AMZoutBox amZoutBox) {

                                AMZout divZoutIn = divMapPJ.get(amZoutBox.getBoxNum());
                                if (divZoutIn == null) {
                                    AMZout divZout = new AMZout();
                                    divZout.setSku(amZout.getSku());
                                    divZout.setASIN(amZout.getASIN());
                                    divZout.setFNSKU(amZout.getFNSKU());
                                    divZout.setObName(amZout.getObName());
                                    divZout.setObNumber(amZout.getObNumber());
                                    divZout.setAddress(amZout.getAddress());
                                    divZout.setStock(amZout.getStock());
                                    divZout.setIsPJ(amZout.getIsPJ());
                                    divZout.setWeight(amZout.getWeight());
                                    divZout.setWidth(amZout.getWidth());
                                    divZout.setLength(amZout.getLength());
                                    divZout.setHeight(amZout.getHeight());
                                    divZout.setBoxes(1);
                                    divZout.setPjNo(amZoutBox.getBoxNum());


                                    divZout.setPer(amZoutBox.getBoxShoes());
                                    divZout.setShoes(amZoutBox.getBoxShoes());
                                    divZout.setTotal(amZoutBox.getBoxShoes());
                                    divMapPJ.put(amZoutBox.getBoxNum(), divZout);
                                } else {
                                    divZoutIn.setBoxes(divZoutIn.getBoxes() + 1);

                                    divZoutIn.setShoes(divZoutIn.getShoes() + amZoutBox.getBoxShoes());
                                    divZoutIn.setTotal(divZoutIn.getTotal() + amZoutBox.getBoxShoes());

                                    divZoutIn.setPjNo(amZoutBox.getBoxNum());
                                }


//                                tmpDivides.add(divZout);
                            }
                        });


                    } else {//相等
                        amZout.setPer(per);
                        amZout.setPjNo(amZouts.get(0).getBoxNum());
                    }

                    tmpDivides.addAll(divMapPJ.values());

                } else if (amZouts != null && amZouts.size() > 0 && isTPJ) {//拼箱数据,不考虑混款，总计时统计款号能出来整箱

                    amZouts.forEach(new Consumer<AMZoutBox>() {
                        @Override
                        public void accept(AMZoutBox amZoutBox) {

                            AMZout divZoutIn = divMapPJ.get(amZoutBox.getBoxNum());//箱号具有唯一性？
                            if (divZoutIn == null) {
                                AMZout divZout = new AMZout();
                                divZout.setSku(amZout.getSku());
                                divZout.setASIN(amZout.getASIN());
                                divZout.setFNSKU(amZout.getFNSKU());
                                divZout.setObName(amZout.getObName());
                                divZout.setObNumber(amZout.getObNumber());
                                divZout.setAddress(amZout.getAddress());
                                divZout.setStock(amZout.getStock());
                                divZout.setWeight(amZout.getWeight());
                                divZout.setWidth(amZout.getWidth());
                                divZout.setLength(amZout.getLength());
                                divZout.setHeight(amZout.getHeight());

                                divZout.setBoxes(1);

                                divZout.setPjNo(amZoutBox.getBoxNum());//拼箱合并单元格

                                divZout.setPer(amZoutBox.getBoxShoes());
                                divZout.setShoes(amZoutBox.getBoxShoes());
                                divZout.setTotal(amZoutBox.getBoxShoes());

                                divMapPJ.put(amZoutBox.getBoxNum(), divZout);
                                amZoutsDiv.add(divZout);
                            } else {
                                AMZout copyAz = SerializationUtils.clone(amZout);
                                copyAz.setBoxes(0);
                                copyAz.setPjNo(amZoutBox.getBoxNum());
                                copyAz.setShoes(amZoutBox.getBoxShoes());
                                amZoutsDiv.add(copyAz);
                            }

//                            divMapPJ.put(amZoutBox.getBoxNum(), divZout);

                        }
                    });
                    tmpDivides.addAll(amZoutsDiv);

                }

            }
        });


        Map<String, List<AMZout>> divSku = tmpDivides.stream().collect(Collectors.groupingBy(AMZout::getSku));
        List<AMZout> finalTreeParser = treeParser.getListData();
        divSku.keySet().forEach(new Consumer<String>() {
            @Override
            public void accept(String s) {

                finalTreeParser.removeIf(new Predicate<AMZout>() {
                    @Override
                    public boolean test(AMZout amZout) {
                        return amZout.getSku().equals(s);
                    }
                });

            }
        });
        finalTreeParser.addAll(tmpDivides);

        // 使用TreeMap对resDepotMap按key值升序排序
//        TreeMap<String, List<AMZout>> sortedResMap = new TreeMap<>(resDepotMap);
        if (resMsg.size() > 0) {
            return ResultGenerator.genSuccessResult(treeParser, resMsg.toString());
        } else {
            return ResultGenerator.genSuccessResult(treeParser);
        }

    }

    private WrapListWithMsg<AMZout> readCsvByLines(FileReader fr, String stock) {
        //RFC4180Parser 严格
        CSVReader reader = new CSVReaderBuilder(fr).withCSVParser(new RFC4180Parser())
                .build();

        WrapListWithMsg<AMZout> resRead = new WrapListWithMsg<>();

        List<AMZout> treeParser = new ArrayList<>();

        resRead.setListData(treeParser);

        AMZout tmpTop = new AMZout();
        AMZout zoutFeeInfo = null;


        List<AMZoutBox> amZoutBoxesTmp = null; // 初始化一个容量为 10 的 List
        boolean isCm = false;
        int isPJ = -1;
        int isTTPJ_count = -1;//箱号/位置重复了，就认定为真拼箱
        for (Iterator<String[]> it = reader.iterator(); it.hasNext(); ) {
            String[] nextLine = it.next();

            String joined = String.join(",", nextLine); // 用逗号连接数组元素
            boolean isBlank = StringUtils.isBlank(joined);
            if (isBlank) {
                continue;
            }

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
//                    tmpTop.setSkuNum(Integer.parseInt(nextLine[1]));
                    continue;
                } else if (toStr.startsWith("商品数量")) {//拼箱后面有一行还有"商品数量"字样
//                    tmpTop.setTotalAll(Integer.parseInt(nextLine[1]));
                    continue;
                }

                //商品开始
                if (toStr.contains("FNSKU") && toStr.contains("商品名称")) {

                    if (!toStr.contains("箱号")) {
                        isPJ = 1;//拼箱

                    } else {
                        if (toStr.contains("厘米")) {
                            isCm = true;
                        }
                        isPJ = 0; //整箱
                    }
                    continue;
                }

                //拼箱
                if (isPJ == 1 && nextLine.length > 9) {//开始读取具体商品了

                    //  zoutFeeInfo.setIsPJ("true"); 真拼箱才打标志
                    if (!nextLine[0].isEmpty()) {


                        zoutFeeInfo = new AMZout();

                        zoutFeeInfo.setIsPJ("false"); //真拼箱才打标志
                        zoutFeeInfo.setPjNo("Def");

                        zoutFeeInfo.setSku(nextLine[0]);
//                        zoutFeeInfo.setName(nextLine[1]);
                        zoutFeeInfo.setASIN(nextLine[2]);
                        zoutFeeInfo.setFNSKU(nextLine[3]);
//                        zoutFeeInfo.setState(nextLine[4]);
                        zoutFeeInfo.setTotal(Integer.parseInt(nextLine[8]));
                        zoutFeeInfo.setShoes(Integer.parseInt(nextLine[8]));//直接总数

                        zoutFeeInfo.setObName(tmpTop.getObName());
                        zoutFeeInfo.setObNumber(tmpTop.getObNumber());
                        zoutFeeInfo.setAddress(tmpTop.getAddress());
                        zoutFeeInfo.setBoxes(tmpTop.getBoxes());
                        zoutFeeInfo.setStock(stock);
//                        zoutFeeInfo.setObWays(obWay);
//                        zoutFeeInfo.setObDate(outDate);

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
                                if (isTTPJ_count == i) {
                                    if (resRead.getErrMsg() == null) {
                                        resRead.setErrMsg("TPJ");
                                    }
                                }
                                isTTPJ_count = i;
                            }
                        }
                        //不全面
//                        if (amZoutBoxes.size() > 1) {
//                            if (resRead.getErrMsg() == null) {
//                                resRead.setErrMsg("TPJ");
//                            }
//                        }
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
                    zoutFeeInfo = new AMZout();
                    zoutFeeInfo.setSku(nextLine[0]);
//                    zoutFeeInfo.setName(nextLine[1]);
                    zoutFeeInfo.setASIN(nextLine[2]);
                    zoutFeeInfo.setFNSKU(nextLine[3]);
//                    zoutFeeInfo.setState(nextLine[4]);


                    zoutFeeInfo.setObName(tmpTop.getObName());
                    zoutFeeInfo.setObNumber(tmpTop.getObNumber());
                    zoutFeeInfo.setAddress(tmpTop.getAddress());
                    zoutFeeInfo.setBoxes(tmpTop.getBoxes());
                    zoutFeeInfo.setStock(stock);
                    zoutFeeInfo.setIsPJ("false");
                    zoutFeeInfo.setPjNo("Def");

                    // 9(重量) 10 L 11 W 12 H


                    zoutFeeInfo.setPer(Integer.parseInt(nextLine[13]));
                    zoutFeeInfo.setShoes(Integer.parseInt(nextLine[15]));
                    zoutFeeInfo.setBoxes(Integer.parseInt(nextLine[14]));

                    if (!isCm) {
                        zoutFeeInfo.setWeight(StringUtils.get2Numbers(String.valueOf(Double.parseDouble(nextLine[9]) * 0.454)));
                        zoutFeeInfo.setLength(StringUtils.get2Numbers(String.valueOf(Double.parseDouble(nextLine[10]) * 2.54)));
                        zoutFeeInfo.setWidth(StringUtils.get2Numbers(String.valueOf(Double.parseDouble(nextLine[11]) * 2.54)));
                        zoutFeeInfo.setHeight(StringUtils.get2Numbers(String.valueOf(Double.parseDouble(nextLine[12]) * 2.54)));
                    } else {
                        zoutFeeInfo.setWeight(Double.parseDouble(nextLine[9]));
                        zoutFeeInfo.setLength(Double.parseDouble(nextLine[10]));
                        zoutFeeInfo.setWidth(Double.parseDouble(nextLine[11]));
                        zoutFeeInfo.setHeight(Double.parseDouble(nextLine[12]));
                    }
                    treeParser.add(zoutFeeInfo);
                }


            }


        }

        List<AMZoutBox> finalAmZoutBoxesTmp = amZoutBoxesTmp;
        if (isPJ == 1) {//拼箱

            final boolean[] isTruePJ = {false};
            boolean finalIsCm = isCm;
            treeParser.forEach(new Consumer<AMZout>() {
                @Override
                public void accept(AMZout amZoutFeeInfo) {


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


                                        if (!finalIsCm) {
                                            amZoutFeeInfo.setWeight(StringUtils.get2Numbers(String.valueOf(amZoutBoxIn.getBoxWeight() * 0.454)));
                                            amZoutFeeInfo.setLength(StringUtils.get2Numbers(String.valueOf(amZoutBoxIn.getBoxLength() * 2.54)));
                                            amZoutFeeInfo.setWidth(StringUtils.get2Numbers(String.valueOf(amZoutBoxIn.getBoxWidth() * 2.54)));
                                            amZoutFeeInfo.setHeight(StringUtils.get2Numbers(String.valueOf(amZoutBoxIn.getBoxLength() * 2.54)));
                                        } else {
                                            amZoutFeeInfo.setWeight(amZoutBoxIn.getBoxWeight());
                                            amZoutFeeInfo.setLength(amZoutBoxIn.getBoxLength());
                                            amZoutFeeInfo.setWidth(amZoutBoxIn.getBoxWidth());
                                            amZoutFeeInfo.setHeight(amZoutBoxIn.getBoxLength());
                                        }


                                        amZoutBox.setBoxNum(amZoutBoxIn.getBoxNum());
                                        amZoutBox.setBoxName(amZoutBoxIn.getBoxName());
                                        Lg.i("重复A>>", amZoutBoxIn.getBoxPos() + ">>" + amZoutBoxIn.getBoxName());
                                        Lg.i("重复B>>", amZoutBox.getBoxPos() + ">>" + amZoutBox.getBoxName());
                                        //箱内总鞋数

                                        amZoutBoxIn.setRepeats(amZoutBoxIn.getRepeats() + 1);
                                        if (amZoutBoxIn.getRepeats() > 1) {
                                            isTruePJ[0] = true;
                                        }

                                        if (resRead.getErrMsg() != null) {//真拼接,得出装箱率
//                                            amZoutBoxIn.setBoxShoes(amZoutBoxIn.getBoxShoes() + amZoutBox.getBoxShoes());
//                                            amZoutBox.setBoxShoes(amZoutBoxIn.getBoxShoes());
                                        } else {
                                            amZoutBoxIn.setBoxShoes(amZoutBoxIn.getBoxShoes() + amZoutBox.getBoxShoes());

                                        }


//                                                double rate = finalIsCm ? 6000 : 167;
                                        //体积重
//                                                amZoutBoxIn.setT_weight(amZoutBoxIn.getBoxHeight() * amZoutBoxIn.getBoxWidth() * amZoutBoxIn.getBoxLength() / rate);
//                                                amZoutBoxIn.setF_weight(Math.max(amZoutBoxIn.getBoxWeight(), amZoutBoxIn.getT_weight()));
                                        if (amZoutBox.getBoxShoesTmp() == null) {
                                            List<Integer> boxShoesTmp = new ArrayList<>();
                                            boxShoesTmp.add(amZoutBox.getBoxShoes());
                                            amZoutBox.setBoxShoesTmp(boxShoesTmp);
                                        } else {
                                            amZoutBox.getBoxShoesTmp().add(amZoutBox.getBoxShoes());
                                        }

                                        //覆盖到最后都是最大的
//                                        amZoutBox.setBoxShoesTmp(amZoutBoxIn.getBoxShoes());
//                                                amZoutBox.setT_weight(amZoutBoxIn.getT_weight());
//                                                amZoutBox.setF_weight(amZoutBoxIn.getF_weight());
                                    }

                                }
                            });

                        }
                    });

                    if (amZoutFeeInfo.getAmZoutBoxes() != null) {
                        amZoutFeeInfo.setBoxes(amZoutFeeInfo.getAmZoutBoxes().size());
                    } else {
                        amZoutFeeInfo.setBoxes(0);
                    }

//                    amZoutFeeInfo.setShoes();

                }
            });

            if (isTruePJ[0]) {
                treeParser.forEach(new Consumer<AMZout>() {
                    @Override
                    public void accept(AMZout amZout) {
                        amZout.setIsPJ("true");
                    }
                });

            }
        }
        return resRead;
    }


    private static int NULL_Name = 0;

    //M-根据解析数据生成最终Excel
    public String generateExcelAll(Map dataMaps, String outFolder, Map<String, List<BoxRules>> boxRulesGroup, Map<String, List<ShenBaoInfo>> shenBaoInfoGroup, boolean isUS) throws IOException {

        NULL_Name = 0;
        System.out.println("开始合成");

        List<String> nameRepeats = new ArrayList<>();
        Set<String> tabStrs = dataMaps.keySet();
        XSSFWorkbook workbook = new XSSFWorkbook();

        tabStrs.forEach(new Consumer<String>() {
            @Override
            public void accept(String s) {

//                generateSheetWays(workbook, dataMap, s);
                Object ob = generateSheetWays(workbook, dataMaps, s);
                if (ob instanceof String) {//有重复表名出错了
                    nameRepeats.add((String) ob);
                }
                NULL_Name = NULL_Name + 1;

            }
        });
//        File fileDir = new File((realPathExcel + C_ResultExcelFloder));
//        if (!fileDir.exists()) {
//            fileDir.mkdirs();
//            Lg.i("saveToAssets", "文件的路径不存在，创建:", fileDir.getPath());
//
//        }
        File fileDirZip = new File((realPathExcel + C_ResultExcelFloder + "/" + outFolder));
        if (!fileDirZip.exists()) {
            fileDirZip.mkdirs();
            Lg.i("saveToAssets", "文件的路径不存在，创建:", fileDirZip.getPath());

        }

        String outFileName = "仓库数据";

//        String trueFileNameStep1 = outDate + "-" + outFileName + "step1" + ".xlsx";
        String trueFileName = outDate + "-" + outFileName + ".xlsx";


//        String pathOutStep1 = fileDir.getPath() + '/' + trueFileNameStep1;

        String pathOut = fileDirZip.getPath() + '/' + trueFileName;

        String pathBack = C_ResultExcelFloder + "/" + outFolder + "/" + trueFileName;
        // 将工作簿对象写入到文件中
        FileOutputStream out = new FileOutputStream(pathOut);
//        FileOutputStream out = new FileOutputStream("example仓库数据.xlsx");

//        workbook.setForceFormulaRecalculation(true);
        WrapListWithMsg<AMZoutAll> resAmZoutAllWrapListWithMsg = getSummarize(workbook);
        creatSummarize(workbook, resAmZoutAllWrapListWithMsg);

        generateAllinOne(workbook, dataMaps, boxRulesGroup, shenBaoInfoGroup, isUS);

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

    //创建汇总
    public void creatSummarize(XSSFWorkbook workbook, WrapListWithMsg<AMZoutAll> resAmZoutAllWrapListWithMsg) {
        XSSFSheet sheet = workbook.createSheet("计费重汇总");
        int len = resAmZoutAllWrapListWithMsg.getListData().size();

        XSSFCellStyle style = workbook.createCellStyle();
        // 设置水平居中
//        style.setAlignment(HorizontalAlignment.CENTER);//这儿设置了会覆盖后面的
        // 设置垂直居中
        style.setVerticalAlignment(VerticalAlignment.CENTER);

        for (int i = 0; i <= len; i++) {

            if (i == 0) {//title

                XSSFCellStyle styleIn = workbook.createCellStyle();//XSSFCellStyle才可自由设置颜色
                styleIn.cloneStyleFrom(style);
                XSSFColor myColor = new XSSFColor();
                byte[] byteColor = new byte[]{(byte) 252, (byte) 197, (byte) 201};
                myColor.setRGB(byteColor);
                styleIn.setFillForegroundColor(myColor);
                styleIn.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                // 创建一个行对象，表示第一行
                XSSFRow row = sheet.createRow(i);
                row.setHeight((short) (25 * 20)); // 25 像素 = 25 * 20 缇
                AMZ_titleMSL.forEach(new Consumer<String>() {
                    @Override
                    public void accept(String s) {
                        XSSFCell cell0 = row.createCell(AMZ_titleMSL.indexOf(s));
                        cell0.setCellValue(s);
                        cell0.setCellStyle(styleIn);
                    }
                });
            } else {

                XSSFRow row = sheet.createRow(i);
                AMZoutAll amZoutAll = resAmZoutAllWrapListWithMsg.getListData().get(i - 1);

                int finalI = i;
                XSSFSheet finalSheet = sheet;
                AMZ_titleMSL.forEach(new Consumer<String>() {
                    @Override
                    public void accept(String s) {
                        int index = AMZ_titleMSL.indexOf(s);
                        XSSFCell cell0 = row.createCell(index);
                        switch (index) {
                            case 0://仓库名
                                cell0.setCellValue(amZoutAll.getStock());
                                cell0.setCellStyle(style);
                                break;
                            case 1://总箱数

                                cell0.setCellValue(amZoutAll.getBoxes());
                                cell0.setCellStyle(style);
                                break;
                            case 2://数量
                                cell0.setCellValue(amZoutAll.getShoes());
                                cell0.setCellStyle(style);
                                break;
                            case 3://总重量
                                cell0.setCellValue(amZoutAll.getWeightAll());
                                cell0.setCellStyle(style);
                                break;
                            case 4://体积重
                                cell0.setCellValue(amZoutAll.getVolume());
                                cell0.setCellStyle(style);
                                break;
                            case 5://增加1CM体积重
                                cell0.setCellValue(amZoutAll.getVolume1());
                                cell0.setCellStyle(style);
                                break;
                            case 6://增加1.5cm体积重
                                cell0.setCellValue(amZoutAll.getVolume15());
                                cell0.setCellStyle(style);
                                break;
                            case 7://增加2CM体积重
                                cell0.setCellValue(amZoutAll.getVolume2());
                                cell0.setCellStyle(style);
                                break;
                        }


                    }
                });


            }


        }
        PoiUtiles.adjustAutoWidth(sheet, AMZ_titleMSL.size());

    }


    public WrapListWithMsg<AMZoutAll> getSummarize(Workbook workbook) throws IOException {

        WrapListWithMsg<AMZoutAll> msgSummarize = new WrapListWithMsg<>();
        List<AMZoutAll> stockDetails = new ArrayList<>();
        List<String> errStrs = new ArrayList<>();
        msgSummarize.setListData(stockDetails);//放前面放后面一样？地址指向？ List<String> 不行？


        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        workbook.forEach(new Consumer<org.apache.poi.ss.usermodel.Sheet>() {
            @Override
            public void accept(org.apache.poi.ss.usermodel.Sheet sheet) {
                String name = sheet.getSheetName();
                int lastRow = sheet.getLastRowNum();
//                sheet.setForceFormulaRecalculation(true);
                String[] sumKeys = new String[]{"C", "D", "I", "J", "N", "R", "V"};
                AMZoutAll amZoutAll = new AMZoutAll();
                Row currentRow1 = sheet.getRow(lastRow - 1);
                Cell boxes = currentRow1.getCell(sumKeys[0].charAt(0) - 'A');
                CellValue cellValueBoxes = evaluator.evaluate(boxes);
                Cell shoes = currentRow1.getCell(sumKeys[1].charAt(0) - 'A');
                CellValue cellValueShoes = evaluator.evaluate(shoes);
                Cell weight = currentRow1.getCell(sumKeys[2].charAt(0) - 'A');
                CellValue cellValueWeight = evaluator.evaluate(weight);


                Row currentRow2 = sheet.getRow(lastRow);

                Cell volum = currentRow2.getCell(sumKeys[3].charAt(0) - 'A');
                CellValue cellValueVolum = evaluator.evaluate(volum);
                Cell volum1 = currentRow2.getCell(sumKeys[4].charAt(0) - 'A');
                CellValue cellValueVolum1 = evaluator.evaluate(volum1);
                Cell volum15 = currentRow2.getCell(sumKeys[5].charAt(0) - 'A');
                CellValue cellValueVolum15 = evaluator.evaluate(volum15);
                Cell volum2 = currentRow2.getCell(sumKeys[6].charAt(0) - 'A');
                CellValue cellValueVolum2 = evaluator.evaluate(volum2);


                amZoutAll.setStock(name);
                amZoutAll.setBoxes((int) cellValueBoxes.getNumberValue());
                amZoutAll.setShoes((int) cellValueShoes.getNumberValue());
//                double result = new BigDecimal(String.valueOf(weight)).setScale(0, BigDecimal.ROUND_HALF_UP).doubleValue();
                //向上取整
                amZoutAll.setWeightAll((int) Math.ceil(cellValueWeight.getNumberValue()));

                amZoutAll.setVolume((int) Math.ceil(cellValueVolum.getNumberValue()));
                amZoutAll.setVolume1((int) Math.ceil(cellValueVolum1.getNumberValue()));
                amZoutAll.setVolume15((int) Math.ceil(cellValueVolum15.getNumberValue()));
                amZoutAll.setVolume2((int) Math.ceil(cellValueVolum2.getNumberValue()));
                stockDetails.add(amZoutAll);


                boolean t = true;
            }
        });


        msgSummarize.setErrMsg(errStrs.toString());//放前面放后面一样？地址指向？
        return msgSummarize;
    }


    //M-单个
    public Object generateSheetWays(XSSFWorkbook workbook, Map<String, List<AMZout>> dataMap, String name) {
//        Set<String> tabStrs = dataMap.keySet();

        //The workbook already contains a sheet named '表名构造失败'>>不能重名
        String sheetName = "表名构造失败" + NULL_Name;

        String repeatName = null;

        if (name != null) {
            sheetName = name;
        }
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


        XSSFCellStyle style = workbook.createCellStyle();
        // 设置水平居中
//        style.setAlignment(HorizontalAlignment.CENTER);//这儿设置了会覆盖后面的
        // 设置垂直居中
        style.setVerticalAlignment(VerticalAlignment.CENTER);


//        final int[] lastMax = {0};
        final int[] lastLen = {0};//缓存一组列表长度，便于计算总计

//        List<String> mTitles = sheetName.contains("US") ? C_titleM : C_titleKh;
        final List<String> mTitles = AMZ_titleMS;

        List<AMZout> depotRes = dataMap.get(name);
        depotRes.sort(new Comparator<AMZout>() {
            @Override
            public int compare(AMZout o1, AMZout o2) {
                //默认同箱的有值1的在前？因为添加的时候是这么添加的
                return o1.getPjNo().compareTo(o2.getPjNo());
            }
        });

        //记录合并的首位位置,起始 + 结束（同一key的values长度）》》》箱号+箱数（0/1）确定位置！！
        Map<String, List<Integer>> mapMerge = new HashMap<>();

//        Map groupBox = depotRes.stream().collect(Collectors.groupingBy(AMZout::getPjNo));

        Map<String, List<AMZout>> groupBox = depotRes.stream()
                .sorted(Comparator.comparing(AMZout::getPjNo).thenComparing(AMZout::getBoxes).reversed())
                .collect(Collectors.groupingBy(AMZout::getPjNo));

        depotRes = groupBox.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());


        int len = depotRes.size();

        XSSFFormulaEvaluator formulaEvaluator =
                workbook.getCreationHelper().createFormulaEvaluator();
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
                        XSSFCell cell0 = row.createCell(mTitles.indexOf(s));
                        cell0.setCellValue(s);
                        cell0.setCellStyle(styleIn);

                    }
                });


            } else {
                XSSFRow row = sheet.createRow(i);
                AMZout depotRes1 = depotRes.get(i - 1);

                int finalI = i;
                XSSFSheet finalSheet = sheet;
                mTitles.forEach(new Consumer<String>() {
                    @Override
                    public void accept(String s) {
                        int index = mTitles.indexOf(s);
                        XSSFCell cell0 = row.createCell(index);
                        String r = String.valueOf((finalI + 1));

                        switch (index) {
                            case 0:
                                cell0.setCellValue(depotRes1.getSku());
                                cell0.setCellStyle(style);
                                org.apache.poi.ss.util.CellAddress ad0 = cell0.getAddress();
                                break;
                            case 1://装箱率
                                cell0.setCellValue(depotRes1.getPer());
                                cell0.setCellStyle(style);
                                if (depotRes1.getBoxes() != 0) {//合并起始位置
                                    if (depotRes1.getPjNo().equals("Def")) {

                                        return;
                                    }
                                    List<AMZout> in1box = ((List<AMZout>) groupBox.get(depotRes1.getPjNo()));
                                    if (in1box.size() > 1) {
                                        final int[] all = {0};
                                        in1box.forEach(new Consumer<AMZout>() {
                                            @Override
                                            public void accept(AMZout amZout) {
                                                all[0] = all[0] + amZout.getShoes();
                                            }
                                        });

                                        //拼箱装箱率
                                        cell0.setCellValue(all[0]);
                                    }
                                }
                                break;
                            case 2://箱数
                                cell0.setCellValue(depotRes1.getBoxes());
                                cell0.setCellStyle(style);
                                if (depotRes1.getBoxes() != 0) {//合并起始位置
                                    if (depotRes1.getPjNo().equals("Def")) {

                                        return;
                                    }
                                    List<Integer> pos = new ArrayList<>();
                                    pos.add(finalI);
                                    int end = ((List<AMZout>) groupBox.get(depotRes1.getPjNo())).size();
                                    if (end > 1) {
                                        pos.add(finalI + end - 1);
                                        mapMerge.put(depotRes1.getPjNo(), pos);
                                    }
                                }
                                break;
                            case 3://总数量（鞋子）
                                cell0.setCellValue(depotRes1.getShoes());
                                cell0.setCellStyle(style);
                                break;
                            case 4://重量,单个？ E
                                if (depotRes1.getBoxes() == 0) {//合并置为空
//                                    cell0.setCellValue(null);
//                                    cell0.setCellStyle(style);
                                } else {
                                    cell0.setCellValue(depotRes1.getWeight());
                                    cell0.setCellStyle(style);
                                }

                                break;
                            case 5://宽 F

                                if (depotRes1.getBoxes() == 0) {//合并置为空
//                                    cell0.setCellValue(null);
//                                    cell0.setCellStyle(style);
                                } else {
                                    cell0.setCellValue(depotRes1.getWidth());
                                    cell0.setCellStyle(style);
                                }


                                break;
                            case 6://长 G


                                if (depotRes1.getBoxes() == 0) {//合并置为空
//                                    cell0.setCellValue(null);
//                                    cell0.setCellStyle(style);
                                } else {
                                    cell0.setCellValue(depotRes1.getLength());
                                    cell0.setCellStyle(style);
                                }

                                break;
                            case 7://高 H


                                if (depotRes1.getBoxes() == 0) {//合并置为空
//                                    cell0.setCellValue(null);
//                                    cell0.setCellStyle(style);
                                } else {
                                    cell0.setCellValue(depotRes1.getHeight());
                                    cell0.setCellStyle(style);
                                }

                                break;
                            case 8://总重量 W * boxes
//                                cell0.setCellValue(depotRes1.getWeight() * depotRes1.getBoxes());
                                try {
                                    String formula = ("E" + r) + "*" + depotRes1.getBoxes();
                                    cell0.setCellFormula(formula);
                                } catch (FormulaParseException | IllegalStateException e) {
                                    e.printStackTrace();
                                    Lg.e("总重量构建失败>>>>", finalSheet.getSheetName());
                                }
                                formulaEvaluator.evaluate(cell0);
//                                cell0.setCellStyle(style);
                                break;
                            case 9://总体积
//                                cell0.setCellValue(depotRes1.getLength()*depotRes1.getWidth()*depotRes1.getHeight()/1000000*depotRes1.getBoxes());
                                //再调用get会重复计算

                                try {
                                    String formula = ("F" + r + "*" + "G" + r + "*" + "H" + r) + "/1000000*" + depotRes1.getBoxes();
                                    cell0.setCellFormula(formula);
                                } catch (FormulaParseException | IllegalStateException e) {
                                    e.printStackTrace();
                                    Lg.e("总体积构建失败>>>>", finalSheet.getSheetName());
                                }
                                formulaEvaluator.evaluate(cell0);

                                break;
                            case 10://K
                                try {
                                    String formula = ("F" + r) + "+1";
                                    cell0.setCellFormula(formula);
                                } catch (FormulaParseException | IllegalStateException e) {
                                    e.printStackTrace();
                                    Lg.e("总体积构建失败>>>>", finalSheet.getSheetName());
                                }
                                formulaEvaluator.evaluate(cell0);
                                break;
                            case 11://L
                                try {
                                    String formula = ("G" + r) + "+1";
                                    cell0.setCellFormula(formula);
                                } catch (FormulaParseException | IllegalStateException e) {
                                    e.printStackTrace();
                                    Lg.e("总体积构建失败>>>>", finalSheet.getSheetName());
                                }
                                formulaEvaluator.evaluate(cell0);
                                break;
                            case 12://M
                                try {
                                    String formula = ("H" + r) + "+1";
                                    cell0.setCellFormula(formula);
                                } catch (FormulaParseException | IllegalStateException e) {
                                    e.printStackTrace();
                                    Lg.e("总体积构建失败>>>>", finalSheet.getSheetName());
                                }
                                formulaEvaluator.evaluate(cell0);
                                break;
                            case 13://增加1CM的体积
                                try {
                                    String formula = ("K" + r + "*" + "L" + r + "*" + "M" + r) + "/1000000*" + depotRes1.getBoxes();
                                    cell0.setCellFormula(formula);
                                } catch (FormulaParseException | IllegalStateException e) {
                                    e.printStackTrace();
                                    Lg.e("总体积构建失败>>>>", finalSheet.getSheetName());
                                }
                                formulaEvaluator.evaluate(cell0);

                                break;
                            case 14://O 加1.5cm
                                try {
                                    String formula = ("F" + r) + "+1.5";
                                    cell0.setCellFormula(formula);
                                } catch (FormulaParseException | IllegalStateException e) {
                                    e.printStackTrace();
                                    Lg.e("总体积构建失败>>>>", finalSheet.getSheetName());
                                }
                                formulaEvaluator.evaluate(cell0);
                                break;
                            case 15://P
                                try {
                                    String formula = ("G" + r) + "+1.5";
                                    cell0.setCellFormula(formula);
                                } catch (FormulaParseException | IllegalStateException e) {
                                    e.printStackTrace();
                                    Lg.e("总体积构建失败>>>>", finalSheet.getSheetName());
                                }
                                formulaEvaluator.evaluate(cell0);
                                break;
                            case 16://Q
                                try {
                                    String formula = ("H" + r) + "+1.5";
                                    cell0.setCellFormula(formula);
                                } catch (FormulaParseException | IllegalStateException e) {
                                    e.printStackTrace();
                                    Lg.e("总体积构建失败>>>>", finalSheet.getSheetName());
                                }
                                formulaEvaluator.evaluate(cell0);
                                break;
                            case 17:
                                try {
                                    String formula = ("O" + r + "*" + "P" + r + "*" + "Q" + r) + "/1000000*" + depotRes1.getBoxes();
                                    cell0.setCellFormula(formula);
                                } catch (FormulaParseException | IllegalStateException e) {
                                    e.printStackTrace();
                                    Lg.e("总体积构建失败>>>>", finalSheet.getSheetName());
                                }
                                formulaEvaluator.evaluate(cell0);
                                break;
                            case 18://S
                                try {
                                    String formula = ("F" + r) + "+2";
                                    cell0.setCellFormula(formula);
                                } catch (FormulaParseException | IllegalStateException e) {
                                    e.printStackTrace();
                                    Lg.e("总体积构建失败>>>>", finalSheet.getSheetName());
                                }
                                formulaEvaluator.evaluate(cell0);
                                break;
                            case 19://T
                                try {
                                    String formula = ("G" + r) + "+2";
                                    cell0.setCellFormula(formula);
                                } catch (FormulaParseException | IllegalStateException e) {
                                    e.printStackTrace();
                                    Lg.e("总体积构建失败>>>>", finalSheet.getSheetName());
                                }
                                formulaEvaluator.evaluate(cell0);
                                break;
                            case 20://U
                                try {
                                    String formula = ("H" + r) + "+2";
                                    cell0.setCellFormula(formula);
                                } catch (FormulaParseException | IllegalStateException e) {
                                    e.printStackTrace();
                                    Lg.e("总体积构建失败>>>>", finalSheet.getSheetName());
                                }
                                formulaEvaluator.evaluate(cell0);
                                break;
                            case 21:
                                try {
                                    String formula = ("S" + r + "*" + "T" + r + "*" + "U" + r) + "/1000000*" + depotRes1.getBoxes();
                                    cell0.setCellFormula(formula);
                                } catch (FormulaParseException | IllegalStateException e) {
                                    e.printStackTrace();
                                    Lg.e("总体积构建失败>>>>", finalSheet.getSheetName());
                                }
                                formulaEvaluator.evaluate(cell0);
                                break;
                        }


                    }
                });

            }
        }

        //最后一组0
        int lastRow = sheet.getLastRowNum();//前面map大小为0，空表？？
        if (lastRow < 1) {
            return sheet;
        }
        XSSFRow rowSum = sheet.createRow(lastRow + 1);

//        mapMerge.forEach(new BiConsumer<String, List<Integer>>() {
//            @Override
//            public void accept(String s, List<Integer> integers) {
//                sheet.addMergedRegion(0,0,0,0);
//            }
//        });

        if (mapMerge.size() > 0) {
            List<List<Integer>> pos = new ArrayList<>(mapMerge.values());
            for (int i = 0; i < pos.size(); i++) {
                List<Integer> p = pos.get(i);
                //装箱率
                sheet.addMergedRegion(new CellRangeAddress(p.get(0), p.get(1), 1, 1));
                //箱子数
                sheet.addMergedRegion(new CellRangeAddress(p.get(0), p.get(1), 2, 2));

            }
        }


        XSSFCellStyle styleR = workbook.createCellStyle();
        // 设置水平居中
        styleR.setAlignment(HorizontalAlignment.RIGHT);
        // 设置垂直居中
        styleR.setVerticalAlignment(VerticalAlignment.CENTER);
        //J N R V 体积要再换算一次in
        String[] sumKeys = new String[]{"C", "D", "I", "J", "N", "R", "V"};
        for (int j = 0; j < sumKeys.length; j++) {

            char key = sumKeys[j].charAt(0);
            // E、F、G、H、I
            XSSFCell cell0 = rowSum.createCell((key - 'A'));
            //"SUM(F2:F4)"
            String start = sumKeys[j] + 2;
            String end = sumKeys[j] + (lastRow + 1);
//            String digiNums = key < 'E' ? "0" : "8";//这儿规定0位小数无效
            String digiNums = "8";//这儿规定0位小数无效
            try {
                String formula1 = "SUM(" + start + ":" + end + ")";
                String formula2 = "ROUND(" + formula1 + "," + digiNums + ")";
//                String formula3 = "INT(" + formula1 + ")";//int INT fastexcel 读不出来
                String formula3 = formula1;//int
                if (key < 'E') {
                    cell0.setCellFormula(formula3);
                } else {
                    cell0.setCellFormula(formula2);
                }

            } catch (FormulaParseException | IllegalStateException e) {
                e.printStackTrace();
                Lg.e("总计构建失败>>>>", sheet.getSheetName());
            }
            cell0.setCellStyle(styleR);
            formulaEvaluator.evaluate(cell0);

//            System.out.println("Excel->merge>SUM" + lastRow + "lastLen>" + lastLen[0] + "start>" + start + "end>" + end);

        }

        int lastRow1 = sheet.getLastRowNum();//前面map大小为0，空表？？
        if (lastRow1 < 1) {
            return sheet;
        }
        XSSFRow rowSum1 = sheet.createRow(lastRow1 + 1);

        String[] sumKeys1 = new String[]{"J", "N", "R", "V"};
        for (int j = 0; j < sumKeys1.length; j++) {

            char key = sumKeys1[j].charAt(0);
            // E、F、G、H、I
            XSSFCell cell0 = rowSum1.createCell((key - 'A'));
            //"SUM(F2:F4)"
//            String digiNums = key < 'E' ? "0" : "8";//这儿规定0位小数无效
            String digiNums = "8";//这儿规定0位小数无效
            try {
                String start = sumKeys1[j] + (lastRow1 + 1);
                String formula1 = start + "/0.006";
                String formula2 = "ROUND(" + formula1 + "," + digiNums + ")";
                cell0.setCellFormula(formula2);

            } catch (FormulaParseException | IllegalStateException e) {
                e.printStackTrace();
                Lg.e("总计构建失败>>>>", sheet.getSheetName());
            }
            formulaEvaluator.evaluate(cell0);
//            System.out.println("Excel->merge>SUM" + lastRow + "lastLen>" + lastLen[0] + "start>" + start + "end>" + end);

        }


        PoiUtiles.adjustAutoWidth(sheet, mTitles.size());
//        dataMap.clear();//清缓存
        return sheet;
    }

    //仓库数据汇总
    public Object generateAllinOne(XSSFWorkbook workbook, Map<String, List<AMZout>> dataMap, Map<String, List<BoxRules>> boxRulesGroup, Map<String, List<ShenBaoInfo>> shenBaoInfoGroup, boolean isUS) {

        String sheetName = "仓库数据汇总";

        XSSFSheet sheet = null;
        try {
            sheet = workbook.createSheet(sheetName);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();

            return sheetName;//表名重复
        }

        XSSFCellStyle style = workbook.createCellStyle();
        // 设置水平居中
//        style.setAlignment(HorizontalAlignment.CENTER);//这儿设置了会覆盖后面的
        // 设置垂直居中
        style.setVerticalAlignment(VerticalAlignment.CENTER);

        final List<String> mTitles = AMZ_titleMSAll;

        //合并一下重复sku项？>>>开始不用合并，有拼箱
        List<AMZout> rAmZoutList = dataMap.values().stream()
                .flatMap(List::stream) // 将每个List<AMZout>展开为一个流
                .sorted(Comparator.comparing(o -> o.getPjNo()))//按箱号排序后方便合并，箱数为0的像上一个不为0的合并
                .collect(Collectors.toList()); // 将流中的元素收集到一个新的List<AMZout>中
        int len = rAmZoutList.size();


        Map<String, List<Integer>> mapMerge = new HashMap<>();

//        Map groupBox = rAmZoutList.stream().collect(Collectors.groupingBy(AMZout::getPjNo));

        Map<String, List<AMZout>> groupBox = rAmZoutList.stream()
                .sorted(Comparator.comparing(AMZout::getPjNo).thenComparing(AMZout::getBoxes).reversed())
                .collect(Collectors.groupingBy(AMZout::getPjNo));
//顺序很重要，排序方便合并
        rAmZoutList = groupBox.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());


//-------------------所有列与总计 tab1-----------------------------------------------------------

        XSSFFormulaEvaluator formulaEvaluator =
                workbook.getCreationHelper().createFormulaEvaluator();
        for (int i = 0; i <= len; i++) {

            //US-HMT-004-White-10_5-1
            //US-HMT-001-White05_5
            if (i == 0) {//title

                XSSFCellStyle styleIn = workbook.createCellStyle();//XSSFCellStyle才可自由设置颜色
                byte[] byteColor = new byte[]{(byte) 252, (byte) 197, (byte) 201};
                PoiUtiles.cellColor(styleIn, style, byteColor);

                // 创建一个行对象，表示第一行
                XSSFRow row = sheet.createRow(i);
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
                XSSFRow row = sheet.createRow(i);
                AMZout depotRes1 = rAmZoutList.get(i - 1);

                int finalI = i;
                XSSFSheet finalSheet = sheet;
                mTitles.forEach(new Consumer<String>() {
                    @Override
                    public void accept(String s) {
                        int index = mTitles.indexOf(s);
                        XSSFCell cell0 = row.createCell(index);
                        String r = String.valueOf((finalI + 1));

                        switch (index) {
                            case 0:
                                cell0.setCellValue(depotRes1.getSku());
                                cell0.setCellStyle(style);
                                break;
                            case 1://装箱率
                                if (depotRes1.getPer() == 0 && depotRes1.getBoxes() > 0) {

                                    XSSFCellStyle styleR = workbook.createCellStyle();
                                    styleR.cloneStyleFrom(style);
                                    styleR.setFillForegroundColor(IndexedColors.RED.index);
                                    styleR.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                                    cell0.setCellValue((depotRes1.getShoes() / depotRes1.getBoxes()));
                                    cell0.setCellStyle(styleR);
                                } else {
                                    cell0.setCellValue(depotRes1.getPer());
                                    cell0.setCellStyle(style);
                                }

                                if (depotRes1.getBoxes() != 0) {//合并起始位置

                                    if (depotRes1.getPjNo().equals("Def")) {

                                        return;
                                    }

                                    List<AMZout> in1box = ((List<AMZout>) groupBox.get(depotRes1.getPjNo()));
                                    if (in1box.size() > 1) {
                                        final int[] all = {0};
                                        in1box.forEach(new Consumer<AMZout>() {
                                            @Override
                                            public void accept(AMZout amZout) {
                                                all[0] = all[0] + amZout.getShoes();
                                            }
                                        });

                                        //拼箱装箱率
                                        cell0.setCellValue(all[0]);
                                    }
                                }


                                break;
                            case 2://箱数
                                cell0.setCellValue(depotRes1.getBoxes());
                                cell0.setCellStyle(style);

                                if (depotRes1.getBoxes() != 0) {//合并起始位置
                                    if (depotRes1.getPjNo().equals("Def")) {

                                        return;
                                    }
                                    List<Integer> pos = new ArrayList<>();
                                    pos.add(finalI);
                                    int end = ((List<AMZout>) groupBox.get(depotRes1.getPjNo())).size();
                                    if (end > 1) {
                                        pos.add(finalI + end - 1);
                                        mapMerge.put(depotRes1.getPjNo(), pos);
                                    }
                                }

                                break;
                            case 3://总数量（鞋子）
                                cell0.setCellValue(depotRes1.getShoes());
                                cell0.setCellStyle(style);
                                break;
                            case 4://重量,单个？ E
                                if (depotRes1.getBoxes() != 0) {
                                    cell0.setCellValue(depotRes1.getWeight());
                                    cell0.setCellStyle(style);
                                }

                                break;
                            case 5://宽 F
                                if (depotRes1.getBoxes() != 0) {
                                    cell0.setCellValue(depotRes1.getWidth());
                                    cell0.setCellStyle(style);
                                }
                                break;
                            case 6://长 G
                                if (depotRes1.getBoxes() != 0) {
                                    cell0.setCellValue(depotRes1.getLength());
                                    cell0.setCellStyle(style);
                                }
                                break;
                            case 7://高 H
                                if (depotRes1.getBoxes() != 0) {
                                    cell0.setCellValue(depotRes1.getHeight());
                                    cell0.setCellStyle(style);
                                }
                                break;
                            case 8://总重量 W * boxes
//                                cell0.setCellValue(depotRes1.getWeight() * depotRes1.getBoxes());
                                try {
                                    String formula = ("E" + r) + "*" + depotRes1.getBoxes();
                                    cell0.setCellFormula(formula);
                                } catch (FormulaParseException | IllegalStateException e) {
                                    e.printStackTrace();
                                    Lg.e("总重量构建失败>>>>", finalSheet.getSheetName());
                                }
                                formulaEvaluator.evaluate(cell0);
//                                cell0.setCellStyle(style);
                                break;
                            case 9://总体积
//                                cell0.setCellValue(depotRes1.getLength()*depotRes1.getWidth()*depotRes1.getHeight()/1000000*depotRes1.getBoxes());
                                //再调用get会重复计算

                                try {
                                    String formula = ("F" + r + "*" + "G" + r + "*" + "H" + r) + "/1000000*" + depotRes1.getBoxes();
                                    cell0.setCellFormula(formula);
                                } catch (FormulaParseException | IllegalStateException e) {
                                    e.printStackTrace();
                                    Lg.e("总体积构建失败>>>>", finalSheet.getSheetName());
                                }
                                formulaEvaluator.evaluate(cell0);

                                break;
                        }


                    }
                });

            }
        }

        //最后一组0
        int lastRow = sheet.getLastRowNum();//前面map大小为0，空表？？
        if (lastRow < 1) {
            return sheet;
        }
        XSSFRow rowSum = sheet.createRow(lastRow + 1);

        if (mapMerge.size() > 0) {
            List<List<Integer>> pos = new ArrayList<>(mapMerge.values());
            for (int i = 0; i < pos.size(); i++) {
                List<Integer> p = pos.get(i);
                //装箱率
                sheet.addMergedRegion(new CellRangeAddress(p.get(0), p.get(1), 1, 1));
                //箱子数
                sheet.addMergedRegion(new CellRangeAddress(p.get(0), p.get(1), 2, 2));

            }
        }


        XSSFCellStyle styleR = workbook.createCellStyle();
        // 设置水平居中
        styleR.setAlignment(HorizontalAlignment.RIGHT);
        // 设置垂直居中
        styleR.setVerticalAlignment(VerticalAlignment.CENTER);

        styleR.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        styleR.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        String[] sumKeys = new String[]{"C", "D", "I", "J"};
        for (int j = 0; j < sumKeys.length; j++) {

            char key = sumKeys[j].charAt(0);
            XSSFCell cell0 = rowSum.createCell((key - 'A'));
            String start = sumKeys[j] + 2;
            String end = sumKeys[j] + (lastRow + 1);
            String digiNums = "8";//这儿规定0位小数无效
            try {
                String formula1 = "SUM(" + start + ":" + end + ")";
                String formula2 = "ROUND(" + formula1 + "," + digiNums + ")";
//                String formula3 = "INT(" + formula1 + ")";//int INT fastexcel 读不出来
                String formula3 = formula1;//int
                if (key < 'E') {
                    cell0.setCellFormula(formula3);
                } else {
                    cell0.setCellFormula(formula2);
                }

            } catch (FormulaParseException | IllegalStateException e) {
                e.printStackTrace();
                Lg.e("总计构建失败>>>>", sheet.getSheetName());
            }
            cell0.setCellStyle(styleR);
            formulaEvaluator.evaluate(cell0);
        }

        //-------------------所有列与总计 tab1-----------------------------------------------------------

        //-------------------sku和数量的总计 tab2-----------------------------------------------------------

        Map<String, AMZout> mapSkuMerges = rAmZoutList.stream().collect(Collectors.toMap(AMZout::getSku, item -> item, (oldValue, newValue) -> {
//            oldValue.setShoes(oldValue.getShoes() + newValue.getShoes());//会改变原始元素，导致累加！！！
//            return oldValue;

            // 创建一个新对象
            AMZout result = new AMZout();
            // 设置SKU
            result.setSku(oldValue.getSku());
            // 设置鞋子数量
            result.setShoes(oldValue.getShoes() + newValue.getShoes());
            // 返回新对象
            return result;

        }));
        List<AMZout> mapSkuMergesList = mapSkuMerges.values().stream().sorted(Comparator.comparing(o -> o.getSku())).collect(Collectors.toList());

        int lenMerge = mapSkuMergesList.size();


        int tab1LastRow = sheet.getLastRowNum();

        tab1LastRow = tab1LastRow + 2;//空两行

        XSSFCellStyle styleIn = workbook.createCellStyle();//XSSFCellStyle才可自由设置颜色
        //淡粉色 238,180,192
        byte[] byteColor = new byte[]{(byte) 238, (byte) 180, (byte) 192};
        PoiUtiles.cellColor(styleIn, style, byteColor);

        for (int i = 0; i < lenMerge; i++) {
            XSSFRow row = sheet.createRow(tab1LastRow + i);
            AMZout depotRes1 = mapSkuMergesList.get(i);
            for (int j = 0; j < 2; j++) {
                XSSFCell cell0 = row.createCell(j);
                switch (j) {
                    case 0:
                        cell0.setCellValue(depotRes1.getSku());
                        cell0.setCellStyle(styleIn);
                        break;
                    case 1:
                        //总数量（鞋子）
                        cell0.setCellValue(depotRes1.getShoes());
                        cell0.setCellStyle(styleIn);
                        break;
                }
            }
        }
        //最后一组0
        int lastRowTab2 = sheet.getLastRowNum();//前面map大小为0，空表？？
        if (lastRowTab2 < 1) {
            return sheet;
        }
        XSSFRow rowSumTab2 = sheet.createRow(lastRowTab2 + 1);
        for (int j = 0; j < 2; j++) {
            XSSFCell cell0 = rowSumTab2.createCell(j);
            if (j == 0) {
                cell0.setCellValue("总计");
                cell0.setCellStyle(style);
            } else {

                String start = "B" + (tab1LastRow + 1);
                String end = "B" + (lastRowTab2 + 1);
                try {
                    String formula1 = "SUM(" + start + ":" + end + ")";
                    cell0.setCellFormula(formula1);
                } catch (FormulaParseException | IllegalStateException e) {
                    e.printStackTrace();
                    Lg.e("总计构建失败>>>>", sheet.getSheetName());
                }
                cell0.setCellStyle(styleR);
                formulaEvaluator.evaluate(cell0);
            }
        }
        //-------------------sku和数量的总计 tab2-----------------------------------------------------------

        //-------------------去掉尺码的总计 tab3-----------------------------------------------------------
//        int tab1LastRow = sheet.getLastRowNum();
//        tab1LastRow = tab1LastRow + 2;//空两行 //与前面平行或者行重复，不能使用sheet.createRow，会覆盖前面的！

        List<AMZout> amZouts_no_size = new ArrayList<>();
        rAmZoutList.forEach(new Consumer<AMZout>() {
            @Override
            public void accept(AMZout amZout) {
                AMZout amZout1 = new AMZout();

                String sizePart = PoiUtiles.getSizeInSKUZ(amZout.getSku());
                if (sizePart != null) {
                    //US-BS-823-black fleece-8 >>> US-BS
//                    amZout1.setSku(amZout.getSku().split("-" + sizePart)[0]); // 不严谨！！！

                    String result = amZout.getSku().substring(0, amZout.getSku().length() - (sizePart.length() + 1)); // 去掉最后两个字符
                    amZout1.setSku(result);

                } else {
                    //特例外才使用，一般格式错误 或者 箱包等无尺码商品？？
//                    int sepPos = amZout.getSku().lastIndexOf("-"); //获取分隔符的位置
//                    String beforeSep = amZout.getSku().substring(0, sepPos); //获取分隔符前面的子字符串
                    amZout1.setSku(amZout.getSku());
                }

                if (amZout1.getSku().equals("US-BS")) {

                    boolean ttt = true;
                }

                amZout1.setShoes(amZout.getShoes());
                amZouts_no_size.add(amZout1);

            }
        });

        Map<String, AMZout> map_no_sizes = amZouts_no_size.stream().collect(Collectors.toMap(AMZout::getSku, item -> item, (oldValue, newValue) -> {
            // 创建一个新对象
            AMZout result = new AMZout();
            // 设置SKU
            result.setSku(oldValue.getSku());
            // 设置鞋子数量
            result.setShoes(oldValue.getShoes() + newValue.getShoes());
            // 返回新对象
            return result;
        }));
        List<AMZout> res_no_sizes = map_no_sizes.values().stream().sorted(Comparator.comparing(o -> o.getSku())).collect(Collectors.toList());
        int len2 = res_no_sizes.size();
        int cellW = 'D' - 'A';//定在D列

        XSSFCellStyle styleIn3 = workbook.createCellStyle();//XSSFCellStyle才可自由设置颜色
        //淡粉色 238,180,192
        //淡蓝色 135,206,235
        byte[] byteColor3 = new byte[]{(byte) 135, (byte) 206, (byte) 235};
        PoiUtiles.cellColor(styleIn3, style, byteColor3);

        for (int i = 0; i <= len2; i++) {
            XSSFRow row = sheet.getRow(tab1LastRow + i);
            if (i < len2) {

                AMZout depotRes1 = res_no_sizes.get(i);
                for (int j = 0; j < 2; j++) {

                    XSSFCell cell0 = row.createCell(cellW + j);
                    switch (j) {
                        case 0:
                            cell0.setCellValue(depotRes1.getSku());
                            cell0.setCellStyle(styleIn3);
                            break;
                        case 1:
                            //总数量（鞋子）
                            cell0.setCellValue(depotRes1.getShoes());
                            cell0.setCellStyle(styleIn3);
                            break;
                    }
                }
            } else {
                //最后一组0
//                int lastRowTab3 = tab1LastRow + i;//前面map大小为0，空表？？

//                XSSFRow rowSumTab3 = sheet.createRow(lastRowTab2 + 1);
                for (int j = 0; j < 2; j++) {
                    XSSFCell cell0 = row.createCell(cellW + j);
                    if (j == 0) {
                        cell0.setCellValue("总计");
                        cell0.setCellStyle(style);
                    } else {

                        String start = "E" + (tab1LastRow + 1);
                        String end = "E" + (tab1LastRow + len2);
                        try {
                            String formula1 = "SUM(" + start + ":" + end + ")";
                            cell0.setCellFormula(formula1);
                        } catch (FormulaParseException | IllegalStateException e) {
                            e.printStackTrace();
                            Lg.e("总计构建失败>>>>", sheet.getSheetName());
                        }
                        cell0.setCellStyle(styleR);
                        formulaEvaluator.evaluate(cell0);
                    }
                }

            }

        }
        //-------------------去掉尺码的总计 tab3-----------------------------------------------------------

        // 6494  6474 6844 会缺少项？？ 6930
        //-------------------去掉颜色和尺码=款号后的总计 tab4-----------------------------------------------------------
        //款号 箱数 总数量 毛重 净重 体积

        List<AMZout> amZouts_model = new ArrayList<>();
        rAmZoutList.forEach(new Consumer<AMZout>() {
            @Override
            public void accept(AMZout amZout) {
                AMZout amZout1 = new AMZout();

                amZout1.setSku(getSkuModel(amZout.getSku()));

                List<BoxRules> rules = boxRulesGroup.get(amZout.getSku());
                if (rules != null && rules.size() > 0) {
                    amZout.setARTICLE(rules.get(0).getARTICLE());
                    amZout.setErpCode(rules.get(0).getErpCode());
                    amZout.setHS_CODE(rules.get(0).getHS_CODE());
                } else {
                    Lg.e("ErpCode编码缺失>>>>ALL-IN-ONE", amZout);
//                    errs.add(amZout.getStock() + ">" + amZout.getSku() + ">Erp款号缺失");
                }
                //去掉颜色和尺码=款号后的总计
                //截取-号后面的部分
                List<ShenBaoInfo> shenBaoInfos = getSkuShenbaoAll(amZout, shenBaoInfoGroup);

                if (shenBaoInfos != null && shenBaoInfos.size() > 0) {
                    if (isUS) {
//                       String usMoney = shenBaoInfos.get(0).getUsd().contains("")
                        //不报关的没有价格
                        if (StringUtils.isNumericAll(shenBaoInfos.get(0).getUsd())) {
                            amZout1.setPerMoney(String.valueOf(StringUtils.get2Numbers(shenBaoInfos.get(0).getUsd())));
                        } else {
                            amZout1.setPerMoney("0");
                        }


                    } else {
//                        amZout1.setPerMoney(shenBaoInfos.get(0).getRmb());

                        if (StringUtils.isNumericAll(shenBaoInfos.get(0).getRmb())) {
                            amZout1.setPerMoney(String.valueOf(StringUtils.get2Numbers(shenBaoInfos.get(0).getRmb())));
                        } else {
                            amZout1.setPerMoney("0");
                        }


                    }
//                    amZout.setShenbaoNumer(shenBaoInfos.get(0).getHsCode());
//                    amZout.setShenbaoName(shenBaoInfos.get(0).getInfo());
//                    amZout.setUnit(shenBaoInfos.get(0).getUnit());//单位


                }


                amZout1.setBoxes(amZout.getBoxes());
                amZout1.setShoes(amZout.getShoes());
                double allWeight = amZout.getWeight() * amZout.getBoxes();
                amZout1.setWeight(allWeight);
                double vol = amZout.getLength() * amZout.getWidth() * amZout.getHeight() / 1000000 * amZout.getBoxes();
                amZout1.setAllVolume(vol);
                //净重 （净重=总重量-2.5*箱数）
                amZout1.setPureWeight(allWeight - 2.5 * amZout.getBoxes());
                amZouts_model.add(amZout1);

            }
        });

        Map<String, AMZout> map_model = amZouts_model.stream().collect(Collectors.toMap(AMZout::getSku, item -> item, (oldValue, newValue) -> {
            oldValue.setShoes(oldValue.getShoes() + newValue.getShoes());
            oldValue.setBoxes(oldValue.getBoxes() + newValue.getBoxes());
            oldValue.setWeight(oldValue.getWeight() + newValue.getWeight());
            oldValue.setPureWeight(oldValue.getPureWeight() + newValue.getPureWeight());
            oldValue.setAllVolume(oldValue.getAllVolume() + newValue.getAllVolume());
//            oldValue.setShoes(oldValue.getShoes() + newValue.getShoes());
            return oldValue;
        }));
        List<AMZout> res_model = map_model.values().stream().sorted(Comparator.comparing(o -> o.getSku())).collect(Collectors.toList());
        int len4 = res_model.size();
        int cellW4 = 'H' - 'A';//定在H列
//款号 箱数 总数量 毛重 净重 体积
        String[] stringsHead = new String[]{"款号", "箱数", "总数量", "毛重", "净重", "体积", "人民币/美元单价", "总货值"};
        for (int i = 0; i <= (len4 + 1); i++) {//多两行
            XSSFRow row = sheet.getRow(tab1LastRow + i);
            if (row == null) {
                continue;
            }
            if (i == 0) {//head
                for (int j = 0; j < 8; j++) {

                    XSSFCell cell0 = row.createCell(cellW4 + j);
                    cell0.setCellValue(stringsHead[j]);
                    cell0.setCellStyle(styleIn3);
                }
            } else if (i <= len4) {

                AMZout depotRes1 = res_model.get(i - 1);
                for (int j = 0; j < 8; j++) {

                    XSSFCell cell0 = row.createCell(cellW4 + j);
                    switch (j) {
                        case 0:
                            cell0.setCellValue(depotRes1.getSku());
                            cell0.setCellStyle(styleIn3);
                            break;
                        case 1:
                            //箱子数量
                            cell0.setCellValue(depotRes1.getBoxes());
                            cell0.setCellStyle(styleIn3);
                            break;
                        case 2:
                            //总数量（鞋子）
                            cell0.setCellValue(depotRes1.getShoes());
                            cell0.setCellStyle(styleIn3);
                            break;
                        case 3:
                            //毛重
                            cell0.setCellValue(depotRes1.getWeight());
                            cell0.setCellStyle(styleIn3);
                            break;
                        case 4:
                            //净重
                            cell0.setCellValue(depotRes1.getPureWeight());
                            cell0.setCellStyle(styleIn3);
                            break;
                        case 5:
                            //体积
                            cell0.setCellValue(depotRes1.getAllVolume());
                            cell0.setCellStyle(styleIn3);
                            break;
                        case 6://单价>/US/RMB
                            cell0.setCellValue(depotRes1.getPerMoney());
                            cell0.setCellStyle(styleIn3);
                            break;
                        case 7://总价

//                            cell0.setCellValue(Float.valueOf(depotRes1.getPerMoney()) * depotRes1.getShoes());
//                            cell0.setCellStyle(styleIn3);

                            try {
                                float sumIn = Float.parseFloat(depotRes1.getPerMoney());

                                cell0.setCellValue(StringUtils.get2Numbers(String.valueOf(depotRes1.getShoes() * sumIn)));
                                cell0.setCellStyle(styleIn3);
                            } catch (NumberFormatException | NullPointerException e) {
//                                    e.printStackTrace();


//                                HSSFCellStyle styleErr = workbook.createCellStyle();
                                XSSFCellStyle styleErr = workbook.createCellStyle();
                                styleErr.cloneStyleFrom(styleIn3);
                                styleErr.setFillForegroundColor(IndexedColors.RED.index);
                                styleErr.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                                cell0.setCellValue("err");
                                cell0.setCellStyle(styleErr);
                            }


                            break;
                    }
                }
            } else {
                //最后一组0
//                int lastRowTab3 = tab1LastRow + i;//前面map大小为0，空表？？

//                XSSFRow rowSumTab3 = sheet.createRow(lastRowTab2 + 1);
                for (int j = 0; j < 6; j++) {
                    if (row == null) {
                        boolean tt = true;
                    }
                    XSSFCell cell0 = row.createCell(cellW4 + j);
                    if (j == 0) {
                        cell0.setCellValue("总计");
                        cell0.setCellStyle(style);
                    } else {
                        char pos = (char) ('H' + j);
                        String start = String.valueOf(pos) + (tab1LastRow + 2);
                        String end = String.valueOf(pos) + (tab1LastRow + len4 + 1);
                        try {
                            String formula1 = "SUM(" + start + ":" + end + ")";
                            cell0.setCellFormula(formula1);
                        } catch (FormulaParseException | IllegalStateException e) {
                            e.printStackTrace();
                            Lg.e("总计构建失败>>>>", sheet.getSheetName());
                        }
                        cell0.setCellStyle(styleR);
                        formulaEvaluator.evaluate(cell0);
                    }
                }

                //货价总计
                XSSFCell cell0 = row.createCell(cellW4 + 7);
                char pos = (char) ('O');
                String start = String.valueOf(pos) + (tab1LastRow + 2);
                String end = String.valueOf(pos) + (tab1LastRow + len4 + 1);
                try {
                    String formula1 = "SUM(" + start + ":" + end + ")";
                    cell0.setCellFormula(formula1);
                } catch (FormulaParseException | IllegalStateException e) {
                    e.printStackTrace();
                    Lg.e("总计构建失败>>>>", sheet.getSheetName());
                }
                cell0.setCellStyle(styleR);
                formulaEvaluator.evaluate(cell0);

            }

        }


        PoiUtiles.adjustAutoWidth(sheet, mTitles.size());
        return sheet;
    }


    //PACKING_LIST
    public List<String> writePACKING_LIST_template(Map<String, List<AMZout>> dataMap, String shopName, String folderToken, Map<String, List<BoxRules>> boxRulesGroup, TEMPLATE_company isFurui) throws IOException, InvalidFormatException {
//        Set<String> strings = dataMap.keySet();
        List<String> errs = new ArrayList<>();
        String srcTemplate = "excel/PACKING_LIST_template.xls";
        switch (isFurui) {
            case CM:
                srcTemplate = "excel/PACKING_LIST_template_cm.xls";
                break;
            case Furui:
                break;
            case Anbu:
                srcTemplate = "excel/PACKING_LIST_template_anbu.xls";
                break;
        }

        org.springframework.core.io.Resource resource = new ClassPathResource(srcTemplate);
//        HSSFWorkbook
        InputStream fileInputStreamIN = resource.getInputStream();
        HSSFWorkbook workbook = new HSSFWorkbook(fileInputStreamIN);
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(0);
//        Row newRow = sheet.createRow(8); // 在第一行创建一个新的行对象
//        workbook.setPrintArea(0, 0, 3, 0, 9);
        sheet.setPrintGridlines(true);
        //合并一下重复sku项？
        List<AMZout> mapSkuMerges = dataMap.values().stream()
                .flatMap(List::stream) // 将每个List<AMZout>展开为一个流
                .sorted(Comparator.comparing(o -> o.getSku()))
                .collect(Collectors.toList()); // 将流中的元素收集到一个新的List<AMZout>中

        List<AMZout> mapSkuMergesCopy = new ArrayList<>();
        try {
            //不用拷贝操作累加后，会影响writeINVOICE_Ttemplate  writePI_Ttemplate
            mapSkuMergesCopy = CommonUtils.deepCopy(mapSkuMerges);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        //合并相同sku
        Map<String, AMZout> rAmZoutListX = mapSkuMergesCopy.stream().collect(Collectors.toMap(AMZout::getSku, item -> item, (oldValue, newValue) -> {
//            oldValue.setShoes(oldValue.getShoes() + newValue.getShoes());//会改变原始元素，导致累加！！！
//            return oldValue;

            // 创建一个新对象
//            AMZout result = new AMZout();
            // 设置SKU
//            result.setSku(oldValue.getSku());
            // 设置鞋子数量
            oldValue.setShoes(oldValue.getShoes() + newValue.getShoes());
            oldValue.setBoxes(oldValue.getBoxes() + newValue.getBoxes());
            // 返回新对象
            return oldValue;
        }));

        List<AMZout> rAmZoutList = rAmZoutListX.values().stream().sorted(Comparator.comparing(o -> o.getSku())).collect(Collectors.toList());

        //总计
        AMZout allSummarize = new AMZout();
        rAmZoutList.forEach(new Consumer<AMZout>() {
            @Override
            public void accept(AMZout amZout) {
                List<BoxRules> rules = boxRulesGroup.get(amZout.getSku());
                if (rules != null && rules.size() > 0) {
                    amZout.setARTICLE(rules.get(0).getARTICLE());
                    amZout.setErpCode(rules.get(0).getErpCode());
                    amZout.setHS_CODE(rules.get(0).getHS_CODE());
                } else {
                    Lg.e("ErpCode编码缺失>>>>", amZout);
                    errs.add(amZout.getStock() + ">" + amZout.getSku() + ">Erp款号缺失");
                }
                allSummarize.setBoxes(allSummarize.getBoxes() + amZout.getBoxes());
                allSummarize.setShoes(allSummarize.getShoes() + amZout.getShoes());
                double allWeight = amZout.getWeight() * amZout.getBoxes();
                allSummarize.setWeight(allSummarize.getWeight() + allWeight);
                double vol = amZout.getLength() * amZout.getWidth() * amZout.getHeight() / 1000000 * amZout.getBoxes();
                allSummarize.setAllVolume(allSummarize.getAllVolume() + vol);
                //净重 （净重=总重量-2.5*箱数）
                allSummarize.setPureWeight(allSummarize.getPureWeight() + (allWeight - 2.5 * amZout.getBoxes()));

            }
        });
//        rAmZoutList.stream().

        List<AMZout> mergeSizesAMZ = new ArrayList<>();
        Map sizePos = ConstantFu.mapPackingList_size();
        rAmZoutList.forEach(new Consumer<AMZout>() {
            @Override
            public void accept(AMZout amZout) {
                //去掉尺码
//                int sepPos = amZout.getSku().lastIndexOf("-"); //获取分隔符的位置
//                String beforeSep = amZout.getSku().substring(0, sepPos); //获取分隔符前面的子字符串

                String sizePart = PoiUtiles.getSizeInSKUZ(amZout.getSku());


                AMZout amZout1 = new AMZout();
//                amZout1.setSku(beforeSep);
                if (sizePart != null) {
//                    amZout1.setSku(amZout.getSku().split("-" + sizePart)[0]);
                    String result = amZout.getSku().substring(0, amZout.getSku().length() - (sizePart.length() + 1)); // 去掉最后尺码字符
                    amZout1.setSku(result);
                } else {
                    amZout1.setSku(amZout.getSku());
                }

                if (amZout.getSku().equals("US-BS")) {

                    boolean tt = true;
                }


                amZout1.setShoes(amZout.getShoes());
                amZout1.setARTICLE(amZout.getARTICLE());
                amZout1.setHS_CODE(amZout.getHS_CODE());
                amZout1.setErpCode(amZout.getErpCode());
                amZout1.setBoxes(amZout.getBoxes());

                amZout1.setObName(amZout.getObName());//带上文件名称，方便定位错误
                if (sizePart == null) {
                    sizePart = "";
                    boolean t = true;
                }
                String after = sizePart.split("-")[0];//末尾有特殊-1等情况
                // 将_号替换为.号
                String replaced = after.replace("_", ".");

                boolean isNum = StringUtils.isNumericAll(replaced);
                if (StringUtils.isNumericAll(replaced)) {//sku尺码

                    boolean tt = true;

                    // 将字符串转为double
                    double number = Double.parseDouble(replaced);

                    String format = number % 1 == 0 ? "%.0f" : "%.1f";
                    // 使用String.format()方法来保留整数或一位小数
                    String formatted = String.format(format, number);

                    String address = (String) sizePos.get(formatted);
                    //39.5错误由更新模版修复
                    if (address == null) {
                        boolean ttt = true;
                    }

                    if (amZout1.getPosArray() == null) {
                        amZout1.setPosArray(new LinkedHashMap<>());

                        amZout1.getPosArray().put(address, amZout.getShoes());
                    } else {
                        amZout1.getPosArray().put(address, amZout.getShoes());
                    }

                } else {//除了鞋子，箱包等都没有尺码,直接写total

                    if (amZout1.getPosArray() == null) {
                        amZout1.setPosArray(new LinkedHashMap<>());

                        amZout1.getPosArray().put("XX", amZout.getShoes());
                    } else {
                        amZout1.getPosArray().put("XX", amZout.getShoes());
                    }

                }

                if (amZout.getSku().contains("US-HMT-004-Black")) {

//                    boolean tt = true;
                }

                mergeSizesAMZ.add(amZout1);
            }
        });

        Map<String, AMZout> map_no_sizes = mergeSizesAMZ.stream().collect(Collectors.toMap(AMZout::getSku, item -> item, (oldValue, newValue) -> {
            oldValue.setShoes(oldValue.getShoes() + newValue.getShoes());
            oldValue.setBoxes(oldValue.getBoxes() + newValue.getBoxes());//新对象，总数
            oldValue.getPosArray().putAll(newValue.getPosArray());//各个位置对应数量
            return oldValue;
        }));
        List<AMZout> res_no_sizes = map_no_sizes.values().stream().sorted(Comparator.comparing(o -> o.getSku())).collect(Collectors.toList());


        Map<String, List<AMZout>> sortedResMap = res_no_sizes.stream()
                .collect(Collectors.groupingBy(amz -> amz.getARTICLE() + "-" + amz.getHS_CODE()));

        //排序
        TreeMap<String, List<AMZout>> mapPacking = new TreeMap<>(sortedResMap);

        String C_title = "E10,E11";
        List<String> C_titleM = Arrays.asList(C_title.split(","));
        C_titleM.forEach(new Consumer<String>() {
            @Override
            public void accept(String s) {
                switch (s) {
                    case "E10"://INVOICE DATE: 注意空格？
                        String date = "INVOICE DATE:  " + CommonUtils.getStringMonthAndDay("/");
                        setCellValueByFilter(sheet, s, date);
                        break;
                    case "E11"://PORT OF LOADING: 注意空格？
                        String address = "PORT OF LOADING:  " + "QINGDAO";
                        setCellValueByFilter(sheet, s, address);
                        break;
                }
            }
        });
        //B15>


        Set<String> groupKeysSet = mapPacking.keySet();

        int start = 14;
        int footStart = 17;
        Object[] groupKeys = groupKeysSet.toArray();
        HSSFCellStyle styleR = workbook.createCellStyle();


        HSSFCellStyle styleRTitle = workbook.createCellStyle();


        // 设置水平居中
        styleR.setAlignment(HorizontalAlignment.CENTER);
        // 设置垂直居中
        styleR.setVerticalAlignment(VerticalAlignment.CENTER);
        styleR.setFillForegroundColor(IndexedColors.WHITE.index);

        HSSFFont font = workbook.createFont();
//设置字体名称为 Arial
        font.setFontName(HSSFFont.FONT_ARIAL);

        HSSFFont font2 = workbook.createFont();
//设置字体名称为 Arial
        font2.setFontName(HSSFFont.FONT_ARIAL);
        styleR.setFont(font2);
//        styleR.setHidden(false);

        int rowIndex = 0;
        for (int i = 0; i < groupKeys.length; i++) {

            String titles = (String) groupKeys[i];
            String[] titleTags = titles.split("-");
            List<AMZout> amZouts = mapPacking.get(titles);
            int topLen = amZouts.size();
            footStart = footStart + topLen;//不准,但直接最后去挨着遍历查

            int stepRow = topLen + 2;
            if (i == 0) {
                rowIndex = start;
            } else {

            }

            int la = sheet.getLastRowNum();
            int lass = sheet.getPhysicalNumberOfRows();
            if (rowIndex > sheet.getLastRowNum()) {

                boolean t = true;
            }
            sheet.shiftRows(rowIndex, sheet.getLastRowNum(), stepRow);
            //加上两个头
            for (int j = 0; j < topLen + 2; j++) {
//                Row row = sheet.createRow(start + j);

                Row row = sheet.createRow(rowIndex + j);
                if (j == 0) {
                    Cell bTitle0 = row.getCell(0);
                    if (bTitle0 != null) {
                        bTitle0.setCellValue("N/M");
                    } else {
                        bTitle0 = row.createCell(0);
                        bTitle0.setCellValue("N/M");
                    }

                    Cell bTitle = row.createCell(1);
                    bTitle.setCellValue(titleTags[0]);
                    styleRTitle.cloneStyleFrom(styleR);
                    font.setBold(true);
                    styleRTitle.setFont(font);
                    bTitle.setCellStyle(styleRTitle);
                } else if (j == 1) {
                    Cell bTitle = row.createCell(1);
                    bTitle.setCellValue(titleTags[1]);
                    bTitle.setCellStyle(styleR);

                    font.setBold(true);
                    styleRTitle.setFont(font);
                    bTitle.setCellStyle(styleRTitle);
                    sheet.addMergedRegion(new CellRangeAddress(row.getRowNum() - 1, row.getRowNum(), 2, 29));

                } else {
                    //A~AD 30列 >>> A~AE 31列增加39.5
                    for (int k = 0; k < 31; k++) {
                        Cell shoes = row.createCell(k);
                        AMZout amZout = amZouts.get(j - 2);
//                    int sepPos = amZout.getSku().lastIndexOf("-"); //获取分隔符的位置
                        if (k == 0) {
                            shoes.setCellValue("N/M");
                        } else if (k == 1) {
                            shoes.setCellValue(amZout.getSku());
                        } else {

                            amZout.getPosArray().forEach(new BiConsumer<String, Integer>() {
                                @Override
                                public void accept(String s, Integer integer) {

                                    CellAddress cellAddress = shoes.getAddress();
                                    try {
                                        if (shoes.getAddress().toString().startsWith(s)) {
                                            shoes.setCellValue(integer);
                                            //                                        System.out.println("匹配的" + s);
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        Lg.e("未知错误❌>>>>>" + amZout.getObName(), e.getMessage());
                                    }
                                }
                            });

                            if (shoes.getAddress().toString().contains("AE")) {
                                shoes.setCellValue(amZout.getShoes());
                            }
                        }
                        shoes.setCellStyle(styleR);
                    }
                }
            }

            //用完再赋值
            rowIndex = rowIndex + stepRow;

        }

        int last = sheet.getLastRowNum();
        boolean t = true;
        //底部汇总
        for (int i = footStart; i < last; i++) {
            Row row = sheet.getRow(i);
            if (row != null) {
                Cell head = row.getCell(0);
                Cell val = row.getCell(1);
                if (head != null && head.getStringCellValue().contains("PAIRS")) {
                    val.setCellValue(allSummarize.getShoes() + " PAIRS");
                    int endRow = i - 1;
                    Row rowTotal = sheet.getRow(endRow);
                    Cell cellTotal = rowTotal.getCell(30);
                    //这儿用公式总计，可以用作对比校验
                    cellTotal.setCellFormula("SUM(" + "AE15" + ":" + ("AE" + endRow) + ")");

                    // 合并第一行到第二行的第一列到第三列
                    sheet.addMergedRegion(new CellRangeAddress(start, endRow, 0, 0));
                    // 获取合并后的单元格里面的第一个单元格
                    Cell cell = sheet.getRow(start).getCell(0);
                    // 给第一个单元格设置值为“姓名”
                    cell.setCellValue("N/M");
//                    cell.setCellStyle(styleR);


                } else if (head != null && head.getStringCellValue().contains("CARTONS")) {
                    val.setCellValue(allSummarize.getBoxes() + " CARTONS");
                } else if (head != null && head.getStringCellValue().contains("GROSS")) {
                    val.setCellValue(StringUtils.get2Numbers(String.valueOf(allSummarize.getWeight())) + " KGS");
                } else if (head != null && head.getStringCellValue().contains("NET")) {
                    val.setCellValue(StringUtils.get2Numbers(String.valueOf(allSummarize.getPureWeight())) + " KGS");
                } else if (head != null && head.getStringCellValue().contains("MEASUREMENT")) {
                    val.setCellValue(StringUtils.get2Numbers(String.valueOf(allSummarize.getAllVolume())) + " CBM");
                }


            }
        }


        String folder = "报关单";

        File fileDir = new File((realPathExcel + C_ResultExcelFloder + "/" + folderToken + "/" + folder));
        if (!fileDir.exists()) {
            fileDir.mkdirs();
            Lg.i("saveToAssets", "文件的路径不存在，创建:", fileDir.getPath());

        }

        String timeCreated = "-" + CommonUtils.getStringMonthAndDay();

        String pathBack = fileDir.getPath() + "/" + shopName + timeCreated + ".xls";
        FileOutputStream out = new FileOutputStream(pathBack);
        workbook.write(out);
        out.close();
        workbook.close();
        if (fileInputStreamIN != null) {
            fileInputStreamIN.close();
        }
        return errs;
    }

    //INVOICE > String直接返回错误信息，子文件路径不重要了，在zip里
    public List<String> writeINVOICE_Ttemplate(Map<String, List<AMZout>> dataMap, String shopName, String folderToken, Map<String, List<BoxRules>> boxRulesGroup, Map<String, List<ShenBaoInfo>> shenBaoInfoGroup, TEMPLATE_company isFurui, boolean isUS) throws IOException, InvalidFormatException {

        List<String> errs = new ArrayList<>();

        String srcTemplate = "excel/INVOICE_template.xls";

        switch (isFurui) {
            case CM:
                srcTemplate = "excel/INVOICE_cm.xls";
                break;
            case Furui:
                break;
            case Anbu:
                srcTemplate = "excel/INVOICE_anbui.xls";
                break;
        }


        org.springframework.core.io.Resource resource = new ClassPathResource(srcTemplate);
//        HSSFWorkbook
        InputStream fileInputStreamIN = resource.getInputStream();
        HSSFWorkbook workbook = new HSSFWorkbook(fileInputStreamIN);
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(0);
        sheet.setPrintGridlines(true);
        //合并一下重复sku项？
        List<AMZout> rAmZoutList = dataMap.values().stream()
                .flatMap(List::stream) // 将每个List<AMZout>展开为一个流
                .sorted(Comparator.comparing(o -> o.getSku()))
                .collect(Collectors.toList()); // 将流中的元素收集到一个新的List<AMZout>中
        int len = rAmZoutList.size();

        //总计
        AMZout allSummarize = new AMZout();
        rAmZoutList.forEach(new Consumer<AMZout>() {
            @Override
            public void accept(AMZout amZout) {
                List<BoxRules> rules = boxRulesGroup.get(amZout.getSku());
                if (rules != null && rules.size() > 0) {
                    amZout.setARTICLE(rules.get(0).getARTICLE());
                    amZout.setErpCode(rules.get(0).getErpCode());
                    amZout.setHS_CODE(rules.get(0).getHS_CODE());
                } else {
                    Lg.e("ErpCode编码缺失>>>>", amZout);
                    errs.add(amZout.getStock() + ">" + amZout.getSku() + ">Erp款号缺失");
                }

//                writeINVOICE
                //找到最后一个-号的位置
                //截取-号后面的部分
                //取出款号
                List<ShenBaoInfo> shenBaoInfos = getSkuShenbaoAll(amZout, shenBaoInfoGroup);
                if (shenBaoInfos != null && shenBaoInfos.size() > 0) {
                    if (isUS) {
//                       String usMoney = shenBaoInfos.get(0).getUsd().contains("")
//                        amZout.setPerMoney(String.valueOf(StringUtils.get2Numbers(shenBaoInfos.get(0).getUsd())));

                        try {
                            amZout.setPerMoney(String.valueOf(StringUtils.get2Numbers(shenBaoInfos.get(0).getUsd())));
                        } catch (NumberFormatException e) {
//                                e.printStackTrace();
                            amZout.setPerMoney("0");
                        }


                    } else {
//                        amZout.setPerMoney(shenBaoInfos.get(0).getRmb());


                        try {
                            amZout.setPerMoney(String.valueOf(StringUtils.get2Numbers(shenBaoInfos.get(0).getRmb())));
                        } catch (NumberFormatException e) {
//                                e.printStackTrace();
                            amZout.setPerMoney("0");
                        }


                    }
                } else {
                    Lg.e("申报要素缺失>>>>", amZout);

                    errs.add(amZout.getStock() + ">" + amZout.getSku() + ">申报要素缺失");
                }

                allSummarize.setBoxes(allSummarize.getBoxes() + amZout.getBoxes());
                allSummarize.setShoes(allSummarize.getShoes() + amZout.getShoes());
                double allWeight = amZout.getWeight() * amZout.getBoxes();
                allSummarize.setWeight(allSummarize.getWeight() + allWeight);
                double vol = amZout.getLength() * amZout.getWidth() * amZout.getHeight() / 1000000 * amZout.getBoxes();
                allSummarize.setAllVolume(allSummarize.getAllVolume() + vol);
                //净重 （净重=总重量-2.5*箱数）
                allSummarize.setPureWeight(allSummarize.getPureWeight() + (allWeight - 2.5 * amZout.getBoxes()));

            }
        });
//        rAmZoutList.stream().

        List<AMZout> mergeSizesAMZ = new ArrayList<>();
        Map sizePos = ConstantFu.mapPackingList_size();
        rAmZoutList.forEach(new Consumer<AMZout>() {
            @Override
            public void accept(AMZout amZout) {
                //去掉尺码
//                int sepPos = amZout.getSku().lastIndexOf("-"); //获取分隔符的位置
//                String beforeSep = amZout.getSku().substring(0, sepPos); //获取分隔符前面的子字符串

                String sizePart = PoiUtiles.getSizeInSKUZ(amZout.getSku());

//                amZout1.setSku(amZout.getSku().split("-" + sizePart)[0]);


                AMZout amZout1 = new AMZout();
//                amZout1.setSku(amZout.getSku().split("-" + sizePart)[0]);

                if (sizePart != null) {
//                    amZout1.setSku(amZout.getSku().split("-" + sizePart)[0]);
                    String result = amZout.getSku().substring(0, amZout.getSku().length() - (sizePart.length() + 1)); // 去掉最后两个字符
                    amZout1.setSku(result);
                } else {
                    amZout1.setSku(amZout.getSku());
                }

                amZout1.setShoes(amZout.getShoes());
                amZout1.setARTICLE(amZout.getARTICLE());
                amZout1.setHS_CODE(amZout.getHS_CODE());
                amZout1.setErpCode(amZout.getErpCode());
                amZout1.setBoxes(amZout.getBoxes());

                amZout1.setPerMoney(amZout.getPerMoney());

                mergeSizesAMZ.add(amZout1);
            }
        });

        Map<String, AMZout> map_no_sizes = mergeSizesAMZ.stream().collect(Collectors.toMap(AMZout::getSku, item -> item, (oldValue, newValue) -> {
            oldValue.setShoes(oldValue.getShoes() + newValue.getShoes());
            oldValue.setBoxes(oldValue.getBoxes() + newValue.getBoxes());//新对象，总数
//            oldValue.getPosArray().putAll(newValue.getPosArray());//各个位置对应数量
            return oldValue;
        }));
        List<AMZout> res_no_sizes = map_no_sizes.values().stream().collect(Collectors.toList());


        Map<String, List<AMZout>> mapPacking = res_no_sizes.stream().sorted(Comparator.comparing(o -> o.getSku()))
                .collect(Collectors.groupingBy(amz -> amz.getARTICLE() + "-" + amz.getHS_CODE()));

        //排序
//        TreeMap<String, List<AMZout>> mapPacking = new TreeMap<>(sortedResMap);

        //美元 Unit Price(FOB QINGDAO USD/PR)  Amount(USD)
        //人民币 Unit Price(FOB QINGDAO RMB/PR) Amount(RMB)
        String C_title = "E12,F12";
        List<String> C_titleM = Arrays.asList(C_title.split(","));

        C_titleM.forEach(new Consumer<String>() {
            @Override
            public void accept(String s) {
                switch (s) {
                    case "E12"://INVOICE DATE: 注意空格？

                        String date = "Unit Price(FOB QINGDAO USD/PR)";
                        if (!isUS) {
                            date = "Unit Price(FOB QINGDAO RMB/PR)";
                        }

                        setCellValueByFilter(sheet, s, date);
                        break;
                    case "F12"://PORT OF LOADING: 注意空格？

                        String address = "Amount(USD)";
                        if (!isUS) {
                            address = "Amount(RMB)";
                        }
                        setCellValueByFilter(sheet, s, address);
                        break;
                }
            }
        });


        //A13>
        Set<String> groupKeysSet = mapPacking.keySet();

        int start = 12;
        int footStart = 13;
        Object[] groupKeys = groupKeysSet.toArray();
        HSSFCellStyle styleR = workbook.createCellStyle();

        HSSFCellStyle styleRTitle = workbook.createCellStyle();

        // 设置水平居中
        styleR.setAlignment(HorizontalAlignment.CENTER);
        // 设置垂直居中
        styleR.setVerticalAlignment(VerticalAlignment.CENTER);
        styleR.setFillForegroundColor(IndexedColors.WHITE.index);

        HSSFFont font = workbook.createFont();
//设置字体名称为 Arial
        font.setFontName(HSSFFont.FONT_ARIAL);

        HSSFFont font2 = workbook.createFont();
//设置字体名称为 Arial
        font2.setFontName(HSSFFont.FONT_ARIAL);
        styleR.setFont(font2);
//        styleR.setHidden(false);

        int rowIndex = 0;
        for (int i = 0; i < groupKeys.length; i++) {

            String titles = (String) groupKeys[i];
            String[] titleTags = titles.split("-");
            List<AMZout> amZouts = mapPacking.get(titles);
            int topLen = amZouts.size();
            footStart = footStart + topLen;//不准,但直接最后去挨着遍历查

            int stepRow = topLen + 2;
            if (i == 0) {
                rowIndex = start;
            } else {

            }

            int la = sheet.getLastRowNum();
            int lass = sheet.getPhysicalNumberOfRows();
            if (rowIndex > sheet.getLastRowNum()) {

                boolean t = true;
            }
            sheet.shiftRows(rowIndex, sheet.getLastRowNum(), stepRow);
            //加上两个头
            for (int j = 0; j < topLen + 2; j++) {
//                Row row = sheet.createRow(start + j);

                Row row = sheet.createRow(rowIndex + j);
                if (j == 0) {
                    Cell bTitle0 = row.getCell(0);
                    if (bTitle0 != null) {
                        bTitle0.setCellValue("N/M");
                    } else {
                        bTitle0 = row.createCell(0);
                        bTitle0.setCellValue("N/M");
                    }
                    Cell bTitle1 = row.createCell(1);
                    bTitle1.setCellValue("N/M");

                    Cell bTitle = row.createCell(2);
                    bTitle.setCellValue(titleTags[0]);
                    styleRTitle.cloneStyleFrom(styleR);
                    font.setBold(true);
                    styleRTitle.setFont(font);
                    bTitle.setCellStyle(styleRTitle);
                } else if (j == 1) {
                    Cell bTitle = row.createCell(2);
                    bTitle.setCellValue(titleTags[1]);
                    bTitle.setCellStyle(styleR);

                    font.setBold(true);
                    styleRTitle.setFont(font);
                    bTitle.setCellStyle(styleRTitle);
                    sheet.addMergedRegion(new CellRangeAddress(row.getRowNum() - 1, row.getRowNum(), 3, 5));

                } else {
                    //A~F 6列
                    for (int k = 0; k < 6; k++) {
                        Cell shoes = row.createCell(k);
                        AMZout amZout = amZouts.get(j - 2);
//                    int sepPos = amZout.getSku().lastIndexOf("-"); //获取分隔符的位置
                        if (k == 0) {
                            shoes.setCellValue("N/M");
                        } else if (k == 1) {
                            shoes.setCellValue("N/M");
                        } else {
                            if (shoes.getAddress().toString().contains("C")) {//双数
                                shoes.setCellValue(amZout.getSku());
                                shoes.setCellStyle(styleR);
                            }
                            if (shoes.getAddress().toString().contains("D")) {//双数
                                shoes.setCellValue(amZout.getShoes());
                                shoes.setCellStyle(styleR);
                            }
                            if (shoes.getAddress().toString().contains("E")) {//报关价
//                                shoes.setCellValue("32"); //设为字符无法求和？
                                shoes.setCellValue(amZout.getPerMoney());
                                shoes.setCellStyle(styleR);
                            }
                            if (shoes.getAddress().toString().contains("F")) {//报关价总价
//                                shoes.setCellValue(33);
                                String sum = "err";
                                try {
                                    float sumIn = Float.parseFloat(amZout.getPerMoney());

                                    shoes.setCellValue(StringUtils.get2Numbers(String.valueOf(amZout.getShoes() * sumIn)));
                                    shoes.setCellStyle(styleR);
                                } catch (NumberFormatException | NullPointerException e) {
//                                    e.printStackTrace();


                                    HSSFCellStyle styleErr = workbook.createCellStyle();
                                    styleErr.cloneStyleFrom(styleR);
                                    styleErr.setFillForegroundColor(IndexedColors.RED.index);
                                    styleErr.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                                    shoes.setCellValue("err");
                                    shoes.setCellStyle(styleErr);
                                }

                            }
                        }

                    }
                }
            }

            //用完再赋值
            rowIndex = rowIndex + stepRow;

        }

        int last = sheet.getLastRowNum();
        boolean t = true;
        int endRow = start;
        //底部汇总
        for (int i = footStart; i < last; i++) {
            Row row = sheet.getRow(i);
            if (row != null) {
                Cell head = row.getCell(0);
                if (head != null && head.getStringCellValue().contains("Ltd")) {
                    endRow = i - 7;
                }
            }
        }

        //sum 总计

        int lastSum = endRow + 1;
        Row row = sheet.getRow(lastSum);
        Cell sumShoes = row.getCell(3);
        sumShoes.setCellFormula("SUM(" + ("D" + start) + ":" + ("D" + lastSum) + ")");


        Cell sumMoney = row.getCell(5);
        sumMoney.setCellFormula("SUM(" + ("F" + start) + ":" + ("F" + lastSum) + ")");

//        if (head != null && head.getStringCellValue().contains("Ltd")) {
//            endRow = i - 7;
//        }


        // 合并第一行到第二行的第一列到第三列
        sheet.addMergedRegion(new CellRangeAddress(start, endRow, 0, 1));
        // 获取合并后的单元格里面的第一个单元格
        Cell cell = sheet.getRow(start).getCell(0);
        // 给第一个单元格设置值为“姓名”
        cell.setCellValue("N/M");


        String folder = "报关单";

        File fileDir = new File((realPathExcel + C_ResultExcelFloder + "/" + folderToken + "/" + folder));
        if (!fileDir.exists()) {
            fileDir.mkdirs();
            Lg.i("saveToAssets", "文件的路径不存在，创建:", fileDir.getPath());

        }

        String timeCreated = "-" + CommonUtils.getStringMonthAndDay();

        String pathBack = fileDir.getPath() + "/" + shopName + timeCreated + ".xls";
        FileOutputStream out = new FileOutputStream(pathBack);
        workbook.write(out);
        out.close();
        workbook.close();
        if (fileInputStreamIN != null) {
            fileInputStreamIN.close();
        }

        return errs;
    }


    //INVOICE > String直接返回错误信息，子文件路径不重要了，在zip里
    public List<String> writePI_Ttemplate(Map<String, List<AMZout>> dataMap, String shopName, String folderToken, Map<String, List<BoxRules>> boxRulesGroup, Map<String, List<ShenBaoInfo>> shenBaoInfoGroup, TEMPLATE_company isFurui, boolean isUS) throws IOException, InvalidFormatException {

        List<String> errs = new ArrayList<>();


        String srcTemplate = "excel/PI_template.xlsx";

        switch (isFurui) {
            case CM:
                srcTemplate = "excel/PI_template_cm.xlsx";
                break;
            case Furui:
                break;
            case Anbu:
                srcTemplate = "excel/PI_template_anbui.xlsx";
                break;
        }


        //合并一下重复sku项？
        List<AMZout> rAmZoutList = dataMap.values().stream()
                .flatMap(List::stream) // 将每个List<AMZout>展开为一个流
                .sorted(Comparator.comparing(o -> o.getSku()))
                .collect(Collectors.toList()); // 将流中的元素收集到一个新的List<AMZout>中
        int len = rAmZoutList.size();

        //总计
        AMZout allSummarize = new AMZout();
        rAmZoutList.forEach(new Consumer<AMZout>() {
            @Override
            public void accept(AMZout amZout) {
                List<BoxRules> rules = boxRulesGroup.get(amZout.getSku());
                if (rules != null && rules.size() > 0) {
                    amZout.setARTICLE(rules.get(0).getARTICLE());
                    amZout.setErpCode(rules.get(0).getErpCode());
                    amZout.setHS_CODE(rules.get(0).getHS_CODE());
                } else {
                    Lg.e("ErpCode编码缺失>>>>", amZout);
                    errs.add(amZout.getStock() + ">" + amZout.getSku() + ">Erp款号缺失");
                }

                //writePI
                //取出款号
                List<ShenBaoInfo> shenBaoInfos = getSkuShenbaoAll(amZout, shenBaoInfoGroup);
                if (shenBaoInfos != null && shenBaoInfos.size() > 0) {
                    if (isUS) {
//                       String usMoney = shenBaoInfos.get(0).getUsd().contains("")

                        try {
                            amZout.setPerMoney(String.valueOf(StringUtils.get2Numbers(shenBaoInfos.get(0).getUsd())));
                        } catch (NumberFormatException e) {
//                                e.printStackTrace();
                            amZout.setPerMoney("0");
                        }


                    } else {

                        try {
                            amZout.setPerMoney(String.valueOf(StringUtils.get2Numbers(shenBaoInfos.get(0).getRmb())));
                        } catch (NumberFormatException e) {
//                                e.printStackTrace();
                            amZout.setPerMoney("0");
                        }
                    }

                    amZout.setDescription(shenBaoInfos.get(0).getDescription());
                } else {
                    Lg.e("申报要素缺失>>>>", amZout);

                    errs.add(amZout.getStock() + ">" + amZout.getSku() + ">申报要素缺失");
                }

                allSummarize.setBoxes(allSummarize.getBoxes() + amZout.getBoxes());
                allSummarize.setShoes(allSummarize.getShoes() + amZout.getShoes());
                double allWeight = amZout.getWeight() * amZout.getBoxes();
                allSummarize.setWeight(allSummarize.getWeight() + allWeight);
                double vol = amZout.getLength() * amZout.getWidth() * amZout.getHeight() / 1000000 * amZout.getBoxes();
                allSummarize.setAllVolume(allSummarize.getAllVolume() + vol);
                //净重 （净重=总重量-2.5*箱数）
                allSummarize.setPureWeight(allSummarize.getPureWeight() + (allWeight - 2.5 * amZout.getBoxes()));

                //仓库与文件名
                allSummarize.setStock(amZout.getStock());
                allSummarize.setObName(amZout.getObName());
            }
        });
//        rAmZoutList.stream().

        List<AMZout> mergeSizesAMZ = new ArrayList<>();
        Map sizePos = ConstantFu.mapPackingList_size();
        rAmZoutList.forEach(new Consumer<AMZout>() {
            @Override
            public void accept(AMZout amZout) {
                //去掉尺码
//                int sepPos = amZout.getSku().lastIndexOf("-"); //获取分隔符的位置
//                String beforeSep = amZout.getSku().substring(0, sepPos); //获取分隔符前面的子字符串


                String sizePart = PoiUtiles.getSizeInSKUZ(amZout.getSku());


                AMZout amZout1 = new AMZout();
//                amZout1.setSku(beforeSep);
//                amZout1.setSku(amZout.getSku().split("-" + sizePart)[0]);

                if (sizePart != null) {
//                    amZout1.setSku(amZout.getSku().split("-" + sizePart)[0]);

                    String result = amZout.getSku().substring(0, amZout.getSku().length() - (sizePart.length() + 1)); // 去掉最后两个字符
                    amZout1.setSku(result);
                } else {
                    amZout1.setSku(amZout.getSku());
                }

//                AMZout amZout1 = new AMZout();
//                amZout1.setSku(beforeSep);
                amZout1.setShoes(amZout.getShoes());
                amZout1.setARTICLE(amZout.getARTICLE());
                amZout1.setHS_CODE(amZout.getHS_CODE());
                amZout1.setErpCode(amZout.getErpCode());
                amZout1.setBoxes(amZout.getBoxes());

                amZout1.setPerMoney(amZout.getPerMoney());
                amZout1.setDescription(amZout.getDescription());

                mergeSizesAMZ.add(amZout1);
            }
        });

        Map<String, AMZout> map_no_sizes = mergeSizesAMZ.stream().collect(Collectors.toMap(AMZout::getSku, item -> item, (oldValue, newValue) -> {
            oldValue.setShoes(oldValue.getShoes() + newValue.getShoes());
            oldValue.setBoxes(oldValue.getBoxes() + newValue.getBoxes());//新对象，总数
//            oldValue.getPosArray().putAll(newValue.getPosArray());//各个位置对应数量
            return oldValue;
        }));
        List<AMZout> res_no_sizes = map_no_sizes.values().stream().sorted(Comparator.comparing(o -> o.getSku())).collect(Collectors.toList());

        int start = 5;
        int lenALL = res_no_sizes.size();
        if (lenALL < 1) {
            errs.add(allSummarize.getObName() + "匹配仓库数据失败");
            return errs;
        }

        org.springframework.core.io.Resource resource = new ClassPathResource(srcTemplate);
//        HSSFWorkbook

        InputStream fileInputStreamIN = resource.getInputStream();
        XSSFWorkbook workbook = new XSSFWorkbook(fileInputStreamIN);
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(0);
        sheet.setPrintGridlines(true);


        sheet.shiftRows(start, sheet.getLastRowNum(), lenALL);

        XSSFCellStyle styleBaoguan = workbook.createCellStyle();
        // 设置水平居中
        styleBaoguan.setAlignment(HorizontalAlignment.CENTER);
        // 设置垂直居中
        styleBaoguan.setVerticalAlignment(VerticalAlignment.CENTER);
        styleBaoguan.setFillForegroundColor(IndexedColors.WHITE.index);
        styleBaoguan.setWrapText(true);


        XSSFCellStyle styleErr = workbook.createCellStyle();
        styleErr.cloneStyleFrom(styleBaoguan);
        styleErr.setFillForegroundColor(IndexedColors.RED.index);
        styleErr.setFillPattern(FillPatternType.SOLID_FOREGROUND);


        XSSFCellStyle styleBaoguanMoney = workbook.createCellStyle();
        styleBaoguanMoney.cloneStyleFrom(styleBaoguan);
        // 创建一个 XSSFDataFormat 对象
        XSSFDataFormat dataFormat = workbook.createDataFormat();
        if (isUS) {
// 设置单元格的数据格式为货币格式，这里使用了人民币符号
            styleBaoguanMoney.setDataFormat(dataFormat.getFormat("$#,##0.00"));
        } else {

// 设置单元格的数据格式为货币格式，这里使用了人民币符号
            styleBaoguanMoney.setDataFormat(dataFormat.getFormat("¥#,##0.00"));
        }

        //XSSF需要设置计算，HSSF不需要设置
        XSSFFormulaEvaluator formulaEvaluator =
                workbook.getCreationHelper().createFormulaEvaluator();
        for (int j = 0; j < lenALL; j++) {

            Row row = sheet.createRow(start + j);

            row.setHeightInPoints(40);

            //A~N
            int cols = 'G' - 'A';
            for (int k = 0; k < cols + 1; k++) {
                Cell shoes = row.createCell(k);
                AMZout amZout = res_no_sizes.get(j);

                if (shoes.getAddress().toString().contains("A")) {//序号
                    shoes.setCellValue(j + 1);
                }

                if (shoes.getAddress().toString().contains("C")) {//description
                    shoes.setCellValue(amZout.getDescription());

//                    sheet.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), 2, 3));
                }
                if (shoes.getAddress().toString().contains("D")) {//数量
                    shoes.setCellValue(amZout.getShoes());
                }

                if (shoes.getAddress().toString().contains("F")) {//总数
                    shoes.setCellValue(amZout.getShoes());
                }

                shoes.setCellStyle(styleBaoguan);
                if (shoes.getAddress().toString().contains("B")) {//SKU不带尺码
                    shoes.setCellValue(amZout.getSku());
                    if (StringUtils.isNullOrEmpty(amZout.getDescription())) {

                        shoes.setCellStyle(styleErr);
                    } else {
                        shoes.setCellStyle(styleBaoguan);
                    }

                }
                if (shoes.getAddress().toString().contains("E")) {//单价
                    if (StringUtils.isNumber(amZout.getPerMoney())) {
                        double money = StringUtils.get2Numbers(amZout.getPerMoney());
                        shoes.setCellValue(money);

                    }
                    shoes.setCellStyle(styleBaoguanMoney);
                }

                if (shoes.getAddress().toString().contains("G")) {//总价
                    shoes.setCellStyle(styleBaoguanMoney);
                    shoes.setCellFormula("SUM(" + ("E" + (row.getRowNum() + 1)) + "*" + ("F" + (row.getRowNum() + 1)) + ")");
//                    formulaEvaluator.evaluate(shoes);
                }

            }
        }

        int last = sheet.getLastRowNum();
        boolean t = true;
        int endRow = start;
        int footStart = start + lenALL;//稍微减少遍历次数
        //底部汇总
        for (int i = footStart; i < last; i++) {
            Row row = sheet.getRow(i);
            if (row != null) {
                Cell head = row.getCell(0);
                if (head != null && head.getStringCellValue().toUpperCase().contains("TOTAL")) {
                    endRow = i;
                }
            }
        }

        //sum 总计

        int lastSum = endRow - 1;
        Row row = sheet.getRow(lastSum);
        Cell sumShoes = row.getCell(5);//数量总计
        sumShoes.setCellFormula("SUM(" + "F6" + ":" + ("F" + lastSum) + ")");


        Cell sumMoneyShoes = row.getCell(6);//数量总计
        sumMoneyShoes.setCellFormula("SUM(" + "G6" + ":" + ("G" + lastSum) + ")");


        formulaEvaluator.evaluateAll();
        String folder = "报关单";

        File fileDir = new File((realPathExcel + C_ResultExcelFloder + "/" + folderToken + "/" + folder));
        if (!fileDir.exists()) {
            fileDir.mkdirs();
            Lg.i("saveToAssets", "文件的路径不存在，创建:", fileDir.getPath());

        }

        String timeCreated = "-" + CommonUtils.getStringMonthAndDay();

        String pathBack = fileDir.getPath() + "/" + shopName + timeCreated + ".xls";
        FileOutputStream out = new FileOutputStream(pathBack);
        workbook.write(out);
        out.close();
        workbook.close();
        if (fileInputStreamIN != null) {
            fileInputStreamIN.close();
        }

        return errs;
    }


    //报关单 > String直接返回错误信息，子文件路径不重要了，在zip里
    public List<String> writeBaoguandan_Ttemplate(Map<String, List<AMZout>> dataMap, String shopName, String folderToken, Map<String, List<BoxRules>> boxRulesGroup, Map<String, List<ShenBaoInfo>> shenBaoInfoGroup, TEMPLATE_company isFurui, boolean isUS) throws IOException, InvalidFormatException {

        List<String> errs = new ArrayList<>();
        String srcTemplate = "excel/报关单_template.xls";

        switch (isFurui) {
            case CM:
                srcTemplate = "excel/报关单_template_cm.xls";
                break;
            case Furui:
                break;
            case Anbu:
                srcTemplate = "excel/报关单_template_anbui.xls";
                break;
        }


//        String srcTemplate = "excel/报关单_template.xls";

        org.springframework.core.io.Resource resource = new ClassPathResource(srcTemplate);
//        HSSFWorkbook
        InputStream fileInputStreamIN = resource.getInputStream();
        HSSFWorkbook workbook = new HSSFWorkbook(fileInputStreamIN);
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(0);
        sheet.setPrintGridlines(true);
        //合并一下重复sku项？
        List<AMZout> rAmZoutList = dataMap.values().stream()
                .flatMap(List::stream) // 将每个List<AMZout>展开为一个流
                .sorted(Comparator.comparing(o -> o.getSku()))
                .collect(Collectors.toList()); // 将流中的元素收集到一个新的List<AMZout>中
        int len = rAmZoutList.size();

        //总计
        AMZout allSummarize = new AMZout();
        rAmZoutList.forEach(new Consumer<AMZout>() {
            @Override
            public void accept(AMZout amZout) {
                List<BoxRules> rules = boxRulesGroup.get(amZout.getSku());
                if (rules != null && rules.size() > 0) {
                    amZout.setARTICLE(rules.get(0).getARTICLE());
                    amZout.setErpCode(rules.get(0).getErpCode());
                    amZout.setHS_CODE(rules.get(0).getHS_CODE());
                } else {
                    Lg.e("ErpCode编码缺失>>>>", amZout);
                    errs.add(amZout.getStock() + ">" + amZout.getSku() + ">Erp款号缺失");
                }
                //  writeBaoguandan 报关单
                //截取-号后面的部分
//                String catalog = ty.substring(index + 1);//不全，缺斤少两
                //取出款号
                List<ShenBaoInfo> shenBaoInfos = getSkuShenbaoAll(amZout, shenBaoInfoGroup);
                if (shenBaoInfos != null && shenBaoInfos.size() > 0) {
                    if (isUS) {
//                       String usMoney = shenBaoInfos.get(0).getUsd().contains("")
//                        amZout.setPerMoney(String.valueOf(StringUtils.get2Numbers(shenBaoInfos.get(0).getUsd())));

                        try {
                            amZout.setPerMoney(String.valueOf(StringUtils.get2Numbers(shenBaoInfos.get(0).getUsd())));
                        } catch (NumberFormatException e) {
//                                e.printStackTrace();
                            amZout.setPerMoney("0");
                        }


                    } else {
//                        amZout.setPerMoney(shenBaoInfos.get(0).getRmb());

                        try {
                            amZout.setPerMoney(String.valueOf(StringUtils.get2Numbers(shenBaoInfos.get(0).getRmb())));
                        } catch (NumberFormatException e) {
//                                e.printStackTrace();
                            amZout.setPerMoney("0");
                        }
                    }
                    amZout.setShenbaoNumer(shenBaoInfos.get(0).getHsCode());
                    amZout.setShenbaoName(shenBaoInfos.get(0).getInfo());
                    amZout.setUnit(shenBaoInfos.get(0).getUnit());//单位


                } else {
                    Lg.e("申报要素缺失>>>>", amZout);

                    errs.add(amZout.getStock() + ">" + amZout.getSku() + ">申报要素缺失");
                }

                allSummarize.setBoxes(allSummarize.getBoxes() + amZout.getBoxes());
                allSummarize.setShoes(allSummarize.getShoes() + amZout.getShoes());
                double allWeight = amZout.getWeight() * amZout.getBoxes();
                allSummarize.setWeight(allSummarize.getWeight() + allWeight);
                double vol = amZout.getLength() * amZout.getWidth() * amZout.getHeight() / 1000000 * amZout.getBoxes();
                allSummarize.setAllVolume(allSummarize.getAllVolume() + vol);
                //净重 （净重=总重量-2.5*箱数）
                allSummarize.setPureWeight(allSummarize.getPureWeight() + (allWeight - 2.5 * amZout.getBoxes()));

            }
        });

        //款号 箱数 总数量 毛重 净重 体积
        List<AMZout> amZouts_model = new ArrayList<>();
        rAmZoutList.forEach(new Consumer<AMZout>() {
            @Override
            public void accept(AMZout amZout) {
                AMZout amZout1 = new AMZout();

                amZout1.setSku(getSkuModel(amZout.getSku()));
                amZout1.setBoxes(amZout.getBoxes());
                amZout1.setShoes(amZout.getShoes());
                double allWeight = amZout.getWeight() * amZout.getBoxes();
                amZout1.setWeight(allWeight);
                double vol = amZout.getLength() * amZout.getWidth() * amZout.getHeight() / 1000000 * amZout.getBoxes();
                amZout1.setAllVolume(vol);
                //净重 （净重=总重量-2.5*箱数）
                amZout1.setPureWeight(allWeight - 2.5 * amZout.getBoxes());


                amZout1.setShenbaoName(amZout.getShenbaoName());
                amZout1.setShenbaoNumer(amZout.getShenbaoNumer());
                amZout1.setPerMoney(amZout.getPerMoney());
                amZout1.setUnit(amZout.getUnit());

                amZouts_model.add(amZout1);

            }
        });

        Map<String, AMZout> map_model = amZouts_model.stream().collect(Collectors.toMap(AMZout::getSku, item -> item, (oldValue, newValue) -> {
            oldValue.setShoes(oldValue.getShoes() + newValue.getShoes());
            oldValue.setBoxes(oldValue.getBoxes() + newValue.getBoxes());
            oldValue.setWeight(oldValue.getWeight() + newValue.getWeight());
            oldValue.setPureWeight(oldValue.getPureWeight() + newValue.getPureWeight());
            oldValue.setAllVolume(oldValue.getAllVolume() + newValue.getAllVolume());
//            oldValue.setShoes(oldValue.getShoes() + newValue.getShoes());
            return oldValue;
        }));

        List<AMZout> res_no_sizes = map_model.values().stream().sorted(Comparator.comparing(o -> o.getSku())).collect(Collectors.toList());

        int start = 18;
        int lenALL = res_no_sizes.size();

        sheet.shiftRows(start, sheet.getLastRowNum(), lenALL);

        HSSFCellStyle styleBaoguan = workbook.createCellStyle();
        // 设置水平居中
        styleBaoguan.setAlignment(HorizontalAlignment.CENTER);
        // 设置垂直居中
        styleBaoguan.setVerticalAlignment(VerticalAlignment.CENTER);
        styleBaoguan.setFillForegroundColor(IndexedColors.WHITE.index);
        styleBaoguan.setWrapText(true);


        HSSFCellStyle styleErr = workbook.createCellStyle();
        styleErr.cloneStyleFrom(styleBaoguan);
        styleErr.setFillForegroundColor(IndexedColors.RED.index);
        styleErr.setFillPattern(FillPatternType.SOLID_FOREGROUND);


//        String C_title = "A5,A9";
//        List<String> C_titleM = Arrays.asList(C_title.split(","));
//
//        //B44 尾部不定！！
//        String company = isFurui ? "青岛福瑞安贸易有限公司(91370213MA3THCEYIE)" : "山东安步盈跨境电子商务有限公司(91370785MABPJ1Q360)";
//        C_titleM.forEach(new Consumer<String>() {
//            @Override
//            public void accept(String s) {
//                switch (s) {
//                    case "A5"://区分公司
//
//                        setCellValueByFilter(sheet, s, company);
//                        break;
//                    case "A9"://区分公司
//
//                        setCellValueByFilter(sheet, s, company);
//                        break;
//                    case "B44":
//
//                        setCellValueByFilter(sheet, s, company.split("\\(")[0]);
//                        break;
//                }
//            }
//        });


        //加上两个头
        for (int j = 0; j < lenALL; j++) {

            //创建一个单元格复制策略
//            CellCopyPolicy policy = new CellCopyPolicy();
////设置是否复制样式
//            policy.setCopyCellStyle(true);
////设置是否复制值
//            policy.setCopyCellValue(true);
////设置是否复制公式
//            policy.setCopyCellFormula(true);
////设置是否复制合并单元格
//            policy.setMergeHyperlink(true);
////复制第一行到第三行

            Row row = sheet.createRow(start + j);

            row.setHeightInPoints(50);

            //A~N
            int cols = 'N' - 'A';
            for (int k = 0; k < cols + 1; k++) {
                Cell shoes = row.createCell(k);
                AMZout amZout = res_no_sizes.get(j);

                if (shoes.getAddress().toString().contains("A")) {//序号
                    shoes.setCellValue(j + 1);
                }

                if (shoes.getAddress().toString().contains("C")) {//申报商品名称及规格型号 C、D合并
                    shoes.setCellValue(amZout.getShenbaoName());

                    sheet.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), 2, 3));
                }
                if (shoes.getAddress().toString().contains("E")) {//净重
                    shoes.setCellValue(amZout.getPureWeight());
                }
                if (shoes.getAddress().toString().contains("F")) {//数量
                    shoes.setCellValue(amZout.getShoes());
                }
                if (shoes.getAddress().toString().contains("G")) {//单位
                    shoes.setCellValue(amZout.getUnit());
                }
                if (shoes.getAddress().toString().contains("H")) {//单价
                    shoes.setCellValue(amZout.getPerMoney());
                }
                if (shoes.getAddress().toString().contains("I")) {//总价
//                    shoes.setCellValue("SUM(" + "H1*F1" + ")");
//                    shoes.setCellValue("SUM(" + "F1*H1" + ")");
//                    float sumIn = Float.parseFloat(amZout.getPerMoney());
//                    shoes.setCellValue(StringUtils.get2Numbers(String.valueOf(amZout.getShoes() * sumIn)));

                    shoes.setCellFormula("SUM(" + ("F" + (row.getRowNum() + 1)) + "*" + ("H" + (row.getRowNum() + 1)) + ")");
                }
                if (shoes.getAddress().toString().contains("J")) {//币制
                    shoes.setCellValue(isUS ? "美金" : "人民币");
                }
                if (shoes.getAddress().toString().contains("K")) {//原产国
                    shoes.setCellValue("中国");
                }
                if (shoes.getAddress().toString().contains("L")) {//空着
//                        shoes.setCellValue(amZout.getSku());
                }
                if (shoes.getAddress().toString().contains("M")) {//双数
                    shoes.setCellValue("(37079)潍坊其他");
                }
                if (shoes.getAddress().toString().contains("N")) {//双数
                    shoes.setCellValue("照章征税");
                }

                shoes.setCellStyle(styleBaoguan);

                if (shoes.getAddress().toString().contains("B")) {//申报商品编号
                    if (StringUtils.isNullOrEmpty(amZout.getShenbaoNumer())) {
                        shoes.setCellValue(amZout.getSku());
                        shoes.setCellStyle(styleErr);
                    } else {
                        shoes.setCellValue(amZout.getShenbaoNumer());
                        shoes.setCellStyle(styleBaoguan);
                    }

                }

            }
        }


        String folder = "报关单";

        File fileDir = new File((realPathExcel + C_ResultExcelFloder + "/" + folderToken + "/" + folder));
        if (!fileDir.exists()) {
            fileDir.mkdirs();
            Lg.i("saveToAssets", "文件的路径不存在，创建:", fileDir.getPath());

        }

        String timeCreated = "-" + CommonUtils.getStringMonthAndDay();

        String pathBack = fileDir.getPath() + "/" + shopName + timeCreated + ".xls";
        FileOutputStream out = new FileOutputStream(pathBack);
        workbook.write(out);
        out.close();
        workbook.close();
        if (fileInputStreamIN != null) {
            fileInputStreamIN.close();
        }
        return errs;
    }


    private void setCellValueByFilter(org.apache.poi.ss.usermodel.Sheet sheet, String address, String value) {
        CellReference cell_stock = new CellReference(address);
        // 获取行索引
        int row = cell_stock.getRow();
        // 获取列索引
        int col = cell_stock.getCol();
        // 获取指定的Row对象
        Row r = sheet.getRow(row);
        // 获取指定的Cell对象
        Cell cell = r.getCell(col);
        cell.setCellValue(value);
    }


}
