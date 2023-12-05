package com.magicair.webpj.AFurui.model;

import java.io.Serializable;
import java.util.List;

public class AMZoutBox implements Serializable {

    String boxName;//箱名
    String boxNum;//箱号
    double boxWeight;//重量（磅，千克）
    double boxLength;//长（英寸，厘米）
    double boxWidth;//宽（英寸，厘米）
    double boxHeight;//高（英寸，厘米）

    int boxShoes;

//    int[] boxShoesTmp;//鞋的总数,缓存

    List<Integer> boxShoesTmp;

    //体积重，长*宽*高*/167
    double t_weight;
    //计费重，体积重和包装箱重量对比取大值
    double f_weight;

    //具体计费重逻辑
    //分摊到单个SKU的计费重 = 计费重*当前箱子内该SKU的数量/当前箱子所有商品的数量
    //累加所有的箱子，得到当前货件每个sku的计费重
    double sku_weight;

    int boxPos;//对应sku的位置

    //箱内鞋总数
    int boxShoesAll;

    //统一箱号对应sku重复出现次数，用来判断是否是真拼箱
    int repeats;


    public int getRepeats() {
        return repeats;
    }

    public void setRepeats(int repeats) {
        this.repeats = repeats;
    }

    public int getBoxShoesAll() {
//        if (this.boxShoesTmp != null) {
//            this.boxShoesAll = this.boxShoesTmp.stream().mapToInt(Integer::intValue).sum();
//        }
        return boxShoesAll;
    }

    public void setBoxShoesAll(int boxShoesAll) {
        this.boxShoesAll = boxShoesAll;
    }

    public double getSku_weight() {
        return sku_weight;
    }

    public void setSku_weight(double sku_weight) {
        this.sku_weight = sku_weight;
    }

    public List<Integer> getBoxShoesTmp() {
        return boxShoesTmp;
    }

    public void setBoxShoesTmp(List<Integer> boxShoesTmp) {
        this.boxShoesTmp = boxShoesTmp;
    }

    public double getT_weight() {
        return t_weight;
    }

    public void setT_weight(double t_weight) {
        this.t_weight = t_weight;
    }

    public double getF_weight() {
        return f_weight;
    }

    public void setF_weight(double f_weight) {
        this.f_weight = f_weight;
    }

    public int getBoxShoes() {
        return boxShoes;
    }

    public void setBoxShoes(int boxShoes) {
        this.boxShoes = boxShoes;
    }

    public String getBoxName() {
        return boxName;
    }

    public void setBoxName(String boxName) {
        this.boxName = boxName;
    }

    public String getBoxNum() {
        return boxNum;
    }

    public void setBoxNum(String boxNum) {
        this.boxNum = boxNum;
    }

    public double getBoxWeight() {
        return boxWeight;
    }

    public void setBoxWeight(double boxWeight) {
        this.boxWeight = boxWeight;
    }

    public double getBoxLength() {
        return boxLength;
    }

    public void setBoxLength(double boxLength) {
        this.boxLength = boxLength;
    }

    public double getBoxWidth() {
        return boxWidth;
    }

    public void setBoxWidth(double boxWidth) {
        this.boxWidth = boxWidth;
    }

    public double getBoxHeight() {
        return boxHeight;
    }

    public void setBoxHeight(double boxHeight) {
        this.boxHeight = boxHeight;
    }

    public int getBoxPos() {
        return boxPos;
    }

    public void setBoxPos(int boxPos) {
        this.boxPos = boxPos;
    }


}
