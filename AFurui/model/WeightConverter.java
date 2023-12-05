package com.magicair.webpj.AFurui.model;

import com.magicair.webpj.utils.Lg;
import com.magicair.webpj.utils.StringUtils;
import com.opencsv.bean.AbstractBeanField;
import com.opencsv.exceptions.CsvConstraintViolationException;
import com.opencsv.exceptions.CsvDataTypeMismatchException;

//
public class WeightConverter extends AbstractBeanField {
    @Override
    protected Object convert(String value) throws CsvDataTypeMismatchException, CsvConstraintViolationException {
        // 获取当前列名
        String columnName = getType().getName();
        Lg.i("WeightConverter>>>>", columnName);
        // 将字符串值转换为double类型
        double weight = Double.parseDouble(value);
        // 如果列名是（磅），则将重量转换为千克
//        if (columnName.equals("包装箱重量（磅）")) {
        if (columnName.contains("磅")) {
            weight = weight * 0.454;

        }
        weight = StringUtils.get2Numbers(String.valueOf(weight));
        // 返回转换后的重量
        return weight;
    }
}

// AgeConverter 类
//public class WeightConverter extends AbstractCsvConverter {
//    @Override
//    public Object convertToRead(String value) {
//        // 去掉前缀 "Age: "
//        String ageString = value.replace("Age: ", "");
//        // 将字符串转换为整数
//        int age = Integer.parseInt(ageString);
//        // 返回转换后的结果
//        return age;
//    }
//
//    @Override
//    public String convertToWrite(Object value) throws CsvDataTypeMismatchException {
//        // 将整数转换为字符串
//        String ageString = String.valueOf(value);
//        // 在字符串前加上前缀 "Age: "
//        String result = "Age: " + ageString;
//        // 返回转换后的结果
//        return result;
//    }
//
//
////    @Override
////    public void setField(AbstractBeanField beanField) {
////        // 这个方法可以留空，或者添加一些初始化逻辑
////    }
//}


