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
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.IOUtils;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openxmlformats.schemas.drawingml.x2006.main.CTPoint2D;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.magicair.webpj.AFurui.ConstantFu.C_BaseStockInfoFloder;
import static com.magicair.webpj.AFurui.ConstantFu.C_ResultExcelFloder;
import static com.magicair.webpj.AFurui.PoiUtiles.findItemInNames;

/* 托书生成
 *功能描述
 * @author lch
 * @date 2023/10/24
 * @param  * @param null
 * @return
 */
@Service
@Transactional
public class FuruiAMZcsvTrustDeedServiceImpl {

    @Value("${web.excel-path}")
    private String realPathExcel;


    //    private static List<TreeMap> dataMapsCache;
//    private  List<AMZout> allRes = new ArrayList<>();

    private String outDate;

    //M-获取最新仓库数据
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
        File[] files = fileDir.listFiles((dir, name) -> (name.endsWith(".xls") || name.endsWith(".xlsx")));
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

    public Result excelActionMergeAllCSVFile(String folderToken, List<File> inputs,
                                             WrapListWithMsg<CargoInfo> cargoWraps,
                                             Map<String, List<ShenBaoInfo>> shenBaoGroup,
                                             Map<String, List<BoxRules>> boxRulesGroup,
                                             String outName) {


        if (cargoWraps.getErrMsg().length() > 3) {
//            return resultsErr.get(0);//暂返回第一个错误
            Result resultAll = ResultGenerator.genFailResult("错误集合");
            resultAll.setData(cargoWraps.getErrMsg());
            return resultAll;
        }

        Map<String, List<CargoInfo>> referenceIDsMap = cargoWraps.getListData().stream().collect(Collectors.groupingBy(CargoInfo::getFBA));//按仓库

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
                        allRes.addAll((List<AMZout>) result.getData());
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
        Lg.i(">>>", "初始所有项" + allRes.size());

        Map resDepotMap = allRes.stream().collect(Collectors.groupingBy(AMZout::getStock));//按仓库

//        使用TreeMap对resDepotMap按key值升序排序
        TreeMap<String, List<AMZout>> sortedResMap = new TreeMap<>(resDepotMap);//这儿的key是sheet名了

        File[] files = getUploads(C_BaseStockInfoFloder);
        if (files == null) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("path", null);
            jsonObject.put("msg", "仓库信息缺失");
//            return ResultGenerator.genSuccessResult(jsonObject);
            return ResultGenerator.genFailResult("仓库信息缺失");
        }
        WrapListWithMsg<StockInfos> stockInfosListFast = null;
        try {
            stockInfosListFast = PoiUtiles.getStockInfosListFast(files[0]);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (stockInfosListFast == null) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("path", null);
            jsonObject.put("msg", "读取仓库信息失败");
            return ResultGenerator.genSuccessResult(jsonObject);
        }
        Map<String, List<StockInfos>> stockMap = stockInfosListFast.getListData().stream().collect(Collectors.groupingBy(StockInfos::getStockName));//按仓库


        List<String> resMsgs = new ArrayList<>();
        Set<String> tabStrs = sortedResMap.keySet();
        tabStrs.forEach(new Consumer<String>() {
            @Override
            public void accept(String s) {
                Lg.i("开始构建！！！！>>>", s);
                try {
                    List<String> warningsAndErrs = writeExcelToTemplate(sortedResMap, s, folderToken, stockMap, referenceIDsMap, shenBaoGroup, boxRulesGroup);
                    resMsgs.addAll(warningsAndErrs);
                } catch (IOException | InvalidFormatException e) {
                    e.printStackTrace();
                }

            }
        });


        long t2 = System.currentTimeMillis() - t1;
        JSONObject jsonObject = new JSONObject();
        Collections.reverse(resMsgs);
        List<String> errs = resMsgs.stream().filter(new Predicate<String>() {
            @Override
            public boolean test(String s) {
                if (s.contains("文件")) {//数据不一致错误
                    return true;
                }
                return false;
            }
        }).collect(Collectors.toList());
        if (errs.size() > 0) {
            jsonObject.put("path", null);
            jsonObject.put("msg", errs);
            return ResultGenerator.genSuccessResult(jsonObject);
        }

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
        if (!resMsgs.isEmpty()) {
            jsonObject.put("msg", resMsgs);
        }
        jsonObject.put("path", backPath);
        jsonObject.put("time_c", t2);
        boolean t = true;
        return ResultGenerator.genSuccessResult(jsonObject);

    }

    public List<String> writeExcelToTemplate(TreeMap<String, List<AMZout>> dataMap, String stockName,
                                             String folderToken, Map<String, List<StockInfos>> stockMap,
                                             Map<String, List<CargoInfo>> referenceIDsMap,
                                             Map<String, List<ShenBaoInfo>> shenBaoGroup,
                                             Map<String, List<BoxRules>> boxRulesGroup
    ) throws IOException, InvalidFormatException {
//        Set<String> strings = dataMap.keySet();

        List<String> errs = new ArrayList<>();

        List<AMZout> resList = dataMap.get(stockName);
        if (resList.size() < 1) {
            return null;
        }
        String srcTemplate = "excel/FBA_template.xlsx";

        org.springframework.core.io.Resource resource = new ClassPathResource(srcTemplate);
//        File file = resource.getFile();
        InputStream fileInputStreamIN = resource.getInputStream();

        //bug 直接用File 会在原表上累加！！！！，爆内存！！！
//        XSSFWorkbook workbook = new XSSFWorkbook(file);
        XSSFWorkbook workbook = new XSSFWorkbook(fileInputStreamIN);//分离出来方便后面关闭！！
//        XSSFWorkbook workbook = new XSSFWorkbook(resource.getInputStream());
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(0); // 获取第1个工作表
//        org.apache.poi.ss.usermodel.Sheet sheet = workbook.cloneSheet(0);

        String fileName = resList.get(0).getObName();
        List<String> nameTags = findItemInNames(fileName);

        resList.forEach(new Consumer<AMZout>() {
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
//                String catalog = PoiUtiles.removeDotPart(amZout.getErpCode());//ERP code 比较对应

//                List<ShenBaoInfo> shenBaoInfos = shenBaoGroup.get(catalog.toUpperCase());

                List<ShenBaoInfo> shenBaoInfos = PoiUtiles.getSkuShenbaoAll(amZout, shenBaoGroup);

                if (shenBaoInfos != null && shenBaoInfos.size() > 0) {
                    ShenBaoInfo shenBaoInfo = shenBaoInfos.get(0);

                    amZout.setMaterial(shenBaoInfo.getMaterial());
                    amZout.setFactory(shenBaoInfo.getFactory());
                    amZout.setKgs(shenBaoInfo.getKgs());
                    amZout.setUsd(shenBaoInfo.getUsd());
                    amZout.setEnName(shenBaoInfo.getEnName());
                    amZout.setTicketName(shenBaoInfo.getTicketName());//开票名>中文品名
                    amZout.setPicData(shenBaoInfo.getPicData());
                    amZout.setBrand(shenBaoInfo.getBrand());
                    amZout.setPurpose(shenBaoInfo.getPurpose());

                    if (StringUtils.isNullOrEmpty(amZout.getHS_CODE()) && !StringUtils.isNullOrEmpty(shenBaoInfo.getHsCode())) {

                        amZout.setHS_CODE(shenBaoInfo.getHsCode());
                    }
                }

                //商品编号：>>>有的会是 "商品编码：" >>>具体显示时分割
//                String head = "商品编号：";
//                if (!StringUtils.isNullOrEmpty(amZout.getHS_CODE())) {
//                    amZout.setHS_CODE(amZout.getHS_CODE().replaceAll(head, ""));
//                }

            }
        });


        //再按FBA编号编组
        Map resDepotMap = resList.stream().collect(Collectors.groupingBy(AMZout::getObNumber));//按编号


        //--------------仓库信息
        //仓库 M8 City城市名: H7 Province州省:M7 Country国家/区域: H5 PostalCode邮编: M5
        //Address地址: H6
        if (stockMap.get(stockName) != null) {
            StockInfos stockInfos = stockMap.get(stockName).get(0);
            String C_title = "M8,H7,M7,H5,M5,H6";
            List<String> C_titleM = Arrays.asList(C_title.split(","));
            C_titleM.forEach(new Consumer<String>() {
                @Override
                public void accept(String s) {
                    switch (s) {
                        case "M8":
                            setCellValueByFilter(sheet, s, stockInfos.getStockName());
                            break;
                        case "H7":
                            setCellValueByFilter(sheet, s, stockInfos.getCity());
                            break;
                        case "M7":
                            setCellValueByFilter(sheet, s, stockInfos.getProvince());
                            break;
                        case "H5":
                            setCellValueByFilter(sheet, s, stockInfos.getCountry());
                            break;
                        case "M5":
                            setCellValueByFilter(sheet, s, stockInfos.getPostalCode());
                            break;
                        case "H6":
                            setCellValueByFilter(sheet, s, stockInfos.getAddress());
                            break;
                    }

                }
            });
        } else {
            errs.add("仓库信息表：" + stockName + "仓库信息缺失");
            boolean ff = true;
        }


        Object[] keys = resDepotMap.keySet().toArray();
        int lenRows = keys.length;

        // 创建一个XSSFCellStyle对象
        XSSFCellStyle style = workbook.createCellStyle();
        // 设置四周的边框样式为细线
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);

        style.setAlignment(HorizontalAlignment.CENTER);
        // 设置垂直对齐的样式为居中对齐
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        int totalBoxes = 0;
        for (int i = 0; i < lenRows; i++) {
            Row newRow = sheet.createRow(11 + i);


            List<AMZout> resListP1 = (List<AMZout>) resDepotMap.get(keys[i]);

            //使用HashSet来存储list中的元素，key为box和total的组合
            Set<String> set = new HashSet<>();
//创建一个新的List来存放不同的元素
            List<AMZout> result = new ArrayList<>();
            List<AMZout> resultIsHasSame = new ArrayList<>();
//遍历list，判断是否存在重复的元素
            for (AMZout amz : resListP1) {
                String key = amz.getSku() + amz.getBoxes() + "-" + amz.getTotalAll();
                if (set.contains(key)) {
                    //如果存在，则说明是相同的元素，跳过
                    resultIsHasSame.add(amz);
                    continue;
                } else {
                    //如果不存在，则说明是不同的元素，添加到result中，并将key加入到set中
                    result.add(amz);
                    set.add(key);
                }
            }
            if (resultIsHasSame.size() > 1) {

                errs.add("文件" + fileName + "箱数和商品总数不一致");
                return errs;
            }
            totalBoxes = result.get(0).getBoxes() + totalBoxes;

            // 设置行高为20点
            newRow.setHeightInPoints((short) 80);//大一点要放图片？

            AMZout cellAmZout = result.get(0);
            //FBA编号	Reference ID  箱数 数量（PCS）
            //0          1             3  8
            for (int j = 0; j < 15; j++) {
                Cell cell0 = newRow.createCell(j);
                cell0.setCellStyle(style);
                if (j == 0) {
                    newRow.getCell(j).setCellValue((String) keys[i]);
                } else if (j == 1) {
                    List<CargoInfo> obCargos = referenceIDsMap.get(String.valueOf(keys[i]));
                    if (obCargos == null || obCargos.size() < 1) {

                    } else {
                        CargoInfo cargoInfo = obCargos.get(0);
                        newRow.getCell(j).setCellValue(cargoInfo.getCargoNum());
                    }


                } else if (j == 3) {//箱数
                    newRow.getCell(j).setCellValue(cellAmZout.getBoxes());
                } else if (j == 4) {//毛重
                    newRow.getCell(j).setCellValue(cellAmZout.getKgs());
                } else if (j == 5) {//HS code
                    String hsCode = cellAmZout.getHS_CODE();
                    if (hsCode != null) {
                        //中英文 ： : 不同
                        String[] hs = hsCode.split("：|:");
                        newRow.getCell(j).setCellValue(hs[hs.length - 1]);
                    }

                } else if (j == 6) {//中文品名
                    newRow.getCell(j).setCellValue(cellAmZout.getTicketName());
                } else if (j == 7) {//英文品名
                    newRow.getCell(j).setCellValue(cellAmZout.getEnName());
                } else if (j == 8) {//双数
                    newRow.getCell(j).setCellValue(cellAmZout.getTotalAll());
                } else if (j == 9) {//单价 (USD)
                    if (cellAmZout.getUsd() != null) {
//                        newRow.getCell(j).setCellValue(StringUtils.get2Numbers(cellAmZout.getUsd()));

                        if (StringUtils.isNumericAll(cellAmZout.getUsd())) {
                            newRow.getCell(j).setCellValue(StringUtils.get2Numbers(cellAmZout.getUsd()));
                        } else {
//                            amZout1.setPerMoney("0");
                        }

                    }
                } else if (j == 10) {//品牌
                    newRow.getCell(j).setCellValue(cellAmZout.getBrand());
                } else if (j == 12) {//材质
                    newRow.getCell(j).setCellValue(cellAmZout.getMaterial());
                } else if (j == 13) {//用途
                    newRow.getCell(j).setCellValue(cellAmZout.getPurpose());
                } else if (j == 14) {

                    //TO DO 插入图片，差源头
                    setImgToCell(sheet, workbook, newRow.getRowNum(), newRow.getCell(j), cellAmZout.getPicData());

//                    newRow.getCell(j).setCellValue(result.get(0).getTotalAll());
                }
            }
        }


        File fileDir = new File((realPathExcel + C_ResultExcelFloder + "/" + folderToken));
        if (!fileDir.exists()) {
            fileDir.mkdirs();
            Lg.i("saveToAssets", "文件的路径不存在，创建:", fileDir.getPath());

        }

        String timeCreated = "-" + CommonUtils.getStringMonthAndDay();
        //FBA_template_美森 MDW2-9
        String finalName = "XX";
        if (nameTags.size() < 3) {

            errs.add(fileName + "货件名称格式错误");
            finalName = "FBA_template_" + "渠道缺失" + "_" + stockName + "-" + totalBoxes;
        } else {
            finalName = "FBA_template_" + nameTags.get(2) + "_" + stockName + "-" + totalBoxes;
        }

        String pathBack = fileDir.getPath() + "/" + finalName + ".xlsx";
        FileOutputStream out = new FileOutputStream(pathBack);
        fileInputStreamIN.close();
        workbook.write(out);
        out.close();
        workbook.close();

        return errs;
    }


    private void setImgToCell(Sheet sheet, Workbook workbook, int row, Cell cell, PictureData pictureData) {
        if (pictureData == null || pictureData.getData() == null) {
            return;
        }
        // 创建一个画图管理器对象
        XSSFDrawing drawing = (XSSFDrawing) sheet.createDrawingPatriarch();
// 读取图片文件，转换为字节数组
        byte[] bytes = new byte[0];
//            String path = "excel/img.png";
//            org.springframework.core.io.Resource resource = new ClassPathResource(path);
//            bytes = IOUtils.toByteArray(resource.getInputStream());

        bytes = pictureData.getData();

// 向工作簿中添加图片，返回图片索引
//            int pictureIdx = workbook.addPicture(bytes, Workbook.PICTURE_TYPE_JPEG);
//        int pictureIdx = workbook.addPicture(bytes, Workbook.PICTURE_TYPE_PNG);
        int pictureIdx = workbook.addPicture(bytes, pictureData.getPictureType());
// 创建一个锚点对象，指定图片插入的坐标，需要计算图片的偏移量和缩放比例


//        double standardWidth = 1.69; // 5厘米
//        double standardHeight = 0.94; // 2厘米
//// 调用cmToPx方法，将厘米转换为像素
//        int pxWidth = cmToPx(standardWidth);
//        int pxHeight = cmToPx(standardHeight);
//// 计算单元格的长宽，单位是像素
//        double cellWidth = sheet.getColumnWidthInPixels(cell.getColumnIndex());
//        double cellHeight = cell.getRow().getHeightInPoints() / 72 * 96;
//// 计算缩放比例，根据目标图片尺寸和单元格尺寸
//        double scaleX = pxWidth / cellWidth;
//        double scaleY = pxHeight / cellHeight;
//// 调用resize方法，传入缩放比例
////            picture.resize(scaleX, scaleY);
//
//// 计算图片的左上角和右下角的偏移量，使图片居中
//        int dx1 = (int) ((cellWidth - pxWidth) / 2);
//        int dy1 = (int) ((cellHeight - pxHeight) / 2);
//        int dx2 = dx1 + pxWidth;
//        int dy2 = dy1 + pxHeight;
//
//
//        double offsetX = (cellWidth - standardWidth) / 2;
//        double offsetY = (cellHeight - standardHeight) / 2;

        // O 列插入图片
//        XSSFClientAnchor anchor = new XSSFClientAnchor(dx1, dy1, dx2, dy2, 14, row, 15, row + 1);
        XSSFClientAnchor anchor = new XSSFClientAnchor(0, 0, 0, 0, 14, row, 15, row + 1);
//            XSSFClientAnchor anchor = new XSSFClientAnchor(0, 0, 0, 0, 14, row, 14, row);
// 设置锚点类型为移动和调整大小
        anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_AND_RESIZE);

        // 使用POI CTPoint2D类来设置锚点对象的参数
//            CTPoint2D from = anchor.getPosition();
//            from.setX((long) offsetX);
//            from.setY((long) offsetY);
//            CTPoint2D to = anchor.get();
//            from.setX((long) (offsetX + standardWidth));
//            from.setY((long) (offsetY + standardHeight));

        // 指定我想要的长宽，单位是厘米
//        double standardWidth = 5.0; // 5厘米
//        double standardHeight = 2.0; // 2厘米

// 设置锚点对象的偏移量
//            anchor.setDx1(dx1);
//            anchor.setDy1(dy1);
//            anchor.setDx2(dx2);
//            anchor.setDy2(dy2);


// 使用画图管理器创建一个图片对象，并传入锚点和图片索引
        Picture picture = drawing.createPicture(anchor, pictureIdx);
//        picture.resize(scaleX, scaleY);

    }

    public static void setImgSize(Picture picture, Sheet sheet, Cell cell) {

        // 指定我想要的长宽，单位是厘米
//        double standardWidth = 5.0; // 5厘米
//        double standardHeight = 2.0; // 2厘米
        double standardWidth = 1.69; // 5厘米
        double standardHeight = 0.94; // 2厘米
// 调用cmToPx方法，将厘米转换为像素
        int pxWidth = cmToPx(standardWidth);
        int pxHeight = cmToPx(standardHeight);
// 计算单元格的长宽，单位是像素
        double cellWidth = sheet.getColumnWidthInPixels(cell.getColumnIndex());
        double cellHeight = cell.getRow().getHeightInPoints() / 72 * 96;
// 计算缩放比例，根据目标图片尺寸和单元格尺寸
        double scaleX = pxWidth / cellWidth;
        double scaleY = pxHeight / cellHeight;
// 调用resize方法，传入缩放比例
        picture.resize(scaleX, scaleY);

    }


    // 定义一个厘米转换为像素的方法
    public static int cmToPx(double cm) {
        // 假设屏幕分辨率为96 dpi，即每英寸有96个像素点
        int dpi = 96;
        // 1厘米等于0.3937007874英寸
        double inch = cm * 0.3937007874;
        // 将英寸乘以分辨率，得到像素值，四舍五入取整
        int px = (int) Math.round(inch * dpi);
        return px;
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

    public Result inputConvertAllList(File inputFile) {

        List<String> resMsg = new ArrayList<>();//异常信息

        List<AMZout> treeParser = new ArrayList<>();
//        Path filePath = Paths.get(path);
        String fileName = inputFile.getName();
        String stock = fileName.split("-")[0];
        stock = stock.substring(stock.indexOf("～") + 1);//保存服务端后有拼接!!是中文的～！！！！


        String filterName = fileName.substring(fileName.indexOf("～") + 1);
        List<String> tagList = findItemInNames(filterName);

        if (tagList.size() < 3) {
            resMsg.add("文件:" + filterName + "名称格式错误");
            return ResultGenerator.genFailResult("文件名格式错误", resMsg);
        }


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

            treeParser = readCsvByLines(fr, stock, fileName);

            String finalStock = stock;
            treeParser.forEach(new Consumer<AMZout>() {
                @Override
                public void accept(AMZout amZout) {
                    amZout.setStock(finalStock);
                }
            });

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

    private List<AMZout> readCsvByLines(FileReader fr, String stock, String fileName) {
        CSVReader reader = new CSVReaderBuilder(fr).withCSVParser(new RFC4180Parser()).build();
        List<AMZout> treeParser = new ArrayList<>();
        AMZout tmpTop = new AMZout();
        AMZout zoutFeeInfo = null;

//            List<AMZoutBox> amZoutBoxesTmp = new ArrayList<>();
        boolean isCm = false;
        int isPJ = -1;
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
                    tmpTop.setObName(nextLine[1]);//取里面的！！
//                    tmpTop.setObName(fileName);
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
//                    Stream<AMZoutBox> stream = Stream.generate(AMZoutBox::new).limit(num);
                    // 收集流中的元素到一个列表中
//                    amZoutBoxesTmp = stream.collect(Collectors.toList());
                    // 现在你可以对 list 进行增删改操作了
                    continue;
                } else if (toStr.contains("SKU 数量")) {
//                    tmpTop.setSkuNum(Integer.parseInt(nextLine[1]));
                    continue;
                } else if (toStr.startsWith("商品数量")) {//拼箱后面有一行还有"商品数量"字样
                    tmpTop.setTotalAll(Integer.parseInt(nextLine[1]));
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
                    if (!StringUtils.isNullOrEmpty(nextLine[0])) {
                        zoutFeeInfo = new AMZout();
                        zoutFeeInfo.setSku(nextLine[0]);
                        zoutFeeInfo.setObName(tmpTop.getObName());
                        zoutFeeInfo.setObNumber(tmpTop.getObNumber());
                        zoutFeeInfo.setAddress(tmpTop.getAddress());
                        zoutFeeInfo.setBoxes(tmpTop.getBoxes());
                        zoutFeeInfo.setStock(stock);
                        zoutFeeInfo.setIsPJ("true");
                        zoutFeeInfo.setTotalAll(tmpTop.getTotalAll());
                        treeParser.add(zoutFeeInfo);
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
                    zoutFeeInfo.setTotalAll(tmpTop.getTotalAll());


                    // 9(重量) 10 L 11 W 12 H


                    zoutFeeInfo.setPer(Integer.parseInt(nextLine[13]));
                    zoutFeeInfo.setShoes(Integer.parseInt(nextLine[15]));
                    //直接总箱子数量
//                    zoutFeeInfo.setBoxes(Integer.parseInt(nextLine[14]));

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
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return treeParser;
    }


    //过滤"货件信息表"
    public Map<String, List<File>> filterByForLoop(List<File> inputFiles, String keyword) {
        Map<String, List<File>> map = new HashMap<>();
        // 创建两个List<File>用于存储过滤后的结果
        List<File> containKeyword = new ArrayList<>(); // 存储文件名包含关键字的File对象
        List<File> notContainKeyword = new ArrayList<>(); // 存储文件名不包含关键字的File对象
        // 遍历输入的List<File>
        for (File file : inputFiles) {
            // 获取文件名
            String fileName = file.getName();
            // 判断文件名是否包含关键字
            if (fileName.contains(keyword)) {
                // 如果包含，就添加到containKeyword列表中
                containKeyword.add(file);
            } else {
                // 如果不包含，就添加到notContainKeyword列表中
                notContainKeyword.add(file);
            }
        }
        map.put("info", containKeyword);
        map.put("data", notContainKeyword);
        // 打印过滤后的结果
        System.out.println("文件名包含" + keyword + "的文件有：");

        return map;
    }

}
