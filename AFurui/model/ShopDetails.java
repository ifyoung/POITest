package com.magicair.webpj.AFurui.model;

import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import java.io.Serializable;

//货件的发货计划
public class ShopDetails implements Serializable {

    //SKU	美码	 FNSKU	FBA专用	在途总和	处理中	30天销量	总计销量
    // "颜色对应的比例"	目标销量	颜色对应的销量	尺码销售比
    // "单个尺码预计月销售量"	专用+在途+处理中	总共发货
    // 补齐数量
    // 发美森数量 	慢船补齐数量	发慢船数量
    // 专用可售月数	在途可售月数	总计可售月数
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    //
    private String SKU;
    private String US_Size;//美码
    private String FNSKU;//FNSKU
    private String AMOUNT_FBA;//FBA专用
    private String AMOUNT_inTransit;//在途总和
    private String AMOUNT_processing;//处理中
    private String sales_30_days;//30天销量
    private String total_sales;//总计销量
    private String color_ratio;//颜色对应的比例
    private String target_sales;//目标销量
    private String color_sales;//颜色对应的销量
    private String size_sales_ratio;//尺码销售比
    private String size_sales_expect_per;//单个尺码预计月销售量
    private String ALL_AMOUNT;//专用+在途+处理中
    private String total_shipment;//总共发货
    private String AMOUNT_fast_fixed;//补齐数量
    private String AMOUNT_shoes_fast;//发美森数量(快船)
    private String AMOUNT_slow_fixed;//慢船补齐数量
    private String AMOUNT_shoes_slow;//发慢船（普船）数量

    private String total_FBA_sales_months;//专用可售月数
    private String total_inTransit_sales_months;//在途可售月数
    private String total_all_sales_months;//总计可售月数


    private String sheetName;//底部tab表名

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

    public String getUS_Size() {
        return US_Size;
    }

    public void setUS_Size(String US_Size) {
        this.US_Size = US_Size;
    }

    public String getFNSKU() {
        return FNSKU;
    }

    public void setFNSKU(String FNSKU) {
        this.FNSKU = FNSKU;
    }

    public String getAMOUNT_FBA() {
        return AMOUNT_FBA;
    }

    public void setAMOUNT_FBA(String AMOUNT_FBA) {
        this.AMOUNT_FBA = AMOUNT_FBA;
    }

    public String getAMOUNT_inTransit() {
        return AMOUNT_inTransit;
    }

    public void setAMOUNT_inTransit(String AMOUNT_inTransit) {
        this.AMOUNT_inTransit = AMOUNT_inTransit;
    }

    public String getAMOUNT_processing() {
        return AMOUNT_processing;
    }

    public void setAMOUNT_processing(String AMOUNT_processing) {
        this.AMOUNT_processing = AMOUNT_processing;
    }

    public String getSales_30_days() {
        return sales_30_days;
    }

    public void setSales_30_days(String sales_30_days) {
        this.sales_30_days = sales_30_days;
    }

    public String getTotal_sales() {
        return total_sales;
    }

    public void setTotal_sales(String total_sales) {
        this.total_sales = total_sales;
    }

    public String getColor_ratio() {
        return color_ratio;
    }

    public void setColor_ratio(String color_ratio) {
        this.color_ratio = color_ratio;
    }

    public String getTarget_sales() {
        return target_sales;
    }

    public void setTarget_sales(String target_sales) {
        this.target_sales = target_sales;
    }

    public String getColor_sales() {
        return color_sales;
    }

    public void setColor_sales(String color_sales) {
        this.color_sales = color_sales;
    }

    public String getSize_sales_ratio() {
        return size_sales_ratio;
    }

    public void setSize_sales_ratio(String size_sales_ratio) {
        this.size_sales_ratio = size_sales_ratio;
    }

    public String getSize_sales_expect_per() {
        return size_sales_expect_per;
    }

    public void setSize_sales_expect_per(String size_sales_expect_per) {
        this.size_sales_expect_per = size_sales_expect_per;
    }

    public String getALL_AMOUNT() {
        return ALL_AMOUNT;
    }

    public void setALL_AMOUNT(String ALL_AMOUNT) {
        this.ALL_AMOUNT = ALL_AMOUNT;
    }

    public String getTotal_shipment() {
        return total_shipment;
    }

    public void setTotal_shipment(String total_shipment) {
        this.total_shipment = total_shipment;
    }

    public String getAMOUNT_fast_fixed() {
        return AMOUNT_fast_fixed;
    }

    public void setAMOUNT_fast_fixed(String AMOUNT_fast_fixed) {
        this.AMOUNT_fast_fixed = AMOUNT_fast_fixed;
    }


    public String getAMOUNT_slow_fixed() {
        return AMOUNT_slow_fixed;
    }

    public void setAMOUNT_slow_fixed(String AMOUNT_slow_fixed) {
        this.AMOUNT_slow_fixed = AMOUNT_slow_fixed;
    }


    public String getTotal_FBA_sales_months() {
        return total_FBA_sales_months;
    }

    public void setTotal_FBA_sales_months(String total_FBA_sales_months) {
        this.total_FBA_sales_months = total_FBA_sales_months;
    }

    public String getTotal_inTransit_sales_months() {
        return total_inTransit_sales_months;
    }

    public void setTotal_inTransit_sales_months(String total_inTransit_sales_months) {
        this.total_inTransit_sales_months = total_inTransit_sales_months;
    }

    public String getTotal_all_sales_months() {
        return total_all_sales_months;
    }

    public void setTotal_all_sales_months(String total_all_sales_months) {
        this.total_all_sales_months = total_all_sales_months;
    }

    public String getAMOUNT_shoes_fast() {
        return AMOUNT_shoes_fast;
    }

    public void setAMOUNT_shoes_fast(String AMOUNT_shoes_fast) {
        this.AMOUNT_shoes_fast = AMOUNT_shoes_fast;
    }

    public String getAMOUNT_shoes_slow() {
        return AMOUNT_shoes_slow;
    }

    public void setAMOUNT_shoes_slow(String AMOUNT_shoes_slow) {
        this.AMOUNT_shoes_slow = AMOUNT_shoes_slow;
    }
}