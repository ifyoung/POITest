package com.magicair.webpj.AFurui;


import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.magicair.webpj.AFurui.model.SKUModel;
import com.magicair.webpj.AFurui.model.wrap.WrapListWithMsg;
import com.magicair.webpj.utils.UniCloudBridgeFu;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Sheet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Service
@Transactional
public class SkuServiceImpl {


    public JSONArray upSkuToUni(InputStream skuPropertyFile) {

//     String stockName = "/Users/lch/Documents/Documents/Furui/数据包10.10/SKU+订单号命名规范索引-0920.xlsx";

//     WrapListWithMsg<SKUModel> modelWrapListWithMsg = getSKUModelFromFile(new File(stockName));

        WrapListWithMsg<SKUModel> modelWrapListWithMsg = null;
        try {
            modelWrapListWithMsg = getSKUModelFromFile(skuPropertyFile);
        } catch (IOException e) {
//         e.printStackTrace();
            return null;
        }

        //  String str = JSON.toJSONString(modelWrapListWithMsg.getListData());
        // action>add search  sqlBase> fu-beihuo-info
        JSONArray list = UniCloudBridgeFu.bridgefuAddBeihuoTO_uni("add", "fu-sku-model", modelWrapListWithMsg.getListData());
        return list;
    }

    //M-sku初始属性读取
    public static WrapListWithMsg<SKUModel> getSKUModelFromFile(InputStream fileIn) throws IOException {

        ReadableWorkbook wb = new ReadableWorkbook(fileIn);

        WrapListWithMsg<SKUModel> msgBoxes = new WrapListWithMsg<>();
        List<SKUModel> boxDetails = new ArrayList<>();
        List<String> errStrs = new ArrayList<>();
        msgBoxes.setListData(boxDetails);//放前面放后面一样？地址指向？ List<String> 不行？
        Stream<Sheet> sheets = wb.getSheets(); //获取Workbook中sheet的个数

        boolean tt1 = true;
        sheets.forEach(sheet -> {
            String name = sheet.getName(); //获取每个sheet的名称
            if (name.contains("不发") || name.contains("数据源")) {//过滤表

            } else {
                org.dhatim.fastexcel.reader.SheetVisibility sheetVisibility = sheet.getVisibility(); //获取每个sheet的可见性
                if (sheetVisibility == org.dhatim.fastexcel.reader.SheetVisibility.VISIBLE && name.toUpperCase().contains("SKU")) {
                    try { // Get a stream of rows from the sheet
                        List<org.dhatim.fastexcel.reader.Row> rr = sheet.read();
                        if (!rr.isEmpty()) {
                            int size = rr.size();
                            //排除首行    站点  店铺  款号  颜色 extra  尺码
                            // 第6行开始  D E  G H  J K   N O P       Q
                            for (int i = 6; i < size; i++) {
                                org.dhatim.fastexcel.reader.Row row = rr.get(i);

//                                details.setSheetName(name);
                                //底部空一大片又突然出现一小格的情况,或者隐藏行
                                if (row == null || row.getCellCount() < 1) {
                                    boolean tt = true;
                                    continue;
                                }

                                final SKUModel[] detailsZD = {null};//站点
                                final SKUModel[] detailsDP = {null};//店铺
                                final SKUModel[] detailsKS = {null};//款式
                                final SKUModel[] detailsYS = {null};//颜色
                                row.stream().forEach(cell -> {
                                    if (cell == null) {
                                        return;
                                    }
                                    Object cellData = cell.getValue();
                                    String address = String.valueOf(cell.getAddress());

                                    //站点
                                    if (address.startsWith("D")) {//一行开始
                                        if (cellData instanceof String) {
                                            String item = (String) cellData;

                                            detailsZD[0] = new SKUModel();
                                            detailsZD[0].setGroup("ZD");
                                            detailsZD[0].setName(item);

                                        } else {
//                                            errStrs.add(name + ">" + address + ":类型错误");
                                        }

                                    } else if (address.startsWith("E")) {
                                        if (cellData instanceof String) {
                                            String sku = (String) cellData;
                                            detailsZD[0].setCode(sku);
                                        } else {
//                                            errStrs.add(name + ">" + address + ":类型错误");
                                        }

                                    } //店铺
                                    else if (address.startsWith("G")) {
                                        if (cellData instanceof String) {
                                            detailsDP[0] = new SKUModel();
                                            detailsDP[0].setGroup("DP");
                                            String sku = (String) cellData;
                                            detailsDP[0].setName(sku);
                                        } else {
//                                            errStrs.add(name + ">" + address + ":类型错误");
                                        }

                                    } else if (address.startsWith("H")) {
                                        if (cellData instanceof String) {
                                            String sku = (String) cellData;
                                            detailsDP[0].setCode(sku);
                                        } else {
//                                            errStrs.add(name + ">" + address + ":类型错误");
                                        }
                                    } //款号
                                    else if (address.startsWith("J")) {
                                        String sku = String.valueOf(cellData);

                                        detailsKS[0] = new SKUModel();
                                        detailsKS[0].setGroup("KS");

                                        detailsKS[0].setName(sku);

                                    } else if (address.startsWith("K")) {//
                                        String sku = String.valueOf(cellData);
                                        detailsKS[0].setCode(sku);

                                    } //颜色
                                    else if (address.startsWith("N")) {
                                        if (cellData instanceof String) {
                                            String sku = (String) cellData;

                                            detailsYS[0] = new SKUModel();
                                            detailsYS[0].setGroup("YS");

                                            detailsYS[0].setName(sku);
                                        } else {
//                                            errStrs.add(name + ">" + address + ":类型错误");
                                        }

                                    } else if (address.startsWith("O")) {
                                        if (cellData instanceof String) {
                                            String sku = (String) cellData;
                                            detailsYS[0].setCode(sku);
                                        } else {
//                                            errStrs.add(name + ">" + address + ":类型错误");
                                        }

                                    } else if (address.startsWith("P")) {
                                        if (cellData instanceof String) {
                                            String sku = (String) cellData;
                                            detailsYS[0].setExtra(sku);
                                        } else {
//                                            errStrs.add(name + ">" + address + ":类型错误");
                                        }

                                    }
                                    if (detailsZD[0] != null && !boxDetails.contains(detailsZD[0])) {
                                        boxDetails.add(detailsZD[0]);
                                    }
                                    if (detailsDP[0] != null && !boxDetails.contains(detailsDP[0])) {
                                        boxDetails.add(detailsDP[0]);
                                    }
                                    if (detailsKS[0] != null && !boxDetails.contains(detailsKS[0])) {
                                        boxDetails.add(detailsKS[0]);
                                    }
                                    if (detailsYS[0] != null && !boxDetails.contains(detailsYS[0])) {
                                        boxDetails.add(detailsYS[0]);
                                    }

                                });

                            }
                        }


                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
                //尺码表读取
                if (sheetVisibility == org.dhatim.fastexcel.reader.SheetVisibility.VISIBLE && name.contains("尺码")) {
                    try { // Get a stream of rows from the sheet
                        List<org.dhatim.fastexcel.reader.Row> rr = sheet.read();
                        if (!rr.isEmpty()) {
                            int size = rr.size();
                            //排除首行    站点  店铺  款号  颜色 extra  尺码
                            // 第6行开始  D E  G H  J K   N O P       Q

                            //美码（通用）美码（男鞋）美码（女鞋）美码（童码）欧码（童码）日本（童码）欧码（通用）身高（CM）服装 无尺码（00占位）
                            //SAU        SAM      SAW       SAC       SEC       SJC       SEU       SH      SC   SNO
                            //ABCDEFGHI

                            for (int i = 1; i < size; i++) {
                                org.dhatim.fastexcel.reader.Row row = rr.get(i);

//                                details.setSheetName(name);
                                //底部空一大片又突然出现一小格的情况,或者隐藏行
                                if (row == null || row.getCellCount() < 1) {
                                    boolean tt = true;
                                    continue;
                                }

//                                final SKUModel[] detailsSize = {null};//站点
                                row.stream().forEach(cell -> {
                                    if (cell == null) {
                                        return;
                                    }
                                    Object cellData = cell.getValue();
                                    String address = String.valueOf(cell.getAddress());


                                    //美码（通用） SAU
                                    if (address.startsWith("A")) {//一行开始
                                        if (cellData instanceof String || cellData instanceof Number) {
                                            String item = String.valueOf(cellData);

                                            SKUModel detailsZD = new SKUModel();
                                            detailsZD.setGroup("SAU");
                                            detailsZD.setName("美码（通用）");
                                            detailsZD.setCode(item);

                                            if (!boxDetails.contains(detailsZD)) {
                                                boxDetails.add(detailsZD);
                                            }

                                        } else {
//                                            errStrs.add(name + ">" + address + ":类型错误");
                                        }

                                    } //美码（男鞋）
                                    else if (address.startsWith("B")) {//一行开始
                                        if (cellData instanceof String || cellData instanceof Number) {
                                            String item = String.valueOf(cellData);
                                            SKUModel detailsZD = new SKUModel();
                                            detailsZD.setGroup("SAM");
                                            detailsZD.setName("美码（男鞋）");
                                            detailsZD.setCode(item);

                                            if (!boxDetails.contains(detailsZD)) {
                                                boxDetails.add(detailsZD);
                                            }
                                        } else {
//                                            errStrs.add(name + ">" + address + ":类型错误");
                                        }

                                    } //美码（女鞋）
                                    else if (address.startsWith("C")) {//一行开始
                                        if (cellData instanceof String || cellData instanceof Number) {
                                            String item = String.valueOf(cellData);
                                            SKUModel detailsZD = new SKUModel();
                                            detailsZD.setGroup("SAW");
                                            detailsZD.setName("美码（女鞋）");
                                            detailsZD.setCode(item);

                                            if (!boxDetails.contains(detailsZD)) {
                                                boxDetails.add(detailsZD);
                                            }
                                        } else {
//                                            errStrs.add(name + ">" + address + ":类型错误");
                                        }

                                    } //美码（童码）
                                    else if (address.startsWith("D")) {//一行开始
                                        if (cellData instanceof String || cellData instanceof Number) {
                                            String item = String.valueOf(cellData);
                                            SKUModel detailsZD = new SKUModel();
                                            detailsZD.setGroup("SAC");
                                            detailsZD.setName("美码（童码）");
                                            detailsZD.setCode(item);

                                            if (!boxDetails.contains(detailsZD)) {
                                                boxDetails.add(detailsZD);
                                            }
                                        } else {
//                                            errStrs.add(name + ">" + address + ":类型错误");
                                        }

                                    } //欧码（童码）
                                    else if (address.startsWith("E")) {//一行开始
                                        if (cellData instanceof String || cellData instanceof Number) {
                                            String item = String.valueOf(cellData);
                                            SKUModel detailsZD = new SKUModel();
                                            detailsZD.setGroup("SEC");
                                            detailsZD.setName("欧码（童码）");
                                            detailsZD.setCode(item);

                                            if (!boxDetails.contains(detailsZD)) {
                                                boxDetails.add(detailsZD);
                                            }
                                        } else {
//                                            errStrs.add(name + ">" + address + ":类型错误");
                                        }

                                    } //日本（童码）
                                    else if (address.startsWith("F")) {//一行开始
                                        if (cellData instanceof String || cellData instanceof Number) {
                                            String item = String.valueOf(cellData);
                                            SKUModel detailsZD = new SKUModel();
                                            detailsZD.setGroup("SJC");
                                            detailsZD.setName("日本（童码）");
                                            detailsZD.setCode(item);

                                            if (!boxDetails.contains(detailsZD)) {
                                                boxDetails.add(detailsZD);
                                            }
                                        } else {
//                                            errStrs.add(name + ">" + address + ":类型错误");
                                        }

                                    } //欧码（通用）
                                    else if (address.startsWith("G")) {//一行开始
                                        if (cellData instanceof String || cellData instanceof Number) {
                                            String item = String.valueOf(cellData);
                                            SKUModel detailsZD = new SKUModel();
                                            detailsZD.setGroup("SEU");
                                            detailsZD.setName("欧码（通用）");
                                            detailsZD.setCode(item);

                                            if (!boxDetails.contains(detailsZD)) {
                                                boxDetails.add(detailsZD);
                                            }
                                        } else {
//                                            errStrs.add(name + ">" + address + ":类型错误");
                                        }

                                    } //身高（CM）
                                    else if (address.startsWith("H")) {//一行开始
                                        if (cellData instanceof String || cellData instanceof Number) {
                                            String item = String.valueOf(cellData);
                                            SKUModel detailsZD = new SKUModel();
                                            detailsZD.setGroup("SH");
                                            detailsZD.setName("身高（CM）");
                                            detailsZD.setCode(item);

                                            if (!boxDetails.contains(detailsZD)) {
                                                boxDetails.add(detailsZD);
                                            }
                                        } else {
//                                            errStrs.add(name + ">" + address + ":类型错误");
                                        }

                                    } //服装
                                    else if (address.startsWith("I")) {//一行开始
                                        if (cellData instanceof String || cellData instanceof Number) {
                                            String item = String.valueOf(cellData);
                                            SKUModel detailsZD = new SKUModel();
                                            detailsZD.setGroup("SC");
                                            detailsZD.setName("服装");
                                            detailsZD.setCode(item);

                                            if (!boxDetails.contains(detailsZD)) {
                                                boxDetails.add(detailsZD);
                                            }
                                        } else {
//                                            errStrs.add(name + ">" + address + ":类型错误");
                                        }

                                    } else if (address.startsWith("J")) {//无尺码
                                        if (cellData instanceof String || cellData instanceof Number) {
                                            String item = String.valueOf(cellData);
                                            SKUModel detailsZD = new SKUModel();
                                            detailsZD.setGroup("SNO");
                                            detailsZD.setName("无尺码");
                                            detailsZD.setCode(item);

                                            if (!boxDetails.contains(detailsZD)) {
                                                boxDetails.add(detailsZD);
                                            }
                                        } else {
//                                            errStrs.add(name + ">" + address + ":类型错误");
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


}
