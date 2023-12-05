package com.magicair.webpj.AFurui.model;

import com.opencsv.bean.CsvBindByPosition;
import org.apache.poi.ss.usermodel.PictureData;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class AMZout implements Serializable {

//    @CsvBindByName(column = "SKU", required = true)
//    @CsvBindByName(column = "SKU", required = true)

    @CsvBindByPosition(position = 0)
    private String sku;

    //ASIN	FNSKU

    @CsvBindByPosition(position = 2)
    private String ASIN;
    @CsvBindByPosition(position = 3)
    private String FNSKU;


    //        @CsvBindByNames({@CsvBindByName(column = "包装箱重量（磅）"), @CsvBindByName(column = "包装箱重量（千克）")})
//    @CsvCustomBindByNames({@CsvCustomBindByName(column = "包装箱重量（磅）", converter = WeightConverter.class, profiles = "pound"), @CsvCustomBindByName(converter = WeightConverter.class, column = "包装箱重量（千克）", profiles = "kilo")})
//上面这种要分两个Reader按配置读取！！
    //    @CsvBindByPosition(position = 1)
// 使用 @CsvCustomBindByPosition 注解来指定属性对应的 CSV 列位置和自定义转换器类
//    @CsvCustomBindByPosition(position = 9, converter = WeightConverter.class)
    @CsvBindByPosition(position = 9)
    private double weight; // 包装箱重量（千克）

    //    @CsvBindByNames({@CsvBindByName(column = "箱子长度（英寸）"), @CsvBindByName(column = "箱子长度（厘米）")})
    @CsvBindByPosition(position = 10)
    private double length;
    //    @CsvBindByNames({@CsvBindByName(column = "箱子宽度（英寸）"), @CsvBindByName(column = "箱子宽度（厘米）")})
    @CsvBindByPosition(position = 11)
    private double width;
    //    @CsvBindByNames({@CsvBindByName(column = "箱子高度（英寸）"), @CsvBindByName(column = "箱子高度（厘米）")})
    @CsvBindByPosition(position = 12)
    private double height;
    //装箱率
//    @CsvBindByName(column = "每箱件数", required = true)
    @CsvBindByPosition(position = 13)
    private int per;
//    @CsvBindByName(column = "箱子总数", required = true)

    @CsvBindByPosition(position = 14)
    private int boxes;
//    @CsvBindByName(column = "商品总数", required = true)

    @CsvBindByPosition(position = 15)
    private int shoes;


    //16 17 18 > 自己拼接上面两列项目>>覆盖>>>会错乱
    //用6、7、8覆盖原有占位
    //即商品预处理方、商品贴标方、预处理类型
    // GREENWOOD, IN>>>>
    //货件编号
    @CsvBindByPosition(position = 6)
    private String obNumber;

    //货件名称
    @CsvBindByPosition(position = 7)
    private String obName;

    //配送地址
    @CsvBindByPosition(position = 8)
    private String address;

    //仓库名
    private String stock;

    //是否是拼箱,占位"状况"
    @CsvBindByPosition(position = 5)
    private String isPJ;


    //箱号
    private String pjNo;

    //净重
    private double pureWeight;

    //总体积
    private double allVolume;

    //顶部的总数
    private int totalAll;


    private String erpCode;//ERP款号
    private String ARTICLE;//ARTICLE号
    private String HS_CODE;//HS CODE

    private String enName;//英文品名
    private String type;//分类
    private String factory;//工厂
    private String ticketName;//开票品名
    private String usd;//最终报关单价/USD

    private String kgs;//单件毛重
    private String brand;//品牌
    private String material;//材质

    private String purpose;//用途

    private PictureData picData;//图片


    private String perMoney;//申报单里价格


    private String unit;//申报单位
    private String shenbaoNumer;//申报商品编号
    private String shenbaoName;//申报商品名称及规格型号

    private String description;//Description


    //箱子关系
    List<AMZoutBox> amZoutBoxes;

    int total;//(单个)商品总数>针对拼箱里的总箱


    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getEnName() {
        return enName;
    }

    public void setEnName(String enName) {
        this.enName = enName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFactory() {
        return factory;
    }

    public void setFactory(String factory) {
        this.factory = factory;
    }

    public String getTicketName() {
        return ticketName;
    }

    public void setTicketName(String ticketName) {
        this.ticketName = ticketName;
    }

    public String getUsd() {
        return usd;
    }

    public void setUsd(String usd) {
        this.usd = usd;
    }

    public String getKgs() {
        return kgs;
    }

    public void setKgs(String kgs) {
        this.kgs = kgs;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    public PictureData getPicData() {
        return picData;
    }

    public void setPicData(PictureData picData) {
        this.picData = picData;
    }

    public List<AMZoutBox> getAmZoutBoxes() {
        return amZoutBoxes;
    }

    public void setAmZoutBoxes(List<AMZoutBox> amZoutBoxes) {
        this.amZoutBoxes = amZoutBoxes;
    }


    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getShenbaoNumer() {
        return shenbaoNumer;
    }

    public void setShenbaoNumer(String shenbaoNumer) {
        this.shenbaoNumer = shenbaoNumer;
    }

    public String getShenbaoName() {
        return shenbaoName;
    }

    public void setShenbaoName(String shenbaoName) {
        this.shenbaoName = shenbaoName;
    }

    public String getPerMoney() {
        return perMoney;
    }

    public void setPerMoney(String perMoney) {
        this.perMoney = perMoney;
    }

    //对应尺码的箱数
    private Map<String, Integer> posArray;


    public Map<String, Integer> getPosArray() {
        return posArray;
    }

    public void setPosArray(Map<String, Integer> posArray) {
        this.posArray = posArray;
    }

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

    public int getTotalAll() {
        return totalAll;
    }

    public void setTotalAll(int totalAll) {
        this.totalAll = totalAll;
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

    public String getIsPJ() {
        return isPJ;
    }

    public void setIsPJ(String isPJ) {
        this.isPJ = isPJ;
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

    public double getPureWeight() {
        return pureWeight;
    }

    public void setPureWeight(double pureWeight) {
        this.pureWeight = pureWeight;
    }

    public double getAllVolume() {
        return allVolume;
    }

    public void setAllVolume(double allVolume) {
        this.allVolume = allVolume;
    }

    public String getStock() {
        return stock;
    }

    public void setStock(String stock) {
        this.stock = stock;
    }

    public int getPer() {
        return per;
    }

    public void setPer(int per) {
        this.per = per;
    }

    public int getBoxes() {
        return boxes;
    }

    public void setBoxes(int boxes) {
        this.boxes = boxes;
    }

    public int getShoes() {
        return shoes;
    }

    public void setShoes(int shoes) {
        this.shoes = shoes;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    //lb转kg 1lb = 0.454kg
    // 总重量2位小数？体积double自由小数？长宽高都保留整数？加1.5保留一位小数？
    public double getWeight() {
//        double w = this.weight * 0.454;

//        this.weight = StringUtils.get2Numbers(String.valueOf(w));
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    //in转cm 1in = 2.54cm
    public double getLength() {
//        double L = this.length * 2.54;
//        this.length = StringUtils.get1Numbers(String.valueOf(L));
        return length;
    }

    public void setLength(double length) {
        this.length = length;
    }

    public double getWidth() {

//        double L = this.width * 2.54;
//        this.width = StringUtils.get1Numbers(String.valueOf(L));

//        this.width = String.valueOf(Double.parseDouble(this.width) * 2.54);
        return width;
    }

    public void setWidth(double width) {
        this.width = width;
    }

    public double getHeight() {
//        this.height = String.valueOf(Double.parseDouble(this.height) * 2.54);

//        double L = this.height * 2.54;
//        this.height = StringUtils.get1Numbers(String.valueOf(L));
        return height;
    }

    public void setHeight(double height) {
        this.height = height;
    }

    public String getPjNo() {
        return pjNo;
    }

    public void setPjNo(String pjNo) {
        this.pjNo = pjNo;
    }
}
