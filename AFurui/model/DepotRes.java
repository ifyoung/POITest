package com.magicair.webpj.AFurui.model;

import com.magicair.webpj.utils.StringUtils;

import java.io.Serializable;

//仓库备货表
public class DepotRes implements Serializable {

    //(4)“发美森的数量”和”发慢船的数量” 来自于发货计划里面的实际美森数量和发慢船数量 发美森计划箱数和发慢船计划箱数 = 美森数量除以箱规，如果出现不能整除的现象，
    // 择入1   eg：装箱率10双/箱 实际发货11双，那么发货箱数=2箱
    // SKU	Size	FNSKU	装箱率	发美森计划箱数	发慢船计划箱数	发美森数量	发慢船数量	总计划发货箱数	备注	仓库实际备货数量
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
//    private Integer id;

    private String SKU;
    private String fSize;//尺码
    private String FNSKU;//FNSKU
    private int perBox;//装箱率


    //发美森计划箱数和发慢船计划箱数 = 美森数量除以箱规，如果出现不能整除的现象，择入1
    //eg：装箱率10双/箱 实际发货11双，那么发货箱数=2箱
    private int AMOUNT_boxes_fast;//发美森计划箱数
    private int AMOUNT_boxes_slow;//发慢船计划箱数


    //发货计划里面的实际美森数量和发慢船数量
    private int AMOUNT_shoes_fast;//发美森数量
    private int AMOUNT_shoes_slow;//发慢船数量


    //计划发货总箱数 =  发美森计划箱数+发慢船计划箱数
    private int AMOUNT_boxes_all;//总计划发货箱数
    private String note;//备注
    private int AMOUNT_actual;//仓库实际备货数量(箱)


    //>>>最后如果同时获取写入时，保证 AMOUNT_boxes_fast AMOUNT_boxes_slow 在前面获取！！因为这个需用上面的结果
    private int AMOUNT_actual_fast;//仓库实际备货数量>发快船箱数（优先满足）
    private int AMOUNT_actual_slow;//仓库实际备货数量>发慢船箱数


    private String group;//分组名称，货件表名（带颜色的）?


    private String sheetName;//底部tab表名


    //按箱规计算重量、长宽高 两套单位

    //默认的来自箱规？
    private double weight_kg;//重，默认kg
    private double width_cm;//宽，默认cm
    private double length_cm;//长，默认cm
    private double height_cm;//高，默认cm

    //欧制
    private double weight_lb;//重lb
    private double width_in;//宽in
    private double length_in;//长in
    private double height_in;//高in

    //发货总箱数和仓库备货差额
    private int delt;


    public int getDelt() {
        return delt;
    }

    public void setDelt(int delt) {
        this.delt = delt;
    }

    //根据备货计算实际，优先满足快船
    public int getAMOUNT_actual_fast() {
//        if(this.AMOUNT_actual>this.getAMOUNT_boxes_fast()){ //这儿还需计算吗？？？
        if (this.AMOUNT_actual >= this.AMOUNT_boxes_fast) {//直接上传来的就不用上面那计算了？或者可以计算复核一下？？

            this.AMOUNT_actual_fast = this.AMOUNT_boxes_fast;
        } else {
            this.AMOUNT_actual_fast = this.AMOUNT_actual;
        }

        return AMOUNT_actual_fast;
    }

    public void setAMOUNT_actual_fast(int AMOUNT_actual_fast) {
        this.AMOUNT_actual_fast = AMOUNT_actual_fast;
    }

    public int getAMOUNT_actual_slow() {

        int leftBoxes = this.AMOUNT_actual - this.getAMOUNT_actual_fast();
        if (leftBoxes >= 0) {
            if (leftBoxes >= this.AMOUNT_boxes_slow) {

                this.AMOUNT_actual_slow = this.AMOUNT_boxes_slow;
            } else {
                this.AMOUNT_actual_slow = leftBoxes;
            }

        } else {
            this.AMOUNT_actual_slow = 0;
        }

        return AMOUNT_actual_slow;
    }

    public void setAMOUNT_actual_slow(int AMOUNT_actual_slow) {
        this.AMOUNT_actual_slow = AMOUNT_actual_slow;
    }

    public String getSheetName() {
        return sheetName;
    }

    public void setSheetName(String sheetName) {
        this.sheetName = sheetName;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

//    public Integer getId() {
//        return id;
//    }
//
//    public void setId(Integer id) {
//        this.id = id;
//    }

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

    //发美森计划箱数和发慢船计划箱数 = 美森数量除以箱规，如果出现不能整除的现象，择入1
    //eg：装箱率10双/箱 实际发货11双，那么发货箱数=2箱
    public int getAMOUNT_boxes_fast() {
//        if (this.perBox > 0) {
//            int remain = this.AMOUNT_shoes_fast % this.perBox;
//            if (remain == 0) {//整除
//                this.AMOUNT_boxes_fast = this.AMOUNT_shoes_fast / this.perBox;
//
//            } else {
//                this.AMOUNT_boxes_fast = this.AMOUNT_shoes_fast / this.perBox + 1;
//            }
//        }
        this.AMOUNT_boxes_fast = computeBoxes(this.AMOUNT_shoes_fast, this.perBox);
        //美森（快船）
        return AMOUNT_boxes_fast;
    }

    public void setAMOUNT_boxes_fast(int AMOUNT_boxes_fast) {
        this.AMOUNT_boxes_fast = AMOUNT_boxes_fast;
    }

    private int computeBoxes(int numShoes, int perBox) {
        int boxes = 0;
        if (perBox > 0) {
            int remain = numShoes % perBox;
            if (remain == 0) {//整除
                boxes = numShoes / perBox;
            } else {
                //  如果出现不能整除的现象，余数小于装箱率 1/3，发货箱数为+0，反之 择入+1这种情况单元格给标记为绿色
                int addNum = remain < perBox / 3 ? 0 : 1;
                boxes = numShoes / perBox + addNum;
            }
        }
        return boxes;
    }

    public int getAMOUNT_boxes_slow() {
        this.AMOUNT_boxes_slow = computeBoxes(this.AMOUNT_shoes_slow, this.perBox);
        //普船（慢船）
        return AMOUNT_boxes_slow;
    }

    public void setAMOUNT_boxes_slow(int AMOUNT_boxes_slow) {
        this.AMOUNT_boxes_slow = AMOUNT_boxes_slow;
    }

    public int getAMOUNT_shoes_fast() {
        return AMOUNT_shoes_fast;
    }

    public void setAMOUNT_shoes_fast(int AMOUNT_shoes_fast) {
        this.AMOUNT_shoes_fast = AMOUNT_shoes_fast;
    }

    public int getAMOUNT_shoes_slow() {
        return AMOUNT_shoes_slow;
    }

    public void setAMOUNT_shoes_slow(int AMOUNT_shoes_slow) {
        this.AMOUNT_shoes_slow = AMOUNT_shoes_slow;
    }

    public int getAMOUNT_boxes_all() {
        //求和
        this.AMOUNT_boxes_all = this.AMOUNT_boxes_fast + this.AMOUNT_boxes_slow;
        return AMOUNT_boxes_all;
    }

    public void setAMOUNT_boxes_all(int AMOUNT_boxes_all) {
        this.AMOUNT_boxes_all = AMOUNT_boxes_all;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public int getAMOUNT_actual() {
        return AMOUNT_actual;
    }

    public void setAMOUNT_actual(int AMOUNT_actual) {
        this.AMOUNT_actual = AMOUNT_actual;
    }

    public double getWeight_kg() {
        return StringUtils.get2Numbers(String.valueOf(this.weight_kg));
    }

    public void setWeight_kg(double weight_kg) {
        this.weight_kg = weight_kg;
    }

    public double getWidth_cm() {
        return width_cm;
    }

    public void setWidth_cm(double width_cm) {
        this.width_cm = width_cm;
    }

    public double getLength_cm() {
        return length_cm;
    }

    public void setLength_cm(double length_cm) {
        this.length_cm = length_cm;
    }

    public double getHeight_cm() {
        return height_cm;
    }

    public void setHeight_cm(double height_cm) {
        this.height_cm = height_cm;
    }

    //lb in
    public double getWeight_lb() {
        //两位小数？
        this.weight_lb = (this.weight_kg * 2.2);
        return StringUtils.get2Numbers(String.valueOf(this.weight_lb));
    }

    public void setWeight_lb(double weight_lb) {
        this.weight_lb = weight_lb;
    }

    public double getWidth_in() {
        //最多8为小数？
        this.width_in = (this.width_cm / 2.54);
        return width_in;
    }

    public void setWidth_in(double width_in) {
        this.width_in = width_in;
    }

    public double getLength_in() {
        this.length_in = (this.length_cm / 2.54);
        return length_in;
    }

    public void setLength_in(double length_in) {
        this.length_in = length_in;
    }

    public double getHeight_in() {
        this.height_in = (this.height_cm / 2.54);
        return height_in;
    }

    public void setHeight_in(float height_in) {
        this.height_in = height_in;
    }

    //不用再主动调用一次，在构建出仓库备货表的那一步（第一步骤）会在写表时调用各个单独的get！！
    public void actionCompute() {
        this.getAMOUNT_boxes_all();
        this.getAMOUNT_boxes_fast();
        this.getAMOUNT_boxes_slow();
    }


    public void actionComputeActual() {
        this.getWeight_lb();
        this.getWidth_in();
        this.getLength_in();
        this.getHeight_in();

        this.getAMOUNT_actual_fast();
        this.getAMOUNT_actual_slow();
    }
}