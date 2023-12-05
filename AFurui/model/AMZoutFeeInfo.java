package com.magicair.webpj.AFurui.model;

import java.util.List;

public class AMZoutFeeInfo {

    String sku;
    String name;//商品名称
    String ASIN;//
    String FNSKU;//

    String state;//状况
    int total;//(单个)商品总数
    int totalAll;//商品数量(头部提取的总数)


    //货件编号
    String obNumber;

    //货件名称
    String obName;

    //配送地址
    String address;

    int skuNum;//sku数量
    int boxes;//箱子总数

    //箱子关系
    List<AMZoutBox> amZoutBoxes;

    //分摊到单个SKU的计费重
//    double sku_weight; //放到box里
    double sku_weight_all;

    //仓库名
    String stock;

    //渠道
    String obWays;
    //船期
    String obDate;


    //这个单子总计费重
    double allFeeWeight;


    private String erpCode;//ERP款号
    private String ARTICLE;//ARTICLE号
    private String HS_CODE;//HS CODE

    private String erpSize;//ERP尺码


    public String getErpCode() {
        return erpCode;
    }

    public void setErpCode(String erpCode) {
        this.erpCode = erpCode;
    }

    public String getARTICLE() {
        return ARTICLE;
    }

    public void setARTICLE(String ARTICLE) {
        this.ARTICLE = ARTICLE;
    }

    public String getHS_CODE() {
        return HS_CODE;
    }

    public void setHS_CODE(String HS_CODE) {
        this.HS_CODE = HS_CODE;
    }

    public String getErpSize() {
        return erpSize;
    }

    public void setErpSize(String erpSize) {
        this.erpSize = erpSize;
    }

    public double getAllFeeWeight() {
        return allFeeWeight;
    }

    public void setAllFeeWeight(double allFeeWeight) {
        this.allFeeWeight = allFeeWeight;
    }

    public String getObWays() {
        return obWays;
    }

    public void setObWays(String obWays) {
        this.obWays = obWays;
    }

    public String getObDate() {
        return obDate;
    }

    public void setObDate(String obDate) {
        this.obDate = obDate;
    }

    public double getSku_weight_all() {
        return sku_weight_all;
    }

    public void setSku_weight_all(double sku_weight_all) {
        this.sku_weight_all = sku_weight_all;
    }

    public int getTotalAll() {
        return totalAll;
    }

    public void setTotalAll(int totalAll) {
        this.totalAll = totalAll;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getASIN() {
        return ASIN;
    }

    public void setASIN(String ASIN) {
        this.ASIN = ASIN;
    }

    public String getFNSKU() {
        return FNSKU;
    }

    public void setFNSKU(String FNSKU) {
        this.FNSKU = FNSKU;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public String getStock() {
        return stock;
    }

    public void setStock(String stock) {
        this.stock = stock;
    }

    public String getObNumber() {
        return obNumber;
    }

    public void setObNumber(String obNumber) {
        this.obNumber = obNumber;
    }

    public String getObName() {
        return obName;
    }

    public void setObName(String obName) {
        this.obName = obName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public int getSkuNum() {
        return skuNum;
    }

    public void setSkuNum(int skuNum) {
        this.skuNum = skuNum;
    }

    public int getBoxes() {
        return boxes;
    }

    public void setBoxes(int boxes) {
        this.boxes = boxes;
    }

    public List<AMZoutBox> getAmZoutBoxes() {
        return amZoutBoxes;
    }

    public void setAmZoutBoxes(List<AMZoutBox> amZoutBoxes) {
        this.amZoutBoxes = amZoutBoxes;
    }
}
