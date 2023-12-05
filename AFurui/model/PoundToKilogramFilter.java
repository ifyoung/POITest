package com.magicair.webpj.AFurui.model;

import com.magicair.webpj.utils.Lg;
import com.magicair.webpj.utils.StringUtils;
import com.opencsv.CSVReader;
import com.opencsv.bean.CsvToBeanFilter;

import java.util.concurrent.ConcurrentHashMap;

// 定义一个CsvToBeanFilter接口的实现类，用来过滤和转换磅为千克
public class PoundToKilogramFilter implements CsvToBeanFilter {
    private CSVReader reader; // csv文件的读取器
    private String[] header; // csv文件的表头
//    private Map exTitles; // 头部拼接的读取信息

    private ConcurrentHashMap<String, Object> exTitles;//多线程安全

    // 构造方法，传入一个CSVReader对象
    public PoundToKilogramFilter(CSVReader reader) {
        this.reader = reader;
//        exTitles = new HashMap();
        exTitles = new ConcurrentHashMap<>();
//        try {
//            reader.skip(9);
//// 读取第一行作为表头
//            this.header = reader.readNext();
////            Lg.i(">>>>", "表头", this.header);
//        } catch (IOException | CsvValidationException e) {
//            e.printStackTrace();
//        }
    }


    // 实现allowLine方法，判断是否允许某一行数据通过过滤
    @Override
    public boolean allowLine(String[] line) {
// 检查表头的第9列是否包含"pound"或"lb"
//        if (header[8].toLowerCase().contains("pound") || header[8].toLowerCase().contains("lb")) {
//        Lg.i(">>>>", "数据变换" + header[9], line);
        if (line.length == 2) {
            Lg.i(">>>>前头", "数据变换" + header, line);
            //货件编号
            //货件名称
            //配送地址
            if (line[0].contains("货件编号")) {
//                exTitles.add(line[1]);
                exTitles.put("obNumber", line[1]);
            }
            if (line[0].contains("货件名称")) {
                exTitles.put("obName", line[1]);
            }
            if (line[0].contains("配送地址")) {
                exTitles.put("address", line[1]);
            }

            return false;
        }
        if (line.length > 9) {
            if (header == null && line[0].toLowerCase().contains("sku")) {
                header = line;
                return false;
            }

            String pattern = "箱子\\d.*";
            //.matches(pattern)
            if (header != null && !String.join(",", header).contains("箱号")) {//分箱,略过
                line[5] = "true";//拼接
                if (exTitles.values().size() > 0) {
                    line[6] = (String) exTitles.get("obNumber");
                    line[7] = (String) exTitles.get("obName");
                    line[8] = (String) exTitles.get("address");

//                    line[9] = "";
//                    line[10] = "";
//                    if (line.length > 11) {
//                        line[11] = "";
//                        line[12] = "";
//                    }

                }
                for (int i = 0; i < line.length; i++) {
                    if (i > 8) {
                        line[i] = "";
                    }
                }

                if (StringUtils.isNullOrEmpty(line[0])) {
                    return false;
                }

                return true;
            }

//            String[] newLine = new String[line.length + 2];//17、18
            // 把line数组的内容复制到新数组中
//            System.arraycopy(line, 0, newLine, 0, line.length);
//            line = newLine;

            Lg.i(">>>>表头XXXXX", "----", header, line);
            Lg.i(">>>>表头拼接", "----", exTitles.values());
            if (header[9].contains("磅")) {
// 如果是，转换成千克，并返回true
                line[9] = String.valueOf(StringUtils.get2Numbers(String.valueOf(Double.parseDouble(line[9]) * 0.454)));

//            return true;
            }
            //10 11 12
//        1in = 2.54cm
            if (header.length > 9 && header[10].contains("英寸")) {
                line[10] = String.valueOf(StringUtils.get1Numbers(String.valueOf(Double.parseDouble(line[10]) * 2.54)));
            }
            try {
                if (header.length > 10 && header[11].contains("英寸")) {
                    line[11] = String.valueOf(StringUtils.get1Numbers(String.valueOf(Double.parseDouble(line[11]) * 2.54)));
                }
            } catch (NumberFormatException e) {
                e.printStackTrace();
            } catch (ArrayIndexOutOfBoundsException ignored) {
                Lg.e(">>" + exTitles.get("obName"), ignored.getMessage());
            }
            if (header.length > 11 && header[12].contains("英寸")) {
                line[12] = String.valueOf(StringUtils.get1Numbers(String.valueOf(Double.parseDouble(line[12]) * 2.54)));
            }
            if (exTitles.values().size() > 0) {
                line[6] = (String) exTitles.get("obNumber");
                line[7] = (String) exTitles.get("obName");
                line[8] = (String) exTitles.get("address");
            }
            return true;
        }


        return false;
    }
}
