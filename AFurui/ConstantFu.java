package com.magicair.webpj.AFurui;

import java.util.*;
import java.util.function.Consumer;

/**
 * Created by lingchun on 2017/9/29.
 */
public class ConstantFu {

    //    isFurui 0 furuian 1 anbu 2 cm
    public enum TEMPLATE_company {
        Furui(0), Anbu(1), CM(2);

        private Integer value;

        //枚举类型的构造函数默认为private，因为枚举类型的初始化要在当前枚举类中完成。
        TEMPLATE_company(Integer value) {
            this.value = value;
        }

        public Integer getRawV() {
            return value;
        }

        public TEMPLATE_company rawRelate(int state) {
            if (state == Furui.value) {
                return Furui;
            }
            if (state == Anbu.value) {
                return Anbu;
            }
            if (state == CM.value) {
                return CM;
            }
            return Furui;
        }
    }

    //全局KEY
    public enum CONFIG_KEY {
        // 枚举常量，每个常量都有一个对应的字符串值
        KEY_beihuo_path("KEY_beihuo_path"),
        KEY_TWO("two"),
        KEY_THREE("three");

        // 枚举类型的私有字段，用于存储字符串值
        private final String value;

        // 枚举类型的私有构造方法，用于给每个常量赋值
        private CONFIG_KEY(String value) {
            this.value = value;
        }

        // 枚举类型的公有方法，用于获取字符串值
        public String getValue() {
            return value;
        }

    }


    //生成结果父文件夹
    public static String C_ResultExcelFloder = "/Results";
    public static String C_BaseBoxFloder = "/BaseBox";//箱规信息
    public static String C_BaseStockInfoFloder = "/BaseStockInfo";//仓库信息
    public static String C_BaseShenbaoInfoFloder = "/BaseShenbaoInfo";//报关申报要素


    public static String C_BaseSkuFloder = "/BaseSkus";//箱规信息


    //eg:备货信息表的上一周期的路径等
    public static String C_BaseConfigFloder = "/BaseConfig";//基础本地缓存信息
    public static String C_BaseConfig_name = "fu_config.json";//基础本地缓存信息

    public static String C_title = "SKU,Size,FNSKU,装箱率,发美森计划箱数,发慢船计划箱数,发美森数量,发慢船数量,总计划发货箱数,备注,仓库实际备货数量";
    public static List<String> C_titleM = Arrays.asList(C_title.split(","));


    //美森箱数/慢船箱数
    public static String C_title_S = "SKU,尺码,装箱率,美森箱数,总数量,重量(KG),宽(CM),长(CM),高(CM),Box weight(lb),Box width(in),Box length(in),Box height(in)";
    public static List<String> C_titleMS = Arrays.asList(C_title_S.split(","));


    public static String C_titleK = "SKU,Size,FNSKU,装箱率,发卡航/铁路计划箱数,发海运计划箱数,发卡航/铁路数量,发海运数量,总计划发货箱数,备注,仓库实际备货数量";
    public static List<String> C_titleKh = Arrays.asList(C_titleK.split(","));


    public static List<String> staticKeys = new ArrayList<>(Arrays.asList("美森数量", "发慢船数量", "实际卡航/铁路数量", "发海运数量", "需要发货数量", "需发货数量"));
    public static List<String> staticKeysTmp = new ArrayList<>(Arrays.asList("美森数量", "发慢船数量", "实际卡航/铁路数量", "发海运数量", "需要发货数量", "需发货数量"));


    public static String AMZ_title_S = "SKU,装箱率,箱数,数量,重量(KG),宽(CM),长(CM),高(CM),总重量,总体积,宽增加1CM,长增加1CM,高增加1CM,增加1CM的体积,宽增加1.5CM,长增加1.5CM,高增加1.5CM,增加1.5CM的体积,宽增加2CM,长增加2CM,高增加2CM,增加2CM的体积";
    public static List<String> AMZ_titleMS = Arrays.asList(AMZ_title_S.split(","));


    public static String AMZ_title_SAll = "SKU,装箱率,箱数,数量,重量(KG),宽(CM),长(CM),高(CM),总重量,总体积";
    public static List<String> AMZ_titleMSAll = Arrays.asList(AMZ_title_SAll.split(","));


    //AMZ 汇总表标题
    public static String AMZ_title_L = "仓库名,总箱数,数量,总重量,体积重,增加1CM体积重,增加1.5cm体积重,增加2CM体积重";
    public static List<String> AMZ_titleMSL = Arrays.asList(AMZ_title_L.split(","));


    public static String AMZ_title_Fee = "渠道,仓库名,船期,票名,店铺名,货件名称,FBA编码,SKU,FNSKU,ASIN,件数,计费重,总计费重,ERP款号,ERP尺码";
    public static List<String> AMZ_title_FeeList = Arrays.asList(AMZ_title_Fee.split(","));

    //PACKING LIST 表单尺码集合 US 1~14.5  EU 36~48.5
//    public static String PackingList_Size= "1,D,2,E,2.5,F,3,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V,W,X,Y,Z,AA,AB,AC";
    //7.2 占位
    public static String PackingList_Size_str = "1,2,2.5,3,4,4.5,5,5.5,6,6.5,7,7.2,7.5,8,8.5,9,9.5,10,10.5,11,11.5,12,12.5,13,13.5,14,14.5,36,37,37.5,38,38.5,39,39.5,40,40.5,41,42,42.5,43,44,44.5,45,45.5,46,47,47.5,48,48.5";

    //不能用，给出的packing list模板尺码不是连续的，有缺失
    public static String generateString(int n) {
        // 初始化一个空数组
        List<String> arr = new ArrayList<>();
        // 从 1 开始循环到 n
        for (int i = 1; i <= n; i++) {
            if (i > 14 && i < 36) {
                continue;
            }
            // 把 i 和 i+0.5 推入数组
            arr.add(String.valueOf(i));
            arr.add(String.valueOf(i + 0.5));
        }
        // 把数组转换成字符串，并用逗号分隔
        return String.join(",", arr);
    }

    //    public static Map<String, String> mapPackingList_size = Map.of("1", "D", "2", "E");
    public static List<String> PackingList_Size_list = Arrays.asList(PackingList_Size_str.split(","));

    //PACKING LIST 尺码对应集合
    public static Map<String, String> mapPackingList_size() {
        Map<String, String> mapPackingList_size = new LinkedHashMap<>();
        char start_US = 'D';
        char start_EU = 'I';
        PackingList_Size_list.forEach(new Consumer<String>() {
            //可用26进制？？
            @Override
            public void accept(String s) {
                if (Double.valueOf(s) < 15) {
                    char resChar = (char) (start_US + PackingList_Size_list.indexOf(s));
                    if (resChar > 'Z') {
                        mapPackingList_size.put(s, "A" + String.valueOf((char) ('A' + resChar - 'Z' - 1)));
                    } else {
                        mapPackingList_size.put(s, String.valueOf(resChar));
                    }

                } else {
                    char resChar = (char) (start_EU + PackingList_Size_list.indexOf(s) - PackingList_Size_list.indexOf("36"));

                    if (resChar > 'Z') {
                        mapPackingList_size.put(s, "A" + String.valueOf((char) ('A' + resChar - 'Z' - 1)));
                    } else {
                        mapPackingList_size.put(s, String.valueOf(resChar));
                    }

                }


            }
        });

        return mapPackingList_size;
    }

    ;


    public enum ID_Prefix {
        //零件10打头，加上自增序号；半成品100打头,加上自增序号；产品1000打头加上自增序号；
        //枚举类的实例对象必须在最前面先定义，而且必须每个实例对象都必须维护上value成员变量
        COMPONENT_ID("10"), SUIT_ID("200"), PRODUCT_ID("3000");

        private String value;

        //枚举类型的构造函数默认为private，因为枚举类型的初始化要在当前枚举类中完成。
        ID_Prefix(String value) {
            this.value = value;
        }

        public String getPrefix() {
            return value;
        }
    }


}
