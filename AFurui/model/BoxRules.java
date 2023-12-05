package com.magicair.webpj.AFurui.model;

import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import java.io.Serializable;


//箱规
public class BoxRules implements Serializable {

    //    SKU	尺码	  FNSKU	 装箱率	重量(KG)	 长(CM)	 宽(CM)	 高(CM)
    // 装箱率>一箱装的双数  其实可以根据装箱率按比估算大致重量？
    //1 磅约等于 0.4536 公斤，1 公斤约等于 2.2046 磅。
    // 重量、长宽高单位有变，kg cm  lp in
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String SKU;
    private String fSize;//尺码
    private String FNSKU;//FNSKU
    private int perBox;//装箱率
    private float bWeight;//重量
    private float bLength;//长
    private float bWidth;//宽
    private float bHeight;//高
    //规格单位类型 ？？ 参数
//    private String type;
    private String sheetName;//底部tab表名
    private int column;//用于列数校验

    private String erpCode;//ERP款号
    private String ARTICLE;//ARTICLE号
    private String HS_CODE;//HS CODE


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

    public int getColumn() {
        return column;
    }

    public void setColumn(int column) {
        this.column = column;
    }

    public String getSheetName() {
        return sheetName;
    }

    public void setSheetName(String sheetName) {
        this.sheetName = sheetName;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getSKU() {
        return SKU;
    }

    public void setSKU(String SKU) {
        this.SKU = SKU;
    }

    public String getfSize() {
        return fSize;
    }

    public void setfSize(String fSize) {
        this.fSize = fSize;
    }

    public String getFNSKU() {
        return FNSKU;
    }

    public void setFNSKU(String FNSKU) {
        this.FNSKU = FNSKU;
    }

    public int getPerBox() {
        return perBox;
    }

    public void setPerBox(int perBox) {
        this.perBox = perBox;
    }

    public float getbWeight() {
        return bWeight;
    }

    public void setbWeight(float bWeight) {
        this.bWeight = bWeight;
    }

    public float getbLength() {
        return bLength;
    }

    public void setbLength(float bLength) {
        this.bLength = bLength;
    }

    public float getbWidth() {
        return bWidth;
    }

    public void setbWidth(float bWidth) {
        this.bWidth = bWidth;
    }

    public float getbHeight() {
        return bHeight;
    }

    public void setbHeight(float bHeight) {
        this.bHeight = bHeight;
    }
}